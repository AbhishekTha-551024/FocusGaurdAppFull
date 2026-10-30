package com.focusguard.app.feature.appblocker.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusguard.app.core.model.AppInfo
import com.focusguard.app.feature.appblocker.ui.AppBlockerUiState

private val Amber400  = Color(0xFFFBBF24)
private val White90   = Color(0xE6FFFFFF)
private val White40   = Color(0x66FFFFFF)

@Composable
fun BlockedAppList(
    uiState: AppBlockerUiState,
    isAccessibilityEnabled: Boolean,
    isOverlayGranted: Boolean,
    onOpenPermissions: () -> Unit,
    onToggleBlock: (AppInfo, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (!isAccessibilityEnabled || !isOverlayGranted) {
            item {
                Surface(
                    color = Color(0x22FBBF24),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Amber400.copy(alpha = 0.45f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().clickable { onOpenPermissions() }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = Amber400, modifier = Modifier.size(18.dp))
                        Text(
                            text = "App Blocking requires Accessibility & Overlay access. Tap to grant.",
                            color = White90,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Text("Enable", color = Amber400, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }

        if (uiState.pendingReviewApps.isNotEmpty()) {
            item {
                Text(
                    text = "PENDING REVIEW (${uiState.pendingReviewApps.size})",
                    color = Amber400,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(start = 4.dp, top = 2.dp, bottom = 4.dp)
                )
            }
            items(uiState.pendingReviewApps, key = { "pending_${it.packageName}" }) { app ->
                PendingReviewAppRow(app = app, onUnblock = { onToggleBlock(app, false) })
            }
            item { Spacer(Modifier.height(6.dp)) }
        }

        if (uiState.blockedApps.isNotEmpty()) {
            item {
                Text(
                    text = "CURRENTLY BLOCKED (${uiState.blockedApps.size})",
                    color = White40,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 4.dp)
                )
            }
            items(uiState.blockedApps, key = { it.packageName }) { app ->
                BlockedAppRow(app = app, onUnblock = { onToggleBlock(app, false) })
            }
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}
