import { api } from './api'

export type TdsApplicabilityBasis =
  | 'ENTITY_TYPE'
  | 'DECLARED'
  | 'CONFIRMED_FROM_BOOKS'
  | 'LEGACY_DEFAULT'
  | 'CARRIED_FORWARD'

export type TurnoverNature = 'BUSINESS' | 'PROFESSION'

export type TurnoverSource = 'BOOKS' | 'GSTR3B' | 'NONE'

export interface TurnoverEstimate {
  measuredFinancialYear: number
  amount: number | null
  source: TurnoverSource
  complete: boolean
}

export interface TdsApplicabilityYear {
  financialYear: number
  applicable: boolean
  basis: TdsApplicabilityBasis
  needsAnswer: boolean
  priorYearTurnover: number | null
  turnoverNature: TurnoverNature | null
  turnoverEstimate: TurnoverEstimate | null
}

export interface TdsApplicabilityPayload {
  entityCode: string | null
  entityCodeSource: 'DEDUCTOR_TYPE' | 'PAN' | null
  tan: string | null
  years: TdsApplicabilityYear[]
}

export function getTdsApplicability(orgId: string): Promise<TdsApplicabilityPayload> {
  return api.get(`/v1/organizations/${orgId}/tds-applicability`).then((res) => res.data)
}

export interface UpdateTdsApplicabilityBody {
  applicable: boolean
  priorYearTurnover?: number
  turnoverNature?: TurnoverNature
  note?: string
}

export function updateTdsApplicability(
  orgId: string,
  financialYear: number,
  body: UpdateTdsApplicabilityBody,
): Promise<TdsApplicabilityYear> {
  return api
    .put(`/v1/organizations/${orgId}/tds-applicability/${financialYear}`, body)
    .then((res) => res.data)
}

/** FY 2025 renders as "FY 2025-26". */
export function fyLabel(financialYear: number): string {
  return `FY ${financialYear}-${String((financialYear + 1) % 100).padStart(2, '0')}`
}
