# Hardcore tier — provisioning FocusGuard as Device Owner

Status: **optional install step**. Nothing in this file is required for the normal app, and no code
in the repository changes based on it. FocusGuard already contains the Device Owner enforcement and
simply refuses to run it when the app is not the device owner.

## Why this tier exists

Without Device Owner, every protection layer lives inside FocusGuard's own process. If that process
is force-stopped while the Accessibility Service is already OFF, nothing inside the app can wake it,
and Android will not deliver `BOOT_COMPLETED` to a stopped app until it is opened again or the device
restarts. That is the only remaining window in the protection model, and it cannot be closed by any
amount of app code — it needs an anchor that the OS enforces by itself.

Device Owner is that anchor. `dpm set-device-owner` is the only way to obtain it, so this tier is a
one-time adb step, not something a user can tap through in the app.

## What the app already does once it is Device Owner

All of the following is existing code in
`app/src/main/java/com/focusguard/app/receiver/FocusGuardDeviceAdminReceiver.kt`, re-applied at every
boot by `BootReceiver` STEP A:

- `setUninstallBlocked(true)` — uninstall is refused for the whole profile, including from adb.
- `DISALLOW_SAFE_BOOT` — closes the Safe Mode bypass, which otherwise unbinds the Accessibility Service.
- `DISALLOW_CONFIG_DATE_TIME`, plus enforced auto-time and auto-timezone — closes the clock-change bypass against timed locks.
- `DISALLOW_CONFIG_VPN`, `DISALLOW_NETWORK_RESET` — keeps the adult-content DNS guard from being reconfigured away.
- `DISALLOW_ADD_USER`, `DISALLOW_ADD_MANAGED_PROFILE`, `DISALLOW_USER_SWITCH` — closes the second-user / work-profile cloning route.
- Android 14+: blocks the OEM clone profile where the platform supports it.

## Provisioning

Install the APK first (same build the website distributes), then:

```
adb shell dpm set-device-owner com.focusguard.app/.receiver.FocusGuardDeviceAdminReceiver
```

The receiver is already `android:exported="true"` with `BIND_DEVICE_ADMIN` and a
`@xml/device_admin_policies` meta-data entry (`app/src/main/AndroidManifest.xml:177-182`), so no
manifest change is needed.

Common failure: the platform refuses while the device has accounts set up. Factory reset or "Remove
all accounts", then run the command, then sign in again.

## Undo

```
adb shell dpm remove-active-admin com.focusguard.app/.receiver.FocusGuardDeviceAdminReceiver
```

This drops Device Owner (and the admin), after which the app becomes uninstallable normally. A
factory reset also clears it.

## Costs to state honestly before recommending this to users

- `DISALLOW_INSTALL_UNKNOWN_SOURCES` is applied, so while this tier is active the owner cannot sideload
  *any other* APK. If your distribution depends on people sideloading more than FocusGuard, drop this
  one restriction rather than the tier.
- Device Owner is an administrative position: the app can restrict the user's settings and is expected
  to be trusted accordingly. It should be offered as an explicit, informed choice, never silently.
- Whether `dpm set-device-owner` succeeds on vivo/ColorOS devices is **not yet verified**. It has to be
  run once on the Vivo V2355 before this tier is published, because OEM builds sometimes reject it.
- Whether `setUninstallBlocked` survives a factory-reset-then-boot cycle is **not verified**; a factory
  reset removes the device owner by design, so the intended recovery path is the adb undo command.
