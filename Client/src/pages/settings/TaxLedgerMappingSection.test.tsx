import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { TaxLedgerMappingSection } from './TaxLedgerMappingSection'

vi.mock('@/store/authStore')
vi.mock('@/lib/api', () => ({
  api: {
    get: vi.fn(),
    put: vi.fn(),
  },
}))

import { useAuthStore } from '@/store/authStore'
import { api } from '@/lib/api'

const mockUseAuthStore = vi.mocked(useAuthStore)
const mockGet = vi.mocked(api.get)
const mockPut = vi.mocked(api.put)

function renderSection(role: string) {
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
      <TaxLedgerMappingSection />
    </QueryClientProvider>,
  )
}

const mappingView = {
  complete: false,
  mastersJobId: null,
  blockers: ['2 tax ledgers not mapped: Output CGST, Compensation Cess'],
  ledgers: [
    {
      ledgerName: 'Output CGST',
      parentGroup: 'Duties & Taxes',
      saved: false,
      role: null,
      head: null,
      suggestedRole: 'OUTPUT',
      suggestedHead: 'CGST',
      savedBy: null,
      savedAt: null,
    },
    {
      ledgerName: 'Compensation Cess',
      parentGroup: 'Duties & Taxes',
      saved: false,
      role: null,
      head: null,
      suggestedRole: null,
      suggestedHead: 'CESS',
      savedBy: null,
      savedAt: null,
    },
  ],
}

function mockMapping(view: Record<string, unknown>) {
  mockGet.mockImplementation((url: string) => {
    if (url === '/v1/gst/tax-ledger-mapping') return Promise.resolve({ data: view })
    return Promise.resolve({ data: {} })
  })
}

describe('TaxLedgerMappingSection', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockMapping(mappingView)
    mockPut.mockResolvedValue({ data: mappingView })
  })

  it('suggestionsAreMarkedUnsavedUntilSave', async () => {
    renderSection('ROLE_ACCOUNTANT')

    expect(await screen.findAllByText('Suggested, not saved')).toHaveLength(2)
    expect(screen.getByText('Incomplete')).toBeInTheDocument()
    expect(
      screen.getByText('2 tax ledgers not mapped: Output CGST, Compensation Cess'),
    ).toBeInTheDocument()
    expect(mockPut).not.toHaveBeenCalled()
  })

  it('taxRoleWithoutHeadBlocksSave', async () => {
    renderSection('ROLE_ACCOUNTANT')

    const user = userEvent.setup()
    const saveButton = await screen.findByRole('button', { name: 'Save' })
    expect(saveButton).toBeDisabled()

    const cessRole = screen.getByLabelText('Role for Compensation Cess')
    await user.selectOptions(cessRole, 'INPUT')
    await user.selectOptions(screen.getByLabelText('Head for Compensation Cess'), 'CESS')
    expect(screen.getByRole('button', { name: 'Save' })).toBeEnabled()

    await user.selectOptions(screen.getByLabelText('Role for Output CGST'), 'OUTPUT')
    await user.selectOptions(screen.getByLabelText('Head for Output CGST'), '')
    expect(screen.getByRole('button', { name: 'Save' })).toBeDisabled()
    expect(screen.getByText('Choose a head')).toBeInTheDocument()
  })

  it('accountantSavesAllRows', async () => {
    renderSection('ROLE_ACCOUNTANT')

    const user = userEvent.setup()
    await user.selectOptions(await screen.findByLabelText('Role for Compensation Cess'), 'INPUT')
    await user.selectOptions(screen.getByLabelText('Head for Compensation Cess'), 'CESS')
    await user.click(screen.getByRole('button', { name: 'Save' }))

    await waitFor(() => {
      expect(mockPut).toHaveBeenCalled()
    })
    expect(mockPut).toHaveBeenCalledWith('/v1/gst/tax-ledger-mapping', {
      items: [
        { ledgerName: 'Output CGST', role: 'OUTPUT', head: 'CGST' },
        { ledgerName: 'Compensation Cess', role: 'INPUT', head: 'CESS' },
      ],
    })
  })

  it('portalBalanceRolesAreSelectableAndNeedAHead', async () => {
    renderSection('ROLE_ACCOUNTANT')

    const user = userEvent.setup()
    await user.selectOptions(
      await screen.findByLabelText('Role for Compensation Cess'),
      'NOT_TAX',
    )
    expect(screen.getByLabelText('Head for Compensation Cess')).toBeDisabled()
    const roleSelect = screen.getByLabelText('Role for Output CGST')
    await user.selectOptions(roleSelect, 'CASH_BALANCE')
    expect(
      within(roleSelect).getByRole('option', { name: 'Portal cash balance' }),
    ).toBeInTheDocument()

    const headSelect = screen.getByLabelText('Head for Output CGST')
    expect(headSelect).toBeEnabled()
    expect(screen.getByText('Choose a head')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Save' })).toBeDisabled()
    await user.selectOptions(headSelect, 'IGST')
    expect(screen.getByRole('button', { name: 'Save' })).toBeEnabled()
  })

  it('operatorSeesReadOnly', async () => {
    renderSection('ROLE_OPERATOR')

    expect(await screen.findAllByText('Suggested, not saved')).toHaveLength(2)
    expect(screen.queryByRole('button', { name: 'Save' })).not.toBeInTheDocument()
    expect(screen.getByLabelText('Role for Output CGST')).toBeDisabled()
    expect(screen.getByLabelText('Head for Output CGST')).toBeDisabled()
  })
})
