package com.focusguard.app.data.repository

import com.focusguard.app.data.db.BlockedAppEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppBlockingRepository @Inject constructor(
    private val repository: FocusGuardRepository
) {
    val blockedApps: Flow<List<BlockedAppEntity>> = repository.blockedApps

    suspend fun blockApp(
        packageName: String,
        appName: String,
        isAutoBlockedNewInstall: Boolean = true,
        isAutoBlockedUnsupportedBrowser: Boolean = false
    ) {
        repository.blockApp(
            packageName = packageName,
            appName = appName,
            isAutoBlockedUnsupportedBrowser = isAutoBlockedUnsupportedBrowser,
            isAutoBlockedNewInstall = isAutoBlockedNewInstall
        )
    }

    suspend fun unblockApp(packageName: String) {
        repository.unblockApp(packageName)
    }

    fun isFastBlockedApp(packageName: String): Boolean =
        repository.isFastBlockedApp(packageName)

    fun isFastAppReviewed(packageName: String): Boolean =
        repository.isFastAppReviewed(packageName)

    suspend fun markAppReviewed(packageName: String) {
        repository.markAppReviewed(packageName)
    }

    suspend fun clearAppReviewed(packageName: String) {
        repository.clearAppReviewed(packageName)
    }
}
