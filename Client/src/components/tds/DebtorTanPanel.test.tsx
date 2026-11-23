import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { DebtorTanPanel } from './DebtorTanPanel'
import { useAuthStore } from '@/store/authStore'

vi.mock('@/lib/api', () => ({
  api: { get: vi.fn(), put: vi.fn(), delete: vi.fn() },
}))

import { api } from '@/lib/api'
import type { DebtorTanGap, DebtorTanItem } from '@/lib/tdsApi'

const mockGet = vi.mocked(api.get)
const mockPut = vi.mocked(api.put)
const mockDelete = vi.mocked(api.delete)

const CONFIGURED = {
  ledgerName: 'Acme Corp',
  ledgerKey: 'acme corp',
  tallyGuid: 'guid-1',
  tan: 'BLRA12345C',
  sourceFindingId: null,
  updatedBy: 'alice',
  updatedAt: '2025-05-01T10:00:00Z',
  inLatestUpload: true,
}

const MISSING = {
  ledgerName: 'Beta Traders',
  ledgerKey: 'beta traders',
  tallyGuid: 'guid-2',
  tan: null,
  sourceFindingId: null,
  updatedBy: null,
  updatedAt: null,
  inLatestUpload: true,
}

function renderPanel() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <DebtorTanPanel />
    </QueryClientProvider>,
  )
}

function setRole(role: string) {
  useAuthStore.setState({
    user: { id: 1, username: 'user', email: 'user@example.com', role },
    isAuthenticated: true,
  })
}

function mockEndpoints(items: DebtorTanItem[], gaps: DebtorTanGap[] = []) {
  mockGet.mockImplementation((url: string) => {
    if (url === '/v1/tds/debtor-tans/missing') {
      return Promise.resolve({ data: gaps })
    }
    return Promise.resolve({ data: items })
  })
}

describe('DebtorTanPanel', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    setRole('ROLE_ACCOUNTANT')
  })

  it('lists debtors with configured and missing TAN status', async () => {
    mockEndpoints([CONFIGURED, MISSING])

    renderPanel()

    expect(await screen.findByText('Acme Corp')).toBeInTheDocument()
    expect(screen.getByText('BLRA12345C')).toBeInTheDocument()
    expect(screen.getByText('Configured')).toBeInTheDocument()
    expect(screen.getByText('Beta Traders')).toBeInTheDocument()
    expect(screen.getByText('Missing')).toBeInTheDocument()
    expect(mockGet).toHaveBeenCalledWith('/v1/tds/debtor-tans')
  })

  it('saves a normalized TAN for a missing debtor', async () => {
    mockEndpoints([MISSING])
    mockPut.mockResolvedValue({ data: {} })
    const user = userEvent.setup()

    renderPanel()
    await user.click(await screen.findByRole('button', { name: 'Add TAN' }))
    await user.type(screen.getByLabelText('TAN for Beta Traders'), 'blra12345c')
    await user.click(screen.getByRole('button', { name: 'Save' }))

    await waitFor(() =>
      expect(mockPut).toHaveBeenCalledWith('/v1/tds/debtor-tans', {
        ledgerName: 'Beta Traders',
        tan: 'BLRA12345C',
        tallyGuid: 'guid-2',
      }),
    )
  })

  it('rejects a malformed TAN without calling the API', async () => {
    mockEndpoints([MISSING])
    const user = userEvent.setup()

    renderPanel()
    await user.click(await screen.findByRole('button', { name: 'Add TAN' }))
    await user.type(screen.getByLabelText('TAN for Beta Traders'), 'bad')
    await user.click(screen.getByRole('button', { name: 'Save' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Enter a valid 10-character TAN (format AAAA99999A, e.g. BLRA12345C).',
    )
    expect(mockPut).not.toHaveBeenCalled()
  })

  it('clears a configured TAN', async () => {
    mockEndpoints([CONFIGURED])
    mockDelete.mockResolvedValue({ data: {} })
    const user = userEvent.setup()

    renderPanel()
    await user.click(await screen.findByRole('button', { name: 'Clear' }))

    await waitFor(() =>
      expect(mockDelete).toHaveBeenCalledWith('/v1/tds/debtor-tans', {
        params: { ledgerName: 'Acme Corp' },
      }),
    )
  })

  it('hides edit controls from operators', async () => {
    setRole('ROLE_OPERATOR')
    mockEndpoints([CONFIGURED, MISSING])

    renderPanel()

    await screen.findByText('Acme Corp')
    expect(screen.queryByRole('button', { name: 'Edit' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Add TAN' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Clear' })).not.toBeInTheDocument()
  })

  it('shows missing TAN gaps from day-book vouchers above the stored TANs', async () => {
    mockEndpoints([CONFIGURED, MISSING], [
      {
        debtorLedgerName: 'Gamma Traders',
        receivableLedgerName: 'TDS Receivable 194J',
        voucherId: 'v-1',
        voucherNumber: 'J-7',
        voucherDate: '2026-05-10',
        deducted: 1000.0,
      },
    ])
    const user = userEvent.setup()

    renderPanel()

    expect(await screen.findByText('Gamma Traders')).toBeInTheDocument()
    expect(screen.getByText(/J-7/)).toBeInTheDocument()
    expect(screen.getByText(/TDS Receivable 194J/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Add TAN for Gamma Traders' })).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Add TAN for Gamma Traders' }))
    await user.type(screen.getByLabelText('TAN for Gamma Traders'), 'blra12345c')
    await user.click(screen.getByRole('button', { name: 'Save' }))

    await waitFor(() =>
      expect(mockPut).toHaveBeenCalledWith('/v1/tds/debtor-tans', {
        ledgerName: 'Gamma Traders',
        tan: 'BLRA12345C',
        tallyGuid: null,
      }),
    )
  })

  it('filters the table by customer name', async () => {
    mockEndpoints([CONFIGURED, MISSING])
    const user = userEvent.setup()

    renderPanel()
    await screen.findByText('Acme Corp')
    await user.type(screen.getByRole('searchbox', { name: 'Search debtors' }), 'beta')

    expect(screen.queryByText('Acme Corp')).not.toBeInTheDocument()
    expect(screen.getByText('Beta Traders')).toBeInTheDocument()
  })
})
