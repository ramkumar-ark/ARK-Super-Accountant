import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import {
  RouterProvider,
  createRouter,
  createRoute,
  createRootRoute,
  redirect,
} from '@tanstack/react-router'
import './index.css'
import { LandingPage } from '@/pages/LandingPage'
import { LoginPage } from '@/pages/LoginPage'
import { SignupPage } from '@/pages/SignupPage'
import { DashboardPage } from '@/pages/DashboardPage'
import { OrganizationSetupPage } from '@/pages/OrganizationSetupPage'
import { MastersPage } from '@/pages/MastersPage'
import { DayBookPage } from '@/pages/DayBookPage'
import { TeamPage } from '@/pages/TeamPage'
import { useAuthStore } from '@/store/authStore'

const rootRoute = createRootRoute()

const indexRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/',
  component: LandingPage,
})

const loginRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/login',
  beforeLoad: () => {
    const hasInvite = new URLSearchParams(window.location.search).has('invite')
    if (useAuthStore.getState().isAuthenticated && !hasInvite) {
      throw redirect({ to: '/dashboard' })
    }
  },
  component: LoginPage,
})

const signupRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/signup',
  beforeLoad: () => {
    const hasInvite = new URLSearchParams(window.location.search).has('invite')
    if (useAuthStore.getState().isAuthenticated && !hasInvite) {
      throw redirect({ to: '/dashboard' })
    }
  },
  component: SignupPage,
})

const dashboardRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/dashboard',
  beforeLoad: () => {
    const { isAuthenticated, user } = useAuthStore.getState()
    if (!isAuthenticated) throw redirect({ to: '/login' })
    if (!user?.organizationId) throw redirect({ to: '/organization/setup' })
  },
  component: DashboardPage,
})

const orgSetupRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/organization/setup',
  beforeLoad: () => {
    const { isAuthenticated, user } = useAuthStore.getState()
    if (!isAuthenticated) throw redirect({ to: '/login' })
    if (user?.organizationId) throw redirect({ to: '/dashboard' })
  },
  component: OrganizationSetupPage,
})

const orgNewRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/organization/new',
  beforeLoad: () => {
    if (!useAuthStore.getState().isAuthenticated) throw redirect({ to: '/login' })
  },
  component: OrganizationSetupPage,
})

const mastersRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/masters',
  beforeLoad: () => {
    const { isAuthenticated, user } = useAuthStore.getState()
    if (!isAuthenticated) throw redirect({ to: '/login' })
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
  beforeLoad: () => {
    const { isAuthenticated, user } = useAuthStore.getState()
    if (!isAuthenticated) throw redirect({ to: '/login' })
    if (!user?.organizationId) throw redirect({ to: '/organization/setup' })
    const role = user?.role ?? ''
    if (role !== 'ROLE_ACCOUNTANT' && role !== 'ROLE_OPERATOR' && role !== 'ROLE_AUDITOR_CA') {
      throw redirect({ to: '/dashboard' })
    }
  },
  component: DayBookPage,
})

const teamRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/team',
  beforeLoad: () => {
    const { isAuthenticated, user } = useAuthStore.getState()
    if (!isAuthenticated) throw redirect({ to: '/login' })
    if (!user?.organizationId) throw redirect({ to: '/organization/setup' })
  },
  component: TeamPage,
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
  teamRoute,
])

const router = createRouter({ routeTree })

declare module '@tanstack/react-router' {
  interface Register {
    router: typeof router
  }
}

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <RouterProvider router={router} />
  </StrictMode>,
)
