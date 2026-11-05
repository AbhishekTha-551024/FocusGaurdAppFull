package com.focusguard.app.feature.settings.ui.components

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusguard.app.security.SupportVerificationResult
import kotlinx.coroutines.launch

private val AccentGreen = Color(0xFF10B981)
private val AccentRose = Color(0xFFF43F5E)
private val TextMuted = Color(0xFF94A3B8)
private val TextSoft = Color(0xFFCBD5E1)

@Composable
internal fun SupportCodeApplyCard(
    success: SupportVerificationResult.Success,
    errorMessage: String?,
    onApplyAction: suspend (SupportVerificationResult.Success) -> Boolean,
    context: Context,
    onDismiss: () -> Unit,
    onFailure: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var isSubmitting by remember(success.codeFingerprint) { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(containerColor = AccentGreen.copy(alpha = 0.12f)),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = AccentGreen,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Verified Support Code!",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccentGreen
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Action: ${success.actionName}",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )

            if (success.targetPayload.isNotBlank()) {
                Text(
                    text = "Target: ${success.targetPayload}",
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = TextMuted
                )
            }

            Text(
                text = success.description,
                fontSize = 12.sp,
                color = TextSoft,
                modifier = Modifier.padding(top = 4.dp)
            )

            Text(
                text = "This code can be applied once. Reusing it requires a new code from support.",
                fontSize = 11.sp,
                color = TextMuted,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }

    errorMessage?.let { error ->
        Spacer(modifier = Modifier.height(10.dp))
        Text(text = error, fontSize = 12.sp, color = AccentRose, textAlign = TextAlign.Center)
    }

    Spacer(modifier = Modifier.height(16.dp))

    Button(
        onClick = {
            if (!isSubmitting) {
                isSubmitting = true
                scope.launch {
                    val applied = runCatching { onApplyAction(success) }
                        .getOrElse { throwable ->
                            context.showToastFailure(success.actionName, throwable.message)
                            isSubmitting = false
                            return@launch
                        }
                    if (applied) {
                        Toast.makeText(context, "${success.actionName} applied successfully!", Toast.LENGTH_LONG).show()
                        onDismiss()
                    } else {
                        onFailure(
                            "${success.actionName} did not fully complete. Some changes may already have " +
                                "taken effect. Check protection status before retrying or contact support."
                        )
                        isSubmitting = false
                    }
                }
            }
        },
        enabled = !isSubmitting,
        colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = if (isSubmitting) "Applying…" else "Confirm & Apply Unlock",
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}

private fun Context.showToastFailure(actionName: String, reason: String?) {
    Toast.makeText(this, "$actionName failed: ${reason ?: "unknown error"}", Toast.LENGTH_LONG).show()
}
