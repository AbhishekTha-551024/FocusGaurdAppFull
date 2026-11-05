package com.focusguard.app.commitment

import com.focusguard.app.core.common.constants.Timings
import com.focusguard.app.domain.model.CommitmentLockState
import com.focusguard.app.domain.repository.CommitmentLockRepository
import com.focusguard.app.domain.repository.CommitmentRead
import com.focusguard.app.domain.repository.CommitmentWrite
import com.focusguard.app.domain.repository.DeviceIdentityRepository
import com.focusguard.app.domain.repository.LocalCommitmentStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Hand-written doubles for the restore engine's three ports.
 *
 * No mocking framework is used (none is on the test classpath) and none is needed: every port has
 * three or fewer members. The doubles record what the engine did so the tests can assert on it.
 */

const val TEST_DEVICE_ID = "abcdef0123456789"

/** Wall clock the engine itself uses, so fixtures are always built relative to the same "now". */
fun nowMs(): Long = System.currentTimeMillis()

fun dayMs(): Long = Timings.ONE_DAY_MS

/** A timed lock that ends in [daysFromNow] whole days (negative = already expired). */
fun lockState(
    deviceId: String = TEST_DEVICE_ID,
    active: Boolean = true,
    days: Int,
    expiresInMs: Long
): CommitmentLockState = CommitmentLockState(
    deviceId = deviceId,
    isTimedLockActive = active,
    days = days,
    expiryWallMs = if (days <= 0) 0L else nowMs() + expiresInMs,
    recordedAtMs = nowMs()
)

class FakeDeviceIdentity(private val id: String) : DeviceIdentityRepository {
    override fun reinstallStableId(): String = id
}

/**
 * Answers reads from [outcomes] in order; the last entry sticks, so a single-value list repeats.
 * Set [throwOnRead] to simulate a port that explodes instead of failing cleanly.
 */
class FakeCommitmentLockRepository(
    private vararg val outcomes: CommitmentRead,
    var writeOutcome: CommitmentWrite = CommitmentWrite.Written
) : CommitmentLockRepository {

    val readIds = mutableListOf<String>()
    val writes = mutableListOf<CommitmentLockState>()
    var throwOnRead: Throwable? = null

    override suspend fun read(deviceId: String): CommitmentRead {
        throwOnRead?.let { throw it }
        readIds += deviceId
        val index = (readIds.size - 1).coerceAtMost(outcomes.size - 1)
        return outcomes[index]
    }

    override suspend fun publishStronger(state: CommitmentLockState): CommitmentWrite {
        writes += state
        return writeOutcome
    }
}

/**
 * In-memory stand-in for the local half of the comparison.
 *
 * All three observables derive from one [snapshot], so the engine's `combine` never sees a torn
 * (enabled, days, expiry) triple that could not exist on a real device.
 */
class FakeLocalCommitmentStore(
    initial: CommitmentLockState = CommitmentLockState(
        deviceId = TEST_DEVICE_ID,
        isTimedLockActive = false,
        days = 0,
        expiryWallMs = 0L,
        recordedAtMs = 0L
    )
) : LocalCommitmentStore {

    private val snapshot = MutableStateFlow(initial)

    /** (days, expiryWallMs, repinUptimeAnchor) in the order the engine applied them. */
    val reArms = mutableListOf<Triple<Int, Long, Boolean>>()

    override fun timedLockState(deviceId: String): Flow<CommitmentLockState> =
        snapshot.map { it.copy(deviceId = deviceId) }

    override fun localCommitmentState(deviceId: String): CommitmentLockState =
        snapshot.value.copy(deviceId = deviceId)

    override suspend fun reArmRestoredTimedProtection(
        days: Int,
        expiryWallMs: Long,
        repinUptimeAnchor: Boolean
    ) {
        reArms += Triple(days, expiryWallMs, repinUptimeAnchor)
        snapshot.value = CommitmentLockState(
            deviceId = snapshot.value.deviceId,
            isTimedLockActive = true,
            days = days,
            expiryWallMs = expiryWallMs,
            recordedAtMs = nowMs()
        )
    }

    /** Simulates the user arming or extending a lock in Settings. */
    fun armLocally(days: Int, expiresInMs: Long) {
        snapshot.value = lockState(days = days, expiresInMs = expiresInMs)
    }

    /** Simulates a released lock: the mirror must never push this upward. */
    fun releaseLocally() {
        snapshot.value = CommitmentLockState(
            deviceId = snapshot.value.deviceId,
            isTimedLockActive = false,
            days = 0,
            expiryWallMs = 0L,
            recordedAtMs = nowMs()
        )
    }

    /** Re-emits the identical state, the way a DataStore replay does on every cold start. */
    fun replayIdentical() {
        val current = snapshot.value
        snapshot.value = current.copy(recordedAtMs = current.recordedAtMs + 1L)
    }
}
