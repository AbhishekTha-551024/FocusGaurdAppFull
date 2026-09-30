package com.focusguard.app.ui.session

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusguard.app.data.model.AppInfo
import com.focusguard.app.ui.components.AppIconView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

import com.focusguard.app.ui.theme.*

private val CardBg = Color(0xFF0D2117)
private val DialTrack = Color(0xFF163826)
private val TextMuted = White60
private val TextDim = White40

/**
 * DeepFocusScreen:
 * Compact, single-screen (zero scroll) interface for Deep Focus Lockdown.
 * Features:
 * 1. Compact 360° circular clock dial (1-minute precision).
 * 2. Tight dual steppers (Hours & Minutes).
 * 3. Compact 3-slot Suitcase whitelist row.
 * 4. Zero "Left" parachute complexity — pure, disciplined lockdown.
 */
@Composable
fun DeepFocusScreen(
    sessionState: SessionUiState,
    availableApps: List<AppInfo>,
    onSelectDuration: (Int) -> Unit,
    onOpenPacking: () -> Unit,
    onClosePacking: () -> Unit,
    onStartSession: () -> Unit,
    onRequestAbort: () -> Unit,
    onConfirmAbort: () -> Unit,
    onDismissAbort: () -> Unit,
    onTogglePackageInSuitcase: (String) -> Unit,
    onQuickPackUtilities: (List<AppInfo>) -> Unit,
    onSearchSuitcaseQueryChange: (String) -> Unit,
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val currentMins = sessionState.selectedDurationMinutes.coerceIn(1, 1440)

    var selectedHours by remember(currentMins) { mutableIntStateOf(currentMins / 60) }
    var selectedMinutes by remember(currentMins) { mutableIntStateOf(currentMins % 60) }

    fun updateDuration(h: Int, m: Int) {
        selectedHours = h
        selectedMinutes = m
        val total = (h * 60 + m).coerceIn(1, 1440)
        onSelectDuration(total)
    }

    val projectedEndTime = remember(currentMins) {
        val end = System.currentTimeMillis() + (currentMins * 60 * 1000L)
        SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(end))
    }

    val formattedDisplay = if (currentMins >= 60) {
        val h = currentMins / 60
        val m = currentMins % 60
        "${h}h ${if (m > 0) "${m}m" else ""}".trim()
    } else {
        "$currentMins min"
    }

    // Suitcase packing dialog
    if (sessionState.showPackingDialog) {
        SuitcasePackingDialog(
            availableApps = availableApps,
            packedPackages = sessionState.packedPackages,
            searchQuery = sessionState.searchQuery,
            onSearchQueryChange = onSearchSuitcaseQueryChange,
            onTogglePackage = onTogglePackageInSuitcase,
            onQuickPackUtilities = onQuickPackUtilities,
            onDismiss = onClosePacking
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Forest950)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        if (sessionState.isActive) {
            // Active Session View (Compact, fits in one screen)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0x1AFFFFFF))
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "Active Lockdown",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Active HUD Card
                ActiveSessionHudCard(
                    sessionState = sessionState,
                    availableApps = availableApps,
                    onOpenPacking = onOpenPacking,
                    onRequestAbort = onRequestAbort,
                    onConfirmAbort = onConfirmAbort,
                    onDismissAbort = onDismissAbort
                )

                Spacer(Modifier.height(8.dp))
            }
        } else {
            // Setup View (No scrolling needed — fits perfectly on single screen)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // ── 1. HEADER (BACK BUTTON + TITLE, ZERO "LEFT" CLUTTER) ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0x1AFFFFFF))
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(Modifier.width(10.dp))
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
                                text = "Deep Focus",
                                color = Color.White,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Lockdown Mode",
                                color = Emerald400,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    // Simple clean status pill
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0x1A22C55E),
                        border = BorderStroke(1.dp, Color(0x3322C55E))
                    ) {
                        Text(
                            text = "Unbreakable",
                            color = Emerald400,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                // ── 2. COMPACT 360° CLOCK DIAL CARD ──
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = CardBg,
                    border = BorderStroke(1.dp, Color(0x3322C55E)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "DIAL DURATION",
                                color = TextDim,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "Ends ~$projectedEndTime",
                                color = Emerald400,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Spacer(Modifier.height(6.dp))

                        // Compact 145dp Dial
                        Box(
                            modifier = Modifier
                                .size(145.dp)
                                .padding(2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            val sweepAngle = ((selectedMinutes.coerceIn(0, 59)) / 60f) * 360f

                            Canvas(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pointerInput(Unit) {
                                        detectTapGestures { offset ->
                                            val center = Offset(size.width / 2f, size.height / 2f)
                                            val angle = calculateAngle(offset, center)
                                            var targetMins = ((angle / 360f) * 60).roundToInt().coerceIn(0, 59)
                                            if (selectedHours == 0 && targetMins == 0) targetMins = 1
                                            updateDuration(selectedHours, targetMins)
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }
                                    }
                                    .pointerInput(Unit) {
                                        detectDragGestures { change, _ ->
                                            val center = Offset(size.width / 2f, size.height / 2f)
                                            val angle = calculateAngle(change.position, center)
                                            var targetMins = ((angle / 360f) * 60).roundToInt().coerceIn(0, 59)
                                            if (selectedHours == 0 && targetMins == 0) targetMins = 1
                                            if (targetMins != selectedMinutes) {
                                                updateDuration(selectedHours, targetMins)
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            }
                                        }
                                    }
                            ) {
                                val strokeWidth = 10.dp.toPx()
                                val radius = (size.minDimension - strokeWidth) / 2f
                                val center = Offset(size.width / 2f, size.height / 2f)

                                // Outer track
                                drawCircle(
                                    color = DialTrack,
                                    radius = radius,
                                    center = center,
                                    style = Stroke(width = strokeWidth)
                                )

                                // Active Glowing Arc
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

                                // Dial Thumb Indicator
                                val thumbAngleRad = (sweepAngle - 90f) * (PI / 180.0)
                                val thumbX = center.x + radius * cos(thumbAngleRad).toFloat()
                                val thumbY = center.y + radius * sin(thumbAngleRad).toFloat()

                                drawCircle(
                                    color = Color.White,
                                    radius = strokeWidth * 0.7f,
                                    center = Offset(thumbX, thumbY)
                                )
                                drawCircle(
                                    color = Emerald500,
                                    radius = strokeWidth * 0.38f,
                                    center = Offset(thumbX, thumbY)
                                )
                            }

                            // Center Digital Readout
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = String.format(Locale.US, "%02d", selectedHours),
                                        color = Color.White,
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Text(
                                        text = ":",
                                        color = Emerald400,
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 1.dp)
                                    )
                                    Text(
                                        text = String.format(Locale.US, "%02d", selectedMinutes),
                                        color = Color.White,
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }

                                Text(
                                    text = formattedDisplay,
                                    color = Emerald400,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        // Compact Dual Steppers Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Hours Stepper
                            CompactStepper(
                                label = "HOURS",
                                value = selectedHours,
                                onDecrement = {
                                    if (selectedHours > 0) {
                                        val newH = selectedHours - 1
                                        var newM = selectedMinutes
                                        if (newH == 0 && newM == 0) newM = 1
                                        updateDuration(newH, newM)
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                },
                                onIncrement = {
                                    if (selectedHours < 23) {
                                        updateDuration(selectedHours + 1, selectedMinutes)
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )

                            // Minutes Stepper (±1m)
                            CompactStepper(
                                label = "MIN (±1m)",
                                value = selectedMinutes,
                                onDecrement = {
                                    if (selectedMinutes > 0) {
                                        updateDuration(selectedHours, selectedMinutes - 1)
                                    } else if (selectedHours > 0) {
                                        updateDuration(selectedHours - 1, 59)
                                    }
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                },
                                onIncrement = {
                                    if (selectedMinutes < 59) {
                                        updateDuration(selectedHours, selectedMinutes + 1)
                                    } else if (selectedHours < 23) {
                                        updateDuration(selectedHours + 1, 0)
                                    }
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // ── 3. COMPACT SUITCASE CARD (MAX 3 APPS) ──
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = CardBg,
                    border = BorderStroke(1.dp, Color(0x3322C55E)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Luggage,
                                    contentDescription = null,
                                    tint = Emerald400,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "The Suitcase",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "(${sessionState.packedPackages.size}/3 Apps)",
                                    color = if (sessionState.packedPackages.size >= 3) Amber400 else TextDim,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Text(
                                text = "Edit >",
                                color = Emerald400,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onOpenPacking() }
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        Spacer(Modifier.height(8.dp))

                        // 3 Compact App Slots Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val packedList = sessionState.packedPackages.toList()
                            for (slotIndex in 0 until 3) {
                                val pkg = packedList.getOrNull(slotIndex)
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (pkg != null) Color(0x2222C55E) else Color(0x0CFFFFFF),
                                    border = BorderStroke(1.dp, if (pkg != null) Color(0x4422C55E) else Color(0x18FFFFFF)),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { onOpenPacking() }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        if (pkg != null) {
                                            AppIconView(packageName = pkg, size = 30.dp)
                                        } else {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Add,
                                                    contentDescription = "Add slot",
                                                    tint = TextDim,
                                                    modifier = Modifier.size(13.dp)
                                                )
                                                Spacer(Modifier.width(2.dp))
                                                Text(
                                                    text = "Slot ${slotIndex + 1}",
                                                    color = TextDim,
                                                    fontSize = 10.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // ── 4. PRIMARY LOCK BUTTON ──
                Button(
                    onClick = onStartSession,
                    colors = ButtonDefaults.buttonColors(containerColor = Emerald500),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Lock Phone & Focus ($formattedDisplay)",
                        color = Color.Black,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactStepper(
    label: String,
    value: Int,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0x14FFFFFF),
        border = BorderStroke(1.dp, Color(0x1AFFFFFF)),
        modifier = modifier.height(42.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = Color(0x22FFFFFF),
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .clickable { onDecrement() }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Remove, contentDescription = "Decrease", tint = Color.White, modifier = Modifier.size(14.dp))
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = String.format(Locale.US, "%02d", value),
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = label,
                    color = TextDim,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Surface(
                shape = CircleShape,
                color = Color(0x22FFFFFF),
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .clickable { onIncrement() }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Add, contentDescription = "Increase", tint = Color.White, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

@Composable
private fun ActiveSessionHudCard(
    sessionState: SessionUiState,
    availableApps: List<AppInfo>,
    onOpenPacking: () -> Unit,
    onRequestAbort: () -> Unit,
    onConfirmAbort: () -> Unit,
    onDismissAbort: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = CardBg,
        border = BorderStroke(1.dp, Color(0x4422C55E)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "SESSION IN PROGRESS",
                color = Emerald400,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )

            Spacer(Modifier.height(14.dp))

            // Stacked or readable digital countdown
            val remainingSecs = (sessionState.remainingMillis / 1000).coerceAtLeast(0L)
            val h = remainingSecs / 3600
            val m = (remainingSecs % 3600) / 60
            val s = remainingSecs % 60

            Text(
                text = String.format(Locale.US, "%02d:%02d:%02d", h, m, s),
                color = Color.White,
                fontSize = 40.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 3.sp
            )

            Spacer(Modifier.height(14.dp))

            Text(
                text = "${sessionState.packedPackages.size} Allowed Apps Active",
                color = TextMuted,
                fontSize = 12.sp
            )

            Spacer(Modifier.height(18.dp))

            // Strict Lockdown Status
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0x1A22C55E),
                border = BorderStroke(1.dp, Color(0x4422C55E)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = Emerald400,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = "Unbreakable Lockdown Active",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "Session strictly runs until the timer completes. No early exit.",
                            color = TextMuted,
                            fontSize = 11.5.sp
                        )
                    }
                }
            }
        }
    }
}

private fun calculateAngle(touch: Offset, center: Offset): Float {
    val deltaX = touch.x - center.x
    val deltaY = touch.y - center.y
    val rad = atan2(deltaY, deltaX)
    var deg = Math.toDegrees(rad.toDouble()).toFloat() + 90f
    if (deg < 0f) deg += 360f
    return deg
}
