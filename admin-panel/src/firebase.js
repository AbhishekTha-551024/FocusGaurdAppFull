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
  updateDoc,
  addDoc,
  serverTimestamp,
  limit
} from 'firebase/firestore';
import { getAuth } from 'firebase/auth';

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
    console.warn('Firebase initialization skipped or encountered error:', err);
  }
}

export { app, db, auth };

/**
 * Look up a registered user by their Gmail address in Firestore
 * Looks up direct document 'users_by_email/{email}' first, then queries 'users' collection.
 */
export async function lookupUserByEmail(email) {
  if (!email || !email.trim()) return null;
  const cleanEmail = email.trim().toLowerCase();

  if (isFirebaseConfigured && db) {
    try {
      // 1. Direct key lookup in users_by_email collection
      const emailDocRef = doc(db, 'users_by_email', cleanEmail);
      const emailSnap = await getDoc(emailDocRef);
      if (emailSnap.exists()) {
        return { id: emailSnap.id, ...emailSnap.data(), source: 'users_by_email' };
      }

      // 2. Query 'users' collection by email field
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
      console.error('Error looking up user by email in Firestore:', err);
    }
  }

  // Fallback demo users if Firestore is not accessible or user is testing offline
  const demoUsers = [
    {
      email: 'user@gmail.com',
      displayName: 'Test User',
      deviceId: 'FG-8821-4902',
      deviceModel: 'Samsung Galaxy S23',
      androidVersion: '14'
    }
  ];
  return demoUsers.find((u) => u.email.toLowerCase() === cleanEmail) || null;
}

/**
 * Mock data for Standalone / Demo Mode
 */
const MOCK_TICKETS = [
  {
    id: 'req_101',
    deviceId: 'FG-8821-4902',
    phoneModel: 'Samsung Galaxy S23 (Android 14)',
    actionCode: '101',
    actionName: 'Disable Uninstall Protection',
    targetPayload: '',
    reason: 'Need to factory reset my phone before selling it to a friend.',
    status: 'pending',
    createdAt: new Date(Date.now() - 1000 * 60 * 18).toISOString(),
    userEmail: 'rohit.sharma@example.com'
  },
  {
    id: 'req_102',
    deviceId: 'FG-3310-9284',
    phoneModel: 'Google Pixel 8 Pro (Android 15)',
    actionCode: '201',
    actionName: 'Unblock Specific App',
    targetPayload: 'com.google.android.youtube',
    reason: 'Have semester exams tomorrow. Need to watch physics lectures on YouTube.',
    status: 'pending',
    createdAt: new Date(Date.now() - 1000 * 60 * 45).toISOString(),
    userEmail: 'priya.verma@example.com'
  },
  {
    id: 'req_103',
    deviceId: 'FG-7A39-K92B',
    phoneModel: 'OnePlus 12 (Android 14)',
    actionCode: '202',
    actionName: 'Delete Blocked Keyword',
    targetPayload: 'reddit.com',
    reason: 'Need to access r/developersIndia for job referral queries.',
    status: 'resolved',
    createdAt: new Date(Date.now() - 1000 * 60 * 60 * 3).toISOString(),
    resolvedAt: new Date(Date.now() - 1000 * 60 * 60 * 2).toISOString(),
    responseCode: '592813',
    userEmail: 'dev.kumar@example.com'
  }
];

const MOCK_DEVICES = [
  {
    id: 'FG-8821-4902',
    deviceId: 'FG-8821-4902',
    model: 'Samsung Galaxy S23',
    osVersion: 'Android 14 (API 34)',
    appVersion: '1.2.4',
    isOnline: true,
    lastActive: 'Just now',
    blockedAppsCount: 6,
    blockedKeywordsCount: 14,
    uninstallProtection: true,
    timedLockDays: 15
  },
  {
    id: 'FG-3310-9284',
    deviceId: 'FG-3310-9284',
    model: 'Google Pixel 8 Pro',
    osVersion: 'Android 15 (API 35)',
    appVersion: '1.2.4',
    isOnline: true,
    lastActive: '4 mins ago',
    blockedAppsCount: 9,
    blockedKeywordsCount: 22,
    uninstallProtection: true,
    timedLockDays: 30
  },
  {
    id: 'FG-7A39-K92B',
    deviceId: 'FG-7A39-K92B',
    model: 'OnePlus 12',
    osVersion: 'Android 14 (API 34)',
    appVersion: '1.2.3',
    isOnline: false,
    lastActive: '3 hours ago',
    blockedAppsCount: 4,
    blockedKeywordsCount: 8,
    uninstallProtection: true,
    timedLockDays: 7
  },
  {
    id: 'FG-4491-X81A',
    deviceId: 'FG-4491-X81A',
    model: 'Xiaomi 13 Pro',
    osVersion: 'Android 13 (API 33)',
    appVersion: '1.2.0',
    isOnline: false,
    lastActive: '2 days ago',
    blockedAppsCount: 12,
    blockedKeywordsCount: 35,
    uninstallProtection: false,
    timedLockDays: 0
  }
];

/**
 * Listens to Real-time Support Requests from users
 */
export function subscribeToSupportTickets(callback) {
  if (isFirebaseConfigured && db) {
    try {
      const q = query(
        collection(db, 'support_requests'),
        orderBy('createdAt', 'desc'),
        limit(50)
      );
      return onSnapshot(q, (snapshot) => {
        const tickets = snapshot.docs.map((d) => ({ id: d.id, ...d.data() }));
        callback(tickets);
      });
    } catch (e) {
      console.warn('Firestore tickets query error, falling back to local/demo:', e);
    }
  }

  // Fallback / Demo state from localStorage
  const saved = localStorage.getItem('focusguard_demo_tickets');
  const list = saved ? JSON.parse(saved) : MOCK_TICKETS;
  callback(list);

  // Return unsubscribe dummy
  return () => {};
}

/**
 * Updates a Support Ticket's status (e.g. Approved with code, or Rejected)
 */
export async function updateSupportTicket(ticketId, updates) {
  if (isFirebaseConfigured && db) {
    try {
      const docRef = doc(db, 'support_requests', ticketId);
      await updateDoc(docRef, {
        ...updates,
        updatedAt: serverTimestamp()
      });
      return true;
    } catch (e) {
      console.error('Failed to update ticket in Firestore:', e);
    }
  }

  // Fallback update in localStorage
  const saved = localStorage.getItem('focusguard_demo_tickets');
  const list = saved ? JSON.parse(saved) : [...MOCK_TICKETS];
  const updatedList = list.map((t) => (t.id === ticketId ? { ...t, ...updates } : t));
  localStorage.setItem('focusguard_demo_tickets', JSON.stringify(updatedList));
  return true;
}

/**
 * Listens to registered FocusGuard devices
 */
export function subscribeToDevices(callback) {
  if (isFirebaseConfigured && db) {
    try {
      const q = query(collection(db, 'devices'), orderBy('lastActive', 'desc'), limit(100));
      return onSnapshot(q, (snapshot) => {
        const devices = snapshot.docs.map((d) => ({ id: d.id, ...d.data() }));
        callback(devices);
      });
    } catch (e) {
      console.warn('Firestore devices query error:', e);
    }
  }

  callback(MOCK_DEVICES);
  return () => {};
}

/**
 * Logs an action to the Audit Trail
 */
export async function recordAuditLog(entry) {
  const auditEntry = {
    ...entry,
    timestamp: new Date().toISOString()
  };

  if (isFirebaseConfigured && db) {
    try {
      await addDoc(collection(db, 'audit_logs'), {
        ...auditEntry,
        createdAt: serverTimestamp()
      });
    } catch (e) {
      console.warn('Failed to push audit log to Firestore:', e);
    }
  }

  // Always keep in localStorage for immediate offline viewing
  try {
    const existing = JSON.parse(localStorage.getItem('focusguard_audit_logs') || '[]');
    existing.unshift(auditEntry);
    localStorage.setItem('focusguard_audit_logs', JSON.stringify(existing.slice(0, 100)));
  } catch (err) {
    // Ignore storage quota error
  }
}

/**
 * Retrieves audit history
 */
export function getLocalAuditLogs() {
  try {
    const raw = localStorage.getItem('focusguard_audit_logs');
    if (!raw) {
      return [
        {
          timestamp: new Date(Date.now() - 1000 * 60 * 25).toISOString(),
          adminEmail: 'support@focusguard.app',
          deviceId: 'FG-7A39-K92B',
          actionCode: '202',
          actionName: 'Delete Blocked Keyword',
          targetPayload: 'reddit.com',
          code: '592813',
          reason: 'User verified via WhatsApp support'
        },
        {
          timestamp: new Date(Date.now() - 1000 * 60 * 120).toISOString(),
          adminEmail: 'admin@focusguard.app',
          deviceId: 'FG-8821-4902',
          actionCode: '101',
          actionName: 'Disable Uninstall Protection',
          targetPayload: '',
          code: '653462',
          reason: 'Device transfer requested'
        }
      ];
    }
    return JSON.parse(raw);
  } catch {
    return [];
  }
}
