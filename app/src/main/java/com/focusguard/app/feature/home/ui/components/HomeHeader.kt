package com.focusguard.app.feature.home.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.LockClock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
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
import com.focusguard.app.core.designsystem.theme.*
import com.focusguard.app.core.model.AuthUser

/**
 * =========================================================================================
 * ARCHITECTURE COMPONENT: Top Navigation Header (Home Feature Presentation Layer)
 * =========================================================================================
 *
 * PURPOSE & RESPONSIBILITIES:
 * 1. App Identity & Status Anchor:
 *    - Renders the primary application logo, title ("FocusGuard"), and shield subtitle.
 *
 * 2. Deep Focus Session Quick-Trigger:
 *    - Features a high-contrast emerald "Focus" pill button that immediately invokes
 *      [onNavigateToFocus], launching the Deep Focus (#blockit) countdown setup.
 *
 * 3. Authentication & Cloud Security Shortcut:
 *    - Displays dynamic account indicator based on [AuthUser]:
 *      * Authenticated: Shows user initial avatar badge with green border.
 *      * Unauthenticated / Guest: Shows neutral account icon.
 *    - Clicking the avatar directly opens [onNavigateToSettings] where the user can manage
 *      cloud backup, partner invites, or sign-in status.
 * =========================================================================================
 */
@Composable
fun HomeHeader(
    authUser: AuthUser?,
    onNavigateToSettings: () -> Unit,
    onNavigateToFocus: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Indigo400.copy(alpha = 0.15f))
                    .border(1.dp, Indigo400.copy(alpha = 0.4f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = Indigo400,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column {
                Text(
                    text = "FocusGuard",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Digital Wellbeing Shield",
                    color = White60,
                    fontSize = 11.5.sp
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Google Account Indicator / Shortcut to Settings
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(if (authUser != null) Color(0xFF143823) else Forest800)
                    .border(
                        1.dp,
                        if (authUser != null) Emerald500.copy(alpha = 0.5f) else Color(0x3322C55E),
                        CircleShape
                    )
                    .clickable { onNavigateToSettings() },
                contentAlignment = Alignment.Center
            ) {
                if (authUser != null) {
                    Text(
                        text = authUser.displayInitial,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = "Account",
                        tint = White60,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Focus Button
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0x2222C55E),
                border = BorderStroke(1.dp, Color(0x6622C55E)),
                modifier = Modifier
                    .height(38.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { onNavigateToFocus() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.LockClock,
                        contentDescription = "Deep Focus",
                        tint = Emerald400,
                        modifier = Modifier.size(17.dp)
                    )
                    Text(
                        text = "Focus",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.4.sp
                    )
                }
            }
        }
    }
}
