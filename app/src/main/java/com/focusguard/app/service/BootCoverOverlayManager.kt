package com.focusguard.app.service

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.UserManager
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.focusguard.app.feature.settings.data.repository.SupportCodeRepositoryImpl
import com.focusguard.app.core.startup.ProtectionStartupReadiness
import com.focusguard.app.security.SupportVerificationResult
import com.focusguard.app.service.ui.BootCoverContent
import com.focusguard.app.service.ui.SupportUiStatus
import com.focusguard.app.service.ui.evaluateDismissEligibility
import com.focusguard.app.ui.theme.FocusGuardTheme
import com.focusguard.app.utils.ServiceLifecycleOwner
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "FG:Shield"

@EntryPoint
@InstallIn(SingletonComponent::class)
interface BootCoverEntryPoint {
    fun supportCodeRepository(): SupportCodeRepositoryImpl
    fun startupReadiness(): ProtectionStartupReadiness
}

/**
 * Manages WindowManager layout params, attachment, detachment, and support code execution for the boot shield.
 */
class BootCoverOverlayManager(
    private val context: Context,
    private val startupReadiness: ProtectionStartupReadiness
) {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var lifecycleOwner: ServiceLifecycleOwner? = null

    var isOverlayAttached: Boolean = false
        private set

    val hasOverlayView: Boolean
        get() = overlayView != null

    private var supportStatus by mutableStateOf<SupportUiStatus>(SupportUiStatus.Idle)

    fun createLayoutParams(): WindowManager.LayoutParams =
        WindowManager.LayoutParams().apply {
            type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            format = PixelFormat.TRANSLUCENT
            @Suppress("DEPRECATION")
            flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_FULLSCREEN or
                    WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    // The cover states "FocusGuard is protecting this device" — a screenshot of it,
                    // or of the black hole where it should be, is information about the user.
                    WindowManager.LayoutParams.FLAG_SECURE
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.MATCH_PARENT
            gravity = Gravity.CENTER
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

    fun mountOverlay(
        isFallbackProvider: () -> Boolean,
        onOpenAccessibilitySettings: () -> Unit,
        onDismissRequested: () -> Unit,
        onMounted: () -> Unit
    ) {
        if (overlayView != null) return

        if (BootCoverPolicy.shouldAbortMount(startupReadiness.isReady())) {
            Log.i(TAG, "Protection startup is ready while preparing view. Aborting overlay mount.")
            onDismissRequested()
            return
        }

        try {
            val wm = (context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager).also {
                windowManager = it
            } ?: run {
                Log.e(TAG, "WindowManager unavailable")
                onDismissRequested()
                return
            }

            val currentLifecycle = ServiceLifecycleOwner().also {
                lifecycleOwner = it
                it.start()
            }

            val isUnlocked = isUserStorageUnlocked(context)
            val supportRepo = if (isUnlocked) resolveSupportRepo(context) else null
            val deviceId = if (isUnlocked && supportRepo != null) supportRepo.getDeviceId() else ""

            val composeView = ComposeView(context).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnLifecycleDestroyed(currentLifecycle))
                setViewTreeLifecycleOwner(currentLifecycle)
                setViewTreeViewModelStoreOwner(currentLifecycle)
                setViewTreeSavedStateRegistryOwner(currentLifecycle)

                setContent {
                    val scope = rememberCoroutineScope()
                    FocusGuardTheme {
                        BootCoverContent(
                            isFallback = isFallbackProvider(),
                            isUserUnlocked = isUnlocked,
                            deviceId = deviceId,
                            supportStatus = supportStatus,
                            onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                            onSubmitSupportCode = { code ->
                                handleSupportCodeSubmit(
                                    code = code,
                                    supportRepo = supportRepo,
                                    scope = scope,
                                    onDismissRequested = onDismissRequested
                                )
                            }
                        )
                    }
                }
            }

            val params = createLayoutParams()
            wm.addView(composeView, params)
            overlayView = composeView
            isOverlayAttached = true
            Log.i(TAG, "Boot cover overlay successfully attached to WindowManager.")

            if (BootCoverPolicy.shouldRemoveAfterMount(startupReadiness.isReady())) {
                Log.i(TAG, "Protection startup became ready during view attachment. Removing overlay.")
                dismissOverlay()
                onDismissRequested()
                return
            }

            onMounted()

        } catch (e: Exception) {
            Log.e(TAG, "Failed to mount boot cover overlay: ${e.message}", e)
            dismissOverlay()
            onDismissRequested()
        }
    }

    private fun handleSupportCodeSubmit(
        code: String,
        supportRepo: SupportCodeRepositoryImpl?,
        scope: CoroutineScope,
        onDismissRequested: () -> Unit
    ) {
        if (supportRepo == null) {
            supportStatus = SupportUiStatus.DeviceEncrypted()
            return
        }
        supportStatus = SupportUiStatus.Verifying

        scope.launch(Dispatchers.IO) {
            val result = supportRepo.verifySupportCode(code)
            when (result) {
                is SupportVerificationResult.Success -> {
                    val applied = supportRepo.executeSupportAction(result)
                    if (evaluateDismissEligibility(result, applied)) {
                        supportStatus = SupportUiStatus.Success
                        Log.i(TAG, "Support code verified and executed; dismissing boot shield.")
                        withContext(Dispatchers.Main) {
                            dismissOverlay()
                            onDismissRequested()
                        }
                    } else {
                        supportStatus = SupportUiStatus.ExecutionError()
                    }
                }
                is SupportVerificationResult.RateLimited -> {
                    supportStatus = SupportUiStatus.RateLimited(result.remainingMinutes)
                }
                is SupportVerificationResult.AlreadyUsed -> {
                    supportStatus = SupportUiStatus.AlreadyUsed()
                }
                is SupportVerificationResult.InvalidCode -> {
                    supportStatus = SupportUiStatus.InvalidCode()
                }
            }
        }
    }

    fun dismissOverlay() {
        overlayView?.let { view ->
            try {
                windowManager?.removeView(view)
                Log.i(TAG, "Boot cover overlay removed from WindowManager.")
            } catch (e: Exception) {
                Log.w(TAG, "Error removing boot cover overlay: ${e.message}")
            }
        }
        overlayView = null
        isOverlayAttached = false

        lifecycleOwner?.stop()
        lifecycleOwner = null
    }

    private fun isUserStorageUnlocked(ctx: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val userManager = ctx.getSystemService(UserManager::class.java)
            userManager != null && userManager.isUserUnlocked
        } else {
            true
        }

    private fun resolveSupportRepo(ctx: Context): SupportCodeRepositoryImpl? =
        try {
            EntryPointAccessors.fromApplication(
                ctx.applicationContext,
                BootCoverEntryPoint::class.java
            ).supportCodeRepository()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resolve SupportCodeRepository: ${e.message}")
            null
        }
}
