package com.focusguard.app.ui.onboarding

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusguard.app.R
import com.focusguard.app.blockers.SessionBlockManager
import com.focusguard.app.core.designsystem.theme.FocusGuardTheme
import com.focusguard.app.core.designsystem.theme.Forest950
import com.focusguard.app.feature.appblocker.ui.AppBlockerViewModel
import com.focusguard.app.feature.auth.ui.AuthViewModel
import com.focusguard.app.feature.auth.ui.LoginScreen
import com.focusguard.app.feature.home.ui.HomeViewModel
import com.focusguard.app.feature.keywordblocker.ui.KeywordBlockerViewModel
import com.focusguard.app.feature.mindful.ui.MindfulAppsViewModel
import com.focusguard.app.feature.schedules.ui.ScheduleViewModel
import com.focusguard.app.feature.session.ui.DeepFocusScreen
import com.focusguard.app.feature.session.ui.SessionLockActivity
import com.focusguard.app.feature.session.ui.SessionViewModel
import com.focusguard.app.feature.settings.ui.SettingsViewModel
import com.focusguard.app.feature.timelimits.ui.TimeLimitViewModel
import com.focusguard.app.receiver.FocusGuardDeviceAdminReceiver
import com.focusguard.app.core.common.constants.Timings
import com.focusguard.app.feature.auth.ui.components.SignInRequiredDialog
import com.focusguard.app.ui.commitment.CommitmentRestoreGate
import com.focusguard.app.ui.navigation.MainBottomBar
import com.focusguard.app.ui.navigation.MainContentPager
import com.focusguard.app.ui.navigation.MainTab
import com.focusguard.app.ui.protection.OemGuidanceScreen
import com.focusguard.app.sync.CloudSyncEngine
import com.focusguard.app.sync.CloudSyncStartupGate
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

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
    private val mindfulAppsViewModel: MindfulAppsViewModel by viewModels()
    private val sessionViewModel: SessionViewModel by viewModels()

    @Inject
    lateinit var sessionBlockManager: SessionBlockManager
    @Inject
    lateinit var cloudSyncEngine: CloudSyncEngine

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (intent?.getBooleanExtra("deactivate_admin", false) == true) {
            FocusGuardDeviceAdminReceiver.deactivateDeviceAdmin(this)
            finish()
            return
        }

        setContent {
            FocusGuardTheme {
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
                val cloudSyncStatus by cloudSyncEngine.syncStatus.collectAsStateWithLifecycle()
                val settingsUiState by settingsViewModel.uiState.collectAsStateWithLifecycle()
                val sessionState by sessionViewModel.uiState.collectAsStateWithLifecycle()
                val isOnboardingComplete by onboardingViewModel.isOnboardingComplete.collectAsStateWithLifecycle()
                val restoreStatus by onboardingViewModel.commitmentRestoreStatus.collectAsStateWithLifecycle()

                LaunchedEffect(authState.user) {
                    if (authState.user != null) {
                        onboardingViewModel.completeOnboarding()
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

                BackHandler(enabled = isDeepFocusOpen || isOemGuidanceOpen || mainPagerState.currentPage != 0) {
                    when {
                        isDeepFocusOpen -> isDeepFocusOpen = false
                        isOemGuidanceOpen -> isOemGuidanceOpen = false
                        mainPagerState.currentPage != 0 -> coroutineScope.launch { mainPagerState.scrollToPage(0) }
                    }
                }

                // While the device commitment record is being consulted there is nowhere to go.
                // Hard ceiling: the lookup is best-effort, so it can never be what locks the user out
                // of their own phone. Releasing the gate does not release protection — a late restore
                // still re-arms the lock, and the next cold start asks again.
                var isRestoreGateTimedOut by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    delay(Timings.COMMITMENT_GATE_HARD_TIMEOUT_MS)
                    isRestoreGateTimedOut = true
                }
                val isRestoreGating = restoreStatus.isGating && !isRestoreGateTimedOut

                BackHandler(enabled = isRestoreGating) { }

                if (isRestoreGating) {
                    CommitmentRestoreGate(status = restoreStatus)
                } else if (!authState.isAuthResolved) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Forest950)
                    )
                } else if (authState.user == null) {
                    LoginScreen(
                        isLoading = authState.isLoading,
                        errorMessage = authState.errorMessage,
                        onSignInWithGoogle = { authViewModel.signInWithGoogle(this@OnboardingActivity) }
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
                        onStartSession = { sessionViewModel.startSession() },
                        onRequestAbort = { sessionViewModel.openAbortDialog() },
                        onConfirmAbort = { sessionViewModel.confirmEmergencyAbort() },
                        onDismissAbort = { sessionViewModel.closeAbortDialog() },
                        onTogglePackageInSuitcase = { pkg -> sessionViewModel.togglePackageInSuitcase(pkg) },
                        onQuickPackUtilities = { apps -> sessionViewModel.quickPackUtilities(apps) },
                        onSearchSuitcaseQueryChange = { q -> sessionViewModel.setSearchQuery(q) },
                        onBack = { isDeepFocusOpen = false }
                    )
                } else if (CloudSyncStartupGate.shouldWaitForSettings(
                        isAuthResolved = authState.isAuthResolved,
                        isSignedIn = true,
                        isLocalSettingsReady = settingsUiState.isInitialSettingsSnapshotLoaded,
                        syncStatus = cloudSyncStatus
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Forest950),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_launcher_foreground),
                            contentDescription = null,
                            modifier = Modifier.size(96.dp)
                        )
                    }
                } else {
                    Scaffold(
                        bottomBar = {
                            MainBottomBar(
                                currentTab = MainTab.entries[mainPagerState.currentPage],
                                isSessionActive = sessionState.isActive,
                                onTabSelected = { tab ->
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    coroutineScope.launch { mainPagerState.scrollToPage(tab.ordinal) }
                                }
                            )
                        },
                        containerColor = Forest950
                    ) { paddingValues ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(paddingValues)
                        ) {
                            MainContentPager(
                                context = this@OnboardingActivity,
                                pagerState = mainPagerState,
                                blockSubTab = blockSubTab,
                                limitsSubTab = limitsSubTab,
                                authState = authState,
                                homeViewModel = homeViewModel,
                                appBlockerViewModel = appBlockerViewModel,
                                mindfulAppsViewModel = mindfulAppsViewModel,
                                keywordBlockerViewModel = keywordBlockerViewModel,
                                timeLimitViewModel = timeLimitViewModel,
                                scheduleViewModel = scheduleViewModel,
                                settingsViewModel = settingsViewModel,
                                authViewModel = authViewModel,
                                onNavigateToFocus = { isDeepFocusOpen = true },
                                onNavigateToOemGuidance = { isOemGuidanceOpen = true },
                                onNavigateToTab = { tabIdx, subTabIdx ->
                                    when (tabIdx) {
                                        1 -> {
                                            blockSubTab = subTabIdx
                                            coroutineScope.launch { mainPagerState.scrollToPage(MainTab.BLOCK.ordinal) }
                                        }
                                        2 -> {
                                            limitsSubTab = subTabIdx
                                            coroutineScope.launch { mainPagerState.scrollToPage(MainTab.LIMITS.ordinal) }
                                        }
                                    }
                                },
                                onNavigateToSettings = { coroutineScope.launch { mainPagerState.scrollToPage(MainTab.SETTINGS.ordinal) } },
                                onRequireSignIn = requireSignIn
                            )
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

    override fun onResume() {
        super.onResume()
        if (::sessionBlockManager.isInitialized && sessionBlockManager.isSessionCurrentlyActive()) {
            SessionLockActivity.start(this)
            return
        }
        onboardingViewModel.refresh()
        homeViewModel.refreshPermissions()
        // Adult shield: starts the DNS tunnel right after the user approves the system VPN
        // consent dialog, and re-arms it if the OS or another VPN killed it.
        settingsViewModel.ensureAdultShieldRunning()
    }
}
