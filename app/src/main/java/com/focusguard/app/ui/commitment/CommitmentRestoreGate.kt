package com.focusguard.app.ui.commitment

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusguard.app.commitment.CommitmentRestoreStatus
import com.focusguard.app.core.designsystem.theme.Emerald400
import com.focusguard.app.core.designsystem.theme.Forest950
import com.focusguard.app.core.designsystem.theme.Forest900
import com.focusguard.app.core.designsystem.theme.White60
import com.focusguard.app.core.designsystem.theme.White90

/**
 * CommitmentRestoreGate
 * =====================
 * First-run wall that stays up while [CommitmentRestoreStatus.isGating] is true.
 *
 * It exists because the reinstall-stable lookup is a network call: without this screen the user could
 * reach an unrestricted app — and the system uninstall path — in the second or two before the restored
 * timed lock is re-armed. It never offers a bypass button; it resolves on its own, and if the server
 * stays unreachable it releases after its bounded retries rather than bricking an offline phone.
 */
@Composable
fun CommitmentRestoreGate(
    status: CommitmentRestoreStatus,
    modifier: Modifier = Modifier
) {
    val isRetrying = status == CommitmentRestoreStatus.RETRYING
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(colors = listOf(Forest950, Forest900, Forest950))
            )
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Shield,
            contentDescription = null,
            tint = Emerald400,
            modifier = Modifier.size(64.dp)
        )
        Text(
            text = if (isRetrying) "Reconnecting to your commitment" else "Restoring your commitment",
            color = White90,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 24.dp)
        )
        Text(
            text = if (isRetrying) {
                "FocusGuard cannot reach the device record yet. The app stays locked until this check finishes."
            } else {
                "Checking whether this device still owes an active lock, so an uninstall and reinstall " +
                    "cannot erase a commitment that has not finished."
            },
            color = White60,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            lineHeight = 21.sp,
            modifier = Modifier.padding(top = 12.dp)
        )
        CircularProgressIndicator(
            color = Emerald400,
            strokeWidth = 3.dp,
            modifier = Modifier.padding(top = 32.dp)
        )
        Text(
            text = "Do not close the app while this is running.",
            color = White60,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 20.dp)
        )
    }
}
