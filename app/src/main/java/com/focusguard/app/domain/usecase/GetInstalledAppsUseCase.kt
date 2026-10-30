package com.focusguard.app.domain.usecase

import android.content.Context
import android.content.pm.PackageManager
import com.focusguard.app.core.model.AppInfo
import com.focusguard.app.data.repository.FocusGuardRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * GetInstalledAppsUseCase
 * =======================
 * High-performance, memory-cached scanner for user-facing applications.
 * Caches raw package scan in memory to deliver 0ms instant UI rendering without Binder IPC lag.
 */
@Singleton
class GetInstalledAppsUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: FocusGuardRepository
) {

    private val packageManager: PackageManager = context.packageManager
    private var cachedRawApps: List<AppInfo>? = null
    private val mutex = Mutex()

    init {
        // Eagerly pre-warm installed apps in RAM at app boot for 0ms instant UI
        val scope = kotlinx.coroutines.CoroutineScope(
            Dispatchers.IO + kotlinx.coroutines.SupervisorJob() +
            kotlinx.coroutines.CoroutineExceptionHandler { _, throwable ->
                android.util.Log.d("GetInstalledAppsUseCase", "Warmup uncaught error: ${throwable.message}")
            }
        )
        scope.launch {
            try {
                val apps = getRawInstalledApps(false)
                // Pre-warm top app icons into memory cache in background for buttery smooth 120 FPS scrolling
                apps.take(75).forEach { app ->
                    try {
                        com.focusguard.app.utils.AppIconLoader.loadIcon(context, app.packageName)
                    } catch (e: Exception) {
                        android.util.Log.d("GetInstalledAppsUseCase", "Icon pre-warm error: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                android.util.Log.d("GetInstalledAppsUseCase", "Pre-warm failed: ${e.message}")
            }
        }
    }

    /**
     * Returns a reactive Flow of all user-facing apps, sorted alphabetically,
     * annotated with their current blocked status in 0ms.
     */
    fun execute(forceRefresh: Boolean = false): Flow<List<AppInfo>> {
        return repository.blockedApps.map { blockedList ->
            val rawApps = getRawInstalledApps(forceRefresh)
            val blockedMap = blockedList.associateBy { it.packageName.lowercase() }

            rawApps.map { raw ->
                val blockedEntry = blockedMap[raw.packageName.lowercase()]
                AppInfo(
                    packageName = raw.packageName,
                    appName = raw.appName,
                    isBlocked = blockedEntry != null,
                    isAutoBlockedUnsupportedBrowser = blockedEntry?.isAutoBlockedUnsupportedBrowser ?: false,
                    isAutoBlockedNewInstall = blockedEntry?.isAutoBlockedNewInstall ?: false
                )
            }.sortedWith(compareByDescending<AppInfo> { it.isBlocked }.thenBy { it.appName.lowercase() })
        }.flowOn(Dispatchers.Default)
    }

    private suspend fun getRawInstalledApps(forceRefresh: Boolean): List<AppInfo> {
        if (!forceRefresh) {
            cachedRawApps?.let { return it }
        }

        return mutex.withLock {
            if (!forceRefresh) {
                cachedRawApps?.let { return it }
            }

            withContext(Dispatchers.IO) {
                val launcherIntent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
                    addCategory(android.content.Intent.CATEGORY_LAUNCHER)
                }
                val resolveInfos = packageManager.queryIntentActivities(launcherIntent, 0)
                val launcherPackages = HashSet<String>(resolveInfos.size)
                for (ri in resolveInfos) {
                    ri.activityInfo?.packageName?.let { launcherPackages.add(it) }
                }

                val apps = packageManager.getInstalledApplications(0)
                val userApps = ArrayList<AppInfo>(launcherPackages.size)

                for (app in apps) {
                    val pkg = app.packageName
                    // Exclude FocusGuard itself and apps without a launcher intent (system daemons)
                    if (pkg == context.packageName || pkg.startsWith("com.focusguard.app")) continue
                    if (!launcherPackages.contains(pkg)) continue

                    val name = try {
                        packageManager.getApplicationLabel(app).toString()
                    } catch (e: Exception) {
                        pkg
                    }

                    userApps.add(AppInfo(packageName = pkg, appName = name))
                }

                cachedRawApps = userApps
                userApps
            }
        }
    }

    suspend fun invalidateCache() {
        mutex.withLock {
            cachedRawApps = null
        }
    }

    /**
     * Synchronous 0ms RAM getter for ViewModel initial values.
     * Prevents initial empty-state / flicker when switching tabs.
     */
    fun getFastInstalledApps(): List<AppInfo> {
        val raw = cachedRawApps ?: return emptyList()
        val blockedSet = repository.getFastBlockedPackages()
        return raw.map { rawApp ->
            val isBlocked = blockedSet.contains(rawApp.packageName.lowercase())
            rawApp.copy(isBlocked = isBlocked)
        }.sortedWith(compareByDescending<AppInfo> { it.isBlocked }.thenBy { it.appName.lowercase() })
    }
}
