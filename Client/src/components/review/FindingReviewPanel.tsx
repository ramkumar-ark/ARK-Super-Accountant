import { useState, type ReactNode } from 'react'
import { CheckCircle2 } from 'lucide-react'

export interface ReviewFinding {
  id: string
  resolveStatus: 'OPEN' | 'ACKNOWLEDGED' | 'RESOLVED' | 'APPROVED' | 'DISCARDED'
  resolveNote: string | null
  proposedValue: string | null
  submittedBy: string | null
  submittedAt?: string | null
  reviewedBy: string | null
  reviewedAt?: string | null
  reviewNote: string | null
  resolvedBy: string | null
  resolvedAt?: string | null
}

export type ReviewRole = 'ROLE_OWNER' | 'ROLE_ACCOUNTANT' | 'ROLE_OPERATOR' | 'ROLE_AUDITOR_CA'

export interface ReviewActionBody {
  value?: string
  note?: string
}

const RESOLVED_STATUSES = ['RESOLVED', 'APPROVED', 'DISCARDED']

/**
 * Shared per-finding review UI for every module: status display (pending review, previous
 * rejection note, resolved), the Override/Submit flow for OPEN findings (with the module's
 * value input as {@code valueInput}), and Approve/Reject for accountants on ACKNOWLEDGED
 * findings. Role gating mirrors the server: AUDITOR_CA and OWNER are read-only.
 */
export function FindingReviewPanel({
  finding,
  role,
  label,
  proposedDisplay,
  valueInput,
  submitDisabled = false,
  onOpenChange,
  onAction,
}: {
  finding: ReviewFinding
  role: ReviewRole
  /** Accessible name fragment, e.g. the ledger name. */
  label?: string
  /** What the module will take if the proposal is approved; defaults to proposedValue. */
  proposedDisplay?: string | null
  /** The module's override value input; its presence enables the Override Value toggle. */
  valueInput?: ReactNode
  submitDisabled?: boolean
  onOpenChange?: (open: boolean) => void
  onAction(action: 'ACKNOWLEDGE' | 'APPROVE' | 'REJECT', body: ReviewActionBody): Promise<void>
}) {
  const isAccountant = role === 'ROLE_ACCOUNTANT'
  const canSubmit = isAccountant || role === 'ROLE_OPERATOR'
  const isResolved = RESOLVED_STATUSES.includes(finding.resolveStatus)
  const [overriding, setOverriding] = useState(false)
  const [rejecting, setRejecting] = useState(false)
  const [note, setNote] = useState('')
  const [rejectNote, setRejectNote] = useState('')
  const [busy, setBusy] = useState(false)

  const setOpen = (open: boolean) => {
    setOverriding(open)
    onOpenChange?.(open)
  }

  async function run(action: 'ACKNOWLEDGE' | 'APPROVE' | 'REJECT', body: ReviewActionBody) {
    setBusy(true)
    try {
      await onAction(action, body)
      setOverriding(false)
      setRejecting(false)
      setNote('')
      setRejectNote('')
    } catch {
      // the parent displays the server error; the form stays open for correction
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="min-w-0">
      {isResolved && (
        <div className="flex items-center gap-1 mt-1">
          <CheckCircle2 size={14} className="text-[var(--color-success)]" />
          <span className="text-xs text-[var(--color-text-muted)]">
            Resolved
            {finding.resolveNote && ` — ${finding.resolveNote}`}
          </span>
        </div>
      )}
      {finding.resolveStatus === 'ACKNOWLEDGED' && (
        <div className="mt-2 text-xs text-[var(--color-text-secondary)]">
          <span className="inline-block px-2 py-0.5 rounded-full bg-[var(--color-warning-bg)] text-[var(--color-warning)]">
            Pending accountant review
          </span>
          <p className="mt-1">
            Proposed value: <span className="font-mono">{proposedDisplay ?? finding.proposedValue ?? '—'}</span>
            {finding.submittedBy && ` · submitted by ${finding.submittedBy}`}
          </p>
          {finding.resolveNote && <p>Note: {finding.resolveNote}</p>}
        </div>
      )}
      {finding.resolveStatus === 'OPEN' && finding.reviewNote && (
        <p className="mt-2 text-xs text-[var(--color-danger)]">
          Rejected{finding.reviewedBy ? ` by ${finding.reviewedBy}` : ''}: {finding.reviewNote}
        </p>
      )}

      {finding.resolveStatus === 'OPEN' && canSubmit && valueInput != null && (
        <div className="flex gap-2 flex-shrink-0">
          <button
            onClick={() => {
              if (overriding) {
                setOpen(false)
                return
              }
              // A rejected proposal comes back pre-filled so it can be corrected and resubmitted.
              setNote(finding.resolveNote ?? '')
              setRejecting(false)
              setOpen(true)
            }}
            aria-expanded={overriding}
            aria-label={`Override Value for ${label ?? finding.id}`}
            className="h-9 px-3 rounded-[var(--radius-md)] text-sm text-[var(--color-text-secondary)] hover:bg-[var(--color-surface-raised)]"
          >
            Override Value
          </button>
        </div>
      )}
      {finding.resolveStatus === 'ACKNOWLEDGED' && isAccountant && (
        <div className="flex gap-2 flex-shrink-0">
          <button
            onClick={() => run('APPROVE', {})}
            disabled={busy}
            aria-label={`Approve ${label ?? finding.id}`}
            className="h-9 px-3 bg-[var(--color-primary)] text-white rounded-[var(--radius-md)] text-sm disabled:opacity-50"
          >
            Approve
          </button>
          <button
            onClick={() => {
              setRejecting(rejecting ? false : true)
              setRejectNote('')
            }}
            disabled={busy}
            aria-expanded={rejecting}
            aria-label={`Reject ${label ?? finding.id}`}
            className="h-9 px-3 rounded-[var(--radius-md)] border border-[var(--color-border)] text-sm text-[var(--color-text-primary)] hover:bg-[var(--color-surface-raised)]"
          >
            Reject
          </button>
        </div>
      )}

      {overriding && finding.resolveStatus === 'OPEN' && (
        <div className="mt-3 bg-[var(--color-surface-raised)] rounded-[var(--radius-md)] p-4">
          {valueInput}
          <textarea
            rows={2}
            value={note}
            onChange={(e) => setNote(e.target.value)}
            placeholder="Add a note explaining the override (optional)…"
            className="w-full px-3 py-2 mt-2 rounded-[var(--radius-md)] border border-[var(--color-border)] bg-[var(--color-surface)] text-sm text-[var(--color-text-primary)] resize-none focus:outline-none focus:border-[var(--color-primary)]"
          />
          <div className="flex gap-2 mt-2">
            <button
              onClick={() => run('ACKNOWLEDGE', { note })}
              disabled={busy || submitDisabled}
              className="h-9 px-3 bg-[var(--color-primary)] text-white rounded-[var(--radius-md)] text-sm disabled:opacity-50"
            >
              {isAccountant ? 'Confirm Override' : 'Submit for Review'}
            </button>
            <button
              onClick={() => {
                setOpen(false)
                setNote('')
              }}
              className="h-9 px-3 text-sm text-[var(--color-text-secondary)] hover:bg-[var(--color-surface-raised)] rounded-[var(--radius-md)]"
            >
              Cancel Changes
            </button>
          </div>
        </div>
      )}
      {rejecting && finding.resolveStatus === 'ACKNOWLEDGED' && (
        <div className="mt-3 bg-[var(--color-surface-raised)] rounded-[var(--radius-md)] p-4">
          <textarea
            rows={2}
            value={rejectNote}
            onChange={(e) => setRejectNote(e.target.value)}
            placeholder="Explain why this is rejected (required)…"
            className="w-full px-3 py-2 rounded-[var(--radius-md)] border border-[var(--color-border)] bg-[var(--color-surface)] text-sm text-[var(--color-text-primary)] resize-none focus:outline-none focus:border-[var(--color-primary)]"
          />
          <div className="flex gap-2 mt-2">
            <button
              onClick={() => run('REJECT', { note: rejectNote.trim() })}
              disabled={busy || rejectNote.trim() === ''}
              className="h-9 px-3 bg-[var(--color-danger)] text-white rounded-[var(--radius-md)] text-sm disabled:opacity-50"
            >
              Confirm Reject
            </button>
            <button
              onClick={() => setRejecting(false)}
              className="h-9 px-3 text-sm text-[var(--color-text-secondary)] hover:bg-[var(--color-surface-raised)] rounded-[var(--radius-md)]"
            >
              Cancel
            </button>
          </div>
        </div>
      )}
    </div>
  )
}
