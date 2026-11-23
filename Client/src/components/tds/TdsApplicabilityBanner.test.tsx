import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { TdsApplicabilityBanner } from './TdsApplicabilityBanner'

vi.mock('@/store/authStore')
vi.mock('@/lib/api', () => ({
  api: { get: vi.fn(), post: vi.fn(), put: vi.fn() },
}))

import { useAuthStore } from '@/store/authStore'
import { api } from '@/lib/api'

const mockUseAuthStore = vi.mocked(useAuthStore)
const mockGet = vi.mocked(api.get)
const mockPost = vi.mocked(api.post)

const BOOKS_PROMPT = {
  id: 'prompt-1',
  kind: 'BOOKS_EXCEED_LIMIT',
  financialYear: 2026,
  estimate: { measuredFinancialYear: 2025, amount: 12000000, source: 'BOOKS', complete: true },
  message: 'Books show receipts of ₹1,20,00,000 for FY 2025-26.',
}

function renderBanner() {
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <TdsApplicabilityBanner />
    </QueryClientProvider>,
  )
}

describe('TdsApplicabilityBanner', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockUseAuthStore.mockReturnValue({
      user: {
        id: 1,
        username: 'alice',
        email: 'alice@test.com',
        role: 'ROLE_OWNER',
        organizationId: 'org-abc',
      },
    } as ReturnType<typeof useAuthStore>)
  })

  it('bannerShowsAmountAndBothActions', async () => {
    mockGet.mockImplementation((url: string) => {
      if (url === '/v1/organizations/org-abc/tds-applicability/prompts') {
        return Promise.resolve({ data: [BOOKS_PROMPT] })
      }
      return Promise.reject(new Error('Unexpected GET ' + url))
    })
    renderBanner()

    expect(await screen.findByText(/₹1,20,00,000/)).toBeInTheDocument()
    expect(screen.getByText(/FY 2025-26/)).toBeInTheDocument()
    expect(screen.getByText(/1 April 2026/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'TDS applies' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'It does not (give reason)' })).toBeInTheDocument()
  })

  it('dismissNeedsReason', async () => {
    mockGet.mockImplementation((url: string) => {
      if (url === '/v1/organizations/org-abc/tds-applicability/prompts') {
        return Promise.resolve({ data: [BOOKS_PROMPT] })
      }
      return Promise.reject(new Error('Unexpected GET ' + url))
    })
    renderBanner()

    await screen.findByText(/₹1,20,00,000/)
    await userEvent.setup().click(screen.getByRole('button', { name: 'It does not (give reason)' }))

    const dismissButton = await screen.findByRole('button', { name: 'Dismiss' })
    await userEvent.setup().click(dismissButton)

    expect(mockPost).not.toHaveBeenCalled()
    expect(await screen.findByText('Please give a reason')).toBeInTheDocument()
  })
})
