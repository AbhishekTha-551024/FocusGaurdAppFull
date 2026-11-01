package com.focusguard.app.service.handlers

import android.content.Context
import android.util.Log
import com.focusguard.app.data.repository.FocusGuardRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

data class AutoBlockAction(
    val appLabel: String
)

@Singleton
class AutoBlockHandler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: FocusGuardRepository
) {
    private val packageInstallTimeCache = ConcurrentHashMap<String, Long>()

    fun checkAutoBlock(
        pkg: String,
        isHardBlocked: Boolean,
        getCachedAppLabel: (String) -> String,
        scope: CoroutineScope
    ): AutoBlockAction? {
        if (!isHardBlocked && repository.isFastAutoBlockNewAppsEnabled()) {
            if (!repository.isFastAppReviewed(pkg)) {
                val enabledTs = repository.getFastAutoBlockEnabledTimestamp()
                if (enabledTs > 0L) {
                    val installTime = packageInstallTimeCache.getOrPut(pkg) {
                        try {
                            context.packageManager.getPackageInfo(pkg, 0).firstInstallTime
                        } catch (e: Exception) { 0L }
                    }
                    val isRecentInstall = installTime >= enabledTs

                    if (isRecentInstall) {
                        val appLabel = getCachedAppLabel(pkg)
                        Log.i(TAG, "Auto-Block JIT triggered for new install: $pkg ($appLabel)")
                        scope.launch {
                            repository.blockApp(
                                packageName = pkg,
                                appName = appLabel,
                                isAutoBlockedNewInstall = true
                            )
                        }
                        return AutoBlockAction(appLabel = appLabel)
                    }
                }
            }
        }
        return null
    }

    fun clearCache() {
        packageInstallTimeCache.clear()
    }

    companion object {
        private const val TAG = "FocusGuardService"
    }
}

