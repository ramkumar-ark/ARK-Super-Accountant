import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useParams } from '@tanstack/react-router'
import { Loader2 } from 'lucide-react'
import { AppShell } from '@/components/AppShell'
import { WorkflowFindingsTab } from '@/components/workflow/WorkflowFindingsTab'
import { WorkflowStepper } from '@/components/workflow/WorkflowStepper'
import { WorkflowUploadStep } from '@/components/workflow/WorkflowUploadStep'
import { WorkflowEventsTimeline } from '@/components/workflow/WorkflowEventsTimeline'
import type { ReviewRole } from '@/components/review/FindingReviewPanel'
import { useAuthStore } from '@/store/authStore'
import { apiErrorMessage } from '@/lib/apiError'
import {
  WORKFLOW_TYPE_LABEL,
  analyzeWorkflow,
  canOperate,
  getWorkflow,
  getWorkflowEvents,
  invalidateWorkflow,
  listUploads,
  type WorkflowStage,
} from '@/lib/workflowApi'

const UPLOAD_STAGES: WorkflowStage[] = ['STARTED', 'DATA_UPLOADED', 'FINDINGS_REVIEW', 'PREPARE']
const BEFORE_PREPARE: WorkflowStage[] = ['STARTED', 'DATA_UPLOADED', 'FINDINGS_REVIEW']
const ANALYZER_NOTICE_STAGES: WorkflowStage[] = [
  'FINDINGS_REVIEW',
  'PREPARE',
  'FINALIZED',
  'FILED',
]

export function WorkflowDetailPage() {
  const { workflowId } = useParams({ from: '/workflows/$workflowId' })
  const { user } = useAuthStore()
  const role = user?.role
  const queryClient = useQueryClient()
  const [analyzeError, setAnalyzeError] = useState('')

  const detailQuery = useQuery({
    queryKey: ['workflow', workflowId],
    queryFn: () => getWorkflow(workflowId),
  })
  const eventsQuery = useQuery({
    queryKey: ['workflow-events', workflowId],
    queryFn: () => getWorkflowEvents(workflowId),
  })
  const uploadsQuery = useQuery({
    queryKey: ['day-book-uploads'],
    queryFn: listUploads,
  })

  const analyzeMutation = useMutation({
    mutationFn: () => analyzeWorkflow(workflowId),
    onSuccess: () => {
      setAnalyzeError('')
      return invalidateWorkflow(queryClient, workflowId)
    },
    onError: (err) => setAnalyzeError(apiErrorMessage(err, 'Could not start analysis. Try again.')),
  })

  if (detailQuery.isLoading) {
    return (
      <AppShell>
        <main className="flex-1 overflow-y-auto p-6">
          <p className="text-sm text-[var(--color-text-muted)]">Loading workflow…</p>
        </main>
      </AppShell>
    )
  }

  if (detailQuery.isError || !detailQuery.data) {
    return (
      <AppShell>
        <main className="flex-1 overflow-y-auto p-6">
          <p role="alert" className="text-sm text-[var(--color-danger)]">
            Could not load the workflow.
          </p>
        </main>
      </AppShell>
    )
  }

  const detail = detailQuery.data
  const workflow = detail.workflow
  const showUpload = UPLOAD_STAGES.includes(workflow.stage) && canOperate(role)
  const boundButNotAnalyzed = workflow.stage === 'DATA_UPLOADED' && workflow.uploadJobId != null
  const showNoAnalyzerNotice =
    !detail.analysisAvailable && ANALYZER_NOTICE_STAGES.includes(workflow.stage)

  return (
    <AppShell>
      <main className="flex-1 overflow-y-auto p-6">
        <div className="mx-auto flex max-w-4xl flex-col gap-6">
          <header>
            <h1 className="text-xl font-semibold text-[var(--color-text-primary)]">
              {WORKFLOW_TYPE_LABEL[workflow.type]} {workflow.periodKey}
            </h1>
            <p className="mt-1 text-sm text-[var(--color-text-muted)]">
              {detail.blockingFindings} blocking finding
              {detail.blockingFindings === 1 ? '' : 's'}
            </p>
          </header>

          <WorkflowStepper stage={workflow.stage} dependencies={detail.dependencies} />

          {boundButNotAnalyzed && (
            <section className="flex flex-col gap-2 rounded-[var(--radius-lg)] border border-[var(--color-warning-bg)] bg-[var(--color-warning-bg)] p-4">
              <p className="text-sm text-[var(--color-text-primary)]">
                Analysis did not finish for this upload.
              </p>
              {canOperate(role) && (
                <button
                  type="button"
                  onClick={() => analyzeMutation.mutate()}
                  disabled={analyzeMutation.isPending}
                  className="inline-flex h-9 w-fit items-center gap-2 rounded-[var(--radius-md)] bg-[var(--color-primary)] px-3 text-sm font-medium text-white hover:bg-[var(--color-primary-hover)] disabled:cursor-not-allowed disabled:opacity-50"
                >
                  {analyzeMutation.isPending && <Loader2 size={14} className="animate-spin" />}
                  Re-run analysis
                </button>
              )}
              {analyzeError && (
                <p role="alert" className="text-sm text-[var(--color-danger)]">
                  {analyzeError}
                </p>
              )}
            </section>
          )}

          {showUpload && (
            <WorkflowUploadStep
              workflowId={workflow.id}
              requiredFrom={detail.requiredCoverageFrom}
              requiredTo={detail.requiredCoverageTo}
              uploads={uploadsQuery.data ?? []}
              beforePrepare={BEFORE_PREPARE.includes(workflow.stage)}
            />
          )}

          {showNoAnalyzerNotice && (
            <p className="rounded-[var(--radius-md)] border border-[var(--color-border)] bg-[var(--color-surface-raised)] px-3 py-2 text-sm text-[var(--color-text-secondary)]">
              Day-book checks for this return type are not available yet
            </p>
          )}

          <section className="rounded-[var(--radius-lg)] border border-[var(--color-border)] bg-[var(--color-surface)] p-4">
            <WorkflowFindingsTab
              workflowId={workflow.id}
              role={role as ReviewRole}
            />
          </section>

          <WorkflowEventsTimeline events={eventsQuery.data ?? []} />
        </div>
      </main>
    </AppShell>
  )
}
