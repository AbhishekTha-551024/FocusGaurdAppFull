package com.focusguard.app.core.startup

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

data class ProtectionStartupState(
    val accessibilityConnected: Boolean = false,
    val rulesSnapshotReady: Boolean = false
) {
    val isReady: Boolean
        get() = accessibilityConnected && rulesSnapshotReady
}

@Singleton
class ProtectionStartupReadiness @Inject constructor() {

    private val mutableState = MutableStateFlow(ProtectionStartupState())
    val state: StateFlow<ProtectionStartupState> = mutableState.asStateFlow()

    fun beginAccessibilityConnection() {
        mutableState.update {
            ProtectionStartupState(accessibilityConnected = true)
        }
    }

    fun markRulesSnapshotReady() {
        mutableState.update { current ->
            current.copy(rulesSnapshotReady = true)
        }
    }

    fun markAccessibilityDisconnected() {
        mutableState.update { ProtectionStartupState() }
    }

    fun isReady(): Boolean = mutableState.value.isReady
}
