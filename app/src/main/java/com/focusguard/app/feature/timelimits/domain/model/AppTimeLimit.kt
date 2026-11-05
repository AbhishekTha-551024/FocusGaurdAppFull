package com.focusguard.app.feature.timelimits.domain.model

/**
 * =====================================================================
 * AppTimeLimit.kt - Domain Model / Entity
 * =====================================================================
 *
 * ## 1. Yeh File Kya Karti Hai? (Core Responsibility)
 * Yeh ek pure, immutable Domain Model data class hai jo kisi specific app ke daily time cap
 * configuration ko represent karti hai.
 *
 * ## 2. Fields Breakdown:
 * - `packageName`: Target app ka unique package identifier (e.g. "com.google.android.youtube").
 * - `appName`: Display label jo user ko dikhta hai (e.g. "YouTube").
 * - `dailyLimitMinutes`: Daily allowable screen time in minutes (e.g. 30, 60).
 * - `isEnabled`: Flag jo indicate karta hai ki time limit active hai ya user ne pause kiya hai.
 * - `dateConfigured`: Epoch timestamp (ms) jab yeh limit pehli baar create/update hui thi.
 *
 * ## 3. Architecture Role:
 * Clean Architecture ke Domain Layer me hone ki wajah se isme koi Android Framework ya Room
 * SQLite annotations nahi hain. Yeh 100% testable aur lightweight hai.
 * =====================================================================
 */
data class AppTimeLimit(
    val packageName: String,
    val appName: String,
    val dailyLimitMinutes: Int,
    val isEnabled: Boolean = true,
    val dateConfigured: Long = System.currentTimeMillis()
)
