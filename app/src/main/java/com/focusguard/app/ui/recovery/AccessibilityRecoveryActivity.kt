package com.focusguard.app.ui.recovery

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusguard.app.recovery.AccessibilityRecoveryHelper
import com.focusguard.app.ui.theme.*
import com.focusguard.app.utils.BatteryOptimizationHelper
import dagger.hilt.android.AndroidEntryPoint

/**
 * AccessibilityRecoveryActivity
 * =============================
 * Immediate, full-screen recovery interface displayed when device restart or OEM battery kill
 * disables the FocusGuard Accessibility Service while protection is active.
 *
 * Guarantees:
 * 1. Appears over lockscreen (showWhenLocked) and turns screen on.
 * 2. Provides 1-tap deep-link to restore the Accessibility toggle.
 * 3. Shows device-specific instructions (e.g. Vivo/iQOO) to permanently fix reboot disablement.
 * 4. Automatically dismisses itself as soon as Accessibility is restored.
 * 5. Consumes back button to prevent circumventing protection.
 */
@AndroidEntryPoint
class AccessibilityRecoveryActivity : ComponentActivity() {

    companion object {
        private const val EXTRA_REASON = "extra_reason"

        fun start(context: Context, reason: String = "") {
            try {
                val intent = Intent(context, AccessibilityRecoveryActivity::class.java).apply {
                    putExtra(EXTRA_REASON, reason)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    val bOptions = android.app.ActivityOptions.makeBasic().apply {
                        setPendingIntentBackgroundActivityStartMode(
                            android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                        )
                    }
                    val pendingIntent = android.app.PendingIntent.getActivity(
                        context,
                        0,
                        intent,
                        android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
                    )
                    try {
                        pendingIntent.send(context, 0, null, null, null, null, bOptions.toBundle())
                    } catch (_: Exception) {
                        context.startActivity(intent, bOptions.toBundle())
                    }
                } else {
                    context.startActivity(intent)
                }
            } catch (e: Exception) {
                android.util.Log.e("AccessibilityRecovery", "Failed to start AccessibilityRecoveryActivity", e)
            }
        }
    }

    @javax.inject.Inject
    lateinit var repository: com.focusguard.app.data.repository.FocusGuardRepository

    private var hasNavigatedToSettings = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!repository.isFastUninstallProtectionEnabled()) {
            finish()
            return
        }
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (AccessibilityRecoveryHelper.isAccessibilityActive(this@AccessibilityRecoveryActivity)) {
                    finish()
                } else {
                    Toast.makeText(
                        this@AccessibilityRecoveryActivity,
                        "⚠️ Accessibility must be turned ON to resume FocusGuard protection.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        })

        setContent {
            FocusGuardTheme {
                AccessibilityRecoveryScreen(
                    onRestoreClick = {
                        hasNavigatedToSettings = true
                        AccessibilityRecoveryHelper.openAccessibilitySettings(this@AccessibilityRecoveryActivity)
                    },
                    onOemFixClick = {
                        BatteryOptimizationHelper.openAutostartSettings(this@AccessibilityRecoveryActivity)
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!repository.isFastUninstallProtectionEnabled()) {
            finish()
            return
        }
        // If the user turned Accessibility ON, immediately finish and confirm
        if (AccessibilityRecoveryHelper.isAccessibilityActive(this)) {
            Toast.makeText(this, "🛡️ FocusGuard Protection Restored!", Toast.LENGTH_LONG).show()
            finish()
        }
    }
}

@Composable
private fun AccessibilityRecoveryScreen(
    onRestoreClick: () -> Unit,
    onOemFixClick: () -> Unit
) {
    val scrollState = rememberScrollState()
    val isVivoOrProblematic = remember { BatteryOptimizationHelper.isProblematicOem() }
    val oemName = remember { BatteryOptimizationHelper.getOemDisplayName() }

    // Pulsing animation for the warning glow
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Forest950,
                        Forest900,
                        Color(0xFF041009)
                    )
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            // Warning Icon with animated halo
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(100.dp)
                    .scale(pulseScale)
            ) {
                Box(
                    modifier = Modifier
                        .size(90.dp)
                        .clip(CircleShape)
                        .background(Rose400.copy(alpha = 0.15f))
                )
                Box(
                    modifier = Modifier
                        .size(70.dp)
                        .clip(CircleShape)
                        .background(Rose400.copy(alpha = 0.25f))
                        .border(2.dp, Rose400.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Protection Warning",
                        tint = Rose400,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Urgent Status Badge
            Surface(
                color = Rose400.copy(alpha = 0.15f),
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Rose400.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Rose400)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "PROTECTION PAUSED BY RESTART",
                        color = Rose400,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Title & Subtitle
            Text(
                text = "Turn On Accessibility",
                color = White90,
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Your phone turned off Accessibility during device restart. To protect your blocked apps and maintain your uninstall shield, Accessibility must be restored.",
                color = White60,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp)
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Primary 1-Tap CTA Button
            Button(
                onClick = onRestoreClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Emerald500,
                    contentColor = Forest950
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "RESTORE ACCESSIBILITY (1 TAP)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Step Instructions Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Forest800),
                border = androidx.compose.foundation.BorderStroke(1.dp, ForestBorder)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "Quick 3-Step Setup",
                        color = Emerald400,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    InstructionRow(number = "1", text = "Tap 'FocusGuard' in the Accessibility list")
                    Spacer(modifier = Modifier.height(8.dp))
                    InstructionRow(number = "2", text = "Turn ON the 'Use FocusGuard' toggle")
                    Spacer(modifier = Modifier.height(8.dp))
                    InstructionRow(number = "3", text = "Tap 'Allow' on the system confirmation prompt")
                }
            }

            // OEM Restart Fix Card (Vivo / iQOO / Xiaomi / Oppo)
            if (isVivoOrProblematic) {
                Spacer(modifier = Modifier.height(16.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Forest700.copy(alpha = 0.6f)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Amber400.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = Amber400,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Keep On After Restart ($oemName)",
                                color = Amber400,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "To prevent $oemName from disabling Accessibility whenever you restart or power off the phone, allow 'High background power consumption' & 'Autostart'.",
                            color = White60,
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedButton(
                            onClick = onOemFixClick,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Amber300),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Amber400.copy(alpha = 0.6f))
                        ) {
                            Text(
                                text = "Open $oemName Background Settings",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun InstructionRow(number: String, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(Emerald500.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number,
                color = Emerald400,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            color = White90,
            fontSize = 13.sp,
            lineHeight = 18.sp
        )
    }
}
