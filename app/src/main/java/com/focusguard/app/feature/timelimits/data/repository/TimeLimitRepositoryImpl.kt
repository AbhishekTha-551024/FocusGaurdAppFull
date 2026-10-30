package com.focusguard.app.feature.timelimits.data.repository

import com.focusguard.app.core.common.result.AppError
import com.focusguard.app.core.common.result.AppResult
import com.focusguard.app.core.database.entity.AppTimeLimitEntity
import com.focusguard.app.core.database.entity.ChallengeType
import com.focusguard.app.core.database.entity.PendingChangeEntity
import com.focusguard.app.core.di.IoDispatcher
import com.focusguard.app.core.model.AppInfo
import com.focusguard.app.data.repository.FocusGuardRepository
import com.focusguard.app.domain.usecase.GetInstalledAppsUseCase
import com.focusguard.app.feature.timelimits.domain.repository.TimeLimitRepository
import com.focusguard.app.usage.TimeLimitEvaluator
import com.focusguard.app.usage.UsageStatsReader
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * =====================================================================
 * TimeLimitRepositoryImpl.kt - Repository Implementation Layer
 * =====================================================================
 *
 * ## 1. Yeh File Kya Karti Hai? (Core Responsibility)
 * Yeh class [TimeLimitRepository] interface ki concrete implementation hai.
 * Iska main kaam installed apps ki screen time usage query karna, Room database me
 * daily time caps save karna, accountability challenges manage karna, aur
 * [TimeLimitEvaluator] ke through real-time usage check karna hai.
 *
 * ## 2. Core Operations & Data Sources:
 * - `getInstalledApps()`: Device ke installed apps ki list provide karta hai.
 * - `getAllTimeLimits()`: Room DB ke `app_time_limits` table se reactive `Flow` stream deta hai.
 * - `getTodayUsageBatch()`: Android `UsageStatsManager` se midnight se lekar ab tak ka exact foreground time nikalta hai.
 * - `getTodayForegroundMinutes(packageName)`: [TimeLimitEvaluator] se live in-memory session time aur historical stats combine karke deta hai.
 * - `setTimeLimit()`: Specific app ka daily limit (minutes me) set karta hai aur Room + in-memory cache sync karta hai.
 * - `removeTimeLimit()`: App ka daily limit remove karta hai (unrestricted browsing).
 * - `activateChallengeForLimit()`: First-time limit lagate waqt ya friction enable karte waqt commitment sentence save karta hai.
 *
 * ## 3. Performance & Safety (Senior Dev Perspective):
 * - **IO Dispatcher Concurrency:** Saare database aur heavy operations `@IoDispatcher` par
 *   `withContext(ioDispatcher)` me run hote hain taaki UI thread 120Hz smooth rahe.
 * - **Result Wrapper:** Saare suspend methods [AppResult.Success] ya [AppResult.Failure] return karte hain
 *   taaki exceptions silently swallow na hon aur UI ko clear status mile.
 * =====================================================================
 */
@Singleton
class TimeLimitRepositoryImpl @Inject constructor(
    private val focusGuardRepository: FocusGuardRepository,
    private val getInstalledAppsUseCase: GetInstalledAppsUseCase,
    private val usageStatsReader: UsageStatsReader,
    private val timeLimitEvaluator: TimeLimitEvaluator,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : TimeLimitRepository {

    override fun getInstalledApps(): Flow<List<AppInfo>> =
        getInstalledAppsUseCase.execute()

    override fun getAllTimeLimits(): Flow<List<AppTimeLimitEntity>> =
        focusGuardRepository.allTimeLimits

    override fun getPendingChanges(): Flow<List<PendingChangeEntity>> =
        focusGuardRepository.pendingChanges

    override fun hasUsagePermission(): Boolean =
        usageStatsReader.hasUsageAccessPermission()

    override fun getTodayUsageBatch(): Map<String, Int> =
        if (hasUsagePermission()) usageStatsReader.getTodayForegroundMinutesBatch() else emptyMap()

    override fun getTodayForegroundMinutes(packageName: String): Int =
        timeLimitEvaluator.getTodayForegroundMinutes(packageName)

    override suspend fun setTimeLimit(
        packageName: String,
        appName: String,
        dailyLimitMinutes: Int,
        isEnabled: Boolean
    ): AppResult<Unit> = withContext(ioDispatcher) {
        try {
            focusGuardRepository.setTimeLimit(packageName, appName, dailyLimitMinutes, isEnabled)
            AppResult.Success(Unit)
        } catch (e: Exception) {
            AppResult.Failure(
                AppError.DatabaseError(
                    errorCode = "DB-030",
                    errorMessage = e.localizedMessage ?: "Failed to set time limit for $appName",
                    throwable = e
                )
            )
        }
    }

    override suspend fun removeTimeLimit(packageName: String): AppResult<Unit> =
        withContext(ioDispatcher) {
            try {
                focusGuardRepository.removeTimeLimit(packageName)
                AppResult.Success(Unit)
            } catch (e: Exception) {
                AppResult.Failure(
                    AppError.DatabaseError(
                        errorCode = "DB-031",
                        errorMessage = e.localizedMessage ?: "Failed to remove time limit for $packageName",
                        throwable = e
                    )
                )
            }
        }

    override suspend fun cancelPendingChange(id: Long): AppResult<Unit> =
        withContext(ioDispatcher) {
            try {
                focusGuardRepository.cancelPendingChange(id)
                AppResult.Success(Unit)
            } catch (e: Exception) {
                AppResult.Failure(
                    AppError.DatabaseError(
                        errorCode = "DB-032",
                        errorMessage = e.localizedMessage ?: "Failed to cancel pending change $id",
                        throwable = e
                    )
                )
            }
        }

    override suspend fun activateChallengeForLimit(
        type: ChallengeType,
        sentence: String
    ): AppResult<Unit> = withContext(ioDispatcher) {
        try {
            focusGuardRepository.setChallengeType(type)
            focusGuardRepository.setCommitmentSentence(sentence)
            focusGuardRepository.setChallengeActive(true)
            AppResult.Success(Unit)
        } catch (e: Exception) {
            AppResult.Failure(
                AppError.SecurityError(
                    errorCode = "SEC-033",
                    errorMessage = e.localizedMessage ?: "Failed to activate challenge"
                )
            )
        }
    }
}
