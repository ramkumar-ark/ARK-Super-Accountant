import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { DeductorSection } from './DeductorSection'

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
})
