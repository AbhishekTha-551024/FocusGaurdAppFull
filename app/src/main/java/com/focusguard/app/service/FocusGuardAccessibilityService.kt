package com.focusguard.app.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.focusguard.app.overlay.manager.OverlayBlockType
import com.focusguard.app.service.handlers.SystemGuardHost
import com.focusguard.app.service.handlers.TargetAppHost
import com.focusguard.app.service.runtime.FocusGuardServiceDeps
import com.focusguard.app.service.runtime.ServiceRuntime
import com.focusguard.app.service.utils.AccessibilityServiceDisabler
import com.focusguard.app.service.utils.OemScanWindowPolicy
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import javax.inject.Inject

private const val TAG = "FocusGuardService"

/**
 * Core Accessibility Service for FocusGuard.
 *
 * Intercepts foreground app changes, browser URLs, and settings navigation.
 *
 * Phase 5 split: component wiring, the event guard chain, lifecycle side effects, the
 * reconnect sweep and tamper handling live in [com.focusguard.app.service.runtime].
 * This class keeps only the Android overrides, the [TargetAppHost] / [SystemGuardHost]
 * contracts, the app-label cache and the process-wide companion state.
 */
@AndroidEntryPoint
class FocusGuardAccessibilityService : AccessibilityService(), TargetAppHost, SystemGuardHost {

    @Inject
    lateinit var deps: FocusGuardServiceDeps

    private val runtimeDelegate = lazy { ServiceRuntime(this, deps, scope) }
    private val runtime: ServiceRuntime by runtimeDelegate

    // In-memory app label cache to avoid expensive main-thread PackageManager IPC
    private val appLabelCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    private val coroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG, "Safely intercepted uncaught coroutine error: ${throwable.message}", throwable)
    }
    private val serviceJob = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Main + serviceJob + coroutineExceptionHandler)

    private fun getCachedAppLabel(pkg: String): String {
        appLabelCache[pkg]?.let { return it }
        return try {
            val appInfo = packageManager.getApplicationInfo(pkg, 0)
            packageManager.getApplicationLabel(appInfo).toString().also { appLabelCache[pkg] = it }
        } catch (ex: Exception) {
            // Do NOT cache the fallback: a package that is not resolvable yet (install in
            // progress, profile switch) must get its real label on a later event.
            pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    override fun onServiceConnected() {
        super.onServiceConnected()
        isServiceRunning = true
        runtime.lifecycle.onConnected()
    }

    override fun onInterrupt() {
        runtime.lifecycle.onInterrupt()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        isServiceRunning = false
        runtime.lifecycle.onUnbind()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        runtime.lifecycle.onDestroySideEffects()
        appLabelCache.clear()
        serviceJob.cancel()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        try {
            runtime.timestamps.lastAliveTimestamp = android.os.SystemClock.elapsedRealtime()
            runtime.router.process(event)
        } catch (t: Throwable) {
            // Must catch Exception and Error (including StackOverflowError) with zero async allocation in catch
            Log.e(TAG, "Crash-proof guard caught unhandled exception in onAccessibilityEvent: ${t.javaClass.simpleName} - ${t.message}", t)
        }
    }

    /**
     * The one gate for every global action this process is allowed to send.
     *
     * [AccessibilityService.performGlobalAction] is final, so it cannot be overridden — guards and
     * helpers must call this instead. Keep it that way: a direct `service.performGlobalAction(HOME)`
     * re-introduces the burst that makes Vivo's anti-tapjacking watchdog unbind the service, and an
     * unbound service protects nothing. See
     * [com.focusguard.app.service.utils.GlobalActionGate].
     */
    override fun sendGlobalAction(action: Int): Boolean =
        deps.globalActionGate.dispatch(action) { performGlobalAction(it) }

    override fun handleSecurityTamper(
        sourcePackage: String,
        reason: String,
        targetName: String,
        blockType: OverlayBlockType,
        customToastMessage: String?
    ) {
        runtime.tamperDelegate.handleSecurityTamper(
            sourcePackage = sourcePackage,
            reason = reason,
            targetName = targetName,
            blockType = blockType,
            customToastMessage = customToastMessage
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TargetAppHost / SystemGuardHost Implementation
    // ─────────────────────────────────────────────────────────────────────────

    override fun currentRoot(): AccessibilityNodeInfo? = rootInActiveWindow

    override fun cachedAppLabel(pkg: String): String = getCachedAppLabel(pkg)

    override fun forceCloseApp(pkg: String) {
        runtime.windowActions.forceCloseApp(pkg)
    }

    override fun context(): Context = this

    override fun service(): AccessibilityService = this

    override fun isOverlayShowing(): Boolean = deps.overlayManager.isOverlayShowing

    override fun triggerBlock(
        sourcePackage: String,
        reason: String,
        blockType: OverlayBlockType,
        targetName: String,
        description: String
    ) {
        runtime.blockTrigger.triggerBlock(
            sourcePackage = sourcePackage,
            reason = reason,
            blockType = blockType,
            targetName = targetName,
            description = description
        )
    }

    override fun isLauncherPackage(pkg: String): Boolean {
        val launcherPackage = deps.launcherDetector.getCurrentLauncherPackage()
        val lower = pkg.lowercase()
        return (launcherPackage != null && lower == launcherPackage.lowercase()) ||
                deps.launcherDetector.isLauncherPackage(pkg) ||
                lower.contains("launcher") ||
                lower.contains("quickstep") ||
                lower.contains("trebuchet") ||
                lower.contains("nexuslauncher") ||
                (lower.contains("home") && !lower.contains("googlequicksearchbox"))
    }

    /** Returns true when we are inside an OEM-initiated transient scan window. */
    override fun isOemScanWindowActive(): Boolean =
        android.os.SystemClock.elapsedRealtime() < oemScanSuppressUntilStatic.get()

    override fun onKeyEvent(event: KeyEvent?): Boolean {
        val handled = deps.keyEventHandler.handleKeyEvent(event, runtime.foregroundSessionTracker.currentPackage)
        if (handled != null) {
            return handled
        }
        return super.onKeyEvent(event)
    }

    /**
     * Zero-latency device admin check -- delegates to [DeviceAdminManager] cache refreshed every 30 s.
     * Never makes a Binder IPC call on the hot accessibility-event path (AGENTS Rule 5).
     */
    override fun isDeviceAdminActive(): Boolean = deps.deviceAdminManager.isDeviceAdminActive()

    companion object {
        /**
         * STATIC AtomicLong -- survives AccessibilityService instance restarts.
         *
         * Vivo/OEM force-unbinds and creates a NEW service instance when it scans settings.
         * An instance field resets to 0 on the new instance -- losing the suppression window.
         * This static field persists across instance restarts within the same process lifetime.
         *
         * Written only by [markOemScanActive]; read by [isOemScanWindowActive].
         */
        private val oemScanSuppressUntilStatic = java.util.concurrent.atomic.AtomicLong(0L)

        /**
         * Arms the OEM scan window for at least [windowMs] milliseconds (default
         * [com.focusguard.app.core.common.constants.Timings.OEM_SCAN_SUPPRESS_WINDOW_MS]).
         *
         * Called by [com.focusguard.app.service.runtime.AccessibilityServiceLifecycle.onConnected]
         * for the connects that can be mid-scan, and by GUARD 1.15a /
         * [com.focusguard.app.observer.AccessibilityStateObserver] when the OEM security engine is
         * caught clearing the setting. The window narrows the tamper RESPONSE (no HOME/BACK); it
         * never hides an attempt on FocusGuard's own accessibility service -- see
         * [com.focusguard.app.service.utils.TamperResponsePolicy].
         *
         * Arming extends an already-open window rather than replacing it, so a shorter arm can
         * never cancel the evidence of a longer one ([OemScanWindowPolicy.extendedUntilElapsed]).
         */
        fun markOemScanActive(
            windowMs: Long = com.focusguard.app.core.common.constants.Timings.OEM_SCAN_SUPPRESS_WINDOW_MS
        ) {
            val now = android.os.SystemClock.elapsedRealtime()
            val previousUntil = oemScanSuppressUntilStatic.get()
            val until = OemScanWindowPolicy.extendedUntilElapsed(now, previousUntil, windowMs)
            oemScanSuppressUntilStatic.set(until)
            Log.i(TAG_COMPANION, "OEM security scan window armed: +${windowMs}ms -> ${(until - now)}ms remaining (static) -- HOME/BACK responses suppressed")
        }

        private const val TAG_COMPANION = "FocusGuardService"
        @Volatile
        var isServiceRunning: Boolean = false
            private set

        @Volatile
        private var instanceRef: java.lang.ref.WeakReference<FocusGuardAccessibilityService>? = null

        /** Broadcast action sent from UI process when uninstall-protection toggle changes. */
        const val ACTION_REFRESH_CACHES = "com.focusguard.app.ACTION_REFRESH_CACHES"

        @Volatile
        var adminActivationAllowedUntil: Long = 0L

        fun allowAdminActivationWindow() {
            adminActivationAllowedUntil = android.os.SystemClock.elapsedRealtime() + 90_000L
            Log.d(TAG, "Admin activation temporary window allowed for 90s")
        }

        fun updateDeviceAdminCache(isActive: Boolean) {
            instanceRef?.get()?.deps?.deviceAdminManager?.updateCache(isActive)
            Log.d(TAG, "Device admin cache explicitly updated: $isActive")
        }

        /** Publishes the live service instance used by [updateDeviceAdminCache] and [disableAccessibilityService]. */
        internal fun bindInstance(service: FocusGuardAccessibilityService) {
            instanceRef = java.lang.ref.WeakReference(service)
        }

        /** Clears the live service instance on destroy. */
        internal fun unbindInstance() {
            instanceRef = null
        }

        /**
         * Safely disables the Accessibility Service so the app can be uninstalled cleanly.
         */
        fun disableAccessibilityService(context: Context) {
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    instanceRef?.get()?.disableSelf()
                    Log.i(TAG_COMPANION, "disableSelf() called on FocusGuardAccessibilityService")
                }
            } catch (e: Exception) {
                Log.w(TAG_COMPANION, "disableSelf() failed: ${e.message}")
            }

            AccessibilityServiceDisabler.removeFromEnabledServices(
                context,
                FocusGuardAccessibilityService::class.java.name
            )
        }
    }
}
