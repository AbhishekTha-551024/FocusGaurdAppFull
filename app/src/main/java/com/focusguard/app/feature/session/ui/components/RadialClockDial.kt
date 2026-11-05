package com.focusguard.app.feature.session.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private val DialBg    = Color(0xFF0D2117)
private val Emerald500 = Color(0xFF22C55E)
private val Emerald400 = Color(0xFF4ADE80)
private val DialTrack = Color(0xFF163826)

@Composable
fun RadialClockDial(
    selectedMinutes: Int,
    onMinutesChange: (Int) -> Unit,
    haptic: HapticFeedback,
    modifier: Modifier = Modifier,
    sizeDp: Int = 180
) {
    Box(
        modifier = modifier
            .size(sizeDp.dp)
            .clip(CircleShape)
            .background(DialBg),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp)
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val angle = calculateAngle(offset, center)
                        val mins = (angle / 360f * 60f).roundToInt().coerceIn(0, 59)
                        if (mins != selectedMinutes) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onMinutesChange(mins)
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val angle = calculateAngle(change.position, center)
                        val mins = (angle / 360f * 60f).roundToInt().coerceIn(0, 59)
                        if (mins != selectedMinutes) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onMinutesChange(mins)
                        }
                    }
                }
        ) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.minDimension / 2f - 10.dp.toPx()

            drawCircle(
                color = DialTrack,
                radius = radius,
                center = center,
                style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
            )

            for (i in 0 until 12) {
                val tickAngle = (i * 30f - 90f) * (PI / 180f).toFloat()
                val innerR = radius - 10.dp.toPx()
                val outerR = radius - 4.dp.toPx()
                val start = Offset(center.x + innerR * cos(tickAngle), center.y + innerR * sin(tickAngle))
                val end = Offset(center.x + outerR * cos(tickAngle), center.y + outerR * sin(tickAngle))
                drawLine(
                    color = Color(0x554ADE80),
                    start = start,
                    end = end,
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            val sweepAngle = (selectedMinutes / 60f) * 360f
            if (sweepAngle > 0f) {
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(Emerald500, Emerald400, Color(0xFF86EFAC)),
                        center = center
                    ),
                    startAngle = -90f,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                )

                val thumbAngle = (sweepAngle - 90f) * (PI / 180f).toFloat()
                val thumbX = center.x + radius * cos(thumbAngle)
                val thumbY = center.y + radius * sin(thumbAngle)

                drawCircle(
                    color = Color.White,
                    radius = 8.dp.toPx(),
                    center = Offset(thumbX, thumbY)
                )
                drawCircle(
                    color = Emerald400,
                    radius = 4.dp.toPx(),
                    center = Offset(thumbX, thumbY)
                )
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$selectedMinutes",
                color = Color.White,
                fontSize = if (sizeDp > 160) 32.sp else 26.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "MINUTES",
                color = Emerald400,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }
    }
}

private fun calculateAngle(point: Offset, center: Offset): Float {
    val dx = point.x - center.x
    val dy = point.y - center.y
    var degrees = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat() + 90f
    if (degrees < 0f) degrees += 360f
    return degrees
}
