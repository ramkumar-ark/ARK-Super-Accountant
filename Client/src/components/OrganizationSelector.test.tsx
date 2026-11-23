import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { OrganizationSelector } from './OrganizationSelector'

const mockNavigate = vi.fn()
vi.mock('@tanstack/react-router', () => ({
  useNavigate: () => mockNavigate,
}))
vi.mock('@/store/authStore')
vi.mock('@/lib/api', () => ({
  api: { post: vi.fn() },
}))

import { useAuthStore } from '@/store/authStore'

const mockUseAuthStore = vi.mocked(useAuthStore)

const TWO_ORGS = [
  { organizationId: 'org-1', organizationName: 'Acme', role: 'ROLE_OWNER', isActive: true },
  { organizationId: 'org-2', organizationName: 'Beta Corp', role: 'ROLE_OWNER', isActive: false },
]

const ONE_ORG = [
  { organizationId: 'org-1', organizationName: 'Acme', role: 'ROLE_OWNER', isActive: true },
]

beforeEach(() => {
  vi.clearAllMocks()
})

describe('OrganizationSelector — multi-org', () => {
  it('shows New Organization button in dropdown for ROLE_OWNER', async () => {
    const user = userEvent.setup()
    mockUseAuthStore.mockReturnValue({
      user: { id: 1, username: 'alice', email: 'a@a.com', role: 'ROLE_OWNER', organizationId: 'org-1', organizationName: 'Acme' },
      organizations: TWO_ORGS,
      switchOrganization: vi.fn(),
    } as unknown as ReturnType<typeof useAuthStore>)

    render(<OrganizationSelector />)
    await user.click(screen.getByRole('button', { name: /Acme/i }))

    const newOrgButton = screen.getByText('+ New Organization')
    expect(newOrgButton).toBeInTheDocument()
    await user.click(newOrgButton)
    expect(mockNavigate).toHaveBeenCalledWith({ to: '/organization/new' })
  })

  it('shows New Organization button in dropdown for ROLE_ACCOUNTANT', async () => {
    const user = userEvent.setup()
    mockUseAuthStore.mockReturnValue({
      user: { id: 2, username: 'charlie', email: 'c@c.com', role: 'ROLE_ACCOUNTANT', organizationId: 'org-1', organizationName: 'Acme' },
      organizations: TWO_ORGS,
      switchOrganization: vi.fn(),
    } as unknown as ReturnType<typeof useAuthStore>)

    render(<OrganizationSelector />)
    await user.click(screen.getByRole('button', { name: /Acme/i }))

    const newOrgButton = screen.getByText('+ New Organization')
    expect(newOrgButton).toBeInTheDocument()
    await user.click(newOrgButton)
    expect(mockNavigate).toHaveBeenCalledWith({ to: '/organization/new' })
  })

  it('hides New Organization button in dropdown for role that is neither Owner nor Accountant', async () => {
    const user = userEvent.setup()
    mockUseAuthStore.mockReturnValue({
      user: { id: 3, username: 'bob', email: 'b@b.com', role: 'ROLE_OPERATOR', organizationId: 'org-1', organizationName: 'Acme' },
      organizations: TWO_ORGS,
      switchOrganization: vi.fn(),
    } as unknown as ReturnType<typeof useAuthStore>)

    render(<OrganizationSelector />)
    await user.click(screen.getByRole('button', { name: /Acme/i }))

    expect(screen.queryByText('+ New Organization')).not.toBeInTheDocument()
  })
})

describe('OrganizationSelector — single org', () => {
  it('renders plain span (no button) for role that is neither Owner nor Accountant with one org', () => {
    mockUseAuthStore.mockReturnValue({
      user: { id: 3, username: 'bob', email: 'b@b.com', role: 'ROLE_OPERATOR', organizationId: 'org-1', organizationName: 'Acme' },
      organizations: ONE_ORG,
      switchOrganization: vi.fn(),
    } as unknown as ReturnType<typeof useAuthStore>)

    render(<OrganizationSelector />)

    expect(screen.getByText('Acme')).toBeInTheDocument()
    expect(screen.queryByRole('button')).not.toBeInTheDocument()
  })

  it('renders a dropdown with New Organization for single-org OWNER', async () => {
    const user = userEvent.setup()
    mockUseAuthStore.mockReturnValue({
      user: { id: 1, username: 'alice', email: 'a@a.com', role: 'ROLE_OWNER', organizationId: 'org-1', organizationName: 'Acme' },
      organizations: ONE_ORG,
      switchOrganization: vi.fn(),
    } as unknown as ReturnType<typeof useAuthStore>)

    render(<OrganizationSelector />)
    await user.click(screen.getByRole('button', { name: /Acme/i }))

    const newOrgButton = screen.getByText('+ New Organization')
    expect(newOrgButton).toBeInTheDocument()
    await user.click(newOrgButton)
    expect(mockNavigate).toHaveBeenCalledWith({ to: '/organization/new' })
  })

  it('renders a dropdown with New Organization for single-org ACCOUNTANT', async () => {
    const user = userEvent.setup()
    mockUseAuthStore.mockReturnValue({
      user: { id: 2, username: 'charlie', email: 'c@c.com', role: 'ROLE_ACCOUNTANT', organizationId: 'org-1', organizationName: 'Acme' },
      organizations: ONE_ORG,
      switchOrganization: vi.fn(),
    } as unknown as ReturnType<typeof useAuthStore>)

    render(<OrganizationSelector />)
    await user.click(screen.getByRole('button', { name: /Acme/i }))

    const newOrgButton = screen.getByText('+ New Organization')
    expect(newOrgButton).toBeInTheDocument()
    await user.click(newOrgButton)
    expect(mockNavigate).toHaveBeenCalledWith({ to: '/organization/new' })
  })
})
