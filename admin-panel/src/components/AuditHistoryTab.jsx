import React, { useState, useEffect } from 'react';
import {
  History,
  Download,
  Copy,
  Check,
  ShieldCheck,
  FileText
} from 'lucide-react';
import { getLocalAuditLogs } from '../firebase';

export default function AuditHistoryTab() {
  const [logs, setLogs] = useState([]);
  const [copiedCodeIndex, setCopiedCodeIndex] = useState(null);

  useEffect(() => {
    setLogs(getLocalAuditLogs());
  }, []);

  const handleCopyCode = (code, index) => {
    navigator.clipboard.writeText(code);
    setCopiedCodeIndex(index);
    setTimeout(() => setCopiedCodeIndex(null), 2000);
  };

  const handleExportJson = () => {
    const dataStr =
      'data:text/json;charset=utf-8,' + encodeURIComponent(JSON.stringify(logs, null, 2));
    const downloadAnchor = document.createElement('a');
    downloadAnchor.setAttribute('href', dataStr);
    downloadAnchor.setAttribute(
      'download',
      `focusguard_audit_logs_${new Date().toISOString().slice(0, 10)}.json`
    );
    document.body.appendChild(downloadAnchor);
    downloadAnchor.click();
    downloadAnchor.remove();
  };

  return (
    <div className="tab-content animate-fade-in">
      <div className="glass-card audit-container">
        <div className="card-header table-header-flex">
          <div className="card-title-group">
            <span className="icon-pill">
              <History size={18} />
            </span>
            <div>
              <h2 className="card-title">Security Audit Trail &amp; Passcode Logs</h2>
              <p className="card-subtitle">
                Immutable records of every support unlock code generated, reason provided, and target device.
              </p>
            </div>
          </div>

          <button onClick={handleExportJson} className="btn-secondary btn-sm">
            <Download size={15} />
            <span>Export JSON Audit</span>
          </button>
        </div>

        <div className="table-responsive">
          <table className="devices-table">
            <thead>
              <tr>
                <th>Timestamp</th>
                <th>Target Device</th>
                <th>Action &amp; Target</th>
                <th>Generated Code</th>
                <th>Reason / Justification</th>
                <th>Admin Agent</th>
              </tr>
            </thead>
            <tbody>
              {logs.length > 0 ? (
                logs.map((entry, idx) => (
                  <tr key={idx}>
                    <td>
                      <span className="audit-time">
                        {new Date(entry.timestamp).toLocaleString()}
                      </span>
                    </td>

                    <td>
                      <span className="mono-text font-bold">{entry.deviceId}</span>
                    </td>

                    <td>
                      <div className="audit-action-cell">
                        <span className="badge badge-cyan">
                          {entry.actionCode ? `Action ${entry.actionCode}` : 'Support'}
                        </span>
                        <div className="audit-action-name">{entry.actionName}</div>
                        {entry.targetPayload && (
                          <span className="audit-target-sub">
                            Target: <code>{entry.targetPayload}</code>
                          </span>
                        )}
                      </div>
                    </td>

                    <td>
                      <div className="audit-code-cell">
                        <strong className="mono-text code-accent">{entry.code}</strong>
                        <button
                          onClick={() => handleCopyCode(entry.code, idx)}
                          className="btn-ghost btn-xs"
                          title="Copy Code"
                        >
                          {copiedCodeIndex === idx ? <Check size={12} /> : <Copy size={12} />}
                        </button>
                      </div>
                    </td>

                    <td>
                      <span className="audit-reason-text">{entry.reason || 'N/A'}</span>
                    </td>

                    <td>
                      <span className="audit-admin-tag">{entry.adminEmail || 'Admin'}</span>
                    </td>
                  </tr>
                ))
              ) : (
                <tr>
                  <td colSpan="6" className="table-empty-td">
                    No audit records logged yet. Codes generated in the Generator tab will appear here.
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
