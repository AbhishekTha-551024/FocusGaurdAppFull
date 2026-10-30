package com.focusguard.app.feature.keywordblocker.domain.repository

import com.focusguard.app.core.common.result.AppResult
import com.focusguard.app.core.database.entity.ChallengeType
import com.focusguard.app.core.database.entity.PendingChangeEntity
import com.focusguard.app.feature.keywordblocker.domain.model.BlockedKeyword
import kotlinx.coroutines.flow.Flow

/**
 * =====================================================================
 * KeywordBlockerRepository.kt - Domain Repository Contract / Interface
 * =====================================================================
 *
 * ## 1. Yeh File Kya Karti Hai? (Core Responsibility)
 * Yeh interface Clean Architecture ke Domain Layer ka boundary contract hai.
 * Yeh define karta hai ki keyword blocking feature ko data operations ke liye
 * kaun-kaun se methods chahiye, bina kisi concrete database ya API implementation par depend kiye.
 *
 * ## 2. Core Contract Methods:
 * - `getBlockedKeywords()`: Database se real-time reactive `Flow` stream provide karta hai.
 * - `getFastBlockedKeywords()`: Synchronous cache list return karta hai taaki app/service instant block kar sake.
 * - `getPendingKeywordChanges()`: Accountability delays ke under pending deletion requests observe karta hai.
 * - `blockKeyword()`: New keyword ya website domain ko securely persist karta hai.
 * - `unblockKeyword()`: Specified keyword ko delete karta hai.
 * - `activateChallengeForKeyword()`: Challenge settings configure karke keyword block karta hai.
 * - `cancelPendingChange()`: User dwara initiate kiye hue unlock delay ko cancel karta hai.
 * - `sanitizeKeyword()`: Raw user string ko standard clean format me convert karta hai.
 *
 * ## 3. Why Interface in Domain? (Senior Dev Explanation):
 * Dependency Inversion Principle (DIP): High-level modules (UseCases/ViewModels) ko low-level
 * modules (Room SQLite / Retrofit / Firestore) par depend nahi karna chahiye; dono ko abstraction
 * (is interface) par depend karna chahiye. Isse unit testing mock objects ke through super easy ho jaati hai.
 * =====================================================================
 */
interface KeywordBlockerRepository {
    fun getBlockedKeywords(): Flow<List<BlockedKeyword>>
    fun getFastBlockedKeywords(): List<BlockedKeyword>
    fun getPendingKeywordChanges(): Flow<List<PendingChangeEntity>>
    suspend fun blockKeyword(rawKeyword: String): AppResult<Unit>
    suspend fun unblockKeyword(id: Long): AppResult<Unit>
    suspend fun activateChallengeForKeyword(
        keyword: String,
        type: ChallengeType,
        sentence: String
    ): AppResult<Unit>
    suspend fun cancelPendingChange(id: Long): AppResult<Unit>
    fun sanitizeKeyword(raw: String): String
}
