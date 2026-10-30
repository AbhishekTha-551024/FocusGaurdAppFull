package com.focusguard.app.feature.keywordblocker.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

private val Navy800 = Color(0xFF0E2419)
private val Navy700 = Color(0xFF163827)
private val Indigo400 = Color(0xFF22C55E)
private val Rose400 = Color(0xFFFB7185)
private val White60 = Color(0x99FFFFFF)
private val White40 = Color(0x66FFFFFF)

/**
 * AddWebsiteModalDialog.kt - Add Website / Keyword Input Modal Dialog
 * Provides input field with domain/keyword validation, suggestion chips,
 * and friction confirmation dialog before blocking.
 */
@Composable
fun AddWebsiteModalDialog(
    input: String,
    errorMessage: String?,
    onInputChange: (String) -> Unit,
    onAdd: () -> Unit,
    onDismiss: () -> Unit
) {
    val suggestions = listOf("reddit.com", "twitter.com", "instagram.com", "facebook.com", "twitch.tv", "netflix.com", "discord.com")
    var showConfirmDialog by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Navy800),
            border = androidx.compose.foundation.BorderStroke(1.5.dp, Indigo400.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .padding(22.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                DialogHeader(onDismiss = onDismiss)

                Text(
                    text = "Enter a website domain or search query to block it across Chrome, Firefox, Brave, and Samsung Internet.",
                    color = White60,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )

                OutlinedTextField(
                    value = input,
                    onValueChange = onInputChange,
                    placeholder = { Text("e.g. reddit.com or reels", color = White40, fontSize = 13.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Navy700,
                        unfocusedContainerColor = Navy700,
                        focusedBorderColor = Indigo400,
                        unfocusedBorderColor = Color(0x33818CF8),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        if (input.trim().length >= 2) showConfirmDialog = true
                    })
                )

                if (errorMessage != null) {
                    Text(text = errorMessage, color = Rose400, fontSize = 12.sp)
                }

                SuggestionsSection(
                    suggestions = suggestions,
                    onSelect = onInputChange
                )

                ActionButtons(
                    input = input,
                    onDismiss = onDismiss,
                    onConfirmed = { showConfirmDialog = true }
                )
            }
        }

        if (showConfirmDialog) {
            ConfirmBlockKeywordDialog(
                keyword = input.trim(),
                onConfirm = {
                    showConfirmDialog = false
                    onAdd()
                },
                onDismiss = { showConfirmDialog = false }
            )
        }
    }
}

@Composable
private fun DialogHeader(onDismiss: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "Block Website / Keyword",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp
        )
        IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Default.Close, contentDescription = "Close", tint = White60)
        }
    }
}

@Composable
private fun SuggestionsSection(
    suggestions: List<String>,
    onSelect: (String) -> Unit
) {
    Text(
        text = "QUICK SUGGESTIONS",
        color = White40,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        suggestions.take(3).forEach { domain ->
            SuggestionChip(
                onClick = { onSelect(domain) },
                label = { Text(domain, fontSize = 11.sp, color = Indigo400) },
                colors = SuggestionChipDefaults.suggestionChipColors(containerColor = Navy700),
                border = SuggestionChipDefaults.suggestionChipBorder(
                    enabled = true,
                    borderColor = Indigo400.copy(alpha = 0.4f)
                )
            )
        }
    }
}

@Composable
private fun ActionButtons(
    input: String,
    onDismiss: () -> Unit,
    onConfirmed: () -> Unit
) {
    val isValid = input.trim().length >= 2
    var isSubmitting by remember { mutableStateOf(false) }
    LaunchedEffect(input) { isSubmitting = false }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        OutlinedButton(
            onClick = onDismiss,
            modifier = Modifier.weight(1f).height(44.dp),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text("Cancel", color = White60)
        }

        Button(
            onClick = {
                if (!isSubmitting && isValid) {
                    isSubmitting = true
                    onConfirmed()
                }
            },
            enabled = isValid && !isSubmitting,
            modifier = Modifier.weight(1f).height(44.dp),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Indigo400, contentColor = Color(0xFF07140D)),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Text("Block", fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, softWrap = false)
        }
    }
}

@Composable
private fun ConfirmBlockKeywordDialog(
    keyword: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    var isSubmitting by remember { mutableStateOf(false) }
    LaunchedEffect(keyword) { isSubmitting = false }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = Indigo400,
                    modifier = Modifier.size(22.dp)
                )
                Text("Block Keyword?", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Any webpage, website URL, or browser search containing \"$keyword\" will be blocked immediately across all browsers.",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 13.sp
                )
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0x1F22C55E),
                    border = androidx.compose.foundation.BorderStroke(0.8.dp, Indigo400.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "🔒 Friction Protection: Removing or unblocking this keyword later will require completing your Accountability Challenge.",
                        color = Indigo400,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(10.dp)
                    )
                }
                Text(
                    text = "Do you want to confirm blocking \"$keyword\"?",
                    color = White60,
                    fontSize = 12.sp
                )
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
                colors = ButtonDefaults.buttonColors(containerColor = Indigo400, contentColor = Color(0xFF07140D))
            ) {
                Text("Confirm Block", fontWeight = FontWeight.Bold)
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
