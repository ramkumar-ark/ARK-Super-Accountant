import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { PanOnlyAdvisoryPanel } from './PanOnlyAdvisoryPanel'
import { useAuthStore } from '@/store/authStore'
import type { TdsApplicabilityPayload } from '@/lib/tdsApplicabilityApi'

vi.mock('@/lib/api', () => ({
  api: { get: vi.fn(), put: vi.fn(), post: vi.fn() },
}))

import { api } from '@/lib/api'

const mockGet = vi.mocked(api.get)

const APPLICABILITY: TdsApplicabilityPayload = {
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

function renderPanel() {
  return render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <PanOnlyAdvisoryPanel />
    </QueryClientProvider>,
  )
}

function mockEndpoints(advisories: unknown[]) {
  mockGet.mockImplementation((url: string) => {
    if (url.includes('/tds-applicability/2025/pan-only-advisories')) {
      return Promise.resolve({ data: advisories })
    }
    if (url === '/v1/organizations/org-1/tds-applicability') {
      return Promise.resolve({ data: APPLICABILITY })
    }
    return Promise.reject(new Error('Unexpected GET ' + url))
  })
}

describe('PanOnlyAdvisoryPanel', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    useAuthStore.setState({
      user: {
        id: 1,
        username: 'user',
        email: 'user@example.com',
        role: 'ROLE_ACCOUNTANT',
        organizationId: 'org-1',
      },
      isAuthenticated: true,
    })
  })

  it('panelHiddenWhenEmpty', async () => {
    mockEndpoints([])

    const { container } = renderPanel()
    await vi.waitFor(() => {
      expect(mockGet).toHaveBeenCalledWith(
        '/v1/organizations/org-1/tds-applicability/2025/pan-only-advisories',
      )
    })

    expect(container).toBeEmptyDOMElement()
  })

  it('showsFormName', async () => {
    mockEndpoints([
      {
        section: '194M',
        partyLedgerName: 'Contractor X',
        financialYear: 2025,
        month: null,
        amount: 5500000,
        form: 'Form 141',
      },
    ])

    renderPanel()

    expect(
      await screen.findByText(
        'TDS applies to this payment without a TAN: deduct 2% and file Form 141 using your PAN.',
      ),
    ).toBeInTheDocument()
    expect(screen.getByText(/Contractor X/)).toBeInTheDocument()
  })
})
