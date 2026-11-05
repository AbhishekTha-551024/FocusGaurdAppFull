package com.focusguard.app.service.handlers

internal enum class SettingsDestructiveAction {
    NONE,
    DISABLE,
    UNINSTALL,
    FORCE_STOP,
    CLEAR_DATA
}

internal object SettingsTamperPolicy {

    fun classifyClickedAction(
        isClick: Boolean,
        clickedText: String,
        checkedToggleClicked: Boolean
    ): SettingsDestructiveAction {
        if (!isClick) return SettingsDestructiveAction.NONE
        if (checkedToggleClicked) return SettingsDestructiveAction.DISABLE

        val text = clickedText.lowercase()
        return when {
            text.contains("force stop") -> SettingsDestructiveAction.FORCE_STOP
            text.contains("clear data") || text.contains("clear storage") -> SettingsDestructiveAction.CLEAR_DATA
            text.contains("uninstall") || text.contains("delete app") || text.contains("remove app") ->
                SettingsDestructiveAction.UNINSTALL
            text.contains("deactivate") || text.contains("disable") || text.contains("turn off") ->
                SettingsDestructiveAction.DISABLE
            else -> SettingsDestructiveAction.NONE
        }
    }

    fun isSettingsSearchScreen(className: String, hasFocusedSearchControl: Boolean): Boolean =
        className.contains("search", ignoreCase = true) || hasFocusedSearchControl

    fun shouldBlockAccessibilityDisable(
        isAccessibilityScreen: Boolean,
        targetsFocusGuard: Boolean,
        action: SettingsDestructiveAction
    ): Boolean =
        isAccessibilityScreen &&
                targetsFocusGuard &&
                action == SettingsDestructiveAction.DISABLE

    fun shouldBlockDeviceAdminDisable(
        isDeviceAdminScreen: Boolean,
        targetsFocusGuard: Boolean,
        action: SettingsDestructiveAction
    ): Boolean =
        isDeviceAdminScreen &&
                targetsFocusGuard &&
                action == SettingsDestructiveAction.DISABLE

    fun shouldBlockFocusGuardAppAction(
        isFocusGuardAppDetailsPage: Boolean,
        action: SettingsDestructiveAction
    ): Boolean =
        isFocusGuardAppDetailsPage &&
                (action == SettingsDestructiveAction.DISABLE ||
                        action == SettingsDestructiveAction.UNINSTALL ||
                        action == SettingsDestructiveAction.FORCE_STOP ||
                        action == SettingsDestructiveAction.CLEAR_DATA)
}
