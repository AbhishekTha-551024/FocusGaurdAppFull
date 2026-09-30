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
  Sparkles
} from 'lucide-react';
import { isFirebaseConfigured } from '../firebase';

export default function Header({ activeTab, setActiveTab, pendingTicketsCount = 0 }) {
  const [timeStr, setTimeStr] = useState('');

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

        {/* Live Clock & Mode Status */}
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
