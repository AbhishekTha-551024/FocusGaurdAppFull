# FocusGuard — Reinstall Data-Restore & Anti-Bypass Gap Analysis

Scope: analysis + hardening design only. No app code changed. Current AGENTS.md mode is
`REFACTOR ONLY. No new features.` — every item in section 6 is a **proposed feature** that
needs an explicit mode change before implementation. All findings below are grounded in the
actual source (file:line), not in the original design writeup.

---

## 1. The question

> Jab user app uninstall karke dubara install kare, toh previous blocked data (lists,
> websites, rules, active 30-day lock) dikhna chahiye ya nahi?

Answer: **Yes — it must, otherwise the 30-day commitment is trivially bypassable.** Today the
app restores *part* of it, and only when the user signs in with the same Google account. The
**timed uninstall lock is never restored at all**, and a reinstall that skips login opens a
fully unrestricted app. That is a real, open hole.

---

## 2. What the restore engine actually is

It is **Firestore + Firebase Auth**, not a Ktor/PostgreSQL backend.

- `sync/CloudSyncCoordinator.kt:39-55` — sync only starts when `authRepository.currentUser`
  emits a non-null user; when null it cancels all jobs and goes `IDLE`. **Restore is login-gated.**
- `CloudSyncCoordinator.kt:73-135` — per user: (1) restore Firestore → Room & prefs, (2) push
  local → cloud, (3) observe Room flows and mirror to Firestore.
- `CloudSyncCoordinator.kt:137-146` — 6 sections restored: app_blocklist, blocked_keywords,
  mindful_apps, schedules, time_limits, accountability/settings.
- `sync/SyncPaths.kt:9-56` — Firestore schema: `users/{uid}` with the subcollections above.
  Key is the **Firebase uid** (email/Google), not a device id.
- `sync/AppBlockSync.kt:25-53` — restore writes cloud docs into `BlockedAppDao`.
- `sync/AccountabilitySync.kt:34-90` — restores `isChallengeActive`, `challengeType`,
  `commitmentSentence`, `timeDelayHours`, with a "local-stronger" conflict rule (`:55-74`).
- `feature/auth/.../AuthRepositoryImpl.kt:62-95` — the auth listener only writes device status
  to Firestore when a user is already logged in; `:97-159` `syncUserDeviceToFirestore` writes a
  `devices/{deviceId}` doc that is **status reporting for support, not a restore path**.
- `AndroidManifest.xml:66` — `android:allowBackup="false"`, and there are **no**
  `fullBackupContent` / `dataExtractionRules` XML files. So Android Auto-Backup and device-to-
  device transfer carry nothing; uninstall deletes the DB and prefs. **Post-reinstall restore is
  exclusively via Firestore after re-login.**

---

## 3. What survives an uninstall → reinstall

| State | Where it lives | After reinstall |
|---|---|---|
| Blocked apps | Room `focusguard.db` + Firestore `users/{uid}/app_blocklist` | **Restored — only if same account signs in** |
| Blocked keywords | Room + Firestore `blocked_keywords` | Restored — login-gated |
| Mindful apps | Room + Firestore `mindful_apps` | Restored — login-gated |
| Focus schedules | Room + Firestore `schedules` | Restored — login-gated |
| Daily time limits | Room + Firestore `time_limits` | Restored — login-gated |
| Challenge config (commitment sentence, delay hours, active flag) | DataStore + Firestore `accountability/settings` | Restored — login-gated |
| **Timed uninstall lock (enabled / expiry / days)** | DataStore + SharedPreferences only (`core/datastore/PreferencesCore.kt:44-46`) | **LOST — never mirrored to Firestore** |
| **UptimeLedger monotonic anchor** | device-protected storage (`core/datastore/UptimeLedger.kt:41`) | **LOST — wiped on uninstall** |
| Device-admin grant | system Settings | **LOST — must be re-granted manually** |
| Accessibility-service enablement | system Settings | **LOST — must be re-granted** |
| Custom device id `FG-####-####` | app data (`ui/onboarding/.../OnboardingPreferences.kt:148-166`) | **Regenerated blank — cannot key remote state** |

Note: there is no user-editable "blocked websites" table — Room (`core/database/FocusGuardDatabase.kt:9-34`)
has no website entity. Site/NSFW blocking is the preset `AdultDomainRegistry` + VPN + keywords,
so "restore blocked websites" is not a user-data restore in the way the writeup's matrix implies.

---

## 4. The bypass sequences, ranked by how easy they are

1. ~~**Reinstall + do not sign in.** Fresh app, no lock, blocklists empty.~~ **Corrected after
   implementation started:** sign-in is mandatory — `feature/auth/ui/LoginScreen.kt:31` documents
   "No 'Skip' option — sign-in is required to proceed", and `OnboardingActivity` renders only the
   login wall while `authState.user == null`. An unauthenticated reinstall therefore shows no
   unprotected app. The exposure that remains on this path is timing, not content: nothing consulted
   the device record before the login wall existed, and that is what the new restore gate adds.
2. **Reinstall + sign in.** Blocklists/rules/challenge come back, but the **timed uninstall lock
   does not** — it is not in Firestore. The user is left with no time-locked protection and can
   uninstall again immediately.
3. **Reinstall + sign in with a different / throwaway account.** Pulls that account's (empty)
   state; original commitment never resurfaces.
4. **Date-change attack.** *Already closed* (see section 5) — but irrelevant after a reinstall,
   because there is no lock left to time-travel past.

Factory reset and "sign in on a brand-new device" remain out of scope for a purely local-lock
design and are called out honestly in section 7.

---

## 5. What is already strong (do not regress it)

- `blockers/TimeTamperGuard.kt` — detects wall-clock rollback (`:80`) and forward jumps that
  lack matching `elapsedRealtime` drift (`:86-92`), resyncs on legitimate carrier/timezone
  changes (`:40-46`), and resets cleanly on reboot (`:68-74`).
- `core/datastore/UptimeLedger.kt` — monotonic uptime proof built only from
  `SystemClock.elapsedRealtime` (Invariant Rule 4), stored in device-protected storage, with a
  **capped single step** when the boot-identity chain breaks (`:86-105`), so repeated reboots or
  clock shifts can never buy more credit than real time costs. Under-crediting is fail-closed.
  `msSinceProtectionArmed()` (`:137-149`) re-pins a missing stamp and returns 0, so a lock armed
  before the ledger existed stays active rather than reading as "infinitely elapsed".
- `feature/settings/.../ProtectionRepositoryImpl.kt:106-156` — a timed lock counts as lapsed only
  when **both** wall clock and monotonic ledger agree; verdict published to DE storage
  (`:164-176`) for direct-boot readers.
- `UptimeLedger.KEY_TIMED_LOCK_STILL_RUNNING` (`:188-196`) defaults **true** — the direct-boot
  recovery reader is fail-closed.

Precise nuance on the writeup's "fail-closed on state loss": the *recovery reader* is fail-closed,
but the *config source of truth* `settingsSnapshot.isUninstallProtectionEnabled` defaults **false**
(`PreferencesCore.kt:205`). On a genuinely fresh install that is arguably correct (nothing was ever
armed) — but it means there is no local fail-closed backstop if a real lock's state is wiped. The
only durable backstop can be the server, which today has no record of the timed lock.

---

## 6. Hardening design (proposed feature — needs mode change to build)

Goal: after reinstall, an active commitment re-arms **before** the user can reach an unrestricted
app, and does not depend on the user choosing to sign in. Layered per Clean Architecture.

### 6.1 Reinstall-stable device identity (the missing primitive)
- Introduce a `DeviceIdentitySource` **port** (domain) with an adapter returning a value that
  survives uninstall. Options, in order of preference:
  - `Settings.Secure.ANDROID_ID` — persists across reinstall (per device + per signing key since
    Android 8), resets on factory reset. Currently **unused** anywhere.
  - Firebase Installations ID (`FirebaseInstallations`) — survives reinstall, ties to the app/GCP
    project.
  - Keep the existing `FG-####-####` only as a human-readable support label, never as a key.
- Caveats to document: ANDROID_ID is not guaranteed unique across all OEM ROMs and is not a
  security credential; it identifies a *device*, not a *person*. Use it as a lock-rearm hint, and
  still bind the authoritative account to Firebase uid when the user does sign in.

### 6.2 Server-stored lock state (domain + data)
- Pure domain model, e.g. `CommitmentLockState(deviceId, isTimedLockActive, expiryWallMs,
  armedUptimeBaseMs, days, updatedAtServerMs)` in `domain/` (no `android.*`).
- New Firestore location keyed by the stable identity (e.g. `device_locks/{androidId}`) storing the
  timed-uninstall-lock fields that are **not synced today**. Write on arm and on any change; the
  existing `AccountabilitySync` "local-stronger" pattern (`:55-74`) is the template.
- **Never trust a lower value from the client** — enforce max(local, server) on days/expiry, and
  treat server as authoritative for "was a lock ever armed on this device".

### 6.3 Pre-login cold-start re-arm gate (presentation/app startup)
- On `FocusGuardApplication` / first Activity, before onboarding is allowed to complete, do a
  one-shot keyed lookup by the stable identity. If the server reports an active commitment,
  re-arm the local timed lock + device-admin + accessibility prompts and block the UI until done.
- This is the piece that closes bypass #1 (no sign-in). It requires the identity in 6.1 to be
  readable without an authenticated user.

### 6.4 Fail-closed defaults
- Flip the fresh-install default for `isUninstallProtectionEnabled` to follow the server verdict
  rather than a hard `false`, and make "state lost but server says active" resolve to **locked**.

### 6.5 Threat model — what this closes and what remains
- Closes: reinstall-without-login, reinstall-then-no-lock, throwaway-account downgrade.
- Partially closes: offline reinstall (needs cached/last-known or a grace that fails closed).
- Does NOT close (be honest with users): factory reset (wipes ANDROID_ID + server can't prove
  identity), signing in on a genuinely new device, or a determined user with a second phone. Those
  need account-level commitment escrow and are a product decision, not a code fix.

### 6.6 Play-policy / privacy notes to resolve before building
- Collecting a device identifier + server-side lock enforcement must be disclosed in the privacy
  policy and match Play's Data Safety + device-identifiers policy (this app is distributed via the
  user's own site, but the policy still matters).
- Encryption-at-rest for the lock doc (the writeup says "Server-Side Encrypted") — decide whether
  Firestore field-level crypto is actually required or TLS + access rules suffice.

---

## 7. Suggested phasing (only when REFACTOR-ONLY is lifted)

1. Add `DeviceIdentitySource` (ANDROID_ID/install-id) + pure `CommitmentLockState` domain model +
   unit tests. No behavior change yet.
2. Mirror the timed-lock fields to Firestore on arm/change; add restore read. Guarded, still
   login-assisted.
3. Add the pre-login cold-start re-arm gate + fail-closed default. This is the security payoff.
4. Verification matrix (below) + a real uninstall/reinstall test on the Vivo V2355.

### Test / verification matrix
| Scenario | Expected |
|---|---|
| Active 30-day lock, 5 days left → uninstall → reinstall → **no login** | Reinstall identity lookup re-arms lock; onboarding blocked until re-armed |
| Reinstall → sign in same account | Blocklists + timed lock both restored; remaining days preserved (max of local/server) |
| Reinstall → sign in throwaway account | Device-identity re-arm still restores the original lock independent of account |
| Reinstall → change device date | TimeTamperGuard + UptimeLedger keep lock active (already true today) |
| Server unreachable at first boot | Fails closed: lock stays active / onboarding gated, no unrestricted window |
| Factory reset | Documented residual risk (identity gone) — not claimed as closed |

---

## 8. Decisions that need the owner

1. Which reinstall-stable identity: ANDROID_ID vs Firebase Installations ID (or both).
2. Is a pre-login network lookup acceptable (privacy + first-run UX latency), or should re-arm be
   login-assisted only?
3. How to treat factory-reset and new-device cases in the product promise ("unbreakable" claim vs
   what is technically enforceable).
4. Confirm switching AGENTS.md out of REFACTOR-ONLY before any of section 6 is built.

---

## 9. Built (phase 1, after the mode switch to BUILD)

Decisions taken: identity = `Settings.Secure.ANDROID_ID`; pre-login lookup allowed; fail-closed on
"server says active"; server is add-only, never a release authority.

| Layer | New / changed file | Role |
|---|---|---|
| domain | `domain/model/CommitmentLockState.kt` | one shape for local + server lock state |
| domain | `domain/policy/CommitmentRestorePolicy.kt` | pure reconciliation: `decide()`, `isOwedAt()`, `localCoversServer()`, `reArmDays()`, and the single add-only merge rule `isStrongerThan()` used by the Firestore adapter |
| domain | `domain/repository/DeviceIdentityRepository.kt`, `CommitmentLockRepository.kt` | ports (+ `CommitmentRead` / `CommitmentWrite` outcomes) |
| data | `data/identity/AndroidIdDeviceIdentityRepository.kt` | ANDROID_ID adapter, blank instead of a guess |
| data | `data/commitment/FirestoreCommitmentLockRepository.kt` | `device_locks/{id}` read + transactional add-only mirror |
| data | `sync/SyncPaths.kt` | collection + field name constants (wire schema) |
| data | `core/datastore/ProtectionPreferences.kt` | `localCommitmentState()`, `reArmRestoredTimedProtection()`; the three cache-write sites now share one helper |
| app | `commitment/CommitmentRestoreEngine.kt`, `CommitmentRestoreStatus.kt` | cold-start lookup w/ bounded retries + local-change mirror watcher |
| app | `FocusGuardApplication.kt` | starts the engine after unlock in both startup branches (lazy, direct-boot safe) |
| ui | `ui/commitment/CommitmentRestoreGate.kt`, `OnboardingViewModel/Activity` | first-run gate while the lookup is unresolved |
| di | `di/CommitmentDataModule.kt` | both port bindings |
| ops | `docs/firestore-device-locks-rules.md` | the console ruleset that makes the record non-weakening (required step) |
| tests | `CommitmentRestorePolicyTest` (16), `SyncPathsTest` (+6 asserts), `TimedLockMonotonicGateTest` (10) | restore decisions, add-only merge rule, wire-schema snapshot, restored lock re-proves its duration on this device's monotonic clock |
| docs | `AGENTS.md` | mode switched REFACTOR ONLY → BUILD; `FG:Commitment` added to the logging-tag list (engine + repository share it) |

Deviations from §6, on purpose: no `armedUptimeBaseMs` in the model (uptime proof is device-local;
`UptimeLedger` is the only valid source, so a restored lock re-proves its duration here); the gate is
**bounded** rather than infinite so an offline fresh install is not bricked; permanent (untimed)
protection is still not mirrored — timed locks only.

Not yet done: real uninstall/reinstall run on the Vivo V2355 with the ruleset published, and the §7
verification matrix end to end.
