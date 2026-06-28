import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { TeamPage } from './TeamPage'

vi.mock('@/components/AppShell', () => ({
  AppShell: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
}))
vi.mock('@/components/InviteTokenDisplay', () => ({
  InviteTokenDisplay: () => <div data-testid="invite-token-display" />,
}))
vi.mock('@/store/authStore')
vi.mock('@/lib/api', () => ({
  api: {
    get: vi.fn(),
    post: vi.fn(),
  },
}))

import { useAuthStore } from '@/store/authStore'
import { api } from '@/lib/api'

const mockUseAuthStore = vi.mocked(useAuthStore)
const mockGet = vi.mocked(api.get)
const mockPost = vi.mocked(api.post)

describe('TeamPage — member list', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('renders Members and Invite tabs for OWNER', () => {
    mockUseAuthStore.mockReturnValue({
      user: {
        id: 1,
        username: 'alice',
        email: 'alice@test.com',
        role: 'ROLE_OWNER',
        organizationId: 'org-abc',
      },
    } as ReturnType<typeof useAuthStore>)
    mockGet.mockResolvedValue({ data: [] })

    render(<TeamPage />)

    expect(screen.getByRole('tab', { name: 'Members' })).toBeInTheDocument()
    expect(screen.getByRole('tab', { name: 'Invite' })).toBeInTheDocument()
  })

  it('Members tab is active by default and shows fetched members', async () => {
    mockUseAuthStore.mockReturnValue({
      user: {
        id: 1,
        username: 'alice',
        email: 'alice@test.com',
        role: 'ROLE_OWNER',
        organizationId: 'org-abc',
      },
    } as ReturnType<typeof useAuthStore>)
    mockGet.mockResolvedValue({
      data: [
        { username: 'alice', email: 'alice@test.com', role: 'ROLE_OWNER' },
        { username: 'bob', email: 'bob@test.com', role: 'ROLE_ACCOUNTANT' },
      ],
    })

    render(<TeamPage />)

    await waitFor(() => {
      expect(mockGet).toHaveBeenCalledWith('/organizations/org-abc/members')
      expect(screen.getByText('alice')).toBeInTheDocument()
      expect(screen.getByText('bob')).toBeInTheDocument()
      expect(screen.getByText('alice@test.com')).toBeInTheDocument()
      expect(screen.getByText('bob@test.com')).toBeInTheDocument()
      expect(screen.getByText('Owner')).toBeInTheDocument()
      expect(screen.getByText('Accountant')).toBeInTheDocument()
    })
  })

  it('shows empty state when no members returned', async () => {
    mockUseAuthStore.mockReturnValue({
      user: {
        id: 1,
        username: 'alice',
        email: 'alice@test.com',
        role: 'ROLE_OWNER',
        organizationId: 'org-abc',
      },
    } as ReturnType<typeof useAuthStore>)
    mockGet.mockResolvedValue({ data: [] })

    render(<TeamPage />)

    await waitFor(() => {
      expect(screen.getByText('No other members yet.')).toBeInTheDocument()
    })
  })

  it('shows error message when fetch fails', async () => {
    mockUseAuthStore.mockReturnValue({
      user: {
        id: 1,
        username: 'alice',
        email: 'alice@test.com',
        role: 'ROLE_OWNER',
        organizationId: 'org-abc',
      },
    } as ReturnType<typeof useAuthStore>)
    mockGet.mockRejectedValue(new Error('Network error'))

    render(<TeamPage />)

    await waitFor(() => {
      expect(screen.getByRole('alert')).toHaveTextContent(/Could not load members/i)
    })
  })

  it('hides tabs and shows restricted message for non-privileged user', () => {
    mockUseAuthStore.mockReturnValue({
      user: {
        id: 2,
        username: 'op',
        email: 'op@test.com',
        role: 'ROLE_OPERATOR',
        organizationId: 'org-abc',
      },
    } as ReturnType<typeof useAuthStore>)

    render(<TeamPage />)

    expect(screen.queryByRole('tab')).not.toBeInTheDocument()
    expect(
      screen.getByText(/Only Owners and Accountants can invite new members/i)
    ).toBeInTheDocument()
  })

  it('switches to Invite tab on click', async () => {
    const user = userEvent.setup()
    mockUseAuthStore.mockReturnValue({
      user: {
        id: 1,
        username: 'alice',
        email: 'alice@test.com',
        role: 'ROLE_OWNER',
        organizationId: 'org-abc',
      },
    } as ReturnType<typeof useAuthStore>)
    mockGet.mockResolvedValue({ data: [] })

    render(<TeamPage />)

    await user.click(screen.getByRole('tab', { name: 'Invite' }))

    expect(screen.getByRole('tab', { name: 'Invite' })).toHaveAttribute('aria-selected', 'true')
    expect(screen.getByLabelText('Role')).toBeInTheDocument()
  })
})
