import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { AlertCircle, Loader2 } from 'lucide-react'
import { useAuthStore } from '@/store/authStore'
import { apiErrorMessage } from '@/lib/apiError'
import { getOrganization } from '@/lib/tdsApi'
import { useTdsApplicability } from '@/hooks/useTdsApplicability'
import {
  getResponsibilityStarts,
  putResponsibilityStart,
  type ResponsibilityStart,
} from '@/lib/complianceApi'
import { WORKFLOW_TYPE_LABEL, WORKFLOW_TYPES, type WorkflowType } from '@/lib/workflowApi'
import {
  ALERT_ERROR_CLASS,
  LOADING_CLASS,
  PRIMARY_BUTTON_CLASS,
  PILL_WARNING_CLASS,
  SECTION_CARD_CLASS,
  TABLE_CELL_CLASS,
  TABLE_HEAD_CLASS,
  TABLE_INPUT_CLASS,
  TABLE_ROW_CLASS,
} from './sectionStyles'

const ORDER: WorkflowType[] = [...WORKFLOW_TYPES]

const TDS_TYPES: WorkflowType[] = ['TDS_PAYMENT', 'TDS_26Q']

/** FY of a "yyyy-mm-dd" value; FY 2026-27 starts in month 4 → 2026-05 is FY 2026. */
function fyOfDate(date: string, fyStartMonth: number): number {
  const [year, m] = date.split('-').map(Number)
  return m >= fyStartMonth ? year : year - 1
}

export function ResponsibilityStartSection() {
  const { user } = useAuthStore()
  const orgId = user?.organizationId
  const queryClient = useQueryClient()
  const canEdit = user?.role === 'ROLE_ACCOUNTANT'
  const [error, setError] = useState<string | null>(null)

  const tdsApplicability = useTdsApplicability(orgId)
  const anyTdsOn = tdsApplicability.anyTdsOn
  const isTdsOnFor = tdsApplicability.isTdsOnFor
  const { data: org } = useQuery({
    queryKey: ['organization', orgId],
    queryFn: () => getOrganization(orgId as string),
    enabled: !!orgId,
  })
  const fyStartMonth = (org?.financialYearStart as number | undefined) ?? 4

  const { data: rows, isLoading } = useQuery({
    queryKey: ['responsibility-starts'],
    queryFn: getResponsibilityStarts,
  })

  const mutation = useMutation({
    mutationFn: ({ type, date }: { type: WorkflowType; date: string }) =>
      putResponsibilityStart(type, date),
    onSuccess: () => {
      setError(null)
      queryClient.invalidateQueries({ queryKey: ['responsibility-starts'] })
    },
    onError: (err) => setError(apiErrorMessage(err, 'Could not save responsibility start')),
  })

  const byType = new Map<WorkflowType, ResponsibilityStart>(
    (rows ?? []).map((r) => [r.type, r]),
  )

  if (isLoading) {
    return (
      <div className={LOADING_CLASS}>
        <Loader2 size={16} className="animate-spin" />
        Loading…
      </div>
    )
  }

  return (
    <section className={SECTION_CARD_CLASS}>
      <h2 className="text-lg font-semibold mb-2">Responsibility start</h2>
      <p className="text-sm text-[var(--color-text-muted)] mb-3">
        First period your firm prepares for each return. Earlier periods in open years become
        baseline.
      </p>
      {error && (
        <div role="alert" className={`${ALERT_ERROR_CLASS} mb-3`}>
          <AlertCircle size={16} className="flex-shrink-0" />
          {error}
        </div>
      )}
      <table className="w-full text-sm">
        <thead>
          <tr className="text-left">
            <th className={TABLE_HEAD_CLASS}>Return</th>
            <th className={TABLE_HEAD_CLASS}>First period</th>
            <th className={TABLE_HEAD_CLASS}>Baseline</th>
            <th className={TABLE_HEAD_CLASS}></th>
          </tr>
        </thead>
        <tbody>
          {ORDER.filter((type) => !TDS_TYPES.includes(type) || anyTdsOn).map((type) => {
            const row = byType.get(type)
            const date = row?.firstPeriodStart ?? ''
            const isTds = TDS_TYPES.includes(type)
            const fy = isTds && date ? fyOfDate(date, fyStartMonth) : null
            const notApplicable = fy !== null && !isTdsOnFor(fy)
            return (
              <tr key={type} className={TABLE_ROW_CLASS}>
                <td className={TABLE_CELL_CLASS}>{WORKFLOW_TYPE_LABEL[type]}</td>
                <td className={TABLE_CELL_CLASS}>
                  {row?.firstPeriodStart ? (
                    <span data-testid={`resp-${type}`}>{row.firstPeriodStart}</span>
                  ) : (
                    <span className="text-[var(--color-text-muted)]">Not set</span>
                  )}
                </td>
                <td className={TABLE_CELL_CLASS}>
                  {row && row.baselineRowCount > 0 && (
                    <span>
                      {row.baselineRowCount} baseline periods
                    </span>
                  )}
                  {row?.locked && (
                    <span className={`ml-2 ${PILL_WARNING_CLASS}`}>
                      Locked
                    </span>
                  )}
                </td>
                <td className={TABLE_CELL_CLASS}>
                  {canEdit && (
                    <RespEditControl
                      type={type}
                      initialDate={date}
                      disabled={(row?.locked ?? false) || notApplicable}
                      title={notApplicable ? 'Not a TDS deductor this year' : undefined}
                      onSave={(d) => mutation.mutate({ type, date: d })}
                    />
                  )}
                </td>
              </tr>
            )
          })}
        </tbody>
      </table>
    </section>
  )
}

function RespEditControl({
  type,
  initialDate,
  disabled,
  title,
  onSave,
}: {
  type: WorkflowType
  initialDate: string
  disabled: boolean
  title?: string
  onSave: (date: string) => void
}) {
  const [date, setDate] = useState(initialDate)
  return (
    <span className="flex items-center gap-2 justify-end">
      <input
        type="date"
        aria-label={`${WORKFLOW_TYPE_LABEL[type]} responsibility start date`}
        value={date}
        disabled={disabled}
        title={title}
        onChange={(e) => setDate(e.target.value)}
        className={TABLE_INPUT_CLASS}
      />
      <button
        type="button"
        disabled={disabled || !date || date === initialDate}
        onClick={() => onSave(date)}
        className={PRIMARY_BUTTON_CLASS}
      >
        Save
      </button>
    </span>
  )
}
