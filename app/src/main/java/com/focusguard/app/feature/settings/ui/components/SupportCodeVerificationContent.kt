package com.focusguard.app.feature.settings.ui.components

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusguard.app.core.common.validator.InputValidator
import com.focusguard.app.core.common.validator.ValidationResult
import com.focusguard.app.security.SupportVerificationResult
import kotlinx.coroutines.launch

private val AccentCyan = Color(0xFF06B6D4)
private val AccentGreen = Color(0xFF10B981)
private val AccentRose = Color(0xFFF43F5E)
private val SurfaceSlate = Color(0xFF1E293B)
private val BorderSlate = Color(0xFF334155)
private val TextMuted = Color(0xFF94A3B8)
private val TextSoft = Color(0xFFCBD5E1)
private const val PASSCODE_LENGTH = 6

/**
 * SupportCodeVerificationContent.kt
 * =================================
 * The passcode entry half of the Emergency Support Passcode dialog.
 *
 * Responsibilities:
 * - [PasscodeLockCard]: shows the rate-limit lockout while [cooldownRemainingSeconds] > 0.
 * - [PasscodeInputCard]: digit-filtered entry, single verification per distinct code, and
 *   honest feedback for InvalidCode / AlreadyUsed / RateLimited.
 * - [VerificationSuccessCard]: describes the unlocked action and applies it, reporting the
 *   real result of the operation instead of assuming success.
 *
 * State rule: the parent dialog owns the verification result so that recompositions and the
 * countdown cannot drift apart from what the user is seeing.
 */
@Composable
fun SupportCodeVerificationContent(
    cooldownRemainingSeconds: Long,
    verificationResult: SupportVerificationResult?,
    onVerifyCode: (String) -> SupportVerificationResult,
    onVerificationResult: (SupportVerificationResult) -> Unit,
    onApplyAction: suspend (SupportVerificationResult.Success) -> Boolean,
    onDismiss: () -> Unit,
    context: Context,
    modifier: Modifier = Modifier
) {
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (cooldownRemainingSeconds > 0) {
            PasscodeLockCard(cooldownRemainingSeconds)
            return@Column
        }

        val success = verificationResult as? SupportVerificationResult.Success
        if (success != null) {
            VerificationSuccessCard(
                success = success,
                errorMessage = errorMessage,
                onApplyAction = onApplyAction,
                context = context,
                onDismiss = onDismiss,
                onFailure = { errorMessage = it }
            )
        } else {
            PasscodeInputCard(
                verificationResult = verificationResult,
                errorMessage = errorMessage,
                onClearError = { errorMessage = null },
                onShowError = { errorMessage = it },
                onVerifyCode = onVerifyCode,
                onVerificationResult = onVerificationResult
            )
        }
    }
}

@Composable
private fun PasscodeLockCard(cooldownRemainingSeconds: Long) {
    val remainingMins = (cooldownRemainingSeconds / 60) + 1
    Card(
        colors = CardDefaults.cardColors(containerColor = AccentRose.copy(alpha = 0.12f)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = AccentRose,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Too many wrong attempts. Code entry locked for $remainingMins more minute(s).",
                fontSize = 12.sp,
                color = AccentRose
            )
        }
    }
}

@Composable
private fun PasscodeInputCard(
    verificationResult: SupportVerificationResult?,
    errorMessage: String?,
    onClearError: () -> Unit,
    onShowError: (String) -> Unit,
    onVerifyCode: (String) -> SupportVerificationResult,
    onVerificationResult: (SupportVerificationResult) -> Unit
) {
    var inputCode by remember { mutableStateOf("") }
    // One verification per distinct code: typing the 6th digit auto-submits, so the button must
    // not burn a second wrong-attempt slot for the very same value.
    var attemptedCode by remember { mutableStateOf<String?>(null) }

    fun submit(code: String) {
        attemptedCode = code
        when (val validation = InputValidator.validateSupportCode(code)) {
            is ValidationResult.Invalid -> onShowError(validation.error.message)
            ValidationResult.Valid -> {
                onClearError()
                val result = onVerifyCode(code)
                onVerificationResult(result)
                when (result) {
                    is SupportVerificationResult.InvalidCode ->
                        onShowError("Invalid code for this device and date. Re-check the Device ID with support.")
                    is SupportVerificationResult.AlreadyUsed ->
                        onShowError("This code has already been used on this device. Ask support for a new one.")
                    is SupportVerificationResult.RateLimited -> onShowError(result.lockMessage())
                    is SupportVerificationResult.Success -> onClearError()
                }
            }
        }
    }

    OutlinedTextField(
        value = inputCode,
        onValueChange = { raw ->
            if (raw.length <= PASSCODE_LENGTH && raw.all { it.isDigit() }) {
                inputCode = raw
                onClearError()
                if (raw.length == PASSCODE_LENGTH && raw != attemptedCode) submit(raw)
            }
        },
        label = { Text("Enter 6-Digit Passcode") },
        placeholder = { Text("e.g. 582910") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        isError = errorMessage != null,
        modifier = Modifier.fillMaxWidth(),
        textStyle = LocalTextStyle.current.copy(
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center,
            color = Color.White
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = AccentCyan,
            unfocusedBorderColor = BorderSlate,
            focusedLabelColor = AccentCyan,
            unfocusedLabelColor = TextMuted,
            focusedContainerColor = SurfaceSlate,
            unfocusedContainerColor = SurfaceSlate
        ),
        shape = RoundedCornerShape(12.dp)
    )

    errorMessage?.let { error ->
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = error,
            fontSize = 12.sp,
            color = AccentRose,
            textAlign = TextAlign.Center
        )
    }

    Spacer(modifier = Modifier.height(16.dp))

    val alreadyChecked = inputCode.isNotEmpty() && inputCode == attemptedCode &&
        verificationResult !is SupportVerificationResult.Success
    Button(
        onClick = {
            if (inputCode.length == PASSCODE_LENGTH) {
                if (alreadyChecked) onShowError("That code was already checked. Correct it or ask support for a new one.")
                else submit(inputCode)
            } else {
                onShowError("Please enter all $PASSCODE_LENGTH digits.")
            }
        },
        enabled = inputCode.length == PASSCODE_LENGTH,
        colors = ButtonDefaults.buttonColors(containerColor = AccentCyan),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = if (alreadyChecked) "Re-check Passcode" else "Verify Passcode",
            fontWeight = FontWeight.Bold,
            color = Color(0xFF07090E)
        )
    }
}

@Composable
private fun VerificationSuccessCard(
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
                        onFailure("${success.actionName} could not be applied. Nothing was changed — try again or contact support.")
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

private fun SupportVerificationResult.RateLimited.lockMessage(): String =
    "Too many wrong attempts. Try again in $remainingMinutes minute(s)."

private fun Context.showToastFailure(actionName: String, reason: String?) {
    Toast.makeText(this, "$actionName failed: ${reason ?: "unknown error"}", Toast.LENGTH_LONG).show()
}
