// TDS section options come from the backend's statutory tables via GET /api/tds/sections —
// the single source of section truth. Values (codes) are what is stored; labels are what
// the user reads. A masters mapping applies across both Acts, so each mapped section shows
// the old section first and the s.393 reference after it.
import { useQuery } from '@tanstack/react-query'
import { api } from './api'

export interface TdsSectionDef {
  code: string
  description: string
  /** Legal reference from the dated rule rows (the s.393 reference when present). */
  legalReference: string | null
  rpu26qCode: string | null
  rpu140Code: string | null
  sortOrder: number
}

export const TDS_SECTIONS_QUERY_KEY = ['tds-sections']

export function getTdsSections(): Promise<TdsSectionDef[]> {
  return api.get('/tds/sections').then((res) => res.data)
}

export function useTdsSections() {
  return useQuery({ queryKey: TDS_SECTIONS_QUERY_KEY, queryFn: getTdsSections })
}

export const NEW_ACT_EFFECTIVE = '2026-04-01'

export function isNewActPeriod(periodStart: string | null | undefined): boolean {
  return !!periodStart && periodStart >= NEW_ACT_EFFECTIVE
}

const NOT_SUBJECT_OPTION = { value: 'NOT_SUBJECT', label: 'Not subject to TDS' }
const OTHER_OPTION = { value: 'OTHER', label: 'Other section' }

function labelFor(s: TdsSectionDef): string {
  const ref = s.legalReference && s.legalReference !== s.code ? `${s.code} / ${s.legalReference}` : s.code
  return `${ref} – ${s.description}`
}

/** The picker options: backend sections in the server's order, NOT_SUBJECT first, OTHER last. */
export function buildTdsSectionOptions(
  sections: TdsSectionDef[] | undefined | null,
): ReadonlyArray<{ value: string; label: string }> {
  const options = (sections ?? []).map((s) => ({ value: s.code, label: labelFor(s) }))
  return [NOT_SUBJECT_OPTION, ...options, OTHER_OPTION]
}

/** The combined old/new-Act label for a stored section code, for masters where no period is known. */
export function tdsSectionBothNames(
  section: string | null | undefined,
  sections?: TdsSectionDef[] | null,
): string {
  if (!section) {
    return '—'
  }
  // Legacy stored 194I (pre-split rent) is the land/building half.
  const key = section === '194I' ? '194I_B' : section
  return buildTdsSectionOptions(sections).find((o) => o.value === key)?.label ?? section
}

/** The reference to show for an entry of a known period: the s.393 reference on/after 1 April 2026. */
export function tdsSectionDisplay(
  section: string | null | undefined,
  periodStart: string | null | undefined,
  sections?: TdsSectionDef[] | null,
): string {
  const code = section ?? ''
  // Legacy stored 194I (pre-split rent) is the land/building half.
  const key = code === '194I' ? '194I_B' : code
  if (isNewActPeriod(periodStart)) {
    const def = sections?.find((s) => s.code === key)
    if (def?.legalReference) {
      return def.legalReference
    }
  }
  return code || '—'
}
