package com.focusguard.app.feature.timelimits.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
import com.focusguard.app.feature.timelimits.ui.AppTimeLimitUiModel
import com.focusguard.app.feature.timelimits.ui.TimeLimitUiState
import com.focusguard.app.ui.components.AppIconView
import com.focusguard.app.ui.components.SearchField

private val Navy900   = Color(0xFF07140D)
private val Navy800   = Color(0xFF0E2419)
private val Navy700   = Color(0xFF163827)
private val Purple400 = Color(0xFF22C55E)
private val White60   = Color(0x99FFFFFF)
private val White40   = Color(0x66FFFFFF)

/**
 * =====================================================================
 * AddLimitPickerDialog.kt - App Picker Full-Screen Dialog
 * =====================================================================
 *
 * ## 1. Yeh File Kya Karti Hai? (Core Responsibility)
 * Yeh Compose Dialog user ko phone ke installed apps ki list dikhata hai with live search
 * bar, taaki user kisi bhi app (e.g. YouTube, Instagram, WhatsApp) ko select karke uspar
 * daily time cap set kar sake.
 *
 * ## 2. Core UI Sections:
 * - `AddLimitPickerHeader`: Dialog header with title and close button.
 * - `SearchField`: Apps ko name ya package name se filter karne ke liye search input.
 * - `LimitAppSelectionRow`: Har app ka icon, name, current configured limit, aur "Set Cap" action button.
 * - `LimitMinutesPickerSubDialog`: App select hone par time selector popup (15m, 30m, 60m, custom slider).
 *
 * ## 3. Performance Features (Senior Dev Perspective):
 * - `DialogProperties(usePlatformDefaultWidth = false)`: Dialog ko full width/height provide karta hai.
 * - `LazyColumn` me `key = { it.appInfo.packageName }` aur `contentType = { "limit_picker_row" }`
 *   use kiya gaya hai taaki 100+ apps hone par bhi 60/120fps smooth scrolling mile.
 * =====================================================================
 */
@Composable
fun AddLimitPickerDialog(
    uiState: TimeLimitUiState,
    onSearchQueryChange: (String) -> Unit,
    onSetLimit: (AppInfo, Int) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedApp by remember { mutableStateOf<AppInfo?>(null) }
    var chosenMinutes by remember { mutableIntStateOf(30) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .background(Navy900),
            color = Navy900
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 16.dp)
            ) {
                AddLimitPickerHeader(onDismiss = onDismiss)
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    SearchField(
                        query = uiState.searchQuery,
                        onQueryChange = onSearchQueryChange,
                        placeholderText = "Search installed apps..."
                    )
                }
                HorizontalDivider(color = Color(0x1AFFFFFF), modifier = Modifier.padding(vertical = 4.dp))
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = uiState.filteredApps,
                        key = { it.appInfo.packageName },
                        contentType = { "limit_picker_row" }
                    ) { model ->
                        LimitAppSelectionRow(
                            model = model,
                            onSelect = { selectedApp = model.appInfo }
                        )
                    }
                }
            }
        }

        selectedApp?.let { app ->
            LimitMinutesPickerSubDialog(
                app = app,
                chosenMinutes = chosenMinutes,
                onMinutesChange = { chosenMinutes = it },
                onConfirm = { minutes ->
                    onSetLimit(app, minutes)
                    selectedApp = null
                },
                onDismiss = { selectedApp = null }
            )
        }
    }
}

@Composable
private fun AddLimitPickerHeader(onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Set Daily Time Limit",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "Pick an app and choose a daily usage cap",
                color = White60,
                fontSize = 11.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onDismiss) {
            Icon(Icons.Default.Close, contentDescription = "Close", tint = White60)
        }
    }
}

@Composable
private fun LimitAppSelectionRow(
    model: AppTimeLimitUiModel,
    onSelect: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Navy800),
        border = BorderStroke(1.dp, Color(0x14FFFFFF)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() }
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AppIconView(packageName = model.appInfo.packageName, size = 38.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = model.appInfo.appName,
                    color = Color.White,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (model.isLimitConfigured) "Cap: ${model.dailyLimitMinutes}m/day (${model.usedMinutesToday}m used)" else "No cap set",
                    color = if (model.isLimitConfigured) Purple400 else White40,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (model.isLimitConfigured) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Navy700,
                    border = BorderStroke(1.dp, Color(0x20FFFFFF))
                ) {
                    Text(
                        text = "Active · Locked",
                        color = Purple400,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            } else {
                Button(
                    onClick = onSelect,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Purple400, contentColor = Navy900),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text("Set Cap", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
        }
    }
}
