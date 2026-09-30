package com.focusguard.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

private val Forest800 = Color(0xFF0E2419)
private val Emerald500 = Color(0xFF22C55E)
private val Emerald400 = Color(0xFF4ADE80)
private val GoogleBlue = Color(0xFF4285F4)
private val White70 = Color(0xB3FFFFFF)
private val White50 = Color(0x80FFFFFF)

/**
 * SignInRequiredDialog
 * ====================
 * Modal prompt displayed whenever an unauthenticated user attempts to configure or activate
 * advanced protection features (Accountability Challenge, Uninstall Protection, Focus Schedules,
 * Time Limits, Website Blocker, or Deep Focus).
 */
@Composable
fun SignInRequiredDialog(
    featureName: String,
    isLoading: Boolean,
    onSignInWithGoogle: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.75f))
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Forest800),
                border = BorderStroke(1.2.dp, Emerald500.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 26.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    // Glowing Icon Badge
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(Emerald500.copy(alpha = 0.12f))
                            .border(1.5.dp, Emerald500.copy(alpha = 0.4f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = Emerald400,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    // Title & Feature Tag
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Sign In Required",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Emerald500.copy(alpha = 0.15f),
                            border = BorderStroke(0.8.dp, Emerald500.copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = featureName,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Emerald400,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                            )
                        }
                    }

                    // Explanation
                    Text(
                        text = "Sign in with your Google account is required before using $featureName. This verifies your identity, enables cloud sync, and ensures your commitment cannot be easily bypassed.",
                        fontSize = 12.5.sp,
                        color = White70,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Google Sign-In Button
                    Surface(
                        onClick = onSignInWithGoogle,
                        enabled = !isLoading,
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, Color(0xFFDADCE0)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = GoogleBlue
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = "Signing in...",
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF3C4043)
                                )
                            } else {
                                // "G" Circle badge
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .background(Color.White),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "G",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 15.sp,
                                        color = GoogleBlue
                                    )
                                }
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = "Sign in with Google",
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF3C4043)
                                )
                            }
                        }
                    }

                    // Dismiss / Cancel Button
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Not Now",
                            color = White50,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}
