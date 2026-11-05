package com.focusguard.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.focusguard.app.data.repository.AppBlockingRepository
import com.focusguard.app.data.repository.DataStoreRepository
import com.focusguard.app.domain.usecase.GetInstalledAppsUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "NewAppInstallReceiver"

@AndroidEntryPoint
class NewAppInstallReceiver : BroadcastReceiver() {

    @Inject
    lateinit var dataStoreRepository: DataStoreRepository

    @Inject
    lateinit var appBlockingRepository: AppBlockingRepository

    @Inject
    lateinit var getInstalledAppsUseCase: GetInstalledAppsUseCase

    private val coroutineExceptionHandler = kotlinx.coroutines.CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG, "Unhandled coroutine exception in NewAppInstallReceiver", throwable)
    }
    private val receiverScope = CoroutineScope(Dispatchers.IO + SupervisorJob() + coroutineExceptionHandler)

    override fun onReceive(context: Context, intent: Intent?) {
        Log.i(TAG, "onReceive triggered: action=${intent?.action}, data=${intent?.data}")
        if (intent?.action != Intent.ACTION_PACKAGE_ADDED) return
        val isReplacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
        if (isReplacing) {
            Log.d(TAG, "Package is being updated/replaced, skipping auto-block: ${intent.data?.schemeSpecificPart}")
            return
        }
        val installedPackageName = intent.data?.schemeSpecificPart ?: return

        // Never block FocusGuard itself
        if (installedPackageName == context.packageName || installedPackageName.startsWith("com.focusguard.app")) {
            Log.d(TAG, "Skipping self-package: $installedPackageName")
            return
        }

        val pendingResult = goAsync()
        receiverScope.launch {
            try {
                // Clear any stale review state for this newly installed package
                appBlockingRepository.clearAppReviewed(installedPackageName)

                val isEnabled = dataStoreRepository.isAutoBlockNewAppsEnabled()
                Log.i(TAG, "isAutoBlockNewAppsEnabled = $isEnabled for package: $installedPackageName")

                if (isEnabled) {
                    val appName = try {
                        val pm = context.packageManager
                        pm.getApplicationLabel(pm.getApplicationInfo(installedPackageName, 0)).toString()
                    } catch (e: Exception) {
                        installedPackageName
                    }

                    // Mark this specifically as auto-blocked (NEW installs), distinct from
                    // manually-blocked and unsupported-browser-auto-blocked apps, so the UI
                    // can show it in "Pending Review" with a clear reason.
                    appBlockingRepository.blockApp(
                        packageName = installedPackageName,
                        appName = appName,
                        isAutoBlockedNewInstall = true
                    )
                    getInstalledAppsUseCase.invalidateCache()
                    Log.i(TAG, "SUCCESS: Auto-blocked new install: $installedPackageName ($appName)")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error auto-blocking new app $installedPackageName", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
