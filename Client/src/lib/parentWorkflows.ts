import { useQuery } from '@tanstack/react-query'
import { listWorkflows, type Workflow, type WorkflowDependency, type WorkflowType } from '@/lib/workflowApi'

/**
 * The workflows whose findings a workflow does not raise again: its dependencies of a different
 * type (GSTR-3B -> GSTR-1, TDS 26Q -> its TDS payment months). The previous period of the same
 * type is not a parent.
 */
export function useParentWorkflows(workflow: Workflow, dependencies: WorkflowDependency[]) {
  const parents = dependencies.filter((d) => d.type !== workflow.type)
  const types = [...new Set(parents.map((d) => d.type as WorkflowType))]
  const query = useQuery({
    queryKey: ['parent-workflows', workflow.id, types.join(',')],
    enabled: parents.length > 0,
    queryFn: async () => {
      const lists = await Promise.all(
        types.map((type) => listWorkflows({ type, fy: workflow.financialYear })),
      )
      return lists.flat().filter((w) => w.active)
    },
  })
  const rows = parents.map((d) => ({
    dependency: d,
    workflow: (query.data ?? []).find((w) => w.type === d.type && w.periodKey === d.periodKey),
  }))
  return { rows, isLoading: query.isLoading }
}
