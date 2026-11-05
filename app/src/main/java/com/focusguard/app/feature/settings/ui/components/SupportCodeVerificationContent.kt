package com.focusguard.app.feature.settings.ui.components

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
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
import com.focusguard.app.core.designsystem.theme.RecoveryAccent
import com.focusguard.app.core.designsystem.theme.RecoveryBorder
import com.focusguard.app.core.designsystem.theme.RecoveryMuted
import com.focusguard.app.core.designsystem.theme.RecoverySurfaceRaised
import com.focusguard.app.security.SupportVerificationResult
import kotlinx.coroutines.launch

private val AccentGreen = Color(0xFF10B981)
private val AccentRose = Color(0xFFF43F5E)
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
 * - [SupportCodeApplyCard]: describes the unlocked action and applies it.
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
            SupportCodeApplyCard(
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

    val submit: (String) -> Unit = { code ->
        attemptedCode = code
        when (val validation = InputValidator.validateSupportCode(code)) {
            is ValidationResult.Invalid -> onShowError(validation.error.message)
            ValidationResult.Valid -> {
                onClearError()
                val result = onVerifyCode(code)
                onVerificationResult(result)
                when (result) {
                    is SupportVerificationResult.InvalidCode ->
                        onShowError("Invalid code for this device and date. Check the passcode and try again.")
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
            focusedBorderColor = RecoveryAccent,
            unfocusedBorderColor = RecoveryBorder,
            focusedLabelColor = RecoveryAccent,
            unfocusedLabelColor = RecoveryMuted,
            focusedContainerColor = RecoverySurfaceRaised,
            unfocusedContainerColor = RecoverySurfaceRaised
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
        colors = ButtonDefaults.buttonColors(containerColor = RecoveryAccent),
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

private fun SupportVerificationResult.RateLimited.lockMessage(): String =
    "Too many wrong attempts. Try again in $remainingMinutes minute(s)."
