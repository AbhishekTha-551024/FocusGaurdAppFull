package com.focusguard.app.feature.mindful.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusguard.app.core.model.AppInfo
import com.focusguard.app.ui.components.AppIconView
import com.focusguard.app.utils.AppIconLoader

private val CardBg     = Color(0xFF0D2117)
private val Emerald500 = Color(0xFF22C55E)
private val Emerald400 = Color(0xFF4ADE80)
private val TextMuted  = Color(0x99FFFFFF)

@Composable
fun MindfulAppCard(
    app: AppInfo,
    isMindful: Boolean,
    allowanceMinutes: Int,
    iconLoader: AppIconLoader? = null,
    onToggle: () -> Unit,
    onUpdateAllowance: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var showAllowanceMenu by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = CardBg,
        border = BorderStroke(1.dp, if (isMindful) Color(0x3322C55E) else Color(0x18FFFFFF)),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIconView(packageName = app.packageName, size = 38.dp)
            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.appName,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (isMindful) "Breathing pause on launch" else app.packageName,
                    color = if (isMindful) Emerald400 else TextMuted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (isMindful) {
                Box {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0x2222C55E),
                        border = BorderStroke(1.dp, Color(0x4422C55E)),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { showAllowanceMenu = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${allowanceMinutes}m",
                                color = Emerald400,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.width(2.dp))
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                tint = Emerald400,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = showAllowanceMenu,
                        onDismissRequest = { showAllowanceMenu = false }
                    ) {
                        listOf(1, 2, 5, 10, 15).forEach { mins ->
                            DropdownMenuItem(
                                text = { Text("${mins} Minutes Allowance", fontSize = 13.sp) },
                                onClick = {
                                    onUpdateAllowance(mins)
                                    showAllowanceMenu = false
                                }
                            )
                        }
                    }
                }
                Spacer(Modifier.width(8.dp))
            }

            Switch(
                checked = isMindful,
                onCheckedChange = { onToggle() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color(0xFF031A0F),
                    checkedTrackColor = Emerald500,
                    uncheckedThumbColor = Color(0x88FFFFFF),
                    uncheckedTrackColor = Color(0x22FFFFFF)
                )
            )
        }
    }
}
