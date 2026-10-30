package com.focusguard.app.core.engine

import android.content.Context
import android.os.Build
import android.util.Log
import com.focusguard.app.blockers.SessionBlockManager
import com.focusguard.app.core.database.dao.MindfulDao
import com.focusguard.app.core.datastore.FocusGuardPreferences
import com.focusguard.app.core.model.RulesSnapshot
import com.focusguard.app.data.repository.FocusGuardRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "RulesSnapshotSync"

/**
 * Reactively synchronizes Room database tables, Jetpack DataStore preferences,
 * and session state into the unified, thread-safe [RulesSnapshotHolder].
 */
@Singleton
class RulesSnapshotSynchronizer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val snapshotHolder: RulesSnapshotHolder,
    private val repository: FocusGuardRepository,
    private val mindfulDao: MindfulDao,
    private val preferences: FocusGuardPreferences,
    private val sessionBlockManager: SessionBlockManager
) {

    fun start(scope: CoroutineScope) {
        // Step 1: Pre-load Direct Boot (DE) cache immediately so zero gaps exist before CE unlocks
        loadDirectBootCache()

        // Step 2: Combine all Room & DataStore flows into a single unified RulesSnapshot stream
        scope.launch {
            combine(
                repository.blockedApps,
                repository.blockedKeywordsSet,
                mindfulDao.getAllMindfulApps(),
                preferences.settingsSnapshot,
                sessionBlockManager.sessionStateFlow
            ) { blockedApps, keywords, mindfulApps, settings, sessionState ->
                val blockedPkgSet = HashSet<String>(blockedApps.size * 2)
                for (app in blockedApps) {
                    blockedPkgSet.add(app.packageName.lowercase())
                    blockedPkgSet.add(app.packageName)
                }

                val mindfulPkgSet = HashSet<String>(mindfulApps.size * 2)
                for (app in mindfulApps) {
                    mindfulPkgSet.add(app.packageName.lowercase())
                    mindfulPkgSet.add(app.packageName)
                }

                RulesSnapshot(
                    blockedPackages = blockedPkgSet,
                    blockedKeywords = keywords,
                    mindfulPackages = mindfulPkgSet,
                    isUninstallProtectionEnabled = settings.isUninstallProtectionEnabled,
                    isShortsBlockingEnabled = settings.isShortsReelsBlockEnabled || settings.isYouTubeShortsBlockEnabled,
                    isReelsBlockingEnabled = settings.isShortsReelsBlockEnabled || settings.isInstagramReelsBlockEnabled || settings.isFacebookReelsBlockEnabled,
                    isIncognitoBlockingEnabled = settings.isIncognitoBlockEnabled,
                    isDeepFocusActive = sessionState.isActive,
                    deepFocusSuitcasePackages = sessionState.packedPackages,
                    isChallengeActive = settings.isChallengeActive,
                    autoBlockNewApps = settings.isAutoBlockNewAppsEnabled,
                    timestampMs = System.currentTimeMillis()
                )
            }.collect { snapshot ->
                snapshotHolder.set(snapshot)
                Log.d(
                    TAG,
                    "RulesSnapshot atomically refreshed: ${snapshot.blockedPackages.size} blocked apps, " +
                        "${snapshot.mindfulPackages.size} mindful apps, protection=${snapshot.isUninstallProtectionEnabled}"
                )
            }
        }
    }

    private fun loadDirectBootCache() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                val deContext = context.createDeviceProtectedStorageContext()
                val dePrefs = deContext.getSharedPreferences("focusguard_de_cache", Context.MODE_PRIVATE)
                val deBlocked = dePrefs.getStringSet("blocked_packages", emptySet()) ?: emptySet()
                val isDeProtected = dePrefs.getBoolean("uninstall_protection_enabled", false)

                val blockedSet = HashSet<String>(deBlocked.size * 2)
                for (pkg in deBlocked) {
                    blockedSet.add(pkg.lowercase())
                    blockedSet.add(pkg)
                }

                snapshotHolder.update { current ->
                    current.copy(
                        blockedPackages = blockedSet,
                        isUninstallProtectionEnabled = isDeProtected
                    )
                }
                Log.i(TAG, "Direct Boot DE cache loaded into snapshot: ${blockedSet.size} packages")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load Direct Boot cache into snapshot: ${e.message}")
            }
        }
    }
}
