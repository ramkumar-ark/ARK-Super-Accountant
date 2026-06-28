import { useAuthStore } from '@/store/authStore'
import { Link } from '@tanstack/react-router'
import { LayoutDashboard, Database, BookOpen, Users } from 'lucide-react'
import { Header } from './Header'

interface AppShellProps {
  children: React.ReactNode
}

export function AppShell({ children }: AppShellProps) {
  const { user } = useAuthStore()
  // ACCOUNTANT, OPERATOR, AUDITOR_CA — OWNER excluded per UI-SPEC
  const canAccessAccountingFeatures =
    user?.role === 'ROLE_ACCOUNTANT' || user?.role === 'ROLE_OPERATOR' || user?.role === 'ROLE_AUDITOR_CA'
  const canInvite = user?.role === 'ROLE_OWNER' || user?.role === 'ROLE_ACCOUNTANT'

  return (
    <div className="flex h-screen bg-[var(--color-bg)] overflow-hidden">
      {/* Sidebar */}
      <aside className="w-[var(--sidebar-width)] flex-shrink-0 flex flex-col bg-[var(--color-sidebar-bg)]">
        {/* Brand */}
        <div className="h-16 flex items-center px-4 border-b border-[var(--color-sidebar-border)]">
          <span className="text-white text-sm font-semibold">Super Accountant</span>
        </div>
        {/* Nav */}
        <nav className="flex-1 pt-2">
          <Link
            to="/dashboard"
            className="h-11 px-4 flex items-center gap-3 text-sm text-[var(--color-sidebar-item)] hover:bg-[var(--color-sidebar-active-bg)]/50 transition-colors"
            activeProps={{
              className:
                'h-11 px-4 flex items-center gap-3 text-sm bg-[var(--color-sidebar-active-bg)] text-[var(--color-sidebar-active-text)] border-l-[3px] border-[var(--color-primary)] transition-colors',
            }}
          >
            <LayoutDashboard size={20} />
            Dashboard
          </Link>
          {canAccessAccountingFeatures && (
            <Link
              to="/day-book"
              className="h-11 px-4 flex items-center gap-3 text-sm text-[var(--color-sidebar-item)] hover:bg-[var(--color-sidebar-active-bg)]/50 transition-colors"
              activeProps={{
                className:
                  'h-11 px-4 flex items-center gap-3 text-sm bg-[var(--color-sidebar-active-bg)] text-[var(--color-sidebar-active-text)] border-l-[3px] border-[var(--color-primary)] transition-colors',
              }}
            >
              <BookOpen size={20} />
              Day Book
            </Link>
          )}
          {canInvite && (
            <Link
              to="/team"
              className="h-11 px-4 flex items-center gap-3 text-sm text-[var(--color-sidebar-item)] hover:bg-[var(--color-sidebar-active-bg)]/50 transition-colors"
              activeProps={{
                className:
                  'h-11 px-4 flex items-center gap-3 text-sm bg-[var(--color-sidebar-active-bg)] text-[var(--color-sidebar-active-text)] border-l-[3px] border-[var(--color-primary)] transition-colors',
              }}
            >
              <Users size={20} />
              Team
            </Link>
          )}
          {canAccessAccountingFeatures && (
            <Link
              to="/masters"
              className="h-11 px-4 flex items-center gap-3 text-sm text-[var(--color-sidebar-item)] hover:bg-[var(--color-sidebar-active-bg)]/50 transition-colors"
              activeProps={{
                className:
                  'h-11 px-4 flex items-center gap-3 text-sm bg-[var(--color-sidebar-active-bg)] text-[var(--color-sidebar-active-text)] border-l-[3px] border-[var(--color-primary)] transition-colors',
              }}
            >
              <Database size={20} />
              Masters
            </Link>
          )}
        </nav>
      </aside>
      {/* Main content area */}
      <div className="flex-1 flex flex-col overflow-hidden">
        <Header />
        {children}
      </div>
    </div>
  )
}
