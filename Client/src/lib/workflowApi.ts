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

export interface WorkflowDependency {
  type: string
  periodKey: string
  satisfied: boolean
}

export interface WorkflowDetail {
  workflow: Workflow
  dependencies: WorkflowDependency[]
  blockingFindings: number
  baselineConfirmed: boolean
  id: string
  analysisAvailable: boolean
  requiredCoverageFrom: string
  requiredCoverageTo: string
}

export interface WorkflowEvent {
  id: string
  workflowId: string
  actor: string | null
  occurredAt: string
  kind: string
  reason: string | null
  payload: string | null
}

export interface UploadHistoryRow {
  id: string
  fileName: string
  status: string
  coverageFrom: string
  coverageTo: string
  totalVouchersParsed?: number | null
  uploadedBy?: string | null
  createdAt?: string | null
  completedAt?: string | null
  errorMessage?: string | null
  minVoucherDate?: string | null
  maxVoucherDate?: string | null
  createdForWorkflowId?: string | null
  warnings?: string[] | null
}

export function getWorkflow(id: string): Promise<WorkflowDetail> {
  return api.get(`/v1/workflows/${id}`).then((res) => res.data)
}

export function getWorkflowEvents(id: string): Promise<WorkflowEvent[]> {
  return api.get(`/v1/workflows/${id}/events`).then((res) => res.data)
}

export function listUploads(): Promise<UploadHistoryRow[]> {
  return api
    .get('/v1/day-book/uploads', { params: { page: 0, size: 20 } })
    .then((res) => res.data.content)
}

export function uploadToWorkflow(
  id: string,
  file: File,
  coverageFrom: string,
  coverageTo: string,
): Promise<Workflow & { warnings: string[] }> {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('coverageFrom', coverageFrom)
  formData.append('coverageTo', coverageTo)
  return api
    .post(`/v1/workflows/${id}/upload`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
    .then((res) => res.data)
}

export function bindExistingUpload(id: string, uploadJobId: string): Promise<Workflow> {
  return api.post(`/v1/workflows/${id}/use-upload`, { uploadJobId }).then((res) => res.data)
}

export function analyzeWorkflow(id: string): Promise<Workflow> {
  return api.post(`/v1/workflows/${id}/analyze`).then((res) => res.data)
}

export function invalidateWorkflow(
  queryClient: { invalidateQueries: (filters: { queryKey: unknown[] }) => Promise<unknown> },
  id: string,
): Promise<unknown> {
  return Promise.all([
    queryClient.invalidateQueries({ queryKey: ['workflow', id] }),
    queryClient.invalidateQueries({ queryKey: ['workflow-events', id] }),
    queryClient.invalidateQueries({ queryKey: ['workflow-findings', id] }),
    queryClient.invalidateQueries({ queryKey: ['workflows'] }),
  ])
}
