import { useState } from 'react'
import { useAuthStore } from '@/store/authStore'
import { AppShell } from '@/components/AppShell'
import { useTdsApplicability } from '@/hooks/useTdsApplicability'
import { FilerProfileSection } from './FilerProfileSection'
import { DeductorSection } from './DeductorSection'
import { TdsApplicabilitySection } from './TdsApplicabilitySection'
import { TaxLedgerMappingSection } from './TaxLedgerMappingSection'
import { TdsLedgerMappingSection } from './TdsLedgerMappingSection'
import { ResponsibilityStartSection } from './ResponsibilityStartSection'
import { BaselineSection } from './BaselineSection'
import { FinancialYearsSection } from './FinancialYearsSection'

type TabId = 'gst' | 'tds' | 'ledger' | 'periods' | 'years'

const TAB_CLASS_ACTIVE =
  'border-[var(--color-primary)] text-[var(--color-primary)]'
const TAB_CLASS_INACTIVE =
  'border-transparent text-[var(--color-text-secondary)] hover:text-[var(--color-text-primary)]'

export function CompliancePage() {
  const { user } = useAuthStore()
  const [tab, setTab] = useState<TabId>('gst')
  const applicability = useTdsApplicability(user?.organizationId)
  const tdsOff = applicability.isSuccess && !applicability.anyTdsOn

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
            {tabs.map((t) => (
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
              </button>
            ))}
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
