import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { TdsApplicabilitySection } from './TdsApplicabilitySection'

vi.mock('@/store/authStore')
vi.mock('@/lib/api', () => ({
  api: { get: vi.fn(), put: vi.fn() },
}))

import { useAuthStore } from '@/store/authStore'
import { api } from '@/lib/api'

const mockUseAuthStore = vi.mocked(useAuthStore)
const mockGet = vi.mocked(api.get)
const mockPut = vi.mocked(api.put)

const APPLICABILITY = {
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
    },
    {
      financialYear: 2026,
      applicable: false,
      basis: 'CARRIED_FORWARD',
      needsAnswer: true,
      priorYearTurnover: null,
      turnoverNature: null,
    },
  ],
}

describe('TdsApplicabilitySection', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockUseAuthStore.mockReturnValue({
      user: {
        id: 1,
        username: 'alice',
        email: 'alice@test.com',
        role: 'ROLE_ACCOUNTANT',
        organizationId: 'org-abc',
      },
    } as ReturnType<typeof useAuthStore>)
    mockGet.mockImplementation((url: string) => {
      if (url === '/v1/organizations/org-abc/tds-applicability') {
        return Promise.resolve({ data: APPLICABILITY })
      }
      return Promise.reject(new Error('Unexpected GET ' + url))
    })
  })

  it('renders each financial year with its basis and flags carried-forward years', async () => {
    render(
      <QueryClientProvider
        client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}
      >
        <TdsApplicabilitySection />
      </QueryClientProvider>,
    )

    expect(await screen.findByText('FY 2025-26 — Not a deductor (declared)')).toBeInTheDocument()
    expect(screen.getByText('FY 2026-27 — Not a deductor (carried forward)')).toBeInTheDocument()
    expect(screen.getByText('Needs answer')).toBeInTheDocument()
  })

  it('answering a year sends PUT /organizations/{id}/tds-applicability/{fy}', async () => {
    const user = userEvent.setup()
    mockPut.mockResolvedValue({ data: {} })
    render(
      <QueryClientProvider
        client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}
      >
        <TdsApplicabilitySection />
      </QueryClientProvider>,
    )

    await screen.findByText('FY 2026-27 — Not a deductor (carried forward)')
    await user.click(screen.getByRole('button', { name: 'Answer' }))
    await user.click(screen.getByRole('radio', { name: 'Yes' }))
    await user.click(screen.getByRole('button', { name: 'Save answer' }))

    await waitFor(() => {
      expect(mockPut).toHaveBeenCalledWith('/v1/organizations/org-abc/tds-applicability/2026', {
        applicable: true,
        turnoverNature: 'BUSINESS',
      })
    })
  })
})
