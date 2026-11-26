import { useQuery } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { apiErrorMessage } from '@/lib/apiError'
import { tdsSectionBothNames, useTdsSections } from '@/lib/tdsSections'

interface MasterChange {
  id: string
  changeKind: string
  ledgerName: string | null
  attributeName: string | null
  oldValue: string | null
  newValue: string | null
  touchedScopes: string[]
  blocking: boolean
  createdAt: string
}

const ATTRIBUTE_LABELS: Record<string, string> = {
  category: 'Category',
  gstin: 'GSTIN',
  gstRegistrationType: 'GST registration type',
  gstApplicable: 'GST applicable',
  gstApplicabilityType: 'GST applicability',
  hsnSacCode: 'HSN/SAC code',
  pan: 'PAN',
  tdsSection: 'TDS section',
  tdsApplicable: 'TDS applicable',
}

/**
 * The masters change list for one completed masters upload: what the tamper
 * comparison found against the frozen masters attributes.
 */
export function MastersChangeList({ uploadId }: { uploadId: string }) {
  const changesQuery = useQuery({
    queryKey: ['master-changes', uploadId],
    queryFn: async () => {
      const res = await api.get(`/v1/masters/uploads/${uploadId}/changes`)
      return res.data as { changes: MasterChange[] }
    },
  })

  const { data: tdsSections } = useTdsSections()

  const changes = changesQuery.data?.changes ?? []

  return (
    <div>
      <h3 className="text-sm font-semibold text-[var(--color-text-primary)] mb-3">
        Changes against the frozen masters
      </h3>
      {changesQuery.isLoading && (
        <p className="text-sm text-[var(--color-text-secondary)]" role="status">
          Loading changes…
        </p>
      )}
      {changesQuery.isError && (
        <p className="text-sm text-[var(--color-danger)]" role="alert">
          {apiErrorMessage(changesQuery.error, 'Could not load changes. Try again.')}
        </p>
      )}
      {!changesQuery.isLoading && !changesQuery.isError && changes.length === 0 && (
        <p className="text-sm text-[var(--color-text-muted)]">
          No changes against the frozen masters.
        </p>
      )}
      {changes.length > 0 && (
        <table className="w-full text-sm border border-[var(--color-border)] rounded-[var(--radius-md)] overflow-hidden">
          <thead className="bg-[var(--color-surface)] text-left text-xs text-[var(--color-text-muted)]">
            <tr>
              <th className="px-3 py-2 font-normal">Status</th>
              <th className="px-3 py-2 font-normal">Kind</th>
              <th className="px-3 py-2 font-normal">Ledger</th>
              <th className="px-3 py-2 font-normal">Change</th>
              <th className="px-3 py-2 font-normal">Scopes</th>
            </tr>
          </thead>
          <tbody>
            {changes.map((c) => {
              const attributeLabel = ATTRIBUTE_LABELS[c.attributeName ?? ''] ?? c.attributeName
              const displayValue = (value: string | null) => {
                if (!value) {
                  return '—'
                }
                return c.attributeName === 'tdsSection' ? tdsSectionBothNames(value, tdsSections) : value
              }
              return (
                <tr key={c.id} className="border-t border-[var(--color-border)]">
                  <td className="px-3 py-2">
                    {c.blocking ? (
                      <span className="text-xs px-2 py-0.5 rounded-full bg-[var(--color-danger-bg)] text-[var(--color-danger)]">
                        Blocking
                      </span>
                    ) : (
                      <span className="text-xs px-2 py-0.5 rounded-full bg-[var(--color-info-bg)] text-[var(--color-info)]">
                        Informational
                      </span>
                    )}
                  </td>
                  <td className="px-3 py-2 text-[var(--color-text-secondary)]">
                    {c.changeKind.replace('TAMPER_', '').replace('MASTER_', '')}
                  </td>
                  <td className="px-3 py-2 text-[var(--color-text-primary)]">{c.ledgerName}</td>
                  <td className="px-3 py-2 font-mono text-[var(--color-text-primary)]">
                    {attributeLabel}: {displayValue(c.oldValue)} → {displayValue(c.newValue)}
                  </td>
                  <td className="px-3 py-2 text-xs text-[var(--color-text-muted)]">
                    {c.touchedScopes.join(', ')}
                  </td>
                </tr>
              )
            })}
          </tbody>
        </table>
      )}
    </div>
  )
}
