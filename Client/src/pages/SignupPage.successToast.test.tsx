import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { createMemoryHistory } from '@tanstack/react-router'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { RouterProvider } from '@tanstack/react-router'
import { createAppRouter } from '@/router'
import { ToastHost } from '@/components/toast/ToastHost'
import { toast } from '@/components/toast/toastStore'
import { useAuthStore } from '@/store/authStore'
import { api } from '@/lib/api'

vi.mock('@/lib/api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/lib/api')>()
  return {
    ...actual,
    api: { get: vi.fn(), post: vi.fn(), put: vi.fn(), patch: vi.fn(), delete: vi.fn() },
  }
})

const mockPost = vi.mocked(api.post)

function renderSignup() {
  const router = createAppRouter(createMemoryHistory({ initialEntries: ['/signup'] }))
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
      <ToastHost />
    </QueryClientProvider>,
  )
  return router
}

function fillValidSignupForm() {
  fireEvent.change(screen.getByLabelText('Username'), { target: { value: 'newuser' } })
  fireEvent.change(screen.getByLabelText('Email'), { target: { value: 'newuser@example.com' } })
  fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'secret1' } })
}

describe('SignupPage success notification', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    toast.dismissAll()
    useAuthStore.setState({
      token: null,
      user: null,
      organizations: [],
      isAuthenticated: false,
    })
  })

  it('shows a success toast and lands on the login page after account creation', async () => {
    mockPost.mockResolvedValue({ data: {} })
    const router = renderSignup()

    expect(await screen.findByRole('heading', { name: 'Super Accountant' })).toBeInTheDocument()
    fillValidSignupForm()
    fireEvent.click(screen.getByRole('button', { name: /create account/i }))

    expect(
      await screen.findByText(
        'Account created successfully. Please sign in with your new account.',
      ),
    ).toBeInTheDocument()
    await waitFor(() => expect(router.state.location.pathname).toBe('/login'))
    expect(mockPost).toHaveBeenCalledWith(
      '/auth/signup',
      expect.objectContaining({ username: 'newuser' }),
    )
  })

  it('does not show the success toast when registration fails', async () => {
    mockPost.mockRejectedValue(new Error('Registration failed'))
    renderSignup()

    expect(await screen.findByRole('heading', { name: 'Super Accountant' })).toBeInTheDocument()
    fillValidSignupForm()
    fireEvent.click(screen.getByRole('button', { name: /create account/i }))

    await waitFor(() =>
      expect(screen.getByText(/Registration failed|Please try again/)).toBeInTheDocument(),
    )
    expect(
      screen.queryByText('Account created successfully. Please sign in with your new account.'),
    ).not.toBeInTheDocument()
  })
})
