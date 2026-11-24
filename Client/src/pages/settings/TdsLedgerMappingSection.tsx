import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useAuthStore } from '@/store/authStore'
import { useTdsApplicability } from '@/hooks/useTdsApplicability'
import { apiErrorMessage } from '@/lib/apiError'
import { TDS_SECTION_OPTIONS } from '@/lib/tdsSections'
import {
  TDS_LEDGER_MAPPING_QUERY_KEY,
  getTdsLedgerMapping,
  saveTdsLedgerMapping,
  TDS_LEDGER_ROLE_LABELS,
  type TdsLedgerMappingRow,
  type TdsLedgerRole,
} from '@/lib/tdsLedgerMappingApi'

const ROLES = Object.keys(TDS_LEDGER_ROLE_LABELS) as TdsLedgerRole[]
const SECTION_OPTIONS = TDS_SECTION_OPTIONS.filter((o) => o.value !== 'NOT_SUBJECT')

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
  // so only a role is required; hidden payable rows still need one for the save.
  const everyRowHasRole = rows.every((r) => r.role !== null)
  const canSave = canEdit && everyRowHasRole

  const mutation = useMutation({
    mutationFn: () =>
      saveTdsLedgerMapping(
        rows.map((r) => ({
          ledgerName: r.ledgerName,
          role: r.role as TdsLedgerRole,
          section: r.role === 'TDS_PAYABLE' ? r.section : null,
        })),
      ),
    onSuccess: () => {
      setDrafts({})
      queryClient.invalidateQueries({ queryKey: TDS_LEDGER_MAPPING_QUERY_KEY })
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
        <h2 className="text-lg font-semibold mb-2">TDS ledgers</h2>
        <p role="alert" className="text-sm text-red-600">
          {apiErrorMessage(error, 'Could not load TDS-ledger mapping')}
        </p>
      </section>
    )

  return (
    <section>
      <div className="flex items-center gap-3 mb-2">
        <h2 className="text-lg font-semibold">TDS ledgers</h2>
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
      {anyTdsOn && blockers.length > 0 && (
        <ul className="text-sm text-red-600 mb-2 list-disc list-inside">
          {blockers.map((b) => (
            <li key={b}>{b}</li>
          ))}
        </ul>
      )}
      {anyTdsOn && (
        <div className="mb-6">
          <h3 className="text-sm font-semibold mb-1">TDS payable ledgers</h3>
          <table className="w-full text-sm">
            <thead>
              <tr className="text-left border-b">
                <th className="py-2">Ledger</th>
                <th className="py-2">Group</th>
                <th className="py-2">Role</th>
                <th className="py-2">Section</th>
                <th className="py-2">Status</th>
              </tr>
            </thead>
            <tbody>
              {payableRows.map((r) => (
                <LedgerRow key={r.ledgerName} r={r} canEdit={canEdit} setDraft={setDraft} />
              ))}
            </tbody>
          </table>
        </div>
      )}
      <div>
        <h3 className="text-sm font-semibold mb-1">TDS receivable ledgers</h3>
        {receivableWarnings.length > 0 && (
          <ul className="text-sm text-amber-600 mb-2 list-disc list-inside">
            {receivableWarnings.map((w) => (
              <li key={w}>{w}</li>
            ))}
          </ul>
        )}
        <table className="w-full text-sm">
          <thead>
            <tr className="text-left border-b">
              <th className="py-2">Ledger</th>
              <th className="py-2">Group</th>
              <th className="py-2">Role</th>
              <th className="py-2">Section</th>
              <th className="py-2">Status</th>
            </tr>
          </thead>
          <tbody>
            {receivableRows.map((r) => (
              <LedgerRow key={r.ledgerName} r={r} canEdit={canEdit} setDraft={setDraft} />
            ))}
          </tbody>
        </table>
      </div>
      {mutation.isError && (
        <p role="alert" className="text-sm text-red-600 mt-2">
          {apiErrorMessage(mutation.error, 'Could not save the TDS-ledger mapping')}
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

function LedgerRow({
  r,
  canEdit,
  setDraft,
}: {
  r: Row
  canEdit: boolean
  setDraft: (ledgerName: string, base: Draft, patch: Partial<Draft>) => void
}) {
  return (
    <tr className="border-b">
      <td className="py-2">{r.ledgerName}</td>
      <td className="py-2">{r.parentGroup ?? '—'}</td>
      <td className="py-2">
        <select
          aria-label={`Role for ${r.ledgerName}`}
          className="border rounded px-2 py-1 text-sm"
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
      <td className="py-2">
        <select
          aria-label={`Section for ${r.ledgerName}`}
          className="border rounded px-2 py-1 text-sm"
          disabled={!canEdit || r.role !== 'TDS_PAYABLE'}
          value={r.role === 'TDS_PAYABLE' ? (r.section ?? '') : ''}
          onChange={(e) =>
            setDraft(r.ledgerName, { role: r.role, section: r.section }, { section: e.target.value || null })
          }
        >
          <option value="">Combined / infer per voucher</option>
          {SECTION_OPTIONS.map((o) => (
            <option key={o.value} value={o.value}>
              {o.label}
            </option>
          ))}
        </select>
      </td>
      <td className="py-2">
        {r.saved ? <>Saved by {r.savedBy ?? '—'}</> : r.edited ? 'Not saved' : 'Suggested, not saved'}
      </td>
    </tr>
  )
}
