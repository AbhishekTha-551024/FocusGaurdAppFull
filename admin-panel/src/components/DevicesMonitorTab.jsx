import React, { useState, useEffect } from 'react';
import {
  Smartphone,
  Shield,
  ShieldAlert,
  Search,
  CheckCircle,
  Clock,
  ExternalLink,
  Copy,
  Check,
  Zap,
  Key,
  Cloud,
  Plus,
  X,
  RefreshCw
} from 'lucide-react';
import {
  subscribeToDevices,
  registerDeviceInFirestore
} from '../firebase';

export default function DevicesMonitorTab({ onSelectDeviceForCode }) {
  const [devices, setDevices] = useState([]);
  const [searchQuery, setSearchQuery] = useState('');
  const [copiedId, setCopiedId] = useState(null);
  const [isSaving, setIsSaving] = useState(false);
  const [showAddModal, setShowAddModal] = useState(false);
  const [customDevice, setCustomDevice] = useState({
    deviceId: '',
    model: '',
    osVersion: 'Android 14',
    appVersion: '1.0.0',
    blockedAppsCount: 0,
    blockedKeywordsCount: 0,
    uninstallProtection: true,
    timedLockDays: 0
  });

  useEffect(() => {
    const unsub = subscribeToDevices((list) => {
      setDevices(list);
    });
    return () => unsub();
  }, []);

  const filteredDevices = devices.filter((d) => {
    const q = searchQuery.toLowerCase().trim();
    if (!q) return true;
    const matchId = d.deviceId?.toLowerCase().includes(q);
    const matchModel = d.model && d.model.toLowerCase().includes(q);
    const matchUserEmail = d.userEmail && d.userEmail.toLowerCase().includes(q);
    const matchPrimary = d.primaryEmail && d.primaryEmail.toLowerCase().includes(q);
    const matchLinked = Array.isArray(d.linkedEmails) && d.linkedEmails.some((em) => em.toLowerCase().includes(q));
    return matchId || matchModel || matchUserEmail || matchPrimary || matchLinked;
  });

  const handleCopyId = (id) => {
    navigator.clipboard.writeText(id);
    setCopiedId(id);
    setTimeout(() => setCopiedId(null), 2000);
  };

  // (No demo fleet sync — data is live from Firestore)

  const handleRegisterCustomDevice = async (e) => {
    e.preventDefault();
    if (!customDevice.deviceId.trim()) {
      alert('Please enter a Device ID (e.g. FG-8821-4902)');
      return;
    }
    setIsSaving(true);
    try {
      await registerDeviceInFirestore({
        ...customDevice,
        id: customDevice.deviceId.trim().toUpperCase(),
        deviceId: customDevice.deviceId.trim().toUpperCase(),
        isOnline: true
      });
      setShowAddModal(false);
      setCustomDevice({
        deviceId: '',
        model: '',
        osVersion: 'Android 14',
        appVersion: '1.0.0',
        blockedAppsCount: 0,
        blockedKeywordsCount: 0,
        uninstallProtection: true,
        timedLockDays: 0
      });
    } catch (err) {
      alert('Failed to register device in Firestore: ' + err.message);
    } finally {
      setIsSaving(false);
    }
  };

  const onlineCount = devices.filter((d) => d.isOnline).length;
  const protectedCount = devices.filter((d) => d.uninstallProtection).length;

  return (
    <div className="tab-content animate-fade-in">
      {/* Top Metric Cards */}
      <div className="metrics-grid">
        <div className="glass-card metric-card">
          <div className="metric-icon-wrap cyan">
            <Smartphone size={20} />
          </div>
          <div>
            <div className="metric-value">{devices.length}</div>
            <div className="metric-label">Total Registered Devices</div>
          </div>
        </div>

        <div className="glass-card metric-card">
          <div className="metric-icon-wrap emerald">
            <Zap size={20} />
          </div>
          <div>
            <div className="metric-value">{onlineCount}</div>
            <div className="metric-label">Online Active Now</div>
          </div>
        </div>

        <div className="glass-card metric-card">
          <div className="metric-icon-wrap violet">
            <Shield size={20} />
          </div>
          <div>
            <div className="metric-value">{protectedCount}</div>
            <div className="metric-label">Uninstall Protection Active</div>
          </div>
        </div>
      </div>

      {/* Main Devices Table Card */}
      <div className="glass-card devices-table-card">
        <div className="card-header table-header-flex">
          <div className="card-title-group">
            <span className="icon-pill">
              <Smartphone size={18} />
            </span>
            <div>
              <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                <h2 className="card-title">FocusGuard Device Fleet Monitor</h2>
                <span className="badge badge-emerald" style={{ fontSize: '0.7rem' }}>
                  <Cloud size={10} /> Live Firestore
                </span>
              </div>
              <p className="card-subtitle">
                Real-time telemetry and lock status of phones running FocusGuard.
              </p>
            </div>
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: '10px', flexWrap: 'wrap' }}>
            <div className="search-box-wrap">
              <Search size={15} className="search-icon" />
              <input
                type="text"
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                placeholder="Search Device ID or model..."
                className="text-input search-input"
              />
            </div>

            <button
              onClick={() => setShowAddModal(true)}
              className="btn-primary"
              style={{ fontSize: '0.82rem', padding: '8px 14px' }}
            >
              <Plus size={14} />
              <span>Register Device</span>
            </button>
          </div>
        </div>

        <div className="table-responsive">
          <table className="devices-table">
            <thead>
              <tr>
                <th>Device ID</th>
                <th>Device Model &amp; OS</th>
                <th>Status</th>
                <th>Blocked Scope</th>
                <th>Uninstall Lock</th>
                <th>Action</th>
              </tr>
            </thead>
            <tbody>
              {filteredDevices.length > 0 ? (
                filteredDevices.map((dev) => (
                  <tr key={dev.id}>
                    <td>
                      <div className="device-id-cell">
                        <span className="mono-text font-bold">{dev.deviceId}</span>
                        <button
                          onClick={() => handleCopyId(dev.deviceId)}
                          className="btn-ghost btn-xs"
                          title="Copy Device ID"
                        >
                          {copiedId === dev.deviceId ? <Check size={13} /> : <Copy size={13} />}
                        </button>
                      </div>
                    </td>

                    <td>
                      <div className="device-model-cell">
                        <div className="model-name">{dev.model}</div>
                        <div className="os-version">{dev.osVersion} &bull; v{dev.appVersion}</div>
                        {((Array.isArray(dev.linkedEmails) && dev.linkedEmails.length > 0) || dev.primaryEmail || dev.userEmail) && (
                          <div style={{ marginTop: '6px', display: 'flex', flexWrap: 'wrap', gap: '4px' }}>
                            {Array.from(new Set([
                              ...(Array.isArray(dev.linkedEmails) ? dev.linkedEmails : []),
                              ...(dev.primaryEmail ? [dev.primaryEmail] : []),
                              ...(dev.userEmail ? [dev.userEmail] : [])
                            ])).map((email) => (
                              <span
                                key={email}
                                style={{
                                  fontSize: '0.72rem',
                                  padding: '2px 6px',
                                  borderRadius: '4px',
                                  background: 'rgba(56, 189, 248, 0.1)',
                                  color: '#38bdf8',
                                  border: '1px solid rgba(56, 189, 248, 0.25)',
                                  fontFamily: 'monospace'
                                }}
                              >
                                {email}
                              </span>
                            ))}
                          </div>
                        )}
                      </div>
                    </td>

                    <td>
                      <span className={`status-pill ${dev.isOnline ? 'online' : 'offline'}`}>
                        <span className="status-dot" />
                        {dev.isOnline ? 'Active' : dev.lastActive || 'Offline'}
                      </span>
                    </td>

                    <td>
                      <div className="scope-cell">
                        <span className="badge badge-cyan">{dev.blockedAppsCount || 0} Apps</span>
                        <span className="badge badge-violet">{dev.blockedKeywordsCount || 0} Keywords</span>
                      </div>
                    </td>

                    <td>
                      {dev.uninstallProtection ? (
                        <span className="badge badge-emerald" title={`${dev.timedLockDays || 0} days remaining`}>
                          <Shield size={12} /> Protected ({dev.timedLockDays || 0}d lock)
                        </span>
                      ) : (
                        <span className="badge badge-rose">
                          <ShieldAlert size={12} /> Unprotected
                        </span>
                      )}
                    </td>

                    <td>
                      <button
                        onClick={() => onSelectDeviceForCode(dev.deviceId)}
                        className="btn-secondary btn-xs"
                        title="Generate support passcode for this device"
                      >
                        <Key size={13} />
                        <span>Generate Code</span>
                      </button>
                    </td>
                  </tr>
                ))
              ) : (
                <tr>
                  <td colSpan="6" className="table-empty-td">
                    <div style={{ padding: '32px', textAlign: 'center' }}>
                      <Smartphone size={36} style={{ color: 'var(--text-muted)', marginBottom: '12px' }} />
                      <p style={{ color: 'var(--text-secondary)', marginBottom: '8px', fontWeight: '500' }}>
                        {searchQuery ? 'No devices match your search.' : 'No devices registered yet.'}
                      </p>
                      <p style={{ color: 'var(--text-muted)', fontSize: '0.82rem' }}>
                        Devices appear here automatically when users sign in to FocusGuard on their phone.
                      </p>
                    </div>
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </div>

      {/* Manual Device Registration Modal */}
      {showAddModal && (
        <div
          style={{
            position: 'fixed',
            inset: 0,
            backgroundColor: 'rgba(0, 0, 0, 0.75)',
            backdropFilter: 'blur(6px)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            zIndex: 1000,
            padding: '20px'
          }}
          onClick={() => setShowAddModal(false)}
        >
          <div
            className="glass-card"
            style={{
              width: '100%',
              maxWidth: '480px',
              padding: '24px',
              border: '1px solid rgba(34, 197, 94, 0.4)'
            }}
            onClick={(e) => e.stopPropagation()}
          >
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '18px' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                <Smartphone size={20} color="#22c55e" />
                <h3 style={{ margin: 0, color: '#fff', fontSize: '1.1rem', fontWeight: 'bold' }}>
                  Register Phone to Fleet Monitor
                </h3>
              </div>
              <button
                onClick={() => setShowAddModal(false)}
                className="btn-ghost"
                style={{ padding: '4px' }}
              >
                <X size={18} />
              </button>
            </div>

            <form onSubmit={handleRegisterCustomDevice} style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
              <div>
                <label style={{ display: 'block', color: 'var(--text-secondary)', fontSize: '0.82rem', marginBottom: '6px' }}>
                  Device ID (from Phone Settings or Support Screen):
                </label>
                <input
                  type="text"
                  required
                  placeholder="e.g. FG-8821-4902"
                  value={customDevice.deviceId}
                  onChange={(e) => setCustomDevice({ ...customDevice, deviceId: e.target.value.toUpperCase() })}
                  className="text-input"
                  style={{ width: '100%', fontFamily: 'monospace', textTransform: 'uppercase' }}
                />
              </div>

              <div>
                <label style={{ display: 'block', color: 'var(--text-secondary)', fontSize: '0.82rem', marginBottom: '6px' }}>
                  Phone Model:
                </label>
                <input
                  type="text"
                  placeholder="e.g. Vivo V29e / Samsung S23"
                  value={customDevice.model}
                  onChange={(e) => setCustomDevice({ ...customDevice, model: e.target.value })}
                  className="text-input"
                  style={{ width: '100%' }}
                />
              </div>

              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '10px' }}>
                <div>
                  <label style={{ display: 'block', color: 'var(--text-secondary)', fontSize: '0.82rem', marginBottom: '6px' }}>
                    Android OS:
                  </label>
                  <input
                    type="text"
                    value={customDevice.osVersion}
                    onChange={(e) => setCustomDevice({ ...customDevice, osVersion: e.target.value })}
                    className="text-input"
                    style={{ width: '100%' }}
                  />
                </div>
                <div>
                  <label style={{ display: 'block', color: 'var(--text-secondary)', fontSize: '0.82rem', marginBottom: '6px' }}>
                    Locked Days:
                  </label>
                  <input
                    type="number"
                    min="1"
                    max="365"
                    value={customDevice.timedLockDays}
                    onChange={(e) => setCustomDevice({ ...customDevice, timedLockDays: parseInt(e.target.value) || 0 })}
                    className="text-input"
                    style={{ width: '100%' }}
                  />
                </div>
              </div>

              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px', marginTop: '10px' }}>
                <button
                  type="button"
                  onClick={() => setShowAddModal(false)}
                  className="btn-secondary"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={isSaving}
                  className="btn-primary"
                >
                  <Cloud size={14} />
                  <span>{isSaving ? 'Saving...' : 'Register to Cloud'}</span>
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
