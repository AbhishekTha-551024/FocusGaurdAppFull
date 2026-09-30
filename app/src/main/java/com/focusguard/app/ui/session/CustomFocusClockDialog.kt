package com.focusguard.app.ui.session

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.LockClock
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private val Forest900 = Color(0xFF07140D)
private val DialBg = Color(0xFF0D2117)
private val Emerald500 = Color(0xFF22C55E)
private val Emerald400 = Color(0xFF4ADE80)
private val DialTrack = Color(0xFF163826)
private val Amber400 = Color(0xFFFBBF24)
private val TextMuted = Color(0x99FFFFFF)

/**
 * CustomFocusClockDialog:
 * Allows user to set ANY custom duration starting from 1 minute up to 24 hours.
 * Includes interactive 360° radial clock dial (1-minute precision) and dual steppers.
 */
@Composable
fun CustomFocusClockDialog(
    initialMinutes: Int,
    onConfirmDuration: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val haptic = LocalHapticFeedback.current

    // Hours (0..23) and Minutes (0..59)
    val safeInitial = initialMinutes.coerceIn(1, 1440)
    var selectedHours by remember { mutableIntStateOf(safeInitial / 60) }
    var selectedMinutes by remember { mutableIntStateOf(safeInitial % 60) }

    val totalMinutes = (selectedHours * 60 + selectedMinutes).coerceIn(1, 1440)

    val projectedEndTime = remember(totalMinutes) {
        val end = System.currentTimeMillis() + (totalMinutes * 60 * 1000L)
        SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(end))
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = Forest900,
            border = BorderStroke(1.dp, Brush.linearGradient(listOf(Color(0x5522C55E), Color(0x22FBBF24)))),
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .wrapContentHeight()
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0x2222C55E),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.LockClock,
                                    contentDescription = null,
                                    tint = Emerald400,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Custom Focus Clock",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "From 1 min to 24 hrs",
                                color = Emerald400,
                                fontSize = 11.sp
                            )
                        }
                    }

                    // Projected End Time Badge
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0x18FFFFFF),
                        border = BorderStroke(1.dp, Color(0x22FFFFFF))
                    ) {
                        Text(
                            text = "Ends ~$projectedEndTime",
                            color = Color(0xEEFFFFFF),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))

                // ── INTERACTIVE 360° RADIAL CLOCK DIAL (1-MINUTE RESOLUTION) ──
                Box(
                    modifier = Modifier
                        .size(210.dp)
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Clock sweep: 0 to 59 minutes represented as 0 to 360 degrees
                    val sweepAngle = ((selectedMinutes.coerceIn(0, 59)) / 60f) * 360f

                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTapGestures { offset ->
                                    val center = Offset(size.width / 2f, size.height / 2f)
                                    val angle = calculateAngle(offset, center)
                                    var targetMins = ((angle / 360f) * 60).roundToInt().coerceIn(0, 59)
                                    if (selectedHours == 0 && targetMins == 0) {
                                        targetMins = 1
                                    }
                                    selectedMinutes = targetMins
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            }
                            .pointerInput(Unit) {
                                detectDragGestures { change, _ ->
                                    val center = Offset(size.width / 2f, size.height / 2f)
                                    val angle = calculateAngle(change.position, center)
                                    var targetMins = ((angle / 360f) * 60).roundToInt().coerceIn(0, 59)
                                    if (selectedHours == 0 && targetMins == 0) {
                                        targetMins = 1
                                    }
                                    if (targetMins != selectedMinutes) {
                                        selectedMinutes = targetMins
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                }
                            }
                    ) {
                        val strokeWidth = 14.dp.toPx()
                        val radius = (size.minDimension - strokeWidth) / 2f
                        val center = Offset(size.width / 2f, size.height / 2f)

                        // Outer track
                        drawCircle(
                            color = DialTrack,
                            radius = radius,
                            center = center,
                            style = Stroke(width = strokeWidth)
                        )

                        // Active Glowing Progress Arc
                        if (sweepAngle > 0f) {
                            drawArc(
                                brush = Brush.sweepGradient(
                                    listOf(Emerald400, Emerald500, Amber400, Emerald400),
                                    center = center
                                ),
                                startAngle = -90f,
                                sweepAngle = sweepAngle,
                                useCenter = false,
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                            )
                        }

                        // Dial Thumb Indicator at the tip
                        val thumbAngleRad = (sweepAngle - 90f) * (PI / 180.0)
                        val thumbX = center.x + radius * cos(thumbAngleRad).toFloat()
                        val thumbY = center.y + radius * sin(thumbAngleRad).toFloat()

                        drawCircle(
                            color = Color.White,
                            radius = strokeWidth * 0.72f,
                            center = Offset(thumbX, thumbY)
                        )
                        drawCircle(
                            color = Emerald500,
                            radius = strokeWidth * 0.42f,
                            center = Offset(thumbX, thumbY)
                        )
                    }

                    // Center Digital Display
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        val hoursFormatted = String.format(Locale.US, "%02d", selectedHours)
                        val minsFormatted = String.format(Locale.US, "%02d", selectedMinutes)

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = hoursFormatted,
                                color = Color.White,
                                fontSize = 34.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = ":",
                                color = Emerald400,
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 2.dp)
                            )
                            Text(
                                text = minsFormatted,
                                color = Color.White,
                                fontSize = 34.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        val summaryText = if (selectedHours > 0) {
                            "${selectedHours}h ${selectedMinutes}m"
                        } else {
                            "$selectedMinutes ${if (selectedMinutes == 1) "minute" else "minutes"}"
                        }

                        Text(
                            text = summaryText,
                            color = Emerald400,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                // ── DUAL STEPPER CONTROLLERS (HOURS & EXACT 1-MINUTE STEPS) ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Hours Stepper
                    StepperCard(
                        label = "HOURS",
                        value = selectedHours,
                        onDecrement = {
                            if (selectedHours > 0) {
                                selectedHours--
                                if (selectedHours == 0 && selectedMinutes == 0) {
                                    selectedMinutes = 1
                                }
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        },
                        onIncrement = {
                            if (selectedHours < 23) {
                                selectedHours++
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )

                    // Minutes Stepper (exact 1-minute steps)
                    StepperCard(
                        label = "MINUTES (±1m)",
                        value = selectedMinutes,
                        onDecrement = {
                            if (selectedMinutes > 0) {
                                selectedMinutes--
                            } else if (selectedHours > 0) {
                                selectedHours--
                                selectedMinutes = 59
                            }
                            if (selectedHours == 0 && selectedMinutes == 0) {
                                selectedMinutes = 1
                            }
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        },
                        onIncrement = {
                            if (selectedMinutes < 59) {
                                selectedMinutes++
                            } else if (selectedHours < 23) {
                                selectedHours++
                                selectedMinutes = 0
                            }
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(Modifier.height(20.dp))

                // ── ACTION BUTTONS ───────────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0x1EFFFFFF)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                    ) {
                        Text("Cancel", color = Color.White, fontSize = 13.sp)
                    }

                    Button(
                        onClick = {
                            val finalMinutes = (selectedHours * 60 + selectedMinutes).coerceIn(1, 1440)
                            onConfirmDuration(finalMinutes)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Emerald500),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1.5f)
                            .height(46.dp)
                    ) {
                        val durationSummary = if (selectedHours > 0) {
                            "${selectedHours}h ${if (selectedMinutes > 0) "${selectedMinutes}m" else ""}".trim()
                        } else {
                            "$selectedMinutes min"
                        }
                        Text(
                            text = "Set ($durationSummary)",
                            color = Color.Black,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StepperCard(
    label: String,
    value: Int,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = DialBg,
        border = BorderStroke(1.dp, Color(0x22FFFFFF)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                color = TextMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )

            Spacer(Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Decrement Button
                Surface(
                    shape = CircleShape,
                    color = Color(0x22FFFFFF),
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .clickable { onDecrement() }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Remove, contentDescription = "Decrease", tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }

                Text(
                    text = String.format(Locale.US, "%02d", value),
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )

                // Increment Button
                Surface(
                    shape = CircleShape,
                    color = Color(0x22FFFFFF),
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .clickable { onIncrement() }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Add, contentDescription = "Increase", tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

/**
 * Calculates angle between 0 and 360 degrees where 12 o'clock is 0 degrees.
 */
private fun calculateAngle(touch: Offset, center: Offset): Float {
    val deltaX = touch.x - center.x
    val deltaY = touch.y - center.y
    val rad = atan2(deltaY, deltaX)
    var deg = Math.toDegrees(rad.toDouble()).toFloat() + 90f
    if (deg < 0f) deg += 360f
    return deg
}
