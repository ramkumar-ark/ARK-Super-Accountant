import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useMemo, useState } from 'react'
import { apiErrorMessage } from '@/lib/apiError'
import { deleteDebtorTan, listDebtorTans, listMissingDebtorTans, upsertDebtorTan, type DebtorTanGap, type DebtorTanItem } from '@/lib/tdsApi'
import { TAN_PATTERN } from '@/components/workflow/findingValueInputs'
import { useAuthStore } from '@/store/authStore'

function StatusBadge({ configured }: { configured: boolean }) {
  return (
    <span
      className={`inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium ${
        configured
          ? 'bg-[var(--color-success-bg)] text-[var(--color-success)]'
          : 'bg-[var(--color-danger-bg)] text-[var(--color-danger)]'
      }`}
    >
      {configured ? 'Configured' : 'Missing'}
    </span>
  )
}

export function DebtorTanPanel() {
  const queryClient = useQueryClient()
  const role = useAuthStore((st) => st.user?.role)
  const canEdit = role === 'ROLE_OWNER' || role === 'ROLE_ACCOUNTANT'
  const [search, setSearch] = useState('')
  const [editingKey, setEditingKey] = useState<string | null>(null)
  const [draft, setDraft] = useState('')
  const [error, setError] = useState('')
  const [saving, setSaving] = useState(false)

  const { data: items, isError, isLoading } = useQuery({
    queryKey: ['debtor-tans'],
    queryFn: listDebtorTans,
  })

  const { data: gaps } = useQuery({
    queryKey: ['debtor-tans-missing'],
    queryFn: listMissingDebtorTans,
  })

  function gapItem(gap: DebtorTanGap): DebtorTanItem {
    return {
      ledgerName: gap.debtorLedgerName,
      ledgerKey: gap.debtorLedgerName.trim().toLowerCase(),
      tallyGuid: null,
      tan: null,
      sourceFindingId: null,
      updatedBy: null,
      updatedAt: null,
      inLatestUpload: true,
    }
  }

  function startGapEdit(gap: DebtorTanGap) {
    setEditingKey(gap.debtorLedgerName.trim().toLowerCase())
    setDraft('')
    setError('')
  }

  const filtered = useMemo(() => {
    const term = search.trim().toLowerCase()
    if (!term) return items ?? []
    return (items ?? []).filter((item) => item.ledgerName.toLowerCase().includes(term))
  }, [items, search])

  function startEdit(item: DebtorTanItem) {
    setEditingKey(item.ledgerKey)
    setDraft(item.tan ?? '')
    setError('')
  }

  function cancelEdit() {
    setEditingKey(null)
    setDraft('')
    setError('')
  }

  async function save(item: DebtorTanItem) {
    const tan = draft.trim().toUpperCase()
    if (!TAN_PATTERN.test(tan)) {
      setError('Enter a valid 10-character TAN (format AAAA99999A, e.g. BLRA12345C).')
      return
    }
    setSaving(true)
    setError('')
    try {
      await upsertDebtorTan({
        ledgerName: item.ledgerName,
        tan,
        tallyGuid: item.tallyGuid,
      })
      await queryClient.invalidateQueries({ queryKey: ['debtor-tans'] })
      cancelEdit()
    } catch (err) {
      setError(apiErrorMessage(err, 'Could not save this TAN. Try again.'))
    } finally {
      setSaving(false)
    }
  }

  async function clear(item: DebtorTanItem) {
    setSaving(true)
    setError('')
    try {
      await deleteDebtorTan(item.ledgerName)
      await queryClient.invalidateQueries({ queryKey: ['debtor-tans'] })
    } catch (err) {
      setError(apiErrorMessage(err, 'Could not clear this TAN. Try again.'))
    } finally {
      setSaving(false)
    }
  }

  return (
    <div>
      <div className="mb-4">
        <h3 className="text-sm font-semibold text-[var(--color-text-primary)]">Debtor TANs</h3>
        <p className="mt-1 text-sm text-[var(--color-text-secondary)]">
          Customer TANs used to match TDS deducted by your debtors in 26AS.
        </p>
      </div>

      <div className="mb-4">
        <input
          type="search"
          aria-label="Search debtors"
          placeholder="Search debtors..."
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          className="h-10 w-full max-w-sm px-3 rounded-[var(--radius-md)] border border-[var(--color-border)] bg-[var(--color-surface-raised)] text-sm text-[var(--color-text-primary)] focus:outline-none focus:border-[var(--color-primary)]"
        />
      </div>

      {error && (
        <p role="alert" className="mb-3 text-sm text-[var(--color-danger)]">
          {error}
        </p>
      )}
      {isError && (
        <p role="alert" className="text-sm text-[var(--color-danger)]">
          Could not load debtor TANs.
        </p>
      )}
      {isLoading && <p className="text-sm text-[var(--color-text-muted)]">Loading debtor TANs...</p>}
      {!isLoading && !isError && filtered.length === 0 && (
        <p className="text-sm text-[var(--color-text-muted)]">
          {items && items.length > 0
            ? 'No debtors match this search.'
            : 'No debtor ledgers yet. Upload masters to list your customers.'}
        </p>
      )}

      {!isLoading && !isError && gaps && gaps.length > 0 && (
        <div className="mb-6 rounded-[var(--radius-md)] border border-[var(--color-border)] p-3">
          <h4 className="text-sm font-semibold text-[var(--color-text-primary)]">
            TDS deducted with no TAN stored
          </h4>
          <p className="mt-1 text-sm text-[var(--color-text-secondary)]">
            Day-book vouchers where a customer's TDS was debited to a TDS Receivable ledger, but no
            TAN is stored for the customer.
          </p>
          <ul className="mt-2 divide-y divide-[var(--color-border)]">
            {gaps.map((gap) => {
              const editing = editingKey === gap.debtorLedgerName.trim().toLowerCase()
              return (
                <li key={gap.voucherId + '|' + gap.debtorLedgerName} className="flex flex-wrap items-center gap-2 py-2 text-sm">
                  <span className="font-medium text-[var(--color-text-primary)]">
                    {gap.debtorLedgerName}
                  </span>
                  <span className="text-[var(--color-text-secondary)]">
                    ₹{gap.deducted.toFixed(2)} to {gap.receivableLedgerName}
                    {gap.voucherNumber ? ` in voucher ${gap.voucherNumber}` : ''}
                    {gap.voucherDate ? ` dated ${gap.voucherDate}` : ''}
                  </span>
                  {canEdit &&
                    (editing ? (
                      <span className="flex items-center gap-2">
                        <input
                          type="text"
                          aria-label={`TAN for ${gap.debtorLedgerName}`}
                          value={draft}
                          onChange={(e) => setDraft(e.target.value)}
                          className="h-9 w-40 px-2 rounded-[var(--radius-md)] border border-[var(--color-border)] bg-[var(--color-surface)] font-mono text-sm"
                        />
                        <button
                          type="button"
                          disabled={saving}
                          onClick={() => save(gapItem(gap))}
                          className="text-[var(--color-primary)] disabled:opacity-50"
                        >
                          Save
                        </button>
                        <button
                          type="button"
                          onClick={cancelEdit}
                          className="text-[var(--color-text-secondary)]"
                        >
                          Cancel
                        </button>
                      </span>
                    ) : (
                      <button
                        type="button"
                        onClick={() => startGapEdit(gap)}
                        className="text-[var(--color-primary)]"
                      >
                        Add TAN for {gap.debtorLedgerName}
                      </button>
                    ))}
                </li>
              )
            })}
          </ul>
        </div>
      )}

      {filtered.length > 0 && (
        <table className="w-full text-left text-sm">
          <thead>
            <tr className="border-b border-[var(--color-border)] text-[var(--color-text-muted)]">
              <th className="py-2 pr-2 font-medium">Customer</th>
              <th className="py-2 pr-2 font-medium">TAN</th>
              <th className="py-2 pr-2 font-medium">Status</th>
              <th className="py-2 font-medium">Action</th>
            </tr>
          </thead>
          <tbody>
            {filtered.map((item) => {
              const editing = editingKey === item.ledgerKey
              return (
                <tr key={item.ledgerKey} className="border-b border-[var(--color-border)]">
                  <td className="py-2 pr-2">
                    <div className="text-[var(--color-text-primary)]">{item.ledgerName}</div>
                    {!item.inLatestUpload && (
                      <div className="text-xs text-[var(--color-text-muted)]">
                        Not in latest upload
                      </div>
                    )}
                  </td>
                  <td className="py-2 pr-2 font-mono">
                    {editing ? (
                      <input
                        type="text"
                        aria-label={`TAN for ${item.ledgerName}`}
                        value={draft}
                        onChange={(e) => setDraft(e.target.value)}
                        className="h-9 w-40 px-2 rounded-[var(--radius-md)] border border-[var(--color-border)] bg-[var(--color-surface)] font-mono text-sm"
                      />
                    ) : (
                      item.tan ?? '—'
                    )}
                  </td>
                  <td className="py-2 pr-2">
                    <StatusBadge configured={item.tan != null} />
                  </td>
                  <td className="py-2">
                    {canEdit &&
                      (editing ? (
                        <div className="flex gap-2">
                          <button
                            type="button"
                            disabled={saving}
                            onClick={() => save(item)}
                            className="text-sm text-[var(--color-primary)] disabled:opacity-50"
                          >
                            Save
                          </button>
                          <button
                            type="button"
                            onClick={cancelEdit}
                            className="text-sm text-[var(--color-text-secondary)]"
                          >
                            Cancel
                          </button>
                        </div>
                      ) : (
                        <div className="flex gap-2">
                          <button
                            type="button"
                            onClick={() => startEdit(item)}
                            className="text-sm text-[var(--color-primary)]"
                          >
                            {item.tan ? 'Edit' : 'Add TAN'}
                          </button>
                          {item.tan && (
                            <button
                              type="button"
                              disabled={saving}
                              onClick={() => clear(item)}
                              className="text-sm text-[var(--color-danger)] disabled:opacity-50"
                            >
                              Clear
                            </button>
                          )}
                        </div>
                      ))}
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
