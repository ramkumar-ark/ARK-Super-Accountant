import {
  createRouter,
  createRoute,
  createRootRoute,
  redirect,
  type RouterHistory,
} from '@tanstack/react-router'
import { LandingPage } from '@/pages/LandingPage'
import { LoginPage } from '@/pages/LoginPage'
import { SignupPage } from '@/pages/SignupPage'
import { DashboardPage } from '@/pages/DashboardPage'
import { OrganizationSetupPage } from '@/pages/OrganizationSetupPage'
import { MastersPage } from '@/pages/MastersPage'
import { DayBookPage } from '@/pages/DayBookPage'
import { WorkflowListPage } from '@/pages/workflows/WorkflowListPage'
import { WorkflowDetailPage } from '@/pages/workflows/WorkflowDetailPage'
import { TeamPage } from '@/pages/TeamPage'
import { CompliancePage } from '@/pages/settings/CompliancePage'
import { BaselinePeriodsPage } from '@/pages/settings/BaselinePeriodsPage'
import { useAuthStore } from '@/store/authStore'
import { validateSession } from '@/lib/session'

async function requireSession(): Promise<void> {
  if (!(await validateSession())) throw redirect({ to: '/login' })
}

const rootRoute = createRootRoute()

const indexRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/',
  component: LandingPage,
})

const loginRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/login',
  beforeLoad: async () => {
    const hasInvite = new URLSearchParams(window.location.search).has('invite')
    if (hasInvite) return
    if (await validateSession()) throw redirect({ to: '/dashboard' })
  },
  component: LoginPage,
})

const signupRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/signup',
  beforeLoad: async () => {
    const hasInvite = new URLSearchParams(window.location.search).has('invite')
    if (hasInvite) return
    if (await validateSession()) throw redirect({ to: '/dashboard' })
  },
  component: SignupPage,
})

const dashboardRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/dashboard',
  beforeLoad: async () => {
    await requireSession()
    const { user } = useAuthStore.getState()
    if (!user?.organizationId) throw redirect({ to: '/organization/setup' })
  },
  component: DashboardPage,
})

const orgSetupRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/organization/setup',
  beforeLoad: async () => {
    await requireSession()
    const { user } = useAuthStore.getState()
    if (user?.organizationId) throw redirect({ to: '/dashboard' })
  },
  component: OrganizationSetupPage,
})

const orgNewRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/organization/new',
  beforeLoad: async () => {
    await requireSession()
  },
  component: OrganizationSetupPage,
})

const mastersRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/masters',
  beforeLoad: async () => {
    await requireSession()
    const { user } = useAuthStore.getState()
    if (!user?.organizationId) throw redirect({ to: '/organization/setup' })
    const role = user?.role ?? ''
    if (role !== 'ROLE_ACCOUNTANT' && role !== 'ROLE_OPERATOR' && role !== 'ROLE_AUDITOR_CA') {
      throw redirect({ to: '/dashboard' })
    }
  },
  component: MastersPage,
})

const dayBookRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/day-book',
  beforeLoad: async () => {
    await requireSession()
    const { user } = useAuthStore.getState()
    if (!user?.organizationId) throw redirect({ to: '/organization/setup' })
    const role = user?.role ?? ''
    if (role !== 'ROLE_ACCOUNTANT' && role !== 'ROLE_OPERATOR' && role !== 'ROLE_AUDITOR_CA') {
      throw redirect({ to: '/dashboard' })
    }
  },
  component: DayBookPage,
})

const workflowsRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/workflows',
  beforeLoad: async () => {
    await requireSession()
    const { user } = useAuthStore.getState()
    if (!user?.organizationId) throw redirect({ to: '/organization/setup' })
  },
  component: WorkflowListPage,
})

const workflowDetailRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/workflows/$workflowId',
  beforeLoad: async () => {
    await requireSession()
    const { user } = useAuthStore.getState()
    if (!user?.organizationId) throw redirect({ to: '/organization/setup' })
  },
  component: WorkflowDetailPage,
})

const teamRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/team',
  beforeLoad: async () => {
    await requireSession()
    const { user } = useAuthStore.getState()
    if (!user?.organizationId) throw redirect({ to: '/organization/setup' })
  },
  component: TeamPage,
})

const complianceRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/settings/compliance',
  beforeLoad: async () => {
    await requireSession()
    const { user } = useAuthStore.getState()
    if (!user?.organizationId) throw redirect({ to: '/organization/setup' })
  },
  component: CompliancePage,
})

const baselinePeriodsRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/settings/baseline-periods',
  beforeLoad: async () => {
    await requireSession()
    const { user } = useAuthStore.getState()
    if (!user?.organizationId) throw redirect({ to: '/organization/setup' })
  },
  component: BaselinePeriodsPage,
})

const routeTree = rootRoute.addChildren([
  indexRoute,
  loginRoute,
  signupRoute,
  dashboardRoute,
  orgSetupRoute,
  orgNewRoute,
  mastersRoute,
  dayBookRoute,
  workflowsRoute,
  workflowDetailRoute,
  teamRoute,
  complianceRoute,
  baselinePeriodsRoute,
])

export function createAppRouter(history?: RouterHistory) {
  return createRouter({ routeTree, history })
}

export type AppRouter = ReturnType<typeof createAppRouter>

declare module '@tanstack/react-router' {
  interface Register {
    router: AppRouter
  }
}
