import React, { useState, useEffect } from 'react';
import {
  Inbox,
  CheckCircle,
  XCircle,
  Clock,
  Smartphone,
  Sparkles,
  Copy,
  Check,
  AlertCircle,
  Filter,
  MessageSquare
} from 'lucide-react';
import confetti from 'canvas-confetti';
import { subscribeToSupportTickets, updateSupportTicket, getAdminEmail, cacheTicketCodeLocally, getCachedTicketCode } from '../firebase';
import { generateSupportCode, ACTION_DEFINITIONS } from '../crypto/codeGenerator';

export default function SupportTicketsTab({ onGenerateForDevice }) {
  const [tickets, setTickets] = useState([]);
  const [filterStatus, setFilterStatus] = useState('pending');
  const [resolvingTicketId, setResolvingTicketId] = useState(null);
  const [copiedTicketId, setCopiedTicketId] = useState(null);
  // Codes issued from this browser, keyed by ticket id (never stored in Firestore).
  const [localCodes, setLocalCodes] = useState({});

  useEffect(() => {
    const unsubscribe = subscribeToSupportTickets((list) => {
      setTickets(list);
    });
    return () => unsubscribe();
  }, []);

  const filteredTickets = tickets.filter((t) => {
    if (filterStatus === 'all') return true;
    return t.status === filterStatus;
  });

  const handleApproveWithCode = async (ticket) => {
    try {
      const gen = generateSupportCode({
        deviceId: ticket.deviceId,
        actionCode: ticket.actionCode,
        targetPayload: ticket.targetPayload || ''
      });

      // The PIN itself is never written to Firestore: this collection is readable from the phone,
      // and a stored code sitting next to its deviceId would be a ready-made unlock for anyone who
      // can list the collection. The fingerprint identifies the issuance; the code stays local.
      await updateSupportTicket(ticket.id, {
        status: 'resolved',
        resolvedAt: new Date().toISOString(),
        responseCodeFingerprint: gen.codeFingerprint,
        resolvedBy: getAdminEmail() || 'UNATTRIBUTED — no signed-in admin'
      });
      cacheTicketCodeLocally(ticket.id, gen.code);
      setLocalCodes((prev) => ({ ...prev, [ticket.id]: gen.code }));

      setResolvingTicketId(ticket.id);

      try {
        confetti({
          particleCount: 60,
          spread: 70,
          origin: { y: 0.7 }
        });
      } catch {
        // Safe
      }

      setTimeout(() => setResolvingTicketId(null), 3000);
    } catch (e) {
      alert('Error approving ticket: ' + e.message);
    }
  };

  const handleReject = async (ticketId) => {
    const reason = prompt('Enter rejection reason for customer:', 'Request did not meet safety / self-control criteria.');
    if (!reason) return;
    await updateSupportTicket(ticketId, {
      status: 'rejected',
      resolvedAt: new Date().toISOString(),
      rejectionReason: reason,
      resolvedBy: getAdminEmail() || 'UNATTRIBUTED — no signed-in admin'
    });
  };

  const handleCopyCode = (ticketId, code) => {
    navigator.clipboard.writeText(code);
    setCopiedTicketId(ticketId);
    setTimeout(() => setCopiedTicketId(null), 2000);
  };

  /**
   * New codes live only in this browser; `ticket.responseCode` is kept as a fallback so tickets
   * resolved before this change still show the PIN their agent issued.
   */
  const codeFor = (ticket) =>
    localCodes[ticket.id] || getCachedTicketCode(ticket.id) || ticket.responseCode || '';

  const pendingCount = tickets.filter((t) => t.status === 'pending').length;

  return (
    <div className="tab-content animate-fade-in">
      <div className="tickets-container glass-card">
        <div className="card-header tickets-header">
          <div className="card-title-group">
            <span className="icon-pill">
              <Inbox size={18} />
            </span>
            <div>
              <div className="title-with-pill">
                <h2 className="card-title">Live In-App Support Queue</h2>
                {pendingCount > 0 && (
                  <span className="badge badge-amber">{pendingCount} Pending Requests</span>
                )}
              </div>
              <p className="card-subtitle">
                Incoming emergency unlock requests submitted directly from user phones via FocusGuard.
              </p>
            </div>
          </div>

          {/* Filter Pills */}
          <div className="filter-pill-group">
            <button
              onClick={() => setFilterStatus('pending')}
              className={`filter-btn ${filterStatus === 'pending' ? 'active' : ''}`}
            >
              Pending ({tickets.filter((t) => t.status === 'pending').length})
            </button>
            <button
              onClick={() => setFilterStatus('resolved')}
              className={`filter-btn ${filterStatus === 'resolved' ? 'active' : ''}`}
            >
              Resolved ({tickets.filter((t) => t.status === 'resolved').length})
            </button>
            <button
              onClick={() => setFilterStatus('all')}
              className={`filter-btn ${filterStatus === 'all' ? 'active' : ''}`}
            >
              All ({tickets.length})
            </button>
          </div>
        </div>

        {/* Tickets List */}
        <div className="tickets-list">
          {filteredTickets.length > 0 ? (
            filteredTickets.map((ticket) => {
              const actionDef =
                ACTION_DEFINITIONS.find((a) => a.code === ticket.actionCode) || {};

              return (
                <div
                  key={ticket.id}
                  className={`ticket-card ${ticket.status === 'pending' ? 'ticket-pending' : ''}`}
                >
                  <div className="ticket-top-row">
                    <div className="ticket-device-info">
                      <Smartphone size={16} className="text-secondary" />
                      <span className="ticket-device-id">{ticket.deviceId}</span>
                      <span className="ticket-phone-model">{ticket.phoneModel || 'Android Device'}</span>
                    </div>

                    <div className="ticket-status-col">
                      {ticket.status === 'pending' && (
                        <span className="badge badge-amber">
                          <Clock size={12} /> Pending Review
                        </span>
                      )}
                      {ticket.status === 'resolved' && (
                        <span className="badge badge-emerald">
                          <CheckCircle size={12} /> Resolved
                        </span>
                      )}
                      {ticket.status === 'rejected' && (
                        <span className="badge badge-rose">
                          <XCircle size={12} /> Rejected
                        </span>
                      )}
                      <span className="ticket-time">
                        {new Date(ticket.createdAt).toLocaleTimeString([], {
                          hour: '2-digit',
                          minute: '2-digit'
                        })}
                      </span>
                    </div>
                  </div>

                  {/* Requested Action */}
                  <div className="ticket-action-box">
                    <div className="ticket-action-header">
                      <span className={`badge badge-${actionDef.badgeColor || 'cyan'}`}>
                        Action {ticket.actionCode}
                      </span>
                      <span className="ticket-action-title">{ticket.actionName}</span>
                    </div>
                    {ticket.targetPayload && (
                      <div className="ticket-target-line">
                        <strong>Target:</strong> <code>{ticket.targetPayload}</code>
                      </div>
                    )}
                  </div>

                  {/* User's Reason Statement */}
                  <div className="ticket-reason-box">
                    <div className="reason-label">
                      <MessageSquare size={13} /> User Reason:
                    </div>
                    <div className="reason-text">&ldquo;{ticket.reason}&rdquo;</div>
                  </div>

                  {/* Action Bar / Result */}
                  <div className="ticket-footer">
                    {ticket.status === 'pending' ? (
                      <div className="ticket-actions-row">
                        <button
                          onClick={() => handleApproveWithCode(ticket)}
                          className="btn-primary btn-sm"
                        >
                          <Sparkles size={15} />
                          <span>Approve &amp; Generate 6-Digit Code</span>
                        </button>
                        <button
                          onClick={() => handleReject(ticket.id)}
                          className="btn-ghost btn-sm text-rose"
                        >
                          <XCircle size={15} />
                          <span>Reject</span>
                        </button>
                      </div>
                    ) : ticket.status === 'resolved' && codeFor(ticket) ? (
                      <div className="ticket-resolved-box">
                        <div className="resolved-code-tag">
                          <span>Generated Code:</span>
                          <strong className="code-text">{codeFor(ticket)}</strong>
                          <button
                            onClick={() => handleCopyCode(ticket.id, codeFor(ticket))}
                            className="btn-ghost btn-xs"
                            title="Copy Code"
                          >
                            {copiedTicketId === ticket.id ? <Check size={13} /> : <Copy size={13} />}
                          </button>
                        </div>
                        {ticket.responseCodeFingerprint && (
                          <span className="resolved-by-tag" title="Audit fingerprint (not the code)">
                            Ledger: {ticket.responseCodeFingerprint.slice(0, 12)}…
                          </span>
                        )}
                        {ticket.resolvedBy && (
                          <span className="resolved-by-tag">Approved by {ticket.resolvedBy}</span>
                        )}
                      </div>
                    ) : (
                      <div className="ticket-rejected-reason">
                        Reason: {ticket.rejectionReason || 'Rejected by admin'}
                      </div>
                    )}
                  </div>
                </div>
              );
            })
          ) : (
            <div className="empty-tickets-view">
              <Inbox size={32} className="text-muted" />
              <p>No {filterStatus} support requests right now.</p>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
