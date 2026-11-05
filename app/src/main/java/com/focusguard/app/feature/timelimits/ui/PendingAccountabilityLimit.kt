package com.focusguard.app.feature.timelimits.ui

import com.focusguard.app.core.model.ChangeType
import com.focusguard.app.core.model.AppInfo

/**
 * =====================================================================
 * PendingAccountabilityLimit.kt - UI State Payload for Challenges
 * =====================================================================
 *
 * ## 1. Yeh File Kya Karti Hai? (Core Responsibility)
 * Yeh data class ek temporary in-flight state holder hai. Jab user pehli baar kisi app par
 * limit set karta hai aur accountability mode on nahi hota, ya jab user limit badhane
 * (loosen karne) ki koshish karta hai, tab yeh model hold karta hai ki user kis app
 * par kitne minutes set karne ki request kar raha tha.
 *
 * ## 2. Fields Breakdown:
 * - `app`: Target app jispar limit lagayi ja rahi hai.
 * - `actionType`: ChangeType (jaise `LOWER_LIMIT` ya `DISABLE_LIMIT`).
 * - `newMinutes`: User dwara chune gaye naye limit minutes.
 * - `currentMinutes`: Pehle se configured minutes (0 if first time).
 *
 * ## 3. Why Needed?
 * Challenge popup aane par user pehle typing complete karta hai. Challenge pass hone ke
 * baad ViewModel ko pata hona chahiye ki original action kya tha, jo is payload se recover hota hai.
 * =====================================================================
 */
data class PendingAccountabilityLimit(
    val app: AppInfo,
    val actionType: ChangeType,
    val newMinutes: Int? = null,
    val currentMinutes: Int = 0
)
