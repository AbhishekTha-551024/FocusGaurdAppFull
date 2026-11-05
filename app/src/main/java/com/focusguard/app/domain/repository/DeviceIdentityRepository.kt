package com.focusguard.app.domain.repository

/**
 * DeviceIdentityRepository
 * ========================
 * Domain port for "which physical device is this, in a way that survives an uninstall?".
 *
 * The app's own `FG-####-####` id lives in app storage, so a reinstall regenerates it and it cannot
 * key remote state. Implementations must return an identifier that Android keeps across
 * install/uninstall cycles (see the device-protected `Settings.Secure.ANDROID_ID` adapter).
 *
 * The value identifies a DEVICE, not a person, and is not a security credential: it is used only as
 * a lock-restore hint. The signed-in Firebase uid stays the authoritative account key.
 */
interface DeviceIdentityRepository {

    /** Reinstall-stable device key. Blank only when the platform genuinely has none. */
    fun reinstallStableId(): String
}
