package com.focusguard.app.service.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusguard.app.ui.theme.Emerald400
import com.focusguard.app.ui.theme.Emerald500
import com.focusguard.app.ui.theme.Forest950
import com.focusguard.app.ui.theme.Rose400
import com.focusguard.app.ui.theme.White60
import com.focusguard.app.ui.theme.White90

/**
 * Full-screen modal overlay for entering 6-digit emergency support codes on the boot shield.
 * Uses an on-screen Compose numeric keypad to eliminate soft keyboard (IME) dependencies.
 */
@Composable
fun BootCoverSupportDialog(
    isUserUnlocked: Boolean,
    deviceId: String,
    status: SupportUiStatus,
    onSubmitCode: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var buffer by remember { mutableStateOf(DigitBuffer()) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Forest950.copy(alpha = 0.96f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { /* Consume touches behind dialog */ },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Emergency Unlock",
                color = White90,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            if (!isUserUnlocked) {
                // Direct Boot (CE storage locked)
                LockedDirectBootNotice(onDismiss = onDismiss)
                return@Column
            }

            if (deviceId.isNotBlank()) {
                Text(
                    text = "Device ID: $deviceId",
                    color = White60,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            DigitIndicatorRow(code = buffer.code)

            Spacer(modifier = Modifier.height(14.dp))

            StatusMessageView(status = status)

            Spacer(modifier = Modifier.height(18.dp))

            KeypadGrid(
                enabled = status !is SupportUiStatus.Verifying && status !is SupportUiStatus.Success,
                onDigit = { digit ->
                    if (buffer.length < 6) buffer = buffer.append(digit)
                },
                onBackspace = { buffer = buffer.backspace() }
            )

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = White60)
                ) {
                    Text(text = "CANCEL", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }

                Button(
                    onClick = {
                        if (buffer.isComplete && status !is SupportUiStatus.Verifying) {
                            onSubmitCode(buffer.code)
                        }
                    },
                    enabled = buffer.isComplete && status !is SupportUiStatus.Verifying && status !is SupportUiStatus.Success,
                    modifier = Modifier.weight(1.4f).height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Emerald500,
                        contentColor = Forest950
                    )
                ) {
                    if (status is SupportUiStatus.Verifying) {
                        CircularProgressIndicator(
                            color = Forest950,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp)
                        )
                    } else {
                        Text(text = "SUBMIT CODE", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun LockedDirectBootNotice(onDismiss: () -> Unit) {
    Spacer(modifier = Modifier.height(12.dp))
    Icon(imageVector = Icons.Default.Lock, contentDescription = null, tint = Rose400, modifier = Modifier.size(44.dp))
    Spacer(modifier = Modifier.height(12.dp))
    Text(
        text = "Device Storage Encrypted\n\nSupport code verification requires unlocking your phone with your PIN or pattern first.",
        color = White60,
        fontSize = 14.sp,
        textAlign = TextAlign.Center,
        lineHeight = 20.sp
    )
    Spacer(modifier = Modifier.height(24.dp))
    Button(
        onClick = onDismiss,
        colors = ButtonDefaults.buttonColors(containerColor = Emerald500, contentColor = Forest950),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text("BACK TO SHIELD", fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun StatusMessageView(status: SupportUiStatus) {
    when (status) {
        is SupportUiStatus.Idle -> Text(
            text = "Enter your 6-digit support code",
            color = White60,
            fontSize = 12.sp
        )
        is SupportUiStatus.Verifying -> Text(
            text = "Verifying code...",
            color = Emerald400,
            fontSize = 12.sp
        )
        is SupportUiStatus.Success -> Text(
            text = "Code verified! Protection deactivated.",
            color = Emerald400,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
        )
        is SupportUiStatus.InvalidCode -> Text(
            text = status.message,
            color = Rose400,
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )
        is SupportUiStatus.RateLimited -> Text(
            text = "Too many attempts. Try again in ${status.remainingMinutes} min.",
            color = Rose400,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp
        )
        is SupportUiStatus.AlreadyUsed -> Text(
            text = status.message,
            color = Rose400,
            fontSize = 12.sp
        )
        is SupportUiStatus.DeviceEncrypted -> Text(
            text = status.message,
            color = Rose400,
            fontSize = 12.sp
        )
        is SupportUiStatus.ExecutionError -> Text(
            text = status.message,
            color = Rose400,
            fontSize = 12.sp
        )
    }
}
