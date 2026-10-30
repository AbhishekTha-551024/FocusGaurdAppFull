/**
 * =====================================================================
 * FILE: MindfulAllowanceChips.kt
 * PACKAGE: com.focusguard.app.overlay
 * =====================================================================
 * 
 * 📌 OVERVIEW (YE FILE KYA KARTI HAI?):
 * Ye file Mindful Pause Intent Selection screen ka "Conscious Time Allowance Selector"
 * component hai. Jab user kisi mindful app ko open karne ka intentional reason select
 * ya type kar leta hai, toh ye chips user ko allow karti hain choose karna ki vo kitni
 * der ke liye app use karna chahta hai (1m, 2m, 5m, 10m, 15m).
 *
 * 🏗️ DESIGN & BEHAVIOR:
 * - Row of 5 Equal-Width Chips: Screen width ke hisaab se 5 chips horizontally
 *   evenly distribute hoti hain (Modifier.weight(1f)).
 * - Visual Feedback: Selected chip par vibrant Emerald background (EMERALD_500) aur
 *   dark contrasting text render hota hai; unselected chips par subtle dark transparent glass
 *   effect (0x16FFFFFF) rehta hai.
 * - Single Selection: State parent IntentSelectionView ke paas hoti hai, jo onSelectDuration
 *   callback ke through synchronize hoti hai.
 * =====================================================================
 */

package com.focusguard.app.overlay.mindful

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MindfulTimeAllowanceSelector(
    selectedDurationMinutes: Int,
    onSelectDuration: (Int) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Conscious Time Allowance:",
            fontSize = 12.sp,
            color = Color(0x88FFFFFF),
            fontWeight = FontWeight.Medium,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(1, 2, 5, 10, 15).forEach { mins ->
                val isSelected = selectedDurationMinutes == mins
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSelected) EMERALD_500 else Color(0x16FFFFFF),
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onSelectDuration(mins) }
                ) {
                    Box(
                        modifier = Modifier.padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${mins}m",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) Color(0xFF031A0F) else Color(0xCCFFFFFF)
                        )
                    }
                }
            }
        }
    }
}
