import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { CompliancePage } from './CompliancePage'

const MAPPING_VIEW = {
  complete: true,
  mastersJobId: null,
  blockers: [],
  ledgers: [
    {
      ledgerName: 'Output CGST',
      parentGroup: 'Duties & Taxes',
      saved: true,
      role: 'OUTPUT',
      head: 'CGST',
      suggestedRole: 'OUTPUT',
      suggestedHead: 'CGST',
      savedBy: 'alice',
      savedAt: '2026-01-01T00:00:00Z',
    },
  ],
}

const TDS_MAPPING_VIEW = {
  complete: false,
  mastersJobId: null,
  blockers: ['1 TDS ledgers not mapped: TDS Payable - Contractors'],
  receivableWarnings: [],
  ledgers: [
    {
      ledgerName: 'TDS Payable - Contractors',
      parentGroup: 'Duties & Taxes',
      saved: false,
      role: null,
      section: null,
      suggestedRole: 'TDS_PAYABLE',
      suggestedSection: '194C',
      area: 'PAYABLE',
      savedBy: null,
      savedAt: null,
    },
  ],
}

function fallbackData(url: string) {
  if (url === '/v1/gst/tax-ledger-mapping') return MAPPING_VIEW
  if (url === '/v1/tds/ledger-mapping') return TDS_MAPPING_VIEW
  return []
}

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
const mockPost = vi.mocked(api.post)

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

async function openTab(name: string) {
  const user = userEvent.setup()
  await user.click(await screen.findByRole('tab', { name }))
  return user
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
      if (url === '/v1/organizations/org-abc/tds-applicability') {
        return Promise.resolve({
          data: {
            years: [
              {
                financialYear: 2025,
                applicable: true,
                basis: 'DECLARED',
                needsAnswer: false,
                priorYearTurnover: null,
                turnoverNature: null,
              },
            ],
          },
        })
      }
      return Promise.resolve({ data: fallbackData(url) })
    })

    renderPage('ROLE_ACCOUNTANT')
    await openTab('Periods & baseline')

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
      return Promise.resolve({ data: fallbackData(url) })
    })
    mockPut.mockRejectedValueOnce({
      response: { status: 409, data: { error: 'Responsibility start for GSTR1 is locked: a workflow already exists' } },
    })

    renderPage('ROLE_ACCOUNTANT')

    const user = await openTab('Periods & baseline')
    const dateInput = await screen.findByLabelText('GSTR-1 responsibility start date')
    await user.type(dateInput, '2025-06-01')
    const respSection = screen.getByText('Responsibility start').closest('section') as HTMLElement
    const saveButtons = within(respSection).getAllByRole('button', { name: 'Save' })
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

    expect(screen.queryByRole('button', { name: 'Add profile' })).not.toBeInTheDocument()
    await openTab('Periods & baseline')
    await screen.findByText('Responsibility start')
    expect(screen.queryByRole('button', { name: 'Save' })).not.toBeInTheDocument()
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
              hsnDigits: 4,
              createdBy: 'alice',
              createdAt: '2024-04-01T00:00:00Z',
            },
            {
              id: 'p2',
              frequency: 'QRMP',
              iffOptIn: true,
              einvoiceEnabled: false,
              effectiveFrom: '2025-04-01',
              hsnDigits: 6,
              createdBy: 'alice',
              createdAt: '2025-04-01T00:00:00Z',
            },
          ],
        })
      }
      return Promise.resolve({ data: fallbackData(url) })
    })
    mockPut.mockResolvedValue({ data: {} })

    renderPage('ROLE_ACCOUNTANT')

    await waitFor(() => {
      expect(screen.getByText('2024-04-01')).toBeInTheDocument()
      expect(screen.getByText('2025-04-01')).toBeInTheDocument()
      expect(screen.getByText('HSN: 6 digits')).toBeInTheDocument()
    })

    const user = userEvent.setup()
    await user.selectOptions(screen.getByLabelText('Frequency'), 'QRMP')
    await user.click(screen.getByLabelText('IFF opt-in'))
    await user.selectOptions(screen.getByLabelText('HSN digits'), '6')
    await user.type(screen.getByLabelText('Effective from'), '2025-07-01')
    await user.click(screen.getByRole('button', { name: 'Add profile' }))

    await waitFor(() => {
      expect(mockPut).toHaveBeenCalledWith('/v1/compliance/filer-profile', {
        frequency: 'QRMP',
        iffOptIn: true,
        einvoiceEnabled: false,
        hsnDigits: 6,
        effectiveFrom: '2025-07-01',
      })
    })
  })
})

describe('CompliancePage — baseline', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  function mockBaseline(status: Record<string, unknown>, years: Record<string, unknown>[] = []) {
    mockGet.mockImplementation((url: string) => {
      if (url === '/v1/compliance/baseline') return Promise.resolve({ data: status })
      if (url === '/v1/compliance/financial-years') return Promise.resolve({ data: years })
      return Promise.resolve({ data: fallbackData(url) })
    })
  }

  it('baseline shows range, imported-data slot and confirms', async () => {
    mockBaseline({
      confirmed: false,
      rangeStart: '2025-04-01',
      rangeEnd: '2025-06-30',
      types: [{ type: 'TDS_PAYMENT', unconfirmedRows: 3, confirmedRows: 0, latestEnd: '2025-06-30' }],
      blockers: [],
      importedFiledData: 'not available',
      discrepancyCount: null,
    })
    mockPost.mockResolvedValue({ data: { confirmed: true } })

    renderPage('ROLE_ACCOUNTANT')
    await openTab('Periods & baseline')

    await screen.findByText('Baseline 2025-04-01 to 2025-06-30')
    expect(screen.getByText('Imported filed data: not available')).toBeInTheDocument()

    const user = userEvent.setup()
    await user.click(screen.getByRole('button', { name: 'Confirm baseline' }))
    await waitFor(() => {
      expect(mockPost).toHaveBeenCalledWith('/v1/compliance/baseline/confirm', {})
    })
  })

  it('baseline blockers disable confirm', async () => {
    mockBaseline({
      confirmed: false,
      rangeStart: '2025-04-01',
      rangeEnd: '2025-06-30',
      types: [],
      blockers: ['Open tamper finding T-1'],
      importedFiledData: 'not available',
      discrepancyCount: null,
    })

    renderPage('ROLE_ACCOUNTANT')
    await openTab('Periods & baseline')

    expect(await screen.findByText('Open tamper finding T-1')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Confirm baseline' })).toBeDisabled()
  })

  it('baselineCardDistinguishesZeroFromNotAvailable', async () => {
    mockBaseline({
      confirmed: false,
      rangeStart: null,
      rangeEnd: null,
      types: [],
      blockers: [],
      importedFiledData: 'not available',
      discrepancyCount: 0,
    })

    const { unmount } = renderPage('ROLE_ACCOUNTANT')
    await openTab('Periods & baseline')
    expect(await screen.findByTestId('discrepancy-count')).toHaveTextContent(
      'Differences to explain (informational): 0',
    )
    expect(screen.getByRole('button', { name: 'Confirm baseline' })).toBeEnabled()
    expect(screen.getByText('Baseline periods')).toBeInTheDocument()
    unmount()

    mockBaseline({
      confirmed: false,
      rangeStart: null,
      rangeEnd: null,
      types: [],
      blockers: [],
      importedFiledData: 'not available',
      discrepancyCount: null,
    })
    const second = renderPage('ROLE_ACCOUNTANT')
    await openTab('Periods & baseline')
    expect(await screen.findByTestId('discrepancy-count')).toHaveTextContent(
      'Differences to explain: Not available (import filed returns and map tax ledgers)',
    )
    expect(screen.getByRole('button', { name: 'Confirm baseline' })).toBeEnabled()
    second.unmount()

    mockBaseline({
      confirmed: false,
      rangeStart: null,
      rangeEnd: null,
      types: [],
      blockers: [],
      importedFiledData: 'not available',
      discrepancyCount: 3,
    })
    renderPage('ROLE_ACCOUNTANT')
    await openTab('Periods & baseline')
    expect(await screen.findByTestId('discrepancy-count')).toHaveTextContent(
      'Differences to explain (informational): 3',
    )
    expect(screen.getByRole('button', { name: 'Confirm baseline' })).toBeEnabled()
  })

  it('baseline confirmed shows confirmed state', async () => {
    mockBaseline({
      confirmed: true,
      rangeStart: null,
      rangeEnd: null,
      types: [],
      blockers: [],
      importedFiledData: 'not available',
      discrepancyCount: null,
    })

    renderPage('ROLE_ACCOUNTANT')
    await openTab('Periods & baseline')

    expect(await screen.findByText('Baseline confirmed')).toBeInTheDocument()
    expect(
      screen.queryByRole('button', { name: 'Confirm baseline' }),
    ).not.toBeInTheDocument()
  })
})

describe('CompliancePage — financial years', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  function mockYears(years: Record<string, unknown>[]) {
    mockGet.mockImplementation((url: string) => {
      if (url === '/v1/compliance/financial-years') return Promise.resolve({ data: years })
      return Promise.resolve({ data: fallbackData(url) })
    })
  }

  const openYear = {
    financialYear: 2024,
    start: '2024-04-01',
    end: '2025-03-31',
    closed: false,
    closeType: null,
    itrAckNo: null,
    itrAckDate: null,
    note: null,
    closable: false,
    openItems: ['TDS_PAYMENT 2024-07: not started'],
    blockers: [],
  }

  it('financial year close form submits handled-elsewhere and shows warnings', async () => {
    mockYears([openYear])
    mockPost.mockResolvedValue({
      data: { year: { ...openYear, closed: true }, warnings: ['TDS_PAYMENT 2024-07: not started'] },
    })

    renderPage('ROLE_ACCOUNTANT')
    await openTab('Financial years')

    await screen.findByText('TDS_PAYMENT 2024-07: not started')
    const itrOption = screen.getByRole('option', { name: 'ITR filed' }) as HTMLOptionElement
    expect(itrOption.disabled).toBe(true)
    expect(screen.getByText('Every period must be filed')).toBeInTheDocument()

    const user = userEvent.setup()
    await user.type(screen.getByLabelText('Note'), 'Prior CA')
    await user.click(screen.getByRole('button', { name: 'Close year' }))

    await waitFor(() => {
      expect(mockPost).toHaveBeenCalledWith('/v1/compliance/financial-years/2024/close', {
        closeType: 'HANDLED_ELSEWHERE',
        note: 'Prior CA',
      })
    })
    expect(await screen.findByLabelText('Warnings')).toHaveTextContent(
      'TDS_PAYMENT 2024-07: not started',
    )
  })

  it('close type defaults to handled elsewhere', async () => {
    mockYears([openYear])

    renderPage('ROLE_ACCOUNTANT')
    await openTab('Financial years')

    const select = await screen.findByLabelText('Close type FY 2024') as HTMLSelectElement
    expect(select.value).toBe('HANDLED_ELSEWHERE')
  })

  it('ITR filed requires acknowledgement fields', async () => {
    mockYears([{ ...openYear, openItems: [], closable: true }])
    mockPost.mockResolvedValue({
      data: { year: { ...openYear, closed: true }, warnings: [] },
    })

    renderPage('ROLE_ACCOUNTANT')
    await openTab('Financial years')

    const select = await screen.findByLabelText('Close type FY 2024')
    const user = userEvent.setup()
    await user.selectOptions(select, 'ITR_FILED')

    expect(screen.getByLabelText('Acknowledgement no.')).toBeInTheDocument()
    expect(screen.getByLabelText('Acknowledgement date')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Close year' })).toBeDisabled()

    await user.type(screen.getByLabelText('Acknowledgement no.'), 'ACK123')
    await user.type(screen.getByLabelText('Acknowledgement date'), '2025-10-15')
    await user.click(screen.getByRole('button', { name: 'Close year' }))

    await waitFor(() => {
      expect(mockPost).toHaveBeenCalledWith('/v1/compliance/financial-years/2024/close', {
        closeType: 'ITR_FILED',
        itrAckNo: 'ACK123',
        itrAckDate: '2025-10-15',
      })
    })
  })
})

describe('CompliancePage — TDS ledger mapping', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('renders the TDS ledger mapping section and saves it', async () => {
    mockGet.mockImplementation((url: string) => {
      if (url === '/v1/organizations/org-abc/tds-applicability') {
        return Promise.resolve({
          data: {
            years: [
              {
                financialYear: 2025,
                applicable: true,
                basis: 'DECLARED',
                needsAnswer: false,
                priorYearTurnover: null,
                turnoverNature: null,
              },
            ],
          },
        })
      }
      return Promise.resolve({ data: fallbackData(url) })
    })
    mockPut.mockResolvedValue({ data: {} })

    renderPage('ROLE_ACCOUNTANT')
    await openTab('TDS')

    expect(await screen.findByText('TDS ledgers')).toBeInTheDocument()
    expect(
      screen.getByText('1 TDS ledgers not mapped: TDS Payable - Contractors'),
    ).toBeInTheDocument()
    expect(screen.getByText('TDS Payable - Contractors')).toBeInTheDocument()
    expect(screen.getByText('TDS receivable ledgers')).toBeInTheDocument()

    const user = userEvent.setup()
    const tdsSection = screen.getByText('TDS ledgers').closest('section') as HTMLElement
    await user.click(within(tdsSection).getByRole('button', { name: 'Save' }))
    await waitFor(() => {
      expect(mockPut).toHaveBeenCalledWith('/v1/tds/ledger-mapping', {
        items: [{ ledgerName: 'TDS Payable - Contractors', role: 'TDS_PAYABLE', section: '194C' }],
      })
    })
  })
})
