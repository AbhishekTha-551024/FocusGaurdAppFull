package com.focusguard.app.feature.auth.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusguard.app.feature.auth.domain.usecase.GetCurrentUserUseCase
import com.focusguard.app.feature.auth.domain.usecase.SignInWithGoogleUseCase
import com.focusguard.app.feature.auth.domain.usecase.SignOutUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val getCurrentUserUseCase: GetCurrentUserUseCase,
    private val signInWithGoogleUseCase: SignInWithGoogleUseCase,
    private val signOutUseCase: SignOutUseCase
) : ViewModel() {

    private val initialUser = getCurrentUserUseCase.syncCurrentUser
    private val _uiState = MutableStateFlow(
        AuthUiState(
            user = initialUser,
            isAuthResolved = initialUser != null
        )
    )
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            getCurrentUserUseCase.currentUser.collect { user ->
                _uiState.update { it.copy(user = user, isLoading = false, isAuthResolved = true) }
            }
        }
    }

    fun onEvent(event: AuthUiEvent) {
        when (event) {
            is AuthUiEvent.SignInWithGoogle -> signInWithGoogle(event.context)
            is AuthUiEvent.SignOut -> signOut(event.context)
            AuthUiEvent.ClearError -> clearError()
        }
    }

    fun signInWithGoogle(activityContext: Context) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val result = signInWithGoogleUseCase(activityContext)
                result.fold(
                    onSuccess = { user ->
                        _uiState.update { it.copy(user = user, isLoading = false, isAuthResolved = true) }
                    },
                    onFailure = { error ->
                        val msg = when {
                            error is androidx.credentials.exceptions.GetCredentialCancellationException -> null
                            error.message?.contains("28441") == true || error.message?.contains("No credentials") == true ->
                                "No Google Account found on this device or Google Sign-In setup incomplete."
                            error.message?.contains("10:") == true || error.message?.contains("DEVELOPER_ERROR") == true ->
                                "Google Sign-In configuration error: verify Web Client ID and SHA-1 in Firebase Console."
                            error.message?.contains("network") == true || error is java.io.IOException ->
                                "Network connection error. Please check your internet connection."
                            else -> error.localizedMessage ?: "Google Sign-In failed"
                        }
                        _uiState.update { it.copy(isLoading = false, errorMessage = msg, isAuthResolved = true) }
                    }
                )
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = e.localizedMessage ?: "Google Sign-In failed", isAuthResolved = true) }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun signOut(activityContext: Context) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val result = signOutUseCase(activityContext)
                result.fold(
                    onSuccess = {
                        _uiState.update { it.copy(user = null, isLoading = false, isAuthResolved = true) }
                    },
                    onFailure = { error ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = error.localizedMessage ?: "Sign out failed",
                                isAuthResolved = true
                            )
                        }
                    }
                )
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = e.localizedMessage ?: "Sign out failed", isAuthResolved = true) }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
