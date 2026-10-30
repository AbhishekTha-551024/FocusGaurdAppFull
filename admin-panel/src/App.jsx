import React, { useState, useEffect } from 'react';
import Header from './components/Header';
import CodeGeneratorTab from './components/CodeGeneratorTab';
import CodeInspectorTab from './components/CodeInspectorTab';
import SupportTicketsTab from './components/SupportTicketsTab';
import DevicesMonitorTab from './components/DevicesMonitorTab';
import AuditHistoryTab from './components/AuditHistoryTab';
import {
  subscribeToSupportTickets,
  subscribeToAuth,
  loginWithGoogle,
  logoutUser,
  isFirebaseConfigured
} from './firebase';
import {
  loadAdminAllowlist,
  isEmailAllowed,
  ADMIN_EMAILS_ENV_VAR,
  MASTER_SECRET_ENV_VAR
} from './crypto/codeGenerator';
import './App.css';

const GATE_STYLE = {
  minHeight: '100vh',
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'center',
  padding: '24px'
};

const CARD_STYLE = {
  maxWidth: '460px',
  width: '100%',
  padding: '28px',
  borderRadius: '16px',
  background: 'rgba(15, 23, 42, 0.75)',
  border: '1px solid var(--border-subtle)',
  textAlign: 'center'
};

const CODE_STYLE = {
  display: 'inline-block',
  padding: '2px 8px',
  borderRadius: '6px',
  background: 'rgba(148, 163, 184, 0.15)',
  fontFamily: 'ui-monospace, monospace',
  fontSize: '12px'
};

/**
 * Full-screen gate shown instead of the console. Rendered for every non-authorized state, so the
 * panel never flashes half-loaded data.
 */
function Gate({ title, description, children }) {
  return (
    <div style={GATE_STYLE}>
      <div style={CARD_STYLE}>
        <div style={{ fontSize: '34px', lineHeight: 1, marginBottom: '12px' }}>🛡️</div>
        <h1 style={{ margin: '0 0 10px', fontSize: '1.25rem' }}>{title}</h1>
        <p style={{ margin: '0 0 18px', fontSize: '13px', lineHeight: 1.6, color: 'var(--text-muted)' }}>
          {description}
        </p>
        {children}
      </div>
    </div>
  );
}

/**
 * Client-side admin gate.
 *
 * What it genuinely protects: an unauthenticated visitor (or a stray screenshot of the URL) can no
 * longer read the device monitor, the ticket queue or generate anything without signing in with a
 * Google account listed in VITE_ADMIN_EMAILS.
 *
 * What it does NOT protect — stated plainly so nobody relies on it: this is a static SPA, so the
 * check runs in the browser. Firestore rules are the real boundary, and the master secret is inlined
 * into the bundle by Vite. Run this panel on the admin machine only; see README "Security model".
 */
export default function App() {
  const [allowlist, setAllowlist] = useState([]);
  const [adminUser, setAdminUser] = useState(null);
  const [isAuthLoading, setIsAuthLoading] = useState(true);
  const [gateError, setGateError] = useState('');

  useEffect(() => {
    setAllowlist(loadAdminAllowlist());
    const unsubscribe = subscribeToAuth((user) => {
      setAdminUser(user);
      setIsAuthLoading(false);
    });
    return () => unsubscribe();
  }, []);

  const handleSignIn = async () => {
    setGateError('');
    try {
      await loginWithGoogle();
    } catch (err) {
      setGateError(err?.message || 'Google sign-in failed.');
    }
  };

  if (!isFirebaseConfigured) {
    return (
      <Gate
        title="Firebase is not configured"
        description={
          <>
            The console needs a Firebase project to authenticate operators. Set the
            {' '}<code style={CODE_STYLE}>VITE_FIREBASE_*</code> values in{' '}
            <code style={CODE_STYLE}>admin-panel/.env</code> and restart.
          </>
        }
      />
    );
  }

  if (allowlist.length === 0) {
    return (
      <Gate
        title="No operators configured"
        description={
          <>
            Access is denied until at least one address is listed in{' '}
            <code style={CODE_STYLE}>{ADMIN_EMAILS_ENV_VAR}</code> (comma-separated) inside{' '}
            <code style={CODE_STYLE}>admin-panel/.env</code>. The panel fails closed on purpose.
          </>
        }
      />
    );
  }

  if (isAuthLoading) {
    return <Gate title="Checking operator session" description="Restoring your Firebase sign-in state…" />;
  }

  if (!adminUser) {
    return (
      <Gate
        title="FocusGuard Admin Console"
        description={
          <>
            Sign in with a Google account on the operator allowlist to continue. Every code you
            generate afterwards is attributed to this address in the audit trail.
          </>
        }
      >
        <button type="button" className="btn-primary" onClick={handleSignIn} style={{ width: '100%', justifyContent: 'center' }}>
          Sign in with Google
        </button>
        {gateError && (
          <p role="alert" style={{ marginTop: '14px', fontSize: '12px', color: '#FCA5A5' }}>
            {gateError}
          </p>
        )}
      </Gate>
    );
  }

  if (!isEmailAllowed(adminUser.email, allowlist)) {
    return (
      <Gate
        title="This account is not an operator"
        description={
          <>
            <strong>{adminUser.email}</strong> is signed in but not listed in{' '}
            <code style={CODE_STYLE}>{ADMIN_EMAILS_ENV_VAR}</code>. Ask the project owner to add it,
            or sign in with the right account.
          </>
        }
      >
        <button type="button" className="btn-secondary" onClick={() => logoutUser()} style={{ width: '100%', justifyContent: 'center' }}>
          Sign out
        </button>
      </Gate>
    );
  }

  return <AdminConsole adminEmail={adminUser.email} />;
}

function AdminConsole({ adminEmail }) {
  const [activeTab, setActiveTab] = useState('generator');
  const [pendingCount, setPendingCount] = useState(0);

  useEffect(() => {
    const unsub = subscribeToSupportTickets((tickets) => {
      const pending = tickets.filter((t) => t.status === 'pending').length;
      setPendingCount(pending);
    });
    return () => unsub();
  }, []);

  const handleSelectDeviceForCode = (deviceId) => {
    setActiveTab('generator');
    // We can dispatch a custom event or let state persist
    setTimeout(() => {
      const input = document.getElementById('device-id-input');
      if (input) {
        input.value = deviceId;
        input.dispatchEvent(new Event('input', { bubbles: true }));
      }
    }, 100);
  };

  return (
    <div className="app-wrapper">
      <Header
        activeTab={activeTab}
        setActiveTab={setActiveTab}
        pendingTicketsCount={pendingCount}
      />

      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          gap: '12px',
          padding: '8px 16px',
          fontSize: '12px',
          color: 'var(--text-muted)',
          borderBottom: '1px solid var(--border-subtle)'
        }}
      >
        <span>
          Signed in as <strong style={{ color: 'var(--text-primary)' }}>{adminEmail}</strong>
          {' '}— codes and audit entries are attributed to this address.
        </span>
        <button type="button" className="btn-ghost" onClick={() => logoutUser()}>
          Sign out
        </button>
      </div>

      <main className="main-content">
        {activeTab === 'generator' && <CodeGeneratorTab />}
        {activeTab === 'inspector' && <CodeInspectorTab />}
        {activeTab === 'tickets' && (
          <SupportTicketsTab onGenerateForDevice={handleSelectDeviceForCode} />
        )}
        {activeTab === 'devices' && (
          <DevicesMonitorTab onSelectDeviceForCode={handleSelectDeviceForCode} />
        )}
        {activeTab === 'audit' && <AuditHistoryTab />}
      </main>

      <footer className="admin-footer" style={{ textAlign: 'center', padding: '24px', color: 'var(--text-muted)', fontSize: '0.8rem', borderTop: '1px solid var(--border-subtle)' }}>
        <p>
          FocusGuard Security Infrastructure &bull; Zero-Knowledge Offline Verification Protocol &bull; Strict Targeted Scope Enforced
        </p>
        <p style={{ marginTop: '6px', fontSize: '0.72rem' }}>
          Codes are signed with <code style={CODE_STYLE}>{MASTER_SECRET_ENV_VAR}</code> from this machine&apos;s .env. Audit records store a fingerprint, never the PIN.
        </p>
      </footer>
    </div>
  );
}
