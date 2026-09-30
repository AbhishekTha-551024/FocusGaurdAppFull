package com.focusguard.app.ui.onboarding

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.focusguard.app.capability.Capability
import com.focusguard.app.ui.components.AccessibilityDisclosureDialog
import com.focusguard.app.ui.theme.*

enum class FocusGoal(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val primaryCapability: Capability
) {
    STOP_DISTRACTIONS(
        title = "Stop Distracting Apps",
        subtitle = "Instantly block addictive apps, reels, and games",
        icon = Icons.Default.Block,
        primaryCapability = Capability.ACCESSIBILITY
    ),
    REDUCE_SCREEN_TIME(
        title = "Reduce Screen Time",
        subtitle = "Measure daily usage and enforce app time limits",
        icon = Icons.Default.HourglassTop,
        primaryCapability = Capability.USAGE_ACCESS
    ),
    DEEP_FOCUS(
        title = "Deep Focus Sessions",
        subtitle = "Lock away distractions for deep study and work",
        icon = Icons.Default.LockClock,
        primaryCapability = Capability.ACCESSIBILITY
    ),
    COMPLETE_BALANCE(
        title = "Complete Digital Balance",
        subtitle = "Full protection, mindful habits, and self-discipline",
        icon = Icons.Default.Shield,
        primaryCapability = Capability.ACCESSIBILITY
    )
}

@Composable
fun OnboardingWizardScreen(
    onComplete: () -> Unit
) {
    val context = LocalContext.current
    var selectedGoal by remember { mutableStateOf(FocusGoal.STOP_DISTRACTIONS) }
    var step by remember { mutableIntStateOf(1) } // 1: Select Goal, 2: Contextual Setup
    var isAccessibilityEnabled by remember { mutableStateOf(false) }
    var isUsageAccessGranted by remember { mutableStateOf(false) }
    var showAccessibilityDisclosure by remember { mutableStateOf(false) }

    fun checkPermissions() {
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: ""
        isAccessibilityEnabled = enabledServices.contains("FocusGuardAccessibilityService") ||
                enabledServices.contains(context.packageName)

        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
        val mode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName
            )
        }
        isUsageAccessGranted = mode == android.app.AppOpsManager.MODE_ALLOWED
    }

    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        checkPermissions()
        onPauseOrDispose { }
    }

    if (showAccessibilityDisclosure) {
        AccessibilityDisclosureDialog(
            onAccept = {
                showAccessibilityDisclosure = false
                try {
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    })
                } catch (_: Exception) {}
            },
            onDismiss = {
                showAccessibilityDisclosure = false
            }
        )
    }

    Scaffold(
        containerColor = Forest950
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Step Progress Indicator
                    val animatedProgress by androidx.compose.animation.core.animateFloatAsState(
                        targetValue = if (step == 1) 0.5f else 1.0f,
                        label = "onboardingProgress"
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (step == 1) "STEP 1 OF 2: GOAL" else "STEP 2 OF 2: SETUP",
                                color = Emerald400,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "${(animatedProgress * 100).toInt()}%",
                                color = White60,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { animatedProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = Emerald500,
                            trackColor = Forest700
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Brand Logo & Badge
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(Color(0x2222C55E)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = Emerald400,
                            modifier = Modifier.size(30.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Surface(
                        color = Color(0x2222C55E),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, Color(0x3322C55E))
                    ) {
                        Text(
                            text = if (step == 1) "WELCOME TO FOCUSGUARD" else "CUSTOMIZED SETUP",
                            color = Emerald400,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Dynamic Headline
                    Text(
                        text = if (step == 1) "Take Control of Your Time" else "Set Up Your Goal",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = if (step == 1)
                            "What is your primary focus priority today?"
                        else
                            "FocusGuard needs a quick permission to power this feature for you.",
                        color = White60,
                        fontSize = 13.5.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 19.sp
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Step 1: Goal Selection
                    if (step == 1) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            FocusGoal.entries.forEach { goal ->
                                val isSelected = selectedGoal == goal
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .clickable { selectedGoal = goal },
                                    color = if (isSelected) Forest800 else Forest900,
                                    border = BorderStroke(
                                        1.2.dp,
                                        if (isSelected) Emerald500 else Forest700
                                    ),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(42.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    if (isSelected) Color(0x3322C55E) else Color(0x11FFFFFF)
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = goal.icon,
                                                contentDescription = null,
                                                tint = if (isSelected) Emerald400 else White60,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = goal.title,
                                                color = Color.White,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = goal.subtitle,
                                                color = White60,
                                                fontSize = 12.sp,
                                                lineHeight = 16.sp
                                            )
                                        }

                                        RadioButton(
                                            selected = isSelected,
                                            onClick = { selectedGoal = goal },
                                            colors = RadioButtonDefaults.colors(
                                                selectedColor = Emerald500,
                                                unselectedColor = White40
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        // Step 2: Contextual Setup
                        val targetCapability = selectedGoal.primaryCapability
                        val isGranted = when (targetCapability) {
                            Capability.ACCESSIBILITY -> isAccessibilityEnabled
                            Capability.USAGE_ACCESS -> isUsageAccessGranted
                            else -> false
                        }

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            color = Forest800,
                            border = BorderStroke(1.2.dp, if (isGranted) Emerald500 else Color(0x3322C55E))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(CircleShape)
                                        .background(if (isGranted) Color(0x3322C55E) else Color(0x1A22C55E)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isGranted) Icons.Outlined.CheckCircle else selectedGoal.icon,
                                        contentDescription = null,
                                        tint = Emerald400,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                Text(
                                    text = if (isGranted) "Protection Ready" else selectedGoal.title,
                                    color = Color.White,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                Text(
                                    text = if (isGranted)
                                        "Everything is configured. You are ready to start building healthy focus habits."
                                    else if (targetCapability == Capability.ACCESSIBILITY)
                                        "FocusGuard uses Accessibility Service to detect when a blocked app opens and trigger your focus screen."
                                    else
                                        "FocusGuard needs Usage Access to measure your daily screen time and enforce app caps.",
                                    color = White60,
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp,
                                    textAlign = TextAlign.Center
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                // Privacy Promise
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0x1A22C55E)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Lock,
                                            contentDescription = null,
                                            tint = Emerald400,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = "Your data stays on this phone. FocusGuard never accesses private messages or passwords.",
                                            color = White90,
                                            fontSize = 11.5.sp,
                                            lineHeight = 15.sp
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(18.dp))

                                if (!isGranted) {
                                    Button(
                                        onClick = {
                                            if (targetCapability == Capability.ACCESSIBILITY) {
                                                showAccessibilityDisclosure = true
                                            } else {
                                                try {
                                                    context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                                    })
                                                } catch (_: Exception) {}
                                            }
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(46.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Emerald500,
                                            contentColor = Forest950
                                        )
                                    ) {
                                        Text(
                                            text = if (targetCapability == Capability.ACCESSIBILITY)
                                                "Enable App Blocking"
                                            else
                                                "Enable Screen Time",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                    }
                                } else {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = Emerald400,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = "Connected & Working",
                                            color = Emerald400,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.5.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Bottom Action Buttons
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            if (step == 1) {
                                step = 2
                            } else {
                                onComplete()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Emerald500,
                            contentColor = Forest950
                        )
                    ) {
                        Text(
                            text = if (step == 1) "Continue" else "Get Started",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    TextButton(
                        onClick = onComplete,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp),
                        colors = ButtonDefaults.textButtonColors(contentColor = White60)
                    ) {
                        Text(
                            text = if (step == 1) "Explore App First" else "Skip for Now",
                            fontSize = 13.5.sp
                        )
                    }
                }
            }
        }
}
