import { api } from './api'

export type WorkflowType = 'GSTR1' | 'GSTR3B' | 'TDS_PAYMENT' | 'TDS_26Q'

export type WorkflowStage =
  | 'BASELINE'
  | 'STARTED'
  | 'DATA_UPLOADED'
  | 'FINDINGS_REVIEW'
  | 'PREPARE'
  | 'FINALIZED'
  | 'FILED'

export interface Workflow {
  id: string
  type: WorkflowType
  periodStart: string
  periodEnd: string
  periodKey: string
  financialYear: number
  revision: number
  stage: WorkflowStage
  active: boolean
  uploadJobId: string | null
  mastersJobId: string | null
  baselineConfirmedAt: string | null
  finalizedAt: string | null
  finalizedBy: string | null
  filedAt: string | null
  filingRef: string | null
  filingRefDate: string | null
  createdBy: string
  createdAt: string
}

export const WORKFLOW_TYPE_LABEL: Record<WorkflowType, string> = {
  GSTR1: 'GSTR-1',
  GSTR3B: 'GSTR-3B',
  TDS_PAYMENT: 'TDS payment',
  TDS_26Q: 'TDS 26Q',
}

export const STAGE_LABEL: Record<WorkflowStage, string> = {
  BASELINE: 'Baseline',
  STARTED: 'Started',
  DATA_UPLOADED: 'Data uploaded',
  FINDINGS_REVIEW: 'Findings review',
  PREPARE: 'Prepare',
  FINALIZED: 'Finalized',
  FILED: 'Filed',
}

export interface WorkflowFilters {
  type?: WorkflowType
  fy?: number
  stage?: WorkflowStage
}

export function listWorkflows(filters: WorkflowFilters): Promise<Workflow[]> {
  const params: Record<string, string | number> = {}
  if (filters.type) params.type = filters.type
  if (filters.fy != null) params.fy = filters.fy
  if (filters.stage) params.stage = filters.stage
  return api.get('/v1/workflows', { params }).then((res) => res.data)
}

export function startWorkflow(body: { type: WorkflowType; periodKey: string }): Promise<Workflow> {
  return api.post('/v1/workflows', body).then((res) => res.data)
}

export function canOperate(role: string | undefined): boolean {
  return role === 'ROLE_OPERATOR' || role === 'ROLE_ACCOUNTANT'
}

export function isAccountant(role: string | undefined): boolean {
  return role === 'ROLE_ACCOUNTANT'
}
