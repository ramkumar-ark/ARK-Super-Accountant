import { useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { apiErrorMessage } from '@/lib/apiError'
import type { ReactNode } from 'react'
import { FindingReviewPanel, type ReviewFinding, type ReviewRole } from '@/components/review/FindingReviewPanel'

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
}) {
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
      <div className="flex flex-col gap-3">
        {findings.map((f) => (
          <div
            key={f.id}
            className={`bg-[var(--color-surface)] rounded-[var(--radius-md)] border border-[var(--color-border)] border-l-4 ${f.blocking ? 'border-l-[var(--color-danger)]' : 'border-l-[var(--color-info)]'} p-4`}
          >
            <div className="flex items-start gap-4">
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
              <Panel finding={f} role={role} onAction={act} />
            </div>
          </div>
        ))}
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
  const valueInput =
    isMasters && !isTyped && finding.resolveStatus === 'OPEN' ? (
      <p className="text-sm text-[var(--color-text-secondary)]">
        {finding.findingType === 'MISSING_IN_CONFIGURATION'
          ? `${finding.ledgerName ?? finding.id} will be added to the approved masters.`
          : `The master will be updated to: ${finding.actualValue ?? '—'}`}
      </p>
    ) : undefined
  return (
    <FindingReviewPanel
      finding={finding}
      role={role}
      label={finding.id}
      valueInput={valueInput}
      onAction={(action, body) =>
        onAction(finding, action, {
          ...body,
          ...(isMasters && !isTyped && action === 'ACKNOWLEDGE' && finding.actualValue != null
            ? { value: finding.actualValue }
            : {}),
        })
      }
    />
  )
}
