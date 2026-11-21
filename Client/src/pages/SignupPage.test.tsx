import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { SignupPage } from './SignupPage'

const mockNavigate = vi.fn()

vi.mock('@tanstack/react-router', () => ({
  useNavigate: () => mockNavigate,
  Link: ({ children, to }: { children: React.ReactNode; to: string }) => (
    <a href={to}>{children}</a>
  ),
}))
vi.mock('@/lib/api', () => ({
  api: {
    get: vi.fn(),
    post: vi.fn(),
  },
}))
vi.mock('@/store/authStore', () => ({
  useAuthStore: vi.fn(),
}))

import { api } from '@/lib/api'
import { useAuthStore } from '@/store/authStore'

const mockPost = vi.mocked(api.post)
const mockUseAuthStore = vi.mocked(useAuthStore)

function setupAuthStoreMock() {
  const state = {
    isAuthenticated: false,
    switchOrganization: vi.fn(),
    setOrganizations: vi.fn(),
  }
  mockUseAuthStore.mockImplementation(
    ((selector: (s: typeof state) => unknown) => selector(state)) as typeof useAuthStore
  )
}

async function fillValidForm(user: ReturnType<typeof userEvent.setup>) {
  await user.type(screen.getByLabelText('Username'), 'validuser')
  await user.type(screen.getByLabelText('Email'), 'user@example.com')
  await user.type(screen.getByLabelText('Password'), 'secret1')
}

describe('SignupPage validation messages', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    setupAuthStoreMock()
    window.history.pushState({}, '', '/signup')
  })

  it('shows no field errors before interaction', () => {
    render(<SignupPage />)
    expect(screen.queryAllByRole('alert')).toHaveLength(0)
    expect(screen.getByRole('button', { name: 'Create account' })).toBeEnabled()
  })

  it('shows an inline error after blurring an invalid username', async () => {
    const user = userEvent.setup()
    render(<SignupPage />)
    const username = screen.getByLabelText('Username')
    await user.type(username, 'ab')
    await user.tab()
    expect(
      await screen.findByText('Username must be 3–20 characters.')
    ).toBeInTheDocument()
    expect(username).toHaveAttribute('aria-invalid', 'true')
  })

  it('shows an inline error after blurring an invalid email', async () => {
    const user = userEvent.setup()
    render(<SignupPage />)
    await user.type(screen.getByLabelText('Email'), 'notanemail')
    await user.tab()
    expect(
      await screen.findByText('Enter a valid email address, like you@company.com.')
    ).toBeInTheDocument()
  })

  it('shows an inline error after blurring a short password', async () => {
    const user = userEvent.setup()
    render(<SignupPage />)
    await user.type(screen.getByLabelText('Password'), '123')
    await user.tab()
    expect(
      await screen.findByText('Password must be at least 6 characters.')
    ).toBeInTheDocument()
  })

  it('states password requirements up front before typing', () => {
    render(<SignupPage />)
    expect(screen.getByText('Use 6–40 characters.')).toBeInTheDocument()
  })

  it('shows errors for every invalid field when the user submits', async () => {
    const user = userEvent.setup()
    render(<SignupPage />)
    await user.click(screen.getByRole('button', { name: 'Create account' }))
    expect(await screen.findByText('Username is required.')).toBeInTheDocument()
    expect(screen.getByText('Email is required.')).toBeInTheDocument()
    expect(screen.getByText('Password is required.')).toBeInTheDocument()
    expect(mockPost).not.toHaveBeenCalled()
  })

  it('clears a field error once the field becomes valid', async () => {
    const user = userEvent.setup()
    render(<SignupPage />)
    await user.click(screen.getByRole('button', { name: 'Create account' }))
    expect(await screen.findByText('Username is required.')).toBeInTheDocument()
    await user.type(screen.getByLabelText('Username'), 'validuser')
    await user.type(screen.getByLabelText('Email'), 'user@example.com')
    await user.type(screen.getByLabelText('Password'), 'secret1')
    expect(screen.queryByText('Username is required.')).not.toBeInTheDocument()
    expect(screen.queryByText('Email is required.')).not.toBeInTheDocument()
    expect(screen.queryByText('Password is required.')).not.toBeInTheDocument()
  })

  it('does not call the API while the form is invalid', async () => {
    const user = userEvent.setup()
    render(<SignupPage />)
    await user.type(screen.getByLabelText('Username'), 'ab')
    await user.click(screen.getByRole('button', { name: 'Create account' }))
    expect(mockPost).not.toHaveBeenCalled()
  })

  it('submits a valid form', async () => {
    const user = userEvent.setup()
    mockPost.mockResolvedValue({ data: {} })
    render(<SignupPage />)
    await fillValidForm(user)
    await user.click(screen.getByRole('button', { name: 'Create account' }))
    await waitFor(() => expect(mockPost).toHaveBeenCalledTimes(1))
    expect(mockPost.mock.calls[0][0]).toBe('/auth/signup')
    await waitFor(() => expect(mockNavigate).toHaveBeenCalledWith({ to: '/login' }))
  })

  it('surfaces server-side signup errors such as username taken', async () => {
    const user = userEvent.setup()
    const serverError = Object.assign(new Error('Request failed'), {
      response: { data: { message: 'Error: Username is already taken!' } },
    })
    mockPost.mockRejectedValue(serverError)
    render(<SignupPage />)
    await fillValidForm(user)
    await user.click(screen.getByRole('button', { name: 'Create account' }))
    expect(
      await screen.findByText('Error: Username is already taken!')
    ).toBeInTheDocument()
  })
})
