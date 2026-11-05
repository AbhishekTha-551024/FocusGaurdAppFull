package com.focusguard.app.feature.timelimits.ui

import com.focusguard.app.core.model.AppInfo

/**
 * =====================================================================
 * AppTimeLimitUiModel.kt - Presentation / UI View Model Item
 * =====================================================================
 *
 * ## 1. Yeh File Kya Karti Hai? (Core Responsibility)
 * Yeh UI Data Class Time Limits screen ke list rows ([com.focusguard.app.feature.timelimits.ui.components.CompactTimeLimitRow])
 * ko display karne ke liye design ki gayi hai. Yeh app ke metadata, configured limit, aur
 * live daily usage metrics ko ek single object me merge karti hai.
 *
 * ## 2. Properties Breakdown:
 * - `appInfo`: App ka package name, icon loader info, aur display title.
 * - `dailyLimitMinutes`: User dwara set kiya gaya daily limit cap (default 30 mins).
 * - `usedMinutesToday`: Aaj subah midnight se lekar ab tak use kiye gaye total minutes.
 * - `isLimitConfigured`: Kya is app par limit lag chuki hai (true) ya abhi unrestricted hai (false).
 * - `isEnabled`: Toggle state (active ya paused).
 *
 * ## 3. UI Computations (Senior Dev Note):
 * Is model se Compose UI directly progress percentage calculate karti hai:
 * `progress = (usedMinutesToday.toFloat() / dailyLimitMinutes.toFloat())`
 * Aur agar `usedMinutesToday >= dailyLimitMinutes` ho toh row ka color rose/red tint me badal jata hai.
 * =====================================================================
 */
data class AppTimeLimitUiModel(
    val appInfo: AppInfo,
    val dailyLimitMinutes: Int = 30,
    val usedMinutesToday: Int = 0,
    val isLimitConfigured: Boolean = false,
    val isEnabled: Boolean = true
)
