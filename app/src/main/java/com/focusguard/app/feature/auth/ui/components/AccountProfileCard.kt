package com.focusguard.app.feature.auth.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusguard.app.core.model.AuthUser

private val Forest800    = Color(0xFF0E2419)
private val Forest700    = Color(0xFF163827)
private val Emerald500   = Color(0xFF22C55E)
private val Success      = Color(0xFF22C55E)
private val White90      = Color(0xE6FFFFFF)
private val White60      = Color(0x99FFFFFF)

@Composable
fun AccountProfileCard(
    user: AuthUser?,
    isLoading: Boolean,
    onSignInWithGoogle: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
    isSignOutLocked: Boolean = false,
    signOutLockReason: String? = null
) {
    var showSignOutConfirmDialog by remember { mutableStateOf(false) }
    var showSignOutLockedDialog by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxWidth()) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Forest800),
            border = androidx.compose.foundation.BorderStroke(
                1.2.dp,
                if (user != null) Success.copy(alpha = 0.35f) else Emerald500.copy(alpha = 0.35f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 14.dp, start = 16.dp, end = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (user == null) {
                    AccountLoggedOutSection(
                        isLoading = isLoading,
                        onSignInWithGoogle = onSignInWithGoogle
                    )
                } else {
                    // ── Logged In State ──
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.linearGradient(
                                            listOf(Color(0xFF16A34A), Color(0xFF059669))
                                        )
                                    )
                                    .border(1.5.dp, Success.copy(alpha = 0.6f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = user.displayInitial,
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = user.displayName ?: "Google User",
                                        color = Color.White,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Verified",
                                        tint = Success,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                                Spacer(Modifier.height(1.dp))
                                Text(
                                    text = user.email ?: "Account Connected",
                                    color = White60,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // Sign Out Button
                        OutlinedButton(
                            onClick = {
                                if (isSignOutLocked) {
                                    showSignOutLockedDialog = true
                                } else {
                                    showSignOutConfirmDialog = true
                                }
                            },
                            enabled = !isLoading,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color(0xFFF87171)
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF87171).copy(alpha = 0.4f)),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = Color(0xFFF87171)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "Sign Out",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFF87171)
                            )
                        }
                    }

                    // Cloud Protection Status Sub-bar
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSignOutLocked) Color(0x1AFB7185) else Color(0x1A22C55E),
                        border = androidx.compose.foundation.BorderStroke(
                            0.8.dp,
                            if (isSignOutLocked) Color(0xFFFB7185).copy(alpha = 0.35f) else Success.copy(alpha = 0.25f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = if (isSignOutLocked) Icons.Default.Lock else Icons.Default.CloudDone,
                                contentDescription = null,
                                tint = if (isSignOutLocked) Color(0xFFFB7185) else Success,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = if (isSignOutLocked) "Sign out locked · Uninstall Protection active" else "Account synced · Protection active & secured",
                                color = if (isSignOutLocked) Color(0xFFFB7185) else White90,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        // Top Badge
        Surface(
            shape = RoundedCornerShape(topStart = 10.dp, topEnd = 4.dp, bottomEnd = 10.dp, bottomStart = 4.dp),
            color = if (user != null) Color(0xFF065F46) else Forest700,
            shadowElevation = 3.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33FFFFFF)),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 14.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = if (user != null) Icons.Default.CheckCircle else Icons.Default.Lock,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(10.dp)
                )
                Text(
                    text = if (user != null) "Google Account Connected" else "Account & Cloud Sync",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    letterSpacing = 0.2.sp
                )
            }
        }
    }

    // Sign Out Confirmation Dialog
    AccountSignOutConfirmDialog(
        show = showSignOutConfirmDialog,
        onConfirm = {
            showSignOutConfirmDialog = false
            onSignOut()
        },
        onDismiss = { showSignOutConfirmDialog = false }
    )

    // Sign Out Locked Explanation Dialog
    AccountSignOutLockedDialog(
        show = showSignOutLockedDialog,
        reason = signOutLockReason,
        onDismiss = { showSignOutLockedDialog = false }
    )
}
