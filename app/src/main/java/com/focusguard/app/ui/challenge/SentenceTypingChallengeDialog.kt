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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
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
private val TimerRed    = Color(0xFFEF4444)
private val TimerOrange = Color(0xFFF97316)

private const val CHALLENGE_TOTAL_SECONDS = 180
private const val PENALTY_SECONDS         = 30
private const val MAX_ALLOWED_DELTA       = 1

private val COMMITMENT_SENTENCE_POOL = listOf(
    "In today's competitive era, maintaining a healthy mind and body is not so easy; but if you make it a habit from a young age, then you stay fit and healthy throughout your life. We might not understand the value of good health at a very young age, but health should be an individual's first priority. It is only when an individual is healthy they can work dedicatedly along with extracurricular activities. It is the most valuable asset than food or money.",
    "I am choosing to break my focus promise. I accept full responsibility for this distraction and its long-term impact on my personal goals and the habits I am trying to build every single day.",
    "I acknowledge that overriding my own commitment weakens my discipline and self-trust. I consciously accept this cost and the long-term consequences it has on the future I am working towards.",
    "By unlocking this app I confirm I have thought deeply about this choice and accept that every consistent override gradually erodes my self-control and delays my progress toward meaningful goals.",
    "I recognise that this distraction pulls me away from my priorities. I take full ownership of this decision and its compounding effect on my focus, productivity, and the life I truly want to build.",
    "Unlocking now means choosing short-term pleasure over long-term growth. I accept this trade-off with full awareness and complete personal responsibility for what I am sacrificing in this moment."
)

private fun pickRandom(exclude: String? = null): String {
    val pool = if (exclude != null) COMMITMENT_SENTENCE_POOL.filter { it != exclude }
               else COMMITMENT_SENTENCE_POOL
    return pool.random()
}

@Composable
fun SentenceTypingChallengeDialog(
    actionDescription: String,
    targetSentence: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    var activeSentence   by remember { mutableStateOf(pickRandom()) }
    var timerStarted     by remember { mutableStateOf(false) }
    var secondsLeft      by remember { mutableIntStateOf(CHALLENGE_TOTAL_SECONDS) }
    var isPenaltyActive  by remember { mutableStateOf(false) }
    var penaltyLeft      by remember { mutableIntStateOf(0) }
    var typedText        by remember { mutableStateOf("") }
    var previousLength   by remember { mutableIntStateOf(0) }
    val isExactMatch     = typedText.trim() == activeSentence.trim()
    val haptic           = LocalHapticFeedback.current

    // Clear clipboard on mount
    val clipboardManager = LocalClipboardManager.current
    LaunchedEffect(Unit) {
        clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(""))
    }

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
                activeSentence = pickRandom(exclude = activeSentence)
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

    val timerColor = when {
        isPenaltyActive   -> TimerRed
        !timerStarted     -> White40
        secondsLeft <= 30 -> TimerRed
        secondsLeft <= 60 -> TimerOrange
        else              -> Amber400
    }

    // Live coloured diff — ONE paragraph, no duplicate
    val annotatedDiff = remember(typedText, activeSentence) {
        buildAnnotatedString {
            for (i in activeSentence.indices) {
                when {
                    i < typedText.length && typedText[i] == activeSentence[i] ->
                        withStyle(SpanStyle(color = Success, fontWeight = FontWeight.SemiBold)) { append(activeSentence[i]) }
                    i < typedText.length ->
                        withStyle(SpanStyle(color = Rose400, fontWeight = FontWeight.SemiBold)) { append(activeSentence[i]) }
                    else ->
                        withStyle(SpanStyle(color = Amber400)) { append(activeSentence[i]) }
                }
            }
            if (typedText.length > activeSentence.length) {
                val overflow = typedText.substring(activeSentence.length)
                if (overflow.trim().isNotEmpty()) {
                    withStyle(SpanStyle(color = Rose400, fontWeight = FontWeight.SemiBold)) { append(overflow) }
                }
            }
        }
    }

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

                // ── Header row: icon + title + timer chip ──────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Amber400.copy(alpha = 0.15f))
                                .border(1.dp, Amber400.copy(alpha = 0.4f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = Amber400, modifier = Modifier.size(18.dp))
                        }
                        Text(
                            text = "Commitment Challenge",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }

                    // Timer chip
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = timerColor.copy(alpha = 0.15f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, timerColor.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.Timer, contentDescription = null, tint = timerColor, modifier = Modifier.size(12.dp))
                            when {
                                isPenaltyActive -> Text("${penaltyLeft}s penalty", color = timerColor, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                !timerStarted   -> Text("3:00", color = timerColor, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                else -> {
                                    val m = secondsLeft / 60; val s = secondsLeft % 60
                                    Text("%d:%02d".format(m, s), color = timerColor, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }

                // ── Penalty banner ─────────────────────────────────────────────
                AnimatedVisibility(visible = isPenaltyActive, enter = fadeIn(), exit = fadeOut()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = TimerRed.copy(alpha = 0.10f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, TimerRed.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = TimerRed, modifier = Modifier.size(14.dp))
                            Text(
                                text = "App-switch detected! New paragraph assigned. Wait ${penaltyLeft}s",
                                color = TimerRed, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

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
                        val delta = newText.length - previousLength
                        if (delta > MAX_ALLOWED_DELTA) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            return@OutlinedTextField
                        }
                        if (!timerStarted && delta == 1) timerStarted = true
                        if (delta == 1) {
                            val lastIdx = newText.length - 1
                            if (lastIdx < activeSentence.length && newText[lastIdx] != activeSentence[lastIdx]) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        }
                        typedText = newText
                        previousLength = newText.length
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
