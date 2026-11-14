import { useParams } from '@tanstack/react-router'
import { AppShell } from '@/components/AppShell'

export function WorkflowDetailPage() {
  const { workflowId } = useParams({ from: '/workflows/$workflowId' })
  return (
    <AppShell>
      <main className="flex-1 overflow-y-auto p-6">
        <p className="text-sm text-[var(--color-text-muted)]">Workflow {workflowId}</p>
      </main>
    </AppShell>
  )
}
