package com.focusguard.app.core.designsystem.theme

import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

private val FocusPageGradient = Brush.verticalGradient(
    colors = listOf(Forest950, Color(0xFF0F172A), Forest950)
)

fun Modifier.focusPageBackground(): Modifier = background(FocusPageGradient)
