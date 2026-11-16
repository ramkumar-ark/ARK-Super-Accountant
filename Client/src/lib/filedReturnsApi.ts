import { api } from './api'

export type Gstr3bRowCode =
  | '3.1a'
  | '3.1b'
  | '3.1c'
  | '3.1d'
  | '3.1e'
  | '4A1'
  | '4A2'
  | '4A3'
  | '4A4'
  | '4A5'
  | '4B1'
  | '4B2'
  | '4C'
  | '4D1'
  | '4D2'
  | '5'
  | '6.1-CASH'
  | '6.1-ITC'

export interface Gstr3bRow {
  rowCode: string
  taxable: number | null
  igst: number | null
  cgst: number | null
  sgst: number | null
  cess: number | null
}

export interface Gstr3bParseResponse {
  fileId: string
  gstin: string
  filingPeriod: string
  rows: Gstr3bRow[]
  warnings: string[]
  parserVersion: string
}

export interface Gstr3bSaveRequest {
  filingPeriod: string
  gstin?: string | null
  fileId?: string
  parserVersion?: string
  rows: Gstr3bRow[]
}

export type FiledFormType = 'GSTR1' | 'IFF' | 'GSTR3B'
export type FiledSourceKind = 'PORTAL_JSON' | 'PORTAL_PDF' | 'MANUAL_ENTRY'

export interface FiledReturnSnapshotSummary {
  id: string
  formType: FiledFormType
  filingPeriod: string
  sourceKind: FiledSourceKind
  importedBy: string
  importedAt: string
  superseded: boolean
  lineCount: number
}

export type HeadName = 'IGST' | 'CGST' | 'SGST' | 'CESS'

export interface HeadVariance {
  filed: number
  books: number
  diff: number
}

export type BaselineStatus =
  | 'NOT_IMPORTED'
  | 'MAPPING_INCOMPLETE'
  | 'NOT_RECONCILED'
  | 'RECONCILED'

export interface BaselinePeriodView {
  workflowId: string
  type: 'GSTR1' | 'GSTR3B'
  periodKey: string
  periodStart: string
  periodEnd: string
  confirmed: boolean
  status: BaselineStatus
  snapshots: FiledReturnSnapshotSummary[]
  materialOpen: number
  materialExplained: number
  info: number
  variance: Record<HeadName, HeadVariance>
}

export interface ReconOutcome {
  workflowId: string
  status: string
  material: number
  info: number
  at: string
}

export function uploadGstr1Json(
  file: File,
  form: 'GSTR1' | 'IFF',
): Promise<FiledReturnSnapshotSummary> {
  const body = new FormData()
  body.append('file', file)
  body.append('form', form)
  return api.post('/v1/filed-returns/gstr1/json', body).then((r) => r.data)
}

export function parseGstr3bPdf(file: File): Promise<Gstr3bParseResponse> {
  const body = new FormData()
  body.append('file', file)
  return api.post('/v1/filed-returns/gstr3b/pdf', body).then((r) => r.data)
}

export function saveGstr3b(req: Gstr3bSaveRequest): Promise<unknown> {
  return api.post('/v1/filed-returns/gstr3b', req)
}

export function listBaselinePeriods(): Promise<BaselinePeriodView[]> {
  return api.get('/v1/filed-returns/baseline').then((r) => r.data)
}

export function reconcileBaseline(workflowId: string): Promise<ReconOutcome> {
  return api.post(`/v1/filed-returns/baseline/${workflowId}/reconcile`).then((r) => r.data)
}

export const GSTR3B_ROWS: {
  code: Gstr3bRowCode
  label: string
  cols: ('taxable' | 'igst' | 'cgst' | 'sgst' | 'cess')[]
}[] = [
  {
    code: '3.1a',
    label: '3.1(a) Taxable outward supplies',
    cols: ['taxable', 'igst', 'cgst', 'sgst', 'cess'],
  },
  {
    code: '3.1b',
    label: '3.1(b) Outward taxable supplies to unregistered',
    cols: ['taxable', 'igst', 'cgst', 'sgst', 'cess'],
  },
  {
    code: '3.1c',
    label: '3.1(c) Outward taxable supplies to consumers',
    cols: ['taxable', 'igst', 'cgst', 'sgst', 'cess'],
  },
  {
    code: '3.1d',
    label: '3.1(d) Deemed supply',
    cols: ['taxable', 'igst', 'cgst', 'sgst', 'cess'],
  },
  {
    code: '3.1e',
    label: '3.1(e) Supplies on which RCM is payable',
    cols: ['taxable', 'igst', 'cgst', 'sgst', 'cess'],
  },
  { code: '4A1', label: '4A(1) ITC available on imports', cols: ['igst', 'cgst', 'sgst', 'cess'] },
  { code: '4A2', label: '4A(2) ITC available on inward RCM', cols: ['igst', 'cgst', 'sgst', 'cess'] },
  {
    code: '4A3',
    label: '4A(3) ITC available on inward supplies from ISD',
    cols: ['igst', 'cgst', 'sgst', 'cess'],
  },
  { code: '4A4', label: '4A(4) ITC available on all other ITC', cols: ['igst', 'cgst', 'sgst', 'cess'] },
  {
    code: '4A5',
    label: '4A(5) ITC available — reversal (Rule 42/43/38)',
    cols: ['igst', 'cgst', 'sgst', 'cess'],
  },
  { code: '4B1', label: '4B(1) ITC reversal (Rule 38)', cols: ['igst', 'cgst', 'sgst', 'cess'] },
  {
    code: '4B2',
    label: '4B(2) ITC reversal (others)',
    cols: ['igst', 'cgst', 'sgst', 'cess'],
  },
  { code: '4C', label: '4(C) Ineligible ITC', cols: ['igst', 'cgst', 'sgst', 'cess'] },
  {
    code: '4D1',
    label: '4D(1) ITC lost on account of refunds',
    cols: ['igst', 'cgst', 'sgst', 'cess'],
  },
  { code: '4D2', label: '4D(2) ITC lost in other cases', cols: ['igst', 'cgst', 'sgst', 'cess'] },
  { code: '5', label: '5 Non-GST inward supplies', cols: ['taxable', 'igst', 'cgst', 'sgst', 'cess'] },
  { code: '6.1-CASH', label: '6.1 Cash paid', cols: ['igst', 'cgst', 'sgst', 'cess'] },
  { code: '6.1-ITC', label: '6.1 ITC claimed', cols: ['igst', 'cgst', 'sgst', 'cess'] },
]
