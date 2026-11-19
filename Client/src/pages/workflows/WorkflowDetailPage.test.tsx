import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { WorkflowDetailPage } from './WorkflowDetailPage'
import { api } from '@/lib/api'
import type { WorkflowDetail, WorkflowEvent, UploadHistoryRow } from '@/lib/workflowApi'
import type { TdsChallan } from '@/lib/tdsApi'

vi.mock('@/lib/api', () => ({
  api: { get: vi.fn(), post: vi.fn() },
}))

vi.mock('@tanstack/react-router', () => ({
  useParams: () => ({ workflowId: 'wf-1' }),
  Link: ({ children }: { children: ReactNode }) => <a>{children}</a>,
}))

vi.mock('@/components/AppShell', () => ({
  AppShell: ({ children }: { children: ReactNode }) => <div>{children}</div>,
}))

vi.mock('@/components/workflow/WorkflowFindingsTab', () => ({
  WorkflowFindingsTab: ({ role }: { role: string }) => (
    <div data-testid="findings-tab">{role}</div>
  ),
}))

const auth = vi.hoisted(() => ({
  user: { role: 'ROLE_ACCOUNTANT', organizationId: 'org-1' },
}))
vi.mock('@/store/authStore', () => ({
  useAuthStore: () => auth,
}))

const mockGet = vi.mocked(api.get)
const mockPost = vi.mocked(api.post)

const NOTICE = 'Day-book checks for this return type are not available yet'

function baseWorkflow(overrides: Partial<WorkflowDetail['workflow']> = {}) {
  return {
    id: 'wf-1',
    type: 'GSTR1' as const,
    periodStart: '2025-04-01',
    periodEnd: '2025-04-30',
    periodKey: '2025-04',
    financialYear: 2025,
    revision: 1,
    stage: 'STARTED' as const,
    active: true,
    uploadJobId: null,
    mastersJobId: null,
    baselineConfirmedAt: null,
    finalizedAt: null,
    finalizedBy: null,
    filedAt: null,
    filingRef: null,
    filingRefDate: null,
    filingChannel: null,
    createdBy: 'op1',
    createdAt: '2025-04-01T00:00:00Z',
    ...overrides,
  }
}

function detail(
  workflowOverrides: Partial<WorkflowDetail['workflow']> = {},
  overrides: Partial<WorkflowDetail> = {},
): WorkflowDetail {
  return {
    workflow: baseWorkflow(workflowOverrides),
    dependencies: [],
    blockingFindings: 0,
    baselineConfirmed: true,
    id: 'wf-1',
    analysisAvailable: false,
    requiredCoverageFrom: '2025-04-01',
    requiredCoverageTo: '2025-04-30',
    ...overrides,
  }
}

function uploadRow(overrides: Partial<UploadHistoryRow> = {}): UploadHistoryRow {
  return {
    id: 'job-8',
    fileName: 'apr.json',
    status: 'COMPLETED',
    coverageFrom: '2025-04-01',
    coverageTo: '2025-04-30',
    ...overrides,
  }
}

function routeApi({
  detailResponse,
  events = [],
  uploads = [],
  findings = [],
  followUps = [],
  challans = [],
  parentWorkflows = [],
}: {
  detailResponse: WorkflowDetail
  events?: WorkflowEvent[]
  uploads?: UploadHistoryRow[]
  challans?: TdsChallan[]
  parentWorkflows?: WorkflowDetail['workflow'][]
  findings?: Array<{ findingType: string; blocking: boolean; message: string }>
  followUps?: Array<{ id: string; voucherKey: string; summary: string; status: string; doneBy: string | null; doneAt: string | null; doneNote: string | null; createdAt: string }>
}) {
  mockGet.mockImplementation((url: string) => {
    if (url === '/v1/workflows/wf-1') {
      return Promise.resolve({ data: detailResponse })
    }
    if (url === '/v1/workflows/wf-1/events') {
      return Promise.resolve({ data: events })
    }
    if (url === '/v1/workflows/wf-1/findings') {
      return Promise.resolve({ data: { findings, blockingCount: 0 } })
    }
    if (url === '/v1/workflows/wf-1/tds/challans') {
      return Promise.resolve({
        data: { challans, tdsDeducted: 5000, approvedTdsTotal: 3000, excess: 0 },
      })
    }
    if (url === '/v1/workflows') {
      return Promise.resolve({ data: parentWorkflows })
    }
    if (url === '/v1/workflows/wf-1/tds/entries') {
      return Promise.resolve({ data: [] })
    }
    if (url === '/v1/workflows/wf-1/tds/quarter-challans') {
      return Promise.resolve({ data: [] })
    }
    if (url === '/v1/workflows/wf-1/tds/gate') {
      return Promise.resolve({ data: [] })
    }
    if (url === '/v1/workflows/wf-1/amendments') {
      return Promise.resolve({ data: { followUps } })
    }
    if (url.startsWith('/v1/day-book/uploads')) {
      return Promise.resolve({ data: { content: uploads } })
    }
    return Promise.reject(new Error('Unexpected GET ' + url))
  })
}

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={client}>
      <WorkflowDetailPage />
    </QueryClientProvider>,
  )
}

function event(overrides: Partial<WorkflowEvent>): WorkflowEvent {
  return {
    id: 'e1',
    workflowId: 'wf-1',
    actor: 'op1',
    occurredAt: '2025-04-01T10:00:00Z',
    kind: 'STARTED',
    reason: null,
    payload: null,
    ...overrides,
  }
}

beforeEach(() => {
  vi.clearAllMocks()
  auth.user = { role: 'ROLE_ACCOUNTANT', organizationId: 'org-1' }
  mockPost.mockResolvedValue({ data: {} })
})

describe('WorkflowDetailPage', () => {
  it('marks the current stage and completed stages in the stepper', async () => {
    routeApi({ detailResponse: detail({ stage: 'FINDINGS_REVIEW' }) })
    renderPage()

    const current = (await screen.findByText('Findings review')).closest('li')
    expect(current).toHaveAttribute('aria-current', 'step')

    expect(screen.getByText('Started').closest('li')).toHaveAttribute('data-state', 'done')
    expect(screen.getByText('Data uploaded').closest('li')).toHaveAttribute('data-state', 'done')
  })

  it('shows the dependency checklist', async () => {
    routeApi({
      detailResponse: detail({}, {
        dependencies: [{ type: 'GSTR1', periodKey: '2025-04', satisfied: false }],
      }),
    })
    renderPage()

    const dependencies = within(await screen.findByTestId('workflow-dependencies'))
    expect(dependencies.getByText('GSTR-1 2025-04')).toBeInTheDocument()
    expect(dependencies.getByText('Not finalized')).toBeInTheDocument()
  })

  it('shows the no-analyzers notice and still renders the findings tab', async () => {
    routeApi({ detailResponse: detail({ stage: 'FINDINGS_REVIEW' }, { analysisAvailable: false }) })
    renderPage()

    expect(await screen.findByText(NOTICE)).toBeInTheDocument()
    expect(screen.getByTestId('findings-tab')).toHaveTextContent('ROLE_ACCOUNTANT')
  })

  it('hides the no-analyzers notice when an analyzer is available', async () => {
    routeApi({ detailResponse: detail({ stage: 'FINDINGS_REVIEW' }, { analysisAvailable: true }) })
    renderPage()

    await screen.findByTestId('findings-tab')
    expect(screen.queryByText(NOTICE)).not.toBeInTheDocument()
  })

  it('offers a re-run when the upload is bound but analysis did not finish', async () => {
    routeApi({
      detailResponse: detail({ stage: 'DATA_UPLOADED', uploadJobId: 'job-1' }),
    })
    renderPage()

    expect(await screen.findByText('Analysis did not finish for this upload.')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Re-run analysis' }))
    expect(mockPost).toHaveBeenCalledWith('/v1/workflows/wf-1/analyze')
  })

  it('prefills the coverage inputs from the required range', async () => {
    routeApi({
      detailResponse: detail(
        { stage: 'STARTED' },
        { requiredCoverageFrom: '2025-04-01', requiredCoverageTo: '2025-04-30' },
      ),
    })
    renderPage()

    expect(await screen.findByLabelText('Coverage from')).toHaveValue('2025-04-01')
    expect(screen.getByLabelText('Coverage to')).toHaveValue('2025-04-30')
  })

  it('uploads a multipart file and shows the intake warnings', async () => {
    routeApi({ detailResponse: detail({ stage: 'STARTED' }) })
    mockPost.mockResolvedValue({
      data: {
        ...baseWorkflow({ stage: 'DATA_UPLOADED' }),
        warnings: ['First voucher is dated 2025-04-01, more than 15 days after the declared start 2025-03-01.'],
      },
    })
    renderPage()

    const input = await screen.findByTestId('workflow-file-input')
    const file = new File(['{}'], 'apr.json', { type: 'application/json' })
    await userEvent.upload(input, file)
    await userEvent.click(screen.getByRole('button', { name: 'Upload and bind' }))

    expect(mockPost).toHaveBeenCalledWith(
      '/v1/workflows/wf-1/upload',
      expect.any(FormData),
      expect.anything(),
    )
    const body = mockPost.mock.calls[0][1] as FormData
    expect(body.get('coverageFrom')).toBe('2025-04-01')
    expect(body.get('coverageTo')).toBe('2025-04-30')
    expect(await screen.findByText(/First voucher is dated/)).toBeInTheDocument()
  })

  it('shows the server message when the upload coverage is rejected', async () => {
    routeApi({ detailResponse: detail({ stage: 'STARTED' }) })
    mockPost.mockRejectedValue({
      response: { data: { error: 'upload must cover 2025-04-01 to 2025-04-30' } },
    })
    renderPage()

    const input = await screen.findByTestId('workflow-file-input')
    await userEvent.upload(input, new File(['{}'], 'apr.json', { type: 'application/json' }))
    await userEvent.click(screen.getByRole('button', { name: 'Upload and bind' }))

    expect(
      await screen.findByText('upload must cover 2025-04-01 to 2025-04-30'),
    ).toBeInTheDocument()
  })

  it('uses the latest COMPLETED upload', async () => {
    routeApi({
      detailResponse: detail({ stage: 'STARTED' }),
      uploads: [
        uploadRow({ id: 'job-9', fileName: 'bad.json', status: 'FAILED' }),
        uploadRow({ id: 'job-8', fileName: 'apr.json', status: 'COMPLETED' }),
      ],
    })
    renderPage()

    await userEvent.click(
      await screen.findByRole('button', { name: 'Use latest upload (apr.json)' }),
    )
    expect(mockPost).toHaveBeenCalledWith('/v1/workflows/wf-1/use-upload', {
      uploadJobId: 'job-8',
    })
  })

  it('tells the user the re-upload reset earlier results once it completes', async () => {
    routeApi({
      detailResponse: detail({ stage: 'FINDINGS_REVIEW', uploadJobId: 'job-1' }),
      uploads: [uploadRow({ id: 'job-1', status: 'COMPLETED' })],
    })
    mockPost.mockResolvedValue({ data: { ...baseWorkflow({ stage: 'DATA_UPLOADED' }), warnings: [] } })
    renderPage()

    const input = await screen.findByTestId('workflow-file-input')
    await userEvent.upload(input, new File(['{}'], 'apr.json', { type: 'application/json' }))
    await userEvent.click(screen.getByRole('button', { name: 'Upload and bind' }))

    expect(
      await screen.findByText(
        'Upload replaced. Earlier findings and results were reset and the workflow is back at Data uploaded.',
      ),
    ).toBeInTheDocument()
  })

  it('hides the upload step once the workflow is finalized', async () => {
    routeApi({ detailResponse: detail({ stage: 'FINALIZED' }) })
    renderPage()

    await screen.findByTestId('findings-tab')
    expect(screen.queryByRole('button', { name: 'Upload and bind' })).not.toBeInTheDocument()
    expect(screen.queryByText(/Use latest upload/)).not.toBeInTheDocument()
  })

  it('shows a read-only owner no upload or analysis actions', async () => {
    auth.user = { role: 'ROLE_OWNER', organizationId: 'org-1' }
    routeApi({ detailResponse: detail({ stage: 'STARTED' }) })
    renderPage()

    await screen.findByTestId('findings-tab')
    expect(screen.queryByRole('button', { name: 'Upload and bind' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Re-run analysis' })).not.toBeInTheDocument()
    expect(screen.getByTestId('findings-tab')).toHaveTextContent('ROLE_OWNER')
  })

  it('renders events newest first with their actor', async () => {
    routeApi({
      detailResponse: detail({ stage: 'DATA_UPLOADED' }),
      events: [
        event({ id: 't1', kind: 'STARTED', occurredAt: '2025-04-01T10:00:00Z' }),
        event({ id: 't2', kind: 'UPLOAD_BOUND', occurredAt: '2025-04-02T10:00:00Z' }),
      ],
    })
    renderPage()

    await screen.findByText('Upload bound')
    const timeline = screen.getByTestId('workflow-events')
    const items = within(timeline)
      .getAllByRole('listitem')
      .map((item) => item.textContent)
    expect(items[0]).toContain('Upload bound')
    expect(items[1]).toContain('Started')
    expect(items[0]).toContain('op1')
  })

  it('shows the masters warning banner', async () => {
    routeApi({
      detailResponse: detail({ stage: 'FINDINGS_REVIEW' }),
      findings: [
        {
          findingType: 'TAMPER_MASTER_MODIFIED',
          blocking: false,
          message: 'GSTIN of Acme Traders changed.',
        },
      ],
    })
    renderPage()

    expect(
      await screen.findByText(
        'A masters change affects a finalized return of another type. It does not block this workflow.',
      ),
    ).toBeInTheDocument()
  })

  it('shows the payment months that reviewed the monthly findings above a 26Q workflow\'s findings', async () => {
    routeApi({
      detailResponse: detail(
        { type: 'TDS_26Q', periodKey: '2025-Q1', stage: 'PREPARE' },
        {
          dependencies: [
            { type: 'TDS_PAYMENT', periodKey: '2025-04', satisfied: true },
            { type: 'TDS_PAYMENT', periodKey: '2025-05', satisfied: true },
            { type: 'TDS_PAYMENT', periodKey: '2025-06', satisfied: true },
          ],
        },
      ),
      parentWorkflows: [
        baseWorkflow({ id: 'p-apr', type: 'TDS_PAYMENT', periodKey: '2025-04' }),
        baseWorkflow({ id: 'p-may', type: 'TDS_PAYMENT', periodKey: '2025-05' }),
        baseWorkflow({ id: 'p-jun', type: 'TDS_PAYMENT', periodKey: '2025-06' }),
      ],
    })
    renderPage()

    expect(await screen.findAllByText(/Monthly findings were reviewed on/)).toHaveLength(3)
  })

  it('shows the challans for a TDS payment workflow in Prepare', async () => {
    routeApi({
      detailResponse: detail({ type: 'TDS_PAYMENT', stage: 'PREPARE' }),
      challans: [
        {
          id: 'c1',
          paymentWorkflowId: 'wf-1',
          bsrCode: '1234567',
          depositDate: '2025-05-07',
          challanSerialNumber: '111',
          cin: null,
          tdsAmount: 3000,
          surcharge: 0,
          educationCess: 0,
          interest: 0,
          penalty: 0,
          totalAmount: 3000,
          nilChallan: false,
          status: 'APPROVED',
          proposedBy: 'op1',
          proposedAt: '2025-05-07T10:00:00Z',
          approvedBy: 'acct',
          approvedAt: '2025-05-08T10:00:00Z',
        },
      ],
    })
    renderPage()

    expect(await screen.findByText('111')).toBeInTheDocument()
  })

  it('shows amendment follow-ups on a finalized workflow', async () => {
    routeApi({
      detailResponse: detail({ stage: 'FINALIZED' }),
      followUps: [
        {
          id: 'fu-1',
          voucherKey: 'MASTER:acme-traders:gstin',
          summary: 'GSTIN of Acme Traders changed.',
          status: 'OPEN',
          doneBy: null,
          doneAt: null,
          doneNote: null,
          createdAt: '2025-05-10T10:00:00Z',
        },
      ],
    })
    renderPage()

    await screen.findByTestId('findings-tab')
    expect(await screen.findByText('Amendment follow-ups')).toBeInTheDocument()
    expect(await screen.findByText('GSTIN of Acme Traders changed.')).toBeInTheDocument()
  })
})
