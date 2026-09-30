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
  Key
} from 'lucide-react';
import { subscribeToDevices } from '../firebase';

export default function DevicesMonitorTab({ onSelectDeviceForCode }) {
  const [devices, setDevices] = useState([]);
  const [searchQuery, setSearchQuery] = useState('');
  const [copiedId, setCopiedId] = useState(null);

  useEffect(() => {
    const unsub = subscribeToDevices((list) => {
      setDevices(list);
    });
    return () => unsub();
  }, []);

  const filteredDevices = devices.filter((d) => {
    const q = searchQuery.toLowerCase();
    return (
      d.deviceId.toLowerCase().includes(q) ||
      (d.model && d.model.toLowerCase().includes(q))
    );
  });

  const handleCopyId = (id) => {
    navigator.clipboard.writeText(id);
    setCopiedId(id);
    setTimeout(() => setCopiedId(null), 2000);
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
              <h2 className="card-title">FocusGuard Device Fleet Monitor</h2>
              <p className="card-subtitle">
                Real-time telemetry and lock status of phones running FocusGuard.
              </p>
            </div>
          </div>

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
                        <span className="badge badge-cyan">{dev.blockedAppsCount} Apps</span>
                        <span className="badge badge-violet">{dev.blockedKeywordsCount} Keywords</span>
                      </div>
                    </td>

                    <td>
                      {dev.uninstallProtection ? (
                        <span className="badge badge-emerald" title={`${dev.timedLockDays} days remaining`}>
                          <Shield size={12} /> Protected ({dev.timedLockDays}d lock)
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
                    No devices match the search query.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
