import { useQuery } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { apiErrorMessage } from '@/lib/apiError'

interface UploadChange {
  id: string
  changeKind: string
  voucherKey: string
  voucherTypeName: string | null
  voucherNumber: string | null
  partyLedgerName: string | null
  oldDate: string | null
  newDate: string | null
  touchedScopes: string[]
  blocking: boolean
}

/**
 * The day-book change list for one completed upload: what the tamper comparison
 * found against the frozen day book, blocking rows first. Rendered inside the
 * upload screens worker O4 mounts.
 */
export function UploadChangeList({ uploadId }: { uploadId: string }) {
  const changesQuery = useQuery({
    queryKey: ['upload-changes', uploadId],
    queryFn: async () => {
      const res = await api.get(`/v1/day-book/uploads/${uploadId}/changes`)
      return res.data as { changes: UploadChange[] }
    },
  })

  const changes = changesQuery.data?.changes ?? []

  return (
    <div>
      <h3 className="text-sm font-semibold text-[var(--color-text-primary)] mb-3">
        Changes against the frozen day book
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
          No changes against the frozen day book.
        </p>
      )}
      {changes.length > 0 && (
        <table className="w-full text-sm border border-[var(--color-border)] rounded-[var(--radius-md)] overflow-hidden">
          <thead className="bg-[var(--color-surface)] text-left text-xs text-[var(--color-text-muted)]">
            <tr>
              <th className="px-3 py-2 font-normal">Status</th>
              <th className="px-3 py-2 font-normal">Kind</th>
              <th className="px-3 py-2 font-normal">Voucher</th>
              <th className="px-3 py-2 font-normal">Party</th>
              <th className="px-3 py-2 font-normal">Date</th>
              <th className="px-3 py-2 font-normal">Scopes</th>
            </tr>
          </thead>
          <tbody>
            {changes.map((c) => (
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
                  {c.changeKind.replace('TAMPER_', '')}
                </td>
                <td className="px-3 py-2 font-mono text-[var(--color-text-primary)]">
                  {c.voucherTypeName} {c.voucherNumber}
                </td>
                <td className="px-3 py-2 text-[var(--color-text-secondary)]">
                  {c.partyLedgerName}
                </td>
                <td className="px-3 py-2 text-[var(--color-text-secondary)]">
                  {c.oldDate ?? c.newDate}
                </td>
                <td className="px-3 py-2 text-xs text-[var(--color-text-muted)]">
                  {c.touchedScopes.join(', ')}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  )
}
