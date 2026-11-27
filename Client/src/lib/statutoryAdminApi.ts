import { useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from './api'
import { TDS_SECTIONS_QUERY_KEY } from './tdsSections'

export interface StatutoryRule {
  id: string
  ruleContext: string
  rateCompany: number | null
  rateNonCompany: number | null
  thresholdPerTransaction: number | null
  thresholdAnnual: number | null
  thresholdMonthly: number | null
  thresholdExcessOnly: boolean | null
  higherRateIfNoPan: number | null
  effectiveFrom: string
  effectiveTo: string | null
  legalReference: string | null
  sourceCitation: string | null
  seeded: boolean
  createdBy: string | null
  updatedAt: string | null
}

export interface StatutorySectionView {
  id: string
  statute: 'INCOME_TAX' | 'CGST'
  code: string
  description: string | null
  rpu26qCode: string | null
  rpu140Code: string | null
  rpu140PayeeSplit: boolean
  sortOrder: number
  active: boolean
  seeded: boolean
  rules: StatutoryRule[]
}

export interface StatutorySectionsResponse {
  canEdit: boolean
  sections: StatutorySectionView[]
}

export interface StatutoryAuditEntry {
  id: string
  occurredAt: string
  actorUsername: string
  action: string
  statute: string
  sectionCode: string
  ruleId: string | null
  details: string | null
}

export interface CreateStatutoryRuleInput {
  ruleContext?: string
  effectiveDateKind: 'FINANCIAL_YEAR_START' | 'STATUTORY_DATE'
  effectiveFrom: string
  effectiveTo?: string | null
  rateCompany?: number | null
  rateNonCompany?: number | null
  thresholdPerTransaction?: number | null
  thresholdAnnual?: number | null
  thresholdMonthly?: number | null
  thresholdExcessOnly?: boolean | null
  higherRateIfNoPan?: number | null
  legalReference?: string | null
  sourceCitation: string
}

export interface CreateStatutorySectionInput {
  code: string
  description: string
  rpu26qCode?: string | null
  rpu140Code?: string | null
  rpu140PayeeSplit?: boolean
  sortOrder?: number
}

export const STATUTORY_SECTIONS_QUERY_KEY = ['statutory-admin', 'sections']
export const STATUTORY_AUDIT_QUERY_KEY = ['statutory-admin', 'audit']

export function getStatutorySections(): Promise<StatutorySectionsResponse> {
  return api.get('/statutory/sections').then((res) => res.data)
}

export function getStatutoryAudit(limit = 100): Promise<StatutoryAuditEntry[]> {
  return api.get(`/statutory/audit?limit=${limit}`).then((res) => res.data)
}

export function createStatutoryRule(
  sectionId: string,
  body: CreateStatutoryRuleInput,
): Promise<{ rule: StatutoryRule; superseded: StatutoryRule | null }> {
  return api.post(`/statutory/sections/${sectionId}/rules`, body).then((res) => res.data)
}

export function createStatutorySection(body: CreateStatutorySectionInput) {
  return api.post('/statutory/sections', body).then((res) => res.data)
}

export function setStatutorySectionActive(id: string, active: boolean) {
  return api.post(`/statutory/sections/${id}/${active ? 'activate' : 'deactivate'}`).then((res) => res.data)
}

export function deleteStatutorySection(id: string) {
  return api.delete(`/statutory/sections/${id}`).then((res) => res.data)
}

export function invalidateStatutoryCaches(qc: ReturnType<typeof useQueryClient>) {
  void qc.invalidateQueries({ queryKey: STATUTORY_SECTIONS_QUERY_KEY })
  void qc.invalidateQueries({ queryKey: STATUTORY_AUDIT_QUERY_KEY })
  void qc.invalidateQueries({ queryKey: TDS_SECTIONS_QUERY_KEY })
}

export function useStatutorySections() {
  return useQuery({ queryKey: STATUTORY_SECTIONS_QUERY_KEY, queryFn: getStatutorySections })
}

export function useStatutoryAudit(limit = 100) {
  return useQuery({ queryKey: STATUTORY_AUDIT_QUERY_KEY, queryFn: () => getStatutoryAudit(limit) })
}
