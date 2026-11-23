import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor, act } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { DeductorSection } from './DeductorSection'
import type { TdsApplicabilityPayload } from '@/lib/tdsApplicabilityApi'

vi.mock('@/store/authStore')
vi.mock('@/lib/api', () => ({
  api: { get: vi.fn(), patch: vi.fn() },
}))

import { useAuthStore } from '@/store/authStore'
import { api } from '@/lib/api'

const mockUseAuthStore = vi.mocked(useAuthStore)
const mockGet = vi.mocked(api.get)
const mockPatch = vi.mocked(api.patch)

const CURRENT_ORG = {
  id: 'org-abc',
  name: 'Test Org',
  gstin: '',
  pan: 'ABCDE1234F',
  registeredAddress: '',
  financialYearStart: 4,
  tan: 'AAAA12345A',
  deductorType: 'K',
  responsiblePersonName: 'Ravi Kumar',
  responsiblePersonDesignation: 'Proprietor',
  interestPayerType: 'OTHER',
  createdAt: '2025-04-01T00:00:00Z',
}

const APPLICABILITY_ON: TdsApplicabilityPayload = {
  entityCode: 'Q',
  entityCodeSource: 'PAN',
  tan: 'AAAA12345A',
  years: [
    {
      financialYear: 2025,
      applicable: true,
      basis: 'DECLARED',
      needsAnswer: false,
      priorYearTurnover: 120000000,
      turnoverNature: 'BUSINESS',
      turnoverEstimate: null,
    },
  ],
}

const APPLICABILITY_OFF: TdsApplicabilityPayload = {
  entityCode: 'Q',
  entityCodeSource: 'PAN',
  tan: null,
  years: [
    {
      financialYear: 2025,
      applicable: false,
      basis: 'DECLARED',
      needsAnswer: false,
      priorYearTurnover: null,
      turnoverNature: null,
      turnoverEstimate: null,
    },
  ],
}

function renderSection(
  role: string,
  applicability = APPLICABILITY_ON,
  queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  }),
  org = CURRENT_ORG,
) {
  mockUseAuthStore.mockReturnValue({
    user: {
      id: 1,
      username: 'alice',
      email: 'alice@test.com',
      role,
      organizationId: 'org-abc',
    },
  } as ReturnType<typeof useAuthStore>)
  mockGet.mockImplementation((url: string) => {
    if (url === '/organizations/org-abc') return Promise.resolve({ data: org })
    if (url === '/v1/organizations/org-abc/tds-applicability') {
      return Promise.resolve({ data: applicability })
    }
    return Promise.reject(new Error('Unexpected GET ' + url))
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <DeductorSection />
    </QueryClientProvider>,
  )
}

describe('DeductorSection', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockGet.mockImplementation((url: string) => {
      if (url === '/organizations/org-abc') return Promise.resolve({ data: CURRENT_ORG })
      return Promise.reject(new Error('Unexpected GET ' + url))
    })
  })

  it('renders the current TAN and deductor type', async () => {
    renderSection('ROLE_ACCOUNTANT')

    expect(await screen.findByLabelText('TAN')).toHaveValue('AAAA12345A')
    expect(screen.getByLabelText('Deductor type')).toHaveValue('K')
    expect(screen.getByLabelText('Interest payer type')).toHaveValue('OTHER')
    expect(screen.getByLabelText('Responsible person name')).toHaveValue('Ravi Kumar')
  })

  it('offers every non-government Annexure 4 deductor type with official labels', async () => {
    renderSection('ROLE_ACCOUNTANT')

    const select = await screen.findByLabelText('Deductor type')
    const options = Array.from(select.querySelectorAll('option')).map((o) => [
      o.value,
      o.textContent,
    ])
    expect(options).toEqual([
      ['', 'Select'],
      ['K', 'Company'],
      ['M', 'Branch / Division of Company'],
      ['P', 'Association of Person (AOP)'],
      ['T', 'Association of Person (Trust)'],
      ['J', 'Artificial Juridical Person'],
      ['B', 'Body of Individuals'],
      ['Q', 'Individual/HUF'],
      ['F', 'Firm'],
    ])
  })

  it('rejects a malformed TAN client-side without calling the API', async () => {
    renderSection('ROLE_ACCOUNTANT')
    const user = userEvent.setup()

    const tan = await screen.findByLabelText('TAN')
    await user.clear(tan)
    await user.type(tan, 'badtan')
    await user.click(screen.getByRole('button', { name: 'Save' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('[A-Z]{4}[0-9]{5}[A-Z]')
    expect(mockPatch).not.toHaveBeenCalled()
  })

  it('submits PATCH /organizations/{id}/deductor', async () => {
    mockPatch.mockResolvedValue({ data: {} })
    renderSection('ROLE_ACCOUNTANT')
    const user = userEvent.setup()

    const tan = await screen.findByLabelText('TAN')
    await user.clear(tan)
    await user.type(tan, 'BBBB67890B')
    await user.selectOptions(screen.getByLabelText('Deductor type'), 'P')
    await user.selectOptions(screen.getByLabelText('Interest payer type'), 'BANK')
    await user.click(screen.getByRole('button', { name: 'Save' }))

    await waitFor(() => {
      expect(mockPatch).toHaveBeenCalledWith('/organizations/org-abc/deductor', {
        tan: 'BBBB67890B',
        deductorType: 'P',
        responsiblePersonName: 'Ravi Kumar',
        responsiblePersonDesignation: 'Proprietor',
        interestPayerType: 'BANK',
      })
    })
  })

  it('saves Individual/HUF as code Q', async () => {
    mockPatch.mockResolvedValue({ data: {} })
    renderSection('ROLE_ACCOUNTANT')
    const user = userEvent.setup()

    await screen.findByLabelText('TAN')
    await user.selectOptions(screen.getByLabelText('Deductor type'), 'Q')
    await user.click(screen.getByRole('button', { name: 'Save' }))

    await waitFor(() => {
      expect(mockPatch).toHaveBeenCalledWith('/organizations/org-abc/deductor', {
        tan: 'AAAA12345A',
        deductorType: 'Q',
        responsiblePersonName: 'Ravi Kumar',
        responsiblePersonDesignation: 'Proprietor',
        interestPayerType: 'OTHER',
      })
    })
  })

  it('shows the server 400 message', async () => {
    mockPatch.mockRejectedValue({
      response: { status: 400, data: { error: 'Invalid TAN format. Expected AAAA99999A.' } },
    })
    renderSection('ROLE_ACCOUNTANT')
    const user = userEvent.setup()

    await screen.findByLabelText('TAN')
    await user.click(screen.getByRole('button', { name: 'Save' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Invalid TAN format. Expected AAAA99999A.',
    )
  })

  it.each(['ROLE_OPERATOR', 'ROLE_AUDITOR_CA'])(
    'is read-only for %s',
    async (role) => {
      renderSection(role)

      expect(await screen.findByLabelText('TAN')).toBeDisabled()
      expect(screen.getByLabelText('Deductor type')).toBeDisabled()
      expect(screen.getByLabelText('Interest payer type')).toBeDisabled()
      expect(screen.queryByRole('button', { name: 'Save' })).not.toBeInTheDocument()
    },
  )

  it('is hidden when no year is applicable', async () => {
    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    })
    renderSection('ROLE_ACCOUNTANT', APPLICABILITY_OFF, queryClient)

    await waitFor(() => {
      expect(queryClient.getQueryState(['tds-applicability', 'org-abc'])?.status).toBe('success')
      expect(queryClient.getQueryState(['organization', 'org-abc'])?.status).toBe('success')
    })
    await act(async () => {})

    expect(screen.queryByText('Deductor details')).not.toBeInTheDocument()
  })

  it('shows the TAN-missing warning when a year is on and TAN is blank', async () => {
    renderSection(
      'ROLE_ACCOUNTANT',
      APPLICABILITY_ON,
      undefined,
      { ...CURRENT_ORG, tan: '' },
    )

    expect(
      await screen.findByText('TAN missing — TDS returns cannot be finalized without it.'),
    ).toBeInTheDocument()
  })
})
