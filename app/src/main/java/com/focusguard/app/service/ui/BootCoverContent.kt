package com.focusguard.app.service.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusguard.app.ui.theme.Forest900
import com.focusguard.app.ui.theme.Forest950
import com.focusguard.app.ui.theme.White60

/**
 * Full-screen Compose UI displayed during device startup cover shield.
 * Consumes all touches, provides settings recovery, and emergency support code entry in all states.
 */
@Composable
internal fun BootCoverContent(
    isFallback: Boolean,
    isUserUnlocked: Boolean,
    deviceId: String,
    supportStatus: SupportUiStatus,
    onOpenAccessibilitySettings: () -> Unit,
    onSubmitSupportCode: (String) -> Unit
) {
    var showSupportDialog by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Forest950,
                        Forest900,
                        Color(0xFF020B06)
                    )
                )
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { /* Consume all background touches */ },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (!isFallback) {
                BootCoverInitializingSection(pulseScale = pulseScale)
            } else {
                BootCoverFallbackSection(
                    pulseScale = pulseScale,
                    onOpenAccessibilitySettings = onOpenAccessibilitySettings
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Condition: Button shown in ALL states (initializing and fallback)
            OutlinedButton(
                onClick = { showSupportDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = White60
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, White60.copy(alpha = 0.35f))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Key,
                        contentDescription = "Support Code",
                        modifier = Modifier.size(18.dp),
                        tint = White60
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "ENTER SUPPORT CODE",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }

        if (showSupportDialog) {
            BootCoverSupportDialog(
                isUserUnlocked = isUserUnlocked,
                deviceId = deviceId,
                status = supportStatus,
                onSubmitCode = onSubmitSupportCode,
                onDismiss = { showSupportDialog = false }
            )
        }
    }
}
