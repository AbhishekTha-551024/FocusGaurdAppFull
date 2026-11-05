# FocusGuard — Feature & Safety Review

**Date:** 2026-10-30 · **Branch:** `fix-boot-cover-overlay` · **Scope:** whole repository (app + admin panel + build/config)

**How this was produced.** Static reading of 296 Kotlin files (~42,200 LOC) and 10 panel source files, plus the
following executed checks: `:app:testDebugUnitTest` → **99 tests, 0 failures** (includes the new 32-test
`SupportCodeEngineTest`); `:app:compileDebugKotlin` clean; `admin-panel`: `npm run build` clean and
`node scripts/support-code-parity.check.mjs` → all parity vectors pass (Kotlin ⇄ JS byte-identical).
**Not verified at runtime:** behaviour on real OEM devices, the Firebase console's Firestore security rules (not in
this repo), and anything requiring a phone in hand.

---

## 1. Project map

| Area | Contents | Notes |
|---|---|---|
| `app/src/main/java/com/focusguard/app/` | 296 `.kt`, ~42.2k lines | 21 packages |
| `blockers/` | Reel, Keyword, Adult, MultiWindow, TimeTamper, AntiCloner, SessionBlock | in-process detectors |
| `service/` | AccessibilityService (**158.7 KB**), BootCover, FocusSession, AdultContentVpn, ProtectionWatchdog (retired) | the enforcement core |
| `tracker/` | BrowserUrlTracker (40.7 KB), heartbeat | URL extraction + a11y polling |
| `core/` | database (Room, 11 entities / 8 DAOs), datastore (`FocusGuardPreferences`, 46.7 KB), engine (decision rules), designsystem, di | |
| `data/` | `FocusGuardRepository` (**62.2 KB**, god-object) + 5 focused repos | |
| `feature/` | 8 vertical slices: appblocker, auth, home, keywordblocker, mindful, schedules, session, settings, timelimits | 16 `*Screen` composables |
| `overlay/` | block / mindful / deepfocus / wallpaper overlays + `BlockOverlayManager` | |
| `receiver/`, `worker/`, `observer/`, `recovery/` | Boot, DeviceAdmin, NewAppInstall, PackageRemoved; 3 Workers; a11y self-heal via `WRITE_SECURE_SETTINGS` | |
| `sync/`, `security/`, `usage/`, `capability/` | CloudSyncEngine (27.5 KB), SupportCodeEngine + IntentValidationEngine + AuthConfig, UsageStatsReader/evaluators, permission→feature mapping | |
| `admin-panel/` | Vite + React support console | now gated, see §5 |
| `admin/`, `scratch/` | legacy Python generator + decompile scripts | candidates for deletion |
| `ECC/` | vendored coding-rule pack | the standard this repo follows |

**Biggest structural risk:** three files hold the app together — AccessibilityService (158 KB), Repository (62 KB),
Preferences (47 KB). Any new feature lands in one of them unless the split happens first.

---

## 2. Feature inventory (current maturity)

| Feature | Maturity | Evidence |
|---|---|---|
| App block + full-screen overlay | Complete | `overlay/block/BlockOverlayManager.kt`, `service/FocusGuardAccessibilityService.kt` |
| Keyword / URL blocking + incognito + unsupported-browser guard | Complete | `blockers/KeywordBlocker.kt`, `tracker/BrowserUrlTracker.kt` |
| Reels/Shorts blocking (IG/YT/FB) | Complete | `blockers/ReelBlocker.kt`, `ReelTargetRegistry` |
| Per-app daily time limits | Complete | `feature/timelimits/`, `usage/TimeLimitEvaluator` |
| Schedules (weekday mask + allowed apps) | Complete | `feature/schedules/`, `usage/ScheduleEvaluator` |
| Mindful pause + intent journal | Complete | `feature/mindful/`, `overlay/mindful/` |
| Deep Focus session (call-priority, suitcase-style lock) | Complete | `overlay/deepfocus/`, `feature/session/`, `service/FocusSessionService` |
| Uninstall protection (timed window + Device Admin) | Complete | `receiver/FocusGuardDeviceAdminReceiver.kt`, `isProtectionReallyActive()` |
| Accountability challenge (typing / partner / delay) + pending-change delay | Complete | `ui/challenge/`, `worker/PendingChangeApplyWorker` |
| OEM shield (Vivo/Samsung/Xiaomi/OnePlus) | Complete | `ui/PROTECTION*`, `worker/AccessibilityHealthWorker` |
| Adult-content DNS/VPN + SafeSearch | Complete | `service/AdultContentVpnService.kt` |
| Anti-cloner / dual-app, multi-window, clock-tamper guards | Complete | `blockers/AntiClonerEngine.kt`, `MultiWindowGuard.kt`, `TimeTamperGuard.kt` |
| Partner invites + in-app support requests (Firestore) | Complete | `feature/auth/`, `sync/CloudSyncEngine.kt` |
| Emergency Support Passcode (offline HMAC, single-use) | Complete (hardened this session) | `security/SupportCodeEngine.kt` + `SupportCodeEngineTest.kt` |
| Cloud rule sync | Partial | `sync/CloudSyncEngine.kt:409-424` merges additively; admin/wallpaper/session settings are excluded |
| `BlockDecisionEngine` rule chain | Partial | one live call site (`FocusGuardAccessibilityService.kt:1636`), OR-ed with legacy fast caches, so it is not yet the single authority |
| Settings tab | **Effectively empty** | see §3.4 |

---

## 3. Dead ends and broken promises (fix or remove — these cost trust)

1. **`FocusGuardRepository.kt:1015` — `securityEvents: Flow<List<SecurityEvent>>` has exactly one reference (its
   declaration) and is never collected.** The Room `SecurityEventDao` writes tamper events (clock rollback,
   multi-window, uninstall attempt) that **no screen ever shows**, and `getByType()` is never called. The app
   detects attacks and hides the evidence from its own user.
2. **`SettingsViewModel.kt:138-139` — `toggleSearchShield()` and `toggleNotificationsBlock()` are dead.** No UI
   calls them, and `SettingsFeedUseCase.kt` has no `"search_shield"` / `"notifications"` branch (verified branches:
   `shorts_reels, incognito, instagram, facebook, youtube, auto_block, unsupported_browsers, adult_content`), so they
   are silent no-ops that look like features.
3. **`FocusGuardRepository.kt:1077 setAntiCloningShieldEnabled()` has zero callers**, while
   `SettingsUiStateBuilder.kt:50` reads the flag — the anti-cloning shield cannot be turned on or off by the user.
4. **`SettingsScreen.kt:38-52`** accepts ~10 feed/overlay/wallpaper callbacks and uses each **once (the declaration
   only)**; the real controls render from `HomeScreen.kt:197,234`. Result: "Settings" contains Account / Support /
   About and no blocking configuration.
5. **`service/ProtectionWatchdogService.kt:61`** — *"retired — stopping immediately"*, yet still declared in the
   manifest and still restarted by `worker/AccessibilityHealthWorker.kt:42`.
6. **`ui/capability/CapabilityExplanationSheet.kt` and `ui/components/SkeletonLoading.kt`** are referenced nowhere.
7. **`capability/CapabilityManager.kt:281`** promises the user *"Visual daily usage analytics and habit trends"* —
   no such screen exists anywhere (usage stats are only consumed by `TimeLimitEvaluator`).
8. **`README.md:308`** documents `worker/HealthAuditWorker.kt`, which does not exist.
9. **`receiver/NewAppInstallReceiver.kt:34-38`** deliberately skips `EXTRA_REPLACING`: a **reinstall-by-update of a
   previously auto-blocked app slips past auto-blocking**.
10. **`res/xml/device_admin_policies.xml:8-13`** notes force-lock is excluded "until that decision is made" — the
    decision is still open (see §5 A6).

`TODO`/`FIXME`/`Coming soon` greps return nothing, so this is not sloppy-comment debt; it is unfinished wiring.

---

## 4. Features worth adding

### P1 — users will choose the app on these

| # | Feature | Why | Where it lives | Effort |
|---|---|---|---|---|
| F1 | **Screen-time dashboard** (today/week graph, top apps, blocked-attempt count) | The data already exists (`UsageStatsReader`, `usage/` + `securityEvents`) and the onboarding text already promises it (`CapabilityManager.kt:281`). Without it, the app has no "did this work?" feedback loop. | new `feature/statistics/` + Home card | M |
| F2 | **Tamper/Activity log screen** ("Protection history") | §3.1: every bypass attempt is recorded and invisible. Trust in a blocker comes from seeing the attempts it stopped. | `feature/settings/` new section collecting `repository.securityEvents` | S |
| F3 | **Local backup & restore of all rules** (single JSON via `ACTION_CREATE_DOCUMENT`) | `grep` for `FileProvider`/`ACTION_CREATE_DOCUMENT` = 0 hits. Today a lost phone, a data clear, or a factory reset silently destroys every block, schedule and challenge the user built. Cloud sync is opt-in and partial. | `core/backup/` + Settings entry | M |
| F4 | **Global daily cap + bedtime window** (whole-device, not per-app) | `AppTimeLimitEntity` is per-package. The classic addiction pattern — 15 min on each of 12 apps — defeats it. | new `feature/wellbeing/` + entity | M |
| F5 | **Fix the Settings tab** (move feed/overlay/wallpaper/incognito controls back where users look, keep Home as the action surface) | §3.4; discoverability and the reason the tab exists. | `SettingsScreen.kt` + reuse `DistractionFeedControlCard`, `OverlayWallpaperCard` | S |
| F6 | **Strict / Hard mode** (self-lock: no support code, no edits, cooling-off only) | Currently every lock can be undone by one code from support; a user who *wants* to be unable to give in has no option. Needs the server-side revocation in §5 B3. | `feature/settings/` + `SupportCodeEngine` action gate | M |

### P2 — retention and reach

| # | Feature | Why | Where |
|---|---|---|---|
| F7 | Allow-list ("people first": contacts, maps, health, employer apps) as a permanent, separate list | Today only per-schedule allowed apps exist (`feature/schedules`); a blocker that blocks the wrong thing gets uninstalled. | `core/database` new entity |
| F8 | One-time / expiring blocks and "pause until Monday" (vs permanent) | `BlockedAppEntity` has no expiry field; everything is forever-or-nothing. | `feature/appblocker/` |
| F9 | Notification guard (block the *pull* — Instagram/YouTube notifications during a focus window) | No `NotificationListenerService` anywhere; the `toggleNotificationsBlock` stub (§3.2) shows it was intended. | `service/` new listener |
| F10 | Quick-Settings tile + home-screen widget (start focus, show today's remaining minutes) | No `TileService`/`AppWidgetProvider`. Low-friction activation drives daily use. | `feature/quicktile/` |
| F11 | Push-delivered partner approvals (FCM) | `firebase-messaging` is declared in the version catalog (`gradle/libs.versions.toml:80`) but **never added to `app/build.gradle.kts`**, and there is no `FirebaseMessaging` code — so a partner must have the app open to see an unlock request. | `di/` + `receiver/` |
| F12 | Streaks / weekly reflection export (shareable) | Accountability apps retain on progress artefacts; the events to compute them already exist. | `feature/statistics/` |
| F13 | Work-profile / child-profile mode (block per profile, one device) | `PackageRemovedReceiver.kt:39` already checks `myUserId`, so the awareness exists; multi-profile management does not. | `receiver/` + onboarding |

### P3 — polish and reach

F14 Localization — `res/values-*` does not exist and `strings.xml` has 3 strings; every label is hardcoded in
Compose. For an India-first wellbeing app, Hindi/Tamil/Telugu copy is cheap and differentiating.
F15 In-app changelog / "what's new" after `MY_PACKAGE_REPLACED` (already received by `BootReceiver.kt:64`).
F16 Wear OS companion for Deep Focus. F17 Crashlytics/analytics (no dep today, so crashes are invisible).
F18 Delete `admin/` + `scratch/` (legacy Python generator and decompile scripts) or move to `tools/` with a README —
they currently read as part of the product.

---

## 5. Safety: what to add next

### A. Against the user's own future self (the product's actual job)

| # | Gap | Evidence | Fix |
|---|---|---|---|
| A1 | **Settings opens with zero friction** — the support-code dialog, toggles and app unblocking sit behind no challenge | `feature/settings/ui/SettingsScreen.kt` (no gate); challenge exists only for pending blocks | add an opt-in "Guard the guard" gate: mindful-pause (30 s) or the typing challenge before any blocker can be turned off |
| A2 | **No `FLAG_SECURE` anywhere** (grep = 0) | overlays can be screenshotted/recorded; more importantly, the block screen can be captured and the *absence* of a block inferred | set `FLAG_SECURE` on `BlockOverlayManager`, `MindfulOverlay`, `DeepFocusOverlay` windows |
| A3 | **Clear-data resets protection** — every flag lives in DataStore; Device Admin stays active while the app forgets it | `core/datastore/FocusGuardPreferences.kt` + `isProtectionReallyActive()` | on cold start, if an active admin exists but local protection says "off", treat as tamper: re-arm + write a `SecurityEvent` + surface the recovery flow |
| A4 | **Safe mode / quick-boot spoofing** | `BootReceiver.kt:62-66` accepts `QUICKBOOT_POWERON` and `com.htc.…`, which are not protected broadcasts | keep handling them but require `isProtectionActive()` + a recorded boot timestamp; ignore otherwise |
| A5 | **App *updates* bypass auto-block** | `NewAppInstallReceiver.kt:34-38` | on `EXTRA_REPLACING`, re-check the previous block list instead of returning early |
| A6 | **Force-lock unused** | `device_admin_policies.xml:8-13`; `lockNow` = 0 hits | opt-in "lock screen on repeated bypass" for Deep Focus sessions |
| A7 | **Deep-link/PWA escape** for the blocked platforms is unhandled — no `vnd.youtube`, `instagram://`, or custom-scheme rules found in `BrowserUrlTracker.kt` | extend the scheme + host matcher to `SessionBlockManager` decisions |
| A8 | **Device Owner path exists but is never offered** (`FocusGuardDeviceAdminReceiver.kt:39,126` — `setUninstallBlocked(true)` makes even `adb pm uninstall` fail) | add an onboarding branch that explains the adb-provisioning step for power users; it is the strongest protection the app can have |
| A9 | Volume-key double-counting (analysed earlier, unfixed): the key-filter path does ≥3 binder calls plus state mutation on a saturated a11y thread, so one press reads as a held key | `service/FocusGuardAccessibilityService.kt:2712-2760` | move the decision off the a11y thread, drop the mutation from the getter, and re-check `FLAG_REQUEST_FILTER_KEY_EVENTS` necessity (`:315`, `notificationTimeout = 0` at `:318`) |

### B. Against attackers, other apps, and data leaks

| # | Gap | Evidence | Fix |
|---|---|---|---|
| B1 | **PII in release logs**: `AuthRepositoryImpl.kt:152` logs the user's **email and Device ID** at `Log.i`; `CloudSyncEngine.kt:104` logs the email. 488 `Log.*` calls total. | `adb logcat` on a rooted/backup-extracted device, or any future bug-report attachment, carries account identities | redact (log `emailHash`, last-4 of deviceId), strip `Log.d/i` from release via R8 `assumenosideeffects` in `app/proguard-rules.pro` |
| B2 | **Cloud payloads are trusted as-is** — `CloudSyncEngine.kt:255-420` reads `packageName`, `keyword`, `challengeType` etc. with no sender/integrity check; it fails safe on *removals* (`:421-424`) but anyone who can write a victim's doc can **add** blocks (harassment / lock-out). | Firestore rules are not in this repo | (i) commit `firestore.rules` to the repo and scope writes to `request.auth.uid == userId`; (ii) sign server-written payloads with an HMAC the app verifies before applying |
| B3 | **The master secret is inside every shipped APK and inside the panel bundle** — offline HMAC is a shared-secret scheme, so one extracted secret mints codes for all devices. | `app/build.gradle.kts` `SUPPORT_MASTER_SECRET`; `admin-panel/.env.example` note | long term: move signing to an authenticated Cloud Function and make the client verify a **server signature per device+date** (device-specific key derivation), with a rotation path; keep the offline scheme only as a degraded fallback |
| B4 | **No server-side revocation or rate limit** — the cooldown and single-use ledger are local only, so a factory reset or clear-data wipes them. | `core/datastore/FocusGuardPreferences.kt` ledger; B3 in the earlier audit | require sign-in for `101`/`999` codes and check a Firestore "used codes" doc keyed by fingerprint; log every attempt server-side |
| B5 | **Cold-start race**: caches (`cachedBlockedPackages`, `cachedTimeLimits`) are read before Room hydration completes, so the first seconds after boot/cold-start can be unprotected. | earlier audit B19, still open | gate enforcement on a `rulesReady: StateFlow` and keep `BootCoverService` up until it flips |
| B6 | **Forward clock set → cooldown bypass** (B18, still open): the cooldown end-time is compared against device time. | `FocusGuardRepository.kt:1175-1180` | store attempt timestamps monotonically (`SystemClock.elapsedRealtime` anchor) and refuse to honour future-dated cooldowns |
| B7 | **`WRITE_SECURE_SETTINGS` self-heal is a double-edged sword**: whoever granted it by adb can also revoke a11y silently. | `recovery/AccessibilityRecoveryHelper.kt:27` | acceptable trade, but record an event and notify the user whenever a11y state changes outside the app |
| B8 | `app/debug.keystore` is committed (tracked), release has **no signingConfig** — only `debug` is defined in `app/build.gradle.kts`. | `git ls-files` | document/produce a real release keystore outside git (`$GRADLE_USER_HOME`/CI secret); the committed debug keystore is harmless but should be gitignored, and note `app/google-services.json` is tracked (fine for a public Firebase project, still worth a look) |
| B9 | **No CI at all** (`.github/workflows` absent). | — | add a pipeline that runs `assembleDebug`, `lint`, `testDebugUnitTest`, the JS parity check, `gitleaks`/secret scan, and fails on any tracked `*.keystore` |

### C. Panel (already improved this session; what remains)

Landed: operator sign-in + `VITE_ADMIN_EMAILS` allowlist (fails closed), real attribution instead of
`support-agent@focusguard.app`, PIN stripped from Firestore audit logs and tickets (fingerprint only), action `103`
added so time-tamper locks are issuable, date picker clamped to the accepted window, malformed Device-ID warning,
honest validity/single-use wording, per-action scope warnings for `101`/`999`, missing-secret hard failure, and a
repeatable parity harness (`admin-panel/scripts/support-code-parity.check.mjs`).

Remaining: the panel is still a **client-side signer** (B3), there is no **two-person rule** for `999`, and the audit
tab's "Immutable records" wording overstates a localStorage log.

---

## 6. Test and quality gaps

Unit tests: **8 files / 1,254 lines vs 42,195 production lines** — 99 tests pass, coverage of the risky parts is near
zero. Missing entirely: all ~30 use-case classes, 9 of 10 ViewModels, every repository and DAO,
`TimeLimitEvaluator` / `ScheduleEvaluator`, `AdultContentVpnService` (DNS packet builder), `ReelBlocker`,
`MultiWindowGuard`, `AntiClonerEngine`, `CloudSyncEngine`. No `androidTest` source set, no Robolectric/mockk/Jacoco in
`app/build.gradle.kts`. Highest value-per-minute: rule evaluators + migration/ledger round-trip + CloudSync merge
(freshness, expiry, and ledger semantics are exactly where an unlock can silently fail).

Architecture debt: `FocusGuardRepository` (62 KB) and the AccessibilityService (158 KB) are untestable by
construction. Split along the existing `feature/*` seams before more capabilities are added.

---

## 7. Suggested order

1. **Trust repair (S):** §3 items 1-5, 7, 8 — surface the security-event log, delete or finish the two dead toggles,
   wire the anti-cloning setter, move Settings controls back to Settings, drop the retired watchdog, correct
   `README.md:308` and the analytics promise.
2. **Safety quick wins (S-M):** B1 log redaction + R8 strip, A2 `FLAG_SECURE`, A5 update re-block, A3 clear-data
   re-arm, B9 CI skeleton.
3. **Data durability (M):** F1 screen-time dashboard, F3 export/import, F2 protection history.
4. **Real lockdown (M):** F6 hard mode + B4 server-side revocation + B2 committed Firestore rules, then B3 signing
   endpoint so the secret can rotate.
5. **Reach (M-L):** F4 global cap/bedtime, F7 allow-list, F9 notification guard, F10 tile/widget, F11 FCM.

---

## 8. What could not be verified from static reading

- Firestore security rules (absent from the repo) — B2 and the panel's allowlist both assume they are strict.
- Runtime behaviour of overlays and key filtering on Vivo/Samsung/Xiaomi/OnePlus (the OEM matrix is where most
  residual bypass risk lives).
- Whether the release build actually signs with anything (no release `signingConfig` is defined).
- Live Firebase project settings (auth providers, app check, abuse quotas) and the real device population.
- Git working-tree state: the tree already carried unrelated staged/unstaged changes (deleted `AGENTS.md`,
  `ARCHITECTURE.md`, legacy `data/datastore`, `data/db/*` paths) before this session; nothing was committed here.
