import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useAuthStore } from '@/store/authStore'
import { apiErrorMessage } from '@/lib/apiError'
import { getBaselineStatus, confirmBaseline } from '@/lib/complianceApi'
import { WORKFLOW_TYPE_LABEL, type WorkflowType } from '@/lib/workflowApi'

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

  if (isLoading) return <p className="text-sm text-[var(--color-text-muted)]">Loading…</p>
  if (!status) return null

  return (
    <section>
      <h2 className="text-lg font-semibold mb-2">Baseline</h2>
      {status.confirmed ? (
        <p className="text-sm">Baseline confirmed</p>
      ) : (
        <>
          {status.rangeStart && status.rangeEnd && (
            <p className="text-sm">
              Baseline {status.rangeStart} to {status.rangeEnd}
            </p>
          )}
          <table className="w-full text-sm mt-2">
            <thead>
              <tr className="text-left border-b">
                <th className="py-2">Return</th>
                <th className="py-2">Unconfirmed</th>
                <th className="py-2">Confirmed</th>
                <th className="py-2">Latest period end</th>
              </tr>
            </thead>
            <tbody>
              {(status.types ?? []).map((t) => (
                <tr key={t.type} className="border-b">
                  <td className="py-2">{WORKFLOW_TYPE_LABEL[t.type as WorkflowType]}</td>
                  <td className="py-2">{t.unconfirmedRows}</td>
                  <td className="py-2">{t.confirmedRows}</td>
                  <td className="py-2">{t.latestEnd ?? '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
          <p className="text-sm mt-2">Imported filed data: {status.importedFiledData}</p>
          <p className="text-sm mt-1" data-testid="discrepancy-count">
            {status.discrepancyCount === null || status.discrepancyCount === undefined
              ? 'Differences to explain: Not available (import filed returns and map tax ledgers)'
              : `Differences to explain (informational): ${status.discrepancyCount}`}
          </p>
          <p className="text-sm mt-1">
            <a href="/settings/baseline-periods" className="underline">
              Baseline periods
            </a>
          </p>
          {(status.blockers ?? []).length > 0 && (
            <ul className="text-sm text-red-600 mt-2 list-disc list-inside">
              {status.blockers.map((b) => (
                <li key={b}>{b}</li>
              ))}
            </ul>
          )}
          {canEdit && (
            <>
              {mutation.isError && (
                <p role="alert" className="text-sm text-red-600 mt-2">
                  {apiErrorMessage(mutation.error, 'Could not confirm baseline')}
                </p>
              )}
              <button
                type="button"
                className="mt-3 px-3 py-1.5 text-sm rounded bg-[var(--color-primary)] text-white disabled:opacity-50"
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
