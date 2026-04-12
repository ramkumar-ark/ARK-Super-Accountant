import { useAuthStore } from '@/store/authStore'
import { useNavigate } from '@tanstack/react-router'

export function DashboardPage() {
  const { user, logout } = useAuthStore()
  const navigate = useNavigate()

  function handleLogout() {
    logout()
    navigate({ to: '/login' })
  }

  return (
    <div className="min-h-screen bg-[var(--color-bg)]">
      {/* Topbar */}
      <header className="h-16 bg-[var(--color-surface)] border-b border-[var(--color-border)] flex items-center justify-between px-6 shadow-[var(--shadow-sm)]">
        <span className="text-base font-semibold text-[var(--color-text-primary)]">
          Super Accountant
        </span>
        <div className="flex items-center gap-4">
          <span className="text-sm text-[var(--color-text-secondary)]">
            {user?.username}{' '}
            <span className="text-xs text-[var(--color-text-muted)] font-mono">
              ({user?.role})
            </span>
          </span>
          <button
            onClick={handleLogout}
            className="text-sm text-[var(--color-text-secondary)] hover:text-[var(--color-danger)] transition-colors"
          >
            Sign out
          </button>
        </div>
      </header>

      {/* Placeholder content */}
      <main className="p-6">
        <h1 className="text-xl font-semibold text-[var(--color-text-primary)] mb-2">
          Dashboard
        </h1>
        <p className="text-sm text-[var(--color-text-muted)]">
          Welcome back, {user?.username}. More features coming soon.
        </p>
      </main>
    </div>
  )
}
