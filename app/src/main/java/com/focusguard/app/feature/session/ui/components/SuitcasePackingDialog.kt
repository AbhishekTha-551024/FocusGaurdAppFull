package com.focusguard.app.feature.session.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.focusguard.app.core.model.AppInfo
import com.focusguard.app.ui.components.AppIconView

private val Forest900 = Color(0xFF07140D)
private val CardBg = Color(0xFF0D2117)
private val Emerald500 = Color(0xFF22C55E)
private val Emerald400 = Color(0xFF4ADE80)
private val Amber400 = Color(0xFFFBBF24)
private val TextMuted = Color(0x99FFFFFF)

@Composable
fun SuitcasePackingDialog(
    availableApps: List<AppInfo>,
    packedPackages: Set<String>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onTogglePackage: (String) -> Unit,
    onQuickPackUtilities: (List<AppInfo>) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Forest900,
            border = BorderStroke(1.dp, Color(0x3322C55E)),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.85f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                SuitcaseDialogHeader(onDismiss = onDismiss)
                Spacer(Modifier.height(14.dp))
                SuitcaseSlotsPreview(
                    packedPackages = packedPackages,
                    availableApps = availableApps,
                    onTogglePackage = onTogglePackage,
                    onQuickPackUtilities = onQuickPackUtilities
                )
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = { Text("Search tools to pack...", color = Color(0x66FFFFFF), fontSize = 13.sp) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = Color(0x88FFFFFF), modifier = Modifier.size(18.dp))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Emerald500,
                        unfocusedBorderColor = Color(0x22FFFFFF),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    singleLine = true
                )
                Spacer(Modifier.height(10.dp))
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(availableApps, key = { it.packageName }) { app ->
                        val isPacked = packedPackages.contains(app.packageName)
                        SuitcaseAppRow(app = app, isPacked = isPacked, onToggle = { onTogglePackage(app.packageName) })
                    }
                }
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Emerald500, contentColor = Color(0xFF031A0F)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Done Packing (${packedPackages.size}/3)", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun SuitcaseDialogHeader(onDismiss: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = CircleShape,
                color = Color(0x2222C55E),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(imageVector = Icons.Default.Luggage, contentDescription = null, tint = Emerald400, modifier = Modifier.size(22.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(text = "Pack Your Suitcase", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text(text = "Choose up to 3 allowed tools", fontSize = 12.sp, color = TextMuted)
            }
        }
        IconButton(onClick = onDismiss) {
            Icon(Icons.Default.Close, contentDescription = "Close", tint = TextMuted)
        }
    }
}

@Composable
private fun SuitcaseSlotsPreview(
    packedPackages: Set<String>,
    availableApps: List<AppInfo>,
    onTogglePackage: (String) -> Unit,
    onQuickPackUtilities: (List<AppInfo>) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = CardBg,
        border = BorderStroke(1.dp, Color(0x2222C55E)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "PACKED SLOTS (${packedPackages.size}/3)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Emerald400, letterSpacing = 1.sp)
                TextButton(
                    onClick = { onQuickPackUtilities(availableApps) },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text("⚡ Auto-Pack Utilities", fontSize = 11.sp, color = Amber400, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                val list = packedPackages.toList()
                for (i in 0 until 3) {
                    val pkg = list.getOrNull(i)
                    if (pkg != null) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0x2222C55E),
                            border = BorderStroke(1.dp, Emerald400),
                            modifier = Modifier
                                .size(56.dp)
                                .clickable { onTogglePackage(pkg) }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                AppIconView(packageName = pkg, size = 36.dp)
                            }
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0x11FFFFFF),
                            border = BorderStroke(1.dp, Color(0x22FFFFFF)),
                            modifier = Modifier.size(56.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(text = "Empty\nSlot", fontSize = 9.sp, color = TextMuted, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SuitcaseAppRow(
    app: AppInfo,
    isPacked: Boolean,
    onToggle: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isPacked) Color(0x2222C55E) else Color(0x0EFFFFFF),
        border = BorderStroke(1.dp, if (isPacked) Color(0x4422C55E) else Color(0x18FFFFFF)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIconView(packageName = app.packageName, size = 36.dp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = app.appName, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(text = app.packageName, color = TextMuted, fontSize = 11.sp, maxLines = 1)
            }
            Checkbox(
                checked = isPacked,
                onCheckedChange = { onToggle() },
                colors = CheckboxDefaults.colors(checkedColor = Emerald500, checkmarkColor = Color(0xFF031A0F), uncheckedColor = Color(0x44FFFFFF))
            )
        }
    }
}
