package com.focusguard.app.ui.recovery

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusguard.app.core.designsystem.theme.Amber300
import com.focusguard.app.core.designsystem.theme.Amber400
import com.focusguard.app.core.designsystem.theme.Emerald400
import com.focusguard.app.core.designsystem.theme.Emerald500
import com.focusguard.app.core.designsystem.theme.Forest700
import com.focusguard.app.core.designsystem.theme.Forest800
import com.focusguard.app.core.designsystem.theme.Forest950
import com.focusguard.app.core.designsystem.theme.ForestBorder
import com.focusguard.app.core.designsystem.theme.White60
import com.focusguard.app.core.designsystem.theme.White90

/**
 * The action half of the recovery shield: the settings button, the exact steps that follow it, and
 * the OEM "keep it on after restart" card.
 *
 * Split out of [AccessibilityRecoveryScreen] so the screen file stays within the 300-line limit and
 * so the cause-independent layout is separate from the cause-specific wording.
 *
 * The button deliberately does NOT claim to restore anything on its own: it only opens Android's
 * Accessibility screen. The toggle still has to be switched on by hand, which the steps below spell
 * out. (Silent programmatic restore happens earlier, in AccessibilityRecoveryHelper.)
 */
@Composable
fun RecoveryRestoreActions(
    oemName: String,
    isProblematicOem: Boolean,
    onRestoreClick: () -> Unit,
    onOemFixClick: () -> Unit
) {
    Spacer(modifier = Modifier.height(28.dp))

    Button(
        onClick = onRestoreClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Emerald500,
            contentColor = Forest950
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "OPEN ACCESSIBILITY SETTINGS",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                letterSpacing = 0.5.sp
            )
        }
    }

    Spacer(modifier = Modifier.height(10.dp))

    Text(
        text = "Opens Settings. Turn the FocusGuard toggle ON there and tap Allow.",
        color = White60,
        fontSize = 12.sp,
        textAlign = TextAlign.Center
    )

    Spacer(modifier = Modifier.height(24.dp))

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Forest800),
        border = BorderStroke(1.dp, ForestBorder)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = "Quick 3-Step Setup",
                color = Emerald400,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))

            InstructionRow(number = "1", text = "Tap 'FocusGuard Screen Protection' in the Accessibility list")
            Spacer(modifier = Modifier.height(8.dp))
            InstructionRow(number = "2", text = "Turn ON the 'Use FocusGuard' toggle")
            Spacer(modifier = Modifier.height(8.dp))
            InstructionRow(number = "3", text = "Tap 'Allow' on the system confirmation prompt")
        }
    }

    if (isProblematicOem) {
        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Forest700.copy(alpha = 0.6f)),
            border = BorderStroke(1.dp, Amber400.copy(alpha = 0.3f))
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = Amber400,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Keep On After Restart ($oemName)",
                        color = Amber400,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "To prevent $oemName from disabling Accessibility whenever you restart or power off the phone, allow 'High background power consumption' & 'Autostart'.",
                    color = White60,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = onOemFixClick,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Amber300),
                    border = BorderStroke(1.dp, Amber400.copy(alpha = 0.6f))
                ) {
                    Text(
                        text = "Open $oemName Background Settings",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun InstructionRow(number: String, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(Emerald500.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number,
                color = Emerald400,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            color = White90,
            fontSize = 13.sp,
            lineHeight = 18.sp
        )
    }
}
