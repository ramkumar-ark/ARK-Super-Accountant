import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useMemo, useState } from 'react'
import { api } from '@/lib/api'
import { apiErrorMessage } from '@/lib/apiError'
import type { ReactNode } from 'react'
import { FindingReviewPanel, type ReviewFinding, type ReviewRole } from '@/components/review/FindingReviewPanel'
import { valueInputFor } from '@/components/workflow/findingValueInputs'

interface WorkflowFinding extends ReviewFinding {
  source: string
  findingType: string
  severity: string
  blocking: boolean
  message: string
  expectedValue: string | null
  actualValue: string | null
  voucherNumber: string | null
  voucherDate: string | null
  ledgerName: string | null
  carriedFromFindingId: string | null
}

// Mirrors MastersPage: rules whose corrected value is typed in by the user in Masters.
const TYPED_INPUT_RULES = ['GSTIN_PRESENCE', 'HSN_SAC_CODE', 'TDS_SECTION_MAPPING', 'GST_APPLICABILITY']

function needsTypedValue(f: WorkflowFinding): boolean {
  if (f.source === 'TDS') return f.findingType === 'PAN_MISSING' || f.findingType === 'PAN_INVALID'
  if (f.source === 'MASTERS') return TYPED_INPUT_RULES.includes(f.findingType)
  return false
}

/**
 * The findings tab of a workflow: every module's findings for the workflow, blocking first,
 * with the shared review panel per finding. Rendered inside the workflow screens worker F owns.
 */
export function WorkflowFindingsTab({
  workflowId,
  role,
}: {
  workflowId: string
  role: ReviewRole
}): ReactNode {
  const queryClient = useQueryClient()
  const findingsQuery = useQuery({
    queryKey: ['workflow-findings', workflowId],
    queryFn: async () => {
      const res = await api.get(`/v1/workflows/${workflowId}/findings`)
      return res.data as { findings: WorkflowFinding[]; blockingCount: number }
    },
  })

  async function act(finding: WorkflowFinding, action: 'ACKNOWLEDGE' | 'APPROVE' | 'REJECT', body: {
    value?: string
    note?: string
  }) {
    try {
      await api.post(`/v1/workflows/${workflowId}/findings/${finding.id}/action`, { action, ...body })
      await queryClient.invalidateQueries({ queryKey: ['workflow-findings', workflowId] })
    } catch (err) {
      throw new Error(apiErrorMessage(err, 'Could not apply this action. Try again.'))
    }
  }

  const findings = findingsQuery.data?.findings ?? []
  const [typeFilter, setTypeFilter] = useState<string[]>([])
  const [severityFilter, setSeverityFilter] = useState<string[]>([])
  const [statusFilter, setStatusFilter] = useState<string[]>([])
  const [selected, setSelected] = useState<string[]>([])
  const [bulkNote, setBulkNote] = useState('')
  const [bulkError, setBulkError] = useState('')
  const [bulkBusy, setBulkBusy] = useState(false)

  const canBulk = role === 'ROLE_OPERATOR' || role === 'ROLE_ACCOUNTANT'
  const isAccountantUser = role === 'ROLE_ACCOUNTANT'
  const selectable = (f: WorkflowFinding) =>
    canBulk &&
    !needsTypedValue(f) &&
    (f.resolveStatus === 'OPEN' || (isAccountantUser && f.resolveStatus === 'ACKNOWLEDGED'))

  const filtered = useMemo(
    () =>
      findings.filter(
        (f) =>
          (typeFilter.length === 0 || typeFilter.includes(f.findingType)) &&
          (severityFilter.length === 0 || severityFilter.includes(f.severity)) &&
          (statusFilter.length === 0 || statusFilter.includes(f.resolveStatus)),
      ),
    [findings, typeFilter, severityFilter, statusFilter],
  )

  const selectedFindings = filtered.filter((f) => selectable(f) && selected.includes(f.id))
  const selectedIds = selectedFindings.map((f) => f.id)
  const allOpen = selectedFindings.length > 0 && selectedFindings.every((f) => f.resolveStatus === 'OPEN')
  const allAcknowledged =
    selectedFindings.length > 0 && selectedFindings.every((f) => f.resolveStatus === 'ACKNOWLEDGED')

  const groups = useMemo(() => {
    const byType = new Map<string, WorkflowFinding[]>()
    for (const finding of filtered) {
      const group = byType.get(finding.findingType)
      if (group) group.push(finding)
      else byType.set(finding.findingType, [finding])
    }
    const blockingCount = (items: WorkflowFinding[]) => items.filter((f) => f.blocking).length
    return [...byType.entries()].sort(
      ([typeA, a], [typeB, b]) =>
        blockingCount(b) - blockingCount(a) || typeA.localeCompare(typeB),
    )
  }, [filtered])

  function toggleSelected(id: string) {
    setSelected((current) =>
      current.includes(id) ? current.filter((v) => v !== id) : [...current, id],
    )
  }

  function toggleGroup(items: WorkflowFinding[]) {
    const ids = items.filter(selectable).map((f) => f.id)
    const allSelected = ids.length > 0 && ids.every((id) => selected.includes(id))
    setSelected((current) =>
      allSelected
        ? current.filter((id) => !ids.includes(id))
        : [...current, ...ids.filter((id) => !current.includes(id))],
    )
  }

  async function bulk(action: 'ACKNOWLEDGE' | 'APPROVE') {
    setBulkBusy(true)
    setBulkError('')
    try {
      await api.post(`/v1/workflows/${workflowId}/findings/bulk-action`, {
        findingIds: selectedIds,
        action,
        ...(bulkNote.trim() !== '' ? { note: bulkNote.trim() } : {}),
      })
      setSelected([])
      setBulkNote('')
      await queryClient.invalidateQueries({ queryKey: ['workflow-findings', workflowId] })
    } catch (err) {
      setBulkError(apiErrorMessage(err, 'Could not apply the bulk action. Try again.'))
    } finally {
      setBulkBusy(false)
    }
  }

  function toggleFilter(
    current: string[],
    setCurrent: (next: string[]) => void,
    value: string,
  ) {
    setCurrent(
      current.includes(value) ? current.filter((v) => v !== value) : [...current, value],
    )
  }

  return (
    <div>
      <div className="flex items-center gap-2 mb-3">
        <h3 className="text-sm font-semibold text-[var(--color-text-primary)]">Findings</h3>
        {findingsQuery.data != null && (
          <span className="text-xs text-[var(--color-text-muted)]" role="status">
            {findingsQuery.data.blockingCount} blocking finding
            {findingsQuery.data.blockingCount === 1 ? '' : 's'}
          </span>
        )}
      </div>
      {findingsQuery.isLoading && (
        <p className="text-sm text-[var(--color-text-secondary)]" role="status">
          Loading findings…
        </p>
      )}
      {findingsQuery.isError && (
        <p className="text-sm text-[var(--color-danger)]" role="alert">
          Could not load findings. Try again.
        </p>
      )}
      {!findingsQuery.isLoading && !findingsQuery.isError && findings.length === 0 && (
        <p className="text-sm text-[var(--color-text-muted)]">No findings</p>
      )}
      {findings.length > 0 && (
        <div className="flex flex-col gap-2 mb-4">
          <FilterGroup
            title="Type"
            values={countValues(findings, 'findingType')}
            selected={typeFilter}
            onToggle={(value) => toggleFilter(typeFilter, setTypeFilter, value)}
          />
          <FilterGroup
            title="Severity"
            values={countValues(findings, 'severity')}
            selected={severityFilter}
            onToggle={(value) => toggleFilter(severityFilter, setSeverityFilter, value)}
          />
          <FilterGroup
            title="Status"
            values={countValues(findings, 'resolveStatus')}
            selected={statusFilter}
            onToggle={(value) => toggleFilter(statusFilter, setStatusFilter, value)}
          />
        </div>
      )}
      {findings.length > 0 && filtered.length === 0 && (
        <p className="text-sm text-[var(--color-text-muted)]">No findings match the filters.</p>
      )}
      <div className="flex flex-col gap-4">
        {groups.map(([type, items]) => {
          const selectableItems = items.filter(selectable)
          const allInGroupSelected =
            selectableItems.length > 0 &&
            selectableItems.every((f) => selected.includes(f.id))
          return (
            <section key={type} className="flex flex-col gap-3">
              <div className="flex items-center gap-2">
                <h4 className="text-xs font-semibold uppercase tracking-wide text-[var(--color-text-muted)]">
                  {type.replaceAll('_', ' ')} ({items.length})
                </h4>
                {selectableItems.length > 0 && (
                  <label className="flex items-center gap-1 text-xs text-[var(--color-text-muted)]">
                    <input
                      type="checkbox"
                      aria-label={`Select all in ${type.replaceAll('_', ' ')}`}
                      checked={allInGroupSelected}
                      onChange={() => toggleGroup(items)}
                    />
                    Select all
                  </label>
                )}
              </div>
              {items.map((f) => (
                <FindingCard
                  key={f.id}
                  finding={f}
                  role={role}
                  onAction={act}
                  selectable={selectable(f)}
                  selected={selected.includes(f.id)}
                  onSelectChange={() => toggleSelected(f.id)}
                />
              ))}
            </section>
          )
        })}
      </div>
      {selectedIds.length > 0 && (
        <div className="sticky bottom-0 mt-4 flex flex-wrap items-center gap-2 rounded-[var(--radius-md)] border border-[var(--color-border)] bg-[var(--color-surface)] p-3">
          <span className="text-sm text-[var(--color-text-secondary)]">{selectedIds.length} selected</span>
          <input
            type="text"
            aria-label="Bulk note"
            placeholder="Add a note (optional)…"
            value={bulkNote}
            onChange={(e) => setBulkNote(e.target.value)}
            className="h-9 flex-1 min-w-[12rem] px-3 rounded-[var(--radius-md)] border border-[var(--color-border)] bg-[var(--color-surface)] text-sm text-[var(--color-text-primary)] focus:outline-none focus:border-[var(--color-primary)]"
          />
          {canBulk && (
            <button
              type="button"
              onClick={() => bulk('ACKNOWLEDGE')}
              disabled={!allOpen || bulkBusy}
              className="h-9 px-3 rounded-[var(--radius-md)] border border-[var(--color-border)] text-sm text-[var(--color-text-primary)] hover:bg-[var(--color-surface-raised)] disabled:opacity-50"
            >
              Acknowledge {selectedIds.length}
            </button>
          )}
          {isAccountantUser && (
            <button
              type="button"
              onClick={() => bulk('APPROVE')}
              disabled={!allAcknowledged || bulkBusy}
              className="h-9 px-3 rounded-[var(--radius-md)] bg-[var(--color-primary)] text-white text-sm disabled:opacity-50"
            >
              Approve {selectedIds.length}
            </button>
          )}
          <button
            type="button"
            onClick={() => setSelected([])}
            className="h-9 px-3 rounded-[var(--radius-md)] text-sm text-[var(--color-text-secondary)] hover:bg-[var(--color-surface-raised)]"
          >
            Clear
          </button>
          {bulkError && (
            <p role="alert" className="w-full text-sm text-[var(--color-danger)]">
              {bulkError}
            </p>
          )}
        </div>
      )}
    </div>
  )
}

function countValues(
  findings: WorkflowFinding[],
  key: 'findingType' | 'severity' | 'resolveStatus',
): Array<[string, number]> {
  const counts = new Map<string, number>()
  for (const finding of findings) {
    const value = finding[key]
    if (!value) continue
    counts.set(value, (counts.get(value) ?? 0) + 1)
  }
  return [...counts.entries()].sort(([a], [b]) => a.localeCompare(b))
}

function FilterGroup({
  title,
  values,
  selected,
  onToggle,
}: {
  title: string
  values: Array<[string, number]>
  selected: string[]
  onToggle: (value: string) => void
}): ReactNode {
  if (values.length === 0) return null
  return (
    <div className="flex flex-wrap items-center gap-2">
      <span className="text-xs uppercase text-[var(--color-text-muted)]">{title}</span>
      {values.map(([value, count]) => (
        <button
          key={value}
          type="button"
          aria-pressed={selected.includes(value)}
          onClick={() => onToggle(value)}
          className={`h-7 px-2 rounded-full border text-xs ${
            selected.includes(value)
              ? 'border-[var(--color-primary)] bg-[var(--color-primary)] text-white'
              : 'border-[var(--color-border)] text-[var(--color-text-secondary)] hover:bg-[var(--color-surface-raised)]'
          }`}
        >
          {value} ({count})
        </button>
      ))}
    </div>
  )
}

function FindingCard({
  finding: f,
  role,
  onAction,
  selectable,
  selected,
  onSelectChange,
}: {
  finding: WorkflowFinding
  role: ReviewRole
  onAction: (
    finding: WorkflowFinding,
    action: 'ACKNOWLEDGE' | 'APPROVE' | 'REJECT',
    body: { value?: string; note?: string },
  ) => Promise<void>
  selectable: boolean
  selected: boolean
  onSelectChange: () => void
}): ReactNode {
  return (
    <div
      className={`bg-[var(--color-surface)] rounded-[var(--radius-md)] border border-[var(--color-border)] border-l-4 ${f.blocking ? 'border-l-[var(--color-danger)]' : 'border-l-[var(--color-info)]'} p-4`}
    >
      <div className="flex items-start gap-4">
        {selectable && (
          <input
            type="checkbox"
            aria-label={`Select ${f.id}`}
            checked={selected}
            onChange={onSelectChange}
            className="mt-1"
          />
        )}
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2 mb-1">
            <span className="text-xs px-2 py-0.5 rounded-full uppercase font-normal bg-[var(--color-danger-bg)] text-[var(--color-danger)]">
              {f.severity}
            </span>
            <span className="text-xs text-[var(--color-text-muted)]">{f.source}</span>
            {f.voucherNumber && (
              <span className="text-xs text-[var(--color-text-muted)] font-mono">{f.voucherNumber}</span>
            )}
          </div>
          {f.ledgerName && (
            <p className="text-sm font-semibold text-[var(--color-text-primary)]">{f.ledgerName}</p>
          )}
          <p className="text-sm text-[var(--color-text-secondary)]">{f.message}</p>
          {f.source === 'GST' && (
            <div className="mt-1 text-sm">
              <p className="text-[var(--color-text-secondary)]">
                Fix this in Tally and re-upload the day book, or acknowledge it with a note.
              </p>
              {(f.expectedValue != null || f.actualValue != null) && (
                <p className="text-[var(--color-text-muted)]">
                  Expected {f.expectedValue ?? '—'}, found {f.actualValue ?? '—'}
                </p>
              )}
            </div>
          )}
          {f.source === 'MASTERS' && (
            <p className="mt-1 text-sm">
              <a
                href="/masters"
                aria-label={`Open ${f.ledgerName ?? f.id} in Masters`}
                className="text-[var(--color-primary)] underline"
              >
                Open in Masters
              </a>
              {f.resolveStatus === 'OPEN' && TYPED_INPUT_RULES.includes(f.findingType) && (
                <span className="ml-2 text-[var(--color-text-secondary)]">
                  Enter the corrected value in Masters.
                </span>
              )}
            </p>
          )}
        </div>
        <Panel finding={f} role={role} onAction={onAction} />
      </div>
    </div>
  )
}

function Panel({
  finding,
  role,
  onAction,
}: {
  finding: WorkflowFinding
  role: ReviewRole
  onAction: (
    finding: WorkflowFinding,
    action: 'ACKNOWLEDGE' | 'APPROVE' | 'REJECT',
    body: { value?: string; note?: string },
  ) => Promise<void>
}): ReactNode {
  const isMasters = finding.source === 'MASTERS'
  const isTyped = TYPED_INPUT_RULES.includes(finding.findingType)
  const isMasterTamper =
    finding.source === 'TAMPER' && finding.findingType.startsWith('TAMPER_MASTER_')
  const [validFrom, setValidFrom] = useState('')
  const [typedValue, setTypedValue] = useState('')
  const customInput =
    finding.source !== 'MASTERS' && finding.source !== 'TAMPER'
      ? valueInputFor(finding, { value: typedValue, onChange: setTypedValue })
      : null
  const valueInput = isMasterTamper ? (
    <label className="block text-xs text-[var(--color-text-secondary)] mb-1">
      Valid from (leave blank to accept as a correction)
      <input
        type="date"
        value={validFrom}
        onChange={(e) => setValidFrom(e.target.value)}
        className="mt-1 w-full h-10 px-3 rounded-[var(--radius-md)] border border-[var(--color-border)] bg-[var(--color-surface)] text-sm text-[var(--color-text-primary)] focus:outline-none focus:border-[var(--color-primary)]"
      />
    </label>
  ) : isMasters && !isTyped && finding.resolveStatus === 'OPEN' ? (
    <p className="text-sm text-[var(--color-text-secondary)]">
      {finding.findingType === 'MISSING_IN_CONFIGURATION'
        ? `${finding.ledgerName ?? finding.id} will be added to the approved masters.`
        : `The master will be updated to: ${finding.actualValue ?? '—'}`}
    </p>
  ) : (
    customInput?.node
  )
  return (
    <FindingReviewPanel
      finding={finding}
      role={role}
      label={finding.id}
      valueInput={valueInput}
      submitDisabled={customInput != null && !customInput.valid}
      onAction={(action, body) =>
        onAction(finding, action, {
          ...body,
          ...(customInput != null && action === 'ACKNOWLEDGE' ? { value: customInput.value } : {}),
          ...(isMasterTamper && validFrom !== '' ? { value: `VALID_FROM:${validFrom}` } : {}),
          ...(isMasters && !isTyped && action === 'ACKNOWLEDGE' && finding.actualValue != null
            ? { value: finding.actualValue }
            : {}),
        })
      }
    />
  )
}
