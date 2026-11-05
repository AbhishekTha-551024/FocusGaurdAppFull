package com.focusguard.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.focusguard.app.core.designsystem.theme.*

private val CardBg = Forest800

/**
 * Minimal Hard Lock Duration Dialog — custom days only, no presets.
 */
@Composable
fun UninstallProtectionDurationDialog(
    onConfirm: (days: Int) -> Unit,
    onDismiss: () -> Unit
) {
    var daysText by remember { mutableStateOf("") }
    val parsedDays = daysText.toIntOrNull()
    val validation = if (parsedDays != null) {
        com.focusguard.app.core.common.validator.InputValidator.validateUninstallDays(parsedDays)
    } else null
    val isValid = validation is com.focusguard.app.core.common.validator.ValidationResult.Valid
    val errorMessage = when (validation) {
        is com.focusguard.app.core.common.validator.ValidationResult.Invalid -> validation.error.message
        else -> null
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.75f))
                .padding(28.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = CardBg),
                border = androidx.compose.foundation.BorderStroke(1.dp, Rose400.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(Rose400.copy(alpha = 0.12f))
                            .border(1.dp, Rose400.copy(alpha = 0.35f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = Rose400,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Lock Duration",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                        Text(
                            text = "How many days should uninstall protection stay locked?\nRemoving it early requires completing the Accountability Challenge.",
                            color = White60,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp
                        )
                    }

                    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedTextField(
                            value = daysText,
                            onValueChange = { input ->
                                if (input.all { it.isDigit() } && input.length <= 2) daysText = input
                            },
                            label = { Text("Days (1 – 30)", color = White60) },
                            placeholder = { Text("e.g. 14", color = White40) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            isError = daysText.isNotEmpty() && !isValid,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Rose400,
                                unfocusedBorderColor = Forest700,
                                errorBorderColor = Amber400
                            )
                        )
                        if (daysText.isNotEmpty() && errorMessage != null) {
                            Text(
                                text = errorMessage,
                                color = Amber400,
                                fontSize = 11.5.sp,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }
                    }

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        var isSubmitting by remember { mutableStateOf(false) }
                        LaunchedEffect(daysText) { isSubmitting = false }
                        Button(
                            onClick = {
                                if (!isSubmitting) {
                                    isSubmitting = true
                                    parsedDays?.let { onConfirm(it) }
                                }
                            },
                            enabled = isValid && !isSubmitting,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Rose400,
                                disabledContainerColor = Rose400.copy(alpha = 0.35f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = if (isValid) "Lock for $parsedDays Days" else "Enter Days",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }

                        TextButton(
                            onClick = onDismiss,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Cancel", color = White60, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}
