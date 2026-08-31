import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { OrganizationSetupPage } from './OrganizationSetupPage'

const mockNavigate = vi.fn()

vi.mock('@tanstack/react-router', () => ({
  useNavigate: () => mockNavigate,
}))
vi.mock('@/components/Header', () => ({
  Header: () => <div data-testid="header" />,
}))
vi.mock('@/lib/api', () => ({
  api: {
    get: vi.fn(),
    post: vi.fn(),
  },
}))

const mockSwitchOrganization = vi.fn()
const mockSetOrganizations = vi.fn()

vi.mock('@/store/authStore', () => ({
  useAuthStore: vi.fn(),
}))

import { api } from '@/lib/api'
import { useAuthStore } from '@/store/authStore'

const mockGet = vi.mocked(api.get)
const mockPost = vi.mocked(api.post)
const mockUseAuthStore = vi.mocked(useAuthStore)

interface FakeAuthState {
  switchOrganization: typeof mockSwitchOrganization
  setOrganizations: typeof mockSetOrganizations
}

function setupAuthStoreMock() {
  const state: FakeAuthState = {
    switchOrganization: mockSwitchOrganization,
    setOrganizations: mockSetOrganizations,
  }
  mockUseAuthStore.mockImplementation(
    ((selector: (s: FakeAuthState) => unknown) => selector(state)) as typeof useAuthStore
  )
}

async function fillAndSubmitOrgForm(user: ReturnType<typeof userEvent.setup>) {
  const nameInput = screen.getByLabelText(/Organization Name/i)
  await user.type(nameInput, 'Acme Corp')
  await user.click(screen.getByRole('button', { name: 'Create Organization' }))
  await waitFor(() => {
    expect(screen.getByText('Select a Master Template')).toBeInTheDocument()
  })
}

const SELECT_URL = '/organizations/org-123/select'
const ONBOARD_URL = '/v1/preconfigured-masters/onboard'

describe('OrganizationSetupPage — step 2 template flow', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    setupAuthStoreMock()
    mockGet.mockResolvedValue({ data: [] })
  })

  it('POSTs /organizations/:id/select BEFORE /v1/preconfigured-masters/onboard (regression guard)', async () => {
    const user = userEvent.setup()
    const callOrder: string[] = []

    mockPost.mockImplementation((url: string) => {
      callOrder.push(url)
      if (url === '/organizations') {
        return Promise.resolve({ data: { id: 'org-123' } })
      }
      if (url === SELECT_URL) {
        return Promise.resolve({
          data: {
            token: 'org-scoped-token',
            organizationId: 'org-123',
            organizationName: 'Acme Corp',
            role: 'ROLE_OWNER',
          },
        })
      }
      if (url === ONBOARD_URL) {
        return Promise.resolve({ data: {} })
      }
      return Promise.reject(new Error(`unexpected POST ${url}`))
    })

    render(<OrganizationSetupPage />)

    await fillAndSubmitOrgForm(user)
    await user.click(screen.getByRole('button', { name: /Apply Template/i }))

    await waitFor(() => {
      expect(mockNavigate).toHaveBeenCalledWith({ to: '/dashboard' })
    })

    const selectIndex = callOrder.indexOf(SELECT_URL)
    const onboardIndex = callOrder.indexOf(ONBOARD_URL)
    expect(selectIndex).toBeGreaterThanOrEqual(0)
    expect(onboardIndex).toBeGreaterThanOrEqual(0)
    expect(selectIndex).toBeLessThan(onboardIndex)

    // The org-scoped token from /select must be applied before onboarding.
    expect(mockSwitchOrganization).toHaveBeenCalledWith('org-scoped-token', {
      organizationId: 'org-123',
      organizationName: 'Acme Corp',
      role: 'ROLE_OWNER',
    })
  })

  it('shows the backend message and a Continue to Dashboard button when onboarding fails after activation succeeds', async () => {
    const user = userEvent.setup()

    mockPost.mockImplementation((url: string) => {
      if (url === '/organizations') {
        return Promise.resolve({ data: { id: 'org-123' } })
      }
      if (url === SELECT_URL) {
        return Promise.resolve({
          data: {
            token: 'org-scoped-token',
            organizationId: 'org-123',
            organizationName: 'Acme Corp',
            role: 'ROLE_OWNER',
          },
        })
      }
      if (url === ONBOARD_URL) {
        return Promise.reject({
          response: {
            data: 'Organization already has pre-configured masters. Onboarding can only be done once.',
          },
        })
      }
      return Promise.reject(new Error(`unexpected POST ${url}`))
    })

    render(<OrganizationSetupPage />)

    await fillAndSubmitOrgForm(user)
    await user.click(screen.getByRole('button', { name: /Apply Template/i }))

    await waitFor(() => {
      expect(screen.getByRole('alert')).toHaveTextContent(
        'Organization already has pre-configured masters. Onboarding can only be done once.'
      )
      expect(screen.getByRole('alert')).toHaveTextContent(
        'Your organization is active — you can continue and configure ledgers from the Masters page.'
      )
    })

    const continueButton = screen.getByRole('button', { name: 'Continue to Dashboard' })
    expect(continueButton).toBeInTheDocument()

    // Activation already happened; navigating onward must not retry activation or onboarding.
    mockPost.mockClear()
    await user.click(continueButton)
    expect(mockNavigate).toHaveBeenCalledWith({ to: '/dashboard' })
    expect(mockPost).not.toHaveBeenCalled()
  })

  it('shows the activation error and does not call onboard when activation itself fails', async () => {
    const user = userEvent.setup()

    mockPost.mockImplementation((url: string) => {
      if (url === '/organizations') {
        return Promise.resolve({ data: { id: 'org-123' } })
      }
      if (url === SELECT_URL) {
        return Promise.reject(new Error('network error'))
      }
      if (url === ONBOARD_URL) {
        return Promise.resolve({ data: {} })
      }
      return Promise.reject(new Error(`unexpected POST ${url}`))
    })

    render(<OrganizationSetupPage />)

    await fillAndSubmitOrgForm(user)
    await user.click(screen.getByRole('button', { name: /Apply Template/i }))

    await waitFor(() => {
      expect(screen.getByRole('alert')).toHaveTextContent(
        'Could not activate the organization. Please try again.'
      )
    })

    expect(mockPost).not.toHaveBeenCalledWith(ONBOARD_URL, expect.anything())
    expect(screen.queryByRole('button', { name: 'Continue to Dashboard' })).not.toBeInTheDocument()
    expect(mockNavigate).not.toHaveBeenCalled()
  })
})
