import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { AlertCircle, CheckCircle2, Loader2 } from 'lucide-react'
import { useAuthStore } from '@/store/authStore'
import { apiErrorMessage } from '@/lib/apiError'
import { WORKFLOW_TYPE_LABEL } from '@/lib/workflowApi'
import {
  getFinancialYears,
  closeFinancialYear,
  type CloseFinancialYearInput,
  type FinancialYearStatus,
  type CloseFinancialYearResult,
} from '@/lib/complianceApi'
import {
  ALERT_ERROR_CLASS,
  INPUT_CLASS,
  LOADING_CLASS,
  NESTED_CARD_CLASS,
  PILL_WARNING_CLASS,
  PRIMARY_BUTTON_CLASS,
  SECTION_CARD_CLASS,
} from './sectionStyles'

const OPEN_ITEM_PATTERN = /^(\S+)\s+([^:]+):\s+(.+)$/
const WORKFLOW_TYPE_LABELS: Record<string, string> = WORKFLOW_TYPE_LABEL

function openItemsByType(openItems: string[]) {
  const groups = new Map<string, { label: string; open: number; started: number }>()
  for (const item of openItems) {
    const match = OPEN_ITEM_PATTERN.exec(item)
    const type = match?.[1] ?? 'Other'
    const stage = match?.[3] ?? ''
    const group = groups.get(type) ?? {
      label: WORKFLOW_TYPE_LABELS[type] ?? type,
      open: 0,
      started: 0,
    }
    group.open += 1
    if (stage !== 'not started') group.started += 1
    groups.set(type, group)
  }
  return [...groups.values()]
}

export function FinancialYearsSection() {
  const { user } = useAuthStore()
  const queryClient = useQueryClient()
  const canEdit = user?.role === 'ROLE_ACCOUNTANT'
  const { data: years, isLoading } = useQuery({
    queryKey: ['financial-years'],
    queryFn: getFinancialYears,
  })

  const mutation = useMutation({
    mutationFn: ({ fy, body }: { fy: number; body: CloseFinancialYearInput }) =>
      closeFinancialYear(fy, body),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['financial-years'] }),
  })

  if (isLoading) {
    return (
      <div className={LOADING_CLASS}>
        <Loader2 size={16} className="animate-spin" />
        Loading…
      </div>
    )
  }

  const openCount = (years ?? []).filter((y) => !y.closed).length

  return (
    <section className={SECTION_CARD_CLASS}>
      <div className="flex items-center justify-between gap-2 mb-3">
        <h2 className="text-lg font-semibold">Financial years</h2>
        {openCount > 0 && <span className={PILL_WARNING_CLASS}>{openCount} open</span>}
      </div>
      {mutation.isError && (
        <div role="alert" className={`${ALERT_ERROR_CLASS} mb-3`}>
          <AlertCircle size={16} className="flex-shrink-0" />
          {apiErrorMessage(mutation.error, 'Could not close financial year')}
        </div>
      )}
      {(years ?? []).length === 0 && (
        <p className="text-sm text-[var(--color-text-muted)]">No financial years yet.</p>
      )}
      <div className="space-y-4">
        {(years ?? []).map((y) => (
          <YearCard
            key={y.financialYear}
            year={y}
            canEdit={canEdit}
            onSubmit={(body) => mutation.mutate({ fy: y.financialYear, body })}
            pending={mutation.isPending && mutation.variables?.fy === y.financialYear}
            result={
              mutation.variables?.fy === y.financialYear ? mutation.data ?? null : null
            }
          />
        ))}
      </div>
    </section>
  )
}

function YearCard({
  year,
  canEdit,
  onSubmit,
  pending,
  result,
}: {
  year: FinancialYearStatus
  canEdit: boolean
  onSubmit: (body: CloseFinancialYearInput) => void
  pending: boolean
  result: CloseFinancialYearResult | null
}) {
  const [closeType, setCloseType] = useState<'ITR_FILED' | 'HANDLED_ELSEWHERE'>(
    'HANDLED_ELSEWHERE',
  )
  const [itrAckNo, setItrAckNo] = useState('')
  const [itrAckDate, setItrAckDate] = useState('')
  const [note, setNote] = useState('')
  const [showPeriods, setShowPeriods] = useState(false)

  if (year.closed) {
    return (
      <div className="flex items-center gap-2 text-sm text-[var(--color-success)]">
        <CheckCircle2 size={16} className="flex-shrink-0" />
        <span>
          FY {year.financialYear} — closed (
          {year.closeType === 'ITR_FILED' ? 'ITR filed' : 'Handled elsewhere'})
        </span>
      </div>
    )
  }

  const itrDisabled = !year.closable || year.openItems.length > 0 || year.blockers.length > 0
  const submitDisabled =
    !canEdit ||
    pending ||
    (closeType === 'ITR_FILED' ? !itrAckNo.trim() || !itrAckDate : !note.trim())
  const summary = openItemsByType(year.openItems)

  return (
    <div className={`${NESTED_CARD_CLASS} p-4 text-sm space-y-3`}>
      <div className="font-medium">FY {year.financialYear} — open</div>
      {summary.length > 0 ? (
        <div className="space-y-1">
          {summary.map((s) => (
            <div key={s.label} className="flex items-baseline justify-between gap-2">
              <span className="font-medium text-[var(--color-text-primary)]">{s.label}</span>
              <span className="text-[var(--color-text-secondary)]">
                {s.open} open {s.open === 1 ? 'period' : 'periods'}
                {s.started > 0 ? ` · ${s.started} started` : ''}
              </span>
            </div>
          ))}
        </div>
      ) : (
        <p className="text-[var(--color-text-muted)]">No open periods</p>
      )}
      {year.openItems.length > 0 && (
        <details open={showPeriods}>
          <summary
            className="cursor-pointer select-none text-[var(--color-text-secondary)] hover:text-[var(--color-text-primary)]"
            onClick={(e) => {
              e.preventDefault()
              setShowPeriods(!showPeriods)
            }}
          >
            Show open periods ({year.openItems.length})
          </summary>
          {showPeriods && (
            <ul className="text-[var(--color-text-muted)] list-disc list-inside mt-1">
              {year.openItems.map((o) => (
                <li key={o}>{o}</li>
              ))}
            </ul>
          )}
        </details>
      )}
      {year.blockers.length > 0 && (
        <ul className="text-[var(--color-danger)] list-disc list-inside">
          {year.blockers.map((b) => (
            <li key={b}>{b}</li>
          ))}
        </ul>
      )}
      {result && result.warnings.length > 0 && (
        <ul className="text-[var(--color-warning)] list-disc list-inside" aria-label="Warnings">
          {result.warnings.map((w) => (
            <li key={w}>{w}</li>
          ))}
        </ul>
      )}
      {canEdit && (
        <form
          aria-label={`Close FY ${year.financialYear}`}
          className="flex flex-wrap items-end gap-3"
          onSubmit={(e) => {
            e.preventDefault()
            if (closeType === 'ITR_FILED') {
              onSubmit({ closeType, itrAckNo, itrAckDate })
            } else {
              onSubmit({ closeType, note })
            }
          }}
        >
          <label className="flex flex-col gap-1">
            Close type
            <select
              aria-label={`Close type FY ${year.financialYear}`}
              value={closeType}
              onChange={(e) => setCloseType(e.target.value as 'ITR_FILED' | 'HANDLED_ELSEWHERE')}
              className={INPUT_CLASS}
            >
              <option value="HANDLED_ELSEWHERE">Handled elsewhere</option>
              <option value="ITR_FILED" disabled={itrDisabled}>
                ITR filed
              </option>
            </select>
          </label>
          {itrDisabled && (
            <p className="text-xs text-[var(--color-text-muted)] self-center">
              Every period must be filed
            </p>
          )}
          {closeType === 'ITR_FILED' ? (
            <>
              <label className="flex flex-col gap-1">
                Acknowledgement no.
                <input
                  aria-label="Acknowledgement no."
                  value={itrAckNo}
                  onChange={(e) => setItrAckNo(e.target.value)}
                  className={INPUT_CLASS}
                  required
                />
              </label>
              <label className="flex flex-col gap-1">
                Acknowledgement date
                <input
                  type="date"
                  aria-label="Acknowledgement date"
                  value={itrAckDate}
                  onChange={(e) => setItrAckDate(e.target.value)}
                  className={INPUT_CLASS}
                  required
                />
              </label>
            </>
          ) : (
            <label className="flex flex-col gap-1">
              Note
              <input
                aria-label="Note"
                value={note}
                onChange={(e) => setNote(e.target.value)}
                className={INPUT_CLASS}
                required
              />
            </label>
          )}
          <button
            type="submit"
            disabled={submitDisabled}
            className={PRIMARY_BUTTON_CLASS}
          >
            Close year
          </button>
        </form>
      )}
    </div>
  )
}
