package com.focusguard.app.ui.block

import android.content.Context
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusguard.app.core.database.entity.BlockedKeywordEntity
import com.focusguard.app.core.database.entity.ChallengeType
import com.focusguard.app.core.model.AppInfo
import com.focusguard.app.feature.appblocker.ui.AppBlockerScreen
import com.focusguard.app.feature.appblocker.ui.AppBlockerUiState
import com.focusguard.app.feature.keywordblocker.domain.model.BlockedKeyword
import com.focusguard.app.feature.keywordblocker.ui.KeywordBlockerScreen
import com.focusguard.app.feature.keywordblocker.ui.KeywordBlockerUiState
import com.focusguard.app.feature.mindful.ui.MindfulAppsScreen
import com.focusguard.app.feature.mindful.ui.MindfulAppsUiState
import com.focusguard.app.ui.theme.FocusGuardTheme
import com.focusguard.app.utils.AppIconLoader
import androidx.compose.material.icons.filled.SelfImprovement
import kotlinx.coroutines.launch

private val Navy900   = Color(0xFF07140D)
private val Indigo400 = Color(0xFF22C55E)
private val White60   = Color(0x99FFFFFF)

@Composable
fun BlockParentScreen(
    initialSubTab: Int = 0,
    appBlockerState: AppBlockerUiState,
    mindfulAppsState: MindfulAppsUiState = MindfulAppsUiState(),
    keywordBlockerState: KeywordBlockerUiState,
    onAppSearchQueryChange: (String) -> Unit,
    onToggleAppBlock: (AppInfo, Boolean) -> Unit,
    onAppChallengeSuccess: (Context) -> Unit = {},
    onAppCancelChallenge: () -> Unit = {},
    onAppCancelPendingChange: (Long) -> Unit = {},
    onAppActivateChallenge: (ChallengeType, String) -> Unit = { _, _ -> },
    onAppDismissAccountability: () -> Unit = {},
    onMindfulSearchQueryChange: (String) -> Unit = {},
    onToggleMindfulApp: (AppInfo, Boolean) -> Unit = { _, _ -> },
    onConfirmAddMindfulApp: (AppInfo) -> Unit = {},
    onDismissConfirmAddMindfulApp: () -> Unit = {},
    onMindfulChallengeSuccess: (Context) -> Unit = {},
    onMindfulCancelChallenge: () -> Unit = {},
    onMindfulActivateChallenge: (ChallengeType, String) -> Unit = { _, _ -> },
    onMindfulDismissAccountability: () -> Unit = {},
    onUpdateMindfulAllowanceMinutes: (String, Int) -> Unit = { _, _ -> },
    onOpenMindfulJournal: () -> Unit = {},
    onDismissMindfulJournal: () -> Unit = {},
    onKeywordInputChange: (String) -> Unit,
    onAddKeyword: () -> Unit,
    onRemoveKeyword: (BlockedKeyword) -> Unit,
    onKeywordSearchQueryChange: (String) -> Unit,
    onKeywordChallengeSuccess: (Context) -> Unit = {},
    onKeywordCancelChallenge: () -> Unit = {},
    onKeywordCancelPendingChange: (Long) -> Unit = {},
    onKeywordActivateChallenge: (ChallengeType, String) -> Unit = { _, _ -> },
    onKeywordDismissAccountability: () -> Unit = {}
) {
    val pagerState = rememberPagerState(initialPage = initialSubTab.coerceIn(0, 2), pageCount = { 3 })
    LaunchedEffect(initialSubTab) {
        if (pagerState.currentPage != initialSubTab) {
            pagerState.scrollToPage(initialSubTab.coerceIn(0, 2))
        }
    }
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val iconLoader = remember { AppIconLoader(context) }

    FocusGuardTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Navy900, Color(0xFF0B1F15), Navy900)
                    )
                )
        ) {
            // Sub-tabs at top
            Surface(
                color = Navy900,
                modifier = Modifier.fillMaxWidth()
            ) {
                TabRow(
                    selectedTabIndex = pagerState.currentPage,
                    containerColor = Navy900,
                    contentColor = Indigo400,
                    indicator = { tabPositions ->
                        if (pagerState.currentPage < tabPositions.size) {
                            TabRowDefaults.SecondaryIndicator(
                                modifier = Modifier.tabIndicatorOffset(tabPositions[pagerState.currentPage]),
                                color = Indigo400,
                                height = 3.dp
                            )
                        }
                    },
                    divider = {
                        HorizontalDivider(color = Color(0x1AFFFFFF))
                    }
                ) {
                    Tab(
                        selected = pagerState.currentPage == 0,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(
                                    0,
                                    animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
                                )
                            }
                        },
                        text = {
                            Text(
                                text = "Hard Lock (${appBlockerState.blockedApps.size})",
                                fontWeight = if (pagerState.currentPage == 0) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 13.sp
                            )
                        },
                        icon = {
                            Icon(Icons.Default.Block, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        selectedContentColor = Indigo400,
                        unselectedContentColor = White60
                    )

                    Tab(
                        selected = pagerState.currentPage == 1,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(
                                    1,
                                    animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
                                )
                            }
                        },
                        text = {
                            Text(
                                text = "Mindful (${mindfulAppsState.mindfulApps.size})",
                                fontWeight = if (pagerState.currentPage == 1) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 13.sp
                            )
                        },
                        icon = {
                            Icon(Icons.Default.SelfImprovement, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        selectedContentColor = Indigo400,
                        unselectedContentColor = White60
                    )

                    Tab(
                        selected = pagerState.currentPage == 2,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(
                                    2,
                                    animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
                                )
                            }
                        },
                        text = {
                            Text(
                                text = "Websites (${keywordBlockerState.blockedKeywords.size})",
                                fontWeight = if (pagerState.currentPage == 2) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 13.sp
                            )
                        },
                        icon = {
                            Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        selectedContentColor = Indigo400,
                        unselectedContentColor = White60
                    )
                }
            }

            // Sub-tab content with swipe/slide HorizontalPager
            HorizontalPager(
                state = pagerState,
                key = { page -> "block_subtab_$page" },
                beyondViewportPageCount = 2,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                when (page) {
                    0 -> {
                        AppBlockerScreen(
                            uiState = appBlockerState,
                            iconLoader = iconLoader,
                            onSearchQueryChange = onAppSearchQueryChange,
                            onToggleBlock = onToggleAppBlock,
                            onChallengeSuccess = onAppChallengeSuccess,
                            onCancelChallenge = onAppCancelChallenge,
                            onCancelPendingChange = onAppCancelPendingChange,
                            onActivateChallenge = onAppActivateChallenge,
                            onDismissAccountability = onAppDismissAccountability,
                            onBack = null
                        )
                    }
                    1 -> {
                        MindfulAppsScreen(
                            uiState = mindfulAppsState,
                            iconLoader = iconLoader,
                            onSearchQueryChange = onMindfulSearchQueryChange,
                            onToggleMindfulApp = onToggleMindfulApp,
                            onConfirmAddMindfulApp = onConfirmAddMindfulApp,
                            onDismissConfirmAddMindfulApp = onDismissConfirmAddMindfulApp,
                            onChallengeSuccess = onMindfulChallengeSuccess,
                            onCancelChallenge = onMindfulCancelChallenge,
                            onActivateChallenge = onMindfulActivateChallenge,
                            onDismissAccountability = onMindfulDismissAccountability,
                            onUpdateAllowanceMinutes = onUpdateMindfulAllowanceMinutes,
                            onOpenJournal = onOpenMindfulJournal,
                            onDismissJournal = onDismissMindfulJournal
                        )
                    }
                    2 -> {
                        KeywordBlockerScreen(
                            uiState = keywordBlockerState,
                            onKeywordInputChange = onKeywordInputChange,
                            onAddKeyword = onAddKeyword,
                            onRemoveKeyword = onRemoveKeyword,
                            onSearchQueryChange = onKeywordSearchQueryChange,
                            onChallengeSuccess = onKeywordChallengeSuccess,
                            onCancelChallenge = onKeywordCancelChallenge,
                            onCancelPendingChange = onKeywordCancelPendingChange,
                            onActivateChallenge = onKeywordActivateChallenge,
                            onDismissAccountability = onKeywordDismissAccountability,
                            onBack = {}
                        )
                    }
                }
            }
        }
    }
}

