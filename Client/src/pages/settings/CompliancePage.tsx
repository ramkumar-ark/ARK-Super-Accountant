import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useAuthStore } from '@/store/authStore'
import { AppShell } from '@/components/AppShell'
import { useTdsApplicability } from '@/hooks/useTdsApplicability'
import { getFinancialYears } from '@/lib/complianceApi'
import { getTaxLedgerMapping, TAX_LEDGER_MAPPING_QUERY_KEY } from '@/lib/taxMappingApi'
import {
  getTdsLedgerMapping,
  TDS_LEDGER_MAPPING_QUERY_KEY,
} from '@/lib/tdsLedgerMappingApi'
import { FilerProfileSection } from './FilerProfileSection'
import { DeductorSection } from './DeductorSection'
import { TdsApplicabilitySection } from './TdsApplicabilitySection'
import { TaxLedgerMappingSection } from './TaxLedgerMappingSection'
import { TdsLedgerMappingSection } from './TdsLedgerMappingSection'
import { ResponsibilityStartSection } from './ResponsibilityStartSection'
import { BaselineSection } from './BaselineSection'
import { FinancialYearsSection } from './FinancialYearsSection'
import { PILL_SUCCESS_CLASS, PILL_WARNING_CLASS } from './sectionStyles'

type TabId = 'gst' | 'tds' | 'ledger' | 'periods' | 'years'

type TabBadge = { kind: 'warn' | 'ok'; text: string }

const TAB_CLASS_ACTIVE =
  'border-[var(--color-primary)] text-[var(--color-primary)]'
const TAB_CLASS_INACTIVE =
  'border-transparent text-[var(--color-text-secondary)] hover:text-[var(--color-text-primary)]'

export function CompliancePage() {
  const { user } = useAuthStore()
  const [tab, setTab] = useState<TabId>('gst')
  const applicability = useTdsApplicability(user?.organizationId)
  const tdsOff = applicability.isSuccess && !applicability.anyTdsOn

  const gstMapping = useQuery({
    queryKey: TAX_LEDGER_MAPPING_QUERY_KEY,
    queryFn: getTaxLedgerMapping,
  })
  const tdsMapping = useQuery({
    queryKey: TDS_LEDGER_MAPPING_QUERY_KEY,
    queryFn: getTdsLedgerMapping,
    enabled: !tdsOff,
  })
  const financialYears = useQuery({
    queryKey: ['financial-years'],
    queryFn: getFinancialYears,
  })

  const badges: Partial<Record<TabId, TabBadge>> = {}
  if (!tdsOff) {
    const needsAnswer = applicability.years.filter((y) => y.needsAnswer).length
    const unmapped =
      tdsMapping.data && !tdsMapping.data.complete ? tdsMapping.data.blockers.length : 0
    const count = needsAnswer + unmapped
    if (count > 0) badges.tds = { kind: 'warn', text: String(count) }
  }
  if (gstMapping.data?.gstApplicable) {
    badges.ledger = gstMapping.data.complete
      ? { kind: 'ok', text: '✓' }
      : { kind: 'warn', text: String(gstMapping.data.blockers.length) }
  }
  const openYears = (financialYears.data ?? []).filter((y) => !y.closed).length
  if (openYears > 0) badges.years = { kind: 'warn', text: String(openYears) }

  const tabs: { id: TabId; label: string }[] = [
    { id: 'gst', label: 'GST' },
    ...(tdsOff ? [] : [{ id: 'tds' as const, label: 'TDS' }]),
    { id: 'ledger', label: 'Ledger mapping' },
    { id: 'periods', label: 'Periods & baseline' },
    { id: 'years', label: 'Financial years' },
  ]
  const active: TabId = tab === 'tds' && tdsOff ? 'gst' : tab

  return (
    <AppShell>
      <main className="flex-1 overflow-y-auto">
        <div className="max-w-4xl mx-auto p-6">
          <h1 className="text-xl font-semibold mb-6">Compliance settings</h1>
          <div
            role="tablist"
            aria-label="Compliance settings sections"
            className="flex border-b border-[var(--color-border)] mb-8 overflow-x-auto"
          >
            {tabs.map((t) => {
              const badge = badges[t.id]
              return (
                <button
                  key={t.id}
                  role="tab"
                  aria-selected={active === t.id}
                  onClick={() => setTab(t.id)}
                  className={`h-11 px-4 text-sm border-b-2 whitespace-nowrap ${
                    active === t.id ? TAB_CLASS_ACTIVE : TAB_CLASS_INACTIVE
                  }`}
                >
                  {t.label}
                  {badge && (
                    <span
                      aria-hidden="true"
                      className={`ml-1.5 ${
                        badge.kind === 'warn' ? PILL_WARNING_CLASS : PILL_SUCCESS_CLASS
                      }`}
                    >
                      {badge.text}
                    </span>
                  )}
                </button>
              )
            })}
          </div>

          {active === 'gst' && (
            <div className="space-y-8">
              <FilerProfileSection />
            </div>
          )}
          {active === 'tds' && (
            <div className="space-y-8">
              <DeductorSection />
              <TdsApplicabilitySection />
              <TdsLedgerMappingSection />
            </div>
          )}
          {active === 'ledger' && <TaxLedgerMappingSection />}
          {active === 'periods' && (
            <div className="space-y-8">
              <ResponsibilityStartSection />
              <BaselineSection />
            </div>
          )}
          {active === 'years' && <FinancialYearsSection />}

          <p className="text-xs text-[var(--color-text-muted)] mt-10">
            Signed in as {user?.username} ({user?.role})
          </p>
        </div>
      </main>
    </AppShell>
  )
}
