package com.focusguard.app.data.repository

import com.focusguard.app.data.datastore.FocusGuardPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataStoreRepository @Inject constructor(
    private val preferences: FocusGuardPreferences
) {
    suspend fun setAutoBlockNewApps(enabled: Boolean) {
        preferences.setAutoBlockNewApps(enabled)
    }

    fun getAutoBlockNewAppsFlow(): Flow<Boolean> =
        preferences.isAutoBlockNewAppsEnabled

    suspend fun isAutoBlockNewAppsEnabled(): Boolean =
        preferences.isAutoBlockNewAppsEnabled.first()
}
