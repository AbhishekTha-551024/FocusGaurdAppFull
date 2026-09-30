package com.focusguard.app.ui.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusguard.app.ui.components.AccessibilityDisclosureDialog
import com.focusguard.app.ui.theme.FocusGuardTheme

private val Indigo400 = Color(0xFF818CF8)
private val Purple400 = Color(0xFFA78BFA)
private val Amber400  = Color(0xFFFBBF24)
private val Success   = Color(0xFF34D399)
private val Navy800   = Color(0xFF111827)
private val Navy700   = Color(0xFF1C2A40)
private val Navy900   = Color(0xFF0B0F1A)
private val White90   = Color(0xE6FFFFFF)
private val White60   = Color(0x99FFFFFF)

@Composable
fun OnboardingScreen(
    isAccessibilityEnabled: Boolean,
    isAdminActive: Boolean,
    isUsageAccessGranted: Boolean,
    isProtectionActive: Boolean,
    onOpenAccessibility: () -> Unit,
    onOpenDeviceAdmin: () -> Unit,
    onOpenUsageAccess: () -> Unit,
    onNavigateToAppBlocker: () -> Unit,
    onNavigateToKeywordBlocker: () -> Unit,
    onNavigateToTimeLimits: () -> Unit,
    onNavigateToSchedules: () -> Unit,
    onNavigateToOemGuidance: () -> Unit
) {
    var showAccessibilityDisclosure by remember { mutableStateOf(false) }

    if (showAccessibilityDisclosure) {
        AccessibilityDisclosureDialog(
            onAccept = {
                showAccessibilityDisclosure = false
                onOpenAccessibility()
            },
            onDismiss = {
                showAccessibilityDisclosure = false
            }
        )
    }

    FocusGuardTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Navy900, Color(0xFF0D1526), Navy900)
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 28.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                // ── Header ────────────────────────────────────────────────────
                OnboardingHeader(isProtectionActive)

                // ── Protection Active Banner ──────────────────────────────────
                AnimatedVisibility(
                    visible = isProtectionActive,
                    enter = fadeIn() + expandVertically()
                ) {
                    ProtectionActiveBanner()
                }

                Text(
                    text = "Protection & Wellbeing Tools",
                    color = White90,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp)
                )

                // ── Feature Card 1: App Blocker ───────────────────────────────
                FeatureCard(
                    title = "App Blocker",
                    description = "Instantly block distracting apps with pinned list, search, and real-time 10ms friction overlay.",
                    icon = Icons.Default.Apps,
                    themeColor = Indigo400,
                    buttonLabel = "Manage Blocked Apps",
                    onClick = onNavigateToAppBlocker
                )

                // ── Feature Card 2: Daily Time Limits (P5) ────────────────────
                FeatureCard(
                    title = "Daily Usage Time Limits",
                    description = "Set daily time caps (15m, 30m, 60m). Reaching the cap locks the app until midnight.",
                    icon = Icons.Default.HourglassTop,
                    themeColor = Purple400,
                    buttonLabel = "Configure Time Limits",
                    onClick = onNavigateToTimeLimits
                )

                // ── Feature Card 3: Focus Schedules (P5) ──────────────────────
                FeatureCard(
                    title = "Focus Schedules",
                    description = "Automate focus windows (e.g. Mon–Fri, 9 AM – 5 PM) to block apps during study or deep work.",
                    icon = Icons.Default.Schedule,
                    themeColor = Amber400,
                    buttonLabel = "Manage Focus Schedules",
                    onClick = onNavigateToSchedules
                )

                // ── Feature Card 4: Keyword & Website Blocker ─────────────────
                FeatureCard(
                    title = "Keyword & Website Blocker",
                    description = "Block custom keywords and websites in Chrome, Edge, Firefox, Brave, and Samsung Internet.",
                    icon = Icons.Default.Language,
                    themeColor = Indigo400,
                    buttonLabel = "Manage Blocked Keywords",
                    onClick = onNavigateToKeywordBlocker
                )

                Text(
                    text = "Required Permissions Checklist",
                    color = White90,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp)
                )

                // ── Step 1: Accessibility ─────────────────────────────────────
                OnboardingStep(
                    stepNumber = 1,
                    title = "Enable Accessibility Service",
                    description = "FocusGuard needs Accessibility permission to detect blocked apps and show the delay shield.",
                    icon = Icons.Default.Accessibility,
                    isDone = isAccessibilityEnabled,
                    actionLabel = if (isAccessibilityEnabled) "Enabled ✓" else "Open Accessibility Settings",
                    actionEnabled = !isAccessibilityEnabled,
                    onAction = { showAccessibilityDisclosure = true }
                )

                // ── Step 2: Device Admin ──────────────────────────────────────
                OnboardingStep(
                    stepNumber = 2,
                    title = "Activate Device Administrator",
                    description = "Device Admin prevents easy removal from Settings. Required for tamper protection.",
                    icon = Icons.Default.AdminPanelSettings,
                    isDone = isAdminActive,
                    actionLabel = if (isAdminActive) "Active ✓" else "Activate Device Admin",
                    actionEnabled = !isAdminActive,
                    onAction = onOpenDeviceAdmin
                )

                // ── Step 3: Usage Access ──────────────────────────────────────
                OnboardingStep(
                    stepNumber = 3,
                    title = "Grant Usage Access (Optional)",
                    description = "Required to accurately track daily screen time from midnight for App Time Limits.",
                    icon = Icons.Default.QueryStats,
                    isDone = isUsageAccessGranted,
                    actionLabel = if (isUsageAccessGranted) "Granted ✓" else "Grant Usage Access",
                    actionEnabled = !isUsageAccessGranted,
                    onAction = onOpenUsageAccess
                )

                // ── Step 4: Autostart & Battery Optimization ──────────────────
                OnboardingStep(
                    stepNumber = 4,
                    title = "Background Protection & Autostart",
                    description = "Allow FocusGuard to run unrestricted so OEM battery savers never pause your protection.",
                    icon = Icons.Default.BatteryChargingFull,
                    isDone = false,
                    actionLabel = "Open Battery & Autostart Setup",
                    actionEnabled = true,
                    onAction = onNavigateToOemGuidance
                )

                // ── Disclosure footer ─────────────────────────────────────────
                DisclosureCard()

                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Sub-composables
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun FeatureCard(
    title: String,
    description: String,
    icon: ImageVector,
    themeColor: Color,
    buttonLabel: String,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = themeColor.copy(alpha = 0.12f)),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, themeColor.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(themeColor.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = themeColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = description,
                color = White60,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
            Button(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth().height(46.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = themeColor, contentColor = if (themeColor == Amber400) Navy900 else Color.White)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = buttonLabel,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
private fun OnboardingHeader(isActive: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(
                    if (isActive) Success.copy(alpha = 0.15f)
                    else Indigo400.copy(alpha = 0.12f)
                )
                .border(
                    1.dp,
                    if (isActive) Success.copy(alpha = 0.6f) else Indigo400.copy(alpha = 0.4f),
                    RoundedCornerShape(22.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Shield,
                contentDescription = "FocusGuard",
                tint = if (isActive) Success else Indigo400,
                modifier = Modifier.size(44.dp)
            )
        }

        Spacer(Modifier.height(14.dp))

        Text(
            text = "FocusGuard",
            color = Color.White,
            fontSize = 26.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.5.sp
        )

        Spacer(Modifier.height(4.dp))

        Text(
            text = "Digital Wellbeing & Tamper Protection",
            color = White60,
            fontSize = 13.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ProtectionActiveBanner() {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0x1F34D399)),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, Success.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.CheckCircle,
                contentDescription = null,
                tint = Success,
                modifier = Modifier.size(26.dp)
            )
            Column {
                Text(
                    text = "Protection Active",
                    color = Success,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "App blocking, focus schedules, time limits, and tamper protection are fully armed.",
                    color = White90,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

@Composable
private fun OnboardingStep(
    stepNumber: Int,
    title: String,
    description: String,
    icon: ImageVector,
    isDone: Boolean,
    actionLabel: String,
    actionEnabled: Boolean,
    onAction: () -> Unit
) {
    val cardBg = if (isDone) Navy700 else Navy800
    val borderColor = if (isDone) Success.copy(alpha = 0.4f) else Color(0x33818CF8)

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(if (isDone) Success.copy(alpha = 0.2f) else Indigo400.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (isDone) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Done",
                            tint = Success,
                            modifier = Modifier.size(18.dp)
                        )
                    } else {
                        Text(
                            text = "$stepNumber",
                            color = Indigo400,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                }

                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
            }

            Text(
                text = description,
                color = White60,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )

            Button(
                onClick = onAction,
                enabled = actionEnabled,
                modifier = Modifier.fillMaxWidth().height(44.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isDone) Color(0x3334D399) else Indigo400,
                    disabledContainerColor = Color(0x2234D399),
                    disabledContentColor = Success
                )
            ) {
                Icon(
                    imageVector = if (isDone) Icons.Default.Check else icon,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = actionLabel,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
private fun DisclosureCard() {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0x0FFFFFFF)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x1AFFFFFF)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = Amber400,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "About Protection & Heartbeat",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text(
                text = "FocusGuard uses Android Accessibility Service, Device Administrator, UsageStatsManager, and WorkManager background heartbeat to keep your digital wellbeing goals on track.",
                color = White60,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
        }
    }
}
