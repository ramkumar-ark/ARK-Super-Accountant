import { useMemo, useState } from 'react'
import { Link, useNavigate } from '@tanstack/react-router'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { Plus } from 'lucide-react'
import { AppShell } from '@/components/AppShell'
import { StartWorkflowDialog } from '@/components/workflow/StartWorkflowDialog'
import { useAuthStore } from '@/store/authStore'
import { useTdsApplicability } from '@/hooks/useTdsApplicability'
import {
  STAGE_LABEL,
  WORKFLOW_TYPE_LABEL,
  canOperate,
  listWorkflows,
  type Workflow,
  type WorkflowStage,
  type WorkflowType,
} from '@/lib/workflowApi'

function financialYearLabel(fy: number) {
  return `FY ${fy}-${String(fy + 1).slice(2)}`
}

const TDS_TYPES: WorkflowType[] = ['TDS_PAYMENT', 'TDS_26Q']

export function WorkflowListPage() {
  const { user } = useAuthStore()
  const role = user?.role
  const orgId = user?.organizationId
  const { anyTdsOn } = useTdsApplicability(orgId)
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  const [type, setType] = useState<WorkflowType | ''>('')
  const [stage, setStage] = useState<WorkflowStage | ''>('')
  const [dialogOpen, setDialogOpen] = useState(false)

  const filters = {
    ...(type ? { type } : {}),
    ...(stage ? { stage } : {}),
  }

  const { data, isLoading, error } = useQuery({
    queryKey: ['workflows', filters],
    queryFn: () => listWorkflows(filters),
  })

  const groups = useMemo(() => {
    const byYear = new Map<number, Workflow[]>()
    for (const workflow of data ?? []) {
      const rows = byYear.get(workflow.financialYear) ?? []
      rows.push(workflow)
      byYear.set(workflow.financialYear, rows)
    }
    return [...byYear.entries()]
      .sort((a, b) => b[0] - a[0])
      .map(([fy, rows]) => [
        fy,
        [...rows].sort((a, b) => a.periodStart.localeCompare(b.periodStart)),
      ] as const)
  }, [data])

  function handleStarted(workflow: Workflow) {
    setDialogOpen(false)
    void queryClient.invalidateQueries({ queryKey: ['workflows'] })
    void navigate({ to: '/workflows/$workflowId', params: { workflowId: workflow.id } })
  }

  return (
    <AppShell>
      <main className="flex-1 overflow-y-auto p-6">
        <div className="flex items-start justify-between gap-4 mb-6">
          <h1 className="text-xl font-semibold text-[var(--color-text-primary)]">Workflows</h1>
          {canOperate(role) && (
            <button
              onClick={() => setDialogOpen(true)}
              className="h-11 px-4 rounded-[var(--radius-md)] bg-[var(--color-primary)] text-sm text-white hover:bg-[var(--color-primary-hover)] flex items-center gap-2"
            >
              <Plus size={18} />
              Start workflow
            </button>
          )}
        </div>

        <div className="flex flex-wrap gap-4 mb-6">
          <div>
            <label className="block text-sm font-semibold text-[var(--color-text-primary)] mb-1">
              Type
            </label>
            <select
              aria-label="Type"
              value={type}
              onChange={(e) => setType(e.target.value as WorkflowType | '')}
              className="h-11 px-3 rounded-[var(--radius-md)] border border-[var(--color-border)] bg-[var(--color-surface-raised)] text-sm text-[var(--color-text-primary)]"
            >
              <option value="">All types</option>
              {(Object.keys(WORKFLOW_TYPE_LABEL) as WorkflowType[])
                .filter((value) => !TDS_TYPES.includes(value) || anyTdsOn)
                .map((value) => (
                <option key={value} value={value}>
                  {WORKFLOW_TYPE_LABEL[value]}
                </option>
              ))}
            </select>
          </div>
          <div>
            <label className="block text-sm font-semibold text-[var(--color-text-primary)] mb-1">
              Stage
            </label>
            <select
              aria-label="Stage"
              value={stage}
              onChange={(e) => setStage(e.target.value as WorkflowStage | '')}
              className="h-11 px-3 rounded-[var(--radius-md)] border border-[var(--color-border)] bg-[var(--color-surface-raised)] text-sm text-[var(--color-text-primary)]"
            >
              <option value="">All stages</option>
              {(Object.keys(STAGE_LABEL) as WorkflowStage[]).map((value) => (
                <option key={value} value={value}>
                  {STAGE_LABEL[value]}
                </option>
              ))}
            </select>
          </div>
        </div>

        {isLoading && <p className="text-sm text-[var(--color-text-muted)]">Loading workflows…</p>}
        {error && (
          <div className="rounded-[var(--radius-md)] border border-[var(--color-danger-border)] bg-[var(--color-danger-subtle)] p-4 text-sm text-[var(--color-danger)] flex flex-col gap-2">
            <p>
              {(error as { response?: { status?: number; data?: { error?: string } } }).response?.status === 403
                ? (error as { response?: { data?: { error?: string } } }).response?.data?.error ||
                  'Access denied to this organization.'
                : 'Could not load workflows.'}
            </p>
            {(error as { response?: { status?: number } }).response?.status === 403 && (
              <p className="text-xs text-[var(--color-text-secondary)]">
                Please{' '}
                <Link
                  to="/organization/setup"
                  className="font-medium text-[var(--color-primary)] underline hover:text-[var(--color-primary-hover)]"
                >
                  select or setup an organization
                </Link>{' '}
                to continue.
              </p>
            )}
          </div>
        )}

        {!isLoading && !error && data?.length === 0 && (
          <p className="text-sm text-[var(--color-text-muted)]">No workflows yet.</p>
        )}

        {groups.map(([fy, rows]) => (
          <section key={fy} className="mb-8">
            <h2 className="text-sm font-semibold text-[var(--color-text-secondary)] mb-2">
              {financialYearLabel(fy)}
            </h2>
            <ul className="rounded-[var(--radius-lg)] border border-[var(--color-border)] bg-[var(--color-surface)] divide-y divide-[var(--color-border)]">
              {rows.map((workflow) => (
                <li key={workflow.id}>
                  <button
                    onClick={() =>
                      void navigate({
                        to: '/workflows/$workflowId',
                        params: { workflowId: workflow.id },
                      })
                    }
                    className="w-full px-4 py-3 flex items-center gap-4 text-left hover:bg-[var(--color-surface-raised)]"
                  >
                    <span className="text-sm font-medium text-[var(--color-text-primary)] w-24">
                      {WORKFLOW_TYPE_LABEL[workflow.type]}
                    </span>
                    <span className="text-sm text-[var(--color-text-secondary)]">
                      {workflow.periodKey}
                    </span>
                    {workflow.revision > 1 && (
                      <span className="text-xs text-[var(--color-text-muted)]">
                        Rev {workflow.revision}
                      </span>
                    )}
                    <span className="ml-auto text-xs px-2 py-1 rounded-[var(--radius-sm)] bg-[var(--color-surface-raised)] text-[var(--color-text-secondary)]">
                      {STAGE_LABEL[workflow.stage]}
                    </span>
                  </button>
                </li>
              ))}
            </ul>
          </section>
        ))}
      </main>

      <StartWorkflowDialog
        open={dialogOpen}
        onClose={() => setDialogOpen(false)}
        onStarted={handleStarted}
      />
    </AppShell>
  )
}
