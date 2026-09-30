import React, { useState } from 'react';
import {
  Key,
  Copy,
  Check,
  Sparkles,
  ShieldAlert,
  Calendar,
  Layers,
  MessageSquare,
  RefreshCw,
  Info,
  Lock,
  Mail,
  Search,
  UserCheck,
  Smartphone,
  AlertCircle,
  ChevronDown,
  ChevronUp
} from 'lucide-react';
import confetti from 'canvas-confetti';
import {
  ACTION_DEFINITIONS,
  DEFAULT_MASTER_SECRET,
  generateSupportCode,
  generateSupportMessage,
  getTodayDateString,
  normalizeDeviceId
} from '../crypto/codeGenerator';
import { recordAuditLog, lookupUserByEmail } from '../firebase';

export default function CodeGeneratorTab() {
  const [searchEmail, setSearchEmail] = useState('');
  const [isSearchingUser, setIsSearchingUser] = useState(false);
  const [foundUser, setFoundUser] = useState(null);
  const [searchError, setSearchError] = useState('');
  const [showManualDeviceId, setShowManualDeviceId] = useState(false);

  const [deviceId, setDeviceId] = useState('');
  const [selectedDate, setSelectedDate] = useState(getTodayDateString());
  const [selectedActionCode, setSelectedActionCode] = useState('101');
  const [targetPayload, setTargetPayload] = useState('');
  const [secretKey, setSecretKey] = useState(DEFAULT_MASTER_SECRET);
  const [showSecret, setShowSecret] = useState(false);
  const [reason, setReason] = useState('User verified via Gmail lookup');

  const [generatedResult, setGeneratedResult] = useState(null);
  const [copiedCode, setCopiedCode] = useState(false);
  const [copiedMsg, setCopiedMsg] = useState(false);
  const [msgLang, setMsgLang] = useState('hi');

  const currentAction =
    ACTION_DEFINITIONS.find((a) => a.code === selectedActionCode) || ACTION_DEFINITIONS[0];

  const handleSearchUserByEmail = async (e) => {
    e?.preventDefault();
    if (!searchEmail.trim()) {
      setSearchError('Please enter a Gmail address to search');
      return;
    }
    setIsSearchingUser(true);
    setSearchError('');
    try {
      const user = await lookupUserByEmail(searchEmail.trim());
      if (user && user.deviceId) {
        setFoundUser(user);
        setDeviceId(user.deviceId);
        setReason(`User ${user.email} verified via Google Auth`);
      } else {
        setFoundUser(null);
        setSearchError(`No device linked to "${searchEmail.trim()}". Verify if user signed in with Google or enter Device ID manually.`);
        setShowManualDeviceId(true);
      }
    } catch (err) {
      setSearchError('Search failed: ' + (err.message || 'Network error'));
      setShowManualDeviceId(true);
    } finally {
      setIsSearchingUser(false);
    }
  };

  const handleDeviceIdChange = (e) => {
    let val = e.target.value.toUpperCase().replace(/[^A-Z0-9-]/g, '');
    // Auto-insert hyphen formatting if typing raw 8 chars
    if (val.startsWith('FG') && !val.includes('-') && val.length > 2) {
      val = 'FG-' + val.slice(2);
    }
    setDeviceId(val);
  };

  const handleGenerate = (e) => {
    e?.preventDefault();
    try {
      const result = generateSupportCode({
        deviceId,
        dateStr: selectedDate,
        actionCode: selectedActionCode,
        targetPayload: currentAction.requiresTarget ? targetPayload : '',
        secretKey
      });

      setGeneratedResult(result);
      setCopiedCode(false);
      setCopiedMsg(false);

      // Trigger celebration confetti
      try {
        confetti({
          particleCount: 50,
          spread: 60,
          origin: { y: 0.8 },
          colors: ['#22C55E', '#16A34A', '#4ADE80', '#FBBF24']
        });
      } catch {
        // Safe fallback
      }

      // Record in audit log
      recordAuditLog({
        deviceId: result.normDevice,
        dateStr: result.normDate,
        actionCode: result.normAction,
        actionName: currentAction.name,
        targetPayload: currentAction.requiresTarget ? targetPayload : '',
        code: result.code,
        reason: reason.trim() || 'Manual support generation',
        adminEmail: 'support-agent@focusguard.app'
      });
    } catch (err) {
      alert(err.message || 'Error generating code');
    }
  };

  const handleCopyCode = () => {
    if (!generatedResult) return;
    navigator.clipboard.writeText(generatedResult.code);
    setCopiedCode(true);
    setTimeout(() => setCopiedCode(false), 2000);
  };

  const handleCopyMessage = () => {
    if (!generatedResult) return;
    const msg = generateSupportMessage({
      deviceId: generatedResult.normDevice,
      code: generatedResult.code,
      actionCode: generatedResult.normAction,
      targetPayload: currentAction.requiresTarget ? targetPayload : '',
      language: msgLang
    });
    navigator.clipboard.writeText(msg);
    setCopiedMsg(true);
    setTimeout(() => setCopiedMsg(false), 2000);
  };

  return (
    <div className="tab-content animate-fade-in">
      <div className="generator-grid">
        {/* Left Form: Parameters */}
        <div className="glass-card generator-form-card">
          <div className="card-header">
            <div className="card-title-group">
              <span className="icon-pill">
                <Key size={18} />
              </span>
              <div>
                <h2 className="card-title">Generate Targeted Support Code</h2>
                <p className="card-subtitle">
                  Generates an offline 6-digit cryptographic PIN for a single action on a specific phone.
                </p>
              </div>
            </div>
          </div>

          <form onSubmit={handleGenerate} className="generator-form">
            {/* ── GMAIL USER LOOKUP (PRIMARY) ── */}
            <div className="input-group">
              <label htmlFor="gmail-search-input" className="input-label" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <span style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                  <Mail size={16} style={{ color: '#22C55E' }} />
                  Search User by Gmail <span className="label-required">*</span>
                </span>
                <span style={{ fontSize: '11px', color: 'var(--text-muted)', fontWeight: 'normal' }}>
                  Automatic Firestore Device Lookup
                </span>
              </label>

              <div className="input-with-action">
                <input
                  id="gmail-search-input"
                  type="email"
                  value={searchEmail}
                  onChange={(e) => setSearchEmail(e.target.value)}
                  onKeyDown={(e) => { if (e.key === 'Enter') { e.preventDefault(); handleSearchUserByEmail(); } }}
                  placeholder="e.g. user@gmail.com"
                  className="text-input"
                  style={{ fontSize: '14px' }}
                />
                <button
                  type="button"
                  onClick={handleSearchUserByEmail}
                  disabled={isSearchingUser || !searchEmail.trim()}
                  className="btn-primary"
                  style={{ padding: '0 16px', height: '42px', display: 'flex', alignItems: 'center', gap: '6px', whiteSpace: 'nowrap' }}
                >
                  {isSearchingUser ? (
                    <RefreshCw size={14} className="animate-spin" />
                  ) : (
                    <Search size={14} />
                  )}
                  {isSearchingUser ? 'Searching...' : 'Find Device'}
                </button>
              </div>

              {/* Found User Card */}
              {foundUser && (
                <div
                  style={{
                    marginTop: '10px',
                    padding: '12px 14px',
                    borderRadius: '10px',
                    background: 'linear-gradient(135deg, rgba(34, 197, 94, 0.12) 0%, rgba(16, 185, 129, 0.08) 100%)',
                    border: '1px solid rgba(34, 197, 94, 0.35)',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                    gap: '12px'
                  }}
                >
                  <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                    <div
                      style={{
                        width: '36px',
                        height: '36px',
                        borderRadius: '50%',
                        backgroundColor: '#16A34A',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        color: '#FFF',
                        fontWeight: 'bold',
                        fontSize: '15px'
                      }}
                    >
                      {foundUser.displayName ? foundUser.displayName[0].toUpperCase() : 'U'}
                    </div>
                    <div>
                      <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                        <span style={{ fontWeight: '600', color: '#F0FDF4', fontSize: '13px' }}>
                          {foundUser.displayName || 'FocusGuard User'}
                        </span>
                        <span
                          style={{
                            fontSize: '10px',
                            fontWeight: '600',
                            padding: '2px 6px',
                            borderRadius: '4px',
                            backgroundColor: 'rgba(34, 197, 94, 0.25)',
                            color: '#4ADE80',
                            border: '1px solid rgba(34, 197, 94, 0.4)'
                          }}
                        >
                          Device Synced
                        </span>
                      </div>
                      <div style={{ fontSize: '11px', color: '#86EFAC', marginTop: '2px' }}>
                        {foundUser.email} &bull; <strong style={{ color: '#FCD34D' }}>{foundUser.deviceId}</strong>
                        {foundUser.deviceModel ? ` (${foundUser.deviceModel})` : ''}
                      </div>
                    </div>
                  </div>
                  <button
                    type="button"
                    onClick={() => { setFoundUser(null); setSearchEmail(''); }}
                    className="btn-ghost"
                    style={{ fontSize: '11px', padding: '4px 8px' }}
                  >
                    Clear
                  </button>
                </div>
              )}

              {/* Search Error Alert */}
              {searchError && (
                <div
                  style={{
                    marginTop: '8px',
                    padding: '8px 12px',
                    borderRadius: '8px',
                    background: 'rgba(239, 68, 68, 0.1)',
                    border: '1px solid rgba(239, 68, 68, 0.3)',
                    color: '#FCA5A5',
                    fontSize: '12px',
                    display: 'flex',
                    alignItems: 'center',
                    gap: '8px'
                  }}
                >
                  <AlertCircle size={14} style={{ flexShrink: 0 }} />
                  <span>{searchError}</span>
                </div>
              )}
            </div>

            {/* ── MANUAL DEVICE ID (EXPANDABLE OFFLINE FALLBACK) ── */}
            <div style={{ marginTop: '4px', marginBottom: '8px' }}>
              <button
                type="button"
                onClick={() => setShowManualDeviceId(!showManualDeviceId)}
                style={{
                  background: 'none',
                  border: 'none',
                  color: 'var(--text-muted)',
                  fontSize: '12px',
                  display: 'flex',
                  alignItems: 'center',
                  gap: '6px',
                  cursor: 'pointer',
                  padding: '4px 0'
                }}
              >
                {showManualDeviceId ? <ChevronUp size={14} /> : <ChevronDown size={14} />}
                {showManualDeviceId ? 'Hide manual Device ID' : (deviceId ? `Linked Device: ${deviceId} (click to change)` : 'Or enter Device ID manually (Offline / No Gmail)')}
              </button>

              {showManualDeviceId && (
                <div className="input-group" style={{ marginTop: '10px' }}>
                  <label htmlFor="device-id-input" className="input-label">
                    Device ID <span className="label-required">*</span>
                  </label>
                  <div className="input-with-action">
                    <input
                      id="device-id-input"
                      type="text"
                      value={deviceId}
                      onChange={handleDeviceIdChange}
                      placeholder="e.g. FG-8821-4902"
                      className="text-input mono-input"
                      required
                    />
                    <button
                      type="button"
                      onClick={() => {
                        const rnd = 'FG-' + Math.floor(1000 + Math.random() * 9000) + '-' + Math.floor(1000 + Math.random() * 9000);
                        setDeviceId(rnd);
                      }}
                      className="btn-ghost"
                      title="Generate Random Sample Device ID"
                    >
                      <RefreshCw size={14} /> Sample
                    </button>
                  </div>
                  <span className="input-hint">
                    Format: FG-XXXX-XXXX. Found inside user app &rarr; Settings &rarr; Support &rarr; Offline Fallback.
                  </span>
                </div>
              )}
            </div>

            {/* Date Selection */}
            <div className="input-group">
              <label htmlFor="date-picker" className="input-label">
                Target Date (Validity Window)
              </label>
              <div className="date-chip-row">
                <button
                  type="button"
                  onClick={() => setSelectedDate(getTodayDateString(-1))}
                  className={`chip-btn ${selectedDate === getTodayDateString(-1) ? 'chip-active' : ''}`}
                >
                  Yesterday
                </button>
                <button
                  type="button"
                  onClick={() => setSelectedDate(getTodayDateString(0))}
                  className={`chip-btn ${selectedDate === getTodayDateString(0) ? 'chip-active' : ''}`}
                >
                  Today (Default)
                </button>
                <button
                  type="button"
                  onClick={() => setSelectedDate(getTodayDateString(1))}
                  className={`chip-btn ${selectedDate === getTodayDateString(1) ? 'chip-active' : ''}`}
                >
                  Tomorrow
                </button>
                <input
                  id="date-picker"
                  type="date"
                  value={selectedDate}
                  onChange={(e) => setSelectedDate(e.target.value)}
                  className="date-input"
                />
              </div>
            </div>

            {/* Action Selection Grid */}
            <div className="input-group">
              <label className="input-label">
                Select Targeted Action <span className="label-required">*</span>
              </label>
              <div className="action-selection-grid">
                {ACTION_DEFINITIONS.map((act) => {
                  const isSelected = selectedActionCode === act.code;
                  return (
                    <div
                      key={act.code}
                      onClick={() => {
                        setSelectedActionCode(act.code);
                        // Reset or set default target preset if applicable
                        if (act.requiresTarget && act.presets && act.presets.length > 0) {
                          setTargetPayload(act.presets[0].value);
                        } else {
                          setTargetPayload('');
                        }
                      }}
                      className={`action-card-option ${isSelected ? 'selected' : ''}`}
                    >
                      <div className="action-option-header">
                        <span className={`badge badge-${act.badgeColor}`}>Action {act.code}</span>
                        <span className="action-category-tag">{act.category}</span>
                      </div>
                      <div className="action-option-name">{act.name}</div>
                      <div className="action-option-desc">{act.description}</div>
                    </div>
                  );
                })}
              </div>
            </div>

            {/* Conditional Target Input */}
            {currentAction.requiresTarget && (
              <div className="input-group animate-fade-in">
                <label htmlFor="target-payload-input" className="input-label">
                  {currentAction.targetLabel || 'Target Identifier'}{' '}
                  <span className="label-required">*</span>
                </label>
                <input
                  id="target-payload-input"
                  type="text"
                  value={targetPayload}
                  onChange={(e) => setTargetPayload(e.target.value)}
                  placeholder={currentAction.targetPlaceholder}
                  className="text-input mono-input"
                  required
                />
                {currentAction.presets && (
                  <div className="preset-badges-row">
                    <span className="preset-label">Quick select:</span>
                    {currentAction.presets.map((preset) => (
                      <button
                        type="button"
                        key={preset.value}
                        onClick={() => setTargetPayload(preset.value)}
                        className={`preset-pill ${targetPayload === preset.value ? 'preset-active' : ''}`}
                      >
                        {preset.label}
                      </button>
                    ))}
                  </div>
                )}
              </div>
            )}

            {/* Reason / Ticket Reference */}
            <div className="input-group">
              <label htmlFor="reason-input" className="input-label">
                Support Note / Reason (For Audit Log)
              </label>
              <input
                id="reason-input"
                type="text"
                value={reason}
                onChange={(e) => setReason(e.target.value)}
                placeholder="e.g. User verified identity, approved YouTube for studying"
                className="text-input"
              />
            </div>

            {/* Master Secret Key Collapsible Toggle */}
            <div className="secret-collapsible">
              <button
                type="button"
                onClick={() => setShowSecret(!showSecret)}
                className="secret-toggle-btn"
              >
                <Lock size={13} />
                <span>{showSecret ? 'Hide Master Formula Key' : 'Advanced: Master Secret Salt'}</span>
              </button>
              {showSecret && (
                <div className="secret-input-area animate-fade-in">
                  <input
                    type="password"
                    value={secretKey}
                    onChange={(e) => setSecretKey(e.target.value)}
                    className="text-input mono-input"
                    title="Master Secret Salt Key"
                  />
                  <span className="input-hint">
                    This key matches the Android app's local decryption engine. Do not change unless updating app build.
                  </span>
                </div>
              )}
            </div>

            {/* Submit CTA */}
            <button
              id="generate-code-submit-btn"
              type="submit"
              className="btn-primary generate-cta"
            >
              <Sparkles size={18} />
              <span>Generate 6-Digit Support Code</span>
            </button>
          </form>
        </div>

        {/* Right Panel: Generated Output Card */}
        <div className="generator-result-column">
          {generatedResult ? (
            <div className="glass-card result-display-card animate-fade-in">
              <div className="result-header">
                <span className="badge badge-emerald">Ready for User</span>
                <span className="result-time">Valid Today until Midnight</span>
              </div>

              {/* Big 6-Digit PIN Box */}
              <div className="pin-reveal-box">
                <div className="pin-glow-backdrop" />
                <div className="pin-digits-display">
                  {generatedResult.code.split('').map((digit, idx) => (
                    <span key={idx} className="pin-digit-box">
                      {digit}
                    </span>
                  ))}
                </div>
                <div className="pin-meta-line">
                  <span className="mono-badge">{generatedResult.normDevice}</span>
                  <span className="divider-bullet">&bull;</span>
                  <span className="mono-badge">Action {generatedResult.normAction}</span>
                </div>
              </div>

              {/* Copy Single PIN Button */}
              <div className="action-buttons-group">
                <button
                  id="copy-pin-btn"
                  onClick={handleCopyCode}
                  className="btn-primary copy-pin-cta"
                >
                  {copiedCode ? <Check size={18} /> : <Copy size={18} />}
                  <span>{copiedCode ? 'PIN Copied to Clipboard!' : 'Copy 6-Digit PIN'}</span>
                </button>
              </div>

              {/* Scope & Protection Summary Box */}
              <div className="scope-box">
                <div className="scope-title">
                  <Info size={15} />
                  <span>Strict Targeted Scope Enforced:</span>
                </div>
                <ul className="scope-list">
                  <li>
                    Unlocks <strong>ONLY</strong>: {currentAction.name}
                    {currentAction.requiresTarget && targetPayload ? ` (${targetPayload})` : ''}
                  </li>
                  <li>
                    Bypasses no other locks — all other blocked apps and uninstall limits stay <strong>100% active</strong>.
                  </li>
                  <li>Automatically expires at midnight (or next day depending on timezone grace).</li>
                </ul>
              </div>

              {/* Pre-formatted WhatsApp / Email Template */}
              <div className="message-template-card">
                <div className="template-header">
                  <div className="template-title">
                    <MessageSquare size={16} />
                    <span>Customer Support Message Template</span>
                  </div>
                  <div className="lang-toggle-group">
                    <button
                      type="button"
                      onClick={() => setMsgLang('hi')}
                      className={`lang-btn ${msgLang === 'hi' ? 'lang-active' : ''}`}
                    >
                      Hindi
                    </button>
                    <button
                      type="button"
                      onClick={() => setMsgLang('en')}
                      className={`lang-btn ${msgLang === 'en' ? 'lang-active' : ''}`}
                    >
                      English
                    </button>
                  </div>
                </div>

                <div className="template-text-box">
                  <pre className="template-content">
                    {generateSupportMessage({
                      deviceId: generatedResult.normDevice,
                      code: generatedResult.code,
                      actionCode: generatedResult.normAction,
                      targetPayload: currentAction.requiresTarget ? targetPayload : '',
                      language: msgLang
                    })}
                  </pre>
                </div>

                <button
                  id="copy-template-msg-btn"
                  onClick={handleCopyMessage}
                  className="btn-secondary copy-template-cta"
                >
                  {copiedMsg ? <Check size={16} /> : <Copy size={16} />}
                  <span>{copiedMsg ? 'Message Copied!' : 'Copy Full WhatsApp Message'}</span>
                </button>
              </div>
            </div>
          ) : (
            <div className="glass-card empty-result-placeholder">
              <div className="empty-icon-circle pulse-glow">
                <Key size={36} />
              </div>
              <h3 className="empty-title">Ready to Generate</h3>
              <p className="empty-desc">
                Select the user&apos;s Device ID and the targeted action on the left, then click{' '}
                <strong>Generate Support Code</strong> to calculate the offline 6-digit cryptographic PIN.
              </p>
              <div className="algorithm-badge">
                <span>HMAC-SHA256 &bull; Zero Server Latency &bull; 100% Offline Compatible</span>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
