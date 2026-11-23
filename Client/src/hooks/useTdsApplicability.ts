import { useQuery } from '@tanstack/react-query'
import { getTdsApplicability } from '@/lib/tdsApplicabilityApi'

export function useTdsApplicability(orgId: string | undefined) {
  const query = useQuery({
    queryKey: ['tds-applicability', orgId],
    queryFn: () => getTdsApplicability(orgId as string),
    enabled: !!orgId,
  })

  const years = query.data?.years ?? []

  /** Whether TDS is on for one financial year (FY 2026-27 = 2026); false while loading. */
  const isTdsOnFor = (financialYear: number): boolean =>
    years.find((y) => y.financialYear === financialYear)?.applicable ?? false

  const anyTdsOn = years.some((y) => y.applicable)

  return { ...query, years, isTdsOnFor, anyTdsOn }
}
