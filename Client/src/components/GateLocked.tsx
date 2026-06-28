import { Link } from '@tanstack/react-router'
import { AlertTriangle, Lock } from 'lucide-react'

interface GateLockedBannerProps {
  reason: string
  unresolvedCount: number
}

/**
 * Non-blocking informational banner for pages where gate is informational only
 * (e.g. Day Book — upload is allowed, but TDS/GSTR-2B will be locked).
 */
export function GateLockedBanner({ reason, unresolvedCount }: GateLockedBannerProps) {
  const hasFindings = unresolvedCount > 0
  return (
    <div
      role="alert"
      className="flex items-start gap-3 bg-[var(--color-warning-bg)] border border-[var(--color-warning)]/20 rounded-[var(--radius-md)] p-4 mb-6"
    >
      <AlertTriangle size={18} className="text-[var(--color-warning)] flex-shrink-0 mt-0.5" />
      <div className="flex-1 min-w-0">
        <p className="text-sm font-semibold text-[var(--color-text-primary)] mb-1">
          {hasFindings ? 'Masters have unresolved HIGH findings' : 'Masters validation required'}
        </p>
        <p className="text-sm text-[var(--color-text-secondary)]">
          {reason}. TDS and GSTR-2B reports will be locked until resolved.
        </p>
        <div className="flex items-center gap-3 mt-2">
          {hasFindings && (
            <span className="text-xs px-2 py-1 rounded-[var(--radius-sm)] bg-[var(--color-danger-bg)] text-[var(--color-danger)]">
              {unresolvedCount} unresolved finding{unresolvedCount !== 1 ? 's' : ''}
            </span>
          )}
          <Link
            to="/masters"
            className="text-sm text-[var(--color-primary)] hover:underline"
          >
            Go to Masters &rarr; Findings
          </Link>
        </div>
      </div>
    </div>
  )
}

interface GateLockedFullProps {
  reason: string
  unresolvedCount: number
}

/**
 * Full-page locked state for Phase 5/6 pages where the feature itself is blocked
 * until masters findings are resolved.
 */
export function GateLockedFull({ reason, unresolvedCount }: GateLockedFullProps) {
  const hasFindings = unresolvedCount > 0
  return (
    <div role="main" className="pt-20 flex flex-col items-center gap-4">
      <Lock size={48} className="text-[var(--color-text-muted)]" />
      <h2 className="text-xl font-semibold text-[var(--color-text-primary)]">Feature Locked</h2>
      <p className="text-sm text-[var(--color-text-secondary)] max-w-sm text-center">{reason}</p>
      {hasFindings && (
        <p className="text-sm text-[var(--color-text-muted)]">
          {unresolvedCount} unresolved HIGH finding{unresolvedCount !== 1 ? 's' : ''} must be resolved
          to unlock this feature.
        </p>
      )}
      <Link
        to="/masters"
        className="h-11 px-6 flex items-center bg-[var(--color-primary)] text-white rounded-[var(--radius-md)] text-sm hover:bg-[var(--color-primary-hover)] transition-colors"
      >
        {hasFindings ? 'Resolve in Masters' : 'Go to Masters'}
      </Link>
    </div>
  )
}
