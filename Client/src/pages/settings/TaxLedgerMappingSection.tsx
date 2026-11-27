import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { AlertCircle, Loader2 } from 'lucide-react'
import { useAuthStore } from '@/store/authStore'
import { apiErrorMessage } from '@/lib/apiError'
import {
  getTaxLedgerMapping,
  saveTaxLedgerMapping,
  TAX_LEDGER_MAPPING_QUERY_KEY,
  TAX_LEDGER_ROLE_LABELS,
  type GstHead,
  type TaxLedgerMappingItem,
  type TaxLedgerRole,
} from '@/lib/taxMappingApi'
import {
  ALERT_ERROR_CLASS,
  ALERT_ERROR_LIST_CLASS,
  GHOST_BUTTON_CLASS,
  LOADING_CLASS,
  PILL_SUCCESS_CLASS,
  PILL_WARNING_CLASS,
  PRIMARY_BUTTON_CLASS,
  SECTION_CARD_CLASS,
  STACKED_BODY_CLASS,
  STACKED_CELL_CLASS,
  STACKED_LABEL_CLASS,
  STACKED_ROW_CLASS,
  STACKED_TABLE_CLASS,
  STACKED_THEAD_CLASS,
  STACKED_VALUE_CLASS,
  TABLE_HEAD_CLASS,
  TABLE_INPUT_CLASS,
} from './sectionStyles'

const ROLES = Object.keys(TAX_LEDGER_ROLE_LABELS) as TaxLedgerRole[]
const HEADS: GstHead[] = ['IGST', 'CGST', 'SGST', 'CESS']

type Draft = { role: TaxLedgerRole | null; head: GstHead | null }

export function TaxLedgerMappingSection() {
  const { user } = useAuthStore()
  const queryClient = useQueryClient()
  const canEdit = user?.role === 'ROLE_ACCOUNTANT'

  const { data: view, isLoading, isError, error } = useQuery({
    queryKey: TAX_LEDGER_MAPPING_QUERY_KEY,
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

  // Dirty-state save: only rows the user touched are sent. A dirty row is
  // client-valid when it has a role and, unless it is NOT_TAX, a head.
  // Invalid dirty rows stay on screen with a per-row error and are skipped.
  const dirtyRows = rows.filter((r) => r.edited)
  const validDirtyRows = dirtyRows.filter(
    (r) => r.role !== null && (r.role === 'NOT_TAX' || r.head !== null),
  )
  const canSave = canEdit && validDirtyRows.length > 0

  const mutation = useMutation({
    mutationFn: (items: TaxLedgerMappingItem[]) => saveTaxLedgerMapping(items),
    onSuccess: (_data, items) => {
      setDrafts((prev) => {
        const next = { ...prev }
        for (const item of items) delete next[item.ledgerName]
        return next
      })
      queryClient.invalidateQueries({ queryKey: TAX_LEDGER_MAPPING_QUERY_KEY })
      queryClient.invalidateQueries({ queryKey: ['baseline-status'] })
    },
  })

  function setDraft(ledgerName: string, base: Draft, patch: Partial<Draft>) {
    setDrafts((prev) => {
      const current = prev[ledgerName] ?? base
      return { ...prev, [ledgerName]: { ...current, ...patch } }
    })
  }

  if (isLoading) {
    return (
      <div className={LOADING_CLASS}>
        <Loader2 size={16} className="animate-spin" />
        Loading…
      </div>
    )
  }
  if (isError || !view)
    return (
      <section className={SECTION_CARD_CLASS}>
        <h2 className="text-lg font-semibold mb-3">Tax ledgers</h2>
        <div role="alert" className={ALERT_ERROR_CLASS}>
          <AlertCircle size={16} className="flex-shrink-0" />
          {apiErrorMessage(error, 'Could not load tax-ledger mapping')}
        </div>
      </section>
    )

  return (
    <section className={SECTION_CARD_CLASS}>
      <div className="flex items-center gap-3 mb-3">
        <h2 className="text-lg font-semibold">Tax ledgers</h2>
        <span className={complete ? PILL_SUCCESS_CLASS : PILL_WARNING_CLASS}>
          {complete ? 'Complete' : 'Incomplete'}
        </span>
      </div>
      {blockers.length > 0 && (
        <div className={`${ALERT_ERROR_LIST_CLASS} mb-3`}>
          <AlertCircle size={16} className="flex-shrink-0 mt-0.5" />
          <ul className="list-disc list-inside">
            {blockers.map((b) => (
              <li key={b}>{b}</li>
            ))}
          </ul>
        </div>
      )}
      <table className={STACKED_TABLE_CLASS}>
        <thead className={STACKED_THEAD_CLASS}>
          <tr className="text-left">
            <th className={TABLE_HEAD_CLASS}>Ledger</th>
            <th className={TABLE_HEAD_CLASS}>Group</th>
            <th className={TABLE_HEAD_CLASS}>Role</th>
            <th className={TABLE_HEAD_CLASS}>Head</th>
            <th className={TABLE_HEAD_CLASS}>Status</th>
          </tr>
        </thead>
        <tbody className={STACKED_BODY_CLASS}>
          {rows.map((r) => {
            const missingHead = r.role !== null && r.role !== 'NOT_TAX' && r.head === null
            const suggestedRole =
              r.role !== null && (r.role === 'NOT_TAX' || r.head !== null) ? r.role : null
            return (
              <tr key={r.ledgerName} className={STACKED_ROW_CLASS}>
                <td className={STACKED_CELL_CLASS}>
                  <span className={STACKED_LABEL_CLASS}>Ledger</span>
                  <span className={STACKED_VALUE_CLASS}>{r.ledgerName}</span>
                </td>
                <td className={STACKED_CELL_CLASS}>
                  <span className={STACKED_LABEL_CLASS}>Group</span>
                  <span className={STACKED_VALUE_CLASS}>{r.parentGroup ?? '—'}</span>
                </td>
                <td className={STACKED_CELL_CLASS}>
                  <span className={STACKED_LABEL_CLASS}>Role</span>
                  <span className={STACKED_VALUE_CLASS}>
                    <select
                      aria-label={`Role for ${r.ledgerName}`}
                      className={TABLE_INPUT_CLASS}
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
                  </span>
                </td>
                <td className={STACKED_CELL_CLASS}>
                  <span className={STACKED_LABEL_CLASS}>Head</span>
                  <span className={STACKED_VALUE_CLASS}>
                    <select
                      aria-label={`Head for ${r.ledgerName}`}
                      className={TABLE_INPUT_CLASS}
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
                  </span>
                </td>
                <td className={STACKED_CELL_CLASS}>
                  <span className={STACKED_LABEL_CLASS}>Status</span>
                  <div className={`${STACKED_VALUE_CLASS} gap-2`}>
                    {r.edited && r.role === null ? (
                      <span className="text-[var(--color-danger)]">Choose a role</span>
                    ) : missingHead ? (
                      <span className="text-[var(--color-danger)]">Choose a head</span>
                    ) : r.saved ? (
                      <span className="text-[var(--color-text-secondary)]">
                        Saved by {r.savedBy ?? '—'}
                      </span>
                    ) : r.edited ? (
                      <span className="text-[var(--color-warning)]">Not saved</span>
                    ) : (
                      <span className="text-[var(--color-text-muted)]">Suggested, not saved</span>
                    )}
                    {canEdit && !r.saved && !r.edited && suggestedRole && (
                      <button
                        type="button"
                        aria-label={`Use suggested for ${r.ledgerName}`}
                        className={GHOST_BUTTON_CLASS}
                        onClick={() => {
                          if (!suggestedRole) return
                          setDrafts((prev) => ({
                            ...prev,
                            [r.ledgerName]: {
                              role: suggestedRole,
                              head: suggestedRole === 'NOT_TAX' ? null : r.head,
                            },
                          }))
                        }}
                      >
                        Use suggested
                      </button>
                    )}
                  </div>
                </td>
              </tr>
            )
          })}
        </tbody>
      </table>
      {mutation.isError && (
        <div role="alert" className={`${ALERT_ERROR_CLASS} mt-3`}>
          <AlertCircle size={16} className="flex-shrink-0" />
          {apiErrorMessage(mutation.error, 'Could not save the tax-ledger mapping')}
        </div>
      )}
      {canEdit && (
        <button
          type="button"
          className={`mt-4 ${PRIMARY_BUTTON_CLASS}`}
          disabled={!canSave || mutation.isPending}
          onClick={() =>
            mutation.mutate(
              validDirtyRows.map((r) => ({
                ledgerName: r.ledgerName,
                role: r.role as TaxLedgerRole,
                head: r.role === 'NOT_TAX' ? null : r.head,
              })),
            )
          }
        >
          Save
        </button>
      )}
    </section>
  )
}
