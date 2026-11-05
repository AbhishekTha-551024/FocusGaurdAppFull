package com.focusguard.app.feature.home.domain.usecase

import com.focusguard.app.feature.home.domain.repository.HomeRepository
import javax.inject.Inject

/**
 * =========================================================================================
 * ARCHITECTURE COMPONENT: Domain Use Case (System Permission & Capability Re-Auditor)
 * =========================================================================================
 *
 * PURPOSE & RESPONSIBILITIES:
 * 1. Intentional Permission Re-check:
 *    - Triggered whenever the user returns to the app from Android System Settings (e.g., after
 *      granting Accessibility, Device Admin, Draw Over Other Apps, or Battery Unrestricted mode).
 *
 * 2. Hardware / OS Audit:
 *    - Delegates to [HomeRepository.refreshPermissions], which directly queries system managers
 *      ([android.view.accessibility.AccessibilityManager], [android.app.admin.DevicePolicyManager],
 *      [android.provider.Settings.canDrawOverlays], and [android.os.PowerManager]).
 *    - Instantly updates UI banners and warning cards to reflect newly granted or revoked permissions.
 * =========================================================================================
 */
class RefreshPermissionsUseCase @Inject constructor(
    private val repository: HomeRepository
) {
    operator fun invoke() {
        repository.refreshPermissions()
    }
}
