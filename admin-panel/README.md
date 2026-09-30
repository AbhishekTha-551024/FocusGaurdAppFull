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
- **Time-Windowed Daily Security:** Codes automatically rotate and expire at midnight local time (`YYYY-MM-DD`).
- **Granular Action Targeting:**
  - `101`: Deactivate Device Admin & Uninstall Protection
  - `102`: Reset Active Challenge Lock
  - `201`: Emergency Unblock of a specific package
  - `202`: Emergency Removal of a blocked keyword/domain
  - `203`: Disable Shorts & Reels filtering
  - `301`: Reset Daily Consumed Time Limit
  - `999`: Master Emergency Reset
- **One-Click WhatsApp Template:** Formats and copies a customer support reply message in one tap.

### 2. Accountability Partner Administration (Tier 2 Cloud Hard Mode)
- **Firebase Firestore Linkage:** Real-time synchronization with client partnership documents (`partnerships` collection).
- **Remote Request Approval:** Enables partners or authorized admins to approve or decline user unlock requests.
- **Account Status Audit:** View linked partner email, verification timestamps, and active lockdown sessions.

### 3. Security Event Monitoring
- **Tamper Alarms:** View reported clock rollback attempts, multi-window split bypasses, and package uninstallation attempts.
- **Watchdog Health:** Monitor service heartbeats and ensure client accessibility services are active.

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

## 🔒 Security & Deployment Architecture

1. **Client vs Server Secrets:**
   - For local development and emergency testing, HMAC calculations run in the browser using the shared master secret.
   - For public-facing production, code generation should be proxied through authenticated **Firebase Cloud Functions** to prevent exposure of master keys in client-side bundles.
2. **Access Control:**
   - In production, restrict access to authorized support agent accounts using Firebase Auth with custom admin claims.
