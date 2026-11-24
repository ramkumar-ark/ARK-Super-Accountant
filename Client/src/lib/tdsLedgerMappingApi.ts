import { api } from './api'

export type TdsLedgerRole = 'TDS_PAYABLE' | 'TDS_RECEIVABLE' | 'NOT_TDS'
export type TdsLedgerArea = 'PAYABLE' | 'RECEIVABLE'

export const TDS_LEDGER_ROLE_LABELS: Record<TdsLedgerRole, string> = {
  TDS_PAYABLE: 'TDS payable',
  TDS_RECEIVABLE: 'TDS receivable',
  NOT_TDS: 'Not a TDS ledger',
}

export interface TdsLedgerMappingRow {
  ledgerName: string
  parentGroup: string | null
  saved: boolean
  role: TdsLedgerRole | null
  section: string | null
  suggestedRole: TdsLedgerRole | null
  suggestedSection: string | null
  area: TdsLedgerArea
  savedBy: string | null
  savedAt: string | null
}

export interface TdsLedgerMappingView {
  complete: boolean
  mastersJobId: string | null
  blockers: string[]
  receivableWarnings: string[]
  ledgers: TdsLedgerMappingRow[]
}

export interface TdsLedgerMappingItem {
  ledgerName: string
  role: TdsLedgerRole
  section: string | null
}

export const TDS_LEDGER_MAPPING_QUERY_KEY = ['tds-ledger-mapping'] as const

export function getTdsLedgerMapping(): Promise<TdsLedgerMappingView> {
  return api.get('/v1/tds/ledger-mapping').then((r) => r.data)
}

export function saveTdsLedgerMapping(items: TdsLedgerMappingItem[]): Promise<unknown> {
  return api.put('/v1/tds/ledger-mapping', { items }).then((r) => r.data)
}
