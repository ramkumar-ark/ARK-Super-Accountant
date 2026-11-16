import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { BaselinePeriodDetail } from './BaselinePeriodDetail'

vi.mock('@/store/authStore')
vi.mock('@/lib/api', () => ({
  api: {
    get: vi.fn(),
    post: vi.fn(),
  },
}))

import { useAuthStore } from '@/store/authStore'
import { api } from '@/lib/api'

const mockUseAuthStore = vi.mocked(useAuthStore)
const mockGet = vi.mocked(api.get)
const mockPost = vi.mocked(api.post)

function renderDetail(role: string, rowOverrides: Record<string, unknown> = {}) {
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
  const row = {
    workflowId: 'w1',
    type: 'GSTR1',
    periodKey: '2026-04',
    periodStart: '2026-04-01',
    periodEnd: '2026-04-30',
    confirmed: false,
    status: 'RECONCILED',
    snapshots: [],
    materialOpen: 2,
    materialExplained: 1,
    info: 3,
    variance: {
      IGST: { filed: 5000, books: 4500, diff: 500 },
      CGST: { filed: 0, books: 0, diff: 0 },
      SGST: { filed: 0, books: 0, diff: 0 },
      CESS: { filed: 0, books: 0, diff: 0 },
    },
    ...rowOverrides,
  } as never
  return render(
    <QueryClientProvider client={queryClient}>
      <BaselinePeriodDetail row={row} />
    </QueryClientProvider>,
  )
}

describe('BaselinePeriodDetail', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockGet.mockImplementation((url: string) => {
      if (url === '/v1/workflows/w1/findings')
        return Promise.resolve({ data: { findings: [], blockingCount: 0 } })
      return Promise.resolve({ data: {} })
    })
  })

  it('showsVarianceAndMountsFindingsTab', async () => {
    renderDetail('ROLE_ACCOUNTANT')

    const strip = await screen.findByTestId('variance-strip')
    expect(strip).toHaveTextContent('5,000.00')
    const diffCells = within(strip).getAllByText('500.00')
    expect(diffCells.length).toBeGreaterThan(0)
    expect(diffCells[0]).toHaveClass('text-red-600')

    await waitFor(() => {
      expect(mockGet).toHaveBeenCalledWith('/v1/workflows/w1/findings')
    })
    expect(
      screen.getByText(
        'Explain each difference with a note; an accountant approves it. These differences do not block anything.',
      ),
    ).toBeInTheDocument()
  })

  it('rerunShowsMappingIncompleteMessage', async () => {
    mockPost.mockResolvedValueOnce({
      data: { workflowId: 'w1', status: 'MAPPING_INCOMPLETE', material: 0, info: 0, at: '2026-05-01T00:00:00Z' },
    })

    renderDetail('ROLE_OPERATOR')

    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'Re-run comparison' }))

    expect(await screen.findByText('Map all tax ledgers first')).toBeInTheDocument()
  })

  it('notImportedHidesFindings', () => {
    renderDetail('ROLE_ACCOUNTANT', { status: 'NOT_IMPORTED' })

    expect(screen.getByText('No filed return imported yet')).toBeInTheDocument()
    expect(mockGet).not.toHaveBeenCalledWith('/v1/workflows/w1/findings')
    expect(screen.queryByText(/Explain each difference/)).not.toBeInTheDocument()
  })
})
