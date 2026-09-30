package com.focusguard.app.ui.keywordblocker

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusguard.app.data.db.BlockedKeywordEntity
import com.focusguard.app.data.db.ChallengeType
import com.focusguard.app.data.db.ChangeType
import com.focusguard.app.data.db.PendingChangeEntity
import com.focusguard.app.data.repository.FocusGuardRepository
import com.focusguard.app.ui.blocker.ActiveChallengeState
import com.focusguard.app.worker.PendingChangeApplyWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class KeywordBlockerUiState(
    val isLoading: Boolean = false,
    val keywordInput: String = "",
    val searchQuery: String = "",
    val errorMessage: String? = null,
    val blockedKeywords: List<BlockedKeywordEntity> = emptyList(),
    val pendingChanges: List<PendingChangeEntity> = emptyList(),
    val activeChallenge: ActiveChallengeState? = null,
    val isChallengeActive: Boolean = false,
    val challengeType: ChallengeType = ChallengeType.TYPING,
    val commitmentSentence: String = "",
    val timeDelayHours: Int = 24,
    val pendingAccountabilityKeyword: BlockedKeywordEntity? = null
)

@HiltViewModel
class KeywordBlockerViewModel @Inject constructor(
    private val repository: FocusGuardRepository
) : ViewModel() {

    private val _keywordInput = MutableStateFlow("")
    private val _searchQuery = MutableStateFlow("")
    private val _errorMessage = MutableStateFlow<String?>(null)
    private val _activeChallenge = MutableStateFlow<ActiveChallengeState?>(null)
    private val _pendingAccountabilityKeyword = MutableStateFlow<BlockedKeywordEntity?>(null)

    private data class LocalInputState(
        val input: String,
        val search: String,
        val error: String?,
        val challenge: ActiveChallengeState?,
        val pendingAccountability: BlockedKeywordEntity?
    )

    private val localInputState = combine(
        _keywordInput,
        _searchQuery,
        _errorMessage,
        _activeChallenge,
        _pendingAccountabilityKeyword
    ) { input, search, error, challenge, pendingKw ->
        LocalInputState(input, search, error, challenge, pendingKw)
    }

    val uiState: StateFlow<KeywordBlockerUiState> = combine(
        repository.blockedKeywords,
        localInputState,
        repository.pendingChanges,
        repository.challengeSettings
    ) { keywords, local, pendingChanges, settings ->
        val filtered = if (local.search.isBlank()) {
            keywords
        } else {
            keywords.filter { it.keyword.contains(local.search, ignoreCase = true) }
        }

        val kwPending = pendingChanges.filter { it.changeType == ChangeType.DELETE_KEYWORD }

        KeywordBlockerUiState(
            isLoading = false,
            keywordInput = local.input,
            searchQuery = local.search,
            errorMessage = local.error,
            blockedKeywords = filtered,
            pendingChanges = kwPending,
            activeChallenge = local.challenge,
            isChallengeActive = settings.isChallengeActive,
            challengeType = settings.type,
            commitmentSentence = settings.commitmentSentence,
            timeDelayHours = settings.timeDelayHours,
            pendingAccountabilityKeyword = local.pendingAccountability
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        KeywordBlockerUiState(
            isLoading = false,
            blockedKeywords = repository.getFastBlockedKeywordsEntities(),
            isChallengeActive = repository.getCurrentSettingsSnapshot().isChallengeActive,
            challengeType = repository.getCurrentSettingsSnapshot().challengeType,
            commitmentSentence = repository.getCurrentSettingsSnapshot().commitmentSentence,
            timeDelayHours = repository.getCurrentSettingsSnapshot().timeDelayHours
        )
    )

    fun onKeywordInputChange(input: String) {
        _keywordInput.value = input
        if (_errorMessage.value != null) _errorMessage.value = null
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun addKeyword() {
        val sanitized = repository.sanitizeKeyword(_keywordInput.value)
        if (sanitized.isEmpty()) {
            _errorMessage.value = "Please enter a keyword or website domain."
            return
        }

        if (sanitized.length < 2) {
            _errorMessage.value = "Keyword must be at least 2 characters."
            return
        }

        viewModelScope.launch {
            val success = repository.blockKeyword(sanitized)
            if (success) {
                _keywordInput.value = ""
                _errorMessage.value = null
            } else {
                _errorMessage.value = "'$sanitized' is already blocked."
            }
        }
    }

    fun removeKeyword(keywordEntity: BlockedKeywordEntity) {
        if (uiState.value.isChallengeActive) {
            _activeChallenge.value = ActiveChallengeState(
                changeType = ChangeType.DELETE_KEYWORD,
                targetId = keywordEntity.id.toString(),
                targetName = keywordEntity.keyword,
                description = "unblock website '${keywordEntity.keyword}'"
            )
        } else {
            // Invariant Rule 6: Accountability Challenge is required! Prompt user to activate it.
            _pendingAccountabilityKeyword.value = keywordEntity
        }
    }

    fun onActivateChallengeForKeyword() {
        val entity = _pendingAccountabilityKeyword.value ?: return
        viewModelScope.launch {
            repository.setChallengeActive(true)
            _pendingAccountabilityKeyword.value = null
            _activeChallenge.value = ActiveChallengeState(
                changeType = ChangeType.DELETE_KEYWORD,
                targetId = entity.id.toString(),
                targetName = entity.keyword,
                description = "unblock website '${entity.keyword}'"
            )
        }
    }

    fun onDismissAccountabilityRequired() {
        _pendingAccountabilityKeyword.value = null
    }

    fun onChallengeSuccess(context: Context) {
        val challenge = _activeChallenge.value ?: return
        viewModelScope.launch {
            val pending = repository.createPendingChange(
                changeType = challenge.changeType,
                targetId = challenge.targetId,
                targetName = challenge.targetName
            )
            repository.applyPendingChange(pending)
            if (challenge.changeType == ChangeType.DELETE_KEYWORD) {
                val id = challenge.targetId.toLongOrNull()
                if (id != null) repository.unblockKeyword(id)
            }
            _activeChallenge.value = null
        }
    }

    fun onCancelChallenge() {
        _activeChallenge.value = null
    }

    fun cancelPendingChange(id: Long) {
        viewModelScope.launch {
            repository.cancelPendingChange(id)
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }
}
