package com.focusguard.app.feature.settings.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.focusguard.app.security.SupportVerificationResult
import kotlinx.coroutines.delay

private val DialogSurface = Color(0xFF0F172A)
private val AccentCyan = Color(0xFF06B6D4)
private val TextMuted = Color(0xFF94A3B8)
private const val SECONDS_PER_MINUTE = 60

/**
 * SupportCodeDialog.kt - Emergency Support Passcode entry dialog.
 *
 * Owns the two pieces of state that must not drift apart:
 * - the latest [SupportVerificationResult] (shared by the email section and the entry field), and
 * - the rate-limit countdown, which is ticked locally because the ViewModel value is only a
 *   snapshot taken when the dialog was composed.
 *
 * Sign-in state is passed in by [SettingsDialogsContainer]; a signed-in user must never be shown
 * a dead "Sign in with Google" button.
 */
@Composable
fun SupportCodeDialog(
    deviceId: String,
    userEmail: String?,
    isLoggedIn: Boolean,
    onSignInWithGoogle: () -> Unit,
    cooldownRemainingSeconds: Long,
    onVerifyCode: (String) -> SupportVerificationResult,
    onApplyAction: suspend (SupportVerificationResult.Success) -> Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var verificationResult by remember { mutableStateOf<SupportVerificationResult?>(null) }
    var liveCooldownSeconds by remember { mutableStateOf(cooldownRemainingSeconds) }

    LaunchedEffect(cooldownRemainingSeconds) {
        if (cooldownRemainingSeconds > liveCooldownSeconds) {
            liveCooldownSeconds = cooldownRemainingSeconds
        }
        while (liveCooldownSeconds > 0) {
            delay(1000L)
            liveCooldownSeconds = (liveCooldownSeconds - 1).coerceAtLeast(0L)
        }
    }

    fun publishResult(result: SupportVerificationResult) {
        verificationResult = result
        if (result is SupportVerificationResult.RateLimited) {
            liveCooldownSeconds = maxOf(
                liveCooldownSeconds,
                result.remainingMinutes * SECONDS_PER_MINUTE
            )
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = DialogSurface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                SupportCodeHeader()

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = supportCodeGuidance(userEmail, isLoggedIn),
                    fontSize = 13.sp,
                    color = TextMuted,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(18.dp))

                SupportCodeEmailSection(
                    userEmail = userEmail,
                    isLoggedIn = isLoggedIn,
                    deviceId = deviceId,
                    onSignInWithGoogle = onSignInWithGoogle,
                    clipboardManager = clipboardManager,
                    context = context
                )

                Spacer(modifier = Modifier.height(20.dp))

                SupportCodeVerificationContent(
                    cooldownRemainingSeconds = liveCooldownSeconds,
                    verificationResult = verificationResult,
                    onVerifyCode = onVerifyCode,
                    onVerificationResult = ::publishResult,
                    onApplyAction = onApplyAction,
                    onDismiss = onDismiss,
                    context = context
                )

                Spacer(modifier = Modifier.height(10.dp))

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = "Close", color = TextMuted)
                }
            }
        }
    }
}

@Composable
private fun SupportCodeHeader() {
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(AccentCyan.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.Key,
            contentDescription = null,
            tint = AccentCyan,
            modifier = Modifier.size(28.dp)
        )
    }
    Spacer(modifier = Modifier.height(16.dp))
    Text(
        text = "Emergency Support Passcode",
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        color = Color.White,
        textAlign = TextAlign.Center
    )
}

private fun supportCodeGuidance(userEmail: String?, isLoggedIn: Boolean): String = when {
    !userEmail.isNullOrBlank() ->
        "Contact Support with your registered Gmail to receive your 6-digit emergency unlock code."
    isLoggedIn ->
        "You are signed in, but this account carries no email address. Send your Device ID to Support instead."
    else ->
        "Sign in with Google so Support can look up your Device ID, or send the Device ID below directly."
}
