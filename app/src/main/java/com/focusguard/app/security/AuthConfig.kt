package com.focusguard.app.security

/**
 * Configuration for Google Sign-In & Authentication in FocusGuard.
 *
 * To connect to your Google Cloud / Firebase project:
 * 1. Open Google Cloud Console or Firebase Console.
 * 2. Create an Android OAuth client for package `com.focusguard.app` with your SHA-1.
 * 3. Create a Web OAuth 2.0 Client ID.
 * 4. Paste your Web Client ID into [GOOGLE_WEB_CLIENT_ID] below.
 */
object AuthConfig {
    /**
     * Your OAuth 2.0 Web Client ID from Google Cloud / Firebase Console.
     */
    const val GOOGLE_WEB_CLIENT_ID: String = "1039832642648-b41p7ounut5cpa2h2fj8goib0uu3llp8.apps.googleusercontent.com"

    val isConfigured: Boolean
        get() = GOOGLE_WEB_CLIENT_ID.isNotBlank() && !GOOGLE_WEB_CLIENT_ID.startsWith("YOUR_")
}
