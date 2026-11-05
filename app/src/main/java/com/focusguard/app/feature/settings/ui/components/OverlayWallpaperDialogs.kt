package com.focusguard.app.feature.settings.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusguard.app.core.designsystem.theme.*
import com.focusguard.app.feature.settings.ui.SettingsUiState

private val Violet500 = Color(0xFF22C55E)

@Composable
fun OverlayCustomMessageDialog(
    initialMessage: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var message by remember { mutableStateOf(initialMessage) }
    val validation = if (message.isNotBlank()) {
        com.focusguard.app.core.common.validator.InputValidator.validateCustomMessage(message)
    } else null
    val isInvalid = validation is com.focusguard.app.core.common.validator.ValidationResult.Invalid
    val errorText = when (validation) {
        is com.focusguard.app.core.common.validator.ValidationResult.Invalid -> validation.error.message
        else -> null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Edit Block Message", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "This message is shown in huge glowing text whenever an app is blocked.",
                    color = White60,
                    fontSize = 13.sp
                )
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it },
                    placeholder = { Text("e.g. Go read a book instead!", color = White40, fontSize = 13.sp) },
                    singleLine = false,
                    maxLines = 3,
                    isError = isInvalid,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = White90,
                        focusedBorderColor = Violet500,
                        unfocusedBorderColor = Color(0x33FFFFFF),
                        focusedContainerColor = Forest950,
                        unfocusedContainerColor = Forest950,
                        errorBorderColor = Rose400
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                if (errorText != null) {
                    Text(
                        text = errorText,
                        color = Rose400,
                        fontSize = 11.5.sp
                    )
                }
            }
        },
        confirmButton = {
            var isSubmitting by remember { mutableStateOf(false) }
            LaunchedEffect(message) { isSubmitting = false }
            Button(
                onClick = {
                    if (!isSubmitting) {
                        isSubmitting = true
                        onConfirm(message.trim())
                    }
                },
                enabled = !isInvalid && !isSubmitting,
                colors = ButtonDefaults.buttonColors(containerColor = Violet500),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(if (message.isBlank()) "Reset to Default" else "Save", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = White60)
            }
        },
        containerColor = Forest800,
        shape = RoundedCornerShape(18.dp)
    )
}

@Composable
fun OverlayCountdownDialog(
    currentSeconds: Int,
    onSelectCountdown: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Select Countdown Timer", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Duration the block overlay stays visible before closing the app.",
                    color = White60,
                    fontSize = 13.sp
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(5, 10, 15, 30).forEach { sec ->
                        val isSelected = currentSeconds == sec
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) Amber400 else Forest700,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onSelectCountdown(sec) }
                        ) {
                            Text(
                                text = "${sec}s",
                                color = if (isSelected) Forest950 else Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 12.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = White60)
            }
        },
        containerColor = Forest800,
        shape = RoundedCornerShape(18.dp)
    )
}

@Composable
fun OverlayCountdownIncreaseWarningDialog(
    currentSeconds: Int,
    targetSeconds: Int,
    isChallengeActive: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = Amber400,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = "Increase Countdown to ${targetSeconds}s?",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "You are increasing the block overlay countdown from ${currentSeconds}s to ${targetSeconds}s.",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 13.sp
                )
                if (isChallengeActive) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Rose400.copy(alpha = 0.15f),
                        border = BorderStroke(0.8.dp, Rose400.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "⚠️ Challenge Protected: Because an Accountability Challenge is active, decreasing this countdown later will require completing your challenge!",
                            color = Rose400,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
                Text(
                    text = "Are you sure you want to lock in a longer ${targetSeconds}s countdown?",
                    color = White60,
                    fontSize = 12.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = Amber400)
            ) {
                Text("Yes, Increase to ${targetSeconds}s", color = Forest950, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = White60)
            }
        },
        containerColor = Forest800,
        shape = RoundedCornerShape(18.dp)
    )
}

@Composable
fun OverlayBehaviorSettingRow(
    title: String,
    subtitle: String,
    onEditClick: () -> Unit,
    onInfoClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEditClick() }
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(1.dp))
            Text(
                text = subtitle,
                color = White60,
                fontSize = 11.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            IconButton(onClick = onEditClick, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Edit, contentDescription = "Edit", tint = White60, modifier = Modifier.size(15.dp))
            }
            IconButton(onClick = onInfoClick, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Info, contentDescription = "Info", tint = White40, modifier = Modifier.size(15.dp))
            }
        }
    }
}
