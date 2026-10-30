package com.focusguard.app.ui.limits

import android.content.Context
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusguard.app.core.database.entity.ChallengeType
import com.focusguard.app.core.database.entity.ScheduleEntity
import com.focusguard.app.core.model.AppInfo
import com.focusguard.app.feature.schedules.ui.ScheduleScreen
import com.focusguard.app.feature.schedules.ui.ScheduleUiState
import com.focusguard.app.ui.theme.FocusGuardTheme
import com.focusguard.app.feature.timelimits.ui.TimeLimitScreen
import com.focusguard.app.feature.timelimits.ui.TimeLimitUiState
import kotlinx.coroutines.launch

private val Navy900   = Color(0xFF07140D)
private val Purple400 = Color(0xFF22C55E)
private val Amber400  = Color(0xFFFBBF24)
private val White60   = Color(0x99FFFFFF)

@Composable
fun LimitsParentScreen(
    initialSubTab: Int = 0,
    timeLimitState: TimeLimitUiState,
    scheduleState: ScheduleUiState,
    onTimeLimitSearchChange: (String) -> Unit,
    onToggleTimeLimit: (AppInfo, Int, Boolean) -> Unit,
    onSetTimeLimitMinutes: (AppInfo, Int) -> Unit,
    onRequestUsagePermission: () -> Unit,
    onTimeLimitRefresh: () -> Unit = {},
    onTimeLimitChallengeSuccess: (Context) -> Unit = {},
    onTimeLimitCancelChallenge: () -> Unit = {},
    onTimeLimitCancelPendingChange: (Long) -> Unit = {},
    onTimeLimitActivateChallenge: (ChallengeType, String) -> Unit = { _, _ -> },
    onTimeLimitDismissAccountability: () -> Unit = {},
    onOpenAddScheduleDialog: () -> Unit,
    onCloseAddScheduleDialog: () -> Unit,
    onAddSchedule: (String, String?, String?, Int, Int, Int) -> Unit,
    onDeleteSchedule: (Long, String) -> Unit,
    onToggleSchedule: (ScheduleEntity, Boolean) -> Unit,
    onScheduleChallengeSuccess: (Context) -> Unit = {},
    onScheduleCancelChallenge: () -> Unit = {},
    onScheduleCancelPendingChange: (Long) -> Unit = {},
    onScheduleActivateChallenge: (ChallengeType, String) -> Unit = { _, _ -> },
    onScheduleDismissAccountability: () -> Unit = {}
) {
    val pagerState = rememberPagerState(initialPage = initialSubTab.coerceIn(0, 1), pageCount = { 2 })
    LaunchedEffect(initialSubTab) {
        if (pagerState.currentPage != initialSubTab) {
            pagerState.scrollToPage(initialSubTab.coerceIn(0, 1))
        }
    }
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

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
                    contentColor = Color.White,
                    indicator = { tabPositions ->
                        if (pagerState.currentPage < tabPositions.size) {
                            TabRowDefaults.SecondaryIndicator(
                                Modifier.tabIndicatorOffset(tabPositions[pagerState.currentPage]),
                                color = if (pagerState.currentPage == 0) Purple400 else Amber400,
                                height = 3.dp
                            )
                        }
                    },
                    divider = {
                        HorizontalDivider(color = Color(0x1FFFFFFF))
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
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(vertical = 12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.HourglassTop,
                                    contentDescription = null,
                                    tint = if (pagerState.currentPage == 0) Purple400 else White60,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Daily Limits",
                                    color = if (pagerState.currentPage == 0) Color.White else White60,
                                    fontWeight = if (pagerState.currentPage == 0) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 14.sp
                                )
                            }
                        }
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
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(vertical = 12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Schedule,
                                    contentDescription = null,
                                    tint = if (pagerState.currentPage == 1) Amber400 else White60,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Focus Schedules",
                                    color = if (pagerState.currentPage == 1) Color.White else White60,
                                    fontWeight = if (pagerState.currentPage == 1) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    )
                }
            }

            // Tab Content with swipe/slide HorizontalPager (pre-warmed with beyondViewportPageCount = 1)
            HorizontalPager(
                state = pagerState,
                key = { page -> "limits_subtab_$page" },
                beyondViewportPageCount = 1,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                when (page) {
                    0 -> {
                        TimeLimitScreen(
                            uiState = timeLimitState,
                            onSearchQueryChange = onTimeLimitSearchChange,
                            onToggleLimit = onToggleTimeLimit,
                            onSetTimeLimit = onSetTimeLimitMinutes,
                            onRequestUsagePermission = onRequestUsagePermission,
                            onRefreshPermissions = onTimeLimitRefresh,
                            onChallengeSuccess = onTimeLimitChallengeSuccess,
                            onCancelChallenge = onTimeLimitCancelChallenge,
                            onCancelPendingChange = onTimeLimitCancelPendingChange,
                            onActivateChallenge = onTimeLimitActivateChallenge,
                            onDismissAccountability = onTimeLimitDismissAccountability
                        )
                    }
                    1 -> {
                        ScheduleScreen(
                            uiState = scheduleState,
                            onOpenAddDialog = onOpenAddScheduleDialog,
                            onCloseAddDialog = onCloseAddScheduleDialog,
                            onAddSchedule = onAddSchedule,
                            onDeleteSchedule = onDeleteSchedule,
                            onToggleSchedule = onToggleSchedule,
                            onChallengeSuccess = onScheduleChallengeSuccess,
                            onCancelChallenge = onScheduleCancelChallenge,
                            onCancelPendingChange = onScheduleCancelPendingChange,
                            onActivateChallenge = onScheduleActivateChallenge,
                            onDismissAccountability = onScheduleDismissAccountability
                        )
                    }
                }
            }
        }
    }
}

