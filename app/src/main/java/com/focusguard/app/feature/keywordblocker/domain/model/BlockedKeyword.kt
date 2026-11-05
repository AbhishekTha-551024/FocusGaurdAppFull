package com.focusguard.app.feature.keywordblocker.domain.model

/**
 * =====================================================================
 * BlockedKeyword.kt - Domain Entity / Model
 * =====================================================================
 *
 * ## 1. Yeh File Kya Karti Hai? (Core Responsibility)
 * Yeh ek pure immutable Domain Model hai jo single blocked keyword ya website domain
 * ko represent karta hai (jaise "reddit.com", "reels", "twitter.com").
 *
 * ## 2. Architecture Me Iska Role Kya Hai?
 * - **Pure Kotlin Model:** Isme koi Room (`@Entity`, `@ColumnInfo`) ya Android (`Parcelable`)
 *   dependency nahi hai. Yeh Clean Architecture ke core Domain layer ka part hai.
 * - **UI Consumption:** ViewModel is model ko Compose UI ([com.focusguard.app.feature.keywordblocker.ui.components.BlockedWebsiteRow])
 *   ko deliver karti hai display aur delete operations ke liye.
 *
 * ## 3. Fields Breakdown:
 * - `id`: Unique database row identifier (Long). Unblocking/deletion ke time iska use hota hai.
 * - `keyword`: Sanitize kiya hua keyword ya domain string (e.g. "instagram.com", "gambling").
 * - `createdAt`: Epoch timestamp (millis) jab user ne is keyword ko block kiya tha.
 * =====================================================================
 */
data class BlockedKeyword(
    val id: Long = 0,
    val keyword: String,
    val createdAt: Long = System.currentTimeMillis()
)
