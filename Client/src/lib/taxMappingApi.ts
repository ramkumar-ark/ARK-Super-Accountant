import { api } from './api'

export type TaxLedgerRole =
  | 'OUTPUT'
  | 'INPUT'
  | 'RCM_LIABILITY'
  | 'RCM_INPUT'
  | 'CASH_BALANCE'
  | 'LIABILITY_BALANCE'
  | 'CREDIT_BALANCE'
  | 'GST_TDS'
  | 'NOT_TAX'

export const TAX_LEDGER_ROLE_LABELS: Record<TaxLedgerRole, string> = {
  OUTPUT: 'Output tax',
  INPUT: 'Input tax',
  RCM_LIABILITY: 'RCM liability',
  RCM_INPUT: 'RCM input',
  CASH_BALANCE: 'Portal cash balance',
  LIABILITY_BALANCE: 'Portal liability balance',
  CREDIT_BALANCE: 'Portal credit (ITC) balance',
  GST_TDS: 'GST TDS',
  NOT_TAX: 'Not a tax ledger',
}

export type GstHead = 'IGST' | 'CGST' | 'SGST' | 'CESS'

export interface TaxLedgerMappingRow {
  ledgerName: string
  parentGroup: string | null
  saved: boolean
  role: TaxLedgerRole | null
  head: GstHead | null
  suggestedRole: TaxLedgerRole | null
  suggestedHead: GstHead | null
  savedBy: string | null
  savedAt: string | null
}

export interface TaxLedgerMappingView {
  complete: boolean
  mastersJobId: string | null
  blockers: string[]
  ledgers: TaxLedgerMappingRow[]
}

export interface TaxLedgerMappingItem {
  ledgerName: string
  role: TaxLedgerRole
  head: GstHead | null
}

export function getTaxLedgerMapping(): Promise<TaxLedgerMappingView> {
  return api.get('/v1/gst/tax-ledger-mapping').then((r) => r.data)
}

export function saveTaxLedgerMapping(
  items: TaxLedgerMappingItem[],
): Promise<TaxLedgerMappingView> {
  return api.put('/v1/gst/tax-ledger-mapping', { items }).then((r) => r.data)
}
