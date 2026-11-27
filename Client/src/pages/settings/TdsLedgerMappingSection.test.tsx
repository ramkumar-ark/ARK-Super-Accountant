import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { TdsLedgerMappingSection } from './TdsLedgerMappingSection'

vi.mock('@/store/authStore')
vi.mock('@/hooks/useTdsApplicability')
vi.mock('@/lib/api', () => ({
  api: {
    get: vi.fn(),
    put: vi.fn(),
  },
}))

import { useAuthStore } from '@/store/authStore'
import { useTdsApplicability } from '@/hooks/useTdsApplicability'
import { api } from '@/lib/api'
import { TDS_SECTIONS_FIXTURE } from '@/test/tdsSectionsFixture'

const mockUseAuthStore = vi.mocked(useAuthStore)
const mockUseTdsApplicability = vi.mocked(useTdsApplicability)
const mockGet = vi.mocked(api.get)
const mockPut = vi.mocked(api.put)

function renderSection(role: string, anyTdsOn = true) {
  mockUseAuthStore.mockReturnValue({
    user: {
      id: 1,
      username: 'alice',
      email: 'alice@test.com',
      role,
      organizationId: 'org-abc',
    },
  } as ReturnType<typeof useAuthStore>)
  mockUseTdsApplicability.mockReturnValue({ anyTdsOn } as ReturnType<
    typeof useTdsApplicability
  >)
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <TdsLedgerMappingSection />
    </QueryClientProvider>,
  )
}

const mappingView = {
  complete: false,
  mastersJobId: null,
  blockers: ['2 TDS ledgers not mapped: TDS Payable - Contractors, TDS Payable - Unknown'],
  receivableWarnings: [
    '1 TDS receivable ledgers not mapped - receivable checks skip them until mapped',
  ],
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
    {
      ledgerName: 'TDS Payable - Unknown',
      parentGroup: 'Duties & Taxes',
      saved: false,
      role: null,
      section: null,
      suggestedRole: null,
      suggestedSection: null,
      area: 'PAYABLE',
      savedBy: null,
      savedAt: null,
    },
    {
      ledgerName: 'TDS Receivable - Customers',
      parentGroup: 'Current Assets',
      saved: false,
      role: null,
      section: null,
      suggestedRole: 'TDS_RECEIVABLE',
      suggestedSection: null,
      area: 'RECEIVABLE',
      savedBy: null,
      savedAt: null,
    },
  ],
}

function mockMapping(view: Record<string, unknown>) {
  mockGet.mockImplementation((url: string) => {
    if (url === '/v1/tds/ledger-mapping') return Promise.resolve({ data: view })
    if (url === '/tds/sections') return Promise.resolve({ data: TDS_SECTIONS_FIXTURE })
    return Promise.resolve({ data: {} })
  })
}

describe('TdsLedgerMappingSection', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockMapping(mappingView)
    mockPut.mockResolvedValue({ data: {} })
  })

  it('suggestionsAreMarkedUnsavedUntilSave', async () => {
    renderSection('ROLE_ACCOUNTANT')

    expect(await screen.findAllByText('Suggested, not saved')).toHaveLength(3)
    expect(screen.getByText('Incomplete')).toBeInTheDocument()
    expect(
      screen.getByText('2 TDS ledgers not mapped: TDS Payable - Contractors, TDS Payable - Unknown'),
    ).toBeInTheDocument()
    expect(screen.getByText('TDS payable ledgers')).toBeInTheDocument()
    expect(screen.getByText('TDS receivable ledgers')).toBeInTheDocument()
    expect(screen.getByLabelText('Role for TDS Receivable - Customers')).toBeInTheDocument()
    expect(
      screen.getByText('1 TDS receivable ledgers not mapped - receivable checks skip them until mapped'),
    ).toBeInTheDocument()
    expect(mockPut).not.toHaveBeenCalled()
  })

  it('payableHiddenWhenNoYear', async () => {
    renderSection('ROLE_ACCOUNTANT', false)

    expect(await screen.findByText('TDS receivable ledgers')).toBeInTheDocument()
    expect(screen.queryByText('TDS payable ledgers')).not.toBeInTheDocument()
    expect(screen.queryByLabelText('Role for TDS Payable - Contractors')).not.toBeInTheDocument()
    expect(
      screen.queryByText(
        '2 TDS ledgers not mapped: TDS Payable - Contractors, TDS Payable - Unknown',
      ),
    ).not.toBeInTheDocument()
    expect(screen.getByLabelText('Role for TDS Receivable - Customers')).toBeInTheDocument()
    expect(
      screen.getByText('1 TDS receivable ledgers not mapped - receivable checks skip them until mapped'),
    ).toBeInTheDocument()
  })

  it('nonDeductorSavesOnlyReceivableRowsEvenWhenHiddenPayableUnresolved', async () => {
    renderSection('ROLE_ACCOUNTANT', false)

    const user = userEvent.setup()
    await user.click(
      await screen.findByRole('button', { name: 'Use suggested for TDS Receivable - Customers' }),
    )
    await user.click(screen.getByRole('button', { name: 'Save' }))

    await waitFor(() => {
      expect(mockPut).toHaveBeenCalled()
    })
    expect(mockPut).toHaveBeenCalledWith('/v1/tds/ledger-mapping', {
      items: [{ ledgerName: 'TDS Receivable - Customers', role: 'TDS_RECEIVABLE', section: null }],
    })
  })

  it('accountantSavesOnlyDirtyRows', async () => {
    renderSection('ROLE_ACCOUNTANT')

    const user = userEvent.setup()
    await user.selectOptions(await screen.findByLabelText('Role for TDS Payable - Unknown'), 'NOT_TDS')
    await user.click(screen.getByRole('button', { name: 'Save' }))

    await waitFor(() => {
      expect(mockPut).toHaveBeenCalled()
    })
    expect(mockPut).toHaveBeenCalledWith('/v1/tds/ledger-mapping', {
      items: [{ ledgerName: 'TDS Payable - Unknown', role: 'NOT_TDS', section: null }],
    })
  })

  it('sectionSelectRequiresPayableRoleAndCombinedSaves', async () => {
    renderSection('ROLE_ACCOUNTANT')

    const user = userEvent.setup()
    expect(await screen.findByLabelText('Role for TDS Receivable - Customers')).toBeInTheDocument()
    expect(screen.getByLabelText('Section for TDS Receivable - Customers')).toBeDisabled()

    const contractorsSection = screen.getByLabelText('Section for TDS Payable - Contractors')
    expect(contractorsSection).toBeEnabled()
    await waitFor(() => expect(contractorsSection).toHaveValue('194C'))
    expect(
      within(contractorsSection).getByRole('option', { name: 'Combined / infer per voucher' }),
    ).toBeInTheDocument()

    await user.selectOptions(contractorsSection, '')
    await user.selectOptions(screen.getByLabelText('Role for TDS Payable - Unknown'), 'NOT_TDS')
    expect(screen.getByLabelText('Section for TDS Payable - Unknown')).toBeDisabled()
    await user.click(screen.getByRole('button', { name: 'Save' }))

    await waitFor(() => {
      expect(mockPut).toHaveBeenCalled()
    })
    expect(mockPut).toHaveBeenCalledWith('/v1/tds/ledger-mapping', {
      items: [
        { ledgerName: 'TDS Payable - Contractors', role: 'TDS_PAYABLE', section: null },
        { ledgerName: 'TDS Payable - Unknown', role: 'NOT_TDS', section: null },
      ],
    })
  })

  it('operatorSeesReadOnly', async () => {
    renderSection('ROLE_OPERATOR')

    expect(await screen.findAllByText('Suggested, not saved')).toHaveLength(3)
    expect(screen.queryByRole('button', { name: 'Save' })).not.toBeInTheDocument()
    expect(
      screen.queryByRole('button', { name: 'Use suggested for TDS Payable - Contractors' }),
    ).not.toBeInTheDocument()
    expect(screen.getByLabelText('Role for TDS Payable - Contractors')).toBeDisabled()
    expect(screen.getByLabelText('Section for TDS Payable - Contractors')).toBeDisabled()
    expect(screen.getByLabelText('Role for TDS Receivable - Customers')).toBeDisabled()
  })

  it('changingRoleClearsSectionDraft', async () => {
    renderSection('ROLE_ACCOUNTANT')

    const user = userEvent.setup()
    expect(await screen.findByLabelText('Section for TDS Payable - Contractors')).toBeInTheDocument()
    await waitFor(() =>
      expect(screen.getByLabelText('Section for TDS Payable - Contractors')).toHaveValue('194C'),
    )

    await user.selectOptions(screen.getByLabelText('Role for TDS Payable - Contractors'), 'TDS_RECEIVABLE')
    const whileReceivable = screen.getByLabelText('Section for TDS Payable - Contractors')
    expect(whileReceivable).toBeDisabled()
    expect(whileReceivable).toHaveValue('')

    await user.selectOptions(screen.getByLabelText('Role for TDS Payable - Contractors'), 'TDS_PAYABLE')
    const afterFlipBack = screen.getByLabelText('Section for TDS Payable - Contractors')
    expect(afterFlipBack).toBeEnabled()
    expect(afterFlipBack).toHaveValue('')
  })
})
