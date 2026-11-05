# FocusGuard — Agent Rules (read fully before every task)

## Project
Android app (Kotlin, Compose, Hilt, Room, DataStore, Firebase). Accessibility-based focus blocker. Test device: Vivo V2355.

## Mode
- Current mode: BUILD. New features are allowed. Every new behavior must still be justified, layered (ui -> domain -> data), covered by a unit test where it is pure logic, and verified before reporting.
- Behavior changes are allowed but must be declared explicitly in the report ("behavior changed: yes" + what changed for the user).
- Work on ONE phase/task at a time. Stop and report after it. Never continue to the next phase on your own.

## Workflow (every task)
1. Show a short plan and the list of files you will touch. Wait if the task is risky.
2. Touch ONLY the files in the plan. If you need another file, say so first.
3. After changes run: .\gradlew.bat assembleDebug and .\gradlew.bat testDebugUnitTest. Fix errors before reporting.
4. Report: files changed, line counts, what moved, and "behavior changed: yes/no".
5. If unsure, write UNSURE and ask. Never guess.

## Code rules
1. Max 300 lines per file. Propose a split before editing a larger file.
2. One file = one responsibility.
3. Layers: ui -> domain -> data. ViewModels must NOT use DAOs directly. Compose must NOT use Room @Entity classes.
4. Domain layer is pure Kotlin: no android.* imports.
5. No mutable static/companion state. Use Hilt @Singleton.
6. No unused code, no uncollected Flows, no duplicate helpers.
7. No magic numbers or hardcoded package names inline. Use named constants.
8. Never log URLs, search queries, passcodes or secrets.
9. When moving code, copy logic EXACTLY. Do not "improve" it in the same step.
10. Never delete a file without first showing a grep of its references.

## Protected invariants (never break)
1. Global block navigation: for eligible app blocks, send one BACK before mounting the overlay, then send HOME only after the overlay is attached. Do not send an additional automatic HOME on countdown dismissal; a deliberate tap on "Leave this app" remains a separate user action. Confirmed FocusGuard launcher protection follows the same BACK -> overlay -> HOME order; unknown/other launcher targets remain untouched. System Settings/installer tamper keeps its existing tapjacking-safe BACK -> HOME -> delayed-overlay sequence. Deep Focus kiosk blocks must not send HOME because that would hide the session lock.
2. First check in event processing: skip our own package.
3. Never run Room/DataStore queries on the accessibility thread. Use in-memory caches.
4. Use SystemClock.elapsedRealtime() for timers and tamper checks.
5. Launcher protection blocks ONLY FocusGuard. Classify target as FOCUSGUARD / OTHER / UNKNOWN. UNKNOWN and OTHER are ignored. Never use a guess or fallback to decide FOCUSGUARD.
6. Anti-cloning has no UI toggle.
7. Unblock, limit change or protection disable requires an active challenge.

## Never touch without explicit permission
- LauncherEventHandler, LauncherTargetResolver, LauncherPopupDetector (just fixed, test-verified)
- BlockOverlayManager window type (TYPE_ACCESSIBILITY_OVERLAY)
- AndroidManifest.xml, build.gradle.kts, ProGuard rules, Firebase config

## Verification
Changes to service, launcher, overlay or handlers need a manual test on Vivo V2355 before commit:
long-press FocusGuard -> overlay shows; long-press YouTube/WhatsApp -> normal menu, no overlay.

## Logging tags
FG:Service, FG:Launcher, FG:Settings, FG:Installer, FG:Keys, FG:TargetApp, FG:Reels, FG:Keywords, FG:Mindful, FG:Commitment, FGBlockOverlay.
