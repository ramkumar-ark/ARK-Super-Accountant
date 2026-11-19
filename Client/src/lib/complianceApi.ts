import { api } from './api'
import type { WorkflowType } from './workflowApi'

export interface ResponsibilityStart {
  type: WorkflowType
  firstPeriodStart: string | null
  setBy: string | null
  setAt: string | null
  locked: boolean
  baselineRowCount: number
}

export interface FilerProfile {
  id: string
  frequency: 'MONTHLY' | 'QRMP'
  iffOptIn: boolean
  einvoiceEnabled: boolean
  hsnDigits: number
  effectiveFrom: string
  createdBy: string | null
  createdAt: string | null
}

export interface FilerProfileInput {
  frequency: 'MONTHLY' | 'QRMP'
  iffOptIn: boolean
  einvoiceEnabled: boolean
  hsnDigits: number
  effectiveFrom: string
}

export interface BaselineTypeStatus {
  type: WorkflowType
  unconfirmedRows: number
  confirmedRows: number
  latestEnd: string | null
}

export interface BaselineStatus {
  confirmed: boolean
  rangeStart: string | null
  rangeEnd: string | null
  types: BaselineTypeStatus[]
  blockers: string[]
  importedFiledData: string
  discrepancyCount: number | null
}

export interface FinancialYearStatus {
  financialYear: number
  start: string
  end: string
  closed: boolean
  closeType: 'ITR_FILED' | 'HANDLED_ELSEWHERE' | null
  itrAckNo: string | null
  itrAckDate: string | null
  note: string | null
  closable: boolean
  openItems: string[]
  blockers: string[]
}

export interface CloseFinancialYearInput {
  closeType: 'ITR_FILED' | 'HANDLED_ELSEWHERE'
  itrAckNo?: string
  itrAckDate?: string
  note?: string
}

export interface CloseFinancialYearResult {
  year: FinancialYearStatus
  warnings: string[]
}

export function getResponsibilityStarts(): Promise<ResponsibilityStart[]> {
  return api.get('/v1/compliance/responsibility-start').then((r) => r.data)
}

export function putResponsibilityStart(
  type: WorkflowType,
  firstPeriodStart: string,
): Promise<ResponsibilityStart> {
  return api
    .put('/v1/compliance/responsibility-start', { type, firstPeriodStart })
    .then((r) => r.data)
}

export function getFilerProfiles(): Promise<FilerProfile[]> {
  return api.get('/v1/compliance/filer-profile').then((r) => r.data)
}

export function putFilerProfile(body: FilerProfileInput): Promise<FilerProfile> {
  return api.put('/v1/compliance/filer-profile', body).then((r) => r.data)
}

export function getBaselineStatus(): Promise<BaselineStatus> {
  return api.get('/v1/compliance/baseline').then((r) => r.data)
}

export function confirmBaseline(uploadJobId?: string): Promise<BaselineStatus> {
  return api
    .post('/v1/compliance/baseline/confirm', uploadJobId ? { uploadJobId } : {})
    .then((r) => r.data)
}

export function getFinancialYears(): Promise<FinancialYearStatus[]> {
  return api.get('/v1/compliance/financial-years').then((r) => r.data)
}

export function closeFinancialYear(
  fy: number,
  body: CloseFinancialYearInput,
): Promise<CloseFinancialYearResult> {
  return api.post(`/v1/compliance/financial-years/${fy}/close`, body).then((r) => r.data)
}
