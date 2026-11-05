package com.focusguard.app.core.startup

import android.content.Context
import android.util.Log
import com.focusguard.app.core.datastore.DirectBootStorage
import com.focusguard.app.core.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "FG:Recovery"

/** Sleep between two unlock probes while the device is still locked. */
private const val POLL_INTERVAL_MS = 500L

/**
 * Locked probes between two "still waiting" log lines.
 * At [POLL_INTERVAL_MS] this is 30 seconds of waiting per log line.
 */
private const val POLLS_BETWEEN_LOGS = 12

/** What the poller does after one unlock probe. */
internal enum class GateAction { STOP_POLLING, POLL, POLL_AND_LOG }

/**
 * Pure polling decision, free of Android and coroutines so it can be unit-tested.
 *
 * The gate never gives up: a locked device has no readable CE storage, and releasing the waiters
 * anyway would re-create the crash this class exists to prevent. Unblocking on a timeout would be
 * fail-open into process death, so the only exits are "unlocked" or the process ending.
 */
internal fun nextGateAction(storageReady: Boolean, lockedPollsSinceLog: Int): GateAction = when {
    storageReady -> GateAction.STOP_POLLING
    lockedPollsSinceLog >= POLLS_BETWEEN_LOGS -> GateAction.POLL_AND_LOG
    else -> GateAction.POLL
}

/**
 * CeStorageGate
 * =============
 * Single choke point that keeps every credential-encrypted (CE) storage read away from the
 * Direct Boot window.
 *
 * After a reboot Android keeps CE storage locked until the user unlocks the device for the first
 * time. Room and DataStore files under /data/user/0/ do not exist yet, so the first query throws
 * SQLiteCantOpenDatabaseException. Repository cache warmers run inside `init { }`, which means that
 * exception arrives while Hilt is building the graph for a broadcast receiver or an Accessibility
 * Service bind, on a background coroutine with no parent handler. It kills the whole process, and
 * the OS re-binding the service starts the crash again: measured on Vivo V2355 as two fatal
 * exceptions per boot, with Settings still reporting protection ON while nothing was alive to
 * enforce it.
 *
 * Collectors that touch CE storage call [awaitUnlocked] first. Once unlocked, the call returns
 * immediately and behaviour is exactly what it was before this class existed.
 */
@Singleton
class CeStorageGate @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {

    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)

    /** CE storage readable (or not yet known to be locked). Only mutated by the poller. */
    private val storageReady = MutableStateFlow(DirectBootStorage.isUserUnlocked(context))

    init {
        if (!storageReady.value) {
            Log.i(TAG, "CE storage locked at Direct Boot: gating cache warmers until first unlock.")
            scope.launch { pollUntilUnlocked() }
        }
    }

    /**
     * Suspends until CE storage is readable, then returns normally. Never throws.
     * Safe to call from any coroutine; returns without suspending in the unlocked case,
     * which is every launch outside Direct Boot.
     */
    suspend fun awaitUnlocked() {
        if (storageReady.value) return
        storageReady.first { it }
    }

    private suspend fun pollUntilUnlocked() {
        var lockedPollsSinceLog = 0
        while (true) {
            delay(POLL_INTERVAL_MS)
            val ready = DirectBootStorage.isUserUnlocked(context)
            when (nextGateAction(ready, lockedPollsSinceLog)) {
                GateAction.STOP_POLLING -> {
                    storageReady.value = true
                    Log.i(TAG, "CE storage unlocked after $lockedPollsSinceLog polls: cache warmers released.")
                    return
                }
                GateAction.POLL -> lockedPollsSinceLog++
                GateAction.POLL_AND_LOG -> {
                    lockedPollsSinceLog = 0
                    Log.i(TAG, "CE storage still locked (waiting since boot); collectors not started.")
                }
            }
        }
    }
}
