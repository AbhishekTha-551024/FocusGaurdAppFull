# 🛡️ FocusGuard — Administrator & Support Portal

[![Vite](https://img.shields.io/badge/Vite-8.2-blue.svg)](https://vitejs.dev)
[![React](https://img.shields.io/badge/React-19.2-cyan.svg)](https://react.dev)
[![Firebase](https://img.shields.io/badge/Firebase-v12-orange.svg)](https://firebase.google.com)
[![Crypto-JS](https://img.shields.io/badge/Crypto--JS-HMAC--SHA256-green.svg)](https://cryptojs.gitbook.io/docs/)

> The official web-based administration and emergency support dashboard for **FocusGuard**. Used by administrators and authorized support staff to generate emergency rescue codes, manage accountability partnerships, and monitor system health.

---

## 📌 Dashboard Capabilities

### 1. Emergency Support Code Generator
- **Cryptographic PIN Calculation:** Generates 6-digit numeric rescue PINs matching FocusGuard's on-device `SupportCodeEngine.kt`.
- **Time-Windowed Rotation:** A code is signed for one `YYYY-MM-DD` date. The phone accepts it while that date is inside its grace window (yesterday / today / UTC-today / tomorrow) — not "until midnight" — and the local single-use ledger rejects it once applied.
- **Granular Action Targeting:**
  - `101`: Deactivate Device Admin & Uninstall Protection
  - `102`: Reset Active Challenge Lock
  - `103`: Reset Time-Tamper (clock rollback) Lock
  - `201`: Emergency Unblock of a specific package
  - `202`: Emergency Removal of a blocked keyword/domain
  - `203`: Disable Shorts & Reels filtering
  - `301`: Reset Daily Consumed Time Limit
  - `999`: Master Emergency Reset
- **One-Click WhatsApp Template:** Formats and copies a customer support reply message in one tap, including the per-action scope warning for the destructive codes (`101`, `999`).

### 2. Accountability Partner Administration (Tier 2 Cloud Hard Mode)
- **Firebase Firestore Linkage:** Real-time synchronization with client partnership documents (`partnerships` collection).
- **Remote Request Approval:** Enables partners or authorized admins to approve or decline user unlock requests.
- **Account Status Audit:** View linked partner email, verification timestamps, and active lockdown sessions.

### 3. Security Event Monitoring
- **Tamper Alarms:** View reported clock rollback attempts, multi-window split bypasses, and package uninstallation attempts.
- **Watchdog Health:** View client heartbeat records and whether the accessibility service reported itself active. (The on-device `ProtectionWatchdogService` is retired; recovery is handled by `AccessibilityHealthWorker` and the a11y self-heal path.)

---

## 🛠️ Technology Stack

| Component | Technology | Version |
|---|---|---|
| **Core Framework** | React | `19.2.8` |
| **Build Tooling** | Vite | `8.2.2` |
| **Icons** | Lucide React | `1.43.0` |
| **HMAC Cryptography** | Crypto-JS | `4.2.0` |
| **Cloud Database** | Firebase Cloud Firestore | `12.18.0` |
| **Authentication** | Firebase Auth | `12.18.0` |
| **Animations & FX** | Canvas-Confetti | `1.9.4` |
| **Linter** | Oxlint | `1.79.0` |

---

## 🚀 Getting Started

### 1. Prerequisites
- Node.js 18.x or higher
- npm 9.x or higher

### 2. Install Dependencies
```bash
npm install
```

### 3. Run Development Server
```bash
npm run dev
```
By default, the server will be available at `http://localhost:5173`.

### 4. Build for Production
```bash
npm run build
```
The optimized production bundle will be output to the `dist/` directory, ready to deploy to Firebase Hosting, Vercel, Netlify, or AWS CloudFront.

### 5. Preview Production Build
```bash
npm run preview
```

---

## 🔒 Security model

**Setup (required before anything can be signed).** Copy `.env.example` to `.env` and fill in
`VITE_SUPPORT_MASTER_SECRET` — the exact same value as `support.master.secret` in the app's
`local.properties` — plus `VITE_ADMIN_EMAILS` with the Google addresses allowed to open the console.
`.env` is git-ignored. Without the secret the panel refuses to generate (it will not fall back to a
default, because a code signed with the wrong secret looks perfectly valid here and simply fails on
every phone); without an allowlist it fails closed on the sign-in screen.

**What the code does today.**

1. **Operator gate** (`src/App.jsx`): Firebase Google sign-in plus an email allowlist from
   `VITE_ADMIN_EMAILS`. The console, device monitor and ticket queue render only after both pass.
2. **Attribution**: audit entries and ticket resolutions record the signed-in admin's email. If no
   session is present they write `UNATTRIBUTED` instead of a canned address.
3. **No PIN in the cloud**: `recordAuditLog` strips `code` before the Firestore write and stores
   `codeFingerprint` instead — the same SHA-256 identity the app's single-use ledger keeps. The raw
   PIN stays in this browser's `localStorage`, which is also what the Audit Trail tab reads.
   Resolved support tickets follow the same rule (`responseCodeFingerprint`).
4. **Honest validity wording**: codes are signed for a date and accepted by the app across a
   yesterday/today/UTC-today/tomorrow window, so templates and the result card say that instead of
   "expires at midnight", and say that a code is single-use.
5. **Date and Device-ID guards**: the date picker is clamped to the accepted window, malformed
   `FG-XXXX-XXXX` IDs are flagged before sending, and the accepted-date list is printed under the
   picker.

**Limits that are NOT solved by this panel — read before deploying anywhere.**

- Vite inlines every `VITE_*` value into the shipped JavaScript. **Publishing this panel to a public
  URL leaks the master secret to anyone who views source.** Run it with `npm run dev` on the
  operator machine. The permanent fix is a Firebase Cloud Function that holds the secret server-side
  and returns only the PIN to an authenticated admin — the panel is already structured so that
  `generateSupportCode` can be swapped for a single function call.
- The allowlist check runs in the browser. It stops casual access; it does not stop someone who
  loads the Firebase config in their own script. Firestore security rules are the real boundary and
  are **not stored in this repository** — they must be reviewed in the Firebase console.
- Anyone with a device's ID plus the secret can compute working codes for it offline. That is
  inherent to a zero-server design; it is mitigated by the single-use ledger and the attempt
  cooldown on the phone, not eliminated.

**Parity check** (Kotlin engine ⇄ this generator, run from `admin-panel/`):

```bash
node scripts/support-code-parity.check.mjs
```

It asserts the fixed vectors kept in
`app/src/test/java/com/focusguard/app/security/SupportCodeEngineTest.kt`. Run it after touching
`src/crypto/codeGenerator.js` or `SupportCodeEngine.kt` — the two implementations must change in the
same commit.
