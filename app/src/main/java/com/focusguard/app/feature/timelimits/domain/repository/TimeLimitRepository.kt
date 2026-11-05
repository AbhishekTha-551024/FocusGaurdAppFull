package com.focusguard.app.feature.timelimits.domain.repository

import com.focusguard.app.core.common.result.AppResult
import com.focusguard.app.core.database.entity.AppTimeLimitEntity
import com.focusguard.app.core.model.ChallengeType
import com.focusguard.app.core.database.entity.PendingChangeEntity
import com.focusguard.app.core.model.AppInfo
import kotlinx.coroutines.flow.Flow

/**
 * =====================================================================
 * TimeLimitRepository.kt - Domain Repository Contract / Interface
 * =====================================================================
 *
 * ## 1. Yeh File Kya Karti Hai? (Core Responsibility)
 * Yeh interface Clean Architecture ke Domain Layer ka contract hai.
 * Yeh define karta hai ki Time Limit feature ko installed apps, usage metrics,
 * database updates, aur accountability friction challenge handle karne ke liye
 * kaun-kaun se methods chahiye.
 *
 * ## 2. Core Contract Methods:
 * - `getInstalledApps()`: Device ke apps ki reactive stream deta hai.
 * - `getAllTimeLimits()`: Database me configured time limits ki live stream deta hai.
 * - `getPendingChanges()`: Time-delayed modification/deletion requests observe karta hai.
 * - `hasUsagePermission()`: Check karta hai ki kya user ne Android "Usage Access" permission di hai.
 * - `getTodayUsageBatch()`: Sabhi apps ka aaj ka usage ek saath batch me return karta hai (O(1) lookup).
 * - `getTodayForegroundMinutes()`: Kisi single app ka exact live usage nikalta hai.
 * - `setTimeLimit()`: App par naya time limit set ya update karta hai.
 * - `removeTimeLimit()`: Time limit ko delete karta hai.
 * - `cancelPendingChange()`: Active time-delay unlock ko cancel karta hai.
 * - `activateChallengeForLimit()`: First-time limit par accountability lock lagata hai.
 *
 * ## 3. Architecture Benefit (Senior Dev Note):
 * Dependency Inversion Principle (DIP): High-level modules is interface par depend karte hain,
 * jisse tests me fake repository banana aur Room/Android system services ko mock karna easy ho jata hai.
 * =====================================================================
 */
interface TimeLimitRepository {
    fun getInstalledApps(): Flow<List<AppInfo>>
    fun getAllTimeLimits(): Flow<List<AppTimeLimitEntity>>
    fun getPendingChanges(): Flow<List<PendingChangeEntity>>
    fun hasUsagePermission(): Boolean
    fun getTodayUsageBatch(): Map<String, Int>
    fun getTodayForegroundMinutes(packageName: String): Int
    suspend fun setTimeLimit(
        packageName: String,
        appName: String,
        dailyLimitMinutes: Int,
        isEnabled: Boolean
    ): AppResult<Unit>
    suspend fun removeTimeLimit(packageName: String): AppResult<Unit>
    suspend fun cancelPendingChange(id: Long): AppResult<Unit>
    suspend fun activateChallengeForLimit(type: ChallengeType, sentence: String): AppResult<Unit>
}
