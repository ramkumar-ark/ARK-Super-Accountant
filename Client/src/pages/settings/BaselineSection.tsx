import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { AlertCircle, CheckCircle2, Loader2 } from 'lucide-react'
import { useAuthStore } from '@/store/authStore'
import { apiErrorMessage } from '@/lib/apiError'
import { getBaselineStatus, confirmBaseline } from '@/lib/complianceApi'
import { WORKFLOW_TYPE_LABEL, type WorkflowType } from '@/lib/workflowApi'
import {
  ALERT_ERROR_CLASS,
  ALERT_ERROR_LIST_CLASS,
  ALERT_SUCCESS_CLASS,
  LOADING_CLASS,
  PRIMARY_BUTTON_CLASS,
  SECTION_CARD_CLASS,
  TABLE_CELL_CLASS,
  TABLE_HEAD_CLASS,
  TABLE_ROW_CLASS,
} from './sectionStyles'

export function BaselineSection() {
  const { user } = useAuthStore()
  const queryClient = useQueryClient()
  const canEdit = user?.role === 'ROLE_ACCOUNTANT'
  const { data: status, isLoading } = useQuery({
    queryKey: ['baseline-status'],
    queryFn: getBaselineStatus,
  })

  const mutation = useMutation({
    mutationFn: () => confirmBaseline(),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: ['baseline-status'] }),
  })

  if (isLoading) {
    return (
      <div className={LOADING_CLASS}>
        <Loader2 size={16} className="animate-spin" />
        Loading…
      </div>
    )
  }
  if (!status) return null

  return (
    <section className={SECTION_CARD_CLASS}>
      <h2 className="text-lg font-semibold mb-3">Baseline</h2>
      {status.confirmed ? (
        <div className={ALERT_SUCCESS_CLASS}>
          <CheckCircle2 size={16} className="flex-shrink-0" />
          Baseline confirmed
        </div>
      ) : (
        <>
          {status.rangeStart && status.rangeEnd && (
            <p className="text-sm">
              Baseline {status.rangeStart} to {status.rangeEnd}
            </p>
          )}
          <table className="w-full text-sm mt-2">
            <thead>
              <tr className="text-left">
                <th className={TABLE_HEAD_CLASS}>Return</th>
                <th className={TABLE_HEAD_CLASS}>Unconfirmed</th>
                <th className={TABLE_HEAD_CLASS}>Confirmed</th>
                <th className={TABLE_HEAD_CLASS}>Latest period end</th>
              </tr>
            </thead>
            <tbody>
              {(status.types ?? []).map((t) => (
                <tr key={t.type} className={TABLE_ROW_CLASS}>
                  <td className={TABLE_CELL_CLASS}>{WORKFLOW_TYPE_LABEL[t.type as WorkflowType]}</td>
                  <td className={TABLE_CELL_CLASS}>{t.unconfirmedRows}</td>
                  <td className={TABLE_CELL_CLASS}>{t.confirmedRows}</td>
                  <td className={TABLE_CELL_CLASS}>{t.latestEnd ?? '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
          <p className="text-sm mt-3">Imported filed data: {status.importedFiledData}</p>
          <p className="text-sm mt-1" data-testid="discrepancy-count">
            {status.discrepancyCount === null || status.discrepancyCount === undefined
              ? 'Differences to explain: Not available (import filed returns and map tax ledgers)'
              : `Differences to explain (informational): ${status.discrepancyCount}`}
          </p>
          <p className="text-sm mt-2">
            <a
              href="/settings/baseline-periods"
              className="text-[var(--color-primary)] underline"
            >
              Baseline periods
            </a>
          </p>
          {(status.blockers ?? []).length > 0 && (
            <div className={`${ALERT_ERROR_LIST_CLASS} mt-3`}>
              <AlertCircle size={16} className="flex-shrink-0 mt-0.5" />
              <ul className="list-disc list-inside">
                {status.blockers.map((b) => (
                  <li key={b}>{b}</li>
                ))}
              </ul>
            </div>
          )}
          {canEdit && (
            <>
              {mutation.isError && (
                <div role="alert" className={`${ALERT_ERROR_CLASS} mt-3`}>
                  <AlertCircle size={16} className="flex-shrink-0" />
                  {apiErrorMessage(mutation.error, 'Could not confirm baseline')}
                </div>
              )}
              <button
                type="button"
                className={`mt-4 ${PRIMARY_BUTTON_CLASS}`}
                disabled={mutation.isPending || (status.blockers ?? []).length > 0}
                onClick={() => mutation.mutate()}
              >
                Confirm baseline
              </button>
            </>
          )}
        </>
      )}
    </section>
  )
}
