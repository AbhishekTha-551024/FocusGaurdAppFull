package com.focusguard.app.feature.keywordblocker.data.repository

import com.focusguard.app.core.common.result.AppError
import com.focusguard.app.core.common.result.AppResult
import com.focusguard.app.core.database.dao.BlockedKeywordDao
import com.focusguard.app.core.database.entity.BlockedKeywordEntity
import com.focusguard.app.core.database.entity.ChallengeType
import com.focusguard.app.core.database.entity.ChangeType
import com.focusguard.app.core.database.entity.PendingChangeEntity
import com.focusguard.app.core.di.IoDispatcher
import com.focusguard.app.data.repository.FocusGuardRepository
import com.focusguard.app.feature.keywordblocker.data.mapper.toDomain
import com.focusguard.app.feature.keywordblocker.domain.model.BlockedKeyword
import com.focusguard.app.feature.keywordblocker.domain.repository.KeywordBlockerRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * =====================================================================
 * KeywordBlockerRepositoryImpl.kt - Repository Implementation Layer
 * =====================================================================
 *
 * ## 1. Yeh File Kya Karti Hai? (Core Responsibility)
 * Yeh class [KeywordBlockerRepository] interface ki concrete implementation hai.
 * Iska main kaam blocked keywords aur websites se related saare database operations,
 * input sanitization, in-memory caching synchronization, aur accountability challenges ko
 * safely execute karna hai.
 *
 * ## 2. Core Features & Functions:
 * - `getBlockedKeywords()`: Room DB ke [BlockedKeywordDao] ko reactive `Flow` me observe karta
 *   hai aur list ko domain model me map karta hai. UI me automatic real-time updates aate hain.
 * - `getFastBlockedKeywords()`: App launch ke time ya Accessibility Service startup par bina
 *   Room flow ke instant in-memory blocked list return karta hai (< 1ms).
 * - `blockKeyword()`: User ke input string ko pehle sanitize karta hai (trim, lowercase, remove https/www/slashes),
 *   fir use database aur in-memory set me persist karta hai.
 * - `unblockKeyword()`: Agar challenge complete ho jaye toh keyword ko database se delete karta hai.
 * - `activateChallengeForKeyword()`: Pehli baar keyword block karte waqt user se commitment sentence ya
 *   partner challenge enable karwata hai, taaki user baad me aasaani se delete na kar sake (Friction Security).
 *
 * ## 3. Architecture & Thread Safety (Senior Dev Perspective):
 * - **Dispatcher Isolation:** Saare suspend functions `@IoDispatcher private val ioDispatcher: CoroutineDispatcher`
 *   par `withContext(ioDispatcher)` use karte hain, jisse Main Thread (UI thread) kabhi block ya freeze nahi hota.
 * - **Result Wrapper:** Saare operations [AppResult.Success] ya [AppResult.Failure] return karte hain
 *   jisse exceptions UI me crash nahi karte balke clean domain error messages bante hain.
 * =====================================================================
 */
@Singleton
class KeywordBlockerRepositoryImpl @Inject constructor(
    private val blockedKeywordDao: BlockedKeywordDao,
    private val focusGuardRepository: FocusGuardRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : KeywordBlockerRepository {

    override fun getBlockedKeywords(): Flow<List<BlockedKeyword>> =
        blockedKeywordDao.watchBlockedKeywords().map { list ->
            list.map { it.toDomain() }
        }

    override fun getFastBlockedKeywords(): List<BlockedKeyword> =
        focusGuardRepository.getFastBlockedKeywordsEntities().map { it.toDomain() }

    override fun getPendingKeywordChanges(): Flow<List<PendingChangeEntity>> =
        focusGuardRepository.pendingChanges.map { list ->
            list.filter { it.changeType == ChangeType.DELETE_KEYWORD }
        }

    override fun sanitizeKeyword(raw: String): String =
        focusGuardRepository.sanitizeKeyword(raw)

    override suspend fun blockKeyword(rawKeyword: String): AppResult<Unit> =
        withContext(ioDispatcher) {
            val sanitized = sanitizeKeyword(rawKeyword)
            if (sanitized.isBlank()) {
                return@withContext AppResult.Failure(
                    AppError.ValidationError("Keyword cannot be empty.")
                )
            }
            val success = focusGuardRepository.blockKeyword(sanitized)
            if (success) {
                AppResult.Success(Unit)
            } else {
                AppResult.Failure(
                    AppError.DatabaseError(
                        errorCode = "DB-002",
                        errorMessage = "'$sanitized' is already blocked."
                    )
                )
            }
        }

    override suspend fun unblockKeyword(id: Long): AppResult<Unit> =
        withContext(ioDispatcher) {
            try {
                focusGuardRepository.unblockKeyword(id)
                AppResult.Success(Unit)
            } catch (e: Exception) {
                AppResult.Failure(
                    AppError.DatabaseError(
                        errorCode = "DB-003",
                        errorMessage = e.localizedMessage ?: "Failed to unblock keyword",
                        throwable = e
                    )
                )
            }
        }

    override suspend fun activateChallengeForKeyword(
        keyword: String,
        type: ChallengeType,
        sentence: String
    ): AppResult<Unit> = withContext(ioDispatcher) {
        try {
            focusGuardRepository.setChallengeType(type)
            focusGuardRepository.setCommitmentSentence(sentence)
            focusGuardRepository.setChallengeActive(true)
            val success = focusGuardRepository.blockKeyword(keyword)
            if (success) {
                AppResult.Success(Unit)
            } else {
                AppResult.Failure(
                    AppError.DatabaseError(
                        errorCode = "DB-002",
                        errorMessage = "'$keyword' is already blocked."
                    )
                )
            }
        } catch (e: Exception) {
            AppResult.Failure(
                AppError.SecurityError(
                    errorCode = "SEC-004",
                    errorMessage = e.localizedMessage ?: "Failed to activate challenge"
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
                        errorCode = "DB-004",
                        errorMessage = e.localizedMessage ?: "Failed to cancel change",
                        throwable = e
                    )
                )
            }
        }
}
