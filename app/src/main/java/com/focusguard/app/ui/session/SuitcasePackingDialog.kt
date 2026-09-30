package com.focusguard.app.ui.session

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
import com.focusguard.app.data.model.AppInfo
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
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.85f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "🧳 Pack Your Suitcase",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (packedPackages.size >= 3) Color(0x33FBBF24) else Color(0x2222C55E),
                                border = BorderStroke(1.dp, if (packedPackages.size >= 3) Color(0x66FBBF24) else Color(0x3322C55E))
                            ) {
                                Text(
                                    text = "${packedPackages.size}/3 PACKED",
                                    color = if (packedPackages.size >= 3) Amber400 else Emerald400,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                        Text(
                            text = "Maximum 3 essential apps allowed during Deep Focus",
                            fontSize = 12.sp,
                            color = TextMuted
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(0x18FFFFFF))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Safety Call Guarantee Banner
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0x1A22C55E),
                    border = BorderStroke(1.dp, Color(0x3322C55E)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Phone,
                            contentDescription = null,
                            tint = Emerald400,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "Phone Calls, Contacts, & Emergency SMS are permanently allowed.",
                            color = Color(0xEEFFFFFF),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Search & Quick Pack
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        placeholder = { Text("Search apps...", fontSize = 13.sp, color = TextMuted) },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null, tint = TextMuted, modifier = Modifier.size(18.dp))
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Emerald500,
                            unfocusedBorderColor = Color(0x22FFFFFF),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    )

                    Button(
                        onClick = { onQuickPackUtilities(availableApps) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0x2522C55E)),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Text("Auto Tools", color = Emerald400, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Apps List
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(availableApps, key = { it.packageName }) { app ->
                        val isPacked = packedPackages.contains(app.packageName)
                        val canSelect = isPacked || packedPackages.size < 3
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isPacked) Color(0x1822C55E) else CardBg,
                            border = BorderStroke(1.dp, if (isPacked) Color(0x4422C55E) else Color(0x18FFFFFF)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (canSelect) {
                                        onTogglePackage(app.packageName)
                                    }
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AppIconView(packageName = app.packageName, size = 36.dp)

                                Spacer(Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = app.appName,
                                        color = if (canSelect) Color.White else Color(0x66FFFFFF),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = if (isPacked) "Allowed (Slot ${packedPackages.indexOf(app.packageName) + 1}/3)" else if (canSelect) "Locked" else "Slots full (Max 3)",
                                        color = if (isPacked) Emerald400 else if (canSelect) TextMuted else Amber400,
                                        fontSize = 11.sp
                                    )
                                }

                                Checkbox(
                                    checked = isPacked,
                                    enabled = canSelect,
                                    onCheckedChange = {
                                        if (canSelect) onTogglePackage(app.packageName)
                                    },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = Emerald500,
                                        checkmarkColor = Color.Black,
                                        uncheckedColor = Color(0x66FFFFFF)
                                    )
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Done Button
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = Emerald500),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text(
                        text = "Done (${packedPackages.size}/3 Packed)",
                        color = Color.Black,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
