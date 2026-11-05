package com.focusguard.app.service.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
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

@Composable
internal fun BootCoverInitializingSection(pulseScale: Float) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(110.dp)
            .scale(pulseScale)
    ) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(Emerald500.copy(alpha = 0.15f))
        )
        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(CircleShape)
                .background(Emerald500.copy(alpha = 0.25f))
                .border(2.dp, Emerald500.copy(alpha = 0.6f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = "Securing Device",
                tint = Emerald400,
                modifier = Modifier.size(36.dp)
            )
        }
    }

    Spacer(modifier = Modifier.height(28.dp))

    Surface(
        color = Emerald500.copy(alpha = 0.12f),
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Emerald500.copy(alpha = 0.35f))
    ) {
        Text(
            text = "BOOT VERIFICATION ACTIVE",
            color = Emerald400,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
        )
    }

    Spacer(modifier = Modifier.height(20.dp))

    Text(
        text = "FocusGuard Shield",
        color = White90,
        fontSize = 24.sp,
        fontWeight = FontWeight.ExtraBold,
        textAlign = TextAlign.Center
    )

    Spacer(modifier = Modifier.height(8.dp))

    Text(
        text = "Securing your device after restart...\nStarting protection services.",
        color = White60,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        textAlign = TextAlign.Center
    )

    Spacer(modifier = Modifier.height(32.dp))

    CircularProgressIndicator(
        color = Emerald500,
        strokeWidth = 3.dp,
        modifier = Modifier.size(36.dp)
    )

    Spacer(modifier = Modifier.height(16.dp))

    Text(
        text = "Please wait",
        color = White60.copy(alpha = 0.6f),
        fontSize = 12.sp,
        letterSpacing = 0.8.sp
    )
}

@Composable
internal fun BootCoverFallbackSection(
    pulseScale: Float,
    onOpenAccessibilitySettings: () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(110.dp)
            .scale(pulseScale)
    ) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(Rose400.copy(alpha = 0.15f))
        )
        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(CircleShape)
                .background(Rose400.copy(alpha = 0.25f))
                .border(2.dp, Rose400.copy(alpha = 0.6f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Action Required",
                tint = Rose400,
                modifier = Modifier.size(36.dp)
            )
        }
    }

    Spacer(modifier = Modifier.height(24.dp))

    Surface(
        color = Rose400.copy(alpha = 0.15f),
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Rose400.copy(alpha = 0.4f))
    ) {
        Text(
            text = "ACTION REQUIRED",
            color = Rose400,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
        )
    }

    Spacer(modifier = Modifier.height(16.dp))

    Text(
        text = "Turn On Accessibility",
        color = White90,
        fontSize = 24.sp,
        fontWeight = FontWeight.ExtraBold,
        textAlign = TextAlign.Center
    )

    Spacer(modifier = Modifier.height(8.dp))

    Text(
        text = "Accessibility service has not connected after restart. Tap the button below to restore your protection shield.",
        color = White60,
        fontSize = 13.sp,
        lineHeight = 19.sp,
        textAlign = TextAlign.Center
    )

    Spacer(modifier = Modifier.height(28.dp))

    Button(
        onClick = onOpenAccessibilitySettings,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
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
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "TURN ON ACCESSIBILITY",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                letterSpacing = 0.5.sp
            )
        }
    }
}

