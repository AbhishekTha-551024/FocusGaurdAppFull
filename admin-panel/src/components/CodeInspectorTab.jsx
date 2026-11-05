import React, { useState } from 'react';
import {
  Search,
  CheckCircle2,
  AlertTriangle,
  HelpCircle,
  Clock,
  Shield,
  Layers,
  ArrowRight
} from 'lucide-react';
import {
  reverseInspectCode,
  normalizeDeviceId
} from '../crypto/codeGenerator';

export default function CodeInspectorTab() {
  const [deviceId, setDeviceId] = useState('FG-8821-4902');
  const [inputCode, setInputCode] = useState('');
  const [inspectionResult, setInspectionResult] = useState(null);
  const [inspectionError, setInspectionError] = useState('');
  const [customPackage, setCustomPackage] = useState('');

  const handleInspect = (e) => {
    e?.preventDefault();
    setInspectionError('');
    const customList = customPackage.trim() ? [customPackage.trim().toLowerCase()] : [];
    try {
      // No secretKey is passed: the engine reads VITE_SUPPORT_MASTER_SECRET and refuses to guess.
      const result = reverseInspectCode({
        deviceId,
        inputCode,
        customTargets: customList
      });
      setInspectionResult({ ...result, inspectedCode: inputCode, inspectedDevice: deviceId });
    } catch (err) {
      setInspectionResult(null);
      setInspectionError(err.message || 'Could not inspect this code.');
    }
  };

  return (
    <div className="tab-content animate-fade-in">
      <div className="inspector-container glass-card">
        <div className="card-header">
          <div className="card-title-group">
            <span className="icon-pill">
              <Search size={18} />
            </span>
            <div>
              <h2 className="card-title">Passcode Inspector &amp; Diagnostic Engine</h2>
              <p className="card-subtitle">
                Customer says a code isn&apos;t working? Enter their Device ID and the 6-digit code to instantly reverse-engineer its exact action and validity date.
              </p>
            </div>
          </div>
        </div>

        <form onSubmit={handleInspect} className="inspector-search-form">
          <div className="inspector-inputs-grid">
            <div className="input-group">
              <label htmlFor="inspector-device-id" className="input-label">
                Device ID
              </label>
              <input
                id="inspector-device-id"
                type="text"
                value={deviceId}
                onChange={(e) => setDeviceId(normalizeDeviceId(e.target.value))}
                placeholder="e.g. FG-8821-4902"
                className="text-input mono-input"
                required
              />
            </div>

            <div className="input-group">
              <label htmlFor="inspector-code-input" className="input-label">
                6-Digit PIN to Diagnose
              </label>
              <input
                id="inspector-code-input"
                type="text"
                maxLength={6}
                value={inputCode}
                onChange={(e) => setInputCode(e.target.value.replace(/\D/g, ''))}
                placeholder="e.g. 582910"
                className="text-input mono-input"
                required
              />
            </div>

            <div className="input-group">
              <label htmlFor="custom-target-input" className="input-label">
                Custom Package / Keyword (Optional)
              </label>
              <input
                id="custom-target-input"
                type="text"
                value={customPackage}
                onChange={(e) => setCustomPackage(e.target.value)}
                placeholder="e.g. com.custom.app or customword"
                className="text-input mono-input"
              />
            </div>
          </div>

          <button
            id="diagnose-code-btn"
            type="submit"
            className="btn-primary inspector-submit-cta"
          >
            <Search size={17} />
            <span>Diagnose Passcode</span>
          </button>
        </form>

        {/* Configuration / diagnosis errors (missing master secret surfaces here, not as an alert) */}
        {inspectionError && (
          <div className="match-status-banner failure animate-fade-in" role="alert">
            <AlertTriangle size={22} />
            <div>
              <div className="match-title">Cannot inspect this code</div>
              <div className="match-desc">{inspectionError}</div>
            </div>
          </div>
        )}

        {/* Results Section */}
        {inspectionResult && (
          <div className="inspection-results-area animate-fade-in">
            {inspectionResult.found ? (
              <div className="match-found-card">
                <div className="match-status-banner success">
                  <CheckCircle2 size={22} />
                  <div>
                    <div className="match-title">
                      Verified Code Match Found ({inspectionResult.matches.length}{' '}
                      {inspectionResult.matches.length === 1 ? 'Action' : 'Actions'})
                    </div>
                    <div className="match-desc">
                      The code <strong>{inspectionResult.inspectedCode}</strong> is cryptographically valid for Device{' '}
                      <strong>{inspectionResult.inspectedDevice}</strong>.
                    </div>
                  </div>
                </div>

                <div className="matched-actions-list">
                  {inspectionResult.matches.map((match, idx) => (
                    <div key={idx} className="matched-action-item">
                      <div className="match-item-badge">
                        <span className={`badge badge-${match.action.badgeColor}`}>
                          Action {match.action.code}
                        </span>
                        {match.isToday ? (
                          <span className="badge badge-emerald">Signed For Today</span>
                        ) : (
                          <span className="badge badge-amber">Date: {match.date}</span>
                        )}
                      </div>

                      <div className="match-item-content">
                        <div className="match-action-title">{match.action.name}</div>
                        <div className="match-action-scope">
                          <strong>Target Scope:</strong>{' '}
                          {match.target ? (
                            <code>{match.target}</code>
                          ) : (
                            <span className="text-muted">Global setting (No specific package payload)</span>
                          )}
                        </div>
                        <div className="match-action-desc">{match.action.description}</div>
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            ) : (
              <div className="match-status-banner failure">
                <AlertTriangle size={22} />
                <div>
                  <div className="match-title">No Cryptographic Match Found</div>
                  <div className="match-desc">
                    The code <strong>{inspectionResult.inspectedCode}</strong> does not match any valid action for Device{' '}
                    <strong>{inspectionResult.inspectedDevice}</strong> across Yesterday, Today, or Tomorrow.
                  </div>
                  <ul className="troubleshooting-tips">
                    <li>Check the Device ID the user read out: hyphens matter (FG-8821-4902 is not FG88214902), letters are case-insensitive.</li>
                    <li>Verify if the user is in a vastly different timezone (more than &plusmn;24 hours).</li>
                    <li>
                      If the code was generated for a custom non-preset app package, enter the package name in the &quot;Custom Package&quot; field above and re-diagnose.
                    </li>
                  </ul>
                </div>
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
}
