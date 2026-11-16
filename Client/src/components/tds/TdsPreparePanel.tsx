import type { Workflow } from '@/lib/workflowApi'
import { PaymentPreparePanel } from './PaymentPreparePanel'
import { QuarterPreparePanel } from './QuarterPreparePanel'

const PREPARE_ONWARD = ['PREPARE', 'FINALIZED', 'FILED']

interface Props {
  workflow: Workflow
  role: string
}

export function TdsPreparePanel({ workflow, role }: Props) {
  if (!PREPARE_ONWARD.includes(workflow.stage)) return null
  if (workflow.type === 'TDS_PAYMENT') {
    return <PaymentPreparePanel workflow={workflow} role={role} />
  }
  if (workflow.type === 'TDS_26Q') {
    return <QuarterPreparePanel workflow={workflow} role={role} />
  }
  return null
}
