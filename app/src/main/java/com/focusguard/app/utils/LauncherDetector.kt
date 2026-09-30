package com.focusguard.app.utils

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LauncherDetector @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var cachedLauncherPackage: String? = null
    private var lastCheckedTime = 0L
    private val CACHE_DURATION_MS = 60_000L // Re-check every 60s in case user switches launcher

    fun getCurrentLauncherPackage(): String? {
        val now = android.os.SystemClock.elapsedRealtime()
        if (cachedLauncherPackage == null || now - lastCheckedTime > CACHE_DURATION_MS) {
            val intent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_HOME) }
            val resolveInfo = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            cachedLauncherPackage = resolveInfo?.activityInfo?.packageName
            lastCheckedTime = now
        }
        return cachedLauncherPackage
    }

    fun isLauncherPackage(packageName: String): Boolean {
        val current = getCurrentLauncherPackage()
        return if (current != null) {
            packageName.equals(current, ignoreCase = true)
        } else {
            packageName.contains("launcher", ignoreCase = true) || packageName.contains("home", ignoreCase = true)
        }
    }
}
