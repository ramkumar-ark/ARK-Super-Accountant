import { useAuthStore } from "@/store/authStore"
import { useNavigate, useRouterState } from "@tanstack/react-router"
import { OrganizationSelector } from "./OrganizationSelector"
import { RoleBadge } from "./RoleBadge"

const PAGE_TITLES: Record<string, string> = {
    '/dashboard': 'Dashboard',
    '/day-book': 'Day Book',
    '/masters': 'Masters',
    '/team': 'Team',
    '/organization/setup': 'Organization Setup',
}

export function Header() {

    const { user, logout } = useAuthStore()
    const navigate = useNavigate()
    const pathname = useRouterState({ select: (s) => s.location.pathname })
    const pageTitle = PAGE_TITLES[pathname] ?? ''

    function handleLogout() {
        logout()
        navigate({ to: '/login' })
    }

    return (
        <header className="h-16 bg-[var(--color-surface)] border-b border-[var(--color-border)] flex items-center justify-between px-6 shadow-[var(--shadow-sm)] flex-shrink-0" >
            <span className="text-base font-semibold text-[var(--color-text-primary)]">
                {pageTitle}
            </span>
            <div className="flex items-center gap-4">
                {user && <OrganizationSelector />}
                <RoleBadge role={user?.role} />
                <span className="text-sm text-[var(--color-text-secondary)]">{user?.username}</span>
                <button
                    onClick={handleLogout}
                    className="text-sm text-[var(--color-text-secondary)] hover:text-[var(--color-danger)] transition-colors"
                >
                    Sign out
                </button>
            </div>
        </header>
    );
}