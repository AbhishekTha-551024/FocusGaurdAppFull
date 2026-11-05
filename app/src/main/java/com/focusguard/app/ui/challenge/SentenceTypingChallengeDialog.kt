package com.focusguard.app.ui.challenge

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay

private val Navy900     = Color(0xFF07140D)
private val Navy800     = Color(0xFF0E2419)
private val Navy700     = Color(0xFF163827)
private val Amber400    = Color(0xFFFBBF24)
private val Rose400     = Color(0xFFFB7185)
private val Success     = Color(0xFF22C55E)
private val White60     = Color(0x99FFFFFF)
private val White40     = Color(0x66FFFFFF)

private const val CHALLENGE_TOTAL_SECONDS = 180
private const val PENALTY_SECONDS         = 30
private const val MAX_ALLOWED_DELTA       = 1

@Composable
fun SentenceTypingChallengeDialog(
    actionDescription: String,
    targetSentence: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    var activeSentence   by remember {
        mutableStateOf(
            if (targetSentence.trim().length >= 500) {
                CommitmentChallengeGenerator.ensureMinLengthAndSymbols(targetSentence)
            } else {
                CommitmentChallengeGenerator.generate()
            }
        )
    }
    var timerStarted     by remember { mutableStateOf(false) }
    var secondsLeft      by remember { mutableIntStateOf(CHALLENGE_TOTAL_SECONDS) }
    var isPenaltyActive  by remember { mutableStateOf(false) }
    var penaltyLeft      by remember { mutableIntStateOf(0) }
    var typedText        by remember { mutableStateOf("") }
    var previousLength   by remember { mutableIntStateOf(0) }
    val isExactMatch     = typedText.trim() == activeSentence.trim()
    val haptic           = LocalHapticFeedback.current

    // Main countdown — only ticks after first keystroke
    LaunchedEffect(timerStarted, secondsLeft, isPenaltyActive) {
        if (timerStarted && !isPenaltyActive && secondsLeft > 0) {
            delay(1_000L)
            secondsLeft--
        } else if (timerStarted && secondsLeft == 0) {
            onCancel()
        }
    }

    // Penalty countdown
    LaunchedEffect(isPenaltyActive, penaltyLeft) {
        if (isPenaltyActive && penaltyLeft > 0) {
            delay(1_000L)
            penaltyLeft--
        } else if (isPenaltyActive && penaltyLeft == 0) {
            isPenaltyActive = false
        }
    }

    // ON_PAUSE: app-switch penalty
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                typedText = ""
                previousLength = 0
                timerStarted = false
                secondsLeft = CHALLENGE_TOTAL_SECONDS
                activeSentence = CommitmentChallengeGenerator.generate(exclude = activeSentence)
                isPenaltyActive = true
                penaltyLeft = PENALTY_SECONDS
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    // Entrance animation
    var isMounted by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { isMounted = true }
    val animScale by animateFloatAsState(
        targetValue = if (isMounted) 1f else 0.93f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "s"
    )
    val animAlpha by animateFloatAsState(
        targetValue = if (isMounted) 1f else 0f,
        animationSpec = tween(160), label = "a"
    )

    // Live coloured diff — ONE paragraph, no duplicate
    val annotatedDiff = remember(typedText, activeSentence) {
        SentenceDiffUtils.buildAnnotatedDiff(typedText, activeSentence)
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (secondsLeft <= 10 && timerStarted) 1.08f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .scale(animScale)
                .alpha(animAlpha)
                .clip(RoundedCornerShape(18.dp))
                .border(1.dp, Amber400.copy(alpha = 0.4f), RoundedCornerShape(18.dp)),
            color = Navy800
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 14.dp, vertical = 14.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {

                SentenceTypingHeader(
                    actionDescription = actionDescription,
                    secondsLeft = secondsLeft,
                    timerStarted = timerStarted,
                    isPenaltyActive = isPenaltyActive,
                    penaltyLeft = penaltyLeft,
                    scale = pulseScale
                )

                // ── Single paragraph: coloured live diff (amber = not typed yet,
                //    green = correct, red = wrong). No duplicate card needed. ──
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Navy900,
                    border = androidx.compose.foundation.BorderStroke(1.dp, Amber400.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = annotatedDiff,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        textAlign = TextAlign.Start,
                        modifier = Modifier.padding(12.dp)
                    )
                }

                // ── Progress bar ───────────────────────────────────────────────
                val progress = if (activeSentence.isEmpty()) 0f
                               else typedText.length.coerceAtMost(activeSentence.length).toFloat() / activeSentence.length
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)),
                    color = if (isExactMatch) Success else Amber400,
                    trackColor = Navy700
                )
                Text(
                    text = "${typedText.length} / ${activeSentence.length}",
                    color = White40, fontSize = 9.sp, textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth()
                )

                // ── Input field ────────────────────────────────────────────────
                OutlinedTextField(
                    value = typedText,
                    onValueChange = { newText ->
                        if (isPenaltyActive) return@OutlinedTextField
                        val sanitized = newText.replace("\n", "")
                        val delta = sanitized.length - previousLength
                        if (delta > MAX_ALLOWED_DELTA) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            return@OutlinedTextField
                        }
                        if (!timerStarted && delta == 1) timerStarted = true
                        if (delta == 1) {
                            val lastIdx = sanitized.length - 1
                            if (lastIdx < activeSentence.length && sanitized[lastIdx] != activeSentence[lastIdx]) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        }
                        typedText = sanitized
                        previousLength = sanitized.length
                    },
                    placeholder = { Text("Type here to start the timer…", color = White40, fontSize = 11.sp) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 140.dp),
                    shape = RoundedCornerShape(10.dp),
                    keyboardOptions = KeyboardOptions(
                        autoCorrectEnabled = false,
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    visualTransformation = VisualTransformation.None,
                    enabled = !isPenaltyActive,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Navy700,
                        unfocusedContainerColor = Navy700,
                        disabledContainerColor = Navy900,
                        focusedBorderColor = if (isExactMatch) Success else Amber400,
                        unfocusedBorderColor = Color(0x33FBBF24),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    maxLines = 10
                )

                // ── Success / tip label ────────────────────────────────────────
                if (isExactMatch) {
                    Text(
                        text = "✓ Commitment verified! Tap Confirm.",
                        color = Success, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                } else if (!timerStarted) {
                    Text(
                        text = "Timer starts when you type the first character.",
                        color = White40, fontSize = 10.sp, textAlign = TextAlign.Center
                    )
                }

                // ── Buttons ────────────────────────────────────────────────────
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.weight(1f).height(42.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Keep Blocked", color = White60, fontSize = 11.sp)
                    }
                    Button(
                        onClick = onConfirm,
                        enabled = isExactMatch && !isPenaltyActive,
                        modifier = Modifier.weight(1f).height(42.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isExactMatch) Success else Navy700,
                            contentColor = if (isExactMatch) Navy900 else White40,
                            disabledContainerColor = Navy700,
                            disabledContentColor = White40
                        )
                    ) {
                        Text("Confirm", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}
