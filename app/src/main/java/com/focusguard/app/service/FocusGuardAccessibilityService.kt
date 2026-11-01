package com.focusguard.app.service

import com.focusguard.app.service.handlers.BlockTrigger
import com.focusguard.app.service.handlers.ForegroundSessionTracker
import com.focusguard.app.service.handlers.HeartbeatCoordinator
import com.focusguard.app.service.handlers.HardBlockHandler
import com.focusguard.app.service.handlers.HardBlockResult
import com.focusguard.app.service.handlers.OemScanDetector
import com.focusguard.app.service.handlers.ScheduleLimitHandler
import com.focusguard.app.service.handlers.ServiceReceiverManager
import com.focusguard.app.service.handlers.TamperResponseHandler
import com.focusguard.app.service.utils.PackageClassifier
import com.focusguard.app.service.utils.ClassNameMatchers
import com.focusguard.app.service.utils.AccessibilityServiceDisabler
import com.focusguard.app.core.common.constants.Timings

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import com.focusguard.app.blockers.AntiClonerEngine
import com.focusguard.app.blockers.KeywordBlocker
import com.focusguard.app.blockers.MultiWindowGuard
import com.focusguard.app.blockers.TimeTamperGuard
import com.focusguard.app.core.database.entity.EventType
import com.focusguard.app.data.repository.FocusGuardRepository
import com.focusguard.app.usage.ScheduleEvaluator
import com.focusguard.app.observer.AccessibilityStateObserver
import com.focusguard.app.overlay.manager.BlockOverlayManager
import com.focusguard.app.overlay.manager.OverlayBlockType
import com.focusguard.app.tracker.BrowserHeartbeatTracker
import com.focusguard.app.usage.TimeLimitEvaluator
import com.focusguard.app.utils.SafeNodeUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
    lateinit var browserHeartbeatTracker: BrowserHeartbeatTracker

    @Inject
    lateinit var timeLimitEvaluator: TimeLimitEvaluator

    @Inject
    lateinit var scheduleEvaluator: ScheduleEvaluator

    @Inject
    lateinit var serviceReceiverManager: ServiceReceiverManager

    @Inject
    lateinit var heartbeatCoordinator: HeartbeatCoordinator

    @Inject
    lateinit var launcherDetector: com.focusguard.app.utils.LauncherDetector

    @Inject
    lateinit var mindfulRepository: com.focusguard.app.data.repository.MindfulRepository

    @Inject
    lateinit var sessionBlockManager: com.focusguard.app.blockers.SessionBlockManager

    @Inject
    lateinit var blockDecisionEngine: com.focusguard.app.core.engine.BlockDecisionEngine

    @Inject
    lateinit var rulesSnapshotHolder: com.focusguard.app.core.engine.RulesSnapshotHolder

    @Inject
    lateinit var rulesSnapshotSynchronizer: com.focusguard.app.core.engine.RulesSnapshotSynchronizer

    @Inject
    lateinit var launcherEventHandler: com.focusguard.app.service.handlers.LauncherEventHandler

    @Inject
    lateinit var launcherTargetResolver: com.focusguard.app.service.handlers.LauncherTargetResolver

    @Inject
    lateinit var settingsEventHandler: com.focusguard.app.service.handlers.SettingsEventHandler

    @Inject
    lateinit var packageInstallerHandler: com.focusguard.app.service.handlers.PackageInstallerHandler

    @Inject
    lateinit var keyEventHandler: com.focusguard.app.service.handlers.KeyEventHandler

    @Inject
    lateinit var deviceAdminManager: com.focusguard.app.service.utils.DeviceAdminManager

    @Inject
    lateinit var mindfulPauseHandler: com.focusguard.app.service.handlers.MindfulPauseHandler

    @Inject
    lateinit var reelDetectionHandler: com.focusguard.app.service.handlers.ReelDetectionHandler

    @Inject
    lateinit var keywordDetectionHandler: com.focusguard.app.service.handlers.KeywordDetectionHandler

    @Inject
    lateinit var autoBlockHandler: com.focusguard.app.service.handlers.AutoBlockHandler

    @Inject
    lateinit var browserGuardHandler: com.focusguard.app.service.handlers.BrowserGuardHandler

    @Inject
    lateinit var clonedAppScanHandler: com.focusguard.app.service.handlers.ClonedAppScanHandler

    @Inject
    lateinit var hardBlockHandler: HardBlockHandler

    @Inject
    lateinit var scheduleLimitHandler: ScheduleLimitHandler

    @Inject
    lateinit var oemScanDetector: OemScanDetector

    @Inject
    lateinit var tamperResponseHandler: TamperResponseHandler

    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val windowActions by lazy {
        com.focusguard.app.service.utils.ServiceWindowActions(
            service = this,
            isSystemPackage = { pkg ->
                val isProtectionActive = isDeviceAdminActive() && repository.isFastUninstallProtectionEnabled()
                val isSessionActive = sessionBlockManager.isSessionCurrentlyActive()
                PackageClassifier.isSystemOrLauncherPackage(pkg, launcherDetector, isProtectionActive, isSessionActive)
            },
            isLauncherPackage = { pkg -> isLauncherPackage(pkg) }
        )
    }

    private val coroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG, "Safely intercepted uncaught coroutine error: ${throwable.message}", throwable)
    }
    private val serviceJob = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Main + serviceJob + coroutineExceptionHandler)

    // ── FIX 4: Cross-process REFRESH_CACHES broadcast receiver ──────────────
    private val blockedPackages = CopyOnWriteArraySet<String>()

    private val foregroundSessionTracker by lazy {
        ForegroundSessionTracker(
            repository = repository,
            timeLimitEvaluator = timeLimitEvaluator,
            scheduleEvaluator = scheduleEvaluator,
            mindfulRepository = mindfulRepository,
            scope = scope,
            isSystemOrLauncherPackage = { pkg ->
                val isProtectionActive = isDeviceAdminActive() && repository.isFastUninstallProtectionEnabled()
                val isSessionActive = sessionBlockManager.isSessionCurrentlyActive()
                PackageClassifier.isSystemOrLauncherPackage(pkg, launcherDetector, isProtectionActive, isSessionActive)
            },
            onTriggerBlock = { sourcePkg, reason, blockType, targetName, description ->
                triggerBlock(
                    sourcePackage = sourcePkg,
                    reason = reason,
                    blockType = blockType,
                    targetName = targetName,
                    description = description
                )
            },
            onStopBrowserHeartbeat = {
                browserHeartbeatTracker.stopHeartbeat()
            }
        )
    }

    private val blockTrigger by lazy {
        BlockTrigger(
            context = applicationContext,
            repository = repository,
            overlayManager = overlayManager,
            windowActions = windowActions,
            foregroundSessionTracker = foregroundSessionTracker,
            launcherDetector = launcherDetector,
            sessionBlockManager = sessionBlockManager,
            scope = scope,
            isDeviceAdminActive = { isDeviceAdminActive() },
            isLauncherPackage = { pkg -> isLauncherPackage(pkg) },
            onUpdateLastBlockTimestamp = { timestamps.lastBlockTimestamp = it },
            onUpdateLastOverlayDismiss = { timestamp, pkg ->
                timestamps.lastOverlayDismissTimestamp = timestamp
                timestamps.lastDismissedPackage = pkg
            }
        )
    }

    private var accessibilityStateObserver: AccessibilityStateObserver? = null

    private val timestamps = GuardTimestamps()

    // ── OEM SECURITY SCAN SUPPRESSION WINDOW ────────────────────────────────
    // oemScanSuppressUntil is now a STATIC companion field (see companion object below).
    // It must be static because Vivo force-unbinds and recreates a NEW service instance;
    // an instance field would reset to 0 on the new instance, losing the suppression window.
    // isOemScanWindowActive() reads from the static field, surviving instance restarts.

    /** Returns true when we are inside an OEM-initiated transient scan window. */
    private fun isOemScanWindowActive(): Boolean =
        android.os.SystemClock.elapsedRealtime() < oemScanSuppressUntilStatic.get()

    // Device Admin state caching & refresh handled by DeviceAdminManager (AGENTS Rule 5)

    // In-memory app label cache to avoid expensive main-thread PackageManager IPC
    private val appLabelCache = java.util.concurrent.ConcurrentHashMap<String, String>()


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
        val elapsedSinceLastAlive = if (timestamps.lastAliveTimestamp > 0L) now - timestamps.lastAliveTimestamp else 0L
        val isUnexpectedRestart = elapsedSinceLastAlive in 1..45_000L
        val eventType = if (isUnexpectedRestart) EventType.SERVICE_RESTARTED_UNEXPECTEDLY else EventType.SERVICE_CONNECTED
        val lastPkg = foregroundSessionTracker.currentPackage ?: "unknown"
        scope.launch {
            repository.logEvent(
                eventType,
                "Service connected. Elapsed: ${elapsedSinceLastAlive}ms | LastFgPkg: $lastPkg"
            )
        }
        timestamps.lastAliveTimestamp = now
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

        serviceReceiverManager.registerEarlyReceivers(this)

        // Pre-populate device admin cache and start 30s background refresh (AGENTS Rule 5)
        deviceAdminManager.startPeriodicRefresh(scope)
        val isDirectBoot = com.focusguard.app.recovery.AccessibilityRecoveryHelper.isDirectBootLocked(this)
        if (isDirectBoot) {
            Log.i(TAG, "Direct Boot detected during onServiceConnected -- loading caches from DE storage.")
            loadDirectBootDeCaches()
            serviceReceiverManager.registerUserUnlockedReceiver(this) {
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

        serviceReceiverManager.registerNewAppReceiver(this)

        // SWEEP ON RECONNECT: Delayed by 3s on every connect.
        // Live events arrive within < 100ms -- the 10s cooldown handles them.
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
            val currentPkg = foregroundSessionTracker.currentPackage
            val isAllowed = currentPkg != null && (sessionBlockManager.isAlwaysAllowedPackage(currentPkg) || sessionBlockManager.suitcasePackages.contains(currentPkg.lowercase()))
            if (!isAllowed) {
                com.focusguard.app.feature.session.ui.SessionLockActivity.start(this)
            }
        }

        scope.launch {
            sessionBlockManager.sessionStateFlow.collect { state ->
                if (state.isActive) {
                    val currentPkg = foregroundSessionTracker.currentPackage
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

    private fun sweepActiveWindowOnConnect() {
        try {
            val root = rootInActiveWindow ?: return
            val pkg = root.packageName?.toString() ?: ""

            // ── CRITICAL FIX: Never sweep Settings/Accessibility screens on reconnect ──
            // When Vivo force-unbinds our service (while the user is on Accessibility Settings)
            // and then rebinds us, rootInActiveWindow still points to the Accessibility Settings
            // screen. Sweeping it immediately calls processAccessibilityEvent() which fires
            // handleSecurityTamper() -> GLOBAL_ACTION_HOME -> Vivo watchdog unbinds us again.
            // This was the infinite disconnect loop.
            val isSettingsOrA11y = ClassNameMatchers.isSettingsOrA11yPackage(pkg)

            if (pkg.isBlank() || pkg == packageName || pkg.startsWith("com.focusguard.app") || isSettingsOrA11y) {
                SafeNodeUtils.safeRecycle(root)
                Log.i(TAG, "sweepActiveWindowOnConnect: SKIPPED settings/a11y screen on reconnect (pkg=$pkg) -- avoids false tamper block")
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
        foregroundSessionTracker.cancelForegroundPolling()
        // Do NOT call clearServiceContext() here -- onInterrupt is temporary feedback interruption;
        // service is still alive and bound. Only onUnbind or onDestroy clears context.
        overlayManager.setServiceContext(this)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        isServiceRunning = false
        Log.w(TAG, "AccessibilityService unbound.")
        browserHeartbeatTracker.stopHeartbeat()
        foregroundSessionTracker.cancelForegroundPolling()
        accessibilityStateObserver?.unregister()
        accessibilityStateObserver = null
        overlayManager.clearServiceContext()
        serviceReceiverManager.unregisterOnUnbind(this)
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
        foregroundSessionTracker.cancelAll()
        accessibilityStateObserver?.unregister()
        accessibilityStateObserver = null
        overlayManager.clearServiceContext()
        serviceReceiverManager.unregisterAll(this)
        instanceRef = null
        mindfulPauseHandler.cancelAll()
        autoBlockHandler.clearCache()
        appLabelCache.clear()
        deviceAdminManager.stopPeriodicRefresh()
        serviceJob.cancel()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Crash-Proof Event Handler
    // ─────────────────────────────────────────────────────────────────────────

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        try {
            timestamps.lastAliveTimestamp = android.os.SystemClock.elapsedRealtime()
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
            foregroundSessionTracker.onSelfPackage(pkg)
            return
        }

        // ── GUARD 1.05: Fast-discard software keyboards & IME (Zero typing CPU lag) ─
        if (PackageClassifier.isKeyboardPackage(pkg)) {
            return
        }

        // ── GUARD 1.1: Active Overlay Protection & Interception ─
        if (overlayManager.isOverlayShowing) {
            val isProtectionActive = isDeviceAdminActive() && repository.isFastUninstallProtectionEnabled()
            // Dismiss dangerous Settings screens immediately if switched to while overlay is active AND uninstall protection is on
            if (PackageClassifier.isSettingsPackage(pkg.lowercase()) && isProtectionActive) {
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
                    if (isPotentialUninstallHost && overlayRootNode != null && packageInstallerHandler.isUninstallConfirmationForFocusGuard(overlayRootNode)) {
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

                    // 3. While block overlay is showing, ignore launcher events entirely
                    // UNLESS target resolver confirms FOCUSGUARD and previous overlay was shown > 1500ms ago.
                    if (isLauncherPackage(pkg)) {
                        val displayMetrics = resources.displayMetrics
                        val target = launcherTargetResolver.resolve(
                            e = e,
                            rootNode = overlayRootNode,
                            screenWidth = displayMetrics.widthPixels,
                            screenHeight = displayMetrics.heightPixels,
                            nowRealtime = android.os.SystemClock.elapsedRealtime()
                        )
                        val now = android.os.SystemClock.elapsedRealtime()
                        if (target == com.focusguard.app.service.handlers.LauncherTarget.FOCUSGUARD && (now - timestamps.lastBlockTimestamp > 1500L)) {
                            Log.w(TAG, "Launcher interaction specifically targeting FocusGuard detected during active overlay -> dismissing popup immediately ($pkg)")
                            timestamps.lastBlockTimestamp = now
                            performGlobalAction(GLOBAL_ACTION_BACK)
                        }
                        return
                    }
                }
            } finally {
                SafeNodeUtils.safeRecycle(overlayRootNode)
            }

            // 4. SystemUI Notification Shade Pull-down Guard during active overlay
            if (pkg == "com.android.systemui") {
                val isShade = ClassNameMatchers.isShadeClass(className)
                if (isShade) {
                    val now = android.os.SystemClock.elapsedRealtime()
                    if (now - timestamps.lastShadeCollapseTimestamp > 1000L) {
                        timestamps.lastShadeCollapseTimestamp = now
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
        val isProtectionActiveForCooldown = isDeviceAdminActive() && repository.isFastUninstallProtectionEnabled()
        val isSessionActiveForCooldown = sessionBlockManager.isSessionCurrentlyActive()
        if (nowRealtime - timestamps.lastOverlayDismissTimestamp < Timings.OVERLAY_DISMISS_COOLDOWN_MS &&
            pkg == timestamps.lastDismissedPackage &&
            !PackageClassifier.isSystemOrLauncherPackage(pkg, launcherDetector, isProtectionActiveForCooldown, isSessionActiveForCooldown) &&
            !PackageClassifier.isSettingsPackage(pkg.lowercase()) &&
            !AntiClonerEngine.isClonerPackage(pkg)) {
            return
        }

        // ── GUARD 1.145: DEEP FOCUS SYSTEMUI SHADE & RECENTS COLLAPSE ───────
        // Prevent escaping Deep Focus Kiosk via notification shade, quick settings tiles, or overview
        if (sessionBlockManager.isSessionCurrentlyActive() && pkg == "com.android.systemui") {
            val isShadeOrRecents = ClassNameMatchers.isShadeOrRecentsClass(className)
            if (isShadeOrRecents) {
                val now = android.os.SystemClock.elapsedRealtime()
                if (now - timestamps.lastShadeCollapseTimestamp > Timings.SHADE_COLLAPSE_THROTTLE_MS) {
                    timestamps.lastShadeCollapseTimestamp = now
                    Log.w(TAG, "Notification shade / quick settings / recents intercepted during Deep Focus -> Collapsing!")
                    performGlobalAction(GLOBAL_ACTION_BACK)
                    com.focusguard.app.feature.session.ui.SessionLockActivity.start(this)
                }
                return
            }
        }

        // ── GUARD 1.15: Transient system events (status bar, volume slider, toasts, keyboard) ─
        // These events are transient overlays and must NOT disrupt the active foreground app's session timer!
        val isProtectionActiveForTransient = isDeviceAdminActive() && repository.isFastUninstallProtectionEnabled()
        if (PackageClassifier.isTransientSystemPackage(pkg, isProtectionActiveForTransient)) {
            return
        }

        // ── GUARD 1.15a: PREEMPTIVE OEM SECURITY SCAN DETECTION ─────────────
        // When events arrive from known OEM security managers (Vivo iManager, Vivo SafeCenter,
        // Vivo PermissionManager) we check whether our accessibility service entry is temporarily
        // absent from ENABLED_ACCESSIBILITY_SERVICES. If so, it's an OEM scan window -- arm the
        // suppression flag immediately so handleSecurityTamper() won't fire HOME during the scan.
        if (oemScanDetector.isOemSecurityPackage(pkg) && !isOemScanWindowActive()) {
            if (oemScanDetector.isAccessibilityTransientlyOff(contentResolver, packageName)) {
                oemScanSuppressUntilStatic.set(android.os.SystemClock.elapsedRealtime() + 12_000L)
                Log.i(TAG, "GUARD 1.15a: OEM security manager ($pkg) detected with transient accessibility clear -- scan window armed for 12s")
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

        val isPackageChanged = pkg != foregroundSessionTracker.currentPackage
        val isWindowTransition = e.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED

        // ── GUARD 1.17: MULTI-WINDOW & SPLIT-SCREEN BYPASS GUARD ──────────────
        if (isWindowTransition || isPackageChanged) {
            val isIntercepted = MultiWindowGuard.checkAndInterceptMultiWindow(
                service = this,
                repository = repository,
                sessionBlockManager = sessionBlockManager,
                onIntercept = { sourcePkg, reason, targetName, desc ->
                    if (sessionBlockManager.isSessionCurrentlyActive()) {
                        windowActions.triggerBlockHaptic()
                        com.focusguard.app.feature.session.ui.SessionLockActivity.start(this)
                        windowActions.dismissUnauthorizedAppInDeepFocus(sourcePkg)
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
                if (nowRt - timestamps.lastDeepFocusReassertTimestamp > 40L) {
                    timestamps.lastDeepFocusReassertTimestamp = nowRt
                    Log.w(TAG, "DEEP FOCUS INTERCEPT: Unauthorized app/launcher blocked: $pkg -> Reasserting Kiosk Lock Screen")
                    windowActions.triggerBlockHaptic()
                    com.focusguard.app.feature.session.ui.SessionLockActivity.start(this)
                    windowActions.dismissUnauthorizedAppInDeepFocus(pkg)
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
            if (nowRt - timestamps.lastAdminTamperLogTimestamp > 60_000L) {
                timestamps.lastAdminTamperLogTimestamp = nowRt
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
                if (launcherEventHandler.processLauncherEvent(
                        service = this,
                        rootNode = rootNode,
                        e = e,
                        pkg = pkg,
                        className = className,
                        protectionWanted = protectionWanted,
                        nowRealtime = nowRealtime,
                        onTriggerBlock = { sourcePkg, reason, blockType, targetName, desc ->
                            triggerBlock(sourcePkg, reason, blockType, targetName, desc)
                        }
                    )) {
                    return
                }
            } finally {
                SafeNodeUtils.safeRecycle(rootNode)
            }
            return
        }

        // ── 2. PACKAGE INSTALLER & STORE UNINSTALL PATH (< 0.1ms) ─────────────
        if (PackageClassifier.isInstallerPackage(pkg, className)) {
            val rootNode = try { rootInActiveWindow ?: e.source } catch (_: Exception) { null }
            try {
                if (packageInstallerHandler.processInstallerEvent(
                        rootNode = rootNode,
                        e = e,
                        pkg = pkg,
                        className = className,
                        protectionWanted = protectionWanted,
                        onTamperTriggered = { sourcePkg, reason, targetName, toastMsg ->
                            performGlobalAction(GLOBAL_ACTION_HOME)
                            handleSecurityTamper(
                                sourcePackage = sourcePkg,
                                reason = reason,
                                targetName = targetName,
                                blockType = OverlayBlockType.UNINSTALL_ATTEMPT,
                                customToastMessage = toastMsg
                            )
                        }
                    )) {
                    return
                }
            } finally {
                SafeNodeUtils.safeRecycle(rootNode)
            }
            return
        }

        // ── 3. SYSTEM SETTINGS & OEM SECURITY CENTERS PATH (< 0.2ms) ──────────
        val lowerPkg = pkg.lowercase()
        if (PackageClassifier.isSettingsPackage(lowerPkg)) {
            val rootNode = try { rootInActiveWindow ?: e.source } catch (_: Exception) { null }
            try {
                if (settingsEventHandler.processSettingsEvent(
                        service = this,
                        rootNode = rootNode,
                        e = e,
                        pkg = pkg,
                        className = className,
                        protectionWanted = protectionWanted,
                        isDeviceAdminCurrentlyActive = isDeviceAdminCurrentlyActive,
                        nowRealtime = nowRealtime,
                        isOemScanWindowActive = { isOemScanWindowActive() },
                        getCachedAppLabel = { getCachedAppLabel(it) },
                        onHandleSecurityTamper = { sourcePkg, reason, targetName, blockType, toastMsg ->
                            handleSecurityTamper(sourcePkg, reason, targetName, blockType, toastMsg)
                        }
                    )) {
                    return
                }
            } finally {
                SafeNodeUtils.safeRecycle(rootNode)
            }
            return
        }

        // ── 4. SYSTEM TOOLS FILTER (Dialer, Clock, SystemUI) ──────────────────
        if (PackageClassifier.isSystemOrLauncherPackage(
                pkg = pkg,
                launcherDetector = launcherDetector,
                isProtectionActive = isDeviceAdminCurrentlyActive && protectionWanted,
                isSessionActive = sessionBlockManager.isSessionCurrentlyActive()
            )) {
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

    // Specialized domain handlers delegated to service/handlers/* (LauncherEventHandler, SettingsEventHandler, PackageInstallerHandler)

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
        if (!isPackageChanged && (nowRealtime - timestamps.lastBlockTimestamp < Timings.TRANSITION_DEBOUNCE_MS)) return

        // Manage In-Foreground package transitions
        if (isPackageChanged) {
            foregroundSessionTracker.onPackageChanged(pkg, nowRealtime)
            heartbeatCoordinator.updateHeartbeat(
                pkg = pkg,
                scope = scope,
                isOverlayShowing = { overlayManager.isOverlayShowing },
                rootProvider = { rootInActiveWindow },
                getCachedAppLabel = { getCachedAppLabel(it) },
                onTriggerBlock = { sourcePkg, reason, blockType, targetName, desc ->
                    triggerBlock(
                        sourcePackage = sourcePkg,
                        reason = reason,
                        blockType = blockType,
                        targetName = targetName,
                        description = desc
                    )
                }
            )
        }

        // ── 1. FAST-PATH: INSTANT HARD APP BLOCKER, EXCEEDED TIME LIMITS & SCHEDULES (0ms latency) ─
        val hardBlockResult = hardBlockHandler.checkHardBlock(
            pkg = pkg,
            className = className,
            eventType = e.eventType,
            nowRealtime = nowRealtime,
            blockedPackages = blockedPackages,
            getCachedAppLabel = ::getCachedAppLabel
        )
        val isHardBlocked = when (hardBlockResult) {
            is HardBlockResult.Block -> {
                timestamps.lastBlockTimestamp = nowRealtime
                Log.i(TAG, "Instant 0ms Block triggered for $pkg (${hardBlockResult.targetName}) [isLimit=${hardBlockResult.isLimit}, isSched=${hardBlockResult.isSched}]")
                triggerBlock(
                    sourcePackage = pkg,
                    reason = hardBlockResult.reason,
                    blockType = hardBlockResult.blockType,
                    targetName = hardBlockResult.targetName,
                    description = hardBlockResult.description
                )
                return
            }
            is HardBlockResult.HardBlockedButAllowed -> true
            is HardBlockResult.Clear -> false
        }

        // ── 1.2 MINDFUL PAUSE INTERCEPT (Conscious Breathing & Intent Delay) ─
        if (mindfulPauseHandler.shouldIntercept(pkg, isHardBlocked, isPackageChanged, isWindowTransition)) {
            val appLabel = getCachedAppLabel(pkg)
            timestamps.lastBlockTimestamp = nowRealtime
            mindfulPauseHandler.showMindfulPause(
                pkg = pkg,
                appLabel = appLabel,
                isForegroundPackage = { foregroundSessionTracker.currentPackage == pkg },
                onTriggerBlock = { sourcePkg, reason, blockType, targetName, desc ->
                    triggerBlock(
                        sourcePackage = sourcePkg,
                        reason = reason,
                        blockType = blockType,
                        targetName = targetName,
                        description = desc
                    )
                },
                onForceClose = { windowActions.forceCloseApp(it) }
            )
            return
        }

        // ── 1.5 AUTO-BLOCK NEW APPS JIT INTERCEPT (Catches recently installed apps on open) ─
        val autoBlockAction = autoBlockHandler.checkAutoBlock(
            pkg = pkg,
            isHardBlocked = isHardBlocked,
            getCachedAppLabel = { getCachedAppLabel(it) },
            scope = scope
        )
        if (autoBlockAction != null) {
            timestamps.lastBlockTimestamp = nowRealtime
            triggerBlock(
                sourcePackage = pkg,
                reason = "Auto-Blocked New App: ${autoBlockAction.appLabel}",
                blockType = OverlayBlockType.APP_BLOCK,
                targetName = autoBlockAction.appLabel,
                description = "This newly installed app was automatically blocked by FocusGuard. You can review and unblock it in the app."
            )
            return
        }

        // ── 1.6 UNSUPPORTED BROWSER GUARD (Kills Unknown Browser Loophole) ─
        val unsupportedBrowserAction = browserGuardHandler.checkUnsupportedBrowser(
            pkg = pkg,
            getCachedAppLabel = { getCachedAppLabel(it) }
        )
        if (unsupportedBrowserAction != null) {
            timestamps.lastBlockTimestamp = nowRealtime
            triggerBlock(
                sourcePackage = pkg,
                reason = "Unsupported Browser Blocked: ${unsupportedBrowserAction.appLabel}",
                blockType = OverlayBlockType.APP_BLOCK,
                targetName = unsupportedBrowserAction.appLabel,
                description = "Unsupported browser detected. FocusGuard only permits verified browsers (Chrome, Firefox, Brave, Opera, DuckDuckGo, Tor, Vivaldi, Spin, 1DM, Ecosia) for safe filtering."
            )
            return
        }

        // ── ANTI-CLONING: STRUCTURAL CLONED TARGET VIEW ID SCAN (Layer 2) ─
        val clonedAppAction = clonedAppScanHandler.checkClonedTarget(
            rootNode = rootNode,
            pkg = pkg,
            isProtectionWanted = protectionWanted,
            getCachedAppLabel = { getCachedAppLabel(it) }
        )
        if (clonedAppAction != null) {
            timestamps.lastBlockTimestamp = nowRealtime
            triggerBlock(
                sourcePackage = pkg,
                reason = "Cloned Distraction App Blocked: ${clonedAppAction.appLabel}",
                blockType = OverlayBlockType.CLONER_BLOCK,
                targetName = "Cloned App (${clonedAppAction.appLabel})",
                description = "Target app views were detected running inside an unverified container or modified APK clone."
            )
            return
        }

        // ── 1.8 ULTRA-FAST REELS INTERCEPT (< 1ms Fast Path, Zero Latency) ─
        val reelAction = reelDetectionHandler.detectReelOrShort(
            e = e,
            rootNode = rootNode,
            pkg = pkg,
            isDebounceElapsed = (nowRealtime - timestamps.lastBlockTimestamp >= 800L),
            isOverlayShowing = overlayManager.isOverlayShowing
        )
        if (reelAction != null) {
            timestamps.lastBlockTimestamp = nowRealtime
            Log.i(TAG, "Ultra-fast ${reelAction.targetLabel} block triggered for $pkg")
            triggerBlock(
                sourcePackage = pkg,
                reason = "${reelAction.targetLabel} Blocked",
                blockType = OverlayBlockType.SHORT_OR_REEL_BLOCK,
                targetName = reelAction.targetLabel,
                description = reelAction.targetDesc
            )
            return
        }

        // ── 2. FOCUS SCHEDULES & TIME LIMITS (Evaluated only on window transition) ─
        if ((isPackageChanged || isWindowTransition) && repository.hasActiveSchedulesOrLimits(pkg)) {
            val appLabel = getCachedAppLabel(pkg)

            scope.launch {
                val liveSessionMs = foregroundSessionTracker.liveSessionMs(android.os.SystemClock.elapsedRealtime())
                scheduleLimitHandler.handleScheduleOrLimit(
                    pkg = pkg,
                    appLabel = appLabel,
                    liveSessionMs = liveSessionMs,
                    isOverlayShowing = { overlayManager.isOverlayShowing },
                    getLastBlockTimestamp = { timestamps.lastBlockTimestamp },
                    clock = { android.os.SystemClock.elapsedRealtime() },
                    onUpdateLastBlockTimestamp = { timestamps.lastBlockTimestamp = it },
                    onTriggerBlock = { sourcePkg, reason, blockType, targetName, description ->
                        triggerBlock(
                            sourcePackage = sourcePkg,
                            reason = reason,
                            blockType = blockType,
                            targetName = targetName,
                            description = description
                        )
                    },
                    onStartForegroundPolling = { p, label ->
                        foregroundSessionTracker.startForegroundPollingIfTracked(p, label)
                    }
                )
            }
        }

        // ── 3. KEYWORD & CONTENT BLOCKING (Browsers, YouTube On-Screen Metadata, and Universal Apps) ─
        val keywordHandled = keywordDetectionHandler.detectAndHandle(
            e = e,
            rootNode = rootNode,
            pkg = pkg,
            getCachedAppLabel = { getCachedAppLabel(it) },
            triggerBlock = { sourcePkg, reason, blockType, targetName, description ->
                triggerBlock(
                    sourcePackage = sourcePkg,
                    reason = reason,
                    blockType = blockType,
                    targetName = targetName,
                    description = description
                )
            }
        )
        if (keywordHandled) return
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
     * FIX C5 -- Double-HOME Prevention:
     * We send HOME here (step 2) to exit the dangerous settings/installer screen.
     * triggerBlock() -> forceCloseApp() would send a SECOND HOME via its onAttached callback,
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
        tamperResponseHandler.handleSecurityTamper(
            sourcePackage = sourcePackage,
            reason = reason,
            targetName = targetName,
            blockType = blockType,
            customToastMessage = customToastMessage,
            isOemScanWindowActive = { isOemScanWindowActive() },
            onUpdateLastBlockTimestamp = { timestamps.lastBlockTimestamp = it },
            triggerBlockHaptic = { windowActions.triggerBlockHaptic() },
            showHudToast = { msg ->
                mainHandler.post {
                    Toast.makeText(applicationContext, msg, Toast.LENGTH_LONG).show()
                }
            },
            performGlobalAction = { performGlobalAction(it) },
            launchHomeIntent = {
                val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                }
                startActivity(homeIntent)
            },
            postDelayed = { delayMs, action -> mainHandler.postDelayed(action, delayMs) },
            onTriggerBlockWithHomeAlreadySent = { srcPkg, r, bt, tn, desc ->
                triggerBlockWithHomeAlreadySent(
                    sourcePackage = srcPkg,
                    reason = r,
                    blockType = bt,
                    targetName = tn,
                    description = desc
                )
            },
            scope = scope
        )
    }

    /**
     * Variant of [triggerBlock] used exclusively by [handleSecurityTamper].
     *
     * Because [handleSecurityTamper] already sent GLOBAL_ACTION_HOME before mounting the
     * overlay, the normal [forceCloseApp] inside [triggerBlock]'s onAttached callback must
     * NOT send another HOME. This variant's onAttached callback only kills the background
     * process of the source package (if non-system) -- no redundant HOME dispatch.
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
        blockTrigger.triggerBlockWithHomeAlreadySent(
            sourcePackage = sourcePackage,
            reason = reason,
            blockType = blockType,
            targetName = targetName,
            description = description
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Trigger Block with Custom Contextual Details
    // ─────────────────────────────────────────────────────────────────────────

    private fun triggerBlock(
        sourcePackage: String,
        reason: String,
        blockType: OverlayBlockType = OverlayBlockType.UNINSTALL_ATTEMPT,
        targetName: String = "FocusGuard Protection",
        description: String = "Action is protected by friction delay."
    ) {
        blockTrigger.triggerBlock(
            sourcePackage = sourcePackage,
            reason = reason,
            blockType = blockType,
            targetName = targetName,
            description = description
        )
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


    // ─────────────────────────────────────────────────────────────────────────
    // Hardware Back Key Consumption
    // ─────────────────────────────────────────────────────────────────────────

    override fun onKeyEvent(event: KeyEvent?): Boolean {
        val handled = keyEventHandler.handleKeyEvent(event, foregroundSessionTracker.currentPackage)
        if (handled != null) {
            return handled
        }
        return super.onKeyEvent(event)
    }

    /**
     * Zero-latency device admin check -- delegates to [DeviceAdminManager] cache refreshed every 30 s.
     * Never makes a Binder IPC call on the hot accessibility-event path (AGENTS Rule 5).
     */
    private fun isDeviceAdminActive(): Boolean = deviceAdminManager.isDeviceAdminActive()

    companion object {
        /**
         * STATIC AtomicLong -- survives AccessibilityService instance restarts.
         *
         * Vivo/OEM force-unbinds and creates a NEW service instance when it scans settings.
         * An instance field resets to 0 on the new instance -- losing the suppression window.
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
            Log.i(TAG_COMPANION, "OEM security scan window armed for ${windowMs}ms (static) -- handleSecurityTamper suppressed")
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
            instanceRef?.get()?.deviceAdminManager?.updateCache(isActive)
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

            AccessibilityServiceDisabler.removeFromEnabledServices(
                context,
                FocusGuardAccessibilityService::class.java.name
            )
        }
    }
}
