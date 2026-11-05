import React, { useState, useEffect } from 'react';
import {
  Shield,
  Key,
  Search,
  Inbox,
  Smartphone,
  History,
  Cloud,
  CloudOff,
  Sparkles,
  LogIn,
  LogOut,
  User
} from 'lucide-react';
import { isFirebaseConfigured, subscribeToAuth, loginWithGoogle, logoutUser } from '../firebase';

export default function Header({ activeTab, setActiveTab, pendingTicketsCount = 0 }) {
  const [timeStr, setTimeStr] = useState('');
  const [currentUser, setCurrentUser] = useState(null);
  const [isLoggingIn, setIsLoggingIn] = useState(false);

  useEffect(() => {
    const unsub = subscribeToAuth((user) => {
      setCurrentUser(user);
    });
    return () => unsub();
  }, []);

  useEffect(() => {
    const update = () => {
      const now = new Date();
      setTimeStr(
        now.toLocaleTimeString('en-US', {
          hour: '2-digit',
          minute: '2-digit',
          second: '2-digit',
          hour12: true
        })
      );
    };
    update();
    const interval = setInterval(update, 1000);
    return () => clearInterval(interval);
  }, []);

  const handleLogin = async () => {
    setIsLoggingIn(true);
    try {
      await loginWithGoogle();
    } catch (e) {
      alert('Google Sign-in failed: ' + e.message);
    } finally {
      setIsLoggingIn(false);
    }
  };

  const handleLogout = async () => {
    try {
      await logoutUser();
    } catch (e) {
      console.warn('Logout failed:', e);
    }
  };

  const navItems = [
    { id: 'generator', label: 'Code Generator', icon: Key },
    { id: 'inspector', label: 'Code Inspector', icon: Search },
    { id: 'tickets', label: 'Support Tickets', icon: Inbox, count: pendingTicketsCount },
    { id: 'devices', label: 'Devices Monitor', icon: Smartphone },
    { id: 'audit', label: 'Audit History', icon: History }
  ];

  return (
    <header className="admin-header">
      <div className="header-container">
        {/* Brand Logo & Name */}
        <div className="brand-badge">
          <div className="shield-icon-wrapper">
            <Shield className="shield-svg" size={24} />
          </div>
          <div className="brand-titles">
            <div className="brand-name">
              FocusGuard <span className="brand-tag">COMMAND CENTER</span>
            </div>
            <div className="brand-subtitle">Offline Cryptographic Passcode &amp; Remote Admin</div>
          </div>
        </div>

        {/* Live Clock, Auth & Mode Status */}
        <div className="header-status-area">
          <div className="clock-pill">
            <span className="live-dot" />
            <span className="clock-text">{timeStr}</span>
          </div>

          <div
            className={`mode-pill ${isFirebaseConfigured ? 'mode-cloud' : 'mode-offline'}`}
            title={
              isFirebaseConfigured
                ? 'Firebase Firestore & Auth Connected'
                : 'Running in Standalone Offline Cryptographic Mode'
            }
          >
            {isFirebaseConfigured ? (
              <>
                <Cloud size={14} />
                <span>Cloud Connected</span>
              </>
            ) : (
              <>
                <CloudOff size={14} />
                <span>Offline Crypto Engine</span>
              </>
            )}
          </div>

          {/* Google Auth Status / Login Button */}
          {currentUser ? (
            <div
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: '8px',
                padding: '4px 10px',
                borderRadius: '8px',
                background: 'rgba(34, 197, 94, 0.12)',
                border: '1px solid rgba(34, 197, 94, 0.3)'
              }}
              title={`Logged in as ${currentUser.email}`}
            >
              {currentUser.photoURL ? (
                <img
                  src={currentUser.photoURL}
                  alt={currentUser.displayName || 'Admin'}
                  style={{ width: '22px', height: '22px', borderRadius: '50%' }}
                />
              ) : (
                <User size={15} color="#4ade80" />
              )}
              <span style={{ fontSize: '0.78rem', color: '#86efac', fontWeight: '600' }}>
                {currentUser.displayName || currentUser.email.split('@')[0]}
              </span>
              <button
                onClick={handleLogout}
                className="btn-ghost"
                style={{ padding: '2px 4px', color: '#94a3b8' }}
                title="Sign out of Firebase"
              >
                <LogOut size={13} />
              </button>
            </div>
          ) : (
            <button
              onClick={handleLogin}
              disabled={isLoggingIn}
              className="btn-secondary"
              style={{
                fontSize: '0.78rem',
                padding: '6px 12px',
                background: 'linear-gradient(135deg, rgba(59, 130, 246, 0.15) 0%, rgba(37, 99, 235, 0.25) 100%)',
                borderColor: 'rgba(59, 130, 246, 0.4)',
                color: '#93c5fd'
              }}
              title="Sign in with Google to read live Firestore users and devices"
            >
              <LogIn size={13} />
              <span>{isLoggingIn ? 'Signing in...' : 'Sign in with Google'}</span>
            </button>
          )}
        </div>
      </div>

      {/* Navigation Tabs Bar */}
      <nav className="header-nav-bar" aria-label="Main Navigation">
        <div className="nav-container">
          {navItems.map((item) => {
            const Icon = item.icon;
            const isActive = activeTab === item.id;
            return (
              <button
                key={item.id}
                id={`tab-btn-${item.id}`}
                onClick={() => setActiveTab(item.id)}
                className={`nav-tab-btn ${isActive ? 'active' : ''}`}
              >
                <Icon size={17} />
                <span>{item.label}</span>
                {item.count > 0 && (
                  <span className="nav-count-badge">{item.count}</span>
                )}
              </button>
            );
          })}
        </div>
      </nav>
    </header>
  );
}
