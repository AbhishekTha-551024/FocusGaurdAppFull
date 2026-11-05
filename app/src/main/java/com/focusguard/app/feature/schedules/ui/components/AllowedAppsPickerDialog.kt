package com.focusguard.app.feature.schedules.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.focusguard.app.ui.components.AppIconView
import com.focusguard.app.ui.components.SearchField

private val Navy900 = Color(0xFF07140D)
private val Navy800 = Color(0xFF0E2419)
private val Success = Color(0xFF34D399)
private val White60 = Color(0x99FFFFFF)

@Composable
fun AllowedAppsPickerDialog(
    installedApps: List<AppInfo>,
    selectedPackages: Set<String>,
    onTogglePackage: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(query, installedApps) {
        if (query.isBlank()) installedApps
        else installedApps.filter { it.appName.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true) }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize().background(Navy900),
            color = Navy900
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(top = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text("Select Apps to Allow", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("Checked apps will NOT be blocked", color = Success, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Button(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Success, contentColor = Navy900),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
                        modifier = Modifier.wrapContentSize()
                    ) {
                        Text("Done", fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, softWrap = false)
                    }
                }

                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    SearchField(
                        query = query,
                        onQueryChange = { query = it },
                        placeholderText = "Search apps to allow (e.g. WhatsApp, Maps)..."
                    )
                }

                HorizontalDivider(color = Color(0x1AFFFFFF))

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(filtered, key = { it.packageName }) { app ->
                        val isAllowed = selectedPackages.contains(app.packageName)
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isAllowed) Color(0x2E34D399) else Navy800
                            ),
                            border = BorderStroke(
                                1.dp,
                                if (isAllowed) Success.copy(alpha = 0.6f) else Color(0x14FFFFFF)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onTogglePackage(app.packageName) }
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                AppIconView(packageName = app.packageName, size = 34.dp)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = app.appName,
                                        color = Color.White,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 14.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = if (isAllowed) "✓ Allowed (Not blocked)" else "Will be blocked during focus",
                                        color = if (isAllowed) Success else White60,
                                        fontSize = 11.sp
                                    )
                                }
                                Checkbox(
                                    checked = isAllowed,
                                    onCheckedChange = { onTogglePackage(app.packageName) },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = Success,
                                        checkmarkColor = Navy900,
                                        uncheckedColor = White60
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
