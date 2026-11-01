package com.focusguard.app.feature.keywordblocker.data.repository

import com.focusguard.app.core.common.result.AppError
import com.focusguard.app.core.common.result.AppResult
import com.focusguard.app.core.database.dao.BlockedKeywordDao
import com.focusguard.app.core.database.dao.PendingChangeDao
import com.focusguard.app.core.database.entity.BlockedKeywordEntity
import com.focusguard.app.core.database.entity.ChallengeType
import com.focusguard.app.core.database.entity.ChangeType
import com.focusguard.app.core.database.entity.PendingChangeEntity
import com.focusguard.app.core.datastore.FocusGuardPreferences
import com.focusguard.app.core.di.IoDispatcher
import com.focusguard.app.feature.keywordblocker.data.mapper.toDomain
import com.focusguard.app.feature.keywordblocker.domain.model.BlockedKeyword
import com.focusguard.app.feature.keywordblocker.domain.repository.KeywordBlockerRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KeywordBlockerRepositoryImpl @Inject constructor(
    private val blockedKeywordDao: BlockedKeywordDao,
    private val pendingChangeDao: PendingChangeDao,
    private val prefs: FocusGuardPreferences,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : KeywordBlockerRepository {

    // ── Single Owner of In-Memory Keyword Cache ──
    private val cachedBlockedKeywords = ConcurrentHashMap.newKeySet<String>()
    private val repoScope = CoroutineScope(SupervisorJob() + ioDispatcher)

    init {
        repoScope.launch {
            try {
                blockedKeywordDao.watchBlockedKeywords().collect { list ->
                    val cleanList = mutableListOf<String>()
                    for (item in list) {
                        val clean = sanitizeKeyword(item.keyword)
                        if (clean.isNotBlank() && clean != item.keyword) {
                            blockedKeywordDao.insert(item.copy(keyword = clean))
                        }
                        if (clean.isNotBlank()) cleanList.add(clean)
                    }
                    cachedBlockedKeywords.clear()
                    cachedBlockedKeywords.addAll(cleanList)
                    prefs.updateSyncCounts(keywords = cleanList.size)
                }
            } catch (_: Exception) {}
        }
    }

    // ── Raw & Entity Flows (used by FocusGuardRepository facade) ──
    val blockedKeywordsEntities: Flow<List<BlockedKeywordEntity>> = blockedKeywordDao.watchBlockedKeywords()
    val blockedKeywordsCount: Flow<Int> = blockedKeywordDao.count()
    val blockedKeywordsSet: Flow<Set<String>> = blockedKeywordDao.watchBlockedKeywords().map { list ->
        list.map { it.keyword.lowercase() }.toSet()
    }

    // ── Fast In-Memory Cache Reads (0ms for accessibility & UI) ──
    fun getFastBlockedKeywordsSet(): Set<String> = cachedBlockedKeywords

    fun getFastBlockedKeywordsEntities(): List<BlockedKeywordEntity> =
        cachedBlockedKeywords.map { BlockedKeywordEntity(keyword = it) }

    // ── Core Operations ──
    override fun sanitizeKeyword(raw: String): String {
        var clean = raw.trim().lowercase()
        clean = clean.removePrefix("https://").removePrefix("http://")
        clean = clean.removePrefix("www.")
        clean = clean.trim('/')
        clean = clean.split("/").firstOrNull() ?: clean
        clean = clean.split("?").firstOrNull() ?: clean
        return clean.trim()
    }

    suspend fun blockKeywordDirect(keyword: String): Boolean = withContext(ioDispatcher) {
        val sanitized = sanitizeKeyword(keyword)
        if (sanitized.isBlank()) return@withContext false
        if (blockedKeywordDao.getBlockedKeyword(sanitized) != null) {
            return@withContext false
        }
        cachedBlockedKeywords.add(sanitized)
        try {
            blockedKeywordDao.insert(BlockedKeywordEntity(keyword = sanitized))
            true
        } catch (_: Exception) {
            cachedBlockedKeywords.remove(sanitized)
            false
        }
    }

    suspend fun unblockKeywordDirect(id: Long) = withContext(ioDispatcher) {
        val kw = blockedKeywordDao.getById(id)
        if (kw != null) {
            cachedBlockedKeywords.remove(kw.keyword.lowercase())
        }
        blockedKeywordDao.deleteById(id)
    }

    suspend fun deleteByKeyword(keyword: String) = withContext(ioDispatcher) {
        cachedBlockedKeywords.remove(keyword.lowercase())
        blockedKeywordDao.deleteByKeyword(keyword)
    }

    suspend fun clearAllKeywords() = withContext(ioDispatcher) {
        val allKw = cachedBlockedKeywords.toList()
        allKw.forEach { blockedKeywordDao.deleteByKeyword(it) }
        cachedBlockedKeywords.clear()
    }

    // ── Domain Interface Implementation (KeywordBlockerRepository) ──
    override fun getBlockedKeywords(): Flow<List<BlockedKeyword>> =
        blockedKeywordsEntities.map { list -> list.map { it.toDomain() } }

    override fun getFastBlockedKeywords(): List<BlockedKeyword> =
        getFastBlockedKeywordsEntities().map { it.toDomain() }

    override fun getPendingKeywordChanges(): Flow<List<PendingChangeEntity>> =
        pendingChangeDao.watchPendingChanges().map { list ->
            list.filter { it.changeType == ChangeType.DELETE_KEYWORD }
        }

    override suspend fun blockKeyword(rawKeyword: String): AppResult<Unit> =
        withContext(ioDispatcher) {
            val sanitized = sanitizeKeyword(rawKeyword)
            if (sanitized.isBlank()) {
                return@withContext AppResult.Failure(
                    AppError.ValidationError("Keyword cannot be empty.")
                )
            }
            val success = blockKeywordDirect(sanitized)
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
                unblockKeywordDirect(id)
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
            prefs.setChallengeType(type)
            prefs.setCommitmentSentence(sentence)
            prefs.setChallengeActive(true)
            val success = blockKeywordDirect(keyword)
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
                pendingChangeDao.deleteById(id)
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
