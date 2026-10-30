// Temporary Kotlin<->JS parity harness for the support-code scheme.
// Golden values were produced with node crypto under the same test secret the Kotlin unit test uses.
import { generateSupportCode, normalizeDeviceId, isWellFormedDeviceId, isDateWithinGraceWindow, getAcceptableDates } from './src/crypto/codeGenerator.js';

const SECRET = 'SupportCodeEngineTest#LocalSecret#0000';
const DEV = 'FG-8821-4902';
const DATE = '2026-10-30';

const cases = [
  ['101', '', '158390'],
  ['102', '', '980134'],
  ['103', '', '135228'],
  ['999', '', '338343'],
  ['203', 'all', '831630'],
  ['203', 'youtube', '441501'],
  ['201', 'com.instagram.android', '455699'],
  ['202', 'gambling', '851192'],
  ['301', 'com.google.android.youtube', '406500']
];

let failures = 0;
for (const [action, target, expected] of cases) {
  const got = generateSupportCode({ deviceId: DEV, dateStr: DATE, actionCode: action, targetPayload: target, secretKey: SECRET });
  const ok = got.code === expected;
  if (!ok) failures++;
  console.log(`${ok ? 'PASS' : 'FAIL'} action ${action.padEnd(4)} target=${(target || '-').padEnd(26)} expected=${expected} got=${got.code}`);
}

const fp = generateSupportCode({ deviceId: DEV, dateStr: DATE, actionCode: '101', targetPayload: '', secretKey: SECRET }).codeFingerprint;
const fpOk = fp === 'aea78a5b0f1d9f00b6e01902273092ad';
if (!fpOk) failures++;
console.log(`${fpOk ? 'PASS' : 'FAIL'} fingerprint(101) = ${fp}`);

const messy = generateSupportCode({ deviceId: '  fg 8821 4902 ', dateStr: DATE, actionCode: '101', targetPayload: '', secretKey: SECRET }).code;
const canonOk = messy === '558197';
if (!canonOk) failures++;
console.log(`${canonOk ? 'PASS' : 'FAIL'} canonicalization "  fg 8821 4902 " -> ${normalizeDeviceId('  fg 8821 4902 ')} code=${messy}`);

console.log('well-formed FG-8821-4902:', isWellFormedDeviceId('FG-8821-4902'), '| FG88214902:', isWellFormedDeviceId('FG88214902'));
console.log('acceptable dates:', getAcceptableDates().join(', '));
console.log('grace window check today:', isDateWithinGraceWindow(DATE), '| 2030 gate:', isDateWithinGraceWindow('2030-01-01'));

try {
  generateSupportCode({ deviceId: DEV, dateStr: DATE, actionCode: '101', targetPayload: '' });
  console.log('FAIL: missing secret did not throw');
  failures++;
} catch (e) {
  console.log('PASS: missing secret throws ->', e.name);
}

console.log(failures === 0 ? '\nALL PARITY CHECKS PASSED' : `\n${failures} PARITY CHECKS FAILED`);
process.exit(failures === 0 ? 0 : 1);
