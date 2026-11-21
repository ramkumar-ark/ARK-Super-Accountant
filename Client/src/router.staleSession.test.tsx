import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { RouterProvider, createMemoryHistory } from '@tanstack/react-router'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { AxiosError, type AxiosResponse, type InternalAxiosRequestConfig } from 'axios'
import { createAppRouter } from '@/router'
import { useAuthStore } from '@/store/authStore'
import { api } from '@/lib/api'

vi.mock('@/lib/api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/lib/api')>()
  return {
    ...actual,
    api: { get: vi.fn(), post: vi.fn(), put: vi.fn(), patch: vi.fn(), delete: vi.fn() },
  }
})

const mockGet = vi.mocked(api.get)

const staleUser = {
  id: 1,
  username: 'staleuser',
  email: 'staleuser@test.local',
  role: 'ROLE_OWNER',
  organizationId: 'org-1',
  organizationName: 'Stale Test Org',
}

function seedStaleSession() {
  useAuthStore.setState({
    token: 'stale-token',
    user: staleUser,
    organizations: [],
    isAuthenticated: true,
  })
}

function rejectWithStatus(status: number) {
  const config = {} as InternalAxiosRequestConfig
  const response = { status, data: {}, config, headers: {} } as unknown as AxiosResponse
  return new AxiosError(
    `Request failed with status code ${status}`,
    'ERR_BAD_REQUEST',
    config,
    null,
    response,
  )
}

function renderAt(path: string) {
  const router = createAppRouter(createMemoryHistory({ initialEntries: [path] }))
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  )
  return router
}

describe('stale session (account deleted server-side)', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    seedStaleSession()
    mockGet.mockRejectedValue(rejectWithStatus(401))
  })

  it('never renders the dashboard: /dashboard lands on the sign-in page', async () => {
    const router = renderAt('/dashboard')

    expect(await screen.findByText('Sign in to your account')).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/login')
    expect(screen.queryByText(/Welcome back/)).not.toBeInTheDocument()
    expect(useAuthStore.getState().isAuthenticated).toBe(false)
    expect(useAuthStore.getState().user).toBeNull()
  })

  it('does not bounce the sign-in page to the dashboard (captain repro)', async () => {
    const router = renderAt('/login')

    expect(await screen.findByText('Sign in to your account')).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/login')
    expect(screen.queryByText(/Welcome back/)).not.toBeInTheDocument()
    expect(useAuthStore.getState().isAuthenticated).toBe(false)
  })

  it('never renders a protected page: /workflows lands on the sign-in page', async () => {
    const router = renderAt('/workflows')

    expect(await screen.findByText('Sign in to your account')).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/login')
    expect(useAuthStore.getState().isAuthenticated).toBe(false)
  })
})

describe('session behavior with a live server', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    seedStaleSession()
    mockGet.mockResolvedValue({ data: [] })
  })

  it('still skips the login page and renders the dashboard for a valid session', async () => {
    const router = renderAt('/login')

    expect(await screen.findByText(/Welcome back, staleuser/)).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/dashboard')
    expect(useAuthStore.getState().isAuthenticated).toBe(true)
  })

  it('shows the sign-in page for a visitor without a cached session, with no server round-trip', async () => {
    useAuthStore.setState({ token: null, user: null, organizations: [], isAuthenticated: false })

    const router = renderAt('/dashboard')

    expect(await screen.findByText('Sign in to your account')).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/login')
    expect(mockGet).not.toHaveBeenCalled()
  })
})
