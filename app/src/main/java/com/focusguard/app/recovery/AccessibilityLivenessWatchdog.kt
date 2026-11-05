package com.focusguard.app.recovery

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.Context
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import com.focusguard.app.core.datastore.DirectBootStorage
import java.lang.ref.WeakReference
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "FG:Recovery"

/** How often the in-process probe runs while the service is connected. */
private const val PROBE_INTERVAL_MS = 15_000L

/** Consecutive dead probes required before a forced rebind. One hit can be a transient window gap. */
private const val DEAD_PROBES_BEFORE_REBIND = 2

/**
 * A dead probe only counts once the event stream has also been silent this long. A truly bound
 * service always receives events while the user interacts; a static screen legitimately produces none.
 */
private const val EVENT_SILENCE_MS = 45_000L

/** Gap between removing our component and adding it back. */
private const val REBIND_GAP_MS = 700L

/** Minimum gap between two forced rebinds, measured on elapsedRealtime. */
private const val REBIND_COOLDOWN_MS = 30_000L

/** Forced rebinds allowed per rolling window before the watchdog backs off. */
private const val MAX_REBINDS_PER_WINDOW = 5
private const val REBIND_WINDOW_MS = 10 * 60_000L

/** Watchdog bookkeeping. Lives in DE storage so it is readable across service instances and reboots. */
private const val KEY_PENDING_READD = "pending_readd"
private const val KEY_LAST_REBIND_AT = "last_rebind_at"
private const val KEY_REBINDS_IN_WINDOW = "rebinds_in_window"
private const val KEY_WINDOW_STARTED_AT = "window_started_at"

/**
 * AccessibilityLivenessWatchdog
 * =============================
 * Detects and repairs the "listed but inert" state Vivo leaves behind after it force-unbinds the
 * service: `enabled_accessibility_services` still names FocusGuard, `dumpsys accessibility` still
 * reports it bound, the process is alive — but no events are ever delivered again.
 *
 * Neither [AccessibilityRecoveryHelper.restoreProtectionFast] (early-returns on
 * [AccessibilityStatusChecker.isAccessibilityHealthy]) nor
 * [ProgrammaticAccessibilityEnabler.tryEnableAccessibilityProgrammatically] (returns true without
 * writing while the 5 s kick cooldown is hot) can repair that state. The only proven repair is a
 * deliberate remove-and-re-add of the component, which makes system_server rebind from scratch.
 *
 * The probe runs on its own daemon thread (never the accessibility thread) and is strictly gated by
 * [AccessibilityRecoveryHelper.isProtectionActive]: with uninstall protection OFF nothing is touched.
 */
@Singleton
class AccessibilityLivenessWatchdog @Inject constructor() {

    private val executor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "fg-a11y-watchdog").apply { isDaemon = true }
    }

    @Volatile
    private var serviceRef: WeakReference<AccessibilityService>? = null

    @Volatile
    private var lastAliveProvider: (() -> Long)? = null

    @Volatile
    private var tickFuture: ScheduledFuture<*>? = null

    /** Mutated only on the watchdog thread. */
    private var consecutiveDeadProbes = 0

    /**
     * Starts (or restarts) the periodic probe for [service]. [lastAliveProvider] must return the
     * monotonic timestamp of the most recent accessibility event handled by this service.
     */
    fun start(service: AccessibilityService, lastAliveProvider: () -> Long) {
        stop()
        serviceRef = WeakReference(service)
        this.lastAliveProvider = lastAliveProvider
        consecutiveDeadProbes = 0
        executor.execute { completeInterruptedReAdd(service.applicationContext) }
        tickFuture = executor.scheduleWithFixedDelay(
            { safeTick() },
            PROBE_INTERVAL_MS,
            PROBE_INTERVAL_MS,
            TimeUnit.MILLISECONDS
        )
        Log.i(TAG, "Accessibility liveness watchdog started (probe every ${PROBE_INTERVAL_MS}ms).")
    }

    /**
     * Detaches from the service instance. The executor is intentionally NOT shut down: a rebind that
     * already started must finish even though the OS tears down this service in the meantime.
     */
    fun stop() {
        tickFuture?.cancel(false)
        tickFuture = null
        serviceRef = null
        lastAliveProvider = null
        consecutiveDeadProbes = 0
    }

    private fun safeTick() {
        try {
            val service = serviceRef?.get() ?: return
            val lastAlive = lastAliveProvider ?: return
            val context = service.applicationContext

            if (AccessibilityStatusChecker.isDirectBootLocked(context)) return

            // Invariant Rule 3: never repair while uninstall protection is OFF.
            if (!AccessibilityRecoveryHelper.isProtectionActive(context)) {
                consecutiveDeadProbes = 0
                return
            }

            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
            if (!powerManager.isInteractive) {
                consecutiveDeadProbes = 0
                return
            }
            val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            if (keyguardManager?.isKeyguardLocked == true) {
                consecutiveDeadProbes = 0
                return
            }

            if (isBinderResponsive(service)) {
                if (consecutiveDeadProbes > 0) {
                    Log.i(TAG, "Accessibility binder responsive again; probe counter reset.")
                }
                consecutiveDeadProbes = 0
                return
            }

            val silentForMs = SystemClock.elapsedRealtime() - lastAlive()
            if (silentForMs < EVENT_SILENCE_MS) return

            consecutiveDeadProbes++
            Log.w(
                TAG,
                "A11y probe failed ($consecutiveDeadProbes/$DEAD_PROBES_BEFORE_REBIND), events silent ${silentForMs}ms while interactive."
            )
            if (consecutiveDeadProbes >= DEAD_PROBES_BEFORE_REBIND) {
                consecutiveDeadProbes = 0
                forceRebind(context)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "A11y watchdog tick failed: ${t.javaClass.simpleName} - ${t.message}")
        }
    }

    /**
     * A live accessibility connection can always describe the interactive window tree. When
     * system_server has silently dropped the binding, both calls come back empty.
     */
    private fun isBinderResponsive(service: AccessibilityService): Boolean {
        return try {
            if (service.rootInActiveWindow != null) return true
            !service.windows.isNullOrEmpty()
        } catch (t: Throwable) {
            false
        }
    }

    private fun forceRebind(context: Context) {
        val now = SystemClock.elapsedRealtime()
        val prefs = DirectBootStorage.deCache(context)

        if (!AccessibilityStatusChecker.canWriteSecureSettings(context)) {
            Log.w(TAG, "Cannot force rebind: WRITE_SECURE_SETTINGS is not granted.")
            return
        }

        val lastRebindAt = prefs.getLong(KEY_LAST_REBIND_AT, 0L)
        if (lastRebindAt > 0L && now >= lastRebindAt && now - lastRebindAt < REBIND_COOLDOWN_MS) {
            Log.d(TAG, "Forced rebind skipped: ${now - lastRebindAt}ms since the last one.")
            return
        }

        val windowStart = prefs.getLong(KEY_WINDOW_STARTED_AT, 0L)
        val inWindow = if (windowStart > 0L && now >= windowStart && now - windowStart < REBIND_WINDOW_MS) {
            prefs.getInt(KEY_REBINDS_IN_WINDOW, 0)
        } else {
            0
        }
        if (inWindow >= MAX_REBINDS_PER_WINDOW) {
            Log.w(TAG, "Forced rebind budget exhausted ($inWindow/$MAX_REBINDS_PER_WINDOW); backing off.")
            return
        }

        try {
            val resolver = context.contentResolver
            val target = AccessibilityStatusChecker.targetComponent(context)
            val shortTarget = AccessibilityStatusChecker.shortComponent(context)
            val currentServices = Settings.Secure.getString(
                resolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ).orEmpty()

            if (currentServices.contains(target) || currentServices.contains(shortTarget)) {
                EnabledServicesMirror.save(context, currentServices)
            }

            prefs.edit()
                .putLong(KEY_LAST_REBIND_AT, now)
                .putLong(KEY_WINDOW_STARTED_AT, if (inWindow == 0) now else windowStart)
                .putInt(KEY_REBINDS_IN_WINDOW, inWindow + 1)
                .putBoolean(KEY_PENDING_READD, true)
                .apply()

            Log.w(TAG, "Forcing accessibility rebind (remove + re-add) to revive event delivery.")
            Settings.Secure.putString(
                resolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                removeComponent(currentServices, target, shortTarget)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Forced rebind removal failed: ${e.message}", e)
            return
        }

        try {
            Thread.sleep(REBIND_GAP_MS)
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        completeInterruptedReAdd(context)
    }

    /**
     * Restores FocusGuard in the enabled-services list. Also runs at start so a re-add interrupted by
     * process death is finished on the next connection instead of leaving accessibility permanently off.
     */
    private fun completeInterruptedReAdd(context: Context) {
        try {
            val prefs = DirectBootStorage.deCache(context)
            if (!prefs.getBoolean(KEY_PENDING_READD, false)) return
            if (!AccessibilityRecoveryHelper.isProtectionActive(context)) {
                prefs.edit().putBoolean(KEY_PENDING_READD, false).apply()
                return
            }

            val resolver = context.contentResolver
            val target = AccessibilityStatusChecker.targetComponent(context)
            val currentServices = Settings.Secure.getString(
                resolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ).orEmpty()
            Settings.Secure.putString(
                resolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                ProgrammaticAccessibilityEnabler.mergeEnabledServices(
                    currentServices = currentServices,
                    preservedServices = EnabledServicesMirror.load(context),
                    targetComponent = target
                )
            )
            Settings.Secure.putString(resolver, Settings.Secure.ACCESSIBILITY_ENABLED, "1")
            prefs.edit().putBoolean(KEY_PENDING_READD, false).apply()
            Log.i(TAG, "Accessibility rebind completed; FocusGuard re-added to secure settings.")
        } catch (e: Exception) {
            Log.e(TAG, "Accessibility rebind re-add failed: ${e.message}", e)
        }
    }

    companion object {
        /**
         * Delegates to the single list-editing helper so the forced-rebind path and the deliberate
         * uninstall path can never disagree about what "remove FocusGuard" means.
         */
        fun removeComponent(currentServices: String?, target: String, shortTarget: String): String =
            ProgrammaticAccessibilityEnabler.removeComponent(currentServices, target, shortTarget)
    }
}
