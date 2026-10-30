/**
 * =====================================================================
 * FILE: MindfulBreathingContent.kt
 * PACKAGE: com.focusguard.app.overlay
 * =====================================================================
 * 
 * 📌 OVERVIEW (YE FILE KYA KARTI HAI?):
 * Ye file Mindful Pause overlay ka "Master Container & State Coordinator" hai.
 * Jab BlockOverlayManager.showMindfulPauseOverlay() call hota hai, toh ye composable
 * root view banta hai. Iska kaam 2 phases ko seamlessly coordinate karna hai:
 *   1. MindfulPhase.BREATHING (BreathingPhaseView): 3-6 second ka somatic relaxation aur focus reset.
 *   2. MindfulPhase.INTENT_SELECTION (IntentSelectionView): Conscious purpose declaration aur time allowance.
 *
 * 🏗️ ARCHITECTURE & LIFECYCLE:
 * - Phase Transitions: AnimatedContent ke through smooth cross-fade animation (500ms in / 400ms out)
 *   ke saath breathing se intent selection par switch hota hai.
 * - Haptic Breathing Engine: Device hardware vibrator (VibratorManager on Android 12+ / Vibrator on older)
 *   ko trigger karke rhythmic gentle tactile feedback (EFFECT_TICK) deta hai jaise hi breath phases change hoti hain.
 * - Hardware Accelerated Pulse: rememberInfiniteTransition se scale (0.78f -> 1.22f) aur glowAlpha drive
 *   hota hai with FastOutSlowInEasing curve.
 *
 * ⚙️ KEY PARAMETERS & CALLBACKS:
 * 1. packageName & appName: Target app jise user open karne ki koshish kar raha hai.
 * 2. breathingDurationSeconds: Breathing timer length (default 6s).
 * 3. isReturning / minutesSinceLastSession / todayOpenCount: Smart awareness metrics jo user ko
 *    dikhate hain ki usne ye app kitni der pehle close ki thi aur aaj kitni baar open ki hai.
 * 4. onContinueWithAllowance: Jab valid intent + allowance select ho jaye, tab accessibility service
 *    temporary bypass window grant karti hai.
 * 5. onWalkAway: Jab user conscious choice karke app band kar deta hai ("Save Focus").
 * =====================================================================
 */

package com.focusguard.app.overlay.mindful

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusguard.app.security.IntentValidationEngine
import com.focusguard.app.security.IntentValidationResult
import kotlinx.coroutines.delay

internal val OLED_BG = Color(0xFF040D08)
internal val CARD_BG = Color(0xEE0B1F15)
internal val EMERALD_500 = Color(0xFF22C55E)
internal val EMERALD_400 = Color(0xFF4ADE80)
internal val MINT_GLOW = Color(0xFF10B981)
internal val AMBER_400 = Color(0xFFFBBF24)
internal val ROSE_400 = Color(0xFFFB7185)

enum class MindfulPhase {
    BREATHING,
    INTENT_SELECTION
}

@Composable
fun MindfulBreathingContent(
    packageName: String,
    appName: String,
    breathingDurationSeconds: Int = 6,
    isReturning: Boolean = false,
    minutesSinceLastSession: Int = 0,
    todayOpenCount: Int = 1,
    onContinueWithAllowance: (durationMinutes: Int, intentText: String, category: String) -> Unit,
    onWalkAway: () -> Unit
) {
    val context = LocalContext.current
    var currentPhase by remember { mutableStateOf(MindfulPhase.BREATHING) }
    var countdownSeconds by remember { mutableIntStateOf(breathingDurationSeconds) }

    var selectedPresetId by remember { mutableStateOf<String?>(if (isReturning) "REFLEX" else "WORK") }
    var customIntentText by remember { mutableStateOf("") }
    var selectedDurationMinutes by remember { mutableIntStateOf(5) }

    // Breathing phase text
    val breathInstruction = when {
        countdownSeconds > 3 -> "Inhale slowly..."
        countdownSeconds > 1 -> "Hold gently..."
        else -> "Exhale slowly..."
    }

    // Countdown & Haptic vibration loop
    LaunchedEffect(Unit) {
        triggerGentleHaptic(context)
        while (countdownSeconds > 1) {
            delay(1000L)
            countdownSeconds--
            if (countdownSeconds == 3) {
                triggerGentleHaptic(context)
            }
        }
        delay(1000L)
        countdownSeconds = 0
        currentPhase = MindfulPhase.INTENT_SELECTION
        triggerGentleHaptic(context)
    }

    // Infinite breathing scale animation
    val infiniteTransition = rememberInfiniteTransition(label = "breathScale")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.78f,
        targetValue = 1.22f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "circleScale"
    )

    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.70f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(OLED_BG),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = currentPhase,
            transitionSpec = {
                fadeIn(animationSpec = tween(500)) togetherWith
                        fadeOut(animationSpec = tween(400))
            },
            label = "phaseTransition"
        ) { phase ->
            when (phase) {
                MindfulPhase.BREATHING -> {
                    BreathingPhaseView(
                        appName = appName,
                        countdownSeconds = countdownSeconds,
                        breathInstruction = breathInstruction,
                        scale = scale,
                        glowAlpha = glowAlpha,
                        onSkipBreathing = {
                            currentPhase = MindfulPhase.INTENT_SELECTION
                        }
                    )
                }

                MindfulPhase.INTENT_SELECTION -> {
                    val presets = if (isReturning) IntentValidationEngine.REPEAT_PRESET_INTENTS else IntentValidationEngine.PRESET_INTENTS
                    IntentSelectionView(
                        appName = appName,
                        isReturning = isReturning,
                        minutesSinceLastSession = minutesSinceLastSession,
                        todayOpenCount = todayOpenCount,
                        presets = presets,
                        selectedPresetId = selectedPresetId,
                        customIntentText = customIntentText,
                        selectedDurationMinutes = selectedDurationMinutes,
                        onSelectPreset = { preset ->
                            selectedPresetId = preset.id
                            customIntentText = preset.defaultText
                        },
                        onSelectCustom = {
                            selectedPresetId = null
                            customIntentText = ""
                        },
                        onCustomTextChanged = { newText ->
                            customIntentText = newText
                            selectedPresetId = null
                        },
                        onSelectDuration = { selectedDurationMinutes = it },
                        onConfirm = {
                            val activePreset = presets.find { it.id == selectedPresetId }
                            val category = activePreset?.id ?: "CUSTOM"
                            val finalIntent = customIntentText.ifBlank { activePreset?.defaultText ?: "Conscious access" }
                            onContinueWithAllowance(selectedDurationMinutes, finalIntent, category)
                        },
                        onWalkAway = onWalkAway
                    )
                }
            }
        }
    }
}

private fun triggerGentleHaptic(context: Context) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
        } else {
            @Suppress("DEPRECATION")
            val v = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            @Suppress("DEPRECATION")
            v?.vibrate(30L)
        }
    } catch (e: Exception) {
        android.util.Log.d("MindfulBreathing", "Haptic feedback error: ${e.message}")
    }
}
