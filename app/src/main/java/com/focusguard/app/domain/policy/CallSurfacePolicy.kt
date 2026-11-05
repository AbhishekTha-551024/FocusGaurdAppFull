package com.focusguard.app.domain.policy

object CallSurfacePolicy {
    private const val FOCUSGUARD_PACKAGE_PREFIX = "com.focusguard.app"
    private const val SYSTEM_UI_PACKAGE = "com.android.systemui"

    private val callSurfacePackages = setOf(
        "com.android.phone",
        "com.android.incallui",
        "com.android.server.telecom",
        "com.samsung.android.incallui",
        "com.android.dialer",
        "com.google.android.dialer",
        "com.samsung.android.dialer",
        "com.android.emergency",
        "com.truecaller",
        "com.callapp.contacts",
        "com.eycon.android",
        "com.syncme.syncmeapp",
        "gogolook.callgogolook2",
        "com.simplemobiletools.dialer"
    )

    private val voipCallPackages = setOf(
        "com.whatsapp",
        "org.telegram.messenger",
        "org.thoughtcrime.securesms",
        "com.viber.voip",
        "com.google.android.apps.tachyon",
        "com.google.android.apps.meetings",
        "com.facebook.orca",
        "jp.naver.line.android",
        "com.tencent.mm",
        "us.zoom.videomeetings",
        "com.microsoft.teams",
        "com.skype.raider",
        "com.discord"
    )

    fun isNamedCallSurface(packageName: String): Boolean {
        val packageId = packageName.lowercase()
        return packageId == SYSTEM_UI_PACKAGE ||
            packageId == FOCUSGUARD_PACKAGE_PREFIX ||
            packageId in callSurfacePackages
    }

    fun isKnownVoipCallPackage(packageName: String): Boolean =
        packageName.lowercase() in voipCallPackages

    fun isCriticalCallSurface(
        packageName: String,
        defaultDialerPackage: String?,
        callInProgress: Boolean
    ): Boolean {
        val packageId = packageName.lowercase()
        return isNamedCallSurface(packageId) ||
            packageId == defaultDialerPackage?.lowercase() ||
            (callInProgress && isKnownVoipCallPackage(packageId))
    }

    fun isAlwaysAllowedDuringSession(
        packageName: String,
        selfPackage: String,
        defaultDialerPackage: String?,
        defaultSmsPackage: String?,
        voipCallInProgress: Boolean
    ): Boolean {
        val packageId = packageName.lowercase()
        return packageId == selfPackage.lowercase() ||
            packageId == FOCUSGUARD_PACKAGE_PREFIX ||
            isNamedCallSurface(packageId) ||
            packageId == defaultDialerPackage?.lowercase() ||
            packageId == defaultSmsPackage?.lowercase() ||
            (voipCallInProgress && isKnownVoipCallPackage(packageId))
    }
}
