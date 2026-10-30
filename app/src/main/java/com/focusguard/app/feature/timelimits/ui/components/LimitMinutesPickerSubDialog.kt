package com.focusguard.app.feature.timelimits.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.focusguard.app.core.model.AppInfo
import com.focusguard.app.ui.components.AppIconView

import com.focusguard.app.core.common.validator.InputValidator
import com.focusguard.app.core.common.validator.ValidationError
import com.focusguard.app.core.common.validator.ValidationResult

private val Navy800   = Color(0xFF0E2419)
private val Navy700   = Color(0xFF163827)
private val Purple400 = Color(0xFF22C55E)
private val Navy900   = Color(0xFF07140D)
private val White60   = Color(0x99FFFFFF)
private val Rose400   = Color(0xFFFB7185)

/**
 * =====================================================================
 * LimitMinutesPickerSubDialog.kt - Minutes Selector Modal Dialog
 * =====================================================================
 *
 * ## 1. Yeh File Kya Karti Hai? (Core Responsibility)
 * Yeh Compose modal dialog kisi specific app ke liye daily allowed minutes choose karne
 * ke liye use hota hai. Isme presets (15m, 30m, 60m, 90m), visual Slider (5m to 180m),
 * aur Custom numeric text input (1 to 1440 minutes) teeno options available hain.
 *
 * ## 2. Core UI Components:
 * - `Preset Quick Chips`: 15m, 30m, 60m, 90m, aur Custom toggle buttons.
 * - `Interactive Slider`: 5m se 180m tak smooth dragging slider with 5-minute step intervals.
 * - `Custom TextField`: Manual input mode with [InputValidator.validateDailyLimitMinutes] integration.
 * - `Action Buttons`: Cancel aur Save button with `isSubmitting` double-click debounce protection.
 *
 * ## 3. Validation & Edge Cases (Senior Dev Perspective):
 * - `customValidation` strictly ensure karta hai ki user 0, negative, ya 1440 se zyada minutes
 *   enter na kare. Agar invalid input ho toh Save button disable rehta hai aur clean error text dikhta hai.
 * =====================================================================
 */
@Composable
fun LimitMinutesPickerSubDialog(
    app: AppInfo,
    chosenMinutes: Int,
    onMinutesChange: (Int) -> Unit,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var isCustomInput by remember { mutableStateOf(false) }
    var customMinutesText by remember { mutableStateOf(chosenMinutes.toString()) }

    val customParsedMins = customMinutesText.toIntOrNull()
    val customValidation = remember(customMinutesText) {
        if (customMinutesText.isBlank()) {
            ValidationResult.Invalid(ValidationError.Blank(fieldName = "Daily screen time limit"))
        } else if (customParsedMins == null) {
            ValidationResult.Invalid(
                ValidationError.OutOfRange(
                    fieldName = "Daily screen time limit",
                    min = 1,
                    max = 1440,
                    actual = 0
                )
            )
        } else {
            InputValidator.validateDailyLimitMinutes(customParsedMins)
        }
    }
    val isCustomError = isCustomInput && customValidation is ValidationResult.Invalid
    val customErrorMessage = (customValidation as? ValidationResult.Invalid)?.error?.message

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Navy800),
            border = BorderStroke(1.5.dp, Purple400.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AppIconView(packageName = app.packageName, size = 48.dp)
                Text(
                    text = "Daily Limit for ${app.appName}",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Text(
                    text = "$chosenMinutes Minutes / Day",
                    color = Purple400,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 24.sp
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(15, 30, 60, 90).forEach { mins ->
                        val isSelected = !isCustomInput && chosenMinutes == mins
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) Purple400 else Navy700,
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    onMinutesChange(mins)
                                    customMinutesText = mins.toString()
                                    isCustomInput = false
                                }
                        ) {
                            Text(
                                text = "${mins}m",
                                color = if (isSelected) Navy900 else Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 6.dp)
                            )
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isCustomInput) Purple400 else Navy700,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { isCustomInput = true }
                    ) {
                        Text(
                            text = "Custom",
                            color = if (isCustomInput) Navy900 else Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 6.dp)
                        )
                    }
                }

                if (isCustomInput) {
                    OutlinedTextField(
                        value = customMinutesText,
                        onValueChange = { text ->
                            val filtered = text.filter { it.isDigit() }.take(4)
                            customMinutesText = filtered
                            filtered.toIntOrNull()?.let {
                                if (it in 1..1440) onMinutesChange(it)
                            }
                        },
                        label = { Text("Enter minutes (1–1440)", color = White60, fontSize = 12.sp) },
                        singleLine = true,
                        isError = isCustomError,
                        supportingText = if (isCustomError && customErrorMessage != null) {
                            { Text(customErrorMessage, color = Rose400, fontSize = 11.sp) }
                        } else null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Purple400,
                            unfocusedBorderColor = Color(0x33FFFFFF),
                            errorBorderColor = Rose400
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Slider(
                        value = chosenMinutes.toFloat(),
                        onValueChange = {
                            val mins = it.toInt()
                            onMinutesChange(mins)
                            customMinutesText = mins.toString()
                        },
                        valueRange = 5f..180f,
                        steps = 34,
                        colors = SliderDefaults.colors(
                            thumbColor = Purple400,
                            activeTrackColor = Purple400,
                            inactiveTrackColor = Navy700
                        )
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Cancel", color = White60)
                    }
                    var isSubmitting by remember { mutableStateOf(false) }
                    LaunchedEffect(customMinutesText, chosenMinutes) { isSubmitting = false }
                    Button(
                        onClick = {
                            if (!isSubmitting) {
                                isSubmitting = true
                                val finalMins = if (isCustomInput) (customParsedMins ?: chosenMinutes) else chosenMinutes
                                onConfirm(finalMins)
                            }
                        },
                        enabled = !isCustomError && !isSubmitting,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Purple400, contentColor = Navy900)
                    ) {
                        Text("Save", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}
