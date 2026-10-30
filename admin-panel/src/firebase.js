import { initializeApp, getApps, getApp } from 'firebase/app';
import {
  getFirestore,
  collection,
  query,
  where,
  orderBy,
  onSnapshot,
  doc,
  getDoc,
  getDocs,
  setDoc,
  updateDoc,
  addDoc,
  serverTimestamp,
  limit
} from 'firebase/firestore';
import {
  getAuth,
  GoogleAuthProvider,
  signInWithPopup,
  signOut,
  onAuthStateChanged
} from 'firebase/auth';

/**
 * FocusGuard Production Firebase Project Config
 */
const firebaseConfig = {
  apiKey: import.meta.env.VITE_FIREBASE_API_KEY || "AIzaSyCkbBFGxLFm0pnn5xxs7nf4muKFIAaG8bs",
  authDomain: import.meta.env.VITE_FIREBASE_AUTH_DOMAIN || "focusguard-e3791.firebaseapp.com",
  projectId: import.meta.env.VITE_FIREBASE_PROJECT_ID || "focusguard-e3791",
  storageBucket: import.meta.env.VITE_FIREBASE_STORAGE_BUCKET || "focusguard-e3791.firebasestorage.app",
  messagingSenderId: import.meta.env.VITE_FIREBASE_MESSAGING_SENDER_ID || "1039832642648",
  appId: import.meta.env.VITE_FIREBASE_APP_ID || "1:1039832642648:web:focusguard"
};

export const isFirebaseConfigured = Boolean(
  firebaseConfig.apiKey &&
  !firebaseConfig.apiKey.includes('YOUR_API_KEY') &&
  firebaseConfig.projectId
);

let app = null;
let db = null;
let auth = null;

if (isFirebaseConfigured) {
  try {
    app = getApps().length > 0 ? getApp() : initializeApp(firebaseConfig);
    db = getFirestore(app);
    auth = getAuth(app);
  } catch (err) {
    console.error('Firebase initialization error:', err);
  }
}

export { app, db, auth };

/**
 * Subscribes to Firebase Auth state changes.
 */
export function subscribeToAuth(callback) {
  if (!auth) return () => {};
  return onAuthStateChanged(auth, callback);
}

/**
 * Signs in the admin with Google.
 * Required to access Firestore under authenticated security rules.
 */
export async function loginWithGoogle() {
  if (!auth) throw new Error('Firebase Auth is not initialized');
  const provider = new GoogleAuthProvider();
  return await signInWithPopup(auth, provider);
}

/**
 * Signs out from Firebase.
 */
export async function logoutUser() {
  if (!auth) return;
  return await signOut(auth);
}

/**
 * The email of the admin currently signed in, or null.
 *
 * Audit records used to claim "support-agent@focusguard.app" for every action, which made the
 * trail useless for answering the only question that matters after a leak: who issued this code?
 * Callers must treat null as "not signed in" rather than substituting a placeholder.
 */
export function getAdminEmail() {
  return auth?.currentUser?.email || null;
}

const TICKET_CODE_CACHE_KEY = 'focusguard_ticket_codes';

/**
 * Keeps an issued ticket code on this machine only.
 *
 * The code itself is deliberately NOT written to support_requests: the FocusGuard app itself can
 * read that collection, so a stored PIN next to its deviceId would hand any device a working
 * unlock for somebody else's phone. Firestore gets the fingerprint; the admin's browser keeps the
 * code so the copy-to-clipboard workflow still works.
 */
export function cacheTicketCodeLocally(ticketId, code) {
  try {
    const map = JSON.parse(localStorage.getItem(TICKET_CODE_CACHE_KEY) || '{}');
    map[ticketId] = code;
    localStorage.setItem(TICKET_CODE_CACHE_KEY, JSON.stringify(map));
  } catch (err) {
    console.warn('Could not cache ticket code locally:', err.message);
  }
}

export function getCachedTicketCode(ticketId) {
  try {
    const map = JSON.parse(localStorage.getItem(TICKET_CODE_CACHE_KEY) || '{}');
    return map[ticketId] || null;
  } catch {
    return null;
  }
}

/**
 * Look up a registered FocusGuard user by Gmail address.
 * Searches:
 *   1. users_by_email/{email}  (direct key lookup)
 *   2. users collection        (queries by email field)
 *
 * Returns null if not found in Firestore.
 */
export async function lookupUserByEmail(email) {
  if (!email || !email.trim()) return null;
  if (!isFirebaseConfigured || !db) {
    throw new Error('Firebase is not configured. Cannot look up user.');
  }

  const cleanEmail = email.trim().toLowerCase();

  // 1. Direct document lookup in users_by_email collection
  try {
    const emailDocRef = doc(db, 'users_by_email', cleanEmail);
    const emailSnap = await getDoc(emailDocRef);
    if (emailSnap.exists()) {
      return { id: emailSnap.id, ...emailSnap.data(), source: 'users_by_email' };
    }
  } catch (err) {
    console.warn('users_by_email lookup failed:', err.message);
  }

  // 2. Query 'users' collection by email field
  try {
    const q = query(
      collection(db, 'users'),
      where('email', '==', cleanEmail),
      limit(1)
    );
    const querySnap = await getDocs(q);
    if (!querySnap.empty) {
      const d = querySnap.docs[0];
      return { id: d.id, ...d.data(), source: 'users' };
    }
  } catch (err) {
    console.warn('users collection query failed:', err.message);
    // Don't throw — fall through to devices lookup
  }

  // 3. Query 'devices' collection where linkedEmails array-contains this email.
  //    This handles the real-world case: same physical phone, multiple Google accounts.
  //    AuthRepository.kt writes: "linkedEmails": FieldValue.arrayUnion(email)
  //    So ALL accounts on the same phone accumulate here — none get overwritten.
  try {
    const devQ = query(
      collection(db, 'devices'),
      where('linkedEmails', 'array-contains', cleanEmail),
      limit(1)
    );
    const devSnap = await getDocs(devQ);
    if (!devSnap.empty) {
      const devData = devSnap.docs[0].data();
      return {
        id: devData.deviceId,
        email: cleanEmail,
        deviceId: devData.deviceId,
        deviceModel: devData.model,
        androidVersion: devData.osVersion?.replace('Android ', '').split(' ')[0] || '?',
        appVersion: devData.appVersion,
        linkedEmails: devData.linkedEmails || [cleanEmail],
        primaryEmail: devData.primaryEmail || cleanEmail,
        source: 'devices_linked_emails'
      };
    }
  } catch (err) {
    console.error('devices linkedEmails query failed:', err);
    throw err;
  }

  // Not found in any Firestore collection
  return null;
}

/**
 * Manually link an email to a device ID in Firestore users_by_email.
 * Admin-only: called from the admin panel manual link form.
 */
export async function linkEmailToDevice(email, deviceId, model = 'Android Phone') {
  if (!isFirebaseConfigured || !db) {
    throw new Error('Firebase is not configured.');
  }
  const cleanEmail = email.trim().toLowerCase();
  const cleanDevice = deviceId.trim().toUpperCase();
  const userData = {
    email: cleanEmail,
    displayName: cleanEmail.split('@')[0],
    deviceId: cleanDevice,
    deviceModel: model,
    linkedAt: serverTimestamp()
  };
  await setDoc(doc(db, 'users_by_email', cleanEmail), userData, { merge: true });
  return userData;
}

/**
 * Subscribes to real-time Support Requests from Firestore.
 * Returns empty array when collection is empty — no mock data.
 */
export function subscribeToSupportTickets(callback) {
  if (!isFirebaseConfigured || !db) {
    console.error('Firebase not configured — cannot load support tickets.');
    callback([]);
    return () => {};
  }

  const q = query(
    collection(db, 'support_requests'),
    orderBy('createdAt', 'desc'),
    limit(50)
  );

  return onSnapshot(
    q,
    (snapshot) => {
      const tickets = snapshot.docs.map((d) => ({ id: d.id, ...d.data() }));
      callback(tickets);
    },
    (err) => {
      console.error('Firestore support_requests listener error:', err);
      callback([]);
    }
  );
}

/**
 * Updates a Support Ticket's status in Firestore.
 */
export async function updateSupportTicket(ticketId, updates) {
  if (!isFirebaseConfigured || !db) {
    throw new Error('Firebase not configured — cannot update ticket.');
  }
  const docRef = doc(db, 'support_requests', ticketId);
  await updateDoc(docRef, {
    ...updates,
    updatedAt: serverTimestamp()
  });
  return true;
}

/**
 * Subscribes to registered FocusGuard devices in real time.
 *
 * Priority:
 *   1. 'devices' collection (written directly by AuthRepository.kt on sign-in)
 *   2. 'users' collection fallback (extracts deviceId + telemetry, de-duped by deviceId)
 *
 * Note: Multiple users on the same physical phone share one deviceId.
 * The panel groups them under a single device entry with all linked emails listed.
 */
export function subscribeToDevices(callback) {
  if (!isFirebaseConfigured || !db) {
    console.error('Firebase not configured — cannot load devices.');
    callback([]);
    return () => {};
  }

  const q = query(collection(db, 'devices'), limit(200));

  return onSnapshot(
    q,
    async (snapshot) => {
      if (!snapshot.empty) {
        const devices = snapshot.docs.map((d) => ({
          id: d.id,
          ...d.data(),
          isCloudLive: true
        }));
        callback(devices);
      } else {
        // 'devices' collection empty — pull telemetry from 'users' collection
        const userDevices = await fetchDevicesFromUsersCollection();
        callback(userDevices);
      }
    },
    async (err) => {
      console.warn('Firestore devices listener error, trying users collection:', err.message);
      try {
        const userDevices = await fetchDevicesFromUsersCollection();
        callback(userDevices);
      } catch {
        callback([]);
      }
    }
  );
}

/**
 * Reads 'users' collection, groups users by deviceId, and returns device list.
 * Handles multiple accounts registered on the same physical phone correctly.
 */
async function fetchDevicesFromUsersCollection() {
  if (!isFirebaseConfigured || !db) return [];
  try {
    const snap = await getDocs(query(collection(db, 'users'), limit(200)));
    if (snap.empty) return [];

    const deviceMap = new Map();

    snap.docs.forEach((d) => {
      const u = d.data();
      if (!u.deviceId) return;

      if (deviceMap.has(u.deviceId)) {
        // Same physical device — just add this email to linkedEmails
        const existing = deviceMap.get(u.deviceId);
        if (u.email && !existing.linkedEmails.includes(u.email)) {
          existing.linkedEmails.push(u.email);
          // Keep the primary email as the first registered one
          if (!existing.userEmail) existing.userEmail = u.email;
        }
      } else {
        deviceMap.set(u.deviceId, {
          id: u.deviceId,
          deviceId: u.deviceId,
          model: u.deviceModel || 'Android Device',
          osVersion: `Android ${u.androidVersion || '?'}`,
          appVersion: u.appVersion || '1.0.0',
          isOnline: true,
          isCloudLive: true,
          lastActive: u.updatedAt?.toDate
            ? u.updatedAt.toDate().toLocaleString()
            : 'Recently synced',
          blockedAppsCount: u.blockedAppsCount || 0,
          blockedKeywordsCount: u.blockedKeywordsCount || 0,
          uninstallProtection: u.uninstallProtection !== false,
          timedLockDays: u.timedLockDays || 0,
          userEmail: u.email || '',
          linkedEmails: u.email ? [u.email] : []
        });
      }
    });

    return Array.from(deviceMap.values());
  } catch (err) {
    console.error('Could not read users collection for devices:', err);
    return [];
  }
}

/**
 * Registers or updates a device in Firestore 'devices' collection.
 * Called from AuthRepository.kt on every successful sign-in.
 */
export async function registerDeviceInFirestore(device) {
  if (!isFirebaseConfigured || !db) {
    throw new Error('Firebase not configured.');
  }
  const docRef = doc(db, 'devices', device.deviceId);
  await setDoc(docRef, {
    ...device,
    lastActive: serverTimestamp(),
    syncedAt: new Date().toISOString()
  }, { merge: true });
  return true;
}

/**
 * Records an admin action to the Audit Trail in Firestore and localStorage.
 *
 * The cloud copy never carries the 6-digit code. `code` is stripped and replaced by
 * `codeFingerprint` (the same SHA-256 identity the app's single-use ledger stores), which is enough
 * to prove what was issued without turning the audit trail into a list of live unlocks. The raw code
 * stays in this browser's localStorage so the operator can re-read what they just sent.
 */
export async function recordAuditLog(entry) {
  const { code, ...safeEntry } = entry || {};

  const cloudEntry = {
    ...safeEntry,
    codeFingerprint: entry?.codeFingerprint || null,
    codeStoredInCloud: false,
    timestamp: new Date().toISOString()
  };

  if (isFirebaseConfigured && db) {
    try {
      await addDoc(collection(db, 'audit_logs'), {
        ...cloudEntry,
        createdAt: serverTimestamp()
      });
    } catch (e) {
      console.warn('Failed to push audit log to Firestore:', e);
    }
  } else {
    console.warn('Audit log not written to Firestore: Firebase is not configured.');
  }

  try {
    const existing = JSON.parse(localStorage.getItem('focusguard_audit_logs') || '[]');
    existing.unshift({ ...cloudEntry, code: code || null });
    localStorage.setItem('focusguard_audit_logs', JSON.stringify(existing.slice(0, 200)));
  } catch (_) {}
}

/**
 * Returns locally cached audit logs (from recordAuditLog calls in this session).
 * Empty array when no admin actions have been performed yet.
 */
export function getLocalAuditLogs() {
  try {
    const raw = localStorage.getItem('focusguard_audit_logs');
    return raw ? JSON.parse(raw) : [];
  } catch {
    return [];
  }
}
