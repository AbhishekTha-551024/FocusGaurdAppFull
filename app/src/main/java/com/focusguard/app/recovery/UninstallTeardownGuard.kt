package com.focusguard.app.recovery

import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.focusguard.app.core.common.constants.Timings
import com.focusguard.app.core.datastore.DirectBootStorage
import com.focusguard.app.core.datastore.FocusGuardPreferences
import com.focusguard.app.core.datastore.KEY_TIMED_LOCK_DAYS
import com.focusguard.app.core.datastore.KEY_TIMED_LOCK_ENABLED
import com.focusguard.app.core.datastore.KEY_TIMED_LOCK_EXPIRY
import com.focusguard.app.core.database.entity.EventType
import com.focusguard.app.core.di.IoDispatcher
import com.focusguard.app.data.repository.FocusGuardRepository
import com.focusguard.app.domain.policy.OwedTeardown
import com.focusguard.app.domain.policy.TeardownVerdict
import com.focusguard.app.domain.policy.UninstallTeardownPolicy
import com.focusguard.app.receiver.FocusGuardDeviceAdminReceiver
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "FG:Recovery"

/** Presence marker: monotonic stamp of the teardown, or [NO_MARKER] when none is outstanding. */
private const val KEY_REQUESTED_AT_ELAPSED = "uninstall_teardown_requested_elapsed"
private const val KEY_PROTECTION_WAS_ENABLED = "uninstall_teardown_protection_enabled"
private const val KEY_OWE_DAYS = "uninstall_teardown_days"
private const val KEY_OWE_EXPIRY = "uninstall_teardown_expiry"

/** Written only to device-protected storage: it must survive the reboot that follows a cancel. */
private const val NO_MARKER = -1L

/** Resolves the guard for the uninstall flow, which is driven from non-Hilt call sites. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface UninstallTeardownEntryPoint {
    fun uninstallTeardownGuard(): UninstallTeardownGuard
}

/**
 * UninstallTeardownGuard
 * ======================
 * Undoes the privilege teardown of an uninstall that never happened.
 *
 * Android refuses to uninstall an app that holds an active Device Administrator, so
 * [com.focusguard.app.utils.UninstallHelper] must strip Device Admin and Accessibility BEFORE the
 * system dialog is shown. Android also gives that dialog no result: the caller cannot learn whether
 * the user confirmed or cancelled. Before this class, Cancel was therefore the cheapest bypass in
 * the app — complete the challenge, press Cancel, and FocusGuard stayed installed with its admin,
 * its Accessibility Service and its Uninstall Protection flag all destroyed, while the
 * "protection OFF means no auto-recovery" rule politely kept them off.
 *
 * The fix is to make the teardown conditional on the outcome it was meant to serve: remember what is
 * about to be destroyed, and if this process is still alive after the system uninstaller has had
 * time to finish, put it back. A real uninstall kills the process, so the restore never runs.
 */
@Singleton
class UninstallTeardownGuard @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: FocusGuardPreferences,
    private val repository: FocusGuardRepository,
    private val adminLossResponder: AdminLossResponder,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val cancelCheckPosted = AtomicBoolean(false)
    private val restoreMutex = Mutex()

    /**
     * Records the protection state that is still readable right now. Idempotent: a marker written by
     * an earlier step (before the challenge disarmed protection) keeps its own, older stamp.
     *
     * @return true when a teardown is now being watched.
     */
    fun armBeforeTeardown(): Boolean {
        val cache = DirectBootStorage.deCache(context)
        if (readPending(cache) != null) return true
        val owed = OwedTeardown(
            protectionWasEnabled = cache.getBoolean(KEY_TIMED_LOCK_ENABLED, false),
            days = cache.getInt(KEY_TIMED_LOCK_DAYS, 0),
            expiryWallMs = cache.getLong(KEY_TIMED_LOCK_EXPIRY, 0L),
            requestedAtElapsedMs = SystemClock.elapsedRealtime()
        )
        if (!UninstallTeardownPolicy.shouldArm(owed.protectionWasEnabled)) {
            Log.i(TAG, "No Uninstall Protection armed before teardown: a cancelled uninstall restores nothing.")
            return false
        }
        writeMarker(cache, owed)
        Log.w(TAG, "Uninstall teardown watched: protection is re-asserted if the uninstall is cancelled.")
        return true
    }

    /** Starts the survival check. Safe to call repeatedly; only one check is in flight per process. */
    fun scheduleCancelCheck() {
        if (!cancelCheckPosted.compareAndSet(false, true)) return
        mainHandler.postDelayed({
            cancelCheckPosted.set(false)
            resumeIfPending()
        }, Timings.UNINSTALL_CANCEL_GRACE_MS)
    }

    /**
     * Drops an outstanding marker without restoring anything.
     *
     * Called when the user deliberately disables Uninstall Protection through the challenge instead
     * of uninstalling. Without this, a marker left over from an earlier cancelled uninstall would
     * re-arm protection on the next app start and silently undo the choice they just made.
     *
     * @return true when a watched teardown was dropped.
     */
    suspend fun discardPendingTeardown(): Boolean = restoreMutex.withLock {
        val cache = DirectBootStorage.deCache(context)
        val pending = readPending(cache) ?: return@withLock false
        clearMarker(cache)
        Log.i(TAG, "Outstanding uninstall teardown discarded: the user disabled Uninstall Protection on purpose.")
        true
    }

    /**
     * Runs the decision for an outstanding marker. Called after the grace window and again on the
     * next app start, because a marker written in a previous boot is already proof of a cancel.
     */
    fun resumeIfPending() {
        val cache = DirectBootStorage.deCache(context)
        val pending = readPending(cache) ?: return
        when (
            UninstallTeardownPolicy.decide(
                pending = pending,
                nowElapsedMs = SystemClock.elapsedRealtime(),
                graceMs = Timings.UNINSTALL_CANCEL_GRACE_MS
            )
        ) {
            TeardownVerdict.NOTHING_PENDING -> clearMarker(cache)
            TeardownVerdict.WAIT_FOR_UNINSTALLER -> scheduleCancelCheck()
            TeardownVerdict.RESTORE -> {
                Log.w(TAG, "Uninstall cancelled with the app still installed: re-asserting what the teardown destroyed.")
                scope.launch {
                    restoreMutex.withLock {
                        if (readPending(cache) != pending) return@withLock
                        if (restore(pending)) {
                            clearMarker(cache)
                        } else {
                            Log.w(TAG, "Cancelled-uninstall restore remains pending; retrying after the recovery delay.")
                            scheduleCancelCheck()
                        }
                    }
                }
            }
        }
    }

    private suspend fun restore(pending: OwedTeardown): Boolean {
        return try {
            if (pending.isTimedLock) {
                // The original deadline is kept. The monotonic arm stamp was cleared by the disarm,
                // so it is re-pinned: that can only make the lock stricter, never shorter.
                prefs.reArmRestoredTimedProtection(
                    days = pending.days,
                    expiryWallMs = pending.expiryWallMs,
                    repinUptimeAnchor = true
                )
            } else {
                repository.setUninstallProtectionEnabled(true)
            }
            repository.refreshUninstallProtectionNow()
            repository.logEvent(
                EventType.UNINSTALL_ATTEMPT,
                "Uninstall cancelled: Uninstall Protection re-armed after privilege teardown"
            )
            healAccessibility()
            adminLossResponder.respond(
                adminComponent = ComponentName(context, FocusGuardDeviceAdminReceiver::class.java),
                protectionEnabled = pending.protectionWasEnabled
            )
            true
        } catch (e: Exception) {
            Log.e(TAG, "Could not re-assert protection after a cancelled uninstall: ${e.message}", e)
            false
        }
    }

    /**
     * Silent re-enable only. The visible recovery flow is [AdminLossResponder]'s shield, which the
     * caller opens right after this, so the two never stack on one failure.
     */
    private fun healAccessibility() {
        if (AccessibilityRecoveryHelper.isAccessibilityHealthy(context)) return
        val healed = AccessibilityRecoveryHelper.tryEnableAccessibilityProgrammatically(context)
        Log.i(TAG, "Cancelled-uninstall accessibility heal: silent re-enable ${if (healed) "succeeded" else "unavailable"}.")
        if (healed) AccessibilityRecoveryScheduler.scheduleBindingVerification(context)
    }

    private fun readPending(cache: SharedPreferences): OwedTeardown? = try {
        val stamp = cache.getLong(KEY_REQUESTED_AT_ELAPSED, NO_MARKER)
        if (stamp == NO_MARKER) null else OwedTeardown(
            protectionWasEnabled = cache.getBoolean(KEY_PROTECTION_WAS_ENABLED, false),
            days = cache.getInt(KEY_OWE_DAYS, 0),
            expiryWallMs = cache.getLong(KEY_OWE_EXPIRY, 0L),
            requestedAtElapsedMs = stamp
        )
    } catch (e: Exception) {
        Log.w(TAG, "Pending uninstall teardown marker unreadable: ${e.message}")
        null
    }

    private fun writeMarker(cache: SharedPreferences, owed: OwedTeardown) {
        try {
            cache.edit()
                .putLong(KEY_REQUESTED_AT_ELAPSED, owed.requestedAtElapsedMs)
                .putBoolean(KEY_PROTECTION_WAS_ENABLED, owed.protectionWasEnabled)
                .putInt(KEY_OWE_DAYS, owed.days)
                .putLong(KEY_OWE_EXPIRY, owed.expiryWallMs)
                .apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist the uninstall teardown marker: ${e.message}", e)
        }
    }

    private fun clearMarker(cache: SharedPreferences) {
        try {
            cache.edit()
                .remove(KEY_REQUESTED_AT_ELAPSED)
                .remove(KEY_PROTECTION_WAS_ENABLED)
                .remove(KEY_OWE_DAYS)
                .remove(KEY_OWE_EXPIRY)
                .apply()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to clear the uninstall teardown marker: ${e.message}")
        }
    }

    companion object {
        /**
         * The guard reaches DataStore and the recovery UI, so it is only built once credential
         * storage is readable — the same Direct Boot hazard that killed the post-reboot protection.
         */
        private fun from(context: Context): UninstallTeardownGuard? {
            if (!DirectBootStorage.isUserUnlocked(context)) return null
            return try {
                EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    UninstallTeardownEntryPoint::class.java
                ).uninstallTeardownGuard()
            } catch (e: Exception) {
                Log.e(TAG, "Uninstall teardown guard unavailable: ${e.message}")
                null
            }
        }

        fun armBeforeTeardown(context: Context): Boolean = from(context)?.armBeforeTeardown() ?: false

        fun scheduleCancelCheck(context: Context) { from(context)?.scheduleCancelCheck() }

        fun resumeIfPending(context: Context) { from(context)?.resumeIfPending() }

        suspend fun discardPendingTeardown(context: Context): Boolean =
            from(context)?.discardPendingTeardown() ?: false
    }
}
