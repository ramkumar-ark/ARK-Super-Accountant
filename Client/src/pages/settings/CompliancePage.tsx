import { useAuthStore } from '@/store/authStore'
import { AppShell } from '@/components/AppShell'
import { FilerProfileSection } from './FilerProfileSection'
import { DeductorSection } from './DeductorSection'
import { TdsApplicabilitySection } from './TdsApplicabilitySection'
import { TaxLedgerMappingSection } from './TaxLedgerMappingSection'
import { TdsLedgerMappingSection } from './TdsLedgerMappingSection'
import { ResponsibilityStartSection } from './ResponsibilityStartSection'
import { BaselineSection } from './BaselineSection'
import { FinancialYearsSection } from './FinancialYearsSection'

export function CompliancePage() {
  const { user } = useAuthStore()
  return (
    <AppShell>
      <main className="flex-1 overflow-y-auto">
        <div className="max-w-4xl mx-auto p-6 space-y-10">
          <h1 className="text-xl font-semibold">Compliance settings</h1>
          <FilerProfileSection />
          <DeductorSection />
          <TdsApplicabilitySection />
          <TaxLedgerMappingSection />
          <TdsLedgerMappingSection />
          <ResponsibilityStartSection />
          <BaselineSection />
          <FinancialYearsSection />
          <p className="text-xs text-[var(--color-text-muted)]">
            Signed in as {user?.username} ({user?.role})
          </p>
        </div>
      </main>
    </AppShell>
  )
}
