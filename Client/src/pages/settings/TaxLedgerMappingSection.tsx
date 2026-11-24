import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useAuthStore } from '@/store/authStore'
import { apiErrorMessage } from '@/lib/apiError'
import {
  getTaxLedgerMapping,
  saveTaxLedgerMapping,
  TAX_LEDGER_ROLE_LABELS,
  type TaxLedgerRole,
  type GstHead,
} from '@/lib/taxMappingApi'

const ROLES = Object.keys(TAX_LEDGER_ROLE_LABELS) as TaxLedgerRole[]
const HEADS: GstHead[] = ['IGST', 'CGST', 'SGST', 'CESS']

type Draft = { role: TaxLedgerRole | null; head: GstHead | null }

export function TaxLedgerMappingSection() {
  const { user } = useAuthStore()
  const queryClient = useQueryClient()
  const canEdit = user?.role === 'ROLE_ACCOUNTANT'

  const { data: view, isLoading, isError, error } = useQuery({
    queryKey: ['tax-ledger-mapping'],
    queryFn: getTaxLedgerMapping,
  })

  const [drafts, setDrafts] = useState<Record<string, Draft>>({})

  const ledgers = view?.ledgers ?? []
  const blockers = view?.blockers ?? []
  const complete = view?.complete ?? false

  const rows = ledgers.map((r) => {
    const draft: Draft | undefined = drafts[r.ledgerName]
    const role = draft ? draft.role : r.saved ? r.role : r.suggestedRole
    const head =
      draft && draft.role !== 'NOT_TAX' ? draft.head : r.saved ? r.head : r.suggestedHead
    const edited = draft !== undefined
    return { ...r, role, head, edited }
  })

  const everyRowHasRole = rows.every((r) => r.role !== null)
  const everyNonNotTaxHasHead = rows.every(
    (r) => r.role === 'NOT_TAX' || r.head !== null,
  )
  const canSave = canEdit && everyRowHasRole && everyNonNotTaxHasHead

  const mutation = useMutation({
    mutationFn: () =>
      saveTaxLedgerMapping(
        rows.map((r) => ({
          ledgerName: r.ledgerName,
          role: r.role as TaxLedgerRole,
          head: r.role === 'NOT_TAX' ? null : r.head,
        })),
      ),
    onSuccess: () => {
      setDrafts({})
      queryClient.invalidateQueries({ queryKey: ['tax-ledger-mapping'] })
      queryClient.invalidateQueries({ queryKey: ['baseline-status'] })
    },
  })

  function setDraft(ledgerName: string, base: Draft, patch: Partial<Draft>) {
    setDrafts((prev) => {
      const current = prev[ledgerName] ?? base
      return { ...prev, [ledgerName]: { ...current, ...patch } }
    })
  }

  if (isLoading) return <p className="text-sm text-[var(--color-text-muted)]">Loading…</p>
  if (isError || !view)
    return (
      <section>
        <h2 className="text-lg font-semibold mb-2">Tax ledgers</h2>
        <p role="alert" className="text-sm text-red-600">
          {apiErrorMessage(error, 'Could not load tax-ledger mapping')}
        </p>
      </section>
    )

  return (
    <section>
      <div className="flex items-center gap-3 mb-2">
        <h2 className="text-lg font-semibold">Tax ledgers</h2>
        <span
          className={
            complete
              ? 'text-xs px-2 py-0.5 rounded-full bg-green-100 text-green-800'
              : 'text-xs px-2 py-0.5 rounded-full bg-amber-100 text-amber-800'
          }
        >
          {complete ? 'Complete' : 'Incomplete'}
        </span>
      </div>
      {blockers.length > 0 && (
        <ul className="text-sm text-red-600 mb-2 list-disc list-inside">
          {blockers.map((b) => (
            <li key={b}>{b}</li>
          ))}
        </ul>
      )}
      <table className="w-full text-sm">
        <thead>
          <tr className="text-left border-b">
            <th className="py-2">Ledger</th>
            <th className="py-2">Group</th>
            <th className="py-2">Role</th>
            <th className="py-2">Head</th>
            <th className="py-2">Status</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((r) => {
            const missingHead = r.role !== null && r.role !== 'NOT_TAX' && r.head === null
            return (
              <tr key={r.ledgerName} className="border-b">
                <td className="py-2">{r.ledgerName}</td>
                <td className="py-2">{r.parentGroup ?? '—'}</td>
                <td className="py-2">
                  <select
                    aria-label={`Role for ${r.ledgerName}`}
                    className="border rounded px-2 py-1 text-sm"
                    disabled={!canEdit}
                    value={r.role ?? ''}
                    onChange={(e) => {
                      const nextRole = (e.target.value || null) as TaxLedgerRole | null
                      setDraft(r.ledgerName, { role: r.role, head: r.head }, { role: nextRole, head: null })
                    }}
                  >
                    <option value="">—</option>
                    {ROLES.map((roleOption) => (
                      <option key={roleOption} value={roleOption}>
                        {TAX_LEDGER_ROLE_LABELS[roleOption]}
                      </option>
                    ))}
                  </select>
                </td>
                <td className="py-2">
                  <select
                    aria-label={`Head for ${r.ledgerName}`}
                    className="border rounded px-2 py-1 text-sm"
                    disabled={!canEdit || r.role === 'NOT_TAX'}
                    value={r.role === 'NOT_TAX' ? '' : (r.head ?? '')}
                    onChange={(e) =>
                      setDraft(
                        r.ledgerName,
                        { role: r.role, head: r.head },
                        { head: (e.target.value || null) as GstHead | null },
                      )
                    }
                  >
                    <option value="">—</option>
                    {HEADS.map((h) => (
                      <option key={h} value={h}>
                        {h}
                      </option>
                    ))}
                  </select>
                </td>
                <td className="py-2">
                  {missingHead ? (
                    <span className="text-red-600">Choose a head</span>
                  ) : r.saved ? (
                    <>Saved by {r.savedBy ?? '—'}</>
                  ) : r.edited ? (
                    'Not saved'
                  ) : (
                    'Suggested, not saved'
                  )}
                </td>
              </tr>
            )
          })}
        </tbody>
      </table>
      {mutation.isError && (
        <p role="alert" className="text-sm text-red-600 mt-2">
          {apiErrorMessage(mutation.error, 'Could not save the tax-ledger mapping')}
        </p>
      )}
      {canEdit && (
        <button
          type="button"
          className="mt-3 px-3 py-1.5 text-sm rounded bg-[var(--color-primary)] text-white disabled:opacity-50"
          disabled={!canSave || mutation.isPending}
          onClick={() => mutation.mutate()}
        >
          Save
        </button>
      )}
    </section>
  )
}
