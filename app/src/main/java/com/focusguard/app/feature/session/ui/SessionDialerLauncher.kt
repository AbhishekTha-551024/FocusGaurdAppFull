package com.focusguard.app.feature.session.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast

internal object SessionDialerLauncher {
    private const val TAG = "FG:Service"

    fun launch(context: Context): Boolean {
        val intents = listOf(
            Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", "", null)),
            Intent(Intent.ACTION_DIAL)
        )

        for (intent in intents) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(intent)
                return true
            } catch (e: ActivityNotFoundException) {
                Log.w(TAG, "No activity handled a dialer action", e)
            } catch (e: SecurityException) {
                Log.w(TAG, "The dialer action was denied", e)
            }
        }

        Toast.makeText(context, "No phone dialer is available on this device.", Toast.LENGTH_LONG).show()
        return false
    }
}
