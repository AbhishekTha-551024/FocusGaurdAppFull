package com.focusguard.app.ui.onboarding

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusguard.app.ui.auth.AuthViewModel
import com.focusguard.app.ui.components.SignInRequiredDialog
import com.focusguard.app.ui.block.BlockParentScreen
import com.focusguard.app.ui.blocker.AppBlockerViewModel
import com.focusguard.app.ui.home.HomeScreen
import com.focusguard.app.ui.home.HomeViewModel
import com.focusguard.app.ui.keywordblocker.KeywordBlockerViewModel
import com.focusguard.app.ui.limits.LimitsParentScreen
import com.focusguard.app.ui.protection.OemGuidanceScreen
import com.focusguard.app.ui.schedules.ScheduleViewModel
import com.focusguard.app.ui.settings.SettingsScreen
import com.focusguard.app.ui.settings.SettingsViewModel
import com.focusguard.app.ui.session.DeepFocusScreen
import com.focusguard.app.ui.theme.*
import com.focusguard.app.ui.timelimits.TimeLimitViewModel
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

enum class MainTab(val title: String, val icon: ImageVector) {
    HOME("Home", Icons.Default.Home),
    BLOCK("Block", Icons.Default.Block),
    LIMITS("Limits", Icons.Default.HourglassTop),
    SETTINGS("Settings", Icons.Default.Settings)
}

@AndroidEntryPoint
class OnboardingActivity : ComponentActivity() {

    private val onboardingViewModel: OnboardingViewModel by viewModels()
    private val homeViewModel: HomeViewModel by viewModels()
    private val appBlockerViewModel: AppBlockerViewModel by viewModels()
    private val keywordBlockerViewModel: KeywordBlockerViewModel by viewModels()
    private val timeLimitViewModel: TimeLimitViewModel by viewModels()
    private val scheduleViewModel: ScheduleViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()
    private val authViewModel: AuthViewModel by viewModels()
    private val mindfulAppsViewModel: com.focusguard.app.ui.mindful.MindfulAppsViewModel by viewModels()
    private val sessionViewModel: com.focusguard.app.ui.session.SessionViewModel by viewModels()

    @Inject
    lateinit var sessionBlockManager: com.focusguard.app.blockers.SessionBlockManager

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Dismiss any lingering foreground watchdog notification
        com.focusguard.app.service.ProtectionWatchdogService.stopService(this)

        setContent {
            FocusGuardTheme {
                val isOnboardingComplete by onboardingViewModel.isOnboardingComplete.collectAsStateWithLifecycle()

                val mainPagerState = rememberPagerState(initialPage = 0, pageCount = { MainTab.entries.size })
                val coroutineScope = rememberCoroutineScope()
                val haptic = LocalHapticFeedback.current
                var blockSubTab by remember { mutableIntStateOf(0) }
                var limitsSubTab by remember { mutableIntStateOf(0) }
                var isOemGuidanceOpen by remember { mutableStateOf(false) }
                var isDeepFocusOpen by remember { mutableStateOf(false) }
                var pendingSignInFeature by remember { mutableStateOf<String?>(null) }
                var pendingSignInAction by remember { mutableStateOf<(() -> Unit)?>(null) }

                val authState by authViewModel.uiState.collectAsStateWithLifecycle()

                LaunchedEffect(authState.user) {
                    if (authState.user != null) {
                        val action = pendingSignInAction
                        pendingSignInFeature = null
                        pendingSignInAction = null
                        action?.invoke()
                    }
                }

                val requireSignIn: (String, () -> Unit) -> Unit = { feature, action ->
                    if (authState.user == null) {
                        pendingSignInAction = action
                        pendingSignInFeature = feature
                    } else {
                        action()
                    }
                }

                val currentTab = MainTab.entries[mainPagerState.currentPage]

                // Pre-warm and continuously collect sessionState at root level
                val sessionState by sessionViewModel.uiState.collectAsStateWithLifecycle()

                // Handle system Back button to close sub-screens first, then return to Home tab
                BackHandler(enabled = isOnboardingComplete && (isDeepFocusOpen || isOemGuidanceOpen || mainPagerState.currentPage != 0)) {
                    when {
                        isDeepFocusOpen -> isDeepFocusOpen = false
                        isOemGuidanceOpen -> isOemGuidanceOpen = false
                        mainPagerState.currentPage != 0 -> coroutineScope.launch { mainPagerState.animateScrollToPage(0) }
                    }
                }

                if (!isOnboardingComplete) {
                    // Task 2: Single-Step Onboarding Wizard
                    OnboardingWizardScreen(
                        onComplete = {
                            onboardingViewModel.completeOnboarding()
                        }
                    )
                } else if (isOemGuidanceOpen) {
                    OemGuidanceScreen(
                        onBack = {
                            isOemGuidanceOpen = false
                            homeViewModel.acknowledgeOemGuidance()
                        }
                    )
                } else if (isDeepFocusOpen) {
                    DeepFocusScreen(
                        sessionState = sessionState,
                        availableApps = sessionState.availableApps,
                        onSelectDuration = { mins -> sessionViewModel.setDuration(mins) },
                        onOpenPacking = { sessionViewModel.openPackingDialog() },
                        onClosePacking = { sessionViewModel.closePackingDialog() },
                        onStartSession = {
                            sessionViewModel.startSession()
                        },
                        onRequestAbort = { sessionViewModel.openAbortDialog() },
                        onConfirmAbort = { sessionViewModel.confirmEmergencyAbort() },
                        onDismissAbort = { sessionViewModel.closeAbortDialog() },
                        onTogglePackageInSuitcase = { pkg -> sessionViewModel.togglePackageInSuitcase(pkg) },
                        onQuickPackUtilities = { apps -> sessionViewModel.quickPackUtilities(apps) },
                        onSearchSuitcaseQueryChange = { q -> sessionViewModel.setSearchQuery(q) },
                        onBack = { isDeepFocusOpen = false }
                    )
                } else {
                    // Task 1: 4-Tab Main App Navigation Structure with 0ms Keep-Alive Retention
                    Scaffold(
                        bottomBar = {
                            NavigationBar(
                                    containerColor = Forest900,
                                    contentColor = Color.White
                                ) {
                                    MainTab.entries.forEach { tab ->
                                    val isSelected = currentTab == tab
                                    NavigationBarItem(
                                        selected = isSelected,
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            coroutineScope.launch {
                                                mainPagerState.animateScrollToPage(tab.ordinal)
                                            }
                                        },
                                        icon = {
                                            val isSessionActive = tab == MainTab.HOME && sessionState.isActive
                                            if (isSessionActive) {
                                                BadgedBox(
                                                    badge = {
                                                        Badge(
                                                            containerColor = Emerald500,
                                                            contentColor = Forest950
                                                        ) {
                                                            Text("ACTIVE", fontSize = 8.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                                                        }
                                                    }
                                                ) {
                                                    Icon(
                                                        imageVector = tab.icon,
                                                        contentDescription = tab.title
                                                    )
                                                }
                                            } else {
                                                Icon(
                                                    imageVector = tab.icon,
                                                    contentDescription = tab.title
                                                )
                                            }
                                        },
                                        label = {
                                            Text(
                                                text = tab.title,
                                                fontSize = 12.sp
                                            )
                                        },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = Emerald500,
                                            selectedTextColor = Emerald500,
                                            unselectedIconColor = White60,
                                            unselectedTextColor = White60,
                                            indicatorColor = Emerald500.copy(alpha = 0.15f)
                                        )
                                    )
                                }
                            }
                        },
                        containerColor = Forest950
                ) { paddingValues ->
                        val homeState by homeViewModel.uiState.collectAsStateWithLifecycle()
                        val settingsState by settingsViewModel.uiState.collectAsStateWithLifecycle()

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(paddingValues)
                        ) {
                            HorizontalPager(
                                state = mainPagerState,
                                key = { page -> "main_tab_${MainTab.entries[page].name}" },
                                userScrollEnabled = false,
                                beyondViewportPageCount = 0,
                                modifier = Modifier.fillMaxSize()
                            ) { pageIndex ->
                                when (MainTab.entries[pageIndex]) {
                                    MainTab.HOME -> {
                                        HomeScreen(
                                            uiState = homeState,
                                            settingsUiState = settingsState,
                                            authUser = authState.user,
                                            isAuthLoading = authState.isLoading,
                                            onSignInWithGoogle = { authViewModel.signInWithGoogle(this@OnboardingActivity) },
                                            onRequireSignIn = { feature -> pendingSignInFeature = feature },
                                            onSendPartnerInvite = { email -> settingsViewModel.sendPartnerInvite(email) },
                                            onNavigateToFocus = {
                                                isDeepFocusOpen = true
                                            },
                                            onNavigateToTab = { tabIdx, subTabIdx ->
                                                when (tabIdx) {
                                                    1 -> {
                                                        blockSubTab = subTabIdx
                                                        coroutineScope.launch {
                                                            mainPagerState.animateScrollToPage(MainTab.BLOCK.ordinal)
                                                        }
                                                    }
                                                    2 -> {
                                                        limitsSubTab = subTabIdx
                                                        coroutineScope.launch {
                                                            mainPagerState.animateScrollToPage(MainTab.LIMITS.ordinal)
                                                        }
                                                    }
                                                }
                                            },
                                            onNavigateToSettings = {
                                                coroutineScope.launch {
                                                    mainPagerState.animateScrollToPage(MainTab.SETTINGS.ordinal)
                                                }
                                            },
                                            onRequestToggleUninstallProtection = { enable ->
                                                if (enable) {
                                                    requireSignIn("Uninstall Protection") {
                                                        settingsViewModel.requestToggleUninstallProtection(true)
                                                    }
                                                } else {
                                                    settingsViewModel.requestToggleUninstallProtection(false)
                                                }
                                            },
                                            onConfirmEnableUninstallProtection = { days ->
                                                settingsViewModel.confirmEnableUninstallProtection(days)
                                            },
                                            onDismissUninstallDurationDialog = {
                                                settingsViewModel.dismissUninstallDurationDialog()
                                            },
                                            onDismissDeviceAdminRequiredDialog = {
                                                settingsViewModel.dismissDeviceAdminRequiredDialog()
                                            },
                                            onDismissAccountabilityRequiredDialog = {
                                                settingsViewModel.dismissAccountabilityRequiredDialog()
                                            },
                                            onProceedToSetupChallenge = {
                                                settingsViewModel.proceedToSetupChallenge()
                                            },
                                            onCancelChallengeSetup = {
                                                settingsViewModel.cancelChallengeSetup()
                                            },
                                            onConfirmEnablePendingFeed = {
                                                settingsViewModel.confirmEnablePendingFeed()
                                            },
                                            onDismissPendingFeedWarning = {
                                                settingsViewModel.dismissPendingFeedWarning()
                                            },
                                            onToggleShortsReelsBlock = { enable ->
                                            settingsViewModel.toggleShortsReelsBlock(enable)
                                        },
                                        onToggleInstagramReelsBlock = { enable ->
                                            settingsViewModel.toggleInstagramReelsBlock(enable)
                                        },
                                        onToggleFacebookReelsBlock = { enable ->
                                            settingsViewModel.toggleFacebookReelsBlock(enable)
                                        },
                                        onToggleYouTubeShortsBlock = { enable ->
                                            settingsViewModel.toggleYouTubeShortsBlock(enable)
                                        },
                                        onToggleIncognitoBlock = { enable ->
                                            settingsViewModel.toggleIncognitoBlock(enable)
                                        },
                                        onToggleBlockUnsupportedBrowsers = { enable ->
                                            settingsViewModel.toggleBlockUnsupportedBrowsers(enable)
                                        },
                                        onToggleAutoBlockNewApps = { enable ->
                                            settingsViewModel.toggleAutoBlockNewApps(enable)
                                        },
                                        onPickWallpaper = { uri ->
                                            settingsViewModel.saveWallpaperFromUri(uri)
                                        },
                                        onClearWallpaper = {
                                            settingsViewModel.clearWallpaper()
                                        },
                                        onSetPreset = { id ->
                                            settingsViewModel.setPreset(id)
                                        },
                                        onSetDimAlpha = { alpha ->
                                            settingsViewModel.setDimAlpha(alpha)
                                        },
                                        onSetCustomBlockMessage = { msg ->
                                            settingsViewModel.setCustomBlockMessage(msg)
                                        },
                                        onSetBlockCountdownSeconds = { sec ->
                                            settingsViewModel.setBlockCountdownSeconds(sec)
                                        },
                                        onPreviewOverlay = {
                                            settingsViewModel.previewOverlay()
                                        },
                                        onRequestRemoveChallenge = {
                                            settingsViewModel.requestRemoveChallenge()
                                        },
                                        onRequestChangeChallenge = { type, sentence, delay ->
                                            if (type == com.focusguard.app.data.db.ChallengeType.PARTNER) {
                                                requireSignIn("Accountability Challenge") {
                                                    settingsViewModel.requestChangeChallenge(type, sentence, delay)
                                                }
                                            } else {
                                                settingsViewModel.requestChangeChallenge(type, sentence, delay)
                                            }
                                        },
                                        onActivateChallenge = {
                                            settingsViewModel.activateChallenge()
                                        },
                                        onSetCommitmentSentence = { sentence ->
                                            settingsViewModel.setCommitmentSentence(sentence)
                                        },
                                        onChallengeSuccess = { ctx ->
                                            settingsViewModel.onChallengeSuccess(ctx)
                                        },
                                        onCancelChallenge = {
                                            settingsViewModel.onCancelChallenge()
                                        },
                                        onRefreshPermissions = {
                                            homeViewModel.refreshPermissions()
                                        },
                                        onNavigateToOemGuidance = {
                                            isOemGuidanceOpen = true
                                        }
                                    )
                                }

                                MainTab.BLOCK -> {
                                    val appBlockerState by appBlockerViewModel.uiState.collectAsStateWithLifecycle()
                                    val mindfulAppsState by mindfulAppsViewModel.uiState.collectAsStateWithLifecycle()
                                    val keywordBlockerState by keywordBlockerViewModel.uiState.collectAsStateWithLifecycle()
                                    BlockParentScreen(
                                        initialSubTab = blockSubTab,
                                        appBlockerState = appBlockerState,
                                        mindfulAppsState = mindfulAppsState,
                                        keywordBlockerState = keywordBlockerState,
                                        onAppSearchQueryChange = { appBlockerViewModel.onSearchQueryChange(it) },
                                        onToggleAppBlock = { app, isBlocked ->
                                            if (isBlocked) {
                                                requireSignIn("App Blocker") {
                                                    appBlockerViewModel.toggleAppBlock(app, true)
                                                }
                                            } else {
                                                appBlockerViewModel.toggleAppBlock(app, false)
                                            }
                                        },
                                        onAppChallengeSuccess = { ctx -> appBlockerViewModel.onChallengeSuccess(ctx) },
                                        onAppCancelChallenge = { appBlockerViewModel.onCancelChallenge() },
                                        onAppCancelPendingChange = { id -> appBlockerViewModel.cancelPendingChange(id) },
                                        onAppActivateChallenge = { appBlockerViewModel.onActivateChallengeForApp() },
                                        onAppDismissAccountability = { appBlockerViewModel.onDismissAccountabilityRequired() },
                                        onMindfulSearchQueryChange = { mindfulAppsViewModel.onSearchQueryChange(it) },
                                        onToggleMindfulApp = { app, enable ->
                                            mindfulAppsViewModel.toggleMindfulApp(app, enable)
                                        },
                                        onConfirmAddMindfulApp = { app ->
                                            requireSignIn("Mindful Apps") {
                                                mindfulAppsViewModel.confirmAddMindfulApp(app)
                                            }
                                        },
                                        onDismissConfirmAddMindfulApp = { mindfulAppsViewModel.dismissAddMindfulApp() },
                                        onMindfulChallengeSuccess = { ctx -> mindfulAppsViewModel.onChallengeSuccess(ctx) },
                                        onMindfulCancelChallenge = { mindfulAppsViewModel.onCancelChallenge() },
                                        onMindfulActivateChallenge = { mindfulAppsViewModel.onActivateChallengeForApp() },
                                        onMindfulDismissAccountability = { mindfulAppsViewModel.onDismissAccountabilityRequired() },
                                        onUpdateMindfulAllowanceMinutes = { pkg, mins -> mindfulAppsViewModel.updateDefaultAllowance(pkg, mins) },
                                        onOpenMindfulJournal = { mindfulAppsViewModel.openJournalDialog() },
                                        onDismissMindfulJournal = { mindfulAppsViewModel.dismissJournalDialog() },
                                        onKeywordInputChange = { keywordBlockerViewModel.onKeywordInputChange(it) },
                                        onAddKeyword = {
                                            requireSignIn("Website Blocker") {
                                                keywordBlockerViewModel.addKeyword()
                                            }
                                        },
                                        onRemoveKeyword = { keywordBlockerViewModel.removeKeyword(it) },
                                        onKeywordSearchQueryChange = { keywordBlockerViewModel.onSearchQueryChange(it) },
                                        onKeywordChallengeSuccess = { ctx -> keywordBlockerViewModel.onChallengeSuccess(ctx) },
                                        onKeywordCancelChallenge = { keywordBlockerViewModel.onCancelChallenge() },
                                        onKeywordCancelPendingChange = { id -> keywordBlockerViewModel.cancelPendingChange(id) },
                                        onKeywordActivateChallenge = { keywordBlockerViewModel.onActivateChallengeForKeyword() },
                                        onKeywordDismissAccountability = { keywordBlockerViewModel.onDismissAccountabilityRequired() }
                                    )
                                }

                                MainTab.LIMITS -> {
                                    val timeLimitState by timeLimitViewModel.uiState.collectAsStateWithLifecycle()
                                    val scheduleState by scheduleViewModel.uiState.collectAsStateWithLifecycle()
                                    LimitsParentScreen(
                                        initialSubTab = limitsSubTab,
                                        timeLimitState = timeLimitState,
                                        scheduleState = scheduleState,
                                        onTimeLimitSearchChange = { timeLimitViewModel.onSearchQueryChange(it) },
                                        onToggleTimeLimit = { app, currentLimit, isEnabled ->
                                            if (isEnabled) {
                                                requireSignIn("Time Limits") {
                                                    timeLimitViewModel.toggleTimeLimit(app, currentLimit, true)
                                                }
                                            } else {
                                                timeLimitViewModel.toggleTimeLimit(app, currentLimit, false)
                                            }
                                        },
                                        onSetTimeLimitMinutes = { app, minutes ->
                                            requireSignIn("Time Limits") {
                                                timeLimitViewModel.setTimeLimit(app, minutes)
                                            }
                                        },
                                        onRequestUsagePermission = {
                                            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                                                data = android.net.Uri.parse("package:$packageName")
                                            }
                                            try {
                                                startActivity(intent)
                                            } catch (e: Exception) {
                                                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                                            }
                                        },
                                        onTimeLimitRefresh = { timeLimitViewModel.refreshPermissions() },
                                        onTimeLimitChallengeSuccess = { ctx -> timeLimitViewModel.onChallengeSuccess(ctx) },
                                        onTimeLimitCancelChallenge = { timeLimitViewModel.onCancelChallenge() },
                                        onTimeLimitCancelPendingChange = { id -> timeLimitViewModel.cancelPendingChange(id) },
                                        onTimeLimitActivateChallenge = { timeLimitViewModel.onActivateChallengeForLimit() },
                                        onTimeLimitDismissAccountability = { timeLimitViewModel.onDismissAccountabilityRequired() },
                                        onOpenAddScheduleDialog = {
                                            requireSignIn("Focus Schedules") {
                                                scheduleViewModel.openAddDialog()
                                            }
                                        },
                                        onCloseAddScheduleDialog = { scheduleViewModel.closeAddDialog() },
                                        onAddSchedule = { title, pkgName, appName, daysMask, startMin, endMin ->
                                            requireSignIn("Focus Schedules") {
                                                scheduleViewModel.addSchedule(title, pkgName, appName, daysMask, startMin, endMin)
                                            }
                                        },
                                        onDeleteSchedule = { id, title -> scheduleViewModel.deleteSchedule(id, title) },
                                        onToggleSchedule = { schedule, enabled ->
                                            if (enabled) {
                                                requireSignIn("Focus Schedules") {
                                                    scheduleViewModel.toggleSchedule(schedule, true)
                                                }
                                            } else {
                                                scheduleViewModel.toggleSchedule(schedule, false)
                                            }
                                        },
                                        onScheduleChallengeSuccess = { ctx -> scheduleViewModel.onChallengeSuccess(ctx) },
                                        onScheduleCancelChallenge = { scheduleViewModel.onCancelChallenge() },
                                        onScheduleCancelPendingChange = { id -> scheduleViewModel.cancelPendingChange(id) },
                                        onScheduleActivateChallenge = { scheduleViewModel.onActivateChallengeForSchedule() },
                                        onScheduleDismissAccountability = { scheduleViewModel.onDismissAccountabilityRequired() }
                                    )
                                }

                                MainTab.SETTINGS -> {
                                    SettingsScreen(
                                        uiState = settingsState,
                                        onRequestChangeChallenge = { type, sentence, hours ->
                                            if (type == com.focusguard.app.data.db.ChallengeType.PARTNER) {
                                                requireSignIn("Accountability Challenge") {
                                                    settingsViewModel.requestChangeChallenge(type, sentence, hours)
                                                }
                                            } else {
                                                settingsViewModel.requestChangeChallenge(type, sentence, hours)
                                            }
                                        },
                                        onRequestToggleUninstallProtection = { enable ->
                                            if (enable) {
                                                requireSignIn("Uninstall Protection") {
                                                    settingsViewModel.requestToggleUninstallProtection(true)
                                                }
                                            } else {
                                                settingsViewModel.requestToggleUninstallProtection(false)
                                            }
                                        },
                                        onChallengeSuccess = { ctx -> settingsViewModel.onChallengeSuccess(ctx) },
                                        onCancelChallenge = { settingsViewModel.onCancelChallenge() },
                                        onNavigateToOemGuidance = { isOemGuidanceOpen = true },
                                        onRefreshPermissions = { settingsViewModel.refreshPermissions() },
                                        onNavigateToHomeSetup = {
                                            coroutineScope.launch {
                                                mainPagerState.animateScrollToPage(0)
                                            }
                                        },
                                        // Phase 7: Overlay Wallpaper
                                        onPickWallpaper = { uri -> settingsViewModel.saveWallpaperFromUri(uri) },
                                        onClearWallpaper = { settingsViewModel.clearWallpaper() },
                                        onSetPreset = { id -> settingsViewModel.setPreset(id) },
                                        onSetDimAlpha = { alpha -> settingsViewModel.setDimAlpha(alpha) },
                                        onPreviewOverlay = { settingsViewModel.previewOverlay() },
                                        onConfirmEnableUninstallProtection = { days ->
                                            settingsViewModel.confirmEnableUninstallProtection(days)
                                        },
                                        onDismissUninstallDurationDialog = {
                                            settingsViewModel.dismissUninstallDurationDialog()
                                        },
                                        onDismissAccountabilityRequiredDialog = {
                                            settingsViewModel.dismissAccountabilityRequiredDialog()
                                        },
                                        onProceedToSetupChallenge = {
                                            settingsViewModel.proceedToSetupChallenge()
                                        },
                                        onCancelChallengeSetup = {
                                            settingsViewModel.cancelChallengeSetup()
                                        },
                                        onDismissDeviceAdminRequiredDialog = {
                                            settingsViewModel.dismissDeviceAdminRequiredDialog()
                                        },
                                        onConfirmEnablePendingFeed = {
                                            settingsViewModel.confirmEnablePendingFeed()
                                        },
                                        onDismissPendingFeedWarning = {
                                            settingsViewModel.dismissPendingFeedWarning()
                                        },
                                        onToggleShortsReelsBlock = { enable ->
                                            settingsViewModel.toggleShortsReelsBlock(enable)
                                        },
                                        onToggleInstagramReelsBlock = { enable ->
                                            settingsViewModel.toggleInstagramReelsBlock(enable)
                                        },
                                        onToggleFacebookReelsBlock = { enable ->
                                            settingsViewModel.toggleFacebookReelsBlock(enable)
                                        },
                                        onToggleYouTubeShortsBlock = { enable ->
                                            settingsViewModel.toggleYouTubeShortsBlock(enable)
                                        },
                                        onToggleIncognitoBlock = { enable ->
                                            settingsViewModel.toggleIncognitoBlock(enable)
                                        },
                                        onToggleAutoBlockNewApps = { enable ->
                                            settingsViewModel.toggleAutoBlockNewApps(enable)
                                        },
                                        onSetCustomBlockMessage = { msg ->
                                            settingsViewModel.setCustomBlockMessage(msg)
                                        },
                                        onSetBlockCountdownSeconds = { sec ->
                                            settingsViewModel.setBlockCountdownSeconds(sec)
                                        },
                                        onOpenSupportCodeDialog = { settingsViewModel.openSupportCodeDialog() },
                                        onDismissSupportCodeDialog = { settingsViewModel.dismissSupportCodeDialog() },
                                        onVerifySupportCode = { code -> settingsViewModel.verifySupportCode(code) },
                                        onApplySupportAction = { action, payload -> settingsViewModel.applySupportAction(action, payload) },
                                        authUser = authState.user,
                                        isAuthLoading = authState.isLoading,
                                        onSignInWithGoogle = { authViewModel.signInWithGoogle(this@OnboardingActivity) },
                                        onSignOut = { authViewModel.signOut(this@OnboardingActivity) },
                                        onSendPartnerInvite = { email -> settingsViewModel.sendPartnerInvite(email) },
                                        onCancelPartnerInvite = { id -> settingsViewModel.cancelPartnerInvite(id) },
                                        onAcceptPartnerInvite = { id -> settingsViewModel.acceptPartnerInvite(id) },
                                        onDeclinePartnerInvite = { id -> settingsViewModel.declinePartnerInvite(id) },
                                        onRequestRevokePartner = { id -> settingsViewModel.requestRevokePartner(id) }
                                    )

                                    LaunchedEffect(authState.errorMessage) {
                                        val err = authState.errorMessage
                                        if (!err.isNullOrBlank()) {
                                            android.widget.Toast.makeText(this@OnboardingActivity, err, android.widget.Toast.LENGTH_LONG).show()
                                            authViewModel.clearError()
                                        }
                                    }

                                }
                            }
                        }
                    }
                }

                pendingSignInFeature?.let { featureName ->
                    SignInRequiredDialog(
                        featureName = featureName,
                        isLoading = authState.isLoading,
                        onSignInWithGoogle = { authViewModel.signInWithGoogle(this@OnboardingActivity) },
                        onDismiss = {
                            pendingSignInFeature = null
                            pendingSignInAction = null
                        }
                    )
                }
            }
        }
    }
}

    override fun onResume() {
        super.onResume()
        if (::sessionBlockManager.isInitialized && sessionBlockManager.isSessionCurrentlyActive()) {
            com.focusguard.app.ui.session.SessionLockActivity.start(this)
            return
        }
        onboardingViewModel.refresh()
        homeViewModel.refreshPermissions()
    }
}
