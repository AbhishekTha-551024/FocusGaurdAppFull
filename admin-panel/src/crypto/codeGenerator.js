import CryptoJS from 'crypto-js';

export const DEFAULT_MASTER_SECRET = 'FocusGuard#MasterSecret#2026@Key';

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
    badgeColor: 'amber'
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
    badgeColor: 'blue'
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
    ]
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
    ]
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
    ]
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
    ]
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
    badgeColor: 'rose'
  }
];

/**
 * Normalizes Device ID to clean uppercase format (e.g., FG-XXXX-XXXX)
 */
export function normalizeDeviceId(rawId) {
  if (!rawId) return '';
  return rawId.trim().toUpperCase().replace(/[^A-Z0-9-]/g, '');
}

/**
 * Normalizes Date string to YYYY-MM-DD
 */
export function getTodayDateString(offsetDays = 0) {
  const d = new Date();
  if (offsetDays !== 0) {
    d.setDate(d.getDate() + offsetDays);
  }
  const year = d.getFullYear();
  const month = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

/**
 * Generates a 6-digit support code using HMAC-SHA256
 */
export function generateSupportCode({
  deviceId,
  dateStr = getTodayDateString(),
  actionCode,
  targetPayload = '',
  secretKey = DEFAULT_MASTER_SECRET
}) {
  const normDevice = normalizeDeviceId(deviceId);
  const normDate = (dateStr || getTodayDateString()).trim();
  const normAction = String(actionCode || '101').trim();
  const normTarget = (targetPayload || '').trim().toLowerCase();
  const secret = (secretKey || DEFAULT_MASTER_SECRET).trim();

  if (!normDevice) {
    throw new Error('Device ID is required to generate code.');
  }

  // Cryptographic normalized payload
  const rawPayload = `${normDevice}|${normDate}|${normAction}|${normTarget}`;

  // HMAC-SHA256
  const hashHex = CryptoJS.HmacSHA256(rawPayload, secret).toString(CryptoJS.enc.Hex);

  // Take first 8 hex characters (4 bytes / 32-bit uint), mask to positive, modulo 900,000 + 100,000
  const first8 = hashHex.substring(0, 8);
  const intVal = parseInt(first8, 16) & 0x7fffffff;
  const pinNum = (intVal % 900000) + 100000;

  return {
    code: String(pinNum),
    rawPayload,
    hashHex,
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
  secretKey = DEFAULT_MASTER_SECRET,
  customTargets = []
}) {
  const normDevice = normalizeDeviceId(deviceId);
  const targetCode = String(inputCode).trim();

  if (!normDevice || !targetCode) {
    return { found: false, matches: [] };
  }

  const datesToTest = [
    getTodayDateString(0),  // Today
    getTodayDateString(-1), // Yesterday
    getTodayDateString(1)   // Tomorrow
  ];

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
          matches.push({
            date,
            action,
            target: '',
            isToday: date === getTodayDateString(0)
          });
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
            matches.push({
              date,
              action,
              target,
              isToday: date === getTodayDateString(0)
            });
          }
        }
      }
    }
  }

  return {
    found: matches.length > 0,
    matches
  };
}

/**
 * Formats a ready-to-send Customer Support template
 */
export function generateSupportMessage({
  deviceId,
  code,
  actionCode,
  targetPayload = '',
  language = 'hi'
}) {
  const action = ACTION_DEFINITIONS.find((a) => a.code === String(actionCode)) || ACTION_DEFINITIONS[0];
  const targetText = targetPayload ? ` (${targetPayload})` : '';

  if (language === 'hi') {
    return `🛡️ *FocusGuard Official Support*

Namaste! Aapke Device (*${deviceId}*) ke liye verification code generate ho gaya hai:

🔑 *Secret Code:* \`${code}\`
📋 *Action:* ${action.name}${targetText}
⏰ *Validity:* Aaj raat 12:00 baje tak (Midnight IST)

📌 *Kaise use karein:*
1. FocusGuard app open kijiye.
2. *Settings* me jakar *"Enter Support Code"* par tap kijiye.
3. Yeh 6-digit code daalte hi aapka maanga hua feature unlock ho jayega!
_(Dhayan dein: Baki saari blocking aur settings pehle ki tarah 100% active rahengi)._`;
  }

  return `🛡️ *FocusGuard Official Support*

Hello! Your one-time verification code for device (*${deviceId}*) has been generated:

🔑 *One-Time Code:* \`${code}\`
📋 *Target Action:* ${action.name}${targetText}
⏰ *Valid Until:* Today at 11:59 PM (Midnight)

📌 *How to use:*
1. Open FocusGuard on your phone.
2. Navigate to *Settings* -> Tap *"Enter Support Code"*.
3. Enter the 6-digit code above to execute this specific unlock.
_(Note: All other focus blocks and protection remain 100% locked and safe)._`;
}
