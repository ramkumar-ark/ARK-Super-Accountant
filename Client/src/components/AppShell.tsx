import { useState } from 'react'
import { useAuthStore } from '@/store/authStore'
import { Link } from '@tanstack/react-router'
import { LayoutDashboard, Database, BookOpen, Users, ListChecks, Settings, Scale } from 'lucide-react'
import { Header } from './Header'

interface AppShellProps {
  children: React.ReactNode
}

const NAV_LINK_CLASS =
  'h-11 px-4 flex items-center gap-3 text-sm text-[var(--color-sidebar-item)] hover:bg-[var(--color-sidebar-active-bg)]/50 transition-colors'
const NAV_LINK_ACTIVE_CLASS =
  'h-11 px-4 flex items-center gap-3 text-sm bg-[var(--color-sidebar-active-bg)] text-[var(--color-sidebar-active-text)] border-l-[3px] border-[var(--color-primary)] transition-colors'

export function AppShell({ children }: AppShellProps) {
  const { user } = useAuthStore()
  const [sidebarOpen, setSidebarOpen] = useState(false)
  // ACCOUNTANT, OPERATOR, AUDITOR_CA — OWNER excluded per UI-SPEC
  const canAccessAccountingFeatures =
    user?.role === 'ROLE_ACCOUNTANT' || user?.role === 'ROLE_OPERATOR' || user?.role === 'ROLE_AUDITOR_CA'
  const canInvite = user?.role === 'ROLE_OWNER' || user?.role === 'ROLE_ACCOUNTANT'

  const closeSidebar = () => setSidebarOpen(false)

  return (
    <div className="flex h-screen bg-[var(--color-bg)] overflow-hidden">
      {sidebarOpen && (
        <div
          className="fixed inset-0 z-40 bg-black/50 lg:hidden"
          onClick={closeSidebar}
          aria-hidden="true"
        />
      )}
      {/* Sidebar: static column on lg+, slide-in drawer below lg */}
      <aside
        className={`fixed inset-y-0 left-0 z-50 w-[var(--sidebar-width)] flex flex-col bg-[var(--color-sidebar-bg)] transform transition-transform duration-200 lg:static lg:translate-x-0 ${
          sidebarOpen ? 'translate-x-0' : '-translate-x-full'
        }`}
      >
        {/* Brand */}
        <div className="h-16 flex items-center px-4 border-b border-[var(--color-sidebar-border)]">
          <span className="text-white text-sm font-semibold">Super Accountant</span>
        </div>
        {/* Nav */}
        <nav className="flex-1 pt-2 overflow-y-auto">
          <Link
            to="/dashboard"
            className={NAV_LINK_CLASS}
            activeProps={{ className: NAV_LINK_ACTIVE_CLASS }}
            onClick={closeSidebar}
          >
            <LayoutDashboard size={20} />
            Dashboard
          </Link>
          {user?.organizationId && (
            <Link
              to="/workflows"
              className={NAV_LINK_CLASS}
              activeProps={{ className: NAV_LINK_ACTIVE_CLASS }}
              onClick={closeSidebar}
            >
              <ListChecks size={20} />
              Workflows
            </Link>
          )}
          {canAccessAccountingFeatures && (
            <Link
              to="/day-book"
              className={NAV_LINK_CLASS}
              activeProps={{ className: NAV_LINK_ACTIVE_CLASS }}
              onClick={closeSidebar}
            >
              <BookOpen size={20} />
              Day Book
            </Link>
          )}
          {canInvite && (
            <Link
              to="/team"
              className={NAV_LINK_CLASS}
              activeProps={{ className: NAV_LINK_ACTIVE_CLASS }}
              onClick={closeSidebar}
            >
              <Users size={20} />
              Team
            </Link>
          )}
          {canAccessAccountingFeatures && (
            <Link
              to="/masters"
              className={NAV_LINK_CLASS}
              activeProps={{ className: NAV_LINK_ACTIVE_CLASS }}
              onClick={closeSidebar}
            >
              <Database size={20} />
              Masters
            </Link>
          )}
          {user?.organizationId && (
            <Link
              to="/settings/compliance"
              className={NAV_LINK_CLASS}
              activeProps={{ className: NAV_LINK_ACTIVE_CLASS }}
              onClick={closeSidebar}
            >
              <Settings size={20} />
              Compliance settings
            </Link>
          )}
          <Link
            to="/settings/statutory"
            className={NAV_LINK_CLASS}
            activeProps={{ className: NAV_LINK_ACTIVE_CLASS }}
            onClick={closeSidebar}
          >
            <Scale size={20} />
            Statutory configuration
          </Link>
        </nav>
      </aside>
      {/* Main content area */}
      <div className="flex-1 flex flex-col overflow-hidden min-w-0">
        <Header onMenuClick={() => setSidebarOpen(true)} />
        {children}
      </div>
    </div>
  )
}
