import CryptoJS from 'crypto-js';

/**
 * FocusGuard support-code generator.
 *
 * This file is the mirror image of app/src/main/java/com/focusguard/app/security/SupportCodeEngine.kt.
 * Byte compatibility is contractual: the Kotlin engine asserts fixed vectors in
 * app/src/test/java/com/focusguard/app/security/SupportCodeEngineTest.kt. Any change to the payload
 * layout, the canonicalization or the PIN folding here must be made in the Kotlin engine in the same
 * commit, or every code already issued to a user silently stops verifying.
 *
 * Shared payload:  CANON(deviceId)|yyyy-MM-dd|actionCode|lower(trim(target))
 * Shared folding:  (BE32(HMAC-SHA256[0..3]) & 0x7FFFFFFF) % 900000 + 100000   -> six digits
 *
 * Secret handling:
 *   The master secret is not stored in this repository. It is read from the build environment
 *   (admin-panel/.env -> VITE_SUPPORT_MASTER_SECRET, or the same variable in CI). If it is missing,
 *   generation fails loudly instead of signing with a default that the phone will reject.
 *
 *   STATED LIMITATION, so nobody mistakes this for a server-side control: Vite inlines VITE_*
 *   values into the shipped bundle, so a *published* panel exposes the secret to anyone who reads
 *   the page source. Run this panel locally on the admin machine (npm run dev); do not host it
 *   publicly. The permanent fix is a signing endpoint that keeps the secret server-side — see
 *   admin-panel/README.md "Security model".
 */

export const MASTER_SECRET_ENV_VAR = 'VITE_SUPPORT_MASTER_SECRET';
export const ADMIN_EMAILS_ENV_VAR = 'VITE_ADMIN_EMAILS';

const PIN_MODULO = 900000;
const PIN_OFFSET = 100000;
const POSITIVE_INT_MASK = 0x7fffffff;
const FINGERPRINT_HEX_LENGTH = 32;
const ISO_DATE_PATTERN = /^\d{4}-\d{2}-\d{2}$/;
const WELL_FORMED_DEVICE_ID_PATTERN = /^FG-[A-Z0-9]{4}-[A-Z0-9]{4}$/;

/** The phone accepts codes signed for local today, UTC today, yesterday or tomorrow. Keep in sync. */
export const GRACE_WINDOW_DAYS = 1;

export const ACTION_DEFINITIONS = [
  {
    code: '101',
    id: 'DISABLE_UNINSTALL',
    name: 'Disable Uninstall Protection',
    description: 'Turns off Device Admin / Uninstall Protection so FocusGuard can be safely managed or uninstalled.',
    requiresTarget: false,
    targetPlaceholder: '',
    category: 'Protection',
    icon: 'ShieldOff',
    badgeColor: 'amber',
    userNote: 'After this code is applied, FocusGuard can be uninstalled or reconfigured by anyone holding the phone, until protection is turned on again.'
  },
  {
    code: '102',
    id: 'RESET_CHALLENGE',
    name: 'Reset Challenge Lock',
    description: 'Bypasses Sentence Typing Challenge or 24-hour time delay pending locks.',
    requiresTarget: false,
    targetPlaceholder: '',
    category: 'Friction',
    icon: 'KeyRound',
    badgeColor: 'blue',
    userNote: 'Clears the pending commitment challenge only. The apps and schedules you blocked stay blocked.'
  },
  {
    code: '103',
    id: 'RESET_TIME_TAMPER',
    name: 'Reset Time Tamper Lock',
    description: 'Clears the clock-manipulation flag set when the system time was moved back to dodge a lock.',
    requiresTarget: false,
    targetPlaceholder: '',
    category: 'Friction',
    icon: 'Clock',
    badgeColor: 'cyan',
    userNote: 'Use this when the app reports a clock-tampering lock but the system date and time are already correct.'
  },
  {
    code: '201',
    id: 'UNBLOCK_APP',
    name: 'Unblock Specific App',
    description: 'Removes a specific app from the blocked apps list.',
    requiresTarget: true,
    targetLabel: 'Package Name / Target App',
    targetPlaceholder: 'e.g. com.instagram.android or com.google.android.youtube',
    category: 'Apps',
    icon: 'PackageCheck',
    badgeColor: 'emerald',
    presets: [
      { label: 'Instagram', value: 'com.instagram.android' },
      { label: 'YouTube', value: 'com.google.android.youtube' },
      { label: 'Facebook', value: 'com.facebook.katana' },
      { label: 'Chrome', value: 'com.android.chrome' },
      { label: 'X (Twitter)', value: 'com.twitter.android' },
      { label: 'Snapchat', value: 'com.snapchat.android' },
      { label: 'Reddit', value: 'com.reddit.frontpage' }
    ],
    userNote: 'Only the app named above is unblocked. Every other blocked app, schedule and daily limit is untouched.'
  },
  {
    code: '202',
    id: 'DELETE_KEYWORD',
    name: 'Delete Blocked Keyword / Domain',
    description: 'Unblocks a custom website keyword or domain from the accessibility browser tracker.',
    requiresTarget: true,
    targetLabel: 'Keyword / Domain to Remove',
    targetPlaceholder: 'e.g. gambling, reddit.com, or adult',
    category: 'Websites',
    icon: 'Globe',
    badgeColor: 'violet',
    presets: [
      { label: 'gambling', value: 'gambling' },
      { label: 'casino', value: 'casino' },
      { label: 'porn', value: 'porn' },
      { label: 'betting', value: 'betting' },
      { label: 'reddit.com', value: 'reddit.com' },
      { label: 'twitter.com', value: 'twitter.com' }
    ],
    userNote: 'Only that one keyword or domain is removed from the web block list.'
  },
  {
    code: '203',
    id: 'DISABLE_FEED',
    name: 'Disable Shorts & Reels Blocker',
    description: 'Disables granular short-video blocking for a specific platform.',
    requiresTarget: true,
    targetLabel: 'Platform',
    targetPlaceholder: 'instagram, facebook, or youtube',
    category: 'Feeds',
    icon: 'Film',
    badgeColor: 'pink',
    presets: [
      { label: 'Instagram Reels', value: 'instagram' },
      { label: 'YouTube Shorts', value: 'youtube' },
      { label: 'Facebook Reels', value: 'facebook' },
      { label: 'All Feeds', value: 'all' }
    ],
    userNote: 'Short-video blocking is switched off for the selected platform only. "all" switches off every feed blocker.'
  },
  {
    code: '301',
    id: 'RESET_LIMIT',
    name: 'Reset App Daily Usage Limit',
    description: 'Resets today\'s elapsed usage timer for an app that has exceeded its daily limit.',
    requiresTarget: true,
    targetLabel: 'Target App Package',
    targetPlaceholder: 'e.g. com.google.android.youtube',
    category: 'Limits',
    icon: 'TimerReset',
    badgeColor: 'sky',
    presets: [
      { label: 'YouTube', value: 'com.google.android.youtube' },
      { label: 'Instagram', value: 'com.instagram.android' },
      { label: 'Chrome', value: 'com.android.chrome' }
    ],
    userNote: 'Resets today\'s usage counter for that app. The daily limit itself stays enabled and restarts tomorrow.'
  },
  {
    code: '999',
    id: 'FULL_RESET',
    name: 'Master Emergency Reset',
    description: 'Complete emergency unlock: disables uninstall protection, clears all active blockers and challenges.',
    requiresTarget: false,
    targetPlaceholder: '',
    category: 'Emergency',
    icon: 'AlertOctagon',
    badgeColor: 'rose',
    userNote: 'DESTRUCTIVE: clears every blocked app, keyword, schedule, daily limit and protection on this device. It cannot be undone with another code.'
  }
];

class MasterSecretMissingError extends Error {
  constructor() {
    super(
      `${MASTER_SECRET_ENV_VAR} is not set. Copy admin-panel/.env.example to admin-panel/.env and set it to the same secret the app build uses (local.properties -> support.master.secret).`
    );
    this.name = 'MasterSecretMissingError';
  }
}

/** Vite defines import.meta.env; Node (test scripts) does not. Guard so both can import this file. */
function readEnv(key) {
  return (import.meta?.env?.[key] || '').toString().trim();
}

/**
 * Reads the master secret from the build environment.
 * Throws rather than falling back to a literal: a wrong secret still produces a perfectly shaped
 * 6-digit code, so the admin would see "success" while the phone rejects every one of them.
 */
export function loadMasterSecret() {
  const raw = readEnv(MASTER_SECRET_ENV_VAR);
  if (!raw) throw new MasterSecretMissingError();
  return raw;
}

/** Whether the panel can sign codes at all — used to gate the UI before an alert fires. */
export function isMasterSecretConfigured() {
  try {
    loadMasterSecret();
    return true;
  } catch {
    return false;
  }
}

/**
 * Non-secret identity of a key, so the admin can confirm which secret is loaded without displaying it.
 * The app prints the same digest prefix in its support dialog logs.
 */
export function describeMasterSecret(secretKey) {
  const secret = (secretKey ?? '').trim();
  if (!secret) return 'not configured';
  return `sha256:${CryptoJS.SHA256(secret).toString(CryptoJS.enc.Hex).slice(0, 8)}`;
}

/**
 * Normalizes Device ID to the canonical signing form.
 * Must match SupportCodeEngine.canonicalizeDeviceId exactly.
 */
export function normalizeDeviceId(rawId) {
  if (!rawId) return '';
  return rawId.trim().toUpperCase().replace(/[^A-Z0-9-]/g, '');
}

/** The app renders IDs as FG-XXXX-XXXX; anything else is worth flagging before it is sent to a user. */
export function isWellFormedDeviceId(rawId) {
  return WELL_FORMED_DEVICE_ID_PATTERN.test(normalizeDeviceId(rawId));
}

function formatDate(d) {
  const year = d.getFullYear();
  const month = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

/**
 * Normalizes Date string to YYYY-MM-DD, offset from today.
 */
export function getTodayDateString(offsetDays = 0) {
  const d = new Date();
  if (offsetDays !== 0) {
    d.setDate(d.getDate() + offsetDays);
  }
  return formatDate(d);
}

/** UTC date string — the phone also accepts codes signed against this when the device clock straddles midnight. */
export function getUtcDateString() {
  return new Date().toISOString().slice(0, 10);
}

/**
 * Every date the phone will still accept, as YYYY-MM-DD.
 * Mirrors SupportCodeEngine.getCandidateDates(): local today, UTC today, yesterday, tomorrow.
 */
export function getAcceptableDates() {
  return Array.from(new Set([
    getTodayDateString(0),
    getUtcDateString(),
    getTodayDateString(-GRACE_WINDOW_DAYS),
    getTodayDateString(GRACE_WINDOW_DAYS)
  ]));
}

export function isDateWithinGraceWindow(dateStr) {
  const norm = (dateStr || '').trim();
  if (!ISO_DATE_PATTERN.test(norm)) return false;
  return getAcceptableDates().includes(norm);
}

/** The one place the wire format is defined for the panel. */
function buildCanonicalPayload(deviceId, dateStr, actionCode, targetPayload) {
  return `${normalizeDeviceId(deviceId)}|${(dateStr || '').trim()}|${String(actionCode || '').trim()}|${(targetPayload || '').trim().toLowerCase()}`;
}

/**
 * SHA-256 fingerprint of the canonical payload — the value the app stores in its single-use ledger.
 * Log and share this instead of the code: it identifies which action was issued without handing
 * anyone a working unlock.
 */
export function fingerprintOf({ deviceId, dateStr, actionCode, targetPayload = '' }) {
  return CryptoJS.SHA256(buildCanonicalPayload(deviceId, dateStr, actionCode, targetPayload))
    .toString(CryptoJS.enc.Hex)
    .slice(0, FINGERPRINT_HEX_LENGTH);
}

/**
 * Generates a 6-digit support code using HMAC-SHA256.
 */
export function generateSupportCode({
  deviceId,
  dateStr = getTodayDateString(),
  actionCode,
  targetPayload = '',
  secretKey = loadMasterSecret()
}) {
  const normDevice = normalizeDeviceId(deviceId);
  const normDate = (dateStr || getTodayDateString()).trim();
  const normAction = String(actionCode || '101').trim();
  const normTarget = (targetPayload || '').trim().toLowerCase();
  const secret = (secretKey || '').trim();

  if (!normDevice) {
    throw new Error('Device ID is required to generate code.');
  }
  if (!secret) {
    throw new MasterSecretMissingError();
  }
  if (!ISO_DATE_PATTERN.test(normDate)) {
    throw new Error(`Invalid date "${normDate}". Expected YYYY-MM-DD.`);
  }

  const rawPayload = buildCanonicalPayload(normDevice, normDate, normAction, normTarget);

  const hashHex = CryptoJS.HmacSHA256(rawPayload, secret).toString(CryptoJS.enc.Hex);

  // First 8 hex characters (4 bytes / 32-bit uint), mask to positive, modulo 900,000 + 100,000
  const first8 = hashHex.substring(0, 8);
  const intVal = parseInt(first8, 16) & POSITIVE_INT_MASK;
  const pinNum = (intVal % PIN_MODULO) + PIN_OFFSET;

  return {
    code: String(pinNum),
    rawPayload,
    hashHex,
    codeFingerprint: fingerprintOf({
      deviceId: normDevice,
      dateStr: normDate,
      actionCode: normAction,
      targetPayload: normTarget
    }),
    normDevice,
    normDate,
    normAction,
    normTarget
  };
}

/**
 * Reverse code inspector: Finds what action and date matches an entered 6-digit code
 */
export function reverseInspectCode({
  deviceId,
  inputCode,
  secretKey = loadMasterSecret(),
  customTargets = []
}) {
  const normDevice = normalizeDeviceId(deviceId);
  const targetCode = String(inputCode).trim();

  if (!normDevice || !targetCode) {
    return { found: false, matches: [] };
  }

  const datesToTest = getAcceptableDates();
  const today = getTodayDateString(0);
  const matches = [];

  for (const date of datesToTest) {
    for (const action of ACTION_DEFINITIONS) {
      if (!action.requiresTarget) {
        const gen = generateSupportCode({
          deviceId: normDevice,
          dateStr: date,
          actionCode: action.code,
          targetPayload: '',
          secretKey
        });
        if (gen.code === targetCode) {
          matches.push({ date, action, target: '', isToday: date === today });
        }
      } else {
        // Test all preset targets and custom targets
        const targetsToTest = [
          ...(action.presets || []).map((p) => p.value),
          ...customTargets
        ];

        for (const target of targetsToTest) {
          const gen = generateSupportCode({
            deviceId: normDevice,
            dateStr: date,
            actionCode: action.code,
            targetPayload: target,
            secretKey
          });
          if (gen.code === targetCode) {
            matches.push({ date, action, target, isToday: date === today });
          }
        }
      }
    }
  }

  return { found: matches.length > 0, matches };
}

function actionDefinition(actionCode) {
  return ACTION_DEFINITIONS.find((a) => a.code === String(actionCode)) || ACTION_DEFINITIONS[0];
}

/**
 * Honest validity wording.
 * The phone accepts yesterday / today / UTC today / tomorrow, so a code signed "today" keeps working
 * into the next day. Telling the user "expires at midnight" makes them re-request a code, hammer the
 * dialog, and trip the wrong-attempt cooldown while holding a still-valid PIN.
 * The app also refuses to apply the same code twice (single-use ledger), which is worth stating.
 */
function validityLines(language, normDate) {
  const today = getTodayDateString(0);
  if (normDate !== today) {
    return language === 'hi'
      ? `⏰ *Validity:* यह कोड ${normDate} के लिए बना है। फ़ोन पर तब तक चलेगा जब तक यह तारीख़ स्वीकार्य window (कल से अगले कल) में आती है। एक बार लगने के बाद यही कोड दोबारा नहीं चलेगा।`
      : `⏰ *Validity:* Signed for ${normDate}. It verifies while that date is inside the phone's acceptance window (yesterday through tomorrow). Once applied, the same code will not work again on that device.`;
  }
  return language === 'hi'
    ? '⏰ *Validity:* आज और अगले एक दिन तक फ़ोन के clock के हिसाब से चलेगा। एक बार सफलतापूर्वक लग जाने के बाद यही कोड दोबारा काम नहीं करेगा।'
    : '⏰ *Validity:* Works today and for one more day, according to the phone\'s own clock. Once applied successfully, the same code will not work again on that device.';
}

/**
 * Formats a ready-to-send Customer Support template
 */
export function generateSupportMessage({
  deviceId,
  code,
  actionCode,
  targetPayload = '',
  dateStr = getTodayDateString(),
  language = 'hi'
}) {
  const action = actionDefinition(actionCode);
  const targetText = targetPayload ? ` (${targetPayload})` : '';
  const deviceText = normalizeDeviceId(deviceId);
  const validity = validityLines(language, (dateStr || '').trim() || getTodayDateString());
  const scopeNote = action.userNote
    ? `\n⚠️ *Scope:* ${action.userNote}`
    : '';

  if (language === 'hi') {
    return `🛡️ *FocusGuard Official Support*

Namaste! Aapke Device (*${deviceText}*) ke liye verification code generate ho gaya hai:

🔑 *Secret Code:* \`${code}\`
📋 *Action:* ${action.name}${targetText}
${validity}

📌 *Kaise use karein:*
1. FocusGuard app open kijiye.
2. *Settings* me jakar *"Enter Support Code"* par tap kijiye.
3. Yeh 6-digit code daalte hi aapka maanga hua feature unlock ho jayega!
_(Dhayan dein: Baki saari blocking aur settings pehle ki tarah 100% active rahengi)._
${scopeNote}`;
  }

  return `🛡️ *FocusGuard Official Support*

Hello! Your one-time verification code for device (*${deviceText}*) has been generated:

🔑 *One-Time Code:* \`${code}\`
📋 *Target Action:* ${action.name}${targetText}
${validity}

📌 *How to use:*
1. Open FocusGuard on your phone.
2. Navigate to *Settings* -> Tap *"Enter Support Code"*.
3. Enter the 6-digit code above to execute this specific unlock.
_(Note: All other focus blocks and protection remain 100% locked and safe)._
${scopeNote}`;
}

/**
 * Parses the admin allowlist from the environment.
 * "a@x.com, b@x.com" and "a@x.com\nb@x.com" both work; entries are lowercased.
 */
export function loadAdminAllowlist() {
  return readEnv(ADMIN_EMAILS_ENV_VAR)
    .split(/[,\s]+/)
    .map((e) => e.trim().toLowerCase())
    .filter(Boolean);
}

export function isEmailAllowed(email, allowlist = loadAdminAllowlist()) {
  if (!email) return false;
  return allowlist.includes(String(email).trim().toLowerCase());
}
