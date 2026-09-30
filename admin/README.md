# 🔑 FocusGuard — Emergency Support Tools & Passcode Generators

This directory contains standalone emergency rescue tools used to generate time-windowed, cryptographically secure 6-digit support passcodes for users who have locked their devices or need customer support intervention.

---

## 📂 Tools Included

| Tool | Type | Description |
|---|---|---|
| [`generate_support_code.py`](generate_support_code.py) | Python CLI | Interactive terminal-based passcode generator with automatic WhatsApp reply formatting. |
| [`support-admin.html`](support-admin.html) | Web Portal | Single-file, client-side HTML5/JS admin tool with a modern dark UI (runs in any browser). |

---

## ⚡ Supported Action Codes

Each emergency unlock action in FocusGuard corresponds to a standardized numeric code:

| Code | Action Name | Required Target Payload | Description |
|---|---|---|---|
| **101** | Disable Uninstall Protection | None (blank) | Temporarily deactivates Device Admin and uninstall guards. |
| **102** | Reset Active Challenge Lock | None (blank) | Resets stuck or pending friction challenges. |
| **201** | Unblock Specific App | Package name (e.g. `com.instagram.android`) | Removes the target app from the blocklist without typing challenge. |
| **202** | Delete Blocked Keyword | Domain / keyword (e.g. `reddit.com`) | Unblocks a filtered keyword or website domain. |
| **203** | Disable Shorts & Reels | Feed target (`instagram`, `youtube`, `facebook`, `all`) | Disables short-form video blocking for the specified platform. |
| **301** | Reset App Daily Limit | Package name (e.g. `com.twitter.android`) | Resets the daily consumed time limit counter for the app. |
| **999** | Master Emergency Reset | None (blank) | Clears all app blocks, keyword filters, and active focus sessions. |

---

## 🚀 How to Use the Python CLI

### Prerequisites
- Python 3.8+ installed (uses standard library `hmac`, `hashlib`, `struct`).

### Interactive Mode
Run without arguments for guided prompts:
```bash
python generate_support_code.py
```

Prompts will ask for:
1. **Device ID:** (e.g. `FG-8821-4902` - found in user's FocusGuard Settings).
2. **Action Code:** (e.g. `101`, `102`, `201`, `999`).
3. **Target Payload:** (if applicable, e.g. package name or domain).

### Command-Line Arguments
```bash
# Disable Uninstall Protection for device FG-8821-4902
python generate_support_code.py --device FG-8821-4902 --action 101

# Unblock Instagram
python generate_support_code.py --device FG-8821-4902 --action 201 --target com.instagram.android

# Master Emergency Reset
python generate_support_code.py --device FG-8821-4902 --action 999
```

---

## 🌐 How to Use the HTML Web Portal

1. Double-click or open [`support-admin.html`](support-admin.html) in Google Chrome, Microsoft Edge, or Firefox.
2. Enter the user's **Device ID**.
3. Select the desired **Action** from the dropdown menu.
4. If applicable, enter the target package name or domain.
5. Click **"Generate Support PIN"**.
6. Copy the 6-digit PIN or click **"Copy WhatsApp Message"** to reply directly to the customer.

---

## 🔒 Security & Cryptographic Details

- **Algorithm:** HMAC-SHA256
- **Payload Format:** `"{DEVICE_ID}|{YYYY-MM-DD}|{ACTION_CODE}|{TARGET_PAYLOAD}"`
- **Output:** 6-digit decimal PIN calculated from the first 4 bytes of the HMAC digest:
  $$\text{PIN} = (\text{uint32}(\text{digest}[0..3]) \pmod{900000}) + 100000$$
- **Time Window:** Daily date window (`YYYY-MM-DD`). Passcodes automatically expire at midnight local time.
- **Matching Engine:** Validated on-device in Kotlin by `SupportCodeEngine.kt`.
