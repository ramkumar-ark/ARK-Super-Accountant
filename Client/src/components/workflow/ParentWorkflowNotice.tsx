import type { ReactNode } from 'react'
import { Link } from '@tanstack/react-router'
import { useParentWorkflows } from '@/lib/parentWorkflows'
import {
  STAGE_LABEL,
  WORKFLOW_TYPE_LABEL,
  type Workflow,
  type WorkflowDependency,
  type WorkflowType,
} from '@/lib/workflowApi'

const LEAD: Partial<Record<WorkflowType, string>> = {
  GSTR1: 'Sales-side findings were reviewed on',
  TDS_PAYMENT: 'Monthly findings were reviewed on',
}

/** Read-only lines on a workflow's Findings section pointing at the parent workflows that own the rest. */
export function ParentWorkflowNotice({
  workflow,
  dependencies,
}: {
  workflow: Workflow
  dependencies: WorkflowDependency[]
}): ReactNode {
  const { rows } = useParentWorkflows(workflow, dependencies)
  if (rows.length === 0) return null
  return (
    <ul className="flex flex-col gap-1 text-sm text-[var(--color-text-secondary)]">
      {rows.map(({ dependency, workflow: parent }) => {
        const depType = dependency.type as WorkflowType
        const label = `${WORKFLOW_TYPE_LABEL[depType]} ${dependency.periodKey}`
        const lead = LEAD[depType] ?? 'Findings were reviewed on'
        return (
          <li key={`${dependency.type}-${dependency.periodKey}`}>
            {lead}{' '}
            {parent ? (
              <Link to="/workflows/$workflowId" params={{ workflowId: parent.id }} className="underline">
                {label}
              </Link>
            ) : (
              label
            )}
            {parent ? ` (${STAGE_LABEL[parent.stage]})` : ''}
          </li>
        )
      })}
    </ul>
  )
}
