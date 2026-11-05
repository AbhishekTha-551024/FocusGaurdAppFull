package com.focusguard.app.feature.keywordblocker.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusguard.app.core.common.result.AppResult
import com.focusguard.app.core.model.ChallengeType
import com.focusguard.app.core.model.ChangeType
import com.focusguard.app.data.repository.FocusGuardRepository
import com.focusguard.app.feature.keywordblocker.domain.model.BlockedKeyword
import com.focusguard.app.feature.keywordblocker.domain.repository.KeywordBlockerRepository
import com.focusguard.app.feature.keywordblocker.domain.usecase.ActivateKeywordChallengeUseCase
import com.focusguard.app.feature.keywordblocker.domain.usecase.AddBlockedKeywordUseCase
import com.focusguard.app.feature.keywordblocker.domain.usecase.DeleteBlockedKeywordUseCase
import com.focusguard.app.feature.keywordblocker.domain.usecase.GetBlockedKeywordsUseCase
import com.focusguard.app.core.model.ActiveChallengeState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * =====================================================================
 * KeywordBlockerViewModel.kt - Presentation / State Management Layer
 * =====================================================================
 *
 * ## 1. Yeh File Kya Karti Hai? (Core Responsibility)
 * Yeh ViewModel Keyword Blocker screen ka brain hai. Yeh UI se aane wale events
 * ([KeywordBlockerUiEvent]) ko process karta hai, Domain Use Cases ke sath communicate karta hai,
 * aur reactive `StateFlow<KeywordBlockerUiState>` emit karta hai jise Compose UI observe karti hai.
 *
 * ## 2. Core Business Logic & State Combining:
 * - **Multi-Stream State Combining:** Yeh 4 alag-alag reactive streams ko `combine(...)` karta hai:
 *   1. `getBlockedKeywordsUseCase()` (Room database ki live blocked list)
 *   2. `localInputFlow` (Text inputs, search filter, active challenge state)
 *   3. `keywordBlockerRepository.getPendingKeywordChanges()` (Active time delays)
 *   4. `focusGuardRepository.challengeSettings` (Global friction settings)
 * - **Search Filtering:** User agar search box me type karta hai toh live in-memory filter apply hota hai.
 * - **Accountability Guard:** Agar user pehla keyword block karne ki koshish karta hai aur accountability
 *   off hai, toh seedha block karne ke bajaye `_pendingAccountabilityKeyword` set karke challenge dialog trigger karta hai.
 * - **Unblock Security:** Agar challenge active hai aur user delete icon dabata hai, toh direct delete
 *   nahi hota; pehle typing challenge ya partner approval dialogue trigger hota hai.
 *
 * ## 3. Lifecycle & Performance (Senior Dev Perspective):
 * - `SharingStarted.Eagerly`: StateFlow ko eagerly start karta hai aur initial value me
 *   `getBlockedKeywordsUseCase.getFastBlocked()` aur `getCurrentSettingsSnapshot()` use karta hai,
 *   jisse user ko launch par 0-millisecond instant UI dikhta hai (No white screens / flickers).
 * =====================================================================
 */
@HiltViewModel
class KeywordBlockerViewModel @Inject constructor(
    private val getBlockedKeywordsUseCase: GetBlockedKeywordsUseCase,
    private val addBlockedKeywordUseCase: AddBlockedKeywordUseCase,
    private val deleteBlockedKeywordUseCase: DeleteBlockedKeywordUseCase,
    private val activateKeywordChallengeUseCase: ActivateKeywordChallengeUseCase,
    private val keywordBlockerRepository: KeywordBlockerRepository,
    private val focusGuardRepository: FocusGuardRepository
) : ViewModel() {

    private val _keywordInput = MutableStateFlow("")
    private val _searchQuery = MutableStateFlow("")
    private val _errorMessage = MutableStateFlow<String?>(null)
    private val _activeChallenge = MutableStateFlow<ActiveChallengeState?>(null)
    private val _pendingAccountabilityKeyword = MutableStateFlow<BlockedKeyword?>(null)

    private val localInputFlow = combine(
        _keywordInput,
        _searchQuery,
        _errorMessage,
        _activeChallenge,
        _pendingAccountabilityKeyword
    ) { input, search, error, challenge, pendingKw ->
        LocalState(input, search, error, challenge, pendingKw)
    }

    private data class LocalState(
        val input: String,
        val search: String,
        val error: String?,
        val challenge: ActiveChallengeState?,
        val pendingKw: BlockedKeyword?
    )

    val uiState: StateFlow<KeywordBlockerUiState> = combine(
        getBlockedKeywordsUseCase(),
        localInputFlow,
        keywordBlockerRepository.getPendingKeywordChanges(),
        focusGuardRepository.challengeSettings
    ) { keywords, local, pendingChanges, settings ->
        val filtered = if (local.search.isBlank()) {
            keywords
        } else {
            keywords.filter { it.keyword.contains(local.search, ignoreCase = true) }
        }
        KeywordBlockerUiState(
            isLoading = false,
            keywordInput = local.input,
            searchQuery = local.search,
            errorMessage = local.error,
            blockedKeywords = filtered,
            pendingChanges = pendingChanges,
            activeChallenge = local.challenge,
            isChallengeActive = settings.isChallengeActive,
            challengeType = settings.type,
            commitmentSentence = settings.commitmentSentence,
            timeDelayHours = settings.timeDelayHours,
            pendingAccountabilityKeyword = local.pendingKw
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        KeywordBlockerUiState(
            isLoading = false,
            blockedKeywords = getBlockedKeywordsUseCase.getFastBlocked(),
            isChallengeActive = focusGuardRepository.getCurrentSettingsSnapshot().isChallengeActive,
            challengeType = focusGuardRepository.getCurrentSettingsSnapshot().challengeType,
            commitmentSentence = focusGuardRepository.getCurrentSettingsSnapshot().commitmentSentence,
            timeDelayHours = focusGuardRepository.getCurrentSettingsSnapshot().timeDelayHours
        )
    )

    fun onEvent(event: KeywordBlockerUiEvent) {
        when (event) {
            is KeywordBlockerUiEvent.OnKeywordInputChange -> onKeywordInputChange(event.input)
            is KeywordBlockerUiEvent.OnSearchQueryChange -> onSearchQueryChange(event.query)
            is KeywordBlockerUiEvent.AddKeyword -> addKeyword()
            is KeywordBlockerUiEvent.RemoveKeyword -> removeKeyword(event.keyword)
            is KeywordBlockerUiEvent.OnActivateChallenge -> onActivateChallengeForKeyword(event.type, event.sentence)
            is KeywordBlockerUiEvent.OnDismissAccountability -> onDismissAccountabilityRequired()
            is KeywordBlockerUiEvent.OnChallengeSuccess -> onChallengeSuccess(event.context)
            is KeywordBlockerUiEvent.OnCancelChallenge -> onCancelChallenge()
            is KeywordBlockerUiEvent.OnCancelPendingChange -> cancelPendingChange(event.id)
            is KeywordBlockerUiEvent.ClearError -> clearError()
        }
    }

    fun onKeywordInputChange(input: String) {
        _keywordInput.value = input
        if (_errorMessage.value != null) _errorMessage.value = null
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun addKeyword() {
        val sanitized = addBlockedKeywordUseCase.sanitize(_keywordInput.value)
        if (sanitized.isEmpty()) {
            _errorMessage.value = "Please enter a keyword or website domain."
            return
        }
        if (sanitized.length < 2) {
            _errorMessage.value = "Keyword must be at least 2 characters."
            return
        }
        if (!uiState.value.isChallengeActive) {
            _pendingAccountabilityKeyword.value = BlockedKeyword(keyword = sanitized)
            return
        }

        viewModelScope.launch {
            when (val result = addBlockedKeywordUseCase(sanitized)) {
                is AppResult.Success -> {
                    _keywordInput.value = ""
                    _errorMessage.value = null
                }
                is AppResult.Failure -> {
                    _errorMessage.value = result.error.message
                }
            }
        }
    }

    fun removeKeyword(keyword: BlockedKeyword) {
        if (uiState.value.isChallengeActive) {
            _activeChallenge.value = ActiveChallengeState(
                changeType = ChangeType.DELETE_KEYWORD,
                targetId = keyword.id.toString(),
                targetName = keyword.keyword,
                description = "unblock website '${keyword.keyword}'"
            )
        } else {
            viewModelScope.launch {
                deleteBlockedKeywordUseCase(keyword.id)
            }
        }
    }

    fun onActivateChallengeForKeyword(newType: ChallengeType, sentence: String) {
        val entity = _pendingAccountabilityKeyword.value ?: return
        viewModelScope.launch {
            val result = activateKeywordChallengeUseCase(entity.keyword, newType, sentence)
            _pendingAccountabilityKeyword.value = null
            when (result) {
                is AppResult.Success -> {
                    _keywordInput.value = ""
                    _errorMessage.value = null
                }
                is AppResult.Failure -> {
                    _errorMessage.value = result.error.message
                }
            }
        }
    }

    fun onDismissAccountabilityRequired() {
        _pendingAccountabilityKeyword.value = null
    }

    fun onChallengeSuccess(context: Context) {
        val challenge = _activeChallenge.value ?: return
        viewModelScope.launch {
            val pending = focusGuardRepository.createPendingChange(
                changeType = challenge.changeType,
                targetId = challenge.targetId,
                targetName = challenge.targetName
            )
            focusGuardRepository.applyPendingChange(pending)
            _activeChallenge.value = null
        }
    }

    fun onCancelChallenge() {
        _activeChallenge.value = null
    }

    fun cancelPendingChange(id: Long) {
        viewModelScope.launch {
            keywordBlockerRepository.cancelPendingChange(id)
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }
}
