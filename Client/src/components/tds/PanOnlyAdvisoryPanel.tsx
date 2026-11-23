import { useMemo } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useAuthStore } from '@/store/authStore'
import {
  getPanOnlyAdvisories,
  getTdsApplicability,
  type PanOnlyAdvisory,
} from '@/lib/tdsApplicabilityApi'

function advisoryLine(advisory: PanOnlyAdvisory): string {
  return (
    'TDS applies to this payment without a TAN: deduct 2% and file ' +
    `${advisory.form} using your PAN.`
  )
}

function advisoryDetail(advisory: PanOnlyAdvisory): string {
  const rupees = `₹${advisory.amount.toLocaleString('en-IN')}`
  return `${advisory.section} — ${advisory.partyLedgerName}: ${rupees}` +
    (advisory.month ? ` (${advisory.month})` : '')
}

export function PanOnlyAdvisoryPanel() {
  const { user } = useAuthStore()
  const orgId = user?.organizationId

  const { data: applicability } = useQuery({
    queryKey: ['tds-applicability', orgId],
    queryFn: () => getTdsApplicability(orgId as string),
    enabled: !!orgId,
  })

  const nonDeductorYear = useMemo(() => {
    const years = (applicability?.years ?? []).filter((year) => !year.applicable)
    return years.length ? years[years.length - 1].financialYear : null
  }, [applicability])

  const { data: advisories } = useQuery({
    queryKey: ['pan-only-advisories', orgId, nonDeductorYear],
    queryFn: () => getPanOnlyAdvisories(orgId as string, nonDeductorYear as number),
    enabled: !!orgId && nonDeductorYear != null,
  })

  if (!advisories?.length) return null

  return (
    <div className="rounded-[var(--radius-lg)] border border-[var(--color-border)] bg-[var(--color-surface)] p-6 shadow-[var(--shadow-sm)] mb-6">
      <h2 className="text-sm font-semibold text-[var(--color-text-primary)]">
        PAN-only TDS advisories
      </h2>
      <ul className="mt-3 divide-y divide-[var(--color-border)]">
        {advisories.map((advisory) => (
          <li key={`${advisory.section}|${advisory.partyLedgerName}|${advisory.month ?? ''}`}
              className="py-2 text-sm">
            <p className="font-medium text-[var(--color-text-primary)]">
              {advisoryDetail(advisory)}
            </p>
            <p className="mt-1 text-[var(--color-text-secondary)]">{advisoryLine(advisory)}</p>
          </li>
        ))}
      </ul>
    </div>
  )
}
