package com.focusguard.app.feature.auth.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Forest700    = Color(0xFF163827)
private val Emerald500   = Color(0xFF22C55E)
private val White90      = Color(0xE6FFFFFF)
private val White60      = Color(0x99FFFFFF)

@Composable
fun AccountLoggedOutSection(
    isLoading: Boolean,
    onSignInWithGoogle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Emerald500.copy(alpha = 0.15f))
                    .border(1.dp, Emerald500.copy(alpha = 0.35f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AccountCircle,
                    contentDescription = null,
                    tint = Emerald500,
                    modifier = Modifier.size(28.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Account & Cloud Security",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Sign in to bind recovery codes, protect uninstall settings, and sync focus profiles.",
                    color = White60,
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp
                )
            }
        }

        // Sign in button
        Button(
            onClick = onSignInWithGoogle,
            enabled = !isLoading,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Forest700,
                contentColor = Color.White
            ),
            border = androidx.compose.foundation.BorderStroke(1.dp, Emerald500.copy(alpha = 0.4f)),
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = Emerald500,
                    strokeWidth = 2.dp
                )
                Spacer(Modifier.width(8.dp))
                Text("Connecting...", fontSize = 13.sp, color = White90)
            } else {
                Surface(
                    shape = CircleShape,
                    color = Color.White,
                    modifier = Modifier.size(20.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "G",
                            fontWeight = FontWeight.Black,
                            fontSize = 12.sp,
                            color = Color(0xFF4285F4)
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "Sign in with Google",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
        }
    }
}
