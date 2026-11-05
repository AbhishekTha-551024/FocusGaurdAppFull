package com.focusguard.app.service.runtime

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.focusguard.app.core.database.entity.EventType
import com.focusguard.app.service.FocusGuardAccessibilityService
import com.focusguard.app.service.utils.OemScanWindowPolicy
import kotlinx.coroutines.launch

private const val TAG = "FocusGuardService"

/**
 * Lifecycle side effects for [FocusGuardAccessibilityService].
 *
 * Phase 5 split: `onServiceConnected`, CE/DE cache initialization and the unbind/destroy
 * teardown bodies moved here verbatim. The service keeps the Android overrides and calls
 * these methods in the same order as before.
 */
internal class AccessibilityServiceLifecycle(
    private val service: FocusGuardAccessibilityService,
    private val runtime: ServiceRuntime
) {
    private val deps get() = runtime.deps

    private var isCeStorageInitialized = false

    fun onConnected() {
        com.focusguard.app.service.utils.FgDiagnostics.logLifecycleEvent(
            service, "onServiceConnected", deps.globalActionGate.lastNavigationSentElapsed
        )
        FocusGuardAccessibilityService.bindInstance(service)
        deps.startupReadiness.beginAccessibilityConnection()
        Log.i(TAG, "AccessibilityService connected.")
        val now = android.os.SystemClock.elapsedRealtime()
        val elapsedSinceLastAlive = if (runtime.timestamps.lastAliveTimestamp > 0L) now - runtime.timestamps.lastAliveTimestamp else 0L
        val isUnexpectedRestart = elapsedSinceLastAlive in 1..45_000L
        val eventType = if (isUnexpectedRestart) EventType.SERVICE_RESTARTED_UNEXPECTEDLY else EventType.SERVICE_CONNECTED
        val lastPkg = runtime.foregroundSessionTracker.currentPackage ?: "unknown"
        runtime.scope.launch {
            deps.repository.logEvent(
                eventType,
                "Service connected. Elapsed: ${elapsedSinceLastAlive}ms | LastFgPkg: $lastPkg"
            )
        }
        runtime.timestamps.lastAliveTimestamp = now
        // Vivo can leave us listed and "bound" while never delivering another event. Probe the
        // binder in-process and force a real rebind when it goes inert (protection ON only).
        deps.livenessWatchdog.start(service) { runtime.timestamps.lastAliveTimestamp }
        deps.overlayManager.setServiceContext(service)
        com.focusguard.app.worker.AccessibilityHealthWorker.cancelReactivationNotification(service)
        // ── SETTINGS COOLDOWN: ONLY FOR CONNECTS THAT CAN BE MID-SCAN ───────
        // On an OEM-forced connect (or a manual re-enable while the user is still on the
        // Accessibility screen) the service reconnects while that Settings window is still up.
        // The very first accessibility event (arrives in < 100ms) is from that same
        // Settings screen. Without a cooldown, GUARD 4.1 fires handleSecurityTamper
        // immediately, sends HOME, and triggers Vivo's anti-tapjacking watchdog.
        //
        // The window only changes WHICH response runs (see TamperResponsePolicy): an attempt on
        // our own accessibility toggle is still detected and still contested, just without a
        // global action. It used to be 10-12s and to swallow everything, which meant entering
        // Settings handed the user a free window on a plate -- blanking the setting is itself
        // what causes this connect.
        //
        // It is no longer armed on EVERY connect. A connect is not evidence of a scan -- it also
        // happens on boot, after an app update and after every programmatic heal -- and each
        // millisecond the window is open is a millisecond in which the cloning, hide-apps and
        // private-space guards answer SILENT. The scanner's real fingerprints (GUARD 1.15a and
        // AccessibilityStateObserver) still arm the full window on their own evidence.
        val foregroundPackage = readActiveWindowPackage()
        if (OemScanWindowPolicy.shouldArmOnConnect(foregroundPackage, isUnexpectedRestart)) {
            val windowMs = com.focusguard.app.core.common.constants.Timings.POST_CONNECT_OEM_SCAN_WINDOW_MS
            FocusGuardAccessibilityService.markOemScanActive(windowMs)
            Log.i(TAG, "onServiceConnected: post-connect OEM scan arm +${windowMs}ms (fg=$foregroundPackage, isUnexpectedRestart=$isUnexpectedRestart)")
        } else {
            Log.i(TAG, "onServiceConnected: post-connect blindfold skipped (fg=$foregroundPackage is not a Settings/OEM screen)")
        }

        deps.serviceReceiverManager.registerEarlyReceivers(service)

        // Pre-populate device admin cache and start 30s background refresh (AGENTS Rule 5)
        deps.deviceAdminManager.startPeriodicRefresh(runtime.scope)
        val isDirectBoot = com.focusguard.app.recovery.AccessibilityRecoveryHelper.isDirectBootLocked(service)
        if (isDirectBoot) {
            Log.i(TAG, "Direct Boot detected during onServiceConnected -- loading caches from DE storage.")
            loadDirectBootDeCaches()
            deps.serviceReceiverManager.registerUserUnlockedReceiver(service) {
                initCeStorageAndObservers()
            }
        } else {
            initCeStorageAndObservers()
        }

        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                    AccessibilityEvent.TYPE_VIEW_CLICKED or
                    AccessibilityEvent.TYPE_VIEW_LONG_CLICKED or
                    AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED or
                    // SECURITY FIX (AGENTS Rule 8): TYPE_WINDOWS_CHANGED is required so that
                    // MultiWindowGuard.checkAndInterceptMultiWindow() actually receives split-screen
                    // and PiP window events. Without this flag the OS never delivers these events
                    // and the entire multi-window block path is silently bypassed.
                    AccessibilityEvent.TYPE_WINDOWS_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
            notificationTimeout =
                com.focusguard.app.core.common.constants.Timings.A11Y_EVENT_BATCH_WINDOW_MS
        }
        service.serviceInfo = info

        deps.serviceReceiverManager.registerNewAppReceiver(service)

        // SWEEP ON RECONNECT: Delayed by 3s on every connect.
        // Live events arrive within < 100ms -- the OEM scan window keeps their response safe.
        // Sweep runs at 3s to catch any blocked app that was open BEFORE the service started.
        runtime.mainHandler.postDelayed({ runtime.sweep.sweepActiveWindowOnConnect() }, 3000L)
    }

    /**
     * One Binder read of the window the OS considers active at connect time, used only to scope the
     * post-connect blindfold ([OemScanWindowPolicy]). This is not the accessibility-event path: it
     * runs once per connect, and it reads no Room or DataStore state (AGENTS invariant 3).
     * A null result means "no window yet / unreadable", which the policy treats as unknown.
     */
    private fun readActiveWindowPackage(): String? {
        val root = try {
            service.rootInActiveWindow
        } catch (t: Throwable) {
            null
        } ?: return null
        return try {
            root.packageName?.toString()
        } catch (t: Throwable) {
            null
        } finally {
            com.focusguard.app.utils.SafeNodeUtils.safeRecycle(root)
        }
    }

    private fun initCeStorageAndObservers() {
        if (isCeStorageInitialized) return
        isCeStorageInitialized = true
        Log.i(TAG, "Initializing CE storage observers (Room, DataStore, Sessions).")

        // Start unified RulesSnapshot synchronizer
        deps.rulesSnapshotSynchronizer.start(runtime.scope)

        // Force-sync uninstall protection from DataStore to eliminate startup race window
        deps.repository.refreshUninstallProtectionNow()


        // The Settings toggle detector lives in FocusGuardApplication (process-level) so it
        // survives this service being unbound — the exact state it exists to detect. Registering
        // a second copy here made every disable trigger two recovery paths at once.

        // Reactively observe Room blocked apps to keep memory cache synchronized
        runtime.scope.launch {
            deps.repository.blockedApps.collect { list ->
                runtime.blockedPackages.clear()
                runtime.blockedPackages.addAll(list.map { it.packageName.lowercase() })
                runtime.blockedPackages.addAll(list.map { it.packageName })
                Log.i(TAG, "Blocked apps cache synchronized: ${runtime.blockedPackages.size} apps blocked")
            }
        }

        // Eagerly refresh exceeded time limits cache
        deps.repository.refreshExceededTimeLimits()

        // Immediately synchronize in-memory blocked keywords
        val fastKeywords = deps.repository.getFastBlockedKeywords()
        if (fastKeywords.isNotEmpty()) {
            deps.keywordBlocker.updateKeywords(fastKeywords)
            Log.i(TAG, "Fast blocked keywords pre-populated: ${fastKeywords.size} keywords")
        }

        // Reactively observe Room blocked keywords & domains
        runtime.scope.launch {
            deps.repository.blockedKeywordsSet.collect { keywords ->
                deps.keywordBlocker.updateKeywords(keywords)
                Log.i(TAG, "Blocked keywords synchronized: ${keywords.size} keywords active")
            }
        }

        // Reactively observe Deep Focus Lockdown state to auto-mount lock screen on session start
        if (deps.sessionBlockManager.isSessionCurrentlyActive()) {
            val currentPkg = runtime.foregroundSessionTracker.currentPackage
            val isAllowed = currentPkg != null && (deps.sessionBlockManager.isAlwaysAllowedPackage(currentPkg) || deps.sessionBlockManager.suitcasePackages.contains(currentPkg.lowercase()))
            if (!isAllowed) {
                com.focusguard.app.feature.session.ui.SessionLockActivity.start(service)
            }
        }

        runtime.scope.launch {
            deps.sessionBlockManager.sessionStateFlow.collect { state ->
                if (state.isActive) {
                    val currentPkg = runtime.foregroundSessionTracker.currentPackage
                    val isAllowed = currentPkg != null && (deps.sessionBlockManager.isAlwaysAllowedPackage(currentPkg) || deps.sessionBlockManager.suitcasePackages.contains(currentPkg.lowercase()))
                    if (!isAllowed) {
                        com.focusguard.app.feature.session.ui.SessionLockActivity.start(service)
                    }
                }
            }
        }
    }

    private fun loadDirectBootDeCaches() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                val deContext = service.createDeviceProtectedStorageContext()
                val dePrefs = deContext.getSharedPreferences("focusguard_de_cache", Context.MODE_PRIVATE)
                val deBlocked = dePrefs.getStringSet("blocked_packages", emptySet()) ?: emptySet()
                runtime.blockedPackages.clear()
                runtime.blockedPackages.addAll(deBlocked.map { it.lowercase() })
                runtime.blockedPackages.addAll(deBlocked)
                val isDeProtected = dePrefs.getBoolean("uninstall_protection_enabled", false)
                Log.i(TAG, "Direct Boot DE cache loaded: ${runtime.blockedPackages.size} packages, uninstallProtection=$isDeProtected")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load DE cache in Direct Boot: ${e.message}", e)
            }
        }
    }

    fun onInterrupt() {
        com.focusguard.app.service.utils.FgDiagnostics.logLifecycleEvent(
            service, "onInterrupt", deps.globalActionGate.lastNavigationSentElapsed
        )
        Log.w(TAG, "AccessibilityService interrupted by system.")
        deps.browserHeartbeatTracker.stopHeartbeat()
        runtime.foregroundSessionTracker.cancelForegroundPolling()
        // Do NOT call clearServiceContext() here -- onInterrupt is temporary feedback interruption;
        // service is still alive and bound. Only onUnbind or onDestroy clears context.
        deps.overlayManager.setServiceContext(service)
    }

    fun onUnbind() {
        com.focusguard.app.service.utils.FgDiagnostics.logLifecycleEvent(
            service, "onUnbind", deps.globalActionGate.lastNavigationSentElapsed
        )
        Log.w(TAG, "AccessibilityService unbound.")
        deps.startupReadiness.markAccessibilityDisconnected()
        deps.browserHeartbeatTracker.stopHeartbeat()
        runtime.foregroundSessionTracker.cancelForegroundPolling()
        deps.overlayManager.clearServiceContext()
        deps.serviceReceiverManager.unregisterOnUnbind(service)
        // Shared gate: reads the device-protected mirror when the repository cache is still cold
        // (first unbind right after a reboot). A plain fast-cache read reported "OFF" there and
        // skipped the heal entirely.
        val isProtectionActive = com.focusguard.app.recovery.AccessibilityRecoveryHelper
            .isProtectionActive(service.applicationContext, deps.repository)
        if (isProtectionActive) {
            try {
                com.focusguard.app.recovery.AccessibilityRecoveryHelper.restoreProtectionFast(
                    context = service.applicationContext,
                    source = "AccessibilityService:onUnbind",
                    repository = deps.repository
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error triggering recovery on unbind: ${e.message}")
            }
        } else {
            Log.i(TAG, "AccessibilityService: onUnbind called while uninstall protection is disabled. Allowing service to stop cleanly.")
            com.focusguard.app.worker.AccessibilityHealthWorker.cancelReactivationNotification(service.applicationContext)
        }
    }

    fun onDestroySideEffects() {
        com.focusguard.app.service.utils.FgDiagnostics.logLifecycleEvent(
            service, "onDestroy", deps.globalActionGate.lastNavigationSentElapsed
        )
        deps.browserHeartbeatTracker.stopHeartbeat()
        runtime.foregroundSessionTracker.cancelAll()
        deps.overlayManager.clearServiceContext()
        deps.serviceReceiverManager.unregisterAll(service)
        FocusGuardAccessibilityService.unbindInstance()
        deps.startupReadiness.markAccessibilityDisconnected()
        deps.mindfulPauseHandler.cancelAll()
        deps.autoBlockHandler.clearCache()
        deps.deviceAdminManager.stopPeriodicRefresh()
        deps.livenessWatchdog.stop()
    }
}
