import React, { useState, useEffect } from 'react';
import Header from './components/Header';
import CodeGeneratorTab from './components/CodeGeneratorTab';
import CodeInspectorTab from './components/CodeInspectorTab';
import SupportTicketsTab from './components/SupportTicketsTab';
import DevicesMonitorTab from './components/DevicesMonitorTab';
import AuditHistoryTab from './components/AuditHistoryTab';
import { subscribeToSupportTickets } from './firebase';
import './App.css';

export default function App() {
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
      </footer>
    </div>
  );
}
