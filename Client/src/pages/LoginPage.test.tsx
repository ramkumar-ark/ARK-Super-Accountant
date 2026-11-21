import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { LoginPage } from './LoginPage'

const mockNavigate = vi.fn()

vi.mock('@tanstack/react-router', () => ({
  useNavigate: () => mockNavigate,
  Link: ({ children }: { children: React.ReactNode }) => <a href="#">{children}</a>,
}))
vi.mock('@/lib/api', () => ({
  api: {
    get: vi.fn(),
    post: vi.fn(),
  },
}))

const mockLogin = vi.fn()
const mockSetOrganizations = vi.fn()
const mockSwitchOrganization = vi.fn()

vi.mock('@/store/authStore', () => ({
  useAuthStore: vi.fn(),
}))

import { api } from '@/lib/api'
import { useAuthStore } from '@/store/authStore'

const mockGet = vi.mocked(api.get)
const mockPost = vi.mocked(api.post)
const mockUseAuthStore = vi.mocked(useAuthStore)

function setupAuthStoreMock() {
  mockUseAuthStore.mockImplementation(
    ((selector: (s: {
      login: typeof mockLogin
      setOrganizations: typeof mockSetOrganizations
      switchOrganization: typeof mockSwitchOrganization
    }) => unknown) =>
      selector({
        login: mockLogin,
        setOrganizations: mockSetOrganizations,
        switchOrganization: mockSwitchOrganization,
      })) as typeof useAuthStore
  )
}

describe('LoginPage — browser autofill regression', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    setupAuthStoreMock()
    mockPost.mockResolvedValue({ data: {} })
    mockGet.mockResolvedValue({ data: [] })
  })

  it('keeps the Sign in button enabled before any input event fires', () => {
    render(<LoginPage />)
    expect(screen.getByRole('button', { name: 'Sign in' })).toBeEnabled()
  })

  it('signs in with values present in the DOM but never typed (browser autofill)', async () => {
    const user = userEvent.setup()
    mockPost.mockImplementation((url: string) => {
      if (url === '/auth/signin') {
        return Promise.resolve({
          data: {
            token: 'jwt-token',
            id: 1,
            username: 'autofilluser',
            email: 'autofill@example.com',
            role: 'ROLE_OWNER',
          },
        })
      }
      if (url === '/organizations/1/select') {
        return Promise.resolve({
          data: {
            token: 'org-token',
            organizationId: 1,
            organizationName: 'Acme',
            role: 'ROLE_OWNER',
          },
        })
      }
      return Promise.reject(new Error(`unexpected POST ${url}`))
    })
    mockGet.mockResolvedValue({
      data: [
        { organizationId: 1, organizationName: 'Acme', role: 'ROLE_OWNER', isActive: true },
      ],
    })

    render(<LoginPage />)

    // Simulate browser autofill: the browser writes the values into the DOM
    // without dispatching input/change events, so no React state updates.
    const usernameInput = screen.getByLabelText('Username') as HTMLInputElement
    const passwordInput = screen.getByLabelText('Password') as HTMLInputElement
    usernameInput.value = 'autofilluser'
    passwordInput.value = 'AutofillPass123!'

    await user.click(screen.getByRole('button', { name: 'Sign in' }))

    await waitFor(() => {
      expect(mockPost).toHaveBeenCalledWith('/auth/signin', {
        username: 'autofilluser',
        password: 'AutofillPass123!',
      })
    })
    expect(mockLogin).toHaveBeenCalledWith('jwt-token', {
      id: 1,
      username: 'autofilluser',
      email: 'autofill@example.com',
      role: 'ROLE_OWNER',
    })
    await waitFor(() => {
      expect(mockNavigate).toHaveBeenCalledWith({ to: '/dashboard' })
    })
  })

  it('shows a validation error and skips the API when fields are empty', async () => {
    const user = userEvent.setup()
    render(<LoginPage />)

    await user.click(screen.getByRole('button', { name: 'Sign in' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Please enter your username and password'
    )
    expect(mockPost).not.toHaveBeenCalled()
  })
})
