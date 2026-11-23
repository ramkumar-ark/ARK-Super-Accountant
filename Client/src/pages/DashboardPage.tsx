import { useAuthStore } from '@/store/authStore'
import { AppShell } from '@/components/AppShell'
import { TdsApplicabilityBanner } from '@/components/tds/TdsApplicabilityBanner'

export function DashboardPage() {
  const { user } = useAuthStore()

  return (
    <AppShell>
      <main className="flex-1 overflow-y-auto p-6">
        <TdsApplicabilityBanner />
        <p className="text-sm text-[var(--color-text-muted)]">
          Welcome back, {user?.username}. More features coming soon.
        </p>
      </main>
    </AppShell>
  )
}
