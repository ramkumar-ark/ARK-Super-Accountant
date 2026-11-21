import type { AxiosResponse } from 'axios'
import { api } from '@/lib/api'

export type TallyChangeStatus = 'PENDING' | 'EXPORTED' | 'APPLIED' | 'FAILED' | 'SUPERSEDED'

export type TallyCheckOutcome = 'FOUND' | 'RENAMED' | 'NOT_FOUND'

export type TallyCheckReason = 'NO_MASTERS_UPLOAD' | null

export type TallyFailureReason = 'TALLY_VALUE_CHANGED' | 'LEDGER_MISSING' | null

export interface MastersUpload {
  jobId: string
  uploadedAt: string
  stale: boolean
}

export interface TallyChangeItem {
  id: string
  ledgerKey: string
  ledgerName: string
  changeType: string
  field: string
  baselineValue: string | null
  newValue: string | null
  status: TallyChangeStatus
  sourceType: string
  approvedBy: string | null
  approvedAt: string | null
  observedValue: string | null
  failureReason: TallyFailureReason
  lastCheckOutcome: TallyCheckOutcome | null
  lastCheckedAt: string | null
  createdAt: string
  resolvedAt: string | null
  checkOutcome: TallyCheckOutcome | null
  checkReason: TallyCheckReason
  currentTallyName: string | null
  currentTallyPan: string | null
}

export interface TallyChangesResponse {
  mastersUpload: MastersUpload | null
  items: TallyChangeItem[]
}

export interface TallyExportRequest {
  itemIds?: string[]
  confirmStale?: boolean
}

export function listTallyChanges(status?: TallyChangeStatus): Promise<TallyChangesResponse> {
  return api
    .get('/v1/tally-changes', { params: status ? { status } : undefined })
    .then((res) => res.data)
}

export function exportTallyChanges(body: TallyExportRequest): Promise<AxiosResponse<Blob>> {
  return api.post('/v1/tally-changes/export', body, { responseType: 'blob' })
}

export function retryTallyChange(id: string): Promise<TallyChangeItem> {
  return api.post(`/v1/tally-changes/${id}/retry`).then((res) => res.data)
}
