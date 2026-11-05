package com.focusguard.app.commitment

import android.util.Log
import com.focusguard.app.core.common.constants.Timings
import com.focusguard.app.core.di.IoDispatcher
import com.focusguard.app.domain.model.CommitmentLockState
import com.focusguard.app.domain.policy.CommitmentRestoreDecision
import com.focusguard.app.domain.policy.CommitmentRestorePolicy
import com.focusguard.app.domain.repository.CommitmentLockRepository
import com.focusguard.app.domain.repository.CommitmentRead
import com.focusguard.app.domain.repository.CommitmentReadFailure
import com.focusguard.app.domain.repository.CommitmentWrite
import com.focusguard.app.domain.repository.DeviceIdentityRepository
import com.focusguard.app.domain.repository.LocalCommitmentStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "FG:Commitment"

/**
 * CommitmentRestoreEngine
 * =======================
 * The one owner of "does this device still owe a timed uninstall lock, and is that recorded where an
 * uninstall cannot reach it?".
 *
 * Runs once per process, after the first user unlock (Firestore needs credential-encrypted storage),
 * in two directions:
 *
 * 1. **Restore** — reads `device_locks/{ANDROID_ID}` and re-arms local protection when the server
 *    record covers more than this install does. This is what closes the
 *    "uninstall → reinstall → sign in → no timed lock" bypass: the timed lock was never in the
 *    account sync tree, so signing back in could not bring it back.
 * 2. **Mirror** — watches the local timed-lock fields and pushes every arm/extension upward, so the
 *    next reinstall has something to find.
 *
 * Deliberate limits of this phase, stated plainly:
 * - Only a *timed* lock is mirrored. Untimed/permanent protection stays local.
 * - Releases are never mirrored: an in-app disarm (challenge or support code) is honored locally, but
 *   the device record keeps its remaining days, so a reinstall re-arms the lock and the challenge has
 *   to be passed again. That asymmetry is the point of the feature.
 * - While the server cannot be reached the app must still work, so the gate is bounded: after
 *   [Timings.COMMITMENT_RESTORE_MAX_ATTEMPTS] attempts it releases with [CommitmentRestoreStatus.UNREACHABLE]
 *   and every later cold start retries. Absence of proof never disarms a lock that is already running.
 */
@Singleton
class CommitmentRestoreEngine @Inject constructor(
    private val deviceIdentity: DeviceIdentityRepository,
    private val commitmentLocks: CommitmentLockRepository,
    private val prefs: LocalCommitmentStore,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    private val errorHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG, "Commitment restore failed: ${throwable.javaClass.simpleName}")
    }
    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher + errorHandler)

    private val _status = MutableStateFlow(CommitmentRestoreStatus.CHECKING)
    val status: StateFlow<CommitmentRestoreStatus> = _status.asStateFlow()

    /** Guard so a re-entry of the process-start path cannot stack a second lookup + watcher. */
    private val started = AtomicBoolean(false)

    /** Last (days, expiry) pair this process pushed upward, so DataStore replays do not re-write.
     *  Written from the restore coroutine, read from the mirror collector — hence volatile. */
    @Volatile private var lastMirroredDays: Int = -1
    @Volatile private var lastMirroredExpiryMs: Long = -1L

    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch { restoreThenWatch() }
    }

    private suspend fun restoreThenWatch() {
        val deviceId = deviceIdentity.reinstallStableId()
        if (deviceId.isBlank()) {
            Log.w(TAG, "No reinstall-stable identity: commitment restore and mirror disabled")
            _status.value = CommitmentRestoreStatus.SKIPPED_NO_IDENTITY
            return
        }
        runCatching { restore(deviceId) }
            .onFailure {
                // Every expected outcome already sets a status inside restore(); reaching here means
                // something unforeseen threw. Leave the status terminal rather than stuck on CHECKING,
                // so the UI's own ceiling is not the only thing that releases the gate. Protection is
                // untouched either way: an unresolved lookup never disarms a running local lock.
                Log.w(TAG, "Commitment restore aborted: ${it.javaClass.simpleName}")
                if (_status.value.isGating) _status.value = CommitmentRestoreStatus.UNREACHABLE
            }
        mirrorLocalChanges(deviceId)
    }

    /** One-shot cold-start lookup with bounded retries; decides and applies via the pure policy. */
    private suspend fun restore(deviceId: String) {
        var attempt = 0
        while (true) {
            attempt++
            when (val read = commitmentLocks.read(deviceId)) {
                is CommitmentRead.Found -> {
                    val local = prefs.localCommitmentState(deviceId)
                    applyDecision(
                        CommitmentRestorePolicy.decide(
                            local = local,
                            server = read.state,
                            nowMs = System.currentTimeMillis(),
                            oneDayMs = Timings.ONE_DAY_MS
                        ),
                        local
                    )
                    return
                }
                CommitmentRead.NoRecord -> {
                    Log.i(TAG, "No commitment recorded for this device")
                    _status.value = CommitmentRestoreStatus.NOTHING_OWED
                    return
                }
                is CommitmentRead.Unavailable -> {
                    Log.w(TAG, "Commitment lookup attempt $attempt failed: ${read.failure}")
                    // A denied or malformed answer will not heal by asking again; only connectivity
                    // does. Retrying a PERMISSION_DENIED five times would just hold the gate longer.
                    val isTransient = read.failure == CommitmentReadFailure.NETWORK ||
                        read.failure == CommitmentReadFailure.TIMEOUT
                    if (!isTransient || attempt >= Timings.COMMITMENT_RESTORE_MAX_ATTEMPTS) {
                        _status.value = CommitmentRestoreStatus.UNREACHABLE
                        return
                    }
                    _status.value = CommitmentRestoreStatus.RETRYING
                    delay(Timings.COMMITMENT_RESTORE_RETRY_DELAY_MS)
                }
            }
        }
    }

    private suspend fun applyDecision(
        decision: CommitmentRestoreDecision,
        local: CommitmentLockState
    ) {
        when (decision) {
            is CommitmentRestoreDecision.ReArmLocal -> {
                prefs.reArmRestoredTimedProtection(
                    days = decision.days,
                    expiryWallMs = decision.expiryWallMs,
                    repinUptimeAnchor = decision.repinUptimeAnchor
                )
                rememberMirrored(decision.days, decision.expiryWallMs)
                Log.i(TAG, "Commitment re-armed: ${decision.days}d left, repinned=${decision.repinUptimeAnchor}")
                _status.value = CommitmentRestoreStatus.PROTECTED
            }
            is CommitmentRestoreDecision.MirrorLocalToServer -> {
                pushUpward(decision.state)
                _status.value = CommitmentRestoreStatus.PROTECTED
            }
            CommitmentRestoreDecision.LocalAlreadyCoversServer -> {
                // The record is already satisfied locally, so the mirror watcher has nothing new to
                // push either — remembering avoids a redundant transaction on every cold start.
                rememberMirrored(local.days, local.expiryWallMs)
                Log.i(TAG, "Local lock already covers the device record")
                _status.value = CommitmentRestoreStatus.PROTECTED
            }
            CommitmentRestoreDecision.NothingToRestore -> {
                _status.value = CommitmentRestoreStatus.NOTHING_OWED
            }
        }
    }

    /** Observes the local timed lock and mirrors activations to the device record. */
    private fun mirrorLocalChanges(deviceId: String) {
        scope.launch {
            prefs.timedLockState(deviceId).collect { state -> mirrorIfNewAndOwed(state) }
        }
    }

    private suspend fun mirrorIfNewAndOwed(state: CommitmentLockState) {
        val nowMs = System.currentTimeMillis()
        if (!CommitmentRestorePolicy.isOwedAt(state, nowMs)) return
        if (state.days == lastMirroredDays && state.expiryWallMs == lastMirroredExpiryMs) return
        pushUpward(state)
    }

    private suspend fun pushUpward(state: CommitmentLockState) {
        when (commitmentLocks.publishStronger(state)) {
            is CommitmentWrite.Written -> {
                rememberMirrored(state.days, state.expiryWallMs)
                Log.i(TAG, "Commitment mirrored upward: days=${state.days}")
            }
            CommitmentWrite.AlreadyCurrent ->
                rememberMirrored(state.days, state.expiryWallMs)
            is CommitmentWrite.Failed ->
                Log.w(TAG, "Commitment mirror failed: ${state.days}d / ${state.expiryWallMs}")
        }
    }

    private fun rememberMirrored(days: Int, expiryWallMs: Long) {
        lastMirroredDays = days
        lastMirroredExpiryMs = expiryWallMs
    }
}
