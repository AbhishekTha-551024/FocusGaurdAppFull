package com.focusguard.app.feature.appblocker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.focusguard.app.core.model.AppInfo
import com.focusguard.app.feature.appblocker.ui.AppBlockerUiState
import com.focusguard.app.ui.components.AppIconView
import com.focusguard.app.ui.components.SearchField
import com.focusguard.app.ui.theme.Emerald400
import com.focusguard.app.ui.theme.Success

private val Navy900   = Color(0xFF07140D)
private val Navy800   = Color(0xFF0E2419)
private val Indigo400 = Color(0xFF22C55E)
private val Amber400  = Color(0xFFFBBF24)
private val White60   = Color(0x99FFFFFF)

@Composable
fun AppPickerDialog(
    uiState: AppBlockerUiState,
    isAccessibilityEnabled: Boolean,
    isOverlayGranted: Boolean,
    onRefreshPermissions: () -> Unit,
    onRequestPermissions: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onToggleBlock: (AppInfo, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var appToConfirmBlock by remember { mutableStateOf<AppInfo?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize().background(Navy900),
            color = Navy900
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(top = 16.dp)
            ) {
                PickerHeader(onDismiss = onDismiss)

                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    SearchField(
                        query = uiState.searchQuery,
                        onQueryChange = onSearchQueryChange,
                        placeholderText = "Search installed apps..."
                    )
                }

                HorizontalDivider(color = Color(0x1AFFFFFF), modifier = Modifier.padding(vertical = 4.dp))

                PickerAppList(
                    apps = uiState.unblockedApps,
                    onSelectApp = { appToConfirmBlock = it }
                )
            }
        }

        appToConfirmBlock?.let { target ->
            ConfirmBlockAppDialog(
                targetApp = target,
                isChallengeActive = uiState.isChallengeActive,
                challengeTypeName = uiState.challengeType.name,
                onConfirm = {
                    onRefreshPermissions()
                    if (!isAccessibilityEnabled || !isOverlayGranted) {
                        onRequestPermissions()
                        appToConfirmBlock = null
                    } else {
                        onToggleBlock(target, true)
                        appToConfirmBlock = null
                    }
                },
                onDismiss = { appToConfirmBlock = null }
            )
        }
    }
}

@Composable
private fun PickerHeader(onDismiss: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(text = "Select Apps to Block", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(text = "Tap any app to add it to your 24/7 focus blocklist", color = White60, fontSize = 12.sp)
        }
        IconButton(onClick = onDismiss) {
            Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = White60)
        }
    }
}

@Composable
private fun PickerAppList(
    apps: List<AppInfo>,
    onSelectApp: (AppInfo) -> Unit
) {
    if (apps.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No apps found",
                color = White60,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(
            items = apps,
            key = { it.packageName },
            contentType = { "picker_app" }
        ) { app ->
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Navy800),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x14FFFFFF)),
                modifier = Modifier.fillMaxWidth().clickable { onSelectApp(app) }
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AppIconView(packageName = app.packageName, size = 38.dp)

                    Text(
                        text = app.appName,
                        color = Color.White,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    Button(
                        onClick = { onSelectApp(app) },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Indigo400, contentColor = Navy900),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text("Block", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfirmBlockAppDialog(
    targetApp: AppInfo,
    isChallengeActive: Boolean,
    challengeTypeName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    var isSubmitting by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = Indigo400, modifier = Modifier.size(22.dp))
                Text("Block ${targetApp.appName}?", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Once added to your blocklist, ${targetApp.appName} will be strictly locked 24/7.",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 13.sp
                )
                if (isChallengeActive) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0x1F22C55E),
                        border = androidx.compose.foundation.BorderStroke(0.8.dp, Success.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "🔒 Friction Protection Active: Unblocking this app later will require completing your Accountability Challenge ($challengeTypeName).",
                            color = Emerald400,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Amber400.copy(alpha = 0.15f),
                        border = androidx.compose.foundation.BorderStroke(0.8.dp, Amber400.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "⚠️ No Accountability Challenge set: To prevent impulsive unblocking, we recommend setting up an Accountability Challenge in Settings.",
                            color = Amber400,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
                Text(text = "Do you want to confirm blocking ${targetApp.appName}?", color = White60, fontSize = 12.sp)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (!isSubmitting) {
                        isSubmitting = true
                        onConfirm()
                    }
                },
                enabled = !isSubmitting,
                colors = ButtonDefaults.buttonColors(containerColor = Indigo400, contentColor = Navy900)
            ) {
                Text("Block App", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = White60)
            }
        },
        containerColor = Navy800,
        shape = RoundedCornerShape(18.dp)
    )
}
