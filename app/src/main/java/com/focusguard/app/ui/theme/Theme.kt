package com.focusguard.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Forest950 = com.focusguard.app.core.designsystem.theme.Forest950
val Forest900 = com.focusguard.app.core.designsystem.theme.Forest900
val Forest800 = com.focusguard.app.core.designsystem.theme.Forest800
val Forest700 = com.focusguard.app.core.designsystem.theme.Forest700
val Emerald500 = com.focusguard.app.core.designsystem.theme.Emerald500
val Emerald400 = com.focusguard.app.core.designsystem.theme.Emerald400
val Emerald600 = com.focusguard.app.core.designsystem.theme.Emerald600
val Amber400 = com.focusguard.app.core.designsystem.theme.Amber400
val Amber300 = com.focusguard.app.core.designsystem.theme.Amber300
val Rose400 = com.focusguard.app.core.designsystem.theme.Rose400
val Success = com.focusguard.app.core.designsystem.theme.Success
val White90 = com.focusguard.app.core.designsystem.theme.White90
val White60 = com.focusguard.app.core.designsystem.theme.White60
val White40 = com.focusguard.app.core.designsystem.theme.White40
val ForestBorder = com.focusguard.app.core.designsystem.theme.ForestBorder

val Navy950 = Forest950
val Navy900 = Forest950
val Navy800 = Forest800
val Navy700 = Forest700
val Indigo400 = Emerald500
val Indigo300 = Emerald400

val FocusGuardDarkColors get() = com.focusguard.app.core.designsystem.theme.FocusGuardDarkColors
val InterFontFamily get() = com.focusguard.app.core.designsystem.theme.InterFontFamily
val FocusGuardTypography get() = com.focusguard.app.core.designsystem.theme.FocusGuardTypography

@Composable
fun FocusGuardTheme(content: @Composable () -> Unit) {
    com.focusguard.app.core.designsystem.theme.FocusGuardTheme(content = content)
}

val MaterialTheme.successColor: Color
    @Composable get() = Success
