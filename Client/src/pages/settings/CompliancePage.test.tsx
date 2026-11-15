import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { CompliancePage } from './CompliancePage'

vi.mock('@/components/AppShell', () => ({
  AppShell: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
}))
vi.mock('@/store/authStore')
vi.mock('@/lib/api', () => ({
  api: {
    get: vi.fn(),
    put: vi.fn(),
    post: vi.fn(),
  },
}))

import { useAuthStore } from '@/store/authStore'
import { api } from '@/lib/api'

const mockUseAuthStore = vi.mocked(useAuthStore)
const mockGet = vi.mocked(api.get)
const mockPut = vi.mocked(api.put)

function renderPage(role: string) {
  mockUseAuthStore.mockReturnValue({
    user: {
      id: 1,
      username: 'alice',
      email: 'alice@test.com',
      role,
      organizationId: 'org-abc',
    },
  } as ReturnType<typeof useAuthStore>)
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <CompliancePage />
    </QueryClientProvider>,
  )
}

describe('CompliancePage — responsibility start', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('renders responsibility start per type with locked badge', async () => {
    mockGet.mockImplementation((url: string) => {
      if (url === '/v1/compliance/responsibility-start') {
        return Promise.resolve({
          data: [
            {
              type: 'GSTR1',
              firstPeriodStart: null,
              setBy: null,
              setAt: null,
              locked: false,
              baselineRowCount: 0,
            },
            {
              type: 'TDS_PAYMENT',
              firstPeriodStart: '2025-07-01',
              setBy: 'alice',
              setAt: '2025-07-01T10:00:00Z',
              locked: true,
              baselineRowCount: 3,
            },
          ],
        })
      }
      return Promise.resolve({ data: [] })
    })

    renderPage('ROLE_ACCOUNTANT')

    await waitFor(() => {
      expect(screen.getByTestId('resp-TDS_PAYMENT')).toHaveTextContent('2025-07-01')
    })
    expect(screen.getByText('TDS payment')).toBeInTheDocument()
    expect(screen.getByText('Locked')).toBeInTheDocument()
    expect(screen.getByText('3 baseline periods')).toBeInTheDocument()
    expect(screen.getAllByText('Not set').length).toBeGreaterThan(0)
    const dateInput = screen.getByLabelText('TDS payment responsibility start date')
    expect(dateInput).toBeDisabled()
  })

  it('accountant saves a responsibility start', async () => {
    mockGet.mockImplementation((url: string) => {
      if (url === '/v1/compliance/responsibility-start') {
        return Promise.resolve({
          data: [
            {
              type: 'GSTR1',
              firstPeriodStart: null,
              setBy: null,
              setAt: null,
              locked: false,
              baselineRowCount: 0,
            },
          ],
        })
      }
      return Promise.resolve({ data: [] })
    })
    mockPut.mockRejectedValueOnce({
      response: { status: 409, data: { error: 'Responsibility start for GSTR1 is locked: a workflow already exists' } },
    })

    renderPage('ROLE_ACCOUNTANT')

    const user = userEvent.setup()
    const dateInput = await screen.findByLabelText('GSTR-1 responsibility start date')
    await user.type(dateInput, '2025-06-01')
    const saveButtons = screen.getAllByRole('button', { name: 'Save' })
    await user.click(saveButtons[0])

    await waitFor(() => {
      expect(mockPut).toHaveBeenCalledWith('/v1/compliance/responsibility-start', {
        type: 'GSTR1',
        firstPeriodStart: '2025-06-01',
      })
    })
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Responsibility start for GSTR1 is locked: a workflow already exists',
    )
  })

  it('operator sees read-only settings', async () => {
    mockGet.mockResolvedValue({ data: [] })

    renderPage('ROLE_OPERATOR')

    await screen.findByText('Responsibility start')
    expect(screen.queryByRole('button', { name: 'Save' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Add profile' })).not.toBeInTheDocument()
  })

  it('lists filer profile rows and adds one', async () => {
    mockGet.mockImplementation((url: string) => {
      if (url === '/v1/compliance/filer-profile') {
        return Promise.resolve({
          data: [
            {
              id: 'p1',
              frequency: 'MONTHLY',
              iffOptIn: false,
              einvoiceEnabled: false,
              effectiveFrom: '2024-04-01',
              createdBy: 'alice',
              createdAt: '2024-04-01T00:00:00Z',
            },
            {
              id: 'p2',
              frequency: 'QRMP',
              iffOptIn: true,
              einvoiceEnabled: false,
              effectiveFrom: '2025-04-01',
              createdBy: 'alice',
              createdAt: '2025-04-01T00:00:00Z',
            },
          ],
        })
      }
      return Promise.resolve({ data: [] })
    })
    mockPut.mockResolvedValue({ data: {} })

    renderPage('ROLE_ACCOUNTANT')

    await waitFor(() => {
      expect(screen.getByText('2024-04-01')).toBeInTheDocument()
      expect(screen.getByText('2025-04-01')).toBeInTheDocument()
    })

    const user = userEvent.setup()
    await user.selectOptions(screen.getByLabelText('Frequency'), 'QRMP')
    await user.click(screen.getByLabelText('IFF opt-in'))
    await user.type(screen.getByLabelText('Effective from'), '2025-07-01')
    await user.click(screen.getByRole('button', { name: 'Add profile' }))

    await waitFor(() => {
      expect(mockPut).toHaveBeenCalledWith('/v1/compliance/filer-profile', {
        frequency: 'QRMP',
        iffOptIn: true,
        einvoiceEnabled: false,
        effectiveFrom: '2025-07-01',
      })
    })
  })
})
