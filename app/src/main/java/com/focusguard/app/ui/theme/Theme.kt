package com.focusguard.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp
import com.focusguard.app.R

// ──────────────────────────────────────────────────────────────────────────────
// FocusGuard Clean Emerald & Deep Forest Palette
// Inspired by clean dark-green minimalism: Deep forest black + Vibrant Emerald Green
// ──────────────────────────────────────────────────────────────────────────────

val Forest950    = Color(0xFF07140D)  // Deepest background (rich forest black)
val Forest900    = Color(0xFF0B1F15)  // Mid gradient / container
val Forest800    = Color(0xFF0E2419)  // Card / surface background
val Forest700    = Color(0xFF163827)  // Elevated surface / dialogs
val Emerald500   = Color(0xFF22C55E)  // Primary brand interactive (Vibrant emerald green)
val Emerald400   = Color(0xFF4ADE80)  // Lighter emerald for badges / icons
val Emerald600   = Color(0xFF16A34A)  // Deep emerald for buttons / active states
val Amber400     = Color(0xFFFBBF24)  // Accent tag / warning gold
val Amber300     = Color(0xFFFCD34D)  // Lighter accent
val Rose400      = Color(0xFFFB7185)  // Danger / alerts
val Success      = Color(0xFF22C55E)  // Active green
val White90      = Color(0xE6FFFFFF)  // Primary text
val White60      = Color(0x99FFFFFF)  // Secondary text
val White40      = Color(0x66FFFFFF)  // Tertiary text
val ForestBorder = Color(0x2E22C55E)  // Subtle crisp emerald border

// Backward compatibility aliases
val Navy950   = Forest950
val Navy900   = Forest950
val Navy800   = Forest800
val Navy700   = Forest700
val Indigo400 = Emerald500
val Indigo300 = Emerald400

private val FocusGuardDarkColors = darkColorScheme(
    primary              = Emerald500,
    onPrimary            = Forest950,
    primaryContainer     = Forest700,
    onPrimaryContainer   = Emerald400,
    secondary            = Amber400,
    onSecondary          = Forest950,
    secondaryContainer   = Color(0xFF2A2000),
    onSecondaryContainer = Amber300,
    background           = Forest950,
    onBackground         = White90,
    surface              = Forest800,
    onSurface            = White90,
    surfaceVariant       = Forest700,
    onSurfaceVariant     = White60,
    error                = Rose400,
    onError              = Forest950,
)


private val fontProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs
)

private val interFontName = GoogleFont("Inter")

val InterFontFamily = FontFamily(
    Font(googleFont = interFontName, fontProvider = fontProvider, weight = FontWeight.Normal),
    Font(googleFont = interFontName, fontProvider = fontProvider, weight = FontWeight.Medium),
    Font(googleFont = interFontName, fontProvider = fontProvider, weight = FontWeight.SemiBold),
    Font(googleFont = interFontName, fontProvider = fontProvider, weight = FontWeight.Bold)
)

val FocusGuardTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.25).sp
    ),
    displayMedium = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 45.sp,
        lineHeight = 52.sp
    ),
    displaySmall = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 44.sp
    ),
    headlineLarge = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 36.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp
    ),
    titleLarge = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    titleMedium = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp
    ),
    titleSmall = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp
    ),
    bodySmall = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp
    ),
    labelLarge = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
)

@Composable
fun FocusGuardTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = FocusGuardDarkColors,
        typography = FocusGuardTypography,
        content = content
    )
}

// Expose semantic colour tokens for use in composables
val MaterialTheme.successColor: Color
    @Composable get() = Success
