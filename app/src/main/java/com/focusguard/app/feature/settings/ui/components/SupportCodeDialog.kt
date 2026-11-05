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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.focusguard.app.security.SupportVerificationResult
import com.focusguard.app.core.designsystem.theme.*
import kotlinx.coroutines.delay

private const val SECONDS_PER_MINUTE = 60

/**
 * SupportCodeDialog.kt - Emergency Support Passcode entry dialog.
 *
 * Owns the two pieces of state that must not drift apart:
 * - the latest [SupportVerificationResult] (shared by the email section and the entry field), and
 * - the rate-limit countdown, which is ticked locally because the ViewModel value is only a
 *   snapshot taken when the dialog was composed.
 *
 */
@Composable
fun SupportCodeDialog(
    userEmail: String?,
    cooldownRemainingSeconds: Long,
    onVerifyCode: (String) -> SupportVerificationResult,
    onApplyAction: suspend (SupportVerificationResult.Success) -> Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
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
            colors = CardDefaults.cardColors(containerColor = RecoverySurface)
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
                    text = supportCodeGuidance(userEmail),
                    fontSize = 13.sp,
                    color = White60,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(18.dp))

                SupportCodeEmailSection(
                    userEmail = userEmail
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
                    Text(text = "Close", color = White60)
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
            .background(Emerald500.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.Key,
            contentDescription = null,
            tint = Emerald500,
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

private fun supportCodeGuidance(userEmail: String?): String =
    if (userEmail.isNullOrBlank()) {
        "Enter your 6-digit emergency support passcode below."
    } else {
        "Use the registered email shown below to request a 6-digit emergency support passcode."
    }
