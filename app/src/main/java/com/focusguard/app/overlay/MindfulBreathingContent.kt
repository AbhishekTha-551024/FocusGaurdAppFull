package com.focusguard.app.overlay

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

private val OLED_BG = Color(0xFF040D08)
private val CARD_BG = Color(0xEE0B1F15)
private val EMERALD_500 = Color(0xFF22C55E)
private val EMERALD_400 = Color(0xFF4ADE80)
private val MINT_GLOW = Color(0xFF10B981)
private val AMBER_400 = Color(0xFFFBBF24)
private val ROSE_400 = Color(0xFFFB7185)

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

@Composable
private fun BreathingPhaseView(
    appName: String,
    countdownSeconds: Int,
    breathInstruction: String,
    scale: Float,
    glowAlpha: Float,
    onSkipBreathing: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // App header pill
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color(0x2222C55E),
            border = BorderStroke(1.dp, Color(0x4422C55E))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🌿 Mindful Pause",
                    color = EMERALD_400,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "•  $appName",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Center Breathing Animation Ring
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(240.dp)
            ) {
                // Outer glowing aura
                Box(
                    modifier = Modifier
                        .size(220.dp)
                        .scale(scale * 1.15f)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    MINT_GLOW.copy(alpha = glowAlpha * 0.4f),
                                    Color.Transparent
                                )
                            ),
                            shape = CircleShape
                        )
                )

                // Mid glowing circle
                Box(
                    modifier = Modifier
                        .size(170.dp)
                        .scale(scale)
                        .background(
                            brush = Brush.linearGradient(
                                listOf(
                                    Color(0x3322C55E),
                                    Color(0x2210B981)
                                )
                            ),
                            shape = CircleShape
                        )
                        .border(
                            width = 2.dp,
                            color = EMERALD_400.copy(alpha = glowAlpha),
                            shape = CircleShape
                        )
                )

                // Inner core
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .background(
                            brush = Brush.linearGradient(
                                listOf(
                                    Color(0xFF0B291A),
                                    Color(0xFF071B11)
                                )
                            ),
                            shape = CircleShape
                        )
                        .border(1.dp, EMERALD_500.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$countdownSeconds",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            Spacer(Modifier.height(36.dp))

            Text(
                text = "Take a Deep Breath",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = breathInstruction,
                fontSize = 15.sp,
                color = EMERALD_400,
                fontWeight = FontWeight.Medium
            )
        }

        // Skip prompt (subtle)
        TextButton(
            onClick = onSkipBreathing,
            colors = ButtonDefaults.textButtonColors(contentColor = Color(0x66FFFFFF))
        ) {
            Text(
                text = "Skip to Intent",
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun IntentSelectionView(
    appName: String,
    isReturning: Boolean,
    minutesSinceLastSession: Int,
    todayOpenCount: Int,
    presets: List<com.focusguard.app.security.PresetIntent>,
    selectedPresetId: String?,
    customIntentText: String,
    selectedDurationMinutes: Int,
    onSelectPreset: (com.focusguard.app.security.PresetIntent) -> Unit,
    onSelectCustom: () -> Unit,
    onCustomTextChanged: (String) -> Unit,
    onSelectDuration: (Int) -> Unit,
    onConfirm: () -> Unit,
    onWalkAway: () -> Unit
) {
    val scrollState = rememberScrollState()

    // Validation state
    val isPresetActive = selectedPresetId != null
    val customValidation = remember(customIntentText) {
        IntentValidationEngine.validateIntent(customIntentText)
    }

    val isFormValid = isPresetActive || customValidation is IntentValidationResult.Valid

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 24.dp, vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header
        Surface(
            shape = CircleShape,
            color = Color(0x2222C55E),
            border = BorderStroke(1.dp, Color(0x4422C55E)),
            modifier = Modifier.size(52.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.SelfImprovement,
                    contentDescription = null,
                    tint = EMERALD_400,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = if (isReturning) "Why again this time?" else "Why do you need $appName?",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(4.dp))

        Text(
            text = if (isReturning) {
                if (minutesSinceLastSession > 0)
                    "You closed $appName ${minutesSinceLastSession}m ago · Open #$todayOpenCount today"
                else
                    "You just closed $appName · Open #$todayOpenCount today"
            } else {
                "Set an intentional purpose before opening."
            },
            fontSize = 13.sp,
            color = if (isReturning) EMERALD_400 else Color(0xAAFFFFFF),
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(24.dp))

        // Preset Chips
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            presets.forEach { preset ->
                val isSelected = selectedPresetId == preset.id
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) Color(0x2622C55E) else Color(0x11FFFFFF),
                    border = BorderStroke(
                        width = if (isSelected) 1.5.dp else 1.dp,
                        color = if (isSelected) EMERALD_500 else Color(0x22FFFFFF)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSelectPreset(preset) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = preset.label,
                            color = if (isSelected) Color.White else Color(0xDDFFFFFF),
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier.weight(1f)
                        )
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = EMERALD_400,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // Custom reason selector
            val isCustomActive = selectedPresetId == null
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isCustomActive) Color(0x2622C55E) else Color(0x11FFFFFF),
                border = BorderStroke(
                    width = if (isCustomActive) 1.5.dp else 1.dp,
                    color = if (isCustomActive) EMERALD_500 else Color(0x22FFFFFF)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onSelectCustom() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "✍️ Custom Purpose (Type below)",
                        color = if (isCustomActive) Color.White else Color(0xDDFFFFFF),
                        fontSize = 13.sp,
                        fontWeight = if (isCustomActive) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.weight(1f)
                    )
                    if (isCustomActive) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = EMERALD_400,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // Custom text box (shown if custom active)
        if (selectedPresetId == null) {
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = customIntentText,
                onValueChange = onCustomTextChanged,
                placeholder = {
                    Text("e.g. Reply to client regarding project deadline", color = Color(0x55FFFFFF), fontSize = 13.sp)
                },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = EMERALD_500,
                    unfocusedBorderColor = Color(0x33FFFFFF),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = EMERALD_400
                ),
                shape = RoundedCornerShape(12.dp),
                minLines = 2,
                maxLines = 3
            )

            // Validation Helper Text
            Spacer(Modifier.height(6.dp))
            val helperColor = when (customValidation) {
                is IntentValidationResult.Valid -> EMERALD_400
                is IntentValidationResult.TooShort -> AMBER_400
                is IntentValidationResult.TooFewWords -> AMBER_400
                is IntentValidationResult.GibberishDetected -> ROSE_400
            }

            val helperText = when (customValidation) {
                is IntentValidationResult.Valid -> "✓ Intent verified (Conscious purpose)"
                is IntentValidationResult.TooShort -> "At least ${customValidation.minRequired} characters required (${customValidation.currentChars}/${customValidation.minRequired})"
                is IntentValidationResult.TooFewWords -> "At least ${customValidation.minRequired} words required (${customValidation.currentWords}/${customValidation.minRequired})"
                is IntentValidationResult.GibberishDetected -> customValidation.message
            }

            Text(
                text = helperText,
                color = helperColor,
                fontSize = 11.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Start
            )
        }

        Spacer(Modifier.height(20.dp))

        // Time Allowance Selector
        Text(
            text = "Conscious Time Allowance:",
            fontSize = 12.sp,
            color = Color(0x88FFFFFF),
            fontWeight = FontWeight.Medium,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(1, 2, 5, 10, 15).forEach { mins ->
                val isSelected = selectedDurationMinutes == mins
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSelected) EMERALD_500 else Color(0x16FFFFFF),
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onSelectDuration(mins) }
                ) {
                    Box(
                        modifier = Modifier.padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${mins}m",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) Color(0xFF031A0F) else Color(0xCCFFFFFF)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(28.dp))

        // Action Buttons
        Button(
            onClick = onConfirm,
            enabled = isFormValid,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = EMERALD_500,
                disabledContainerColor = Color(0x22FFFFFF),
                contentColor = Color(0xFF031A0F),
                disabledContentColor = Color(0x44FFFFFF)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = "Continue with Intent (${selectedDurationMinutes}m)",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }

        Spacer(Modifier.height(10.dp))

        OutlinedButton(
            onClick = onWalkAway,
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = ROSE_400
            ),
            border = BorderStroke(1.dp, Color(0x33FB7185)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "🛑 Walk Away (Save Focus)",
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp
            )
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
    } catch (_: Exception) {}
}
