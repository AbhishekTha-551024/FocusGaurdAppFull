package com.focusguard.app.ui.navigation

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusguard.app.core.model.ChallengeType
import com.focusguard.app.feature.appblocker.ui.AppBlockerViewModel
import com.focusguard.app.feature.auth.ui.AuthUiState
import com.focusguard.app.feature.auth.ui.AuthViewModel
import com.focusguard.app.feature.home.ui.HomeScreen
import com.focusguard.app.feature.home.ui.HomeViewModel
import com.focusguard.app.feature.keywordblocker.ui.KeywordBlockerViewModel
import com.focusguard.app.feature.mindful.ui.MindfulAppsViewModel
import com.focusguard.app.feature.schedules.ui.ScheduleViewModel
import com.focusguard.app.feature.settings.ui.SettingsScreen
import com.focusguard.app.feature.settings.ui.SettingsViewModel
import com.focusguard.app.feature.timelimits.ui.TimeLimitViewModel
import com.focusguard.app.ui.block.BlockParentScreen
import com.focusguard.app.ui.limits.LimitsParentScreen

@Composable
fun MainContentPager(
    context: Context,
    pagerState: PagerState,
    blockSubTab: Int,
    limitsSubTab: Int,
    authState: AuthUiState,
    homeViewModel: HomeViewModel,
    appBlockerViewModel: AppBlockerViewModel,
    mindfulAppsViewModel: MindfulAppsViewModel,
    keywordBlockerViewModel: KeywordBlockerViewModel,
    timeLimitViewModel: TimeLimitViewModel,
    scheduleViewModel: ScheduleViewModel,
    settingsViewModel: SettingsViewModel,
    authViewModel: AuthViewModel,
    onNavigateToFocus: () -> Unit,
    onNavigateToOemGuidance: () -> Unit,
    onNavigateToTab: (Int, Int) -> Unit,
    onNavigateToSettings: () -> Unit,
    onRequireSignIn: (String, () -> Unit) -> Unit
) {
    val homeState by homeViewModel.uiState.collectAsStateWithLifecycle()
    val settingsState by settingsViewModel.uiState.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
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
                        onSignInWithGoogle = { authViewModel.signInWithGoogle(context) },
                        onRequireSignIn = onRequireSignIn,
                        onSendPartnerInvite = { email -> settingsViewModel.sendPartnerInvite(email) },
                        onNavigateToFocus = onNavigateToFocus,
                        onNavigateToTab = onNavigateToTab,
                        onNavigateToSettings = onNavigateToSettings,
                        onRequestToggleUninstallProtection = { enable -> settingsViewModel.requestToggleUninstallProtection(enable) },
                        onConfirmEnableUninstallProtection = { days -> settingsViewModel.confirmEnableUninstallProtection(days) },
                        onDismissUninstallDurationDialog = { settingsViewModel.dismissUninstallDurationDialog() },
                        onDismissDeviceAdminRequiredDialog = { settingsViewModel.dismissDeviceAdminRequiredDialog() },
                        onDismissAccountabilityRequiredDialog = { settingsViewModel.dismissAccountabilityRequiredDialog() },
                        onDismissPermissionsRequiredDialog = { settingsViewModel.dismissPermissionsRequiredDialog() },
                        onProceedToSetupChallenge = { settingsViewModel.proceedToSetupChallenge() },
                        onCancelChallengeSetup = { settingsViewModel.cancelChallengeSetup() },
                        onConfirmEnablePendingFeed = { settingsViewModel.confirmEnablePendingFeed() },
                        onDismissPendingFeedWarning = { settingsViewModel.dismissPendingFeedWarning() },
                        onToggleShortsReelsBlock = { enable -> settingsViewModel.toggleShortsReelsBlock(enable) },
                        onToggleInstagramReelsBlock = { enable -> settingsViewModel.toggleInstagramReelsBlock(enable) },
                        onToggleFacebookReelsBlock = { enable -> settingsViewModel.toggleFacebookReelsBlock(enable) },
                        onToggleYouTubeShortsBlock = { enable -> settingsViewModel.toggleYouTubeShortsBlock(enable) },
                        onToggleIncognitoBlock = { enable -> settingsViewModel.toggleIncognitoBlock(enable) },
                        onToggleBlockUnsupportedBrowsers = { enable -> settingsViewModel.toggleBlockUnsupportedBrowsers(enable) },
                        onToggleAutoBlockNewApps = { enable -> settingsViewModel.toggleAutoBlockNewApps(enable) },
                        onPickWallpaper = { uri -> settingsViewModel.saveWallpaperFromUri(uri) },
                        onClearWallpaper = { settingsViewModel.clearWallpaper() },
                        onSetPreset = { id -> settingsViewModel.setPreset(id) },
                        onSetDimAlpha = { alpha -> settingsViewModel.setDimAlpha(alpha) },
                        onSetCustomBlockMessage = { msg -> settingsViewModel.setCustomBlockMessage(msg) },
                        onSetBlockCountdownSeconds = { sec -> settingsViewModel.setBlockCountdownSeconds(sec) },
                        onPreviewOverlay = { settingsViewModel.previewOverlay() },
                        onRequestRemoveChallenge = { settingsViewModel.requestRemoveChallenge() },
                        onRequestChangeChallenge = { type, sentence, delay ->
                            if (type == ChallengeType.PARTNER) {
                                onRequireSignIn("Accountability Challenge") {
                                    settingsViewModel.requestChangeChallenge(type, sentence, delay)
                                }
                            } else {
                                settingsViewModel.requestChangeChallenge(type, sentence, delay)
                            }
                        },
                        onActivateChallenge = { settingsViewModel.activateChallenge() },
                        onSetCommitmentSentence = { sentence -> settingsViewModel.setCommitmentSentence(sentence) },
                        onChallengeSuccess = { ctx -> settingsViewModel.onChallengeSuccess(ctx) },
                        onCancelChallenge = { settingsViewModel.onCancelChallenge() },
                        onRefreshPermissions = { homeViewModel.refreshPermissions() },
                        onNavigateToOemGuidance = onNavigateToOemGuidance,
                        onToggleAdultContentBlock = { enable -> settingsViewModel.toggleAdultContentBlock(enable) }
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
                        onToggleAppBlock = { app, isBlocked -> appBlockerViewModel.toggleAppBlock(app, isBlocked) },
                        onAppChallengeSuccess = { ctx -> appBlockerViewModel.onChallengeSuccess(ctx) },
                        onAppCancelChallenge = { appBlockerViewModel.onCancelChallenge() },
                        onAppCancelPendingChange = { id -> appBlockerViewModel.cancelPendingChange(id) },
                        onAppActivateChallenge = { type, sentence -> appBlockerViewModel.onActivateChallengeForApp(type, sentence) },
                        onAppDismissAccountability = { appBlockerViewModel.onDismissAccountabilityRequired() },
                        onMindfulSearchQueryChange = { mindfulAppsViewModel.onSearchQueryChange(it) },
                        onToggleMindfulApp = { app, enable -> mindfulAppsViewModel.toggleMindfulApp(app, enable) },
                        onConfirmAddMindfulApp = { app -> mindfulAppsViewModel.confirmAddMindfulApp(app) },
                        onDismissConfirmAddMindfulApp = { mindfulAppsViewModel.dismissAddMindfulApp() },
                        onMindfulChallengeSuccess = { ctx -> mindfulAppsViewModel.onChallengeSuccess(ctx) },
                        onMindfulCancelChallenge = { mindfulAppsViewModel.onCancelChallenge() },
                        onMindfulActivateChallenge = { type, sentence -> mindfulAppsViewModel.onActivateChallengeForApp(type, sentence) },
                        onMindfulDismissAccountability = { mindfulAppsViewModel.onDismissAccountabilityRequired() },
                        onKeywordInputChange = { keywordBlockerViewModel.onKeywordInputChange(it) },
                        onAddKeyword = { keywordBlockerViewModel.addKeyword() },
                        onRemoveKeyword = { keywordBlockerViewModel.removeKeyword(it) },
                        onKeywordSearchQueryChange = { keywordBlockerViewModel.onSearchQueryChange(it) },
                        onKeywordChallengeSuccess = { ctx -> keywordBlockerViewModel.onChallengeSuccess(ctx) },
                        onKeywordCancelChallenge = { keywordBlockerViewModel.onCancelChallenge() },
                        onKeywordActivateChallenge = { type, sentence -> keywordBlockerViewModel.onActivateChallengeForKeyword(type, sentence) },
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
                            timeLimitViewModel.toggleTimeLimit(app, currentLimit, isEnabled)
                        },
                        onSetTimeLimitMinutes = { app, minutes -> timeLimitViewModel.setTimeLimit(app, minutes) },
                        onRequestUsagePermission = {
                            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                                data = android.net.Uri.parse("package:${context.packageName}")
                            }
                            try {
                                context.startActivity(intent)
                            } catch (_: Exception) {
                                context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                            }
                        },
                        onTimeLimitRefresh = { timeLimitViewModel.refreshPermissions() },
                        onTimeLimitChallengeSuccess = { ctx -> timeLimitViewModel.onChallengeSuccess(ctx) },
                        onTimeLimitCancelChallenge = { timeLimitViewModel.onCancelChallenge() },
                        onTimeLimitCancelPendingChange = { id -> timeLimitViewModel.cancelPendingChange(id) },
                        onTimeLimitActivateChallenge = { type, sentence -> timeLimitViewModel.onActivateChallengeForLimit(type, sentence) },
                        onTimeLimitDismissAccountability = { timeLimitViewModel.onDismissAccountabilityRequired() },
                        onOpenAddScheduleDialog = { scheduleViewModel.openAddDialog() },
                        onCloseAddScheduleDialog = { scheduleViewModel.closeAddDialog() },
                        onAddSchedule = { title, pkgName, appName, daysMask, startMin, endMin ->
                            scheduleViewModel.addSchedule(title, pkgName, appName, daysMask, startMin, endMin)
                        },
                        onDeleteSchedule = { id, title -> scheduleViewModel.deleteSchedule(id, title) },
                        onToggleSchedule = { schedule, enabled -> scheduleViewModel.toggleSchedule(schedule, enabled) },
                        onScheduleChallengeSuccess = { ctx -> scheduleViewModel.onChallengeSuccess(ctx) },
                        onScheduleCancelChallenge = { scheduleViewModel.onCancelChallenge() },
                        onScheduleCancelPendingChange = { id -> scheduleViewModel.cancelPendingChange(id) },
                        onScheduleActivateChallenge = { type, sentence -> scheduleViewModel.onActivateChallengeForSchedule(type, sentence) },
                        onScheduleDismissAccountability = { scheduleViewModel.onDismissAccountabilityRequired() }
                    )
                }

                MainTab.SETTINGS -> {
                    SettingsScreen(
                        uiState = settingsState,
                        onRequestChangeChallenge = { type, sentence, hours ->
                            if (type == ChallengeType.PARTNER) {
                                onRequireSignIn("Accountability Challenge") {
                                    settingsViewModel.requestChangeChallenge(type, sentence, hours)
                                }
                            } else {
                                settingsViewModel.requestChangeChallenge(type, sentence, hours)
                            }
                        },
                        onRequestToggleUninstallProtection = { enable -> settingsViewModel.requestToggleUninstallProtection(enable) },
                        onChallengeSuccess = { ctx -> settingsViewModel.onChallengeSuccess(ctx) },
                        onCancelChallenge = { settingsViewModel.onCancelChallenge() },
                        onNavigateToOemGuidance = onNavigateToOemGuidance,
                        onRefreshPermissions = { settingsViewModel.refreshPermissions() },
                        onNavigateToHomeSetup = onNavigateToSettings,
                        onPickWallpaper = { uri -> settingsViewModel.saveWallpaperFromUri(uri) },
                        onClearWallpaper = { settingsViewModel.clearWallpaper() },
                        onSetPreset = { id -> settingsViewModel.setPreset(id) },
                        onSetDimAlpha = { alpha -> settingsViewModel.setDimAlpha(alpha) },
                        onPreviewOverlay = { settingsViewModel.previewOverlay() },
                        onConfirmEnableUninstallProtection = { days -> settingsViewModel.confirmEnableUninstallProtection(days) },
                        onDismissUninstallDurationDialog = { settingsViewModel.dismissUninstallDurationDialog() },
                        onDismissAccountabilityRequiredDialog = { settingsViewModel.dismissAccountabilityRequiredDialog() },
                        onDismissPermissionsRequiredDialog = { settingsViewModel.dismissPermissionsRequiredDialog() },
                        onProceedToSetupChallenge = { settingsViewModel.proceedToSetupChallenge() },
                        onCancelChallengeSetup = { settingsViewModel.cancelChallengeSetup() },
                        onDismissDeviceAdminRequiredDialog = { settingsViewModel.dismissDeviceAdminRequiredDialog() },
                        onConfirmEnablePendingFeed = { settingsViewModel.confirmEnablePendingFeed() },
                        onDismissPendingFeedWarning = { settingsViewModel.dismissPendingFeedWarning() },
                        onToggleShortsReelsBlock = { enable -> settingsViewModel.toggleShortsReelsBlock(enable) },
                        onToggleInstagramReelsBlock = { enable -> settingsViewModel.toggleInstagramReelsBlock(enable) },
                        onToggleFacebookReelsBlock = { enable -> settingsViewModel.toggleFacebookReelsBlock(enable) },
                        onToggleYouTubeShortsBlock = { enable -> settingsViewModel.toggleYouTubeShortsBlock(enable) },
                        onToggleIncognitoBlock = { enable -> settingsViewModel.toggleIncognitoBlock(enable) },
                        onToggleAutoBlockNewApps = { enable -> settingsViewModel.toggleAutoBlockNewApps(enable) },
                        onSetCustomBlockMessage = { msg -> settingsViewModel.setCustomBlockMessage(msg) },
                        onSetBlockCountdownSeconds = { sec -> settingsViewModel.setBlockCountdownSeconds(sec) },
                        onOpenSupportCodeDialog = { settingsViewModel.openSupportCodeDialog() },
                        onDismissSupportCodeDialog = { settingsViewModel.dismissSupportCodeDialog() },
                        onVerifySupportCode = { code -> settingsViewModel.verifySupportCode(code) },
                        onApplySupportAction = { verified -> settingsViewModel.applySupportAction(verified) },
                        authUser = authState.user,
                        isAuthLoading = authState.isLoading,
                        onSignInWithGoogle = { authViewModel.signInWithGoogle(context) },
                        onSignOut = { authViewModel.signOut(context) },
                        onSendPartnerInvite = { email -> settingsViewModel.sendPartnerInvite(email) },
                        onCancelPartnerInvite = { id -> settingsViewModel.cancelPartnerInvite(id) },
                        onAcceptPartnerInvite = { id -> settingsViewModel.acceptPartnerInvite(id) },
                        onDeclinePartnerInvite = { id -> settingsViewModel.declinePartnerInvite(id) },
                        onRequestRevokePartner = { id -> settingsViewModel.requestRevokePartner(id) },
                        onUninstallAppClick = { settingsViewModel.requestUninstallApp() },
                        onConfirmDirectUninstall = { settingsViewModel.confirmDirectUninstall(context) },
                        onDismissUninstallConfirmDialog = { settingsViewModel.dismissUninstallConfirmDialog() }
                    )

                    LaunchedEffect(authState.errorMessage) {
                        val err = authState.errorMessage
                        if (!err.isNullOrBlank()) {
                            android.widget.Toast.makeText(context, err, android.widget.Toast.LENGTH_LONG).show()
                            authViewModel.clearError()
                        }
                    }
                }
            }
        }
    }
}
