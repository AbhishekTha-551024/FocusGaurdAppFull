package com.focusguard.app.feature.settings.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusguard.app.core.designsystem.theme.White40
import com.focusguard.app.core.model.AuthUser
import com.focusguard.app.feature.auth.ui.components.AccountProfileCard

@Composable
fun AccountProfileSection(
    user: AuthUser?,
    isLoading: Boolean,
    onSignInWithGoogle: () -> Unit,
    onSignOut: () -> Unit,
    isSignOutLocked: Boolean,
    signOutLockReason: String?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "ACCOUNT & PROFILE",
            color = White40,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )

        AccountProfileCard(
            user = user,
            isLoading = isLoading,
            onSignInWithGoogle = onSignInWithGoogle,
            onSignOut = onSignOut,
            isSignOutLocked = isSignOutLocked,
            signOutLockReason = signOutLockReason
        )
    }
}
