import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiErrorMessage } from '@/lib/apiError'
import {
  canOperate,
  isAccountant,
  STAGE_LABEL,
  type Workflow,
} from '@/lib/workflowApi'
import {
  downloadExport,
  getGate,
  listEntries,
  listQuarterChallans,
  mapEntries,
  type QuarterChallans,
  type TdsDeducteeEntry,
} from '@/lib/tdsApi'

const buttonClass =
  'inline-flex h-9 w-fit items-center justify-center rounded-[var(--radius-md)] bg-[var(--color-primary)] px-3 text-sm font-medium text-white hover:bg-[var(--color-primary-hover)] disabled:cursor-not-allowed disabled:opacity-50'
const selectClass =
  'h-9 rounded-[var(--radius-md)] border border-[var(--color-border)] bg-[var(--color-surface-raised)] px-2 text-sm text-[var(--color-text-primary)]'

function money(value: number | null): string {
  if (value == null) return '—'
  return value.toLocaleString('en-IN')
}

function challanLabel(month: string, serial: string | null): string {
  return `${month} / ${serial ?? '—'}`
}

function filenameFrom(headers: Record<string, unknown> | undefined): string {
  const raw = headers?.['content-disposition']
  if (typeof raw !== 'string') return '26Q.txt'
  const match = /filename="?([^";]+)"?/.exec(raw)
  return match?.[1] ?? '26Q.txt'
}

interface Props {
  workflow: Workflow
  role: string
}

export function QuarterPreparePanel({ workflow, role }: Props) {
  const queryClient = useQueryClient()
  const [selected, setSelected] = useState<string[]>([])
  const [bulkChallan, setBulkChallan] = useState('')
  const [mapError, setMapError] = useState('')
  const [downloadError, setDownloadError] = useState('')

  const entriesQuery = useQuery({
    queryKey: ['tds-entries', workflow.id],
    queryFn: () => listEntries(workflow.id),
  })
  const monthsQuery = useQuery({
    queryKey: ['tds-quarter-challans', workflow.id],
    queryFn: () => listQuarterChallans(workflow.id),
  })
  const gateQuery = useQuery({
    queryKey: ['tds-gate', workflow.id],
    queryFn: () => getGate(workflow.id),
  })

  const canMap = canOperate(role) && workflow.stage === 'PREPARE'

  const refresh = () =>
    Promise.all([
      queryClient.invalidateQueries({ queryKey: ['tds-entries', workflow.id] }),
      queryClient.invalidateQueries({ queryKey: ['tds-gate', workflow.id] }),
    ])

  const mapMutation = useMutation({
    mutationFn: ({ challanId, entryIds }: { challanId: string | null; entryIds: string[] }) =>
      mapEntries(workflow.id, challanId, entryIds),
    onSuccess: () => {
      setSelected([])
      setMapError('')
      return refresh()
    },
    onError: (err) => setMapError(apiErrorMessage(err, 'Could not map the entries.')),
  })

  if (entriesQuery.isLoading || monthsQuery.isLoading || gateQuery.isLoading) {
    return <p className="text-sm text-[var(--color-text-muted)]">Loading the quarter…</p>
  }
  if (entriesQuery.isError || monthsQuery.isError || gateQuery.isError
    || !entriesQuery.data || !monthsQuery.data || !gateQuery.data) {
    return (
      <p role="alert" className="text-sm text-[var(--color-danger)]">
        Could not load the quarter.
      </p>
    )
  }

  const entries = entriesQuery.data
  const months = monthsQuery.data
  const gate = gateQuery.data
  const allPassed = gate.every((condition) => condition.passed)
  const challanOptions = months.flatMap((month) =>
    month.challans.map((challan) => ({
      id: challan.id,
      label: challanLabel(month.periodKey, challan.challanSerialNumber),
    })),
  )
  const challanLabelById = new Map(challanOptions.map((option) => [option.id, option.label]))
  const monthByChallan = new Map<string, string>()
  for (const month of months) {
    for (const challan of month.challans) monthByChallan.set(challan.id, month.periodKey)
  }

  const toggle = (entryId: string) => {
    setSelected((current) =>
      current.includes(entryId)
        ? current.filter((id) => id !== entryId)
        : [...current, entryId],
    )
  }

  async function download() {
    setDownloadError('')
    try {
      const res = await downloadExport(workflow.id)
      const filename = filenameFrom(res.headers as Record<string, unknown> | undefined)
      const url = URL.createObjectURL(res.data as Blob)
      const anchor = document.createElement('a')
      anchor.href = url
      anchor.download = filename
      document.body.appendChild(anchor)
      anchor.click()
      anchor.remove()
      URL.revokeObjectURL(url)
    } catch (err) {
      let message = apiErrorMessage(err, 'Could not download the RPU file.')
      const data = (err as { response?: { data?: unknown } })?.response?.data
      if (data instanceof Blob) {
        try {
          const parsed = JSON.parse(await data.text()) as { error?: string }
          if (parsed.error) message = parsed.error
        } catch {
          // keep the fallback message
        }
      }
      setDownloadError(message)
    }
  }

  const exportEnabled =
    isAccountant(role)
    && (workflow.stage === 'FINALIZED'
      || workflow.stage === 'FILED'
      || (workflow.stage === 'PREPARE' && allPassed))

  return (
    <section className="flex flex-col gap-6 rounded-[var(--radius-lg)] border border-[var(--color-border)] bg-[var(--color-surface)] p-4">
      <header className="flex items-center justify-between">
        <h2 className="text-base font-semibold text-[var(--color-text-primary)]">
          26Q Prepare · {STAGE_LABEL[workflow.stage]}
        </h2>
        {isAccountant(role) && (
          <button
            type="button"
            className={buttonClass}
            disabled={!exportEnabled}
            onClick={download}
          >
            Download RPU file
          </button>
        )}
      </header>

      <div>
        <h3 className="mb-2 text-sm font-semibold text-[var(--color-text-primary)]">
          Gate conditions
        </h3>
        <ul className="flex flex-col gap-1 text-sm">
          {gate.map((condition) => (
            <li key={condition.code} className="text-[var(--color-text-secondary)]">
              <span className={condition.passed ? 'text-[var(--color-success)]' : 'text-[var(--color-danger)]'}>
                {condition.passed ? '✓' : '✗'}
              </span>{' '}
              {condition.label}: {condition.detail}
              {condition.code === 'DEDUCTOR_COMPLETE' && !condition.passed
                && ' Complete the deductor details in the deductor settings'}
            </li>
          ))}
        </ul>
        {downloadError && (
          <p role="alert" className="mt-2 text-sm text-[var(--color-danger)]">
            {downloadError}
          </p>
        )}
      </div>

      <div>
        <h3 className="mb-2 text-sm font-semibold text-[var(--color-text-primary)]">
          Approved challans by payment month
        </h3>
        <div className="flex flex-col gap-2">
          {months.map((month: QuarterChallans) => (
            <div key={month.paymentWorkflowId}>
              <p className="text-sm text-[var(--color-text-primary)]">
                {month.periodKey} · {STAGE_LABEL[month.stage]}
              </p>
              {month.challans.length === 0 ? (
                <p className="text-sm text-[var(--color-text-muted)]">No approved challan</p>
              ) : (
                <ul className="text-sm text-[var(--color-text-secondary)]">
                  {month.challans.map((challan) => (
                    <li key={challan.id}>
                      {challan.bsrCode ?? '—'} / {challan.challanSerialNumber ?? '—'} · TDS{' '}
                      {money(challan.tdsAmount)}
                    </li>
                  ))}
                </ul>
              )}
            </div>
          ))}
        </div>
      </div>

      <div>
        <div className="mb-2 flex items-center justify-between">
          <h3 className="text-sm font-semibold text-[var(--color-text-primary)]">Entries</h3>
          {canMap && (
            <div className="flex items-center gap-2">
              <select
                aria-label="Map selected to"
                className={selectClass}
                value={bulkChallan}
                onChange={(e) => setBulkChallan(e.target.value)}
              >
                <option value="">Unmapped</option>
                {challanOptions.map((option) => (
                  <option key={option.id} value={option.id}>
                    {option.label}
                  </option>
                ))}
              </select>
              <button
                type="button"
                className={buttonClass}
                disabled={selected.length === 0}
                onClick={() =>
                  mapMutation.mutate({ challanId: bulkChallan || null, entryIds: selected })}
              >
                Map selected
              </button>
            </div>
          )}
        </div>
        <table className="w-full text-left text-sm">
          <thead>
            <tr className="border-b border-[var(--color-border)] text-[var(--color-text-muted)]">
              {canMap && <th className="w-8 py-2 pr-2 font-medium"> </th>}
              <th className="py-2 pr-2 font-medium">Deductee</th>
              <th className="py-2 pr-2 font-medium">PAN</th>
              <th className="py-2 pr-2 font-medium">Section</th>
              <th className="py-2 pr-2 font-medium">Paid on</th>
              <th className="py-2 pr-2 font-medium">Amount paid</th>
              <th className="py-2 pr-2 font-medium">TDS deducted</th>
              <th className="py-2 pr-2 font-medium">TDS deposited</th>
              <th className="py-2 font-medium">Challan</th>
            </tr>
          </thead>
          <tbody>
            {entries.length === 0 && (
              <tr>
                <td colSpan={canMap ? 9 : 8} className="py-3 text-[var(--color-text-muted)]">
                  No entries.
                </td>
              </tr>
            )}
            {entries.map((entry: TdsDeducteeEntry) => {
              const mappedMonth = entry.tdsChallanId
                ? monthByChallan.get(entry.tdsChallanId)
                : undefined
              const mappedLabel = entry.tdsChallanId
                ? challanLabelById.get(entry.tdsChallanId)
                  ?? (mappedMonth ? `${mappedMonth} / —` : 'Mapped')
                : 'Unmapped'
              return (
                <tr key={entry.id} className="border-b border-[var(--color-border)]">
                  {canMap && (
                    <td className="py-2 pr-2">
                      <input
                        type="checkbox"
                        aria-label={`Select ${entry.deducteeName ?? 'entry'}`}
                        checked={selected.includes(entry.id)}
                        onChange={() => toggle(entry.id)}
                      />
                    </td>
                  )}
                  <td className="py-2 pr-2">{entry.deducteeName ?? '—'}</td>
                  <td className="py-2 pr-2">
                    {entry.deducteePan ?? '—'}
                    {!entry.panValid && (
                      <span className="ml-2 text-xs text-[var(--color-danger)]">
                        {entry.deducteePan ? 'invalid PAN' : 'missing PAN'}
                      </span>
                    )}
                  </td>
                  <td className="py-2 pr-2">{entry.section ?? '—'}</td>
                  <td className="py-2 pr-2">{entry.dateOfPayment ?? '—'}</td>
                  <td className="py-2 pr-2">{money(entry.amountPaid)}</td>
                  <td className="py-2 pr-2">{money(entry.tdsDeducted)}</td>
                  <td className="py-2 pr-2">{money(entry.tdsDeposited)}</td>
                  <td className="py-2">
                    {canMap ? (
                      <select
                        aria-label={`Challan for ${entry.deducteeName ?? 'entry'}`}
                        className={selectClass}
                        value={entry.tdsChallanId ?? ''}
                        onChange={(e) =>
                          mapMutation.mutate({
                            challanId: e.target.value || null,
                            entryIds: [entry.id],
                          })}
                      >
                        <option value="">Unmapped</option>
                        {challanOptions.map((option) => (
                          <option key={option.id} value={option.id}>
                            {option.label}
                          </option>
                        ))}
                      </select>
                    ) : (
                      <span className="text-[var(--color-text-secondary)]">{mappedLabel}</span>
                    )}
                  </td>
                </tr>
              )
            })}
          </tbody>
        </table>
        {mapError && (
          <p role="alert" className="mt-2 text-sm text-[var(--color-danger)]">
            {mapError}
          </p>
        )}
      </div>
    </section>
  )
}
