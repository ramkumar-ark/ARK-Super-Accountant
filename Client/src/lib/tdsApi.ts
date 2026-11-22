import { api } from './api'
import type { WorkflowStage } from './workflowApi'

export type TdsChallanStatus = 'PROPOSED' | 'APPROVED'

export interface TdsChallan {
  id: string
  paymentWorkflowId: string
  bsrCode: string | null
  depositDate: string | null
  challanSerialNumber: string | null
  cin: string | null
  tdsAmount: number | null
  surcharge: number | null
  educationCess: number | null
  interest: number | null
  penalty: number | null
  totalAmount: number | null
  nilChallan: boolean
  status: TdsChallanStatus
  proposedBy: string | null
  proposedAt: string | null
  approvedBy: string | null
  approvedAt: string | null
}

export interface ChallanListResponse {
  challans: TdsChallan[]
  tdsDeducted: number | null
  approvedTdsTotal: number
  excess: number | null
}

export interface ChallanRequest {
  bsrCode: string
  depositDate: string
  challanSerialNumber: string
  cin?: string | null
  tdsAmount?: number | null
  surcharge?: number | null
  educationCess?: number | null
  interest?: number | null
  penalty?: number | null
  nilChallan: boolean
}

export function listChallans(workflowId: string): Promise<ChallanListResponse> {
  return api.get(`/v1/workflows/${workflowId}/tds/challans`).then((res) => res.data)
}

export function createChallan(workflowId: string, req: ChallanRequest): Promise<TdsChallan> {
  return api.post(`/v1/workflows/${workflowId}/tds/challans`, req).then((res) => res.data)
}

export function updateChallan(
  workflowId: string,
  challanId: string,
  req: ChallanRequest,
): Promise<TdsChallan> {
  return api
    .put(`/v1/workflows/${workflowId}/tds/challans/${challanId}`, req)
    .then((res) => res.data)
}

export function deleteChallan(workflowId: string, challanId: string): Promise<void> {
  return api.delete(`/v1/workflows/${workflowId}/tds/challans/${challanId}`).then(() => undefined)
}

export function approveChallan(workflowId: string, challanId: string): Promise<TdsChallan> {
  return api
    .post(`/v1/workflows/${workflowId}/tds/challans/${challanId}/approve`)
    .then((res) => res.data)
}

export interface TdsDeducteeEntry {
  id: string
  workflowId: string
  tdsChallanId: string | null
  deducteeName: string | null
  deducteePan: string | null
  panValid: boolean
  deducteeType: string | null
  section: string | null
  amountPaid: number | null
  tdsDeducted: number | null
  tdsDeposited: number | null
  dateOfPayment: string | null
  dateOfDeduction: string | null
  rateApplied: number | null
  higherRateApplied: boolean
  sourceVoucherIds: string | null
  supersededAt: string | null
}

export interface TdsGateCondition {
  code: string
  label: string
  passed: boolean
  detail: string
}

export interface QuarterChallans {
  paymentWorkflowId: string
  periodKey: string
  stage: WorkflowStage
  challans: TdsChallan[]
}

export function listEntries(workflowId: string): Promise<TdsDeducteeEntry[]> {
  return api.get(`/v1/workflows/${workflowId}/tds/entries`).then((res) => res.data)
}

export function listQuarterChallans(workflowId: string): Promise<QuarterChallans[]> {
  return api.get(`/v1/workflows/${workflowId}/tds/quarter-challans`).then((res) => res.data)
}

export function mapEntries(
  workflowId: string,
  challanId: string | null,
  entryIds: string[],
): Promise<TdsDeducteeEntry[]> {
  return api
    .post(`/v1/workflows/${workflowId}/tds/entries/map`, { challanId, entryIds })
    .then((res) => res.data)
}

export function getGate(workflowId: string): Promise<TdsGateCondition[]> {
  return api.get(`/v1/workflows/${workflowId}/tds/gate`).then((res) => res.data)
}

export function downloadExport(workflowId: string) {
  return api.get(`/v1/workflows/${workflowId}/tds/export`, { responseType: 'blob' })
}

export interface DeductorDetails {
  tan: string
  deductorType: string
  responsiblePersonName: string
  responsiblePersonDesignation: string
  interestPayerType: string
}

export function getOrganization(orgId: string): Promise<DeductorDetails & Record<string, unknown>> {
  return api.get(`/organizations/${orgId}`).then((res) => res.data)
}

export function updateDeductor(orgId: string, body: DeductorDetails): Promise<unknown> {
  return api.patch(`/organizations/${orgId}/deductor`, body).then((res) => res.data)
}

export interface PanOverride {
  partyLedgerName: string
  pan: string
  approvedBy: string | null
  approvedAt: string | null
  mastersPan: string | null
  mastersDiffers: boolean
}

export function listPanOverrides(): Promise<PanOverride[]> {
  return api.get('/v1/tds/pan-overrides').then((res) => res.data)
}

export interface DebtorTanItem {
  ledgerName: string
  ledgerKey: string
  tallyGuid: string | null
  tan: string | null
  sourceFindingId: string | null
  updatedBy: string | null
  updatedAt: string | null
  inLatestUpload: boolean
}

export interface DebtorTanRequest {
  ledgerName: string
  tan: string
  tallyGuid?: string | null
}

export function listDebtorTans(): Promise<DebtorTanItem[]> {
  return api.get('/v1/tds/debtor-tans').then((res) => res.data)
}

export function upsertDebtorTan(
  request: DebtorTanRequest,
): Promise<DebtorTanItem> {
  return api
    .put('/v1/tds/debtor-tans', request)
    .then((res) => res.data)
}

export function deleteDebtorTan(ledgerName: string): Promise<void> {
  return api
    .delete('/v1/tds/debtor-tans', { params: { ledgerName } })
    .then(() => undefined)
}
