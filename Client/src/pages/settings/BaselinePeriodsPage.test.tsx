import { describe, it, expect, vi, beforeEach } from 'vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { BaselinePeriodsPage } from './BaselinePeriodsPage'

vi.mock('@/components/AppShell', () => ({
  AppShell: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
}))
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
      <BaselinePeriodsPage />
    </QueryClientProvider>,
  )
}


const periods = [
  {
    workflowId: 'w1',
    type: 'GSTR1',
    periodKey: '2026-04',
    periodStart: '2026-04-01',
    periodEnd: '2026-04-30',
    confirmed: false,
    status: 'RECONCILED',
    snapshots: [
      {
        id: 's1',
        formType: 'GSTR1',
        filingPeriod: '2026-04',
        sourceKind: 'PORTAL_JSON',
        importedBy: 'alice',
        importedAt: '2026-05-02T00:00:00Z',
        superseded: false,
        lineCount: 12,
      },
    ],
    materialOpen: 2,
    materialExplained: 1,
    info: 3,
    variance: {
      IGST: { filed: 5000, books: 5000, diff: 0 },
      CGST: { filed: 0, books: 0, diff: 0 },
      SGST: { filed: 0, books: 0, diff: 0 },
      CESS: { filed: 0, books: 0, diff: 0 },
    },
  },
  {
    workflowId: 'w2',
    type: 'GSTR3B',
    periodKey: '2026-04',
    periodStart: '2026-04-01',
    periodEnd: '2026-04-30',
    confirmed: false,
    status: 'NOT_IMPORTED',
    snapshots: [],
    materialOpen: 0,
    materialExplained: 0,
    info: 0,
    variance: {
      IGST: { filed: 0, books: 0, diff: 0 },
      CGST: { filed: 0, books: 0, diff: 0 },
      SGST: { filed: 0, books: 0, diff: 0 },
      CESS: { filed: 0, books: 0, diff: 0 },
    },
  },
]

function mockPeriods(rows: unknown[]) {
  mockGet.mockImplementation((url: string) => {
    if (url === '/v1/filed-returns/baseline') return Promise.resolve({ data: rows })
    return Promise.resolve({ data: [] })
  })
}

describe('BaselinePeriodsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockPeriods(periods)
  })

  it('listsRowsWithStatusAndCounts', async () => {
    renderPage('ROLE_ACCOUNTANT')

    expect(await screen.findByRole('heading', { name: 'GSTR-1' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'GSTR-3B' })).toBeInTheDocument()
    await waitFor(() => {
      expect(screen.getByText('2')).toBeInTheDocument()
    })
    expect(screen.getByText('Not imported')).toBeInTheDocument()
  })

  it('operatorSeesImportButtonsAuditorDoesNot', async () => {
    const { unmount } = renderPage('ROLE_OPERATOR')
    await screen.findByRole('heading', { name: 'GSTR-1' })
    expect(screen.getByText('Import GSTR-1 JSON')).toBeInTheDocument()
    expect(screen.getByText('Import GSTR-3B PDF')).toBeInTheDocument()
    unmount()

    renderPage('ROLE_AUDITOR_CA')
    await screen.findByRole('heading', { name: 'GSTR-1' })
    expect(screen.queryByText('Import GSTR-1 JSON')).not.toBeInTheDocument()
  })

  it('gstr1UploadShowsServerError', async () => {
    mockPost.mockRejectedValueOnce({
      response: { status: 409, data: { error: 'No baseline period covers 2025-01' } },
    })

    renderPage('ROLE_ACCOUNTANT')

    const user = userEvent.setup()
    const input = await screen.findByLabelText('Import GSTR-1 JSON file')
    await user.upload(input, new File(['{}'], 'gstr1.json', { type: 'application/json' }))
    const radio = screen.getByLabelText('GSTR-1')
    await user.click(radio)
    await user.click(screen.getByRole('button', { name: 'Upload' }))

    await waitFor(() => {
      expect(screen.getByText('No baseline period covers 2025-01')).toBeInTheDocument()
    })
  })
})
