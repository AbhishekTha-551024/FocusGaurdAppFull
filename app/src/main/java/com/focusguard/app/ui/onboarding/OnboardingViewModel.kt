package com.focusguard.app.ui.onboarding

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusguard.app.data.repository.FocusGuardRepository
import com.focusguard.app.receiver.FocusGuardDeviceAdminReceiver
import com.focusguard.app.usage.UsageStatsReader
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: FocusGuardRepository,
    private val usageStatsReader: UsageStatsReader
) : ViewModel() {

    val isOnboardingComplete: StateFlow<Boolean> = repository.isOnboardingComplete
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = repository.isFastOnboardingComplete()
        )

    fun isFastOnboardingComplete(): Boolean = repository.isFastOnboardingComplete()

    private val _isAccessibilityEnabled = MutableStateFlow(isAccessibilityServiceEnabled())
    val isAccessibilityEnabled: StateFlow<Boolean> = _isAccessibilityEnabled

    private val _isAdminActive = MutableStateFlow(isDeviceAdminActive())
    val isAdminActive: StateFlow<Boolean> = _isAdminActive

    private val _isUsageAccessGranted = MutableStateFlow(usageStatsReader.hasUsageAccessPermission())
    val isUsageAccessGranted: StateFlow<Boolean> = _isUsageAccessGranted

    val isProtectionActive: StateFlow<Boolean> = combine(
        _isAccessibilityEnabled,
        _isAdminActive
    ) { acc, admin -> acc && admin }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), isAccessibilityServiceEnabled() && isDeviceAdminActive())

    init {
        refresh()
    }

    fun refresh() {
        _isAccessibilityEnabled.value = isAccessibilityServiceEnabled()
        _isAdminActive.value = isDeviceAdminActive()
        _isUsageAccessGranted.value = usageStatsReader.hasUsageAccessPermission()

        // If Accessibility Service is already enabled, the core protection is active!
        // Immediately mark onboarding complete so the user is never stuck in onboarding screens.
        if (_isAccessibilityEnabled.value) {
            viewModelScope.launch {
                repository.markOnboardingComplete()
            }
        }
    }

    fun completeOnboarding() {
        viewModelScope.launch {
            repository.markOnboardingComplete()
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        return com.focusguard.app.recovery.AccessibilityRecoveryHelper.isAccessibilityActive(context)
    }

    private fun isDeviceAdminActive(): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponent = ComponentName(context, FocusGuardDeviceAdminReceiver::class.java)
        return dpm.isAdminActive(adminComponent)
    }

    fun openAccessibilitySettings(): Intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)

    fun openDeviceAdminActivation(): Intent {
        com.focusguard.app.service.FocusGuardAccessibilityService.allowAdminActivationWindow()
        val adminComponent = ComponentName(context, FocusGuardDeviceAdminReceiver::class.java)
        return Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "FocusGuard uses Device Administrator to prevent unauthorized uninstallation."
            )
        }
    }

    fun openUsageAccessSettings(): Intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
}
