package com.focusguard.app.feature.settings.ui.components

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SupportCodeEmailSection(
    userEmail: String?,
    isLoggedIn: Boolean,
    deviceId: String,
    onSignInWithGoogle: () -> Unit,
    clipboardManager: ClipboardManager,
    context: Context,
    modifier: Modifier = Modifier
) {
    var showOfflineDeviceId by remember { mutableStateOf(false) }
    // A signed-in account without an email must never be offered a redundant sign-in button.
    val accountStatusCopy = if (isLoggedIn) {
        "Signed in, but no email is attached to this account. Send your Device ID to Support instead."
    } else {
        "Link your Google account so Support can look up your device by Gmail."
    }

    Column(modifier = modifier.fillMaxWidth()) {
        if (!userEmail.isNullOrBlank()) {
            // Registered Email Display Card with Copy
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF1E293B))
                    .border(1.dp, Color(0xFF334155), RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Email,
                        contentDescription = null,
                        tint = Color(0xFF22C55E),
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = "REGISTERED GMAIL",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF64748B),
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = userEmail,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                FilledTonalIconButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(userEmail))
                        Toast.makeText(context, "Email copied!", Toast.LENGTH_SHORT).show()
                    },
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = Color(0xFF22C55E).copy(alpha = 0.15f),
                        contentColor = Color(0xFF22C55E)
                    ),
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ContentCopy,
                        contentDescription = "Copy Email",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        } else {
            // Prompt user to sign in
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = accountStatusCopy,
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8),
                        textAlign = TextAlign.Center
                    )
                    if (!isLoggedIn) {
                        Button(
                            onClick = onSignInWithGoogle,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E)),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Icon(Icons.Default.AccountCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Sign in with Google", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Expandable offline Device ID fallback
        TextButton(
            onClick = { showOfflineDeviceId = !showOfflineDeviceId },
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp)
        ) {
            Text(
                text = if (showOfflineDeviceId) "Hide Offline Device ID ▲" else "Offline Fallback: Show Device ID ▼",
                fontSize = 11.sp,
                color = Color(0xFF64748B)
            )
        }

        if (showOfflineDeviceId) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF131C2E))
                    .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "DEVICE ID: $deviceId",
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF06B6D4)
                )
                IconButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(deviceId))
                        Toast.makeText(context, "Device ID copied!", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ContentCopy,
                        contentDescription = "Copy Device ID",
                        tint = Color(0xFF06B6D4),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}
