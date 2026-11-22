import type { ReactNode } from 'react'

export interface FindingValueState {
  value: string
  onChange: (value: string) => void
}

export interface FindingValueInput {
  node: ReactNode
  value: string
  valid: boolean
}

export const PAN_PATTERN = /^[A-Z]{5}[0-9]{4}[A-Z]$/
export const TAN_PATTERN = /^[A-Z]{4}[0-9]{5}[A-Z]$/

/**
 * The per-source value input shown in the shared review panel. Only findings whose backend
 * applier needs a typed value get one; GST review is note-only, so it has none.
 */
export function valueInputFor(
  finding: { source: string; findingType: string },
  state: FindingValueState,
): FindingValueInput | null {
  const isTdsPan =
    finding.source === 'TDS' &&
    (finding.findingType === 'PAN_MISSING' || finding.findingType === 'PAN_INVALID')
  if (!isTdsPan) {
    const isTdsTan =
      finding.source === 'TDS' &&
      finding.findingType === 'DEBTOR_TDS_RECEIVABLE_MISSING_TAN'
    if (!isTdsTan) return null
    return {
      node: (
        <label className="block text-xs text-[var(--color-text-secondary)] mb-1">
          Corrected TAN
          <input
            type="text"
            value={state.value}
            onChange={(e) => state.onChange(e.target.value)}
            className="mt-1 w-full h-10 px-3 rounded-[var(--radius-md)] border border-[var(--color-border)] bg-[var(--color-surface)] text-sm text-[var(--color-text-primary)] font-mono focus:outline-none focus:border-[var(--color-primary)]"
          />
        </label>
      ),
      value: state.value,
      valid: TAN_PATTERN.test(state.value.trim().toUpperCase()),
    }
  }
  return {
    node: (
      <label className="block text-xs text-[var(--color-text-secondary)] mb-1">
        Corrected PAN
        <input
          type="text"
          value={state.value}
          onChange={(e) => state.onChange(e.target.value)}
          className="mt-1 w-full h-10 px-3 rounded-[var(--radius-md)] border border-[var(--color-border)] bg-[var(--color-surface)] text-sm text-[var(--color-text-primary)] font-mono focus:outline-none focus:border-[var(--color-primary)]"
        />
      </label>
    ),
    value: state.value,
    valid: PAN_PATTERN.test(state.value),
  }
}
