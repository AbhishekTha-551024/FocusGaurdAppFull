package com.focusguard.app.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import com.focusguard.app.blockers.AntiClonerEngine
import com.focusguard.app.blockers.KeywordBlocker
import com.focusguard.app.blockers.MultiWindowGuard
import com.focusguard.app.blockers.ReelBlocker
import com.focusguard.app.blockers.TimeTamperGuard
import com.focusguard.app.core.database.entity.EventType
import com.focusguard.app.data.repository.AccessDecision
import com.focusguard.app.data.repository.FocusGuardRepository
import com.focusguard.app.usage.ScheduleEvaluator
import com.focusguard.app.observer.AccessibilityStateObserver
import com.focusguard.app.overlay.manager.BlockOverlayManager
import com.focusguard.app.overlay.manager.OverlayBlockType
import com.focusguard.app.receiver.FocusGuardDeviceAdminReceiver
import com.focusguard.app.tracker.BrowserHeartbeatTracker
import com.focusguard.app.tracker.BrowserUrlTracker
import com.focusguard.app.usage.TimeLimitEvaluator
import com.focusguard.app.utils.SafeNodeUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArraySet
import javax.inject.Inject

private const val TAG = "FocusGuardService"

/**
 * Core Accessibility Service for FocusGuard.
 *
 * Intercepts foreground app changes, browser URLs, and settings navigation.
 */
@AndroidEntryPoint
class FocusGuardAccessibilityService : AccessibilityService() {

    @Inject
    lateinit var repository: FocusGuardRepository

    @Inject
    lateinit var overlayManager: BlockOverlayManager

    @Inject
    lateinit var keywordBlocker: KeywordBlocker

    @Inject
    lateinit var browserUrlTracker: BrowserUrlTracker

    @Inject
    lateinit var browserHeartbeatTracker: BrowserHeartbeatTracker

    @Inject
    lateinit var reelBlocker: com.focusguard.app.blockers.ReelBlocker

    @Inject
    lateinit var timeLimitEvaluator: TimeLimitEvaluator

    @Inject
    lateinit var scheduleEvaluator: ScheduleEvaluator

    @Inject
    lateinit var newAppInstallReceiver: com.focusguard.app.receiver.NewAppInstallReceiver

    @Inject
    lateinit var launcherDetector: com.focusguard.app.utils.LauncherDetector

    @Inject
    lateinit var mindfulRepository: com.focusguard.app.data.repository.MindfulRepository

    @Inject
    lateinit var sessionBlockManager: com.focusguard.app.blockers.SessionBlockManager

    @Inject
    lateinit var adultContentShield: com.focusguard.app.blockers.AdultContentShield

    @Inject
    lateinit var audioManagerHelper: com.focusguard.app.utils.AudioManagerHelper

    @Inject
    lateinit var blockDecisionEngine: com.focusguard.app.core.engine.BlockDecisionEngine

    @Inject
    lateinit var rulesSnapshotHolder: com.focusguard.app.core.engine.RulesSnapshotHolder

    @Inject
    lateinit var rulesSnapshotSynchronizer: com.focusguard.app.core.engine.RulesSnapshotSynchronizer

    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val mindfulExpiryRunnables = java.util.concurrent.ConcurrentHashMap<String, Runnable>()

    private val coroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG, "Safely intercepted uncaught coroutine error: ${throwable.message}", throwable)
    }
    private val serviceJob = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Main + serviceJob + coroutineExceptionHandler)

    // ── FIX 4: Cross-process REFRESH_CACHES broadcast receiver ──────────────
    // When the UI process (Main) toggles uninstall protection, it broadcasts
    // ACTION_REFRESH_CACHES so the :blocker_service process updates its AtomicBoolean
    // caches instantly — no 30-second DataStore polling lag.
    private val refreshCachesReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent?.action != ACTION_REFRESH_CACHES) return
            Log.d(TAG, "REFRESH_CACHES broadcast received — syncing all in-memory caches")
            repository.refreshUninstallProtectionNow()
            cachedDeviceAdminActive.set(isDeviceAdminActiveRaw())
        }
    }
    private var isRefreshCachesReceiverRegistered = false

    private val blockedPackages = CopyOnWriteArraySet<String>()

    private var currentForegroundPackage: String? = null
    private var currentForegroundStartTime: Long = 0L
    private var foregroundPollingJob: kotlinx.coroutines.Job? = null

    private var accessibilityStateObserver: AccessibilityStateObserver? = null

    // Debounce to avoid multi-triggering overlay in same transition
    private var lastBlockTimestamp: Long = 0L
    private var lastFocusGuardLongPressTimestamp: Long = 0L
    private var lastFocusGuardTouchTimestamp: Long = 0L
    private var lastTargetedAppLabel: String = ""
    private var lastDesktopFocusGuardBounds: android.graphics.Rect? = null
    private var lastDesktopFocusGuardTimestamp: Long = 0L
    private var lastShadeCollapseTimestamp: Long = 0L
    private var lastDeepFocusReassertTimestamp: Long = 0L
    private var lastServiceConnectedTimestamp: Long = 0L
    private var lastOverlayDismissTimestamp: Long = 0L
    private var lastDismissedPackage: String = ""
    private var lastAliveTimestamp: Long = 0L
    private var lastAdminTamperLogTimestamp: Long = 0L
    private var lastSettingsContentScanTimestamp: Long = 0L
    private var isNewAppReceiverRegistered = false

    // ── FIX FAST-SECOND-HOLD: After blocking a hold, re-arm for 4 s so rapid retries are caught ──
    // lastFocusGuardPopupBlockTimestamp records when we last blocked the popup.
    // If a new popup appears within 4 s of that block, we re-arm lastDesktopFocusGuardTimestamp
    // before the desktop-icon re-detection loop can fire, catching the second-fast-hold immediately.
    private var lastFocusGuardPopupBlockTimestamp: Long = 0L

    // ── PROACTIVE HOLD DETECTION: Arm the instant the FocusGuard icon disappears from desktop ──
    // When the user holds the FocusGuard icon, it moves into a "drag layer" and vanishes from the
    // findAccessibilityNodeInfosByText scan. We detect this disappearance IMMEDIATELY and arm this
    // timestamp. Block fires whether or not the popup renders (catches both slow and fast holds).
    private var lastFocusGuardPickedUpTimestamp: Long = 0L

    // ── FIX ACCESSIBILITY TOGGLE: Sticky timestamp when user is on FocusGuard's own a11y page ──
    // Records when the accessibility details page for FocusGuard was last detected.
    // If the service receives a click event while on this page (even the toggle switch),
    // we block it regardless of exact event text content.
    private var lastA11yFocusGuardPageTimestamp: Long = 0L

    // ── OEM SECURITY SCAN SUPPRESSION WINDOW ────────────────────────────────
    // oemScanSuppressUntil is now a STATIC companion field (see companion object below).
    // It must be static because Vivo force-unbinds and recreates a NEW service instance;
    // an instance field would reset to 0 on the new instance, losing the suppression window.
    // isOemScanWindowActive() reads from the static field, surviving instance restarts.

    /** Returns true when we are inside an OEM-initiated transient scan window. */
    private fun isOemScanWindowActive(): Boolean =
        android.os.SystemClock.elapsedRealtime() < oemScanSuppressUntilStatic.get()

    private val timeChangeReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            com.focusguard.app.blockers.TimeTamperGuard.onSystemTimeOrTimeZoneChanged()
        }
    }
    private var isTimeReceiverRegistered = false

    // ── AGENTS Rule 5: Cached Device Admin state — eliminates Binder IPC on every accessibility event (<0.01ms) ──
    // Refreshed once in onServiceConnected() and every 30s thereafter via a background coroutine.
    private val cachedDeviceAdminActive = java.util.concurrent.atomic.AtomicBoolean(false)
    private var adminRefreshJob: kotlinx.coroutines.Job? = null

    // In-memory app label cache to avoid expensive main-thread PackageManager IPC
    private val appLabelCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    // In-memory package install time cache to avoid expensive PackageManager IPC (<0.001ms)
    private val packageInstallTimeCache = java.util.concurrent.ConcurrentHashMap<String, Long>()

    private fun getCachedAppLabel(pkg: String): String {
        return appLabelCache.getOrPut(pkg) {
            try {
                val appInfo = packageManager.getApplicationInfo(pkg, 0)
                packageManager.getApplicationLabel(appInfo).toString()
            } catch (ex: Exception) {
                pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Lifecycle & Service Config
    // ─────────────────────────────────────────────────────────────────────────

    override fun onServiceConnected() {
        super.onServiceConnected()
        isServiceRunning = true
        instanceRef = java.lang.ref.WeakReference(this)
        Log.i(TAG, "AccessibilityService connected.")
        val now = android.os.SystemClock.elapsedRealtime()
        val elapsedSinceLastAlive = if (lastAliveTimestamp > 0L) now - lastAliveTimestamp else 0L
        val isUnexpectedRestart = elapsedSinceLastAlive in 1..45_000L
        val eventType = if (isUnexpectedRestart) EventType.SERVICE_RESTARTED_UNEXPECTEDLY else EventType.SERVICE_CONNECTED
        val lastPkg = currentForegroundPackage ?: "unknown"
        scope.launch {
            repository.logEvent(
                eventType,
                "Service connected. Elapsed: ${elapsedSinceLastAlive}ms | LastFgPkg: $lastPkg"
            )
        }
        lastServiceConnectedTimestamp = now
        lastAliveTimestamp = now
        overlayManager.setServiceContext(this)
        com.focusguard.app.worker.AccessibilityHealthWorker.cancelReactivationNotification(this)
        com.focusguard.app.service.BootCoverService.dismiss(this)

        // ── SETTINGS COOLDOWN ON EVERY CONNECT ──────────────────────────────
        // On EVERY connect (OEM-forced OR manual re-enable), the service reconnects
        // while the user is still on the Accessibility Settings screen.
        // The very first accessibility event (arrives in < 100ms) is from that same
        // Settings screen. Without a cooldown, GUARD 4.1 fires handleSecurityTamper
        // immediately, sends HOME, and triggers Vivo's anti-tapjacking watchdog.
        //
        // Arm 10 seconds on every connect: the user has time to navigate away from
        // Settings naturally. After 10s the guard works normally again.
        //
        // On unexpected OEM-forced reconnects (< 45s gap), extend to 12s since the
        // iManager scan itself might still be running.
        val cooldownMs = if (isUnexpectedRestart) 12_000L else 10_000L
        val scanUntil = android.os.SystemClock.elapsedRealtime() + cooldownMs
        oemScanSuppressUntilStatic.set(scanUntil)
        Log.i(TAG, "onServiceConnected: settings cooldown armed for ${cooldownMs}ms (isUnexpectedRestart=$isUnexpectedRestart)")

        if (!isTimeReceiverRegistered) {
            val filter = android.content.IntentFilter().apply {
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
            }
            registerReceiver(timeChangeReceiver, filter)
            isTimeReceiverRegistered = true
        }

        // Register cross-process cache-refresh receiver
        if (!isRefreshCachesReceiverRegistered) {
            val refreshFilter = android.content.IntentFilter(ACTION_REFRESH_CACHES)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    registerReceiver(refreshCachesReceiver, refreshFilter, Context.RECEIVER_NOT_EXPORTED)
                } else {
                    registerReceiver(refreshCachesReceiver, refreshFilter)
                }
                isRefreshCachesReceiverRegistered = true
                Log.d(TAG, "REFRESH_CACHES receiver registered.")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to register REFRESH_CACHES receiver", e)
            }
        }

        // Pre-populate device admin cache and start 30s background refresh (AGENTS Rule 5)
        cachedDeviceAdminActive.set(isDeviceAdminActiveRaw())
        adminRefreshJob?.cancel()
        adminRefreshJob = scope.launch {
            while (isActive) {
                delay(30_000L)
                cachedDeviceAdminActive.set(isDeviceAdminActiveRaw())
                Log.d(TAG, "Device admin cache refreshed: ${cachedDeviceAdminActive.get()}")
            }
        }
        val isDirectBoot = com.focusguard.app.recovery.AccessibilityRecoveryHelper.isDirectBootLocked(this)
        if (isDirectBoot) {
            Log.i(TAG, "Direct Boot detected during onServiceConnected — loading caches from DE storage.")
            loadDirectBootDeCaches()
            registerUserUnlockedReceiver()
        } else {
            initCeStorageAndObservers()
        }

        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                    AccessibilityEvent.TYPE_VIEW_CLICKED or
                    AccessibilityEvent.TYPE_VIEW_LONG_CLICKED or
                    AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED or
                    AccessibilityEvent.TYPE_VIEW_SCROLLED or
                    // SECURITY FIX (AGENTS Rule 8): TYPE_WINDOWS_CHANGED is required so that
                    // MultiWindowGuard.checkAndInterceptMultiWindow() actually receives split-screen
                    // and PiP window events. Without this flag the OS never delivers these events
                    // and the entire multi-window block path is silently bypassed.
                    AccessibilityEvent.TYPE_WINDOWS_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
            notificationTimeout = 10
        }
        this.serviceInfo = info

        // Dynamically register NewAppInstallReceiver so modern Android (API 26-35) delivers ACTION_PACKAGE_ADDED
        try {
            val packageFilter = android.content.IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addDataScheme("package")
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(newAppInstallReceiver, packageFilter, Context.RECEIVER_EXPORTED)
            } else {
                registerReceiver(newAppInstallReceiver, packageFilter)
            }
            isNewAppReceiverRegistered = true
            Log.i(TAG, "Dynamic NewAppInstallReceiver registered successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register dynamic NewAppInstallReceiver", e)
        }

        lastFocusGuardPickedUpTimestamp = 0L
        lastFocusGuardTouchTimestamp = 0L
        lastFocusGuardPopupBlockTimestamp = 0L

        // SWEEP ON RECONNECT: Delayed by 3s on every connect.
        // Live events arrive within < 100ms — the 10s cooldown handles them.
        // Sweep runs at 3s to catch any blocked app that was open BEFORE the service started.
        mainHandler.postDelayed({ sweepActiveWindowOnConnect() }, 3000L)
    }

    private var isCeStorageInitialized = false

    private fun initCeStorageAndObservers() {
        if (isCeStorageInitialized) return
        isCeStorageInitialized = true
        Log.i(TAG, "Initializing CE storage observers (Room, DataStore, Sessions).")

        // Start unified RulesSnapshot synchronizer
        rulesSnapshotSynchronizer.start(scope)

        // Force-sync uninstall protection from DataStore to eliminate startup race window
        repository.refreshUninstallProtectionNow()

        // BlockerHero architecture: No persistent foreground service / notification needed.
        try {
            ProtectionWatchdogService.stopService(this)
        } catch (e: Exception) {
            Log.d(TAG, "Watchdog stop exception: ${e.message}")
        }

        // Register ContentObserver for instant Settings toggle detection
        accessibilityStateObserver = AccessibilityStateObserver(this, repository, scope).also {
            it.register()
        }

        // Reactively observe Room blocked apps to keep memory cache synchronized
        scope.launch {
            repository.blockedApps.collect { list ->
                blockedPackages.clear()
                blockedPackages.addAll(list.map { it.packageName.lowercase() })
                blockedPackages.addAll(list.map { it.packageName })
                Log.i(TAG, "Blocked apps cache synchronized: ${blockedPackages.size} apps blocked")
            }
        }

        // Eagerly refresh exceeded time limits cache
        repository.refreshExceededTimeLimits()

        // Immediately synchronize in-memory blocked keywords
        val fastKeywords = repository.getFastBlockedKeywords()
        if (fastKeywords.isNotEmpty()) {
            keywordBlocker.updateKeywords(fastKeywords)
            Log.i(TAG, "Fast blocked keywords pre-populated: ${fastKeywords.size} keywords")
        }

        // Reactively observe Room blocked keywords & domains
        scope.launch {
            repository.blockedKeywordsSet.collect { keywords ->
                keywordBlocker.updateKeywords(keywords)
                Log.i(TAG, "Blocked keywords synchronized: ${keywords.size} keywords active")
            }
        }

        // Reactively observe Deep Focus Lockdown state to auto-mount lock screen on session start
        if (sessionBlockManager.isSessionCurrentlyActive()) {
            val currentPkg = currentForegroundPackage
            val isAllowed = currentPkg != null && (sessionBlockManager.isAlwaysAllowedPackage(currentPkg) || sessionBlockManager.suitcasePackages.contains(currentPkg.lowercase()))
            if (!isAllowed) {
                com.focusguard.app.feature.session.ui.SessionLockActivity.start(this)
            }
        }

        scope.launch {
            sessionBlockManager.sessionStateFlow.collect { state ->
                if (state.isActive) {
                    val currentPkg = currentForegroundPackage
                    val isAllowed = currentPkg != null && (sessionBlockManager.isAlwaysAllowedPackage(currentPkg) || sessionBlockManager.suitcasePackages.contains(currentPkg.lowercase()))
                    if (!isAllowed) {
                        com.focusguard.app.feature.session.ui.SessionLockActivity.start(this@FocusGuardAccessibilityService)
                    }
                }
            }
        }
    }

    private fun loadDirectBootDeCaches() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                val deContext = createDeviceProtectedStorageContext()
                val dePrefs = deContext.getSharedPreferences("focusguard_de_cache", Context.MODE_PRIVATE)
                val deBlocked = dePrefs.getStringSet("blocked_packages", emptySet()) ?: emptySet()
                blockedPackages.clear()
                blockedPackages.addAll(deBlocked.map { it.lowercase() })
                blockedPackages.addAll(deBlocked)
                val isDeProtected = dePrefs.getBoolean("uninstall_protection_enabled", false)
                Log.i(TAG, "Direct Boot DE cache loaded: ${blockedPackages.size} packages, uninstallProtection=$isDeProtected")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load DE cache in Direct Boot: ${e.message}", e)
            }
        }
    }

    private var userUnlockedReceiver: BroadcastReceiver? = null

    private fun registerUserUnlockedReceiver() {
        if (userUnlockedReceiver != null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_USER_UNLOCKED) {
                    Log.i(TAG, "ACTION_USER_UNLOCKED received — initializing CE storage and observers.")
                    try {
                        unregisterReceiver(this)
                    } catch (e: Exception) {
                        Log.d(TAG, "userUnlockedReceiver inline unregister error: ${e.message}")
                    }
                    userUnlockedReceiver = null
                    initCeStorageAndObservers()
                }
            }
        }
        val filter = IntentFilter(Intent.ACTION_USER_UNLOCKED)
        try {
            registerReceiver(receiver, filter)
            userUnlockedReceiver = receiver
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register USER_UNLOCKED receiver: ${e.message}", e)
        }
    }

    private fun sweepActiveWindowOnConnect() {
        try {
            val root = rootInActiveWindow ?: return
            val pkg = root.packageName?.toString() ?: ""

            // ── CRITICAL FIX: Never sweep Settings/Accessibility screens on reconnect ──
            // When Vivo force-unbinds our service (while the user is on Accessibility Settings)
            // and then rebinds us, rootInActiveWindow still points to the Accessibility Settings
            // screen. Sweeping it immediately calls processAccessibilityEvent() which fires
            // handleSecurityTamper() → GLOBAL_ACTION_HOME → Vivo watchdog unbinds us again.
            // This was the infinite disconnect loop.
            val lowerPkg = pkg.lowercase()
            val isSettingsOrA11y = lowerPkg.contains("settings") ||
                    lowerPkg.contains("imanager") ||
                    lowerPkg.contains("safecenter") ||
                    lowerPkg.contains("permissionmanager") ||
                    lowerPkg.contains("permissioncontroller") ||
                    lowerPkg.contains("securitycenter")

            if (pkg.isBlank() || pkg == packageName || pkg.startsWith("com.focusguard.app") || isSettingsOrA11y) {
                SafeNodeUtils.safeRecycle(root)
                Log.i(TAG, "sweepActiveWindowOnConnect: SKIPPED settings/a11y screen on reconnect (pkg=$pkg) — avoids false tamper block")
                return
            }
            Log.i(TAG, "sweepActiveWindowOnConnect: active window snapshot pkg=$pkg")
            val dummyEvent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                AccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED).apply {
                    packageName = pkg
                    className = root.className?.toString() ?: ""
                }
            } else {
                @Suppress("DEPRECATION")
                AccessibilityEvent.obtain().apply {
                    eventType = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                    packageName = pkg
                    className = root.className?.toString() ?: ""
                }
            }
            processAccessibilityEvent(dummyEvent)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                @Suppress("DEPRECATION")
                dummyEvent.recycle()
            }
            SafeNodeUtils.safeRecycle(root)
        } catch (t: Throwable) {
            Log.w(TAG, "Error in sweepActiveWindowOnConnect", t)
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "AccessibilityService interrupted by system.")
        browserHeartbeatTracker.stopHeartbeat()
        cancelForegroundPolling()
        // Do NOT call clearServiceContext() here — onInterrupt is temporary feedback interruption;
        // service is still alive and bound. Only onUnbind or onDestroy clears context.
        overlayManager.setServiceContext(this)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        isServiceRunning = false
        Log.w(TAG, "AccessibilityService unbound.")
        browserHeartbeatTracker.stopHeartbeat()
        cancelForegroundPolling()
        accessibilityStateObserver?.unregister()
        accessibilityStateObserver = null
        overlayManager.clearServiceContext()
        if (isNewAppReceiverRegistered) {
            try {
                unregisterReceiver(newAppInstallReceiver)
            } catch (e: Exception) {
                Log.w(TAG, "Error unregistering NewAppInstallReceiver", e)
            }
            isNewAppReceiverRegistered = false
        }
        if (isRefreshCachesReceiverRegistered) {
            try {
                unregisterReceiver(refreshCachesReceiver)
            } catch (e: Exception) {
                Log.w(TAG, "Error unregistering refreshCachesReceiver", e)
            }
            isRefreshCachesReceiverRegistered = false
        }
        val isProtectionActive = isDeviceAdminActive() && repository.isFastUninstallProtectionEnabled()
        if (isProtectionActive) {
            try {
                com.focusguard.app.recovery.AccessibilityRecoveryHelper.restoreProtectionFast(
                    context = applicationContext,
                    source = "AccessibilityService:onUnbind",
                    repository = repository
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error triggering recovery on unbind: ${e.message}")
            }
        } else {
            Log.i(TAG, "AccessibilityService: onUnbind called while uninstall protection is disabled. Allowing service to stop cleanly.")
            com.focusguard.app.worker.AccessibilityHealthWorker.cancelReactivationNotification(applicationContext)
        }
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        browserHeartbeatTracker.stopHeartbeat()
        cancelForegroundPolling()
        accessibilityStateObserver?.unregister()
        accessibilityStateObserver = null
        overlayManager.clearServiceContext()
        if (isNewAppReceiverRegistered) {
            try {
                unregisterReceiver(newAppInstallReceiver)
            } catch (e: Exception) {
                Log.w(TAG, "Error unregistering NewAppInstallReceiver", e)
            }
            isNewAppReceiverRegistered = false
        }
        if (isRefreshCachesReceiverRegistered) {
            try {
                unregisterReceiver(refreshCachesReceiver)
            } catch (e: Exception) {
                Log.w(TAG, "Error unregistering refreshCachesReceiver in onDestroy", e)
            }
            isRefreshCachesReceiverRegistered = false
        }
        if (isTimeReceiverRegistered) {
            try {
                unregisterReceiver(timeChangeReceiver)
            } catch (e: Exception) {
                Log.w(TAG, "Error unregistering timeChangeReceiver", e)
            }
            isTimeReceiverRegistered = false
        }
        userUnlockedReceiver?.let {
            try { unregisterReceiver(it) } catch (e: Exception) { Log.d(TAG, "userUnlockedReceiver unregister error: ${e.message}") }
            userUnlockedReceiver = null
        }
        instanceRef = null
        mindfulExpiryRunnables.values.forEach { mainHandler.removeCallbacks(it) }
        mindfulExpiryRunnables.clear()
        packageInstallTimeCache.clear()
        appLabelCache.clear()
        adminRefreshJob?.cancel()
        adminRefreshJob = null
        serviceJob.cancel()
    }

    private fun cancelForegroundPolling() {
        browserHeartbeatTracker.stopHeartbeat()
        foregroundPollingJob?.cancel()
        foregroundPollingJob = null
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Crash-Proof Event Handler
    // ─────────────────────────────────────────────────────────────────────────

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        try {
            lastAliveTimestamp = android.os.SystemClock.elapsedRealtime()
            processAccessibilityEvent(event)
        } catch (t: Throwable) {
            // Must catch Exception and Error (including StackOverflowError) with zero async allocation in catch
            Log.e(TAG, "Crash-proof guard caught unhandled exception in onAccessibilityEvent: ${t.javaClass.simpleName} - ${t.message}", t)
        }
    }

    private fun processAccessibilityEvent(event: AccessibilityEvent?) {
        val e = event ?: return
        val rawPkg = e.packageName?.toString() ?: ""
        val className = e.className?.toString() ?: ""

        // ── GUARD 0: Handle TYPE_WINDOWS_CHANGED even if event.packageName is null ─
        if (e.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            val isIntercepted = MultiWindowGuard.checkAndInterceptMultiWindow(
                service = this,
                repository = repository,
                sessionBlockManager = sessionBlockManager,
                onIntercept = { sourcePkg, reason, targetName, desc ->
                    val appLabel = getCachedAppLabel(sourcePkg)
                    triggerBlock(
                        sourcePackage = sourcePkg,
                        reason = reason,
                        blockType = OverlayBlockType.APP_BLOCK,
                        targetName = if (targetName == sourcePkg) appLabel else targetName,
                        description = desc
                    )
                }
            )
            if (isIntercepted) return
            if (rawPkg.isBlank()) return
        }

        if (rawPkg.isBlank()) return
        val pkg = rawPkg

        // ── GUARD 1: Self-exclusion (never react to FocusGuard itself) ───────
        if (pkg == packageName || pkg.startsWith("com.focusguard.app")) {
            currentForegroundPackage = pkg
            lastFocusGuardPickedUpTimestamp = 0L
            lastFocusGuardTouchTimestamp = 0L
            cancelForegroundPolling()
            return
        }

        // ── GUARD 1.05: Fast-discard software keyboards & IME (Zero typing CPU lag) ─
        if (isKeyboardPackage(pkg)) {
            return
        }

        // ── GUARD 1.1: Active Overlay Protection & Interception ─
        if (overlayManager.isOverlayShowing) {
            val isProtectionActive = isDeviceAdminActive() && repository.isFastUninstallProtectionEnabled()
            // Dismiss dangerous Settings screens immediately if switched to while overlay is active AND uninstall protection is on
            if (isSettingsPackage(pkg.lowercase()) && isProtectionActive) {
                performGlobalAction(GLOBAL_ACTION_BACK)
                performGlobalAction(GLOBAL_ACTION_HOME)
                return
            }

            // 1. Immediately crush any OEM cloner or virtual space launched while overlay is active (ONLY when protection or session is active)
            if ((isProtectionActive || sessionBlockManager.isSessionCurrentlyActive()) && AntiClonerEngine.isClonerPackage(pkg)) {
                Log.w(TAG, "Cloner launched during overlay -> killing immediately ($pkg)")
                performGlobalAction(GLOBAL_ACTION_HOME)
                try {
                    val am = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                    am?.killBackgroundProcesses(pkg)
                } catch (e: Exception) {
                    Log.d(TAG, "killBackgroundProcesses error: ${e.message}")
                }
                triggerBlock(
                    sourcePackage = pkg,
                    reason = "App Cloner Blocked",
                    blockType = OverlayBlockType.CLONER_BLOCK,
                    targetName = "App Cloner Blocked",
                    description = "App cloning is strictly prohibited."
                )
                return
            }

            val overlayRootNode = try { rootInActiveWindow ?: e.source } catch (_: Exception) { null }
            try {
                if (isProtectionActive) {
                    // 2. Immediately dismiss uninstaller dialogs targeting FocusGuard
                    val isPotentialUninstallHost = pkg == "android" ||
                            pkg == "com.google.android.packageinstaller" ||
                            pkg == "com.android.packageinstaller" ||
                            pkg == "com.android.permissioncontroller" ||
                            pkg == "com.google.android.permissioncontroller"
                    if (isPotentialUninstallHost && overlayRootNode != null && isUninstallConfirmationForFocusGuard(overlayRootNode)) {
                        Log.w(TAG, "Uninstall dialog detected during overlay -> dismissing immediately ($pkg)")
                        performGlobalAction(GLOBAL_ACTION_BACK)
                        performGlobalAction(GLOBAL_ACTION_HOME)
                        triggerBlock(
                            sourcePackage = pkg,
                            reason = "Uninstall Attempt Blocked",
                            blockType = OverlayBlockType.UNINSTALL_ATTEMPT,
                            targetName = "FocusGuard Uninstall",
                            description = "FocusGuard is protected from uninstallation."
                        )
                        return
                    }

                    // 3. Immediately dismiss any launcher popup attempting to uninstall or clone FocusGuard
                    if (isLauncherPackage(pkg)) {
                        val eventTextMatchesFg = e.text.any { it.toString().contains("FocusGuard", ignoreCase = true) } ||
                                (e.contentDescription?.contains("FocusGuard", ignoreCase = true) == true)
                        val hasLauncherPopup = (overlayRootNode != null && (
                                containsKeywords(overlayRootNode, "App clone", "Clone app", "Dual app") ||
                                (containsKeywords(overlayRootNode, "App info") && containsKeywords(overlayRootNode, "Uninstall", "Delete"))
                        )) || e.eventType == AccessibilityEvent.TYPE_VIEW_LONG_CLICKED ||
                        e.text.any { it.toString().contains("FocusGuard", ignoreCase = true) || it.toString().contains("App clone", ignoreCase = true) } ||
                        (e.contentDescription?.contains("FocusGuard", ignoreCase = true) == true)

                        val hasFgInPopup = overlayRootNode?.let { node ->
                            val fgNodes = node.findAccessibilityNodeInfosByText("FocusGuard")
                            val found = fgNodes.isNotEmpty()
                            fgNodes.forEach { SafeNodeUtils.safeRecycle(it) }
                            found
                        } ?: false

                        val isFgEvent = eventTextMatchesFg ||
                                (overlayRootNode != null && isUninstallConfirmationForFocusGuard(overlayRootNode)) ||
                                (hasLauncherPopup && hasFgInPopup)

                        if (isFgEvent) {
                            Log.w(TAG, "Launcher interaction specifically targeting FocusGuard detected during overlay -> dismissing immediately ($pkg)")
                            performGlobalAction(GLOBAL_ACTION_BACK)
                            performGlobalAction(GLOBAL_ACTION_HOME)
                            triggerBlock(
                                sourcePackage = pkg,
                                reason = "Launcher Hold on FocusGuard",
                                blockType = OverlayBlockType.UNINSTALL_ATTEMPT,
                                targetName = "FocusGuard Protection",
                                description = "Uninstalling or cloning FocusGuard is protected."
                            )
                            return
                        }
                    }
                }
            } finally {
                SafeNodeUtils.safeRecycle(overlayRootNode)
            }

            // 4. SystemUI Notification Shade Pull-down Guard during active overlay
            if (pkg == "com.android.systemui") {
                val isShade = className.contains("NotificationShade", ignoreCase = true) ||
                        className.contains("NotificationPanel", ignoreCase = true) ||
                        className.contains("QuickSettings", ignoreCase = true) ||
                        className.contains("Shade", ignoreCase = true)
                if (isShade) {
                    val now = android.os.SystemClock.elapsedRealtime()
                    if (now - lastShadeCollapseTimestamp > 1000L) {
                        lastShadeCollapseTimestamp = now
                        Log.w(TAG, "Notification shade pull-down intercepted during block overlay -> Collapsing immediately!")
                        performGlobalAction(GLOBAL_ACTION_BACK)
                    }
                }
            }
            return
        }

        // ── GUARD 1.14: Post-Overlay Dismiss Cooldown ────────────────────────
        // Prevents rapid back-to-back re-blocking of the same package when an overlay just closed.
        // NEVER drop launcher, system packages, settings packages, or cloners!
        val nowRealtime = android.os.SystemClock.elapsedRealtime()
        if (nowRealtime - lastOverlayDismissTimestamp < 2500L &&
            pkg == lastDismissedPackage &&
            !isSystemOrLauncherPackage(pkg) &&
            !isSettingsPackage(pkg.lowercase()) &&
            !AntiClonerEngine.isClonerPackage(pkg)) {
            return
        }

        // ── GUARD 1.145: DEEP FOCUS SYSTEMUI SHADE & RECENTS COLLAPSE ───────
        // Prevent escaping Deep Focus Kiosk via notification shade, quick settings tiles, or overview
        if (sessionBlockManager.isSessionCurrentlyActive() && pkg == "com.android.systemui") {
            val isShadeOrRecents = className.contains("NotificationShade", ignoreCase = true) ||
                    className.contains("NotificationPanel", ignoreCase = true) ||
                    className.contains("QuickSettings", ignoreCase = true) ||
                    className.contains("Shade", ignoreCase = true) ||
                    className.contains("Recents", ignoreCase = true) ||
                    className.contains("Overview", ignoreCase = true)
            if (isShadeOrRecents) {
                val now = android.os.SystemClock.elapsedRealtime()
                if (now - lastShadeCollapseTimestamp > 40L) {
                    lastShadeCollapseTimestamp = now
                    Log.w(TAG, "Notification shade / quick settings / recents intercepted during Deep Focus -> Collapsing!")
                    performGlobalAction(GLOBAL_ACTION_BACK)
                    com.focusguard.app.feature.session.ui.SessionLockActivity.start(this)
                }
                return
            }
        }

        // ── GUARD 1.15: Transient system events (status bar, volume slider, toasts, keyboard) ─
        // These events are transient overlays and must NOT disrupt the active foreground app's session timer!
        if (isTransientSystemPackage(pkg)) {
            return
        }

        // ── GUARD 1.15a: PREEMPTIVE OEM SECURITY SCAN DETECTION ─────────────
        // When events arrive from known OEM security managers (Vivo iManager, Vivo SafeCenter,
        // Vivo PermissionManager) we check whether our accessibility service entry is temporarily
        // absent from ENABLED_ACCESSIBILITY_SERVICES. If so, it's an OEM scan window — arm the
        // suppression flag immediately so handleSecurityTamper() won't fire HOME during the scan.
        val isOemSecurityPkg = pkg == "com.vivo.imanager" ||
                pkg == "com.vivo.safecenter" ||
                pkg == "com.vivo.permissionmanager" ||
                pkg == "com.vivo.abe" ||
                pkg == "com.iqoo.secure" ||
                pkg == "com.oneplus.security" ||
                pkg == "com.miui.securityadd" ||
                pkg == "com.huawei.systemmanager"
        if (isOemSecurityPkg && !isOemScanWindowActive()) {
            try {
                val enabledSvcs = android.provider.Settings.Secure.getString(
                    contentResolver,
                    android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                ) ?: ""
                val isTransientlyOff = !enabledSvcs.contains(packageName)
                if (isTransientlyOff) {
                    oemScanSuppressUntilStatic.set(android.os.SystemClock.elapsedRealtime() + 12_000L)
                    Log.i(TAG, "GUARD 1.15a: OEM security manager ($pkg) detected with transient accessibility clear — scan window armed for 12s")
                }
            } catch (e: Exception) {
                Log.d(TAG, "ENABLED_ACCESSIBILITY_SERVICES query error: ${e.message}")
            }
        }

        // ── GUARD 1.155: ZERO-TOLERANCE ANTI-CLONING & DUAL APPS SHIELD (Layer 1: Cloner Blacklist) ─
        // Catches OEM Cloners (com.oplus.multiapp, com.miui.securityadd, com.vivo.doubleinstance, etc.)
        // and 3rd-party virtual spaces (Parallel Space, Island, Shelter, etc.).
        // Only active when anti-cloning is enabled AND protection or an active focus session is running.
        if (repository.isFastAntiCloningShieldEnabled()) {
            val isProtectionActive = isDeviceAdminActive() && repository.isFastUninstallProtectionEnabled()
            val isSessionActive = sessionBlockManager.isSessionCurrentlyActive()
            if ((isProtectionActive || isSessionActive) && AntiClonerEngine.isClonerPackage(pkg)) {
                val appLabel = getCachedAppLabel(pkg)
                Log.w(TAG, "ANTI-CLONING INTERCEPT: Cloner / Virtual Container blocked: $pkg ($appLabel)")
                handleSecurityTamper(
                    sourcePackage = pkg,
                    reason = "App Cloner Blocked: $appLabel",
                    targetName = "App Cloning Prohibited",
                    customToastMessage = "🚫 App cloning and dual apps are prohibited while FocusGuard protection is active."
                )
                return
            }
        }

        // ── GUARD 1.16: SYSTEM CLOCK ROLLBACK & TIME TRAVEL TAMPER SHIELD ─────
        if (TimeTamperGuard.checkAndValidateTime()) {
            val isProtectionActive = isDeviceAdminActive() && repository.isFastUninstallProtectionEnabled()
            if (isProtectionActive || sessionBlockManager.isSessionCurrentlyActive()) {
                Log.e(TAG, "Clock tampering detected! Triggering security tamper block.")
                triggerBlock(
                    sourcePackage = pkg,
                    reason = "System Clock Tampering Detected",
                    blockType = OverlayBlockType.SETTINGS_TAMPER,
                    targetName = "Time Manipulation Blocked",
                    description = "FocusGuard detected an unauthorized change to the system clock. Clock rollbacks and artificial time jumps are blocked to protect your focus integrity."
                )
                return
            }
        }

        val isPackageChanged = pkg != currentForegroundPackage
        val isWindowTransition = e.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED

        // ── GUARD 1.17: MULTI-WINDOW & SPLIT-SCREEN BYPASS GUARD ──────────────
        if (isWindowTransition || isPackageChanged) {
            val isIntercepted = MultiWindowGuard.checkAndInterceptMultiWindow(
                service = this,
                repository = repository,
                sessionBlockManager = sessionBlockManager,
                onIntercept = { sourcePkg, reason, targetName, desc ->
                    if (sessionBlockManager.isSessionCurrentlyActive()) {
                        triggerBlockHaptic()
                        com.focusguard.app.feature.session.ui.SessionLockActivity.start(this)
                        dismissUnauthorizedAppInDeepFocus(sourcePkg)
                    } else {
                        val appLabel = getCachedAppLabel(sourcePkg)
                        triggerBlock(
                            sourcePackage = sourcePkg,
                            reason = reason,
                            blockType = OverlayBlockType.APP_BLOCK,
                            targetName = if (targetName == sourcePkg) appLabel else targetName,
                            description = desc
                        )
                    }
                }
            )
            if (isIntercepted) return
        }

        // ── GUARD 1.18: DEEP FOCUS LOCKDOWN (#blockit Engine) STRICT KIOSK ENFORCEMENT ──
        // When Deep Focus session is active, the device operates as a zero-bypass kiosk.
        // All Home Launchers, App Drawers, System Settings, App Stores, and non-suitcase apps
        // are intercepted instantly (< 0.01ms) and redirected directly to SessionLockActivity.
        if (sessionBlockManager.isSessionCurrentlyActive()) {
            val isBlockedInSession = sessionBlockManager.isPackageBlockedInSession(pkg)
            if (isBlockedInSession) {
                val nowRt = android.os.SystemClock.elapsedRealtime()
                if (nowRt - lastDeepFocusReassertTimestamp > 40L) {
                    lastDeepFocusReassertTimestamp = nowRt
                    Log.w(TAG, "DEEP FOCUS INTERCEPT: Unauthorized app/launcher blocked: $pkg -> Reasserting Kiosk Lock Screen")
                    triggerBlockHaptic()
                    com.focusguard.app.feature.session.ui.SessionLockActivity.start(this)
                    dismissUnauthorizedAppInDeepFocus(pkg)
                }
                return
            }
        }

        // ── PROTECTION STATE RESOLUTION (<0.001ms cached atomic lookup) ───────
        val isUninstallProtectionOn = repository.isFastUninstallProtectionEnabled()
        val isOnboardingDone = repository.isFastOnboardingComplete()
        val isDeviceAdminCurrentlyActive = isDeviceAdminActive()
        val protectionWanted = isDeviceAdminCurrentlyActive && isUninstallProtectionOn

        if (protectionWanted && !isDeviceAdminCurrentlyActive) {
            val nowRt = android.os.SystemClock.elapsedRealtime()
            if (nowRt - lastAdminTamperLogTimestamp > 60_000L) {
                lastAdminTamperLogTimestamp = nowRt
                scope.launch {
                    repository.logEvent(
                        EventType.ADMIN_REMOVED_WHILE_PROTECTED,
                        "Device Admin is inactive while Uninstall Protection is wanted! Tamper protection remains enforced."
                    )
                }
            }
        }

        // ── 1. LAUNCHER FAST PATH (< 0.05ms) ──────────────────────────────────
        if (isLauncherPackage(pkg)) {
            val rootNode = try { rootInActiveWindow ?: e.source } catch (_: Exception) { null }
            try {
                if (processLauncherEvent(rootNode, e, pkg, className, protectionWanted, nowRealtime)) {
                    return
                }
            } finally {
                SafeNodeUtils.safeRecycle(rootNode)
            }
            return
        }

        // ── 2. PACKAGE INSTALLER & STORE UNINSTALL PATH (< 0.1ms) ─────────────
        if (isInstallerPackage(pkg, className)) {
            val rootNode = try { rootInActiveWindow ?: e.source } catch (_: Exception) { null }
            try {
                if (processInstallerEvent(rootNode, e, pkg, className, protectionWanted)) {
                    return
                }
            } finally {
                SafeNodeUtils.safeRecycle(rootNode)
            }
            return
        }

        // ── 3. SYSTEM SETTINGS & OEM SECURITY CENTERS PATH (< 0.2ms) ──────────
        val lowerPkg = pkg.lowercase()
        if (isSettingsPackage(lowerPkg)) {
            val rootNode = try { rootInActiveWindow ?: e.source } catch (_: Exception) { null }
            try {
                if (processSettingsEvent(rootNode, e, pkg, className, protectionWanted, isDeviceAdminCurrentlyActive, nowRealtime)) {
                    return
                }
            } finally {
                SafeNodeUtils.safeRecycle(rootNode)
            }
            return
        }

        // ── 4. SYSTEM TOOLS FILTER (Dialer, Clock, SystemUI) ──────────────────
        if (isSystemOrLauncherPackage(pkg)) {
            return
        }

        // ── 5. TARGET APPLICATIONS (Blocked Apps, Limits, Reels, Browsers, Keywords) ─
        val rootNode = try { rootInActiveWindow ?: e.source } catch (_: Exception) { null }
        try {
            processTargetAppEvent(
                rootNode = rootNode,
                e = e,
                pkg = pkg,
                className = className,
                nowRealtime = nowRealtime,
                isPackageChanged = isPackageChanged,
                isWindowTransition = isWindowTransition,
                protectionWanted = protectionWanted,
                isDeviceAdminCurrentlyActive = isDeviceAdminCurrentlyActive
            )
        } finally {
            SafeNodeUtils.safeRecycle(rootNode)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Specialized Domain Handlers (Zero-Lag Modular Architecture)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Specialized Fast-Path Handler for Launcher / Home screen events.
     * Evaluates desktop icon presence, hold / long-press detection, popup shortcut menus,
     * drag-to-uninstall drop targets, and launcher hide-app settings.
     * Latency: < 0.05ms.
     */
    private fun processLauncherEvent(
        rootNode: AccessibilityNodeInfo?,
        e: AccessibilityEvent,
        pkg: String,
        className: String,
        protectionWanted: Boolean,
        nowRealtime: Long
    ): Boolean {
        // A. Launcher Hide Apps Intercept (Samsung One UI, Xiaomi Home, Oppo Launcher, etc.)
        if (protectionWanted) {
            val isLauncherHideApps = className.contains("HideApp", ignoreCase = true) ||
                    className.contains("AppHide", ignoreCase = true) ||
                    className.contains("HiddenApp", ignoreCase = true) ||
                    className.contains("PrivateSpace", ignoreCase = true) ||
                    (rootNode != null && containsKeywords(
                        rootNode,
                        "Hide apps on Home and Apps screens",
                        "Hide apps on Home",
                        "Hide apps",
                        "Hidden apps",
                        "App hider",
                        "Hide icons",
                        "Private space"
                    ))

            if (isLauncherHideApps) {
                Log.w(TAG, "BLOCKING Hide Apps tamper attempt in Launcher ($pkg - $className)!")
                triggerBlock(
                    sourcePackage = pkg,
                    reason = "Hide Apps Access Blocked in Launcher",
                    blockType = OverlayBlockType.SETTINGS_TAMPER,
                    targetName = "Hide Apps Protection",
                    description = "Hiding apps is restricted to prevent bypassing FocusGuard protection."
                )
                return true
            }
        }

        // B. Desktop FocusGuard Icon Tracking & Visibility
        var isFgVisibleOnDesktop = false
        if (rootNode != null) {
            val currentFgNodes = rootNode.findAccessibilityNodeInfosByText("FocusGuard")
            isFgVisibleOnDesktop = currentFgNodes.isNotEmpty()
            if (isFgVisibleOnDesktop) {
                val r = android.graphics.Rect()
                currentFgNodes[0].getBoundsInScreen(r)
                if (r.width() > 0 && r.height() > 0) {
                    lastDesktopFocusGuardBounds = r
                    lastDesktopFocusGuardTimestamp = nowRealtime
                }
                lastFocusGuardPickedUpTimestamp = 0L
            }
            currentFgNodes.forEach { SafeNodeUtils.safeRecycle(it) }
        }

        // C. Direct event-based interception for long-press
        val sourceNode = try { e.source } catch (_: Exception) { null }
        val sourceBounds = android.graphics.Rect().also { r ->
            sourceNode?.getBoundsInScreen(r)
        }
        val sourceBoundsMatchesFg = lastDesktopFocusGuardBounds != null &&
                sourceBounds.width() > 0 &&
                android.graphics.Rect.intersects(sourceBounds, lastDesktopFocusGuardBounds!!)

        val eventTextMatchesFocusGuard = e.text.any { it.toString().contains("FocusGuard", ignoreCase = true) } ||
                (e.contentDescription?.contains("FocusGuard", ignoreCase = true) == true) ||
                (sourceNode?.let { s ->
                    val st = s.text?.toString() ?: ""
                    val sd = s.contentDescription?.toString() ?: ""
                    st.contains("FocusGuard", ignoreCase = true) || sd.contains("FocusGuard", ignoreCase = true)
                } ?: false) ||
                sourceBoundsMatchesFg

        if (e.eventType == AccessibilityEvent.TYPE_VIEW_LONG_CLICKED) {
            if (eventTextMatchesFocusGuard) {
                lastFocusGuardLongPressTimestamp = nowRealtime
                lastFocusGuardTouchTimestamp = nowRealtime
                lastTargetedAppLabel = "FocusGuard"

                if (protectionWanted) {
                    Log.w(TAG, "BLOCKING direct long-press on FocusGuard in launcher ($pkg)!")
                    lastFocusGuardPopupBlockTimestamp = nowRealtime
                    lastDesktopFocusGuardTimestamp = nowRealtime

                    performGlobalAction(GLOBAL_ACTION_HOME)

                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        android.widget.Toast.makeText(
                            this,
                            "🛡️ FocusGuard is protected from uninstall & cloning.",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }

                    scope.launch {
                        repository.logEvent(EventType.UNINSTALL_ATTEMPT, "Target=FocusGuard Launcher | Pkg=$pkg | Launcher Long-Press on FocusGuard")
                    }
                    return true
                }
            }
        } else if (e.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            if (eventTextMatchesFocusGuard) {
                lastFocusGuardTouchTimestamp = nowRealtime
                lastTargetedAppLabel = "FocusGuard"
            }

            // 🛡️ PRE-EMPTIVE LAUNCHER CLICK INTERCEPT (Eliminate Fast-Click Race Condition & App Clone Bypass)
            if (protectionWanted) {
                val clickedNode = try { e.source } catch (_: Exception) { null }
                val clickedText = (e.text.joinToString(" ") + " " + (e.contentDescription ?: "") + " " + (clickedNode?.text ?: "") + " " + (clickedNode?.contentDescription ?: "")).lowercase()

                val isCloneClick = clickedText.contains("app clone") ||
                        clickedText.contains("clone") ||
                        clickedText.contains("cloner") ||
                        clickedText.contains("dual app") ||
                        clickedText.contains("twin app") ||
                        clickedText.contains("द्वि-ऐप") ||
                        clickedText.contains("क्लोन")

                // 🚨 UNCONDITIONAL CLONE BLOCK: No app should ever be cloned from launcher when uninstall protection is active!
                if (isCloneClick) {
                    Log.w(TAG, "PREEMPTIVE BLOCK: Direct click on 'App Clone' in launcher ($pkg)!")
                    performGlobalAction(GLOBAL_ACTION_HOME)

                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        android.widget.Toast.makeText(
                            this,
                            "🛡️ App cloning is prohibited while FocusGuard protection is active.",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }

                    scope.launch {
                        repository.logEvent(
                            EventType.UNINSTALL_ATTEMPT,
                            "Target=FocusGuard Launcher | Pkg=$pkg | Preemptive Launcher Clone Block"
                        )
                    }
                    return true
                }

                val isUninstallClick = clickedText.contains("uninstall") ||
                        clickedText.contains("delete") ||
                        clickedText.contains("remove") ||
                        clickedText.contains("अनइंस्टॉल") ||
                        clickedText.contains("desinstalar")

                val isTargetingFocusGuard = (lastTargetedAppLabel == "FocusGuard" && (nowRealtime - lastFocusGuardTouchTimestamp < 30_000L)) ||
                        (rootNode != null && (
                            rootNode.findAccessibilityNodeInfosByText("FocusGuard").isNotEmpty() ||
                            rootNode.findAccessibilityNodeInfosByText("com.focusguard.app").isNotEmpty()
                        )) ||
                        (clickedNode?.parent?.let { p ->
                            val pt = p.text?.toString() ?: ""
                            val pd = p.contentDescription?.toString() ?: ""
                            pt.contains("FocusGuard", ignoreCase = true) || pd.contains("FocusGuard", ignoreCase = true)
                        } == true) ||
                        (lastDesktopFocusGuardBounds != null && (nowRealtime - lastDesktopFocusGuardTimestamp < 60_000L))

                if (isUninstallClick && isTargetingFocusGuard) {
                    Log.w(TAG, "PREEMPTIVE BLOCK: Direct click on 'Uninstall' for FocusGuard in launcher ($pkg)!")
                    lastTargetedAppLabel = ""
                    lastFocusGuardTouchTimestamp = 0L

                    performGlobalAction(GLOBAL_ACTION_HOME)

                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        android.widget.Toast.makeText(
                            this,
                            "🛡️ FocusGuard is protected from Uninstall.",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }

                    scope.launch {
                        repository.logEvent(
                            EventType.UNINSTALL_ATTEMPT,
                            "Target=FocusGuard Launcher | Pkg=$pkg | Preemptive Launcher Uninstall Block"
                        )
                    }
                    return true
                }
            }
        }

        // D. Launcher Popup / Shortcut Menu Interception
        if (protectionWanted && rootNode != null) {
            val popupNodes = rootNode.findAccessibilityNodeInfosByViewId("com.android.launcher3:id/popup_container")
                .ifEmpty { rootNode.findAccessibilityNodeInfosByViewId("com.android.launcher3:id/deep_shortcuts_container") }
                .ifEmpty { rootNode.findAccessibilityNodeInfosByViewId("com.android.launcher3:id/bottom_shortcuts_scroll_view") }
                .ifEmpty { rootNode.findAccessibilityNodeInfosByViewId("com.android.launcher3:id/system_shortcut_icons") }
                .ifEmpty { rootNode.findAccessibilityNodeInfosByViewId("com.android.launcher3:id/bubble_text") }

            val actionNodes = if (popupNodes.isEmpty()) {
                rootNode.findAccessibilityNodeInfosByText("Uninstall")
                    .ifEmpty { rootNode.findAccessibilityNodeInfosByText("Delete") }
                    .ifEmpty { rootNode.findAccessibilityNodeInfosByText("App info") }
                    .ifEmpty { rootNode.findAccessibilityNodeInfosByText("App clone") }
                    .ifEmpty { rootNode.findAccessibilityNodeInfosByText("Clone") }
                    .ifEmpty { rootNode.findAccessibilityNodeInfosByText("क्लोन") }
                    .ifEmpty { rootNode.findAccessibilityNodeInfosByText("Remove") }
                    .ifEmpty { rootNode.findAccessibilityNodeInfosByText("अनइंस्टॉल") }
                    .ifEmpty { rootNode.findAccessibilityNodeInfosByText("ऐप जानकारी") }
                    .ifEmpty { rootNode.findAccessibilityNodeInfosByText("Desinstalar") }
            } else {
                emptyList()
            }

            val hasLauncherPopup = popupNodes.isNotEmpty() || actionNodes.isNotEmpty() ||
                    (containsKeywords(rootNode, "App info") && containsKeywords(rootNode, "Uninstall", "Delete"))

            if (hasLauncherPopup) {
                var isThisPopupForFocusGuard = false
                val popupAnchorNode = popupNodes.firstOrNull() ?: actionNodes.firstOrNull()

                // Check 1: Check if popup specifically contains "FocusGuard" or targets FocusGuard
                if (popupAnchorNode != null) {
                    val popupText = extractScreenTextFast(popupAnchorNode)
                    if (popupText.contains("FocusGuard", ignoreCase = true) || popupText.contains("com.focusguard.app", ignoreCase = true)) {
                        isThisPopupForFocusGuard = true
                    }
                }

                // Check 2: Inspect whole rootNode specifically for FocusGuard popup header
                if (!isThisPopupForFocusGuard) {
                    val fgNodes = rootNode.findAccessibilityNodeInfosByText("FocusGuard")
                    if (fgNodes.isNotEmpty()) {
                        isThisPopupForFocusGuard = true
                        fgNodes.forEach { SafeNodeUtils.safeRecycle(it) }
                    }
                }

                // Check 3: SPATIAL BOUNDS CHECK - In Android launchers, the popup is positioned directly adjacent to the touched icon!
                if (!isThisPopupForFocusGuard && popupAnchorNode != null && lastDesktopFocusGuardBounds != null &&
                    (nowRealtime - lastDesktopFocusGuardTimestamp < 60_000L)) {
                    val popupRect = android.graphics.Rect()
                    popupAnchorNode.getBoundsInScreen(popupRect)
                    val horizontalOverlap = popupRect.left < lastDesktopFocusGuardBounds!!.right && popupRect.right > lastDesktopFocusGuardBounds!!.left
                    val verticalProximity = Math.abs(popupRect.top - lastDesktopFocusGuardBounds!!.bottom) < 600 ||
                            Math.abs(lastDesktopFocusGuardBounds!!.top - popupRect.bottom) < 600
                    if (horizontalOverlap && verticalProximity) {
                        Log.d(TAG, "Popup matches FocusGuard desktop icon spatial coordinates!")
                        isThisPopupForFocusGuard = true
                    }
                }

                // Check 4: Was FocusGuard touched/long-pressed within the last 30 seconds?
                if (!isThisPopupForFocusGuard && (lastFocusGuardTouchTimestamp > 0L && (nowRealtime - lastFocusGuardTouchTimestamp < 30_000L))) {
                    isThisPopupForFocusGuard = true
                }

                popupNodes.forEach { SafeNodeUtils.safeRecycle(it) }
                actionNodes.forEach { SafeNodeUtils.safeRecycle(it) }

                if (isThisPopupForFocusGuard) {
                    Log.w(TAG, "REACTIVE BLOCK: FocusGuard launcher popup intercepted and dismissed ($pkg)!")
                    performGlobalAction(GLOBAL_ACTION_HOME)

                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        android.widget.Toast.makeText(
                            this,
                            "🛡️ FocusGuard is protected from uninstall & cloning.",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }

                    scope.launch {
                        repository.logEvent(EventType.UNINSTALL_ATTEMPT, "Target=FocusGuard Launcher | Pkg=$pkg | Launcher Hold Reactive Block")
                    }
                    return true
                }
            } else {
                popupNodes.forEach { SafeNodeUtils.safeRecycle(it) }
                actionNodes.forEach { SafeNodeUtils.safeRecycle(it) }
            }
        }

        // E. Dragging FocusGuard to "Uninstall" or "Delete" drop targets
        val isDragDropTarget = className.contains("DropTarget", ignoreCase = true) ||
                className.contains("DeleteDropTarget", ignoreCase = true) ||
                (rootNode != null && containsKeywords(rootNode, "Drop to uninstall", "Drag to uninstall"))

        if (protectionWanted && isDragDropTarget &&
            !isFgVisibleOnDesktop && lastDesktopFocusGuardBounds != null &&
            (nowRealtime - lastDesktopFocusGuardTimestamp < 60_000L)) {
            Log.w(TAG, "BLOCKING drag-to-uninstall FocusGuard in launcher ($pkg)!")
            lastDesktopFocusGuardTimestamp = nowRealtime
            lastFocusGuardLongPressTimestamp = 0L
            lastFocusGuardTouchTimestamp = 0L
            lastFocusGuardPopupBlockTimestamp = nowRealtime
            lastTargetedAppLabel = ""

            performGlobalAction(GLOBAL_ACTION_BACK)

            triggerBlock(
                sourcePackage = pkg,
                reason = "Launcher Drop-to-Uninstall FocusGuard",
                blockType = OverlayBlockType.UNINSTALL_ATTEMPT,
                targetName = "FocusGuard Uninstall Protection",
                description = "Uninstalling FocusGuard is protected by friction challenge."
            )

            scope.launch {
                repository.logEvent(EventType.UNINSTALL_ATTEMPT, "Target=FocusGuard Launcher | Pkg=$pkg | Launcher Drop-to-Uninstall FocusGuard")
            }
            return true
        }

        return true
    }

    /**
     * Specialized Fast-Path Handler for PackageInstaller / Google Play Store uninstall screens.
     */
    private fun processInstallerEvent(
        rootNode: AccessibilityNodeInfo?,
        e: AccessibilityEvent,
        pkg: String,
        className: String,
        protectionWanted: Boolean
    ): Boolean {
        if (!protectionWanted) {
            Log.i(TAG, "Uninstall allowed: Uninstall Protection is OFF.")
            return false
        }

        val isPlayStore = pkg == "com.android.vending"
        val eventText = e.text.joinToString(" ")
        val isFastFgTarget = eventText.contains("FocusGuard", ignoreCase = true) ||
                eventText.contains("com.focusguard.app", ignoreCase = true)

        if (e.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && isFastFgTarget) {
            Log.w(TAG, "PREEMPTIVE BLOCK: Instant pre-render kill for PackageInstaller targeting FocusGuard ($pkg)")
            performGlobalAction(GLOBAL_ACTION_HOME)
            handleSecurityTamper(
                sourcePackage = pkg,
                reason = if (isPlayStore) "Google Play Store Uninstall Targeting FocusGuard" else "Package Installer Targeting FocusGuard",
                targetName = "FocusGuard Uninstall",
                blockType = OverlayBlockType.UNINSTALL_ATTEMPT,
                customToastMessage = "🚫 Uninstalling FocusGuard is protected. Complete the friction challenge in the app."
            )
            return true
        }

        if (rootNode != null && (isUninstallConfirmationForFocusGuard(rootNode) ||
                    containsKeywords(rootNode, "FocusGuard", "com.focusguard.app", maxDepth = 10))) {
            handleSecurityTamper(
                sourcePackage = pkg,
                reason = if (isPlayStore) "Google Play Store Uninstall Targeting FocusGuard" else "Package Installer Targeting FocusGuard",
                targetName = "FocusGuard Uninstall",
                blockType = OverlayBlockType.UNINSTALL_ATTEMPT,
                customToastMessage = "🚫 Uninstalling FocusGuard is protected. Complete the friction challenge in the app."
            )
            return true
        }
        return false
    }

    /**
     * Specialized Fast-Path Handler for System Settings & OEM Security Centers.
     */
    private fun processSettingsEvent(
        rootNode: AccessibilityNodeInfo?,
        e: AccessibilityEvent,
        pkg: String,
        className: String,
        protectionWanted: Boolean,
        isDeviceAdminCurrentlyActive: Boolean,
        nowRealtime: Long
    ): Boolean {
        // Throttling: Cap TYPE_WINDOW_CONTENT_CHANGED scans to at most 1 per 150ms
        if (e.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            if (nowRealtime - lastSettingsContentScanTimestamp < 150L) {
                return true
            }
            lastSettingsContentScanTimestamp = nowRealtime
        }

        // Check legitimate admin activation window (ONLY permitted when admin is not yet active)
        val isWithinAdminActivationWindow = !isDeviceAdminCurrentlyActive && (nowRealtime < adminActivationAllowedUntil)
        if (className.contains("DeviceAdminAdd", ignoreCase = true) && isWithinAdminActivationWindow) {
            Log.d(TAG, "Allowing DeviceAdminAdd screen: within legitimate activation window")
            return true
        }

        val screenText = extractScreenTextFast(rootNode)

        // ── GUARD 4.1: ACCESSIBILITY SETTINGS DEACTIVATION TAMPER ──
        if (protectionWanted) {
            val isA11y = className.contains("Accessibility", ignoreCase = true) ||
                    hasText(screenText, "accessibility", "screen protection")
            val targetsFocusGuardInA11y = hasText(screenText, "focusguard", "com.focusguard.app")

            if (isA11y && targetsFocusGuardInA11y && !isOemScanWindowActive()) {
                lastA11yFocusGuardPageTimestamp = nowRealtime
                Log.d(TAG, "A11y FocusGuard page stamped at $nowRealtime")
            }

            val isOnFocusGuardA11yPage = lastA11yFocusGuardPageTimestamp > 0L &&
                    (nowRealtime - lastA11yFocusGuardPageTimestamp < 8_000L)

            val clickedFocusGuard = (e.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) &&
                    (e.text.any { it.toString().contains("FocusGuard", ignoreCase = true) } ||
                     e.contentDescription?.contains("FocusGuard", ignoreCase = true) == true ||
                     (try { e.source } catch (_: Exception) { null })?.text?.toString()
                         ?.contains("FocusGuard", ignoreCase = true) == true)

            // Direct click on FocusGuard item in a11y list
            if (isA11y && clickedFocusGuard) {
                Log.w(TAG, "BLOCKING click on FocusGuard in Accessibility Settings! (cls: $className)")
                lastA11yFocusGuardPageTimestamp = nowRealtime
                handleSecurityTamper(
                    sourcePackage = pkg,
                    reason = "Direct Click on FocusGuard in Accessibility Settings",
                    targetName = "Accessibility Settings",
                    customToastMessage = "🚫 FocusGuard accessibility service is protected. Complete the friction challenge in the app to modify."
                )
                return true
            }

            // Click while on FocusGuard's details page
            if (isOnFocusGuardA11yPage && e.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
                Log.w(TAG, "BLOCKING click on FocusGuard Accessibility details page! (cls: $className)")
                handleSecurityTamper(
                    sourcePackage = pkg,
                    reason = "Interaction on FocusGuard Accessibility Page",
                    targetName = "Accessibility Settings",
                    customToastMessage = "🚫 FocusGuard accessibility service is protected. Complete the friction challenge in the app to modify."
                )
                return true
            }

            // Window change into FocusGuard's a11y details page
            val isFocusGuardA11yDetailsPage = isA11y && targetsFocusGuardInA11y && (
                    hasText(screenText, "turn off", "stop", "use focusguard", "allow focusguard", "shortcut") ||
                    className.contains("ToggleAccessibilityServicePreferenceFragment", ignoreCase = true) ||
                    className.contains("AccessibilityServiceWarning", ignoreCase = true)
            )

            if (isFocusGuardA11yDetailsPage && !isOemScanWindowActive()) {
                Log.w(TAG, "BLOCKING entry into FocusGuard Accessibility Details screen! (cls: $className)")
                handleSecurityTamper(
                    sourcePackage = pkg,
                    reason = "FocusGuard Accessibility Details Screen Opened",
                    targetName = "Accessibility Settings",
                    customToastMessage = "🚫 Disabling FocusGuard is restricted. Complete the friction challenge in the app."
                )
                return true
            }
        }

        // ── GUARD 4.2: DEVICE ADMIN DEACTIVATION TAMPER ──
        if (protectionWanted) {
            val isDeviceAdminScreen = className.contains("DeviceAdmin", ignoreCase = true) ||
                    className.contains("VivoSubSettings", ignoreCase = true) ||
                    className.contains("SubSettings", ignoreCase = true) ||
                    hasText(screenText, "device admin", "admin apps", "device administrators", "this administrator is active")
            val isDeactivationScreen = hasText(
                screenText,
                "deactivate",
                "remove",
                "uninstall",
                "turn off",
                "disable this device admin"
            )
            val targetsFocusGuardInAdmin = hasText(screenText, "focusguard", "com.focusguard.app")

            val clickedFocusGuardOrDeactivate = (e.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) &&
                    (e.text.any { it.toString().contains("focusguard", ignoreCase = true) || it.toString().contains("deactivate", ignoreCase = true) } ||
                     e.contentDescription?.contains("focusguard", ignoreCase = true) == true ||
                     e.contentDescription?.contains("deactivate", ignoreCase = true) == true ||
                     (try { e.source } catch (_: Exception) { null })?.text?.toString()?.contains("focusguard", ignoreCase = true) == true ||
                     (try { e.source } catch (_: Exception) { null })?.text?.toString()?.contains("deactivate", ignoreCase = true) == true)

            val isDirectDeactivateClick = (e.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) && (
                e.text.any { it.toString().contains("deactivate", ignoreCase = true) } ||
                e.contentDescription?.contains("deactivate", ignoreCase = true) == true ||
                (try { e.source } catch (_: Exception) { null })?.text?.toString()?.contains("deactivate", ignoreCase = true) == true
            )

            // Block immediately on:
            // 1. Any click on "Deactivate" button anywhere in Settings
            // 2. Any click on FocusGuard item in Device Admin screens
            // 3. Any arrival on FocusGuard deactivation details page
            if (isDirectDeactivateClick ||
                (isDeviceAdminScreen && (isDeactivationScreen || clickedFocusGuardOrDeactivate) && (targetsFocusGuardInAdmin || clickedFocusGuardOrDeactivate)) ||
                (targetsFocusGuardInAdmin && isDeactivationScreen)) {
                Log.w(TAG, "BLOCKING Device Admin tamper for FocusGuard! (cls: $className, event: ${e.eventType})")
                handleSecurityTamper(
                    sourcePackage = pkg,
                    reason = "Device Admin Screen targeting FocusGuard",
                    targetName = "Device Administrator",
                    customToastMessage = "🚫 Deactivating FocusGuard Device Admin is protected. Complete the friction challenge in the app."
                )
                return true
            }
        }

        // ── GUARD 4.3: APP INFO / FORCE STOP / CLEAR STORAGE TAMPER ──
        if (protectionWanted) {
            val isDeactivationTamper = hasText(
                screenText,
                "deactivate",
                "disable this device admin",
                "device administrator"
            ) || className.contains("DeviceAdmin", ignoreCase = true)

            val targetsFocusGuardInAdmin = isDeactivationTamper && hasText(screenText, "focusguard", "com.focusguard.app")

            val isAppDetailsScreen = className.contains("InstalledAppDetails", ignoreCase = true) ||
                    className.contains("AppInfo", ignoreCase = true) ||
                    className.contains("AppStorageSettings", ignoreCase = true) ||
                    className.contains("ManageSpaceActivity", ignoreCase = true) ||
                    className.contains("AppButtonsPreferenceController", ignoreCase = true) ||
                    hasText(screenText, "force stop", "clear data", "clear storage", "manage space")

            val isSpecificallyFocusGuardApp = isAppDetailsScreen && isFocusGuardAppDetailsPage(rootNode)

            if (targetsFocusGuardInAdmin || isSpecificallyFocusGuardApp) {
                val targetName = if (isDeactivationTamper) "Device Administrator" else "Security Settings"
                val tamperBlockType = if (isDeactivationTamper) OverlayBlockType.SETTINGS_TAMPER else OverlayBlockType.UNINSTALL_ATTEMPT
                Log.w(TAG, "BLOCKING settings tamper: $targetName")
                handleSecurityTamper(
                    sourcePackage = pkg,
                    reason = "FocusGuard Tampering Detected in Settings [$targetName]",
                    targetName = targetName,
                    blockType = tamperBlockType,
                    customToastMessage = "🚫 Modifying FocusGuard settings or storage is restricted. Complete the friction challenge in the app."
                )
                return true
            }
        }

        // ── GUARD 4.4: HIDE APPS, PRIVATE SPACE & STEALTH MODE IN SETTINGS ──
        val isHideAppsSettings = className.contains("HideApp", ignoreCase = true) ||
                className.contains("AppHide", ignoreCase = true) ||
                className.contains("HiddenApp", ignoreCase = true) ||
                className.contains("PrivateSpace", ignoreCase = true) ||
                className.contains("AppHider", ignoreCase = true) ||
                (rootNode != null && containsKeywords(
                    rootNode,
                    "Hide apps",
                    "Hidden apps",
                    "Hide app",
                    "App hider",
                    "Hide icons",
                    "Private space",
                    "Hide apps on Home and Apps screens",
                    "Hide apps on Home",
                    "App lock & hide",
                    "App lock and hide",
                    "App hiding",
                    "Privacy and app encryption",
                    "Hide desktop icons",
                    "Secret apps",
                    "Stealth mode"
                ))

        if ((protectionWanted || sessionBlockManager.isSessionCurrentlyActive()) && isHideAppsSettings) {
            Log.w(TAG, "BLOCKING Hide Apps / Private Space tamper attempt in Settings ($pkg - $className)!")
            handleSecurityTamper(
                sourcePackage = pkg,
                reason = "Hide Apps / Private Space Access Blocked in Settings",
                targetName = "Hide Apps Protection",
                customToastMessage = "🚫 Hiding apps is restricted to prevent bypassing FocusGuard protection."
            )
            return true
        }

        // ── GUARD 4.5: OEM DUAL APPS / CLONER SETTINGS INTERCEPT ──
        if (repository.isFastAntiCloningShieldEnabled()) {
            val isProtectionActive = protectionWanted
            val isSessionActive = sessionBlockManager.isSessionCurrentlyActive()
            if (isProtectionActive || isSessionActive) {
                val isVivoDoubleInstance = pkg == "com.vivo.doubleinstance" || className.contains("DoubleInstance", ignoreCase = true)
                val isDualAppScreen = isVivoDoubleInstance || AntiClonerEngine.isDualAppsSettingsScreen(rootNode, className, pkg)
                if (isDualAppScreen) {
                    val appLabel = getCachedAppLabel(pkg)
                    Log.w(TAG, "ANTI-CLONING INTERCEPT: OEM Dual Apps Settings blocked: $pkg ($appLabel)")
                    handleSecurityTamper(
                        sourcePackage = pkg,
                        reason = "Dual Apps Settings Blocked in $appLabel",
                        targetName = "App Cloning Prohibited",
                        customToastMessage = "🚫 App cloning and dual apps are prohibited while FocusGuard protection is active."
                    )
                    return true
                }
            }
        }
        // ── GUARD 4.6: PRIVATE DNS SETTINGS INTERCEPTION (Layer 3 — Adult Content Shield) ──
        // When Adult Content Shield is ON, block navigation to Private DNS settings.
        // A custom DoH/DoT hostname in Private DNS would silently bypass the Layer 1 DNS VPN.
        if (repository.isFastAdultContentBlockEnabled()) {
            val isPrivateDnsScreen = when {
                (pkg == "com.android.settings" || pkg.contains("settings", ignoreCase = true)) -> {
                    className.contains("PrivateDns", ignoreCase = true) ||
                    (rootNode != null && containsKeywords(rootNode, "Private DNS", "Private dns"))
                }
                else -> false
            }
            if (isPrivateDnsScreen) {
                Log.w(TAG, "GUARD 4.6: Private DNS settings intercepted — adult content shield active")
                handleSecurityTamper(
                    sourcePackage = pkg,
                    reason = "Private DNS Settings Blocked",
                    targetName = "🔞 Adult Content Shield",
                    customToastMessage = "⛔ Changing Private DNS is blocked while the Adult Content Shield is active."
                )
            }
        }

        return true
    }

    private fun processTargetAppEvent(
        rootNode: AccessibilityNodeInfo?,
        e: AccessibilityEvent,
        pkg: String,
        className: String,
        nowRealtime: Long,
        isPackageChanged: Boolean,
        isWindowTransition: Boolean,
        protectionWanted: Boolean,
        isDeviceAdminCurrentlyActive: Boolean
    ) {
        // Monotonic Debounce to avoid multi-triggering overlay in same transition
        if (!isPackageChanged && (nowRealtime - lastBlockTimestamp < 1500L)) return

        // Manage In-Foreground package transitions
        if (isPackageChanged) {
            val prevPkg = currentForegroundPackage
            val prevStart = currentForegroundStartTime
            if (prevPkg != null && prevStart > 0L && !isSystemOrLauncherPackage(prevPkg)) {
                val sessionDuration = nowRealtime - prevStart
                if (sessionDuration > 200L) {
                    timeLimitEvaluator.recordSessionUsage(prevPkg, sessionDuration)
                }
                if (mindfulRepository.isPackageMindful(prevPkg)) {
                    mindfulRepository.recordSessionEnded(prevPkg)
                }
            }

            cancelForegroundPolling()
            currentForegroundPackage = pkg
            currentForegroundStartTime = nowRealtime

            val isBrowser = browserUrlTracker.isSupportedBrowser(pkg)
            val isYt = browserUrlTracker.isYouTubePackage(pkg)
            val isReelTarget = reelBlocker.isTargetPackage(pkg)
            val isReelBlockedForPkg = when (pkg) {
                ReelBlocker.INSTAGRAM_PACKAGE -> repository.isFastInstagramReelsBlockEnabled() || repository.isFastShortsReelsBlockEnabled()
                ReelBlocker.FACEBOOK_PACKAGE, ReelBlocker.FACEBOOK_LITE_PACKAGE -> repository.isFastFacebookReelsBlockEnabled() || repository.isFastShortsReelsBlockEnabled()
                ReelBlocker.YOUTUBE_PACKAGE -> repository.isFastYouTubeShortsBlockEnabled() || repository.isFastShortsReelsBlockEnabled()
                else -> repository.isFastShortsReelsBlockEnabled()
            }
            val hasKeywords = repository.getFastBlockedKeywords().isNotEmpty()
            val shouldStartHeartbeat = (isBrowser || isYt || (isReelTarget && isReelBlockedForPkg) || hasKeywords) &&
                    !browserUrlTracker.isMessagingOrChatPackage(pkg)

            if (shouldStartHeartbeat) {
                browserHeartbeatTracker.startHeartbeat(
                    scope = scope,
                    packageName = pkg,
                    isOverlayShowing = { overlayManager.isOverlayShowing },
                    rootProvider = { rootInActiveWindow },
                    onViolation = { sourcePkg, matchedKeyword, _ ->
                        val appLabel = getCachedAppLabel(sourcePkg)
                        val isYtViol = browserUrlTracker.isYouTubePackage(sourcePkg)
                        val isBrowserViol = browserUrlTracker.isSupportedBrowser(sourcePkg)

                        if (matchedKeyword == "YouTube Shorts" || matchedKeyword == "Instagram Reels" || matchedKeyword == "Facebook Reels") {
                            Log.i(TAG, "Heartbeat caught active $matchedKeyword in $sourcePkg -> displaying overlay and closing app")
                            val desc = when (matchedKeyword) {
                                "Instagram Reels" -> "Instagram Reels is blocked to protect your focus. Feed, profile, and direct messages remain accessible."
                                "Facebook Reels" -> "Facebook Reels is blocked to protect your focus. Feed, groups, and messages remain accessible."
                                else -> "YouTube Shorts is blocked to protect your focus. Normal videos and search remain accessible."
                            }
                            triggerBlock(
                                sourcePackage = sourcePkg,
                                reason = "$matchedKeyword Blocked",
                                blockType = OverlayBlockType.SHORT_OR_REEL_BLOCK,
                                targetName = matchedKeyword,
                                description = desc
                            )
                            return@startHeartbeat
                        }

                        val blockReason = when {
                            isYtViol -> "YouTube Search Blocked: '$matchedKeyword'"
                            isBrowserViol -> "Keyword Blocked: '$matchedKeyword'"
                            else -> "Keyword Blocked in $appLabel: '$matchedKeyword'"
                        }
                        val blockDesc = when {
                            isYtViol -> "Searching for '$matchedKeyword' is blocked in YouTube to protect your focus."
                            isBrowserViol -> "Access to '$matchedKeyword' is blocked across all browsers."
                            else -> "Searching for or typing '$matchedKeyword' is blocked in $appLabel."
                        }
                        triggerBlock(
                            sourcePackage = sourcePkg,
                            reason = blockReason,
                            blockType = OverlayBlockType.KEYWORD_BLOCK,
                            targetName = matchedKeyword,
                            description = blockDesc
                        )
                    }
                )
            } else {
                browserHeartbeatTracker.stopHeartbeat()
            }
        }

        // ── 1. FAST-PATH: INSTANT HARD APP BLOCKER, EXCEEDED TIME LIMITS & SCHEDULES (0ms latency) ─
        val eventContext = com.focusguard.app.core.model.EventContext(
            packageName = pkg,
            className = className,
            eventType = e.eventType,
            monotonicTimeMs = nowRealtime
        )
        val engineDecision = blockDecisionEngine.evaluate(eventContext, rulesSnapshotHolder.current)
        val isEngineBlocked = engineDecision is com.focusguard.app.core.model.BlockDecision.Block
        val isHardBlocked = isEngineBlocked || blockedPackages.contains(pkg) || blockedPackages.contains(pkg.lowercase()) || repository.isFastBlockedApp(pkg)
        val isTimeLimitExceeded = repository.isFastTimeLimitExceeded(pkg)
        val activeSchedules = repository.getFastActiveSchedules()
        val scheduleBlock = if (activeSchedules.isNotEmpty()) {
            scheduleEvaluator.isBlockedBySchedule(
                packageName = pkg,
                schedules = activeSchedules,
                isAppBlockedInBlocklist = isHardBlocked
            )
        } else null

        if ((isHardBlocked || isTimeLimitExceeded || scheduleBlock != null) && !sessionBlockManager.isAlwaysAllowedPackage(pkg)) {
            lastBlockTimestamp = nowRealtime
            val appLabel = getCachedAppLabel(pkg)
            val isLimit = !isHardBlocked && isTimeLimitExceeded && scheduleBlock == null
            val isSched = scheduleBlock != null && !isHardBlocked
            val limitEntity = if (isLimit) repository.getFastTimeLimits()[pkg.lowercase()] else null
            val limitMins = limitEntity?.dailyLimitMinutes ?: 0

            val blockType = when {
                isSched -> OverlayBlockType.SCHEDULE_BLOCK
                isLimit -> OverlayBlockType.TIME_LIMIT_BLOCK
                else -> OverlayBlockType.APP_BLOCK
            }
            val reason = when {
                isSched -> "Focus Schedule Active: ${scheduleBlock?.scheduleTitle ?: "Scheduled"}"
                isLimit -> "Daily Time Limit Exceeded: $appLabel"
                else -> "App Blocked: $appLabel"
            }
            val description = when {
                isSched -> "Blocked by Focus Schedule '${scheduleBlock?.scheduleTitle}' until ${scheduleBlock?.endTimeFormatted}."
                isLimit -> "Daily cap of $limitMins min reached. Resets at midnight."
                else -> "This app is in your FocusGuard blocklist. Take a breath and stay on track!"
            }

            Log.i(TAG, "Instant 0ms Block triggered for $pkg ($appLabel) [isLimit=$isLimit, isSched=$isSched]")
            triggerBlock(
                sourcePackage = pkg,
                reason = reason,
                blockType = blockType,
                targetName = appLabel,
                description = description
            )
            return
        }

        // ── 1.2 MINDFUL PAUSE INTERCEPT (Conscious Breathing & Intent Delay) ─
        if (!isHardBlocked && mindfulRepository.isPackageMindful(pkg)) {
            if (mindfulRepository.isAllowanceActive(pkg)) {
                // Allowance is active! Let user proceed with their conscious work.
            } else if (!overlayManager.isOverlayShowing && (isPackageChanged || isWindowTransition)) {
                val appLabel = getCachedAppLabel(pkg)
                lastBlockTimestamp = nowRealtime
                Log.i(TAG, "Mindful Pause triggered for $pkg ($appLabel)")

                val sessionInfo = mindfulRepository.getSessionInfo(pkg)
                val nowWall = System.currentTimeMillis()
                val minutesSinceLast = if (sessionInfo.lastClosedTimestamp > 0L) {
                    ((nowWall - sessionInfo.lastClosedTimestamp) / 60000L).coerceAtLeast(0L).toInt()
                } else {
                    -1
                }
                // Mark as returning if app was closed previously today and within recent window
                val isReturning = sessionInfo.lastClosedTimestamp > 0L && minutesSinceLast in 0..120
                val displayMinutes = if (minutesSinceLast >= 0) minutesSinceLast else 0
                val todayCount = sessionInfo.todayOpenCount + 1

                overlayManager.showMindfulPauseOverlay(
                    packageName = pkg,
                    appName = appLabel,
                    breathingDurationSeconds = 6,
                    isReturning = isReturning,
                    minutesSinceLastSession = displayMinutes,
                    todayOpenCount = todayCount,
                    onAllowanceGranted = { durationMinutes, intentText, category ->
                        mindfulRepository.grantAllowance(pkg, appLabel, durationMinutes, intentText, category)
                        mindfulRepository.recordSessionStarted(pkg)
                        scheduleMindfulAllowanceExpiry(pkg, appLabel, durationMinutes)
                    },
                    onWalkAway = {
                        mindfulRepository.recordWalkedAway(pkg, appLabel)
                        mindfulRepository.recordSessionEnded(pkg)
                        forceCloseApp(pkg)
                    }
                )
                return
            }
        }

        // ── 1.5 AUTO-BLOCK NEW APPS JIT INTERCEPT (Catches recently installed apps on open) ─
        if (!isHardBlocked && repository.isFastAutoBlockNewAppsEnabled()) {
            if (!repository.isFastAppReviewed(pkg)) {
                val enabledTs = repository.getFastAutoBlockEnabledTimestamp()
                if (enabledTs > 0L) {
                    val installTime = packageInstallTimeCache.getOrPut(pkg) {
                        try {
                            packageManager.getPackageInfo(pkg, 0).firstInstallTime
                        } catch (e: Exception) { 0L }
                    }
                    val isRecentInstall = installTime >= enabledTs

                    if (isRecentInstall) {
                        val appLabel = getCachedAppLabel(pkg)
                        Log.i(TAG, "Auto-Block JIT triggered for new install: $pkg ($appLabel)")
                        scope.launch {
                            repository.blockApp(
                                packageName = pkg,
                                appName = appLabel,
                                isAutoBlockedNewInstall = true
                            )
                        }
                        lastBlockTimestamp = nowRealtime
                        triggerBlock(
                            sourcePackage = pkg,
                            reason = "Auto-Blocked New App: $appLabel",
                            blockType = OverlayBlockType.APP_BLOCK,
                            targetName = appLabel,
                            description = "This newly installed app was automatically blocked by FocusGuard. You can review and unblock it in the app."
                        )
                        return
                    }
                }
            }
        }

        // ── 1.6 UNSUPPORTED BROWSER GUARD (Kills Unknown Browser Loophole) ─
        if (repository.isFastBlockUnsupportedBrowsersEnabled()) {
            if (browserUrlTracker.isUnsupportedBrowser(pkg, this)) {
                val appLabel = getCachedAppLabel(pkg)
                lastBlockTimestamp = nowRealtime
                Log.i(TAG, "Unsupported browser blocked: $pkg ($appLabel)")
                triggerBlock(
                    sourcePackage = pkg,
                    reason = "Unsupported Browser Blocked: $appLabel",
                    blockType = OverlayBlockType.APP_BLOCK,
                    targetName = appLabel,
                    description = "Unsupported browser detected. FocusGuard only permits verified browsers (Chrome, Firefox, Brave, Opera, DuckDuckGo, Tor, Vivaldi, Spin, 1DM, Ecosia) for safe filtering."
                )
                return
            }
        }

        // ── ANTI-CLONING: STRUCTURAL CLONED TARGET VIEW ID SCAN (Layer 2) ─
        if (repository.isFastAntiCloningShieldEnabled()) {
            val isProtectionActive = protectionWanted
            val isSessionActive = sessionBlockManager.isSessionCurrentlyActive()
            if (isProtectionActive || isSessionActive) {
                if (AntiClonerEngine.isClonedTargetViewDetected(rootNode, pkg) { target -> repository.isFastAppBlocked(target) }) {
                    val appLabel = getCachedAppLabel(pkg)
                    lastBlockTimestamp = nowRealtime
                    Log.w(TAG, "ANTI-CLONING INTERCEPT: Cloned Distraction App Blocked: $pkg ($appLabel)")
                    triggerBlock(
                        sourcePackage = pkg,
                        reason = "Cloned Distraction App Blocked: $appLabel",
                        blockType = OverlayBlockType.CLONER_BLOCK,
                        targetName = "Cloned App ($appLabel)",
                        description = "Target app views were detected running inside an unverified container or modified APK clone."
                    )
                    return
                }
            }
        }

        // ── 1.8 ULTRA-FAST REELS INTERCEPT (< 1ms Fast Path, Zero Latency) ─
        if (reelBlocker.isTargetPackage(pkg)) {
            val isPlatformBlocked = when (pkg) {
                ReelBlocker.INSTAGRAM_PACKAGE -> repository.isFastInstagramReelsBlockEnabled() || repository.isFastShortsReelsBlockEnabled()
                ReelBlocker.FACEBOOK_PACKAGE, ReelBlocker.FACEBOOK_LITE_PACKAGE -> repository.isFastFacebookReelsBlockEnabled() || repository.isFastShortsReelsBlockEnabled()
                ReelBlocker.YOUTUBE_PACKAGE -> repository.isFastYouTubeShortsBlockEnabled() || repository.isFastShortsReelsBlockEnabled()
                else -> repository.isFastShortsReelsBlockEnabled()
            }
            if (isPlatformBlocked) {
                if (reelBlocker.isReelsOrShortsDetected(e, rootNode, pkg)) {
                    if (!overlayManager.isOverlayShowing && (nowRealtime - lastBlockTimestamp >= 800L)) {
                        lastBlockTimestamp = nowRealtime
                        val targetLabel = when (pkg) {
                            ReelBlocker.INSTAGRAM_PACKAGE -> "Instagram Reels"
                            ReelBlocker.FACEBOOK_PACKAGE, ReelBlocker.FACEBOOK_LITE_PACKAGE -> "Facebook Reels"
                            else -> "YouTube Shorts"
                        }
                        val targetDesc = when (pkg) {
                            ReelBlocker.INSTAGRAM_PACKAGE -> "Instagram Reels is blocked to protect your focus. Feed, profile, and direct messages remain accessible."
                            ReelBlocker.FACEBOOK_PACKAGE, ReelBlocker.FACEBOOK_LITE_PACKAGE -> "Facebook Reels is blocked to protect your focus. Feed, groups, and messages remain accessible."
                            else -> "YouTube Shorts is blocked to protect your focus. Normal videos and search remain accessible."
                        }
                        Log.i(TAG, "Ultra-fast $targetLabel block triggered for $pkg")
                        triggerBlock(
                            sourcePackage = pkg,
                            reason = "$targetLabel Blocked",
                            blockType = OverlayBlockType.SHORT_OR_REEL_BLOCK,
                            targetName = targetLabel,
                            description = targetDesc
                        )
                        return
                    }
                }
            }
        }

        // ── 2. FOCUS SCHEDULES & TIME LIMITS (Evaluated only on window transition) ─
        if ((isPackageChanged || isWindowTransition) && repository.hasActiveSchedulesOrLimits(pkg)) {
            val appLabel = getCachedAppLabel(pkg)

            scope.launch {
                val liveSessionMs = if (currentForegroundStartTime > 0L) {
                    (android.os.SystemClock.elapsedRealtime() - currentForegroundStartTime).coerceAtLeast(0L)
                } else 0L

                when (val decision = repository.evaluateAccess(pkg, appLabel, liveSessionMs)) {
                    is AccessDecision.BlockedBySchedule -> {
                        if (overlayManager.isOverlayShowing || (android.os.SystemClock.elapsedRealtime() - lastBlockTimestamp < 1500L)) return@launch
                        lastBlockTimestamp = android.os.SystemClock.elapsedRealtime()
                        Log.i(TAG, "Focus Schedule block triggered for $pkg: '${decision.scheduleTitle}' until ${decision.endTimeFormatted}")
                        triggerBlock(
                            sourcePackage = pkg,
                            reason = "Focus Schedule Blocked: $appLabel (${decision.scheduleTitle})",
                            blockType = OverlayBlockType.SCHEDULE_BLOCK,
                            targetName = appLabel,
                            description = "Blocked by Focus Schedule '${decision.scheduleTitle}' until ${decision.endTimeFormatted}."
                        )
                    }
                    is AccessDecision.BlockedByTimeLimit -> {
                        repository.markTimeLimitExceeded(pkg)
                        if (overlayManager.isOverlayShowing || (android.os.SystemClock.elapsedRealtime() - lastBlockTimestamp < 1500L)) return@launch
                        lastBlockTimestamp = android.os.SystemClock.elapsedRealtime()
                        Log.i(TAG, "Time Limit block triggered for $pkg: ${decision.usedMinutes}/${decision.limitMinutes}m")
                        triggerBlock(
                            sourcePackage = pkg,
                            reason = "Daily Time Limit Exceeded: $appLabel (${decision.usedMinutes}/${decision.limitMinutes}m)",
                            blockType = OverlayBlockType.TIME_LIMIT_BLOCK,
                            targetName = appLabel,
                            description = "Daily cap of ${decision.limitMinutes} min reached (${decision.usedMinutes} min used). Resets at midnight."
                        )
                    }
                    is AccessDecision.BlockedByAppBlocker -> {
                        if (overlayManager.isOverlayShowing || (android.os.SystemClock.elapsedRealtime() - lastBlockTimestamp < 1500L)) return@launch
                        lastBlockTimestamp = android.os.SystemClock.elapsedRealtime()
                        Log.i(TAG, "Hard App Blocker triggered for $pkg")
                        triggerBlock(
                            sourcePackage = pkg,
                            reason = "App Blocked: $appLabel",
                            blockType = OverlayBlockType.APP_BLOCK,
                            targetName = appLabel,
                            description = "This app is in your FocusGuard blocklist. Take a breath and stay on track!"
                        )
                    }
                    AccessDecision.Allowed -> {
                        startForegroundPollingIfTracked(pkg, appLabel)
                    }
                }
            }
        }

        // ── 3. KEYWORD & CONTENT BLOCKING (Browsers, YouTube On-Screen Metadata, and Universal Apps) ─
        if (!browserUrlTracker.isMessagingOrChatPackage(pkg)) {
            if (browserUrlTracker.isYouTubePackage(pkg)) {
                val ytItems = browserUrlTracker.extractYouTubeMetadata(rootNode, e)
                for (item in ytItems) {
                    if (item.text.length >= 2) {
                        if (repository.isFastAdultContentBlockEnabled()) {
                            val adultViolation = adultContentShield.checkViolation(item.text)
                            if (adultViolation != null) {
                                Log.i(TAG, "YouTube adult violation: '${adultViolation.matchedTerm}' in '${item.text}'")
                                triggerBlock(
                                    sourcePackage = pkg,
                                    reason = "Explicit YouTube Content Blocked",
                                    blockType = OverlayBlockType.KEYWORD_BLOCK,
                                    targetName = "🔞 Adult Content Shield",
                                    description = adultViolation.reason
                                )
                                break
                            }
                        }
                        val matchedKeyword = keywordBlocker.isViolationDetectedSync(item.text)
                        if (matchedKeyword != null) {
                            val (blockReason, blockDesc) = when (item.type) {
                                BrowserUrlTracker.YouTubeContentType.CHANNEL_NAME ->
                                    "YouTube Channel Blocked: '$matchedKeyword'" to
                                    "Videos from channel '$matchedKeyword' are blocked to protect your focus."
                                BrowserUrlTracker.YouTubeContentType.VIDEO_TITLE ->
                                    "YouTube Video Blocked: '$matchedKeyword'" to
                                    "The video containing '$matchedKeyword' in its title is blocked to protect your focus."
                                BrowserUrlTracker.YouTubeContentType.DESCRIPTION ->
                                    "YouTube Description Blocked: '$matchedKeyword'" to
                                    "Video with description containing '$matchedKeyword' is blocked to protect your focus."
                                BrowserUrlTracker.YouTubeContentType.SEARCH_QUERY ->
                                    "YouTube Search Blocked: '$matchedKeyword'" to
                                    "Searching for '$matchedKeyword' is blocked in YouTube to protect your focus."
                                BrowserUrlTracker.YouTubeContentType.SEARCH_SUGGESTION ->
                                    "YouTube Suggestion Blocked: '$matchedKeyword'" to
                                    "Search suggestion containing '$matchedKeyword' was blocked to protect your focus."
                            }
                            Log.i(TAG, "YouTube keyword violation ($blockReason): '$matchedKeyword' in '${item.text}'")
                            triggerBlock(
                                sourcePackage = pkg,
                                reason = blockReason,
                                blockType = OverlayBlockType.KEYWORD_BLOCK,
                                targetName = matchedKeyword,
                                description = blockDesc
                            )
                            break
                        }
                    }
                }
            } else if (browserUrlTracker.isSupportedBrowser(pkg)) {
                if (repository.isFastIncognitoBlockEnabled() &&
                    browserUrlTracker.isIncognitoOrPrivateTab(rootNode, e, pkg)
                ) {
                    Log.i(TAG, "Incognito / Private tab blocked in $pkg")
                    triggerBlock(
                        sourcePackage = pkg,
                        reason = "Incognito Mode Blocked",
                        blockType = OverlayBlockType.INCOGNITO_BLOCK,
                        targetName = "Incognito Mode",
                        description = "Private and Incognito browsing is blocked by FocusGuard to keep you accountable."
                    )
                    return
                }

                val browserStrings = browserUrlTracker.extractBrowserStrings(rootNode, e, pkg)
                for (str in browserStrings) {
                    if (str.length >= 2) {
                        if (repository.isFastAdultContentBlockEnabled()) {
                            val adultViolation = adultContentShield.checkViolation(str)
                            if (adultViolation != null) {
                                Log.i(TAG, "Browser adult violation: '${adultViolation.matchedTerm}' in '$str'")
                                triggerBlock(
                                    sourcePackage = pkg,
                                    reason = "Adult Content Blocked: '${adultViolation.matchedTerm}'",
                                    blockType = OverlayBlockType.KEYWORD_BLOCK,
                                    targetName = "🔞 Adult Content Shield",
                                    description = adultViolation.reason
                                )
                                break
                            }
                        }
                        val matchedKeyword = keywordBlocker.isViolationDetectedSync(str)
                        if (matchedKeyword != null) {
                            Log.i(TAG, "Browser keyword violation: '$matchedKeyword' in '$str'")
                            triggerBlock(
                                sourcePackage = pkg,
                                reason = "Website/Search Blocked: '$matchedKeyword'",
                                blockType = OverlayBlockType.KEYWORD_BLOCK,
                                targetName = matchedKeyword,
                                description = "Access to '$matchedKeyword' is blocked across all browsers."
                            )
                            break
                        }
                    }
                }
            } else {
                val queryToInspect = browserUrlTracker.extractUniversalSearchOrInputQuery(rootNode, e)
                if (!queryToInspect.isNullOrBlank() && queryToInspect.length >= 2) {
                    if (repository.isFastAdultContentBlockEnabled()) {
                        val adultViolation = adultContentShield.checkViolation(queryToInspect)
                        if (adultViolation != null) {
                            val appLabel = getCachedAppLabel(pkg)
                            Log.i(TAG, "Universal search adult violation in $appLabel ($pkg): '${adultViolation.matchedTerm}'")
                            triggerBlock(
                                sourcePackage = pkg,
                                reason = "Adult Search Blocked in $appLabel",
                                blockType = OverlayBlockType.KEYWORD_BLOCK,
                                targetName = "🔞 Adult Content Shield",
                                description = adultViolation.reason
                            )
                            return
                        }
                    }
                    val matchedKeyword = keywordBlocker.isViolationDetectedSync(queryToInspect)
                    if (matchedKeyword != null) {
                        val appLabel = getCachedAppLabel(pkg)
                        Log.i(TAG, "Universal search keyword violation in $appLabel ($pkg): '$matchedKeyword' in '$queryToInspect'")
                        triggerBlock(
                            sourcePackage = pkg,
                            reason = "Keyword Blocked in $appLabel: '$matchedKeyword'",
                            blockType = OverlayBlockType.KEYWORD_BLOCK,
                            targetName = matchedKeyword,
                            description = "Searching for or typing '$matchedKeyword' is blocked in $appLabel."
                        )
                    }
                }
            }
        }
    }

    private fun isKeyboardPackage(pkg: String): Boolean {
        val lower = pkg.lowercase()
        return lower.contains("inputmethod") ||
                lower.contains("keyboard") ||
                lower.contains("honeyboard") ||
                lower.contains("swiftkey") ||
                lower.contains("gboard")
    }

    private fun isTransientSystemPackage(pkg: String): Boolean {
        val lower = pkg.lowercase()
        val isProtectionActive = isDeviceAdminActive() && repository.isFastUninstallProtectionEnabled()
        if (isProtectionActive && (lower.contains("permissioncontroller") || lower == "android")) {
            return false
        }
        return lower == "android" ||
                lower == "com.android.systemui" ||
                lower.startsWith("com.android.providers.") ||
                lower.startsWith("com.google.android.gms") ||
                lower.startsWith("com.google.android.gsf") ||
                lower.contains("permissioncontroller") ||
                lower.contains("screenshot") ||
                isKeyboardPackage(pkg)
    }

    private fun isSystemOrLauncherPackage(pkg: String): Boolean {
        // Known cloner or dual app packages are only treated as non-system when protection is actively armed
        val isProtectionActive = isDeviceAdminActive() && repository.isFastUninstallProtectionEnabled()
        val isSessionActive = sessionBlockManager.isSessionCurrentlyActive()
        if ((isProtectionActive || isSessionActive) && AntiClonerEngine.isClonerPackage(pkg)) return false

        val lower = pkg.lowercase()
        return lower == "android" ||
                lower == "com.android.systemui" ||
                launcherDetector.isLauncherPackage(pkg) ||
                lower.contains("launcher") ||
                lower.contains("quickstep") ||
                lower.contains("trebuchet") ||
                lower.contains("nexuslauncher") ||
                lower.contains("keyboard") ||
                lower.contains("inputmethod") ||
                lower.contains("clock") ||
                lower.contains("alarm") ||
                lower.contains("wellbeing") ||
                lower.contains("wellness") ||
                lower.contains("dialer") ||
                lower.contains("incallui") ||
                lower.contains("telecom") ||
                lower.contains("telephony") ||
                lower.contains("phone") ||
                lower.contains("truecaller") ||
                lower == "com.callapp.contacts" ||
                lower == "com.eycon.android" ||
                lower == "com.syncme.syncmeapp" ||
                lower.contains("callerid") ||
                // SECURITY FIX: Use structured match instead of broad `contains("settings")` which
                // allowed any app named e.g. "mysettings.bypass" to skip all time limits.
                isKnownSettingsPackage(lower) ||
                lower.contains("safecenter") ||
                lower.contains("permissioncontroller") ||
                lower.contains("applock") ||
                lower.contains("phonemaster") ||
                lower == "com.oneplus.security" ||
                lower == "com.iqoo.secure" ||
                lower.startsWith("com.android.providers.") ||
                lower.startsWith("com.google.android.gms") ||
                lower.startsWith("com.google.android.gsf") ||
                lower.startsWith("com.google.android.packageinstaller") ||
                lower.startsWith("com.android.packageinstaller") ||
                lower.startsWith("com.focusguard.app")
    }

    /**
     * Task 3: In-Foreground Polling & Real-Time Monitoring (< 1-second accuracy)
     * Tracks the live continuous session and triggers immediately when the
     * daily time limit expires or a scheduled focus window begins while the user is actively inside the app.
     */
    private fun startForegroundPollingIfTracked(pkg: String, appLabel: String) {
        if (isSystemOrLauncherPackage(pkg)) return
        if (foregroundPollingJob?.isActive == true && currentForegroundPackage == pkg) return

        foregroundPollingJob?.cancel()

        val limitEntity = repository.getFastTimeLimits()[pkg.lowercase()]
        val hasLimit = limitEntity != null && limitEntity.isEnabled
        val hasSchedules = repository.getFastActiveSchedules().isNotEmpty()

        if (!hasLimit && !hasSchedules) return

        // Snapshot the exact baseline usage at the start of this continuous foreground session
        val sessionBaselineMs = timeLimitEvaluator.getTodayUsageMillis(pkg)
        val limitMs = (limitEntity?.dailyLimitMinutes ?: 0) * 60_000L

        foregroundPollingJob = scope.launch {
            Log.d(TAG, "Started in-foreground real-time monitoring for $pkg ($appLabel) [Base=$sessionBaselineMs ms, Limit=$limitMs ms]")
            val sessionStartTime = if (currentForegroundStartTime > 0L) currentForegroundStartTime else android.os.SystemClock.elapsedRealtime()

            while (isActive && currentForegroundPackage == pkg) {
                val liveSessionMs = (android.os.SystemClock.elapsedRealtime() - sessionStartTime).coerceAtLeast(0L)

                // 1. Millisecond-accurate daily time limit evaluation (Zero Binder IPC in loop!)
                if (hasLimit) {
                    val totalUsedMs = sessionBaselineMs + liveSessionMs
                    val remainingMs = limitMs - totalUsedMs

                    if (remainingMs <= 0L) {
                        repository.markTimeLimitExceeded(pkg)
                        Log.i(TAG, "In-foreground real-time monitoring caught time limit reached for $pkg: $totalUsedMs ms >= $limitMs ms")
                        val usedMinutes = (totalUsedMs / 60_000L).toInt().coerceAtLeast(limitEntity.dailyLimitMinutes)
                        triggerBlock(
                            sourcePackage = pkg,
                            reason = "Daily Time Limit Exceeded (In-Foreground): $appLabel",
                            blockType = OverlayBlockType.TIME_LIMIT_BLOCK,
                            targetName = appLabel,
                            description = "Daily cap of ${limitEntity.dailyLimitMinutes} min reached ($usedMinutes min used). Resets at midnight."
                        )
                        break
                    }
                }

                // 2. Schedule boundary evaluation (0ms fast-path check)
                if (hasSchedules) {
                    val isAppInBlocklist = repository.isFastBlockedApp(pkg)
                    val scheduleBlock = scheduleEvaluator.isBlockedBySchedule(
                        packageName = pkg,
                        schedules = repository.getFastActiveSchedules(),
                        isAppBlockedInBlocklist = isAppInBlocklist
                    )
                    if (scheduleBlock != null) {
                        Log.i(TAG, "In-foreground real-time monitoring caught schedule boundary for $pkg")
                        triggerBlock(
                            sourcePackage = pkg,
                            reason = "Focus Schedule Active (In-Foreground): $appLabel",
                            blockType = OverlayBlockType.SCHEDULE_BLOCK,
                            targetName = appLabel,
                            description = "Blocked by Focus Schedule '${scheduleBlock.scheduleTitle}' until ${scheduleBlock.endTimeFormatted}."
                        )
                        break
                    }
                }

                // 3. Adaptive delay: checks every 1000ms, or exact milliseconds if less than 1s remaining.
                // FIX H7: Use monotonic SystemClock.elapsedRealtime() exclusively — mixing
                // System.currentTimeMillis() (wall clock) with elapsedRealtime() baselines was
                // inconsistent and could be skewed by clock tampering.
                val delayTime = if (hasLimit) {
                    val liveElapsed = (android.os.SystemClock.elapsedRealtime() - sessionStartTime).coerceAtLeast(0L)
                    val totalMs = sessionBaselineMs + liveElapsed
                    val remaining = limitMs - totalMs
                    if (remaining > 1000L) 1000L else remaining.coerceIn(100L, 1000L)
                } else {
                    5000L
                }

                delay(delayTime)
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Fast Keyword Traversal
    // ─────────────────────────────────────────────────────────────────────────
    /**
     * Ultra-fast single-pass BFS text extraction (< 0.5ms).
     */
    private fun extractScreenTextFast(node: AccessibilityNodeInfo?, maxNodes: Int = 150): String {
        if (node == null) return ""
        val sb = StringBuilder(512)
        var count = 0
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(node)
        while (queue.isNotEmpty() && count < maxNodes) {
            val current = queue.removeFirst()
            count++
            current.text?.let { sb.append(it).append(' ') }
            current.contentDescription?.let { sb.append(it).append(' ') }
            val childCount = current.childCount.coerceAtMost(50)
            for (i in 0 until childCount) {
                val child = try { current.getChild(i) } catch (_: Throwable) { null }
                if (child != null) {
                    queue.add(child)
                }
            }
            if (current != node) {
                SafeNodeUtils.safeRecycle(current)
            }
        }
        return sb.toString().lowercase()
    }

    private fun hasText(text: String, vararg keywords: String): Boolean {
        if (text.isEmpty()) return false
        for (kw in keywords) {
            if (text.contains(kw.lowercase())) return true
        }
        return false
    }

    /**
     * Inspects the active node tree using single-pass in-memory extraction.
     * ZERO Binder IPC round-trips (< 0.5ms) across deeply nested OEM settings hierarchies.
     */
    private fun hasTextAcrossTree(node: AccessibilityNodeInfo?, vararg keywords: String): Boolean {
        if (node == null) return false
        val text = extractScreenTextFast(node)
        return hasText(text, *keywords)
    }

    private fun containsKeywords(
        node: AccessibilityNodeInfo?,
        vararg keywords: String,
        maxDepth: Int = 8,
        currentDepth: Int = 0,
        nodesVisited: java.util.concurrent.atomic.AtomicInteger = java.util.concurrent.atomic.AtomicInteger(0)
    ): Boolean {
        if (node == null) return false
        val text = extractScreenTextFast(node)
        return hasText(text, *keywords)
    }

    /**
     * Determines whether the active Settings App Info page specifically belongs to FocusGuard.
     * Prevents false-positive clear data / storage blocks when viewing third-party apps.
     */
    private fun isFocusGuardAppDetailsPage(rootNode: AccessibilityNodeInfo?): Boolean {
        if (rootNode == null) return false
        val titleIds = listOf(
            "com.android.settings:id/entity_header_title",
            "android:id/title",
            "com.android.settings:id/app_title",
            ":id/entity_header_title",
            ":id/app_name",
            ":id/title"
        )
        for (id in titleIds) {
            try {
                val nodes = rootNode.findAccessibilityNodeInfosByViewId(id)
                val matches = nodes.any { node ->
                    val text = node.text?.toString() ?: ""
                    text.equals("FocusGuard", ignoreCase = true) || text.equals("com.focusguard.app", ignoreCase = true)
                }
                nodes.forEach { SafeNodeUtils.safeRecycle(it) }
                if (matches) return true
            } catch (_: Exception) {}
        }
        return false
    }

    /**
     * Determines whether the current active window or dialog is an uninstall/removal prompt targeting FocusGuard.
     * Works natively across all Android versions and OEM skins (Vivo, Samsung, Xiaomi, Pixel, etc.).
     */
    private fun isUninstallConfirmationForFocusGuard(rootNode: AccessibilityNodeInfo?): Boolean {
        if (rootNode == null) return false

        // CRITICAL GUARD 1: Accessibility settings & service activation screens must NEVER be treated as uninstall prompts!
        val isA11yScreen = containsKeywords(
            rootNode,
            "Accessibility",
            "Screen Protection",
            "Use FocusGuard",
            "Turn on FocusGuard",
            "Allow FocusGuard",
            "Installed apps",
            "Downloaded apps",
            "full control of your device"
        )
        if (isA11yScreen) {
            return false
        }

        val pkg = rootNode.packageName?.toString()?.lowercase() ?: ""
        val isLauncher = launcherDetector.isLauncherPackage(pkg) || pkg.contains("launcher")

        // Must have confirmation buttons ("Uninstall", "Delete")
        val hasActionBtn = containsKeywords(rootNode, "Uninstall", "Delete")
        if (!hasActionBtn) return false

        // Check if there is a Cancel/negative button (essential to distinguish dialogs from menus)
        val hasCancelBtn = containsKeywords(rootNode, "Cancel", "Keep", "No")

        // Confirmation dialogs ALWAYS have a cancel/dismiss option
        if (!hasCancelBtn) return false

        // 1. Direct title/message/app_name node check across all Android versions and OEMs (Samsung OneUI, Vivo, Pixel, etc.)
        val targetNodeIds = listOf(
            "com.android.packageinstaller:id/app_name",
            "com.google.android.packageinstaller:id/app_name",
            "com.android.packageinstaller:id/message",
            "com.google.android.packageinstaller:id/message",
            "android:id/alertTitle",
            "android:id/message",
            "android:id/title",
            ":id/txt_uninstall_main_title",
            ":id/alertTitle",
            ":id/dialog_title",
            ":id/message"
        )

        for (id in targetNodeIds) {
            val nodes = rootNode.findAccessibilityNodeInfosByViewId(id)
            if (nodes.isNotEmpty()) {
                val targetsFg = nodes.any {
                    val t = (it.text?.toString() ?: "") + " " + (it.contentDescription?.toString() ?: "")
                    t.contains("FocusGuard", ignoreCase = true) || t.contains("com.focusguard.app", ignoreCase = true)
                }
                nodes.forEach { SafeNodeUtils.safeRecycle(it) }
                if (targetsFg) return true
            }
        }

        // 2. Strict text-based verification: Must explicitly pair "uninstall" or "delete" with "FocusGuard"
        val screenText = extractScreenTextFast(rootNode)

        val explicitFocusGuardUninstall =
            screenText.contains("uninstall focusguard", ignoreCase = true) ||
            screenText.contains("delete focusguard", ignoreCase = true) ||
            screenText.contains("uninstall \"focusguard\"", ignoreCase = true) ||
            screenText.contains("delete \"focusguard\"", ignoreCase = true) ||
            screenText.contains("uninstall com.focusguard.app", ignoreCase = true)

        if (explicitFocusGuardUninstall) {
            return true
        }

        // If NOT on a launcher (e.g. PackageInstaller / Settings / Google Play dialogs), check if FocusGuard is the app being uninstalled
        if (!isLauncher) {
            val hasFg = screenText.contains("FocusGuard", ignoreCase = true) || screenText.contains("com.focusguard.app", ignoreCase = true)
            val isUninstallPrompt = screenText.contains("do you want to uninstall", ignoreCase = true) ||
                    screenText.contains("uninstall this app", ignoreCase = true) ||
                    screenText.contains("delete all of its data", ignoreCase = true)
            if (hasFg && isUninstallPrompt) {
                return true
            }
        }

        return false
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Fast & Safe System Security Intercept
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Handles system tampering attempts (Settings deactivation, Device Admin removal,
     * Package Installer uninstall attempts, launcher icon manipulations).
     *
     * CRITICAL SECURITY ARCHITECTURE:
     * NEVER attach a WindowManager TYPE_ACCESSIBILITY_OVERLAY directly over system settings or
     * package installer screens! Doing so triggers Android 12+ and OEM (Vivo FuntouchOS/OriginOS/ColorOS)
     * anti-tapjacking security watchdogs, which forcibly unbind and disable the AccessibilityService.
     *
     * Instead, we immediately execute GLOBAL_ACTION_HOME + launch the Home launcher intent to
     * boot the user to the Home screen in <2ms, display an informative HUD Toast, and then
     * mount the rich Block Overlay over the Home screen after 150ms.
     *
     * FIX C5 — Double-HOME Prevention:
     * We send HOME here (step 2) to exit the dangerous settings/installer screen.
     * triggerBlock() → forceCloseApp() would send a SECOND HOME via its onAttached callback,
     * causing launcher flicker on MIUI/ColorOS/FuntouchOS. We use
     * triggerBlockWithHomeAlreadySent() to suppress the redundant HOME inside forceCloseApp().
     */
    private fun handleSecurityTamper(
        sourcePackage: String,
        reason: String,
        targetName: String,
        blockType: OverlayBlockType = OverlayBlockType.SETTINGS_TAMPER,
        customToastMessage: String? = null
    ) {
        // ── OEM SCAN SUPPRESSION GUARD ────────────────────────────────
        // Vivo iManager temporarily clears ENABLED_ACCESSIBILITY_SERVICES during
        // its security scan (2-5 s). During this window, ANY Settings/iManager event
        // we receive is from the OEM scanner — not a real user tamper attempt.
        // Calling performGlobalAction(HOME) here during the scan triggers Vivo’s
        // anti-tapjacking watchdog, which force-unbinds us. Skip the block entirely.
        // CRITICAL EXEMPTION: Device Admin, Uninstall, and Settings tamper must NEVER be suppressed!
        val isExemptFromSuppression = targetName == "Device Administrator" ||
                targetName == "Hide Apps Protection" ||
                blockType == OverlayBlockType.UNINSTALL_ATTEMPT ||
                reason.contains("Device Admin", ignoreCase = true)

        if (isOemScanWindowActive() && !isExemptFromSuppression) {
            Log.i(TAG, "handleSecurityTamper SUPPRESSED during OEM security scan window [$targetName] pkg=$sourcePackage")
            return
        }
        val nowRealtime = android.os.SystemClock.elapsedRealtime()
        lastBlockTimestamp = nowRealtime
        triggerBlockHaptic()
        Log.w(TAG, "SECURITY TAMPER INTERCEPTED [$targetName]: $reason (pkg=$sourcePackage)")

        val message = customToastMessage
            ?: "🚫 $targetName is locked by FocusGuard. Complete the friction challenge in the app to modify."

        // 1. Post a clean native HUD Toast explaining the protection
        mainHandler.post {
            Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
        }

        // 2. First pop the dangerous settings page off the back stack
        performGlobalAction(GLOBAL_ACTION_BACK)

        // 3. Return to Home screen immediately in < 2ms to exit dangerous settings/installer page.
        performGlobalAction(GLOBAL_ACTION_HOME)
        try {
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
            }
            startActivity(homeIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch home intent on security tamper", e)
        }

        // 3. Mount rich Block Overlay over the Home screen after the 150ms transition.
        //    homeAlreadySent=true tells triggerBlockWithHomeAlreadySent to skip the second
        //    HOME that forceCloseApp() would otherwise fire inside onAttached.
        mainHandler.postDelayed({
            triggerBlockWithHomeAlreadySent(
                sourcePackage = sourcePackage,
                reason = reason,
                blockType = blockType,
                targetName = targetName,
                description = message
            )
        }, 150L)

        scope.launch {
            repository.logEvent(
                EventType.APP_BLOCKED,
                "[SETTINGS_TAMPER] Target=$targetName | Pkg=$sourcePackage | $reason"
            )
        }
    }

    /**
     * Variant of [triggerBlock] used exclusively by [handleSecurityTamper].
     *
     * Because [handleSecurityTamper] already sent GLOBAL_ACTION_HOME before mounting the
     * overlay, the normal [forceCloseApp] inside [triggerBlock]'s onAttached callback must
     * NOT send another HOME. This variant's onAttached callback only kills the background
     * process of the source package (if non-system) — no redundant HOME dispatch.
     *
     * FIX C5: Eliminates the double-HOME (flicker) on MIUI / ColorOS / FuntouchOS launchers.
     */
    private fun triggerBlockWithHomeAlreadySent(
        sourcePackage: String,
        reason: String,
        blockType: OverlayBlockType = OverlayBlockType.SETTINGS_TAMPER,
        targetName: String = "FocusGuard Protection",
        description: String = "Action is protected by friction delay."
    ) {
        lastBlockTimestamp = android.os.SystemClock.elapsedRealtime()
        Log.i(TAG, "TAMPER BLOCK (home pre-sent) [$blockType: $targetName] Reason: $reason (pkg=$sourcePackage)")

        val duration = repository.getFastBlockCountdownSeconds().coerceAtLeast(3)
        val customMessage = repository.getFastCustomBlockMessage().takeIf { it.isNotBlank() }
        overlayManager.currentPresetId = repository.getFastOverlayPresetId()
        overlayManager.currentDimAlpha = repository.getFastOverlayDimAlpha()

        val isSystemPkg = isSystemOrLauncherPackage(sourcePackage)

        overlayManager.showOverlay(
            durationSeconds = duration,
            blockType = blockType,
            targetName = targetName,
            sourcePackage = sourcePackage,
            description = description,
            customMessage = customMessage,
            // HOME was already sent before overlay mount, but we still want to send it
            // again on dismiss so the user always lands on home when they click Close.
            sendHomeOnDismiss = true,
            onAttached = {
                // HOME is already live. Only clean up the background process for non-system pkgs
                // to prevent a PiP window from the settings/installer appearing behind the overlay.
                if (!isSystemPkg) {
                    try {
                        val am = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                        am?.killBackgroundProcesses(sourcePackage)
                        Log.d(TAG, "triggerBlockWithHomeAlreadySent: killed bg processes for $sourcePackage")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error killing bg process for $sourcePackage in tamper block", e)
                    }
                }
            },
            onDismissed = {
                lastOverlayDismissTimestamp = android.os.SystemClock.elapsedRealtime()
                lastDismissedPackage = sourcePackage
                Log.i(TAG, "Tamper overlay countdown ended — $sourcePackage")
            }
        )


        scope.launch {
            repository.logEvent(EventType.APP_BLOCKED, "[$blockType] Target=$targetName | Pkg=$sourcePackage | $reason")
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Trigger Block with Custom Contextual Details
    // ─────────────────────────────────────────────────────────────────────────

    private fun triggerBlockHaptic() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator?.vibrate(
                    VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
                )
            } else {
                @Suppress("DEPRECATION")
                val v = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v?.vibrate(VibrationEffect.createOneShot(45, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    v?.vibrate(45)
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Haptic vibration error: ${e.message}")
        }
    }

    private fun triggerBlock(
        sourcePackage: String,
        reason: String,
        blockType: OverlayBlockType = OverlayBlockType.UNINSTALL_ATTEMPT,
        targetName: String = "FocusGuard Protection",
        description: String = "Action is protected by friction delay."
    ) {
        val nowRealtime = android.os.SystemClock.elapsedRealtime()
        lastBlockTimestamp = nowRealtime
        triggerBlockHaptic()
        Log.i(TAG, "BLOCK triggered! [$blockType: $targetName] Reason: $reason (pkg=$sourcePackage)")

        // If blocking the currently active foreground package, immediately capture its session duration
        if (sourcePackage == currentForegroundPackage && currentForegroundStartTime > 0L) {
            val sessionElapsed = nowRealtime - currentForegroundStartTime
            if (sessionElapsed > 0L) {
                timeLimitEvaluator.recordSessionUsage(sourcePackage, sessionElapsed)
            }
            currentForegroundStartTime = 0L
            cancelForegroundPolling()
        }

        // Duration: User-configured countdown duration for all block types uniformly (Settings, Tamper, Shorts, etc.)
        val duration = repository.getFastBlockCountdownSeconds().coerceAtLeast(3)

        val customMessage = repository.getFastCustomBlockMessage().takeIf { it.isNotBlank() }

        // Synchronize selected overlay style from user settings
        overlayManager.currentPresetId = repository.getFastOverlayPresetId()
        overlayManager.currentDimAlpha = repository.getFastOverlayDimAlpha()

        val isLauncher = isLauncherPackage(sourcePackage)
        val isSystemPkg = isSystemOrLauncherPackage(sourcePackage)

        // ── SEND HOME ON DISMISS: Always true for ALL block types ────────────────
        // When the user clicks "Close Overlay", they should ALWAYS land on the home
        // screen — not inside Settings, YouTube, or the launcher with a dangling popup.
        //
        // The BlockOverlayManager.sendToHome() already uses both performGlobalAction(HOME)
        // + startActivity(homeIntent). For system/settings packages we rely only on
        // startActivity since performGlobalAction on a settings screen can trigger the
        // Vivo anti-tapjacking watchdog. BlockOverlayManager handles this safely.
        val shouldSendHomeOnDismiss = true


        overlayManager.showOverlay(
            durationSeconds = duration,
            blockType = blockType,
            targetName = targetName,
            sourcePackage = sourcePackage,
            description = description,
            customMessage = customMessage,
            sendHomeOnDismiss = shouldSendHomeOnDismiss,
            onAttached = {
                // Invariant Rule 1: Attach overlay FIRST. forceCloseApp sends HOME while overlay is live on screen.
                forceCloseApp(sourcePackage)
            },
            onDismissed = {
                lastOverlayDismissTimestamp = android.os.SystemClock.elapsedRealtime()
                lastDismissedPackage = sourcePackage
                Log.i(TAG, "Overlay countdown ended — $sourcePackage [isSystem=$isSystemPkg, sendHome=$shouldSendHomeOnDismiss]")
                try {
                    // NEVER kill system settings or system packages! Only kill 3rd party apps!
                    if (!isSystemPkg) {
                        val am = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                        am?.killBackgroundProcesses(sourcePackage)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error cleaning up $sourcePackage after overlay dismiss", e)
                }
            }
        )

        scope.launch {
            repository.logEvent(EventType.APP_BLOCKED, "[$blockType] Target=$targetName | Pkg=$sourcePackage | $reason")
        }
    }

    private fun dismissUnauthorizedAppInDeepFocus(pkg: String) {
        try {
            val isLauncher = isLauncherPackage(pkg)
            val isSystem = isSystemOrLauncherPackage(pkg)

            // 1. Send BACK to dismiss any open app drawers, folders, recents, or popup menus
            performGlobalAction(GLOBAL_ACTION_BACK)

            // 2. Kill background processes for 3rd-party unallowed apps to stop PiP video, audio, etc.
            if (!isSystem && !isLauncher) {
                val am = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                am?.killBackgroundProcesses(pkg)
                Log.d(TAG, "Killed background processes for $pkg during Deep Focus lockdown.")
            }

            // Invariant: NEVER send GLOBAL_ACTION_HOME or startActivity(homeIntent) in Deep Focus!
            // Deep Focus is a Kiosk. Returning HOME would hide SessionLockActivity behind the launcher.
        } catch (e: Exception) {
            Log.e(TAG, "Error dismissing unauthorized app $pkg in Deep Focus", e)
        }
    }

    private fun forceCloseApp(pkg: String) {
        try {
            val isSystem = isSystemOrLauncherPackage(pkg)
            val isLauncher = isLauncherPackage(pkg)

            // CRITICAL: Never send GLOBAL_ACTION_BACK to system settings!
            // Sending BACK in settings navigates to a DIFFERENT settings page, which then fires
            // another TYPE_WINDOW_STATE_CHANGED event that can trigger another blocking round,
            // creating an infinite loop. Only send BACK for 3rd-party app video players.
            if (!isSystem) {
                performGlobalAction(GLOBAL_ACTION_BACK)
            }

            // ── FIX: If pkg is the launcher, we still MUST send HOME + start home intent.
            // Previously we returned early here which meant the user stayed on the launcher
            // and could immediately hold FocusGuard again. Now we always proceed to HOME.
            // We skip BACK (done above for non-system) but always send HOME.

            // 2. Return to Home screen
            performGlobalAction(GLOBAL_ACTION_HOME)

            // 3. Kill background processes for 3rd party apps only (Chess, YouTube, etc.)
            // NEVER kill system packages — this caused the AccessibilityService to become unbound!
            if (!isSystem && !isLauncher) {
                val am = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                am?.killBackgroundProcesses(pkg)
                Log.d(TAG, "Killed background processes for $pkg to eliminate PiP window.")
            }

            // 4. Bring home launcher to top
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            startActivity(homeIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Error force-closing $pkg", e)
        }
    }

    private fun isLauncherPackage(pkg: String): Boolean {
        val launcherPackage = launcherDetector.getCurrentLauncherPackage()
        val lower = pkg.lowercase()
        return (launcherPackage != null && lower == launcherPackage.lowercase()) ||
                launcherDetector.isLauncherPackage(pkg) ||
                lower.contains("launcher") ||
                lower.contains("quickstep") ||
                lower.contains("trebuchet") ||
                lower.contains("nexuslauncher") ||
                (lower.contains("home") && !lower.contains("googlequicksearchbox"))
    }

    private fun scheduleMindfulAllowanceExpiry(pkg: String, appLabel: String, durationMinutes: Int) {
        val prevRunnable = mindfulExpiryRunnables[pkg]
        if (prevRunnable != null) {
            mainHandler.removeCallbacks(prevRunnable)
        }
        val r = Runnable {
            mindfulRepository.revokeAllowance(pkg)
            mindfulRepository.recordSessionEnded(pkg)
            if (currentForegroundPackage == pkg && !overlayManager.isOverlayShowing) {
                Log.i(TAG, "Mindful allowance expired for $pkg -> Triggering conscious re-block and closing app!")
                triggerBlock(
                    sourcePackage = pkg,
                    reason = "Conscious Allowance Ended ($durationMinutes min)",
                    blockType = OverlayBlockType.APP_BLOCK,
                    targetName = appLabel,
                    description = "Your conscious allowance of $durationMinutes minutes for $appLabel has ended. Closing app to protect your focus."
                )
            }
            mindfulExpiryRunnables.remove(pkg)
        }
        mindfulExpiryRunnables[pkg] = r
        mainHandler.postDelayed(r, durationMinutes * 60 * 1000L)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Hardware Back Key Consumption
    // ─────────────────────────────────────────────────────────────────────────

    override fun onKeyEvent(event: KeyEvent?): Boolean {
        if (event == null) return false

        val keyCode = event.keyCode
        val isVolumeKey = keyCode == KeyEvent.KEYCODE_VOLUME_DOWN ||
                keyCode == KeyEvent.KEYCODE_VOLUME_UP ||
                keyCode == KeyEvent.KEYCODE_VOLUME_MUTE

        // ⚡ ZERO-LATENCY FAST-PATH FOR HARDWARE VOLUME KEYS (AGENTS Rule 5 Invariant):
        // Hardware volume keys should NEVER experience latency during normal device usage.
        // If no block overlay is displaying on screen and no alert siren is active,
        // instantly return super.onKeyEvent(event) with 0ms delay and zero Binder IPC calls.
        // This guarantees InputDispatcher receives an immediate acknowledgement, eliminating
        // key repeat auto-ramp and preventing multi-step jumps on single taps.
        if (isVolumeKey) {
            val isOverlayShowing = overlayManager.isOverlayShowing
            val isAlertActive = audioManagerHelper.isAlertActive()
            if (!isOverlayShowing && !isAlertActive) {
                return super.onKeyEvent(event)
            }
        }

        // 🚨 EMERGENCY & IN-CALL IMMUNITY (Task 5 & Safety Invariant) 🚨
        // Never intercept, mute, or consume any hardware keys during active phone or VoIP calls.
        if (sessionBlockManager.isInPhoneCall() || audioManagerHelper.isInCall()) {
            return super.onKeyEvent(event)
        }

        val isOverlayShowing = overlayManager.isOverlayShowing
        val isDeepFocus = sessionBlockManager.isSessionCurrentlyActive()
        val currentPkg = currentForegroundPackage
        val isAllowedTool = currentPkg != null && (sessionBlockManager.isAlwaysAllowedPackage(currentPkg) || sessionBlockManager.suitcasePackages.contains(currentPkg.lowercase()))

        // 🚨 AUDIO STREAM PROTECTION: Prevent muting alert sirens or focus alarms (Task 2) 🚨
        if (audioManagerHelper.isAlertActive()) {
            if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_MUTE) {
                if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                    audioManagerHelper.ensureAudibleAlertVolume(android.media.AudioManager.STREAM_ALARM)
                    Log.d(TAG, "Hardware volume-down consumed & alarm volume locked during active alert")
                }
                return true
            }
        }

        // 🚨 OVERLAY KEY ISOLATION: Prevent volume key combinations from interacting with background apps 🚨
        if (isOverlayShowing && isVolumeKey) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_MUTE) {
                    audioManagerHelper.ensureAudibleAlertVolume(android.media.AudioManager.STREAM_ALARM)
                }
                Log.d(TAG, "Hardware volume key $keyCode consumed to isolate block overlay window")
            }
            return true
        }

        // 🚨 NAVIGATION KEY ISOLATION: Back, App-Switch, Home 🚨
        if ((isOverlayShowing || (isDeepFocus && !isAllowedTool)) &&
            (keyCode == KeyEvent.KEYCODE_BACK ||
             keyCode == KeyEvent.KEYCODE_APP_SWITCH ||
             keyCode == KeyEvent.KEYCODE_HOME)
        ) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                Log.d(TAG, "Hardware key $keyCode strictly consumed (overlayShowing=$isOverlayShowing, deepFocus=$isDeepFocus)")
            }
            return true
        }

        return super.onKeyEvent(event)
    }

    /**
     * Zero-latency device admin check — reads from the AtomicBoolean cache refreshed every 30 s.
     * Never makes a Binder IPC call on the hot accessibility-event path (AGENTS Rule 5).
     */
    private fun isDeviceAdminActive(): Boolean {
        val cached = cachedDeviceAdminActive.get()
        if (!cached) {
            val raw = isDeviceAdminActiveRaw()
            if (raw) cachedDeviceAdminActive.set(true)
            return raw
        }
        return true
    }

    /** Raw Binder IPC call — only used to populate/refresh the cache, NOT on the hot path. */
    private fun isDeviceAdminActiveRaw(): Boolean {
        return try {
            val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val adminComponent = ComponentName(this, FocusGuardDeviceAdminReceiver::class.java)
            dpm.isAdminActive(adminComponent)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking device admin state", e)
            false
        }
    }

    /**
     * Returns true for known Android / OEM system settings packages.
     * SECURITY FIX: Replaces the old broad `lower.contains("settings")` which allowed
     * any app with "settings" in its package name to bypass time limits and schedules.
     */
    /**
     * Returns true only for known, explicitly listed OEM settings packages.
     *
     * FIX NC2: Removed broad `lower.endsWith(".settings")` and `lower.contains(".settings.")`
     * patterns. Any malicious app whose package name ends with ".settings" (e.g.,
     * "com.evilapp.settings") satisfied those checks and bypassed ALL time limits and schedules.
     * Now every vendor is listed by explicit prefix — no wildcard escapes.
     *
     * Also added: Infinix/Tecno/itel (Transsion), Nothing Phone, and Realme settings packages.
     */
    private fun isSettingsPackage(lowerPkg: String): Boolean {
        return isKnownSettingsPackage(lowerPkg) ||
                lowerPkg == "android" ||
                lowerPkg.contains("settings") ||
                lowerPkg.contains("safecenter") ||
                lowerPkg.contains("securitycenter") ||
                lowerPkg.contains("securityadd") ||
                lowerPkg.contains("permissioncontroller") ||
                lowerPkg.contains("permissionmanager") ||
                lowerPkg.contains("applock") ||
                lowerPkg.contains("phonemaster") ||
                lowerPkg == "com.oneplus.security" ||
                lowerPkg == "com.iqoo.secure" ||
                lowerPkg == "com.vivo.permissionmanager" ||
                lowerPkg == "com.vivo.safecenter" ||
                lowerPkg == "com.vivo.imanager" ||
                lowerPkg == "com.vivo.abe" ||
                lowerPkg == "com.vivo.upslide"
    }

    private fun isInstallerPackage(pkg: String, className: String): Boolean {
        val lower = pkg.lowercase()
        return lower == "com.android.vending" ||
                lower.contains("packageinstaller") ||
                lower.contains("permissioncontroller") ||
                className.contains("UninstallerActivity", ignoreCase = true) ||
                className.contains("UninstallAppProgress", ignoreCase = true) ||
                className.contains("DeletePackage", ignoreCase = true)
    }

    private fun isKnownSettingsPackage(lower: String): Boolean {
        return lower == "com.android.settings" ||
                lower.startsWith("com.android.settings.") ||
                lower.startsWith("com.samsung.android.settings") ||
                lower.startsWith("com.miui.settings") ||
                lower.startsWith("com.oneplus.settings") ||
                lower.startsWith("com.coloros.settings") ||
                lower.startsWith("com.oppo.settings") ||
                lower.startsWith("com.realme.settings") ||
                lower.startsWith("com.vivo.settings") ||
                lower.startsWith("com.huawei.systemmanager") ||
                lower.startsWith("com.asus.settings") ||
                lower.startsWith("com.htc.settings") ||
                lower.startsWith("com.lge.settings") ||
                lower.startsWith("com.motorola.settings") ||
                lower.startsWith("com.transsion.settings") ||   // Infinix / Tecno / itel
                lower.startsWith("com.hissage.settings") ||     // Transsion variant
                lower.startsWith("com.nothing.settings") ||     // Nothing Phone
                lower.startsWith("com.google.android.settings") // Pixel / GSI settings
    }

    companion object {
        /**
         * STATIC AtomicLong — survives AccessibilityService instance restarts.
         *
         * Vivo/OEM force-unbinds and creates a NEW service instance when it scans settings.
         * An instance field resets to 0 on the new instance — losing the suppression window.
         * This static field persists across instance restarts within the same process lifetime.
         *
         * Set via [markOemScanActive]. Read via [isOemScanWindowActive] (which delegates here).
         */
        val oemScanSuppressUntilStatic = java.util.concurrent.atomic.AtomicLong(0L)

        /**
         * Called by [AccessibilityStateObserver] the instant it detects that
         * ENABLED_ACCESSIBILITY_SERVICES was transiently cleared by the OEM security engine.
         * Arms the suppression window for [windowMs] milliseconds (default 12 s).
         */
        fun markOemScanActive(windowMs: Long = 12_000L) {
            val until = android.os.SystemClock.elapsedRealtime() + windowMs
            oemScanSuppressUntilStatic.set(until)
            Log.i(TAG_COMPANION, "OEM security scan window armed for ${windowMs}ms (static) — handleSecurityTamper suppressed")
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
            instanceRef?.get()?.cachedDeviceAdminActive?.set(isActive)
            Log.d(TAG, "Device admin cache explicitly updated: $isActive")
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

            try {
                if (com.focusguard.app.recovery.AccessibilityRecoveryHelper.canWriteSecureSettings(context)) {
                    val targetComponent = "${context.packageName}/${FocusGuardAccessibilityService::class.java.name}"
                    val shortComponent = "${context.packageName}/.service.FocusGuardAccessibilityService"
                    val contentResolver = context.contentResolver
                    val current = android.provider.Settings.Secure.getString(
                        contentResolver,
                        android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                    ) ?: ""
                    val updated = current.split(":")
                        .filter { it.isNotBlank() && !it.equals(targetComponent, true) && !it.equals(shortComponent, true) }
                        .joinToString(":")
                    android.provider.Settings.Secure.putString(
                        contentResolver,
                        android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                        updated
                    )
                    Log.i(TAG_COMPANION, "Removed FocusGuard from ENABLED_ACCESSIBILITY_SERVICES programmatically")
                }
            } catch (e: Exception) {
                Log.w(TAG_COMPANION, "Could not remove from Secure.Settings: ${e.message}")
            }
        }
    }
}
