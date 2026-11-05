# Plan — Accessibility Auto-Recovery (Uninstall Protection gated)

Status: Phase 0 audit + plan. **No source files changed.**
Date: 2026-11-03 · Branch: `fix-boot-cover-overlay` · HEAD: `75ca199`
Device under test: Vivo V2355, Android 16 (Funtouch/OriginOS, `com.vivo.imanager`)

---

## 1. The requirement being audited

> While **Uninstall Protection is ON**, if Accessibility turns off for *any* reason it must
> come back automatically. While **Uninstall Protection is OFF**, the app must *never*
> auto-recover or auto-enable Accessibility.

Everything below is judged only against that rule, plus the secondary goal that a restart must
not leave the app unprotected or trivially uninstallable.

---

## 2. Verified current state (what already exists)

A concurrent refactor has already landed most of the recovery stack. Baseline is green:
`assembleDebug` + `testDebugUnitTest` → BUILD SUCCESSFUL.

### 2.1 Decision core (pure Kotlin)

| File | Responsibility |
|---|---|
| `recovery/AutoRecoveryPolicy.kt` | The rule. `shouldAutoRecover()` returns `isProtectionActive`; probe 15 s when protected / 60 s idle; retry gap 30 s → 5 min cap; escalate after 3 failures. |
| `recovery/AccessibilityStatusChecker.kt` | Two different questions: `isAccessibilityActive()` = bound **or** listed in Settings (for UI parity); `isAccessibilityHealthy()` = bound **and** listed (strict, used by recovery). |
| `recovery/AccessibilityRecoveryScheduler.kt` | 20 s post-write binding verification, max 8 rapid attempts per episode, counter in DE storage. |

### 2.2 Detectors

| Detector | Cadence | Notes |
|---|---|---|
| `A11yAutoRecoveryProbe` | 15 s / 60 s | Runs in the app process, independent of the bound service. Started from `FocusGuardApplication.kt:80` and `:96`. |
| `AccessibilityStateObserver` | Instant on secure-setting change, 1200 ms confirm | Registered at process level, `FocusGuardApplication.kt:124`. Survives service unbind. |
| `AccessibilityHealthWorker` | 15 min (+5 min flex) | Process-death backstop; now calls `restoreProtectionFast()` instead of only notifying (`AccessibilityHealthWorker.kt:78`). |
| `BootReceiver` | BOOT / LOCKED_BOOT / USER_PRESENT / MY_PACKAGE_REPLACED | Direct-boot branch, boot-cover overlay, `restoreProtectionFast()`, worker re-arm, pending-change reconcile. |

### 2.3 Restore path (`AccessibilityRecoveryHelper.restoreProtectionFast`, lines 126–207)

1. Already healthy → reset counters, dismiss notices, return.
2. **Single protection gate** `isProtectionActive()` → if OFF, stop. This is the only place
   that decides whether recovery may run, and it is shared by every caller.
3. Silent enable via `WRITE_SECURE_SETTINGS` → on success schedule 20 s binding verification.
4. Configured in Settings + benign source → grace period, then verification.
5. Direct boot → wait for unlock.
6. Fallback: recovery activity **only if `canDrawOverlays()`**, plus a notification.

### 2.4 Write hardening (`ProgrammaticAccessibilityEnabler`)

5 s cooldown between forced re-writes, re-read-after-write verification, "did not stick"
diagnostic. Fixes the self-inflicted write storm that made Settings flicker on/off.

### 2.5 Disarm protection (`ProtectionRepositoryImpl.kt:214–227`)

A running timed lock cannot be turned off without `challengeAuthorized`. **Verified present and
enforced** (logs "Refused to disarm…"). This was previously unverified — it is now confirmed.

---

## 3. Requirement audit

| # | Requirement | Verdict |
|---|---|---|
| R1 | A11y OFF + protection ON → auto-recover | **Mostly implemented.** Three independent detectors + boot restore. Weak points: G2, G3, G4, G5 below. |
| R2 | Protection OFF → never auto-recover | **Implemented and verifiable.** Every caller funnels through `isProtectionActive()`; observer and probe both cancel notices when OFF. |
| R3 | In-app ON must match Settings | **Implemented.** `isAccessibilityActive()` no longer short-circuits on the process-static `isServiceRunning`; UI reads the real bound/configured state. |
| R4 | Recovery must not fight itself | **Partial.** Write-path cooldown exists, but the *restore entry point* is not throttled, so observer + probe can both escalate UI/notifications (G1). |
| R5 | Timed lock cannot be shortened/disarmed without challenge | **Implemented and verified** (see 2.5). |
| R6 | Survive reboot (incl. direct boot) | **Implemented, needs device verification.** Boot race in `isProtectionActive()` fixed (a `false` fast-cache read is no longer authoritative post-boot). Boot-cover overlay path exists; must be re-tested on V2355 (G8). |
| R7 | Onboarding must not silently disappear | **Partial / unclear semantics.** Onboarding completion is still driven by the a11y check alone; needs an explicit decision (E). |

---

## 4. Gaps found (ordered by user impact)

**G1 — Two detectors, one restore entry point, no shared throttle.**
`AccessibilityStateObserver` and `A11yAutoRecoveryProbe` can call `restoreProtectionFast()` for
the same episode. Only the secure write is throttled (5 s); the activity launch and notification
are not. Result: repeated recovery screens / notifications during a persistent failure.
*Fix: one `RecoveryAttemptGate` (monotonic, in `AutoRecoveryPolicy`) checked at the top of
`restoreProtectionFast`, not per-caller.*

**G2 — Escalation can be invisible.**
`POST_NOTIFICATIONS` is declared in the manifest (`:25`) but **never requested at runtime**, and
Channel 3 asks `for permission` → no. On the test device it is `granted=false`. After 3 failed
silent attempts the probe "posts a notification" that the user never sees.
*Fix: request it during onboarding with an explicit rationale; treat it as required for Uninstall
Protection, not optional.*

**G3 — Silent recovery needs adb; website users won't have it.**
`WRITE_SECURE_SETTINGS` is granted only for user 0 on the test device (not 10/999). A normal
website install cannot get it. Then the only recovery is Layer 2 (requires overlay) + Layer 3
(requires notification permission). This is the single biggest real-world gap.
*Fix: product decision needed — Device Owner provisioning, Shizuku, an in-app adb/PC setup guide,
or accepting "recovery screen only". See decision D-A.*

**G4 — Process death leaves a 15-minute hole.**
The probe lives in the app process. Vivo kills the process → nothing runs until the 15-minute
WorkManager tick (WorkManager also lives in CE storage, unavailable before first unlock).
*Fix options in Phase D; no free lunch, tradeoff is battery vs. window length.*

**G5 — Layer 2 silently does nothing without overlay permission.**
`restoreProtectionFast:189` guards the activity behind `Settings.canDrawOverlays`. If overlay is
off and notifications are denied, recovery has **no user-visible output at all** — it retries
forever. This compounds G2/G3.

**G6 — Probe retries forever after escalation.**
The 8-attempt budget belongs to `AccessibilityRecoveryScheduler`, not the probe. The probe keeps
attempting every 5 min indefinitely and re-notifies each time past attempt 3. Desired or noisy?
Needs a decision (D-B).

**G7 — No-adb boot display.**
`BootReceiver` starts `BootCoverService` + `AccessibilityRecoveryActivity` when protection is ON
and the service is not bound. Background-activity-launch restrictions on Android 10+ can swallow
this. Must be tested on V2355 after a real restart.

**G8 — Working tree is 121 files uncommitted.**
Reviewed changes are riding on top of a large uncommitted refactor; a concurrent session already
rewrote the a11y service once mid-task. Risk of losing/mixing work. Nothing will be committed
without explicit approval.

**Minor:** `AccessibilityStateObserver` comment says "Debounce 7 seconds" but waits 1200 ms
(`:57`). Bookkeeping only.

---

## 5. Proposed phases (ONE at a time, stop after each)

### Phase A — Single throttled recovery entry point (no user-visible behavior change)
**Status: DONE (2026-11-03) — build + tests green, verified on V2355.**
- `AutoRecoveryPolicy`: `DUPLICATE_SUPPRESSION_MS = 10 s` + pure `shouldSuppressDuplicate()`.
- `AccessibilityRecoveryHelper.restoreProtectionFast`: one gate after the protection check;
  last attempt persisted in DE storage, discarded after reboot (`now < last`).
- Duplicate gate kept below the 20 s binding verification so verification still reaches the
  restore path. Verified: two removals 3 s apart → unbind + observer suppressed, recovery
  retried at ~11 s and restored silently.
- *(original scope below)*
- Add monotonic `shouldAttemptNow()` to `AutoRecoveryPolicy`.
- Call it once at the top of `restoreProtectionFast`; every detector inherits it.
- Keep the observer as the instant detector, probe as the periodic fallback; no duplicate logic.
- Unit tests: gap maths, first-attempt passthrough, reset on healthy.
- Files: `AutoRecoveryPolicy.kt`, `AccessibilityRecoveryHelper.kt`, new/extended test.
- *Behavior changed: yes (fewer duplicate recovery screens; recovery timing unchanged).*

### Phase B — Make escalation visible
**Status: DONE (2026-11-03) — build green; unit tests green except one pre-existing order-dependent
failure in `CommitmentMirrorWatcherTest` (passes in isolation, no shared code path with this phase).**
- `RecoveryAlertsPolicy` (pure): `shouldRequestNotificationsOnArm()` + `shouldWarnBeforeArming()`
  (warn only when overlay AND notifications are both unavailable) + unit tests.
- New shared `RecoveryAlertPermissions` (moved verbatim out of `RecoveryAlertsGuard`, no duplicates)
  and `UninstallProtectionArmingGate` (ui → domain, one arming flow): duration picker →
  notification rationale + runtime `POST_NOTIFICATIONS` request at the arming moment →
  fail-visible "Recovery may be invisible" warning (Arm anyway / Enable alerts / Cancel) when both
  channels are missing. Wired into BOTH arming entry points (Settings + Home dialog containers);
  the gate calls the ViewModel arming callback only after the user passes it.
- Runtime `POST_NOTIFICATIONS` request with rationale at the moment Uninstall Protection is armed.
- If overlay is off **and** notifications are denied, show an explicit "recovery may be invisible"
  warning before arming protection (fail-visible, not fail-silent).
- Files: onboarding/settings UI + capability wiring. Manifest untouched.
- *Behavior changed: yes (new permission prompt).*

### Phase C — Non-adb recovery strategy (decision first, then code)
- Deliverable: short decision note comparing Device Owner, Shizuku, adb setup guide, screen-only.
- Recommended: in-app setup guide + Device Owner path for committed users; silent write when adb
  was granted.
- *Behavior changed: pending decision.*

### Phase D — Process-death backstop
- Evaluate: shorter backstop impossible with WorkManager's 15-min floor; options are an exact
  alarm (fragile on Android 12+ without `SCHEDULE_EXACT_ALARM`), a foreground service (battery +
  notification), or OEM auto-start permission onboarding (the real gate on Vivo).
- Recommend: OEM auto-start/background permission as a first-class onboarding step, keep the
  worker as backstop. Decide in D.
- *Behavior changed: pending decision.*

### Phase E — Onboarding semantics
- Separate "permission granted" from "onboarding completed". Persist explicit completion; never
  auto-complete from a transient a11y check.
- *Behavior changed: yes.*

### Phase F — Vivo V2355 verification matrix
1. Kill process from recents → recovery within ~15 s (protected ON).
2. Force-stop via `com.vivo.imanager` → recovery.
3. Turn a11y off in Settings → recovery; in-app state matches.
4. Restart phone → protection restored; boot cover does not leak launcher.
5. Turn Uninstall Protection OFF → turn a11y off → **no** recovery, no notification.
6. Timed lock armed → attempt disarm → challenge required.

### Phase G — Commit hygiene (only with approval)
Logical commits for the 121-file tree before further work.

---

## 6. Decisions needed from you

- **D-A** How do website users recover without adb: Device Owner, Shizuku, setup guide, or
  screen-only? (Determines Phase C.)
- **D-B** If recovery keeps failing, should the probe keep retrying every 5 min forever, or stop
  and show the recovery screen only?
- **D-C** Should taping Uninstall Protection require notification permission (recommended) or stay
  optional?
- **D-D** Proceed with Phase A now, or discuss the plan first? (Your instruction: plan first,
  then discuss.)
