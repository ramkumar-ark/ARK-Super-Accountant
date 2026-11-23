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

// ---- Task 8: applicability prompts ----

export type PromptKind = 'BOOKS_EXCEED_LIMIT' | 'NEXT_YEAR_DECLARATION' | 'LEGACY_INDIVIDUAL_NOTE'

export interface TdsApplicabilityPrompt {
  id: string
  kind: PromptKind
  financialYear: number
  estimate: TurnoverEstimate | null
  message: string
}

export function getTdsApplicabilityPrompts(orgId: string): Promise<TdsApplicabilityPrompt[]> {
  return api.get(`/v1/organizations/${orgId}/tds-applicability/prompts`).then((res) => res.data)
}

export interface ConfirmTdsPromptBody {
  applicable: boolean
  priorYearTurnover?: number
  turnoverNature?: TurnoverNature
}

export function confirmTdsApplicabilityPrompt(
  orgId: string,
  promptId: string,
  body: ConfirmTdsPromptBody,
): Promise<TdsApplicabilityPrompt> {
  return api
    .post(`/v1/organizations/${orgId}/tds-applicability/prompts/${promptId}/confirm`, body)
    .then((res) => res.data)
}

export function dismissTdsApplicabilityPrompt(
  orgId: string,
  promptId: string,
  reason: string,
): Promise<TdsApplicabilityPrompt> {
  return api
    .post(`/v1/organizations/${orgId}/tds-applicability/prompts/${promptId}/dismiss`, { reason })
    .then((res) => res.data)
}

// ---- Task 9: PAN-only 194M / 194-IB advisories ----

export interface PanOnlyAdvisory {
  section: string
  partyLedgerName: string
  financialYear: number
  month: string | null
  amount: number
  form: string
}

export function getPanOnlyAdvisories(orgId: string, financialYear: number): Promise<PanOnlyAdvisory[]> {
  return api
    .get(`/v1/organizations/${orgId}/tds-applicability/${financialYear}/pan-only-advisories`)
    .then((res) => res.data)
}
