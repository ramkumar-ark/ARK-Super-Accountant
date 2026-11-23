import { useAuthStore } from '@/store/authStore'
import { useTdsApplicability } from '@/hooks/useTdsApplicability'
import { fyLabel, type TdsApplicabilityYear } from '@/lib/tdsApplicabilityApi'

const BASIS_LABELS: Record<TdsApplicabilityYear['basis'], { on: string; off: string }> = {
  ENTITY_TYPE: { on: 'TDS applies (entity type)', off: 'Not a deductor (entity type)' },
  DECLARED: { on: 'TDS applies (declared)', off: 'Not a deductor (declared)' },
  CONFIRMED_FROM_BOOKS: {
    on: 'TDS applies (confirmed from books)',
    off: 'Not a deductor (confirmed from books)',
  },
  LEGACY_DEFAULT: { on: 'TDS applies (existing organization)', off: 'Not a deductor' },
  CARRIED_FORWARD: { on: 'TDS applies (carried forward)', off: 'Not a deductor (carried forward)' },
}

const ENTITY_CODE_LABELS: Record<string, string> = {
  K: 'Company',
  M: 'Branch / Division of Company',
  P: 'Association of Person (AOP)',
  T: 'Association of Person (Trust)',
  J: 'Artificial Juridical Person',
  B: 'Body of Individuals',
  Q: 'Individual/HUF',
  F: 'Firm',
}

export function TdsApplicabilitySection() {
  const { user } = useAuthStore()
  const { data } = useTdsApplicability(user?.organizationId)

  if (!data?.years) return <section />

  return (
    <section>
      <h2 className="text-lg font-semibold mb-2">TDS applicability</h2>
      <p className="text-sm text-[var(--color-text-muted)] mb-3">
        {data.entityCode
          ? `Deductor type: ${ENTITY_CODE_LABELS[data.entityCode] ?? data.entityCode} (${data.entityCodeSource === 'PAN' ? 'from PAN' : 'declared'})`
          : 'Deductor type: not known yet'}
        {data.tan ? ` — TAN ${data.tan}` : ' — no TAN saved'}
        . TDS features are hidden for the years where TDS does not apply; existing data is kept.
      </p>
      <ul className="flex flex-col gap-1 text-sm">
        {data.years.map((year) => {
          const labels = BASIS_LABELS[year.basis]
          return (
            <li key={year.financialYear} className="flex items-center gap-2">
              <span>
                {fyLabel(year.financialYear)} — {year.applicable ? labels.on : labels.off}
              </span>
              {year.needsAnswer && (
                <span className="text-xs px-1.5 py-0.5 rounded bg-amber-100 text-amber-800">
                  Needs answer
                </span>
              )}
            </li>
          )
        })}
      </ul>
    </section>
  )
}
