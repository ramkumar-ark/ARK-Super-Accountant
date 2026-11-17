import { useQuery } from '@tanstack/react-query'
import { listPanOverrides } from '@/lib/tdsApi'

export function PanOverridePanel() {
  const { data: overrides, isError } = useQuery({
    queryKey: ['pan-overrides'],
    queryFn: listPanOverrides,
  })

  return (
    <div>
      <h3 className="mb-2 text-sm font-semibold text-[var(--color-text-primary)]">
        PAN overrides
      </h3>
      {isError && (
        <p role="alert" className="text-sm text-[var(--color-danger)]">
          Could not load PAN overrides.
        </p>
      )}
      {overrides && overrides.length === 0 && (
        <p className="text-sm text-[var(--color-text-muted)]">
          No PAN overrides. Overrides are created by approving a PAN finding.
        </p>
      )}
      {overrides && overrides.length > 0 && (
        <table className="w-full text-left text-sm">
          <thead>
            <tr className="border-b border-[var(--color-border)] text-[var(--color-text-muted)]">
              <th className="py-2 pr-2 font-medium">Deductee</th>
              <th className="py-2 pr-2 font-medium">PAN</th>
              <th className="py-2 pr-2 font-medium">Approved by</th>
              <th className="py-2 font-medium">Approved at</th>
            </tr>
          </thead>
          <tbody>
            {overrides.map((o) => (
              <tr key={o.partyLedgerName} className="border-b border-[var(--color-border)]">
                <td className="py-2 pr-2">{o.partyLedgerName}</td>
                <td className="py-2 pr-2">{o.pan}</td>
                <td className="py-2 pr-2">{o.approvedBy ?? '—'}</td>
                <td className="py-2">{o.approvedAt ? o.approvedAt.slice(0, 10) : '—'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  )
}
