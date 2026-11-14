import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { WorkflowListPage } from './WorkflowListPage'
import { api } from '@/lib/api'
import type { Workflow } from '@/lib/workflowApi'

const auth = vi.hoisted(() => ({ user: { role: 'ROLE_OPERATOR' } }))

vi.mock('@/store/authStore', () => ({
  useAuthStore: () => auth,
}))
vi.mock('@/components/AppShell', () => ({
  AppShell: ({ children }: { children: ReactNode }) => <div>{children}</div>,
}))
vi.mock('@tanstack/react-router', () => ({
  Link: ({ children }: { children: ReactNode }) => <a>{children}</a>,
  useNavigate: () => vi.fn(),
}))
vi.mock('@/lib/api', () => ({
  api: { get: vi.fn(), post: vi.fn() },
}))

function workflow(overrides: Partial<Workflow>): Workflow {
  return {
    id: 'wf-1',
    type: 'GSTR1',
    periodStart: '2025-04-01',
    periodEnd: '2025-04-30',
    periodKey: '2025-04',
    financialYear: 2025,
    revision: 1,
    stage: 'STARTED',
    active: true,
    uploadJobId: null,
    mastersJobId: null,
    baselineConfirmedAt: null,
    finalizedAt: null,
    finalizedBy: null,
    filedAt: null,
    filingRef: null,
    filingRefDate: null,
    createdBy: 'owner',
    createdAt: '2025-05-01T10:00:00Z',
    ...overrides,
  }
}

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <WorkflowListPage />
    </QueryClientProvider>,
  )
}

describe('WorkflowListPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    auth.user = { role: 'ROLE_OPERATOR' }
  })

  it('list_groupsByFinancialYearInPeriodOrder', async () => {
    vi.mocked(api.get).mockResolvedValue({
      data: [
        workflow({ id: 'wf-may', periodKey: '2025-05', periodStart: '2025-05-01', periodEnd: '2025-05-31' }),
        workflow({ id: 'wf-apr', periodKey: '2025-04', stage: 'FINDINGS_REVIEW', revision: 2 }),
        workflow({
          id: 'wf-q4',
          type: 'TDS_26Q',
          periodKey: '2024-Q4',
          periodStart: '2025-01-01',
          periodEnd: '2025-03-31',
          financialYear: 2024,
        }),
      ],
    })

    renderPage()

    await screen.findByText('2025-04')
    const headings = screen.getAllByRole('heading', { level: 2 }).map((h) => h.textContent)
    expect(headings).toEqual(['FY 2025-26', 'FY 2024-25'])

    const apr = screen.getByText('2025-04')
    const may = screen.getByText('2025-05')
    expect(apr.compareDocumentPosition(may)).toBe(Node.DOCUMENT_POSITION_FOLLOWING)

    const aprRow = screen.getByRole('button', { name: /2025-04/ })
    const mayRow = screen.getByRole('button', { name: /2025-05/ })
    const q4Row = screen.getByRole('button', { name: /2024-Q4/ })
    expect(within(aprRow).getByText('GSTR-1')).toBeInTheDocument()
    expect(within(q4Row).getByText('TDS 26Q')).toBeInTheDocument()
    expect(within(aprRow).getByText('Rev 2')).toBeInTheDocument()
    expect(screen.queryByText('Rev 1')).not.toBeInTheDocument()
    expect(within(mayRow).getByText('Started')).toBeInTheDocument()
    expect(within(aprRow).getByText('Findings review')).toBeInTheDocument()
  })

  it('list_filtersPassedAsQueryParams', async () => {
    vi.mocked(api.get).mockResolvedValue({ data: [] })
    const user = userEvent.setup()

    renderPage()

    await waitFor(() =>
      expect(api.get).toHaveBeenLastCalledWith('/v1/workflows', { params: {} }),
    )

    await user.selectOptions(screen.getByLabelText('Type'), 'GSTR3B')
    await user.selectOptions(screen.getByLabelText('Stage'), 'PREPARE')

    await waitFor(() =>
      expect(api.get).toHaveBeenLastCalledWith('/v1/workflows', {
        params: { type: 'GSTR3B', stage: 'PREPARE' },
      }),
    )
  })

  it('list_operatorSeesStartButton', async () => {
    vi.mocked(api.get).mockResolvedValue({ data: [] })
    renderPage()
    expect(await screen.findByRole('button', { name: 'Start workflow' })).toBeInTheDocument()
  })

  it('list_auditorSeesNoStartButton', async () => {
    vi.mocked(api.get).mockResolvedValue({ data: [] })
    auth.user = { role: 'ROLE_AUDITOR_CA' }
    renderPage()
    await screen.findByText('No workflows yet.')
    expect(screen.queryByRole('button', { name: 'Start workflow' })).not.toBeInTheDocument()
  })

  it('list_ownerSeesNoStartButton', async () => {
    vi.mocked(api.get).mockResolvedValue({ data: [] })
    auth.user = { role: 'ROLE_OWNER' }
    renderPage()
    await screen.findByText('No workflows yet.')
    expect(screen.queryByRole('button', { name: 'Start workflow' })).not.toBeInTheDocument()
  })

  it('list_emptyState', async () => {
    vi.mocked(api.get).mockResolvedValue({ data: [] })
    renderPage()
    expect(await screen.findByText('No workflows yet.')).toBeInTheDocument()
  })
})
