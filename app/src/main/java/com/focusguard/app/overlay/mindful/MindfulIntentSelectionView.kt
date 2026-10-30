/**
 * =====================================================================
 * FILE: MindfulIntentSelectionView.kt
 * PACKAGE: com.focusguard.app.overlay
 * =====================================================================
 * 
 * 📌 OVERVIEW (YE FILE KYA KARTI HAI?):
 * Ye file Mindful Pause overlay ka "Phase 2: Intent Journaling & Conscious Choice UI" hai.
 * Breathing complete hone ke baad, user ko autopilot scroll karne se rokne ke liye
 * ye screen poochhti hai: "Why do you need this app?" ya "Why again this time?".
 * User ya toh curated presets me se purpose select karta hai ya custom conscious reason
 * likhta hai, jo AI/NLP validation engine se verify hota hai.
 *
 * 🏗️ ARCHITECTURE & VALIDATION ENGINE:
 * - Intelligent Awareness Context: Agar user ne abhi 2 minute pehle app band ki thi
 *   aur fir open ki (isReturning == true), toh screen nudge deti hai:
 *   "You just closed Instagram 2m ago · Open #4 today".
 * - Preset vs Custom Intent: Pre-configured intent chips (Work, Communication, Quick Check)
 *   ya custom multi-line text input field.
 * - IntentValidationEngine Integration: Custom text ko real-time validate karta hai
 *   (TooShort, TooFewWords, Gibberish/Spam keyboard mash detection).
 * - MindfulTimeAllowanceSelector: Conscious duration choose karne ki facility (1m - 15m).
 * - Dual Exit Strategy:
 *     1. "Continue with Intent": Access grant karta hai chosen duration ke liye.
 *     2. "Walk Away (Save Focus)": Conscious triumph, app launch cancel karke user ko Home bhejta hai.
 * =====================================================================
 */

package com.focusguard.app.overlay.mindful

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusguard.app.security.IntentValidationEngine
import com.focusguard.app.security.IntentValidationResult
import com.focusguard.app.security.PresetIntent

@Composable
fun IntentSelectionView(
    appName: String,
    isReturning: Boolean,
    minutesSinceLastSession: Int,
    todayOpenCount: Int,
    presets: List<PresetIntent>,
    selectedPresetId: String?,
    customIntentText: String,
    selectedDurationMinutes: Int,
    onSelectPreset: (PresetIntent) -> Unit,
    onSelectCustom: () -> Unit,
    onCustomTextChanged: (String) -> Unit,
    onSelectDuration: (Int) -> Unit,
    onConfirm: () -> Unit,
    onWalkAway: () -> Unit
) {
    val scrollState = rememberScrollState()

    val isPresetActive = selectedPresetId != null
    val customValidation = remember(customIntentText) {
        IntentValidationEngine.validateIntent(customIntentText)
    }

    val isFormValid = isPresetActive || customValidation is IntentValidationResult.Valid

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 24.dp, vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header
        Surface(
            shape = CircleShape,
            color = Color(0x2222C55E),
            border = BorderStroke(1.dp, Color(0x4422C55E)),
            modifier = Modifier.size(52.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.SelfImprovement,
                    contentDescription = null,
                    tint = EMERALD_400,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = if (isReturning) "Why again this time?" else "Why do you need $appName?",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(4.dp))

        Text(
            text = if (isReturning) {
                if (minutesSinceLastSession > 0)
                    "You closed $appName ${minutesSinceLastSession}m ago · Open #$todayOpenCount today"
                else
                    "You just closed $appName · Open #$todayOpenCount today"
            } else {
                "Set an intentional purpose before opening."
            },
            fontSize = 13.sp,
            color = if (isReturning) EMERALD_400 else Color(0xAAFFFFFF),
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(24.dp))

        // Preset Chips
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            presets.forEach { preset ->
                val isSelected = selectedPresetId == preset.id
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) Color(0x2622C55E) else Color(0x11FFFFFF),
                    border = BorderStroke(
                        width = if (isSelected) 1.5.dp else 1.dp,
                        color = if (isSelected) EMERALD_500 else Color(0x22FFFFFF)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSelectPreset(preset) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = preset.label,
                            color = if (isSelected) Color.White else Color(0xDDFFFFFF),
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier.weight(1f)
                        )
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = EMERALD_400,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // Custom reason selector
            val isCustomActive = selectedPresetId == null
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isCustomActive) Color(0x2622C55E) else Color(0x11FFFFFF),
                border = BorderStroke(
                    width = if (isCustomActive) 1.5.dp else 1.dp,
                    color = if (isCustomActive) EMERALD_500 else Color(0x22FFFFFF)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onSelectCustom() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "✍️ Custom Purpose (Type below)",
                        color = if (isCustomActive) Color.White else Color(0xDDFFFFFF),
                        fontSize = 13.sp,
                        fontWeight = if (isCustomActive) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.weight(1f)
                    )
                    if (isCustomActive) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = EMERALD_400,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // Custom text box (shown if custom active)
        if (selectedPresetId == null) {
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = customIntentText,
                onValueChange = onCustomTextChanged,
                placeholder = {
                    Text("e.g. Reply to client regarding project deadline", color = Color(0x55FFFFFF), fontSize = 13.sp)
                },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = EMERALD_500,
                    unfocusedBorderColor = Color(0x33FFFFFF),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = EMERALD_400
                ),
                shape = RoundedCornerShape(12.dp),
                minLines = 2,
                maxLines = 3
            )

            // Validation Helper Text
            Spacer(Modifier.height(6.dp))
            val helperColor = when (customValidation) {
                is IntentValidationResult.Valid -> EMERALD_400
                is IntentValidationResult.TooShort -> AMBER_400
                is IntentValidationResult.TooFewWords -> AMBER_400
                is IntentValidationResult.GibberishDetected -> ROSE_400
            }

            val helperText = when (customValidation) {
                is IntentValidationResult.Valid -> "✓ Intent verified (Conscious purpose)"
                is IntentValidationResult.TooShort -> "At least ${customValidation.minRequired} characters required (${customValidation.currentChars}/${customValidation.minRequired})"
                is IntentValidationResult.TooFewWords -> "At least ${customValidation.minRequired} words required (${customValidation.currentWords}/${customValidation.minRequired})"
                is IntentValidationResult.GibberishDetected -> customValidation.message
            }

            Text(
                text = helperText,
                color = helperColor,
                fontSize = 11.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Start
            )
        }

        Spacer(Modifier.height(20.dp))

        // Time Allowance Selector
        MindfulTimeAllowanceSelector(
            selectedDurationMinutes = selectedDurationMinutes,
            onSelectDuration = onSelectDuration
        )

        Spacer(Modifier.height(28.dp))

        // Action Buttons
        Button(
            onClick = onConfirm,
            enabled = isFormValid,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = EMERALD_500,
                disabledContainerColor = Color(0x22FFFFFF),
                contentColor = Color(0xFF031A0F),
                disabledContentColor = Color(0x44FFFFFF)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = "Continue with Intent (${selectedDurationMinutes}m)",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }

        Spacer(Modifier.height(10.dp))

        OutlinedButton(
            onClick = onWalkAway,
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = ROSE_400
            ),
            border = BorderStroke(1.dp, Color(0x33FB7185)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "🛑 Walk Away (Save Focus)",
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp
            )
        }
    }
}
