import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiErrorMessage } from '@/lib/apiError'
import { isAccountant } from '@/lib/workflowApi'
import type { Workflow } from '@/lib/workflowApi'
import {
  getGstr1Tables,
  putGstr1FillIn,
  type Gstr1TableSummary,
} from '@/lib/api'

const buttonClass =
  'inline-flex h-9 w-fit items-center justify-center rounded-[var(--radius-md)] bg-[var(--color-primary)] px-3 text-sm font-medium text-white hover:bg-[var(--color-primary-hover)] disabled:cursor-not-allowed disabled:opacity-50'
const inputClass =
  'h-9 rounded-[var(--radius-md)] border border-[var(--color-border)] bg-[var(--color-surface-raised)] px-2 text-sm text-[var(--color-text-primary)]'
const tabClass =
  'h-9 rounded-[var(--radius-md)] px-3 text-sm font-medium disabled:cursor-not-allowed'

const PREPARE_ONWARD = ['PREPARE', 'FINALIZED', 'FILED']

const ATTRIBUTES = [
  'HSN',
  'UQC',
  'QTY',
  'POS',
  'EXPORT_TYPE',
  'PORT_CODE',
  'SHIPPING_BILL_NO',
  'SHIPPING_BILL_DATE',
  'SEZ_DEEMED',
  'SUPPLY_NATURE',
]

const TOTAL_LABELS: Record<string, string> = {
  taxableValue: 'Gross taxable value',
  igst: 'IGST',
  cgst: 'CGST',
  sgst: 'SGST',
  cess: 'Cess',
}

function money(value: number | null | undefined): string {
  if (value == null) return '—'
  return value.toLocaleString('en-IN')
}

function totalsOf(table: Gstr1TableSummary | undefined): Array<[string, number]> {
  if (!table) return []
  return Object.entries(table.totals)
}

interface Props {
  workflow: Workflow
  role: string
}

export function Gstr1PreparePanel({ workflow, role }: Props) {
  const queryClient = useQueryClient()
  const [activeTable, setActiveTable] = useState<string | null>(null)
  const [scope, setScope] = useState('LEDGER')
  const [key, setKey] = useState('')
  const [attribute, setAttribute] = useState('HSN')
  const [value, setValue] = useState('')
  const [saveError, setSaveError] = useState('')

  const tablesQuery = useQuery({
    queryKey: ['gstr1-tables', workflow.id],
    queryFn: () => getGstr1Tables(workflow.id),
  })

  const editable = isAccountant(role) && workflow.stage === 'PREPARE'

  const saveMutation = useMutation({
    mutationFn: () =>
      putGstr1FillIn(workflow.id, { scope, key, attribute, value }),
    onSuccess: () => {
      setSaveError('')
      setKey('')
      setValue('')
      return queryClient.invalidateQueries({ queryKey: ['gstr1-tables', workflow.id] })
    },
    onError: (err) => setSaveError(apiErrorMessage(err, 'Could not save the fill-in.')),
  })

  if (!PREPARE_ONWARD.includes(workflow.stage)) return null

  if (tablesQuery.isLoading) {
    return (
      <section className="rounded-[var(--radius-lg)] border border-[var(--color-border)] bg-[var(--color-surface)] p-4">
        <p className="text-sm text-[var(--color-text-muted)]">Loading the prepared return…</p>
      </section>
    )
  }
  if (tablesQuery.isError || !tablesQuery.data) {
    return (
      <section className="rounded-[var(--radius-lg)] border border-[var(--color-border)] bg-[var(--color-surface)] p-4">
        <p role="alert" className="text-sm text-[var(--color-danger)]">
          Could not load the prepared return.
        </p>
      </section>
    )
  }

  const tables = tablesQuery.data.tables
  const selected =
    tables.find((table) => table.tableCode === activeTable) ?? tables[0] ?? null
  const fillIns = tablesQuery.data.fillIns
  const fixed = fillIns.filter((fillIn) => fillIn.fixedInPrepare)

  return (
    <section className="flex flex-col gap-6 rounded-[var(--radius-lg)] border border-[var(--color-border)] bg-[var(--color-surface)] p-4">
      <header>
        <h2 className="text-base font-semibold text-[var(--color-text-primary)]">
          GSTR-1 Prepare
        </h2>
        {tablesQuery.data.frozenAt != null && (
          <p className="mt-1 text-sm text-[var(--color-text-muted)]">
            Frozen at Finalize — tables are read-only.
          </p>
        )}
      </header>

      <div className="flex min-w-0 flex-wrap gap-2">
        {tables.map((table) => (
          <button
            key={table.tableCode}
            type="button"
            role="tab"
            aria-selected={selected?.tableCode === table.tableCode}
            onClick={() => setActiveTable(table.tableCode)}
            className={tabClass}
            style={
              selected?.tableCode === table.tableCode
                ? { background: 'var(--color-primary)', color: 'white' }
                : { background: 'var(--color-surface-raised)', color: 'var(--color-text-secondary)' }
            }
          >
            {table.tableCode} · {table.rowCount} row{table.rowCount === 1 ? '' : 's'}
          </button>
        ))}
        {tables.length === 0 && (
          <p className="text-sm text-[var(--color-text-muted)]">No tables in the prepared return.</p>
        )}
      </div>

      {selected && (
        <div>
          <h3 className="mb-2 text-sm font-semibold text-[var(--color-text-primary)]">
            {selected.tableCode} — totals
          </h3>
          <dl className="flex flex-wrap gap-x-6 gap-y-1 text-sm">
            {totalsOf(selected).map(([totalKey, totalValue]) => (
              <div key={totalKey}>
                <dt className="text-[var(--color-text-muted)]">{TOTAL_LABELS[totalKey] ?? totalKey}</dt>
                <dd className="text-[var(--color-text-primary)]">{money(totalValue)}</dd>
              </div>
            ))}
          </dl>
        </div>
      )}

      <div>
        <h3 className="mb-2 text-sm font-semibold text-[var(--color-text-primary)]">
          Fixed in Prepare
        </h3>
        {fixed.length === 0 ? (
          <p className="text-sm text-[var(--color-text-muted)]">No fill-ins fixed yet.</p>
        ) : (
          <ul className="flex flex-col gap-1 text-sm">
            {fixed.map((fillIn) => (
              <li key={`${fillIn.scope}/${fillIn.key}/${fillIn.attribute}`}>
                <span className="text-[var(--color-text-secondary)]">
                  {fillIn.scope}: {fillIn.key} · {fillIn.attribute} = {fillIn.value ?? '—'}
                </span>
              </li>
            ))}
          </ul>
        )}
      </div>

      {editable && (
        <div>
          <h3 className="mb-2 text-sm font-semibold text-[var(--color-text-primary)]">
            Add a fill-in
          </h3>
          <div className="flex flex-wrap items-center gap-2">
            <select
              aria-label="Scope"
              className={inputClass}
              value={scope}
              onChange={(e) => setScope(e.target.value)}
            >
              <option value="LEDGER">Ledger</option>
              <option value="VOUCHER">Voucher</option>
            </select>
            <input
              aria-label="Key"
              placeholder="Ledger or voucher key"
              className={inputClass}
              value={key}
              onChange={(e) => setKey(e.target.value)}
            />
            <select
              aria-label="Attribute"
              className={inputClass}
              value={attribute}
              onChange={(e) => setAttribute(e.target.value)}
            >
              {ATTRIBUTES.map((name) => (
                <option key={name} value={name}>
                  {name}
                </option>
              ))}
            </select>
            <input
              aria-label="Value"
              placeholder="Value"
              className={inputClass}
              value={value}
              onChange={(e) => setValue(e.target.value)}
            />
            <button
              type="button"
              className={buttonClass}
              disabled={key.trim() === '' || value.trim() === '' || saveMutation.isPending}
              onClick={() => saveMutation.mutate()}
            >
              Save fill-in
            </button>
          </div>
          {saveError && (
            <p role="alert" className="mt-2 text-sm text-[var(--color-danger)]">
              {saveError}
            </p>
          )}
        </div>
      )}
    </section>
  )
}
