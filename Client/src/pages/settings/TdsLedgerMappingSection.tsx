import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useState, type Dispatch, type SetStateAction } from 'react'
import { AlertCircle, AlertTriangle, Loader2 } from 'lucide-react'
import { useAuthStore } from '@/store/authStore'
import { useTdsApplicability } from '@/hooks/useTdsApplicability'
import { apiErrorMessage } from '@/lib/apiError'
import { buildTdsSectionOptions, useTdsSections } from '@/lib/tdsSections'
import {
  TDS_LEDGER_MAPPING_QUERY_KEY,
  getTdsLedgerMapping,
  saveTdsLedgerMapping,
  TDS_LEDGER_ROLE_LABELS,
  type TdsLedgerMappingItem,
  type TdsLedgerMappingRow,
  type TdsLedgerRole,
} from '@/lib/tdsLedgerMappingApi'
import {
  ALERT_ERROR_CLASS,
  ALERT_ERROR_LIST_CLASS,
  ALERT_WARNING_LIST_CLASS,
  GHOST_BUTTON_CLASS,
  LOADING_CLASS,
  PILL_SUCCESS_CLASS,
  PILL_WARNING_CLASS,
  PRIMARY_BUTTON_CLASS,
  SECTION_CARD_CLASS,
  TABLE_CELL_CLASS,
  TABLE_HEAD_CLASS,
  TABLE_INPUT_CLASS,
  TABLE_ROW_CLASS,
} from './sectionStyles'

const ROLES = Object.keys(TDS_LEDGER_ROLE_LABELS) as TdsLedgerRole[]

type Draft = { role: TdsLedgerRole | null; section: string | null }
type Row = TdsLedgerMappingRow & { role: TdsLedgerRole | null; section: string | null; edited: boolean }

export function TdsLedgerMappingSection() {
  const { user } = useAuthStore()
  const queryClient = useQueryClient()
  const canEdit = user?.role === 'ROLE_ACCOUNTANT'
  const { anyTdsOn } = useTdsApplicability(user?.organizationId)

  const { data: view, isLoading, isError, error } = useQuery({
    queryKey: TDS_LEDGER_MAPPING_QUERY_KEY,
    queryFn: getTdsLedgerMapping,
  })

  const [drafts, setDrafts] = useState<Record<string, Draft>>({})

  const ledgers = view?.ledgers ?? []
  const blockers = view?.blockers ?? []
  const receivableWarnings = view?.receivableWarnings ?? []
  const complete = view?.complete ?? false

  const rows: Row[] = ledgers.map((r) => {
    const draft: Draft | undefined = drafts[r.ledgerName]
    const role = draft ? draft.role : r.saved ? r.role : r.suggestedRole
    const section =
      draft && draft.role === 'TDS_PAYABLE'
        ? draft.section
        : r.saved
          ? r.section
          : r.suggestedSection
    const edited = draft !== undefined
    return { ...r, role, section, edited }
  })

  const payableRows = rows.filter((r) => r.area === 'PAYABLE')
  const receivableRows = rows.filter((r) => r.area === 'RECEIVABLE')

  // Section is optional for TDS payable (null = combined / infer per voucher),
  // so only a role is required; payable rows hidden for non-deductors are neither
  // required nor saved.
  // Dirty-state save: only rows the user touched are sent. A dirty row is
  // client-valid once it has a role; invalid dirty rows stay on screen with a
  // per-row error and are skipped.
  const savableRows = anyTdsOn ? rows : receivableRows
  const dirtyRows = savableRows.filter((r) => r.edited)
  const validDirtyRows = dirtyRows.filter((r) => r.role !== null)
  const canSave = canEdit && validDirtyRows.length > 0

  const mutation = useMutation({
    mutationFn: (items: TdsLedgerMappingItem[]) => saveTdsLedgerMapping(items),
    onSuccess: (_data, items) => {
      setDrafts((prev) => {
        const next = { ...prev }
        for (const item of items) delete next[item.ledgerName]
        return next
      })
      queryClient.invalidateQueries({ queryKey: TDS_LEDGER_MAPPING_QUERY_KEY })
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
        <h2 className="text-lg font-semibold mb-3">TDS ledgers</h2>
        <div role="alert" className={ALERT_ERROR_CLASS}>
          <AlertCircle size={16} className="flex-shrink-0" />
          {apiErrorMessage(error, 'Could not load TDS-ledger mapping')}
        </div>
      </section>
    )

  return (
    <section className={SECTION_CARD_CLASS}>
      <div className="flex items-center gap-3 mb-3">
        <h2 className="text-lg font-semibold">TDS ledgers</h2>
        <span className={complete ? PILL_SUCCESS_CLASS : PILL_WARNING_CLASS}>
          {complete ? 'Complete' : 'Incomplete'}
        </span>
      </div>
      {anyTdsOn && blockers.length > 0 && (
        <div className={`${ALERT_ERROR_LIST_CLASS} mb-3`}>
          <AlertCircle size={16} className="flex-shrink-0 mt-0.5" />
          <ul className="list-disc list-inside">
            {blockers.map((b) => (
              <li key={b}>{b}</li>
            ))}
          </ul>
        </div>
      )}
      {anyTdsOn && (
        <div className="mb-6">
          <h3 className="text-sm font-semibold mb-2 text-[var(--color-text-primary)]">TDS payable ledgers</h3>
          {payableRows.length === 0 && (
            <p className="text-sm text-[var(--color-text-muted)] py-2">
              No TDS payable ledgers found in your books.
            </p>
          )}
          {payableRows.length > 0 && (
            <table className="w-full text-sm">
              <thead>
                <tr className="text-left">
                  <th className={TABLE_HEAD_CLASS}>Ledger</th>
                  <th className={TABLE_HEAD_CLASS}>Group</th>
                  <th className={TABLE_HEAD_CLASS}>Role</th>
                  <th className={TABLE_HEAD_CLASS}>Section</th>
                  <th className={TABLE_HEAD_CLASS}>Status</th>
                </tr>
              </thead>
              <tbody>
                {payableRows.map((r) => (
                  <LedgerRow key={r.ledgerName} r={r} canEdit={canEdit} setDraft={setDraft} setDrafts={setDrafts} />
                ))}
              </tbody>
            </table>
          )}
        </div>
      )}
      <div>
        <h3 className="text-sm font-semibold mb-2 text-[var(--color-text-primary)]">TDS receivable ledgers</h3>
        {receivableWarnings.length > 0 && (
          <div className={`${ALERT_WARNING_LIST_CLASS} mb-3`}>
            <AlertTriangle size={16} className="flex-shrink-0 mt-0.5" />
            <ul className="list-disc list-inside">
              {receivableWarnings.map((w) => (
                <li key={w}>{w}</li>
              ))}
            </ul>
          </div>
        )}
        {receivableRows.length === 0 && (
          <p className="text-sm text-[var(--color-text-muted)] py-2">
            No TDS receivable ledgers found in your books.
          </p>
        )}
        {receivableRows.length > 0 && (
          <table className="w-full text-sm">
            <thead>
              <tr className="text-left">
                <th className={TABLE_HEAD_CLASS}>Ledger</th>
                <th className={TABLE_HEAD_CLASS}>Group</th>
                <th className={TABLE_HEAD_CLASS}>Role</th>
                <th className={TABLE_HEAD_CLASS}>Section</th>
                <th className={TABLE_HEAD_CLASS}>Status</th>
              </tr>
            </thead>
            <tbody>
              {receivableRows.map((r) => (
                <LedgerRow key={r.ledgerName} r={r} canEdit={canEdit} setDraft={setDraft} setDrafts={setDrafts} />
              ))}
            </tbody>
          </table>
        )}
      </div>
      {mutation.isError && (
        <div role="alert" className={`${ALERT_ERROR_CLASS} mt-3`}>
          <AlertCircle size={16} className="flex-shrink-0" />
          {apiErrorMessage(mutation.error, 'Could not save the TDS-ledger mapping')}
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
                role: r.role as TdsLedgerRole,
                section: r.role === 'TDS_PAYABLE' ? r.section : null,
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

function LedgerRow({
  r,
  canEdit,
  setDraft,
  setDrafts,
}: {
  r: Row
  canEdit: boolean
  setDraft: (ledgerName: string, base: Draft, patch: Partial<Draft>) => void
  setDrafts: Dispatch<SetStateAction<Record<string, Draft>>>
}) {
  const { data: tdsSections } = useTdsSections()
  const sectionOptions = buildTdsSectionOptions(tdsSections).filter((o) => o.value !== 'NOT_SUBJECT')
  const suggestedRole = r.role !== null ? r.role : null
  return (
    <tr className={TABLE_ROW_CLASS}>
      <td className={TABLE_CELL_CLASS}>{r.ledgerName}</td>
      <td className={TABLE_CELL_CLASS}>{r.parentGroup ?? '—'}</td>
      <td className={TABLE_CELL_CLASS}>
        <select
          aria-label={`Role for ${r.ledgerName}`}
          className={TABLE_INPUT_CLASS}
          disabled={!canEdit}
          value={r.role ?? ''}
          onChange={(e) => {
            const nextRole = (e.target.value || null) as TdsLedgerRole | null
            setDraft(r.ledgerName, { role: r.role, section: r.section }, { role: nextRole, section: null })
          }}
        >
          <option value="">—</option>
          {ROLES.map((roleOption) => (
            <option key={roleOption} value={roleOption}>
              {TDS_LEDGER_ROLE_LABELS[roleOption]}
            </option>
          ))}
        </select>
      </td>
      <td className={TABLE_CELL_CLASS}>
        <select
          aria-label={`Section for ${r.ledgerName}`}
          className={TABLE_INPUT_CLASS}
          disabled={!canEdit || r.role !== 'TDS_PAYABLE'}
          value={r.role === 'TDS_PAYABLE' ? (r.section ?? '') : ''}
          onChange={(e) =>
            setDraft(r.ledgerName, { role: r.role, section: r.section }, { section: e.target.value || null })
          }
        >
          <option value="">Combined / infer per voucher</option>
          {sectionOptions.map((o) => (
            <option key={o.value} value={o.value}>
              {o.label}
            </option>
          ))}
        </select>
      </td>
      <td className={TABLE_CELL_CLASS}>
        <div className="flex items-center gap-2">
          {r.edited && r.role === null ? (
            <span className="text-[var(--color-danger)]">Choose a role</span>
          ) : r.saved ? (
            <span className="text-[var(--color-text-secondary)]">Saved by {r.savedBy ?? '—'}</span>
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
                    section: suggestedRole === 'TDS_PAYABLE' ? r.section : null,
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
}
