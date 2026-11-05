package com.focusguard.app.ui.recovery

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusguard.app.core.designsystem.theme.Emerald500
import com.focusguard.app.core.designsystem.theme.Forest900
import com.focusguard.app.core.designsystem.theme.Forest950
import com.focusguard.app.core.designsystem.theme.Rose400
import com.focusguard.app.core.designsystem.theme.White60
import com.focusguard.app.core.designsystem.theme.White90
import com.focusguard.app.utils.BatteryOptimizationHelper

/**
 * Recovery shield layout. All wording comes from [recoveryShieldCopy] so the badge, title and body
 * always agree about why the shield appeared; the action block lives in [RecoveryRestoreActions].
 */
@Composable
fun AccessibilityRecoveryScreen(
    cause: RecoveryCause,
    onRestoreClick: () -> Unit,
    onOemFixClick: () -> Unit,
    onAdminRestoreClick: () -> Unit
) {
    val scrollState = rememberScrollState()
    val copy = remember(cause) { recoveryShieldCopy(cause) }
    val isVivoOrProblematic = remember { BatteryOptimizationHelper.isProblematicOem() }
    val oemName = remember { BatteryOptimizationHelper.getOemDisplayName() }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
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
                        Color(0xFF041009)
                    )
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(100.dp)
                    .scale(pulseScale)
            ) {
                Box(
                    modifier = Modifier
                        .size(90.dp)
                        .clip(CircleShape)
                        .background(Rose400.copy(alpha = 0.15f))
                )
                Box(
                    modifier = Modifier
                        .size(70.dp)
                        .clip(CircleShape)
                        .background(Rose400.copy(alpha = 0.25f))
                        .border(2.dp, Rose400.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Protection Warning",
                        tint = Rose400,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Surface(
                color = Rose400.copy(alpha = 0.15f),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, Rose400.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Rose400)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = copy.badge,
                        color = Rose400,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = copy.title,
                color = White90,
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = copy.body,
                color = White60,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp)
            )

            // Admin removal needs its own activation action; the Accessibility setting cannot restore it.
            if (cause.requiresAdminReactivation) {
                Button(
                    onClick = onAdminRestoreClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 28.dp)
                        .height(58.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Emerald500,
                        contentColor = Forest950
                    )
                ) {
                    Text(
                        text = "REACTIVATE DEVICE ADMIN",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            } else if (!cause.isAdminBlock) {
                RecoveryRestoreActions(
                    oemName = oemName,
                    isProblematicOem = isVivoOrProblematic,
                    onRestoreClick = onRestoreClick,
                    onOemFixClick = onOemFixClick
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
