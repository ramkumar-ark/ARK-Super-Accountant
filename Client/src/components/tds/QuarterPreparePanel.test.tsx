import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { QuarterPreparePanel } from './QuarterPreparePanel'
import { api } from '@/lib/api'
import type { Workflow } from '@/lib/workflowApi'
import type {
  QuarterChallans,
  TdsChallan,
  TdsDeducteeEntry,
  TdsGateCondition,
} from '@/lib/tdsApi'

vi.mock('@/lib/api', () => ({
  api: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
}))
vi.mock('@tanstack/react-router', () => ({
  Link: ({ to, children, ...rest }: { to: string; children: React.ReactNode }) => (
    <a href={to} {...rest}>
      {children}
    </a>
  ),
}))

const mockGet = vi.mocked(api.get)
const mockPost = vi.mocked(api.post)

function workflow(overrides: Partial<Workflow> = {}): Workflow {
  return {
    id: 'wf-1',
    type: 'TDS_26Q',
    periodStart: '2025-04-01',
    periodEnd: '2025-06-30',
    periodKey: '2025-Q1',
    financialYear: 2025,
    revision: 0,
    stage: 'PREPARE',
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

function entry(overrides: Partial<TdsDeducteeEntry> = {}): TdsDeducteeEntry {
  return {
    id: 'e1',
    workflowId: 'wf-1',
    tdsChallanId: null,
    deducteeName: 'ABC Contractors',
    deducteePan: 'ABCDE1234F',
    panValid: true,
    deducteeType: 'COMPANY',
    section: '194C',
    amountPaid: 100000,
    tdsDeducted: 1000,
    tdsDeposited: 1000,
    dateOfPayment: '2025-04-10',
    dateOfDeduction: '2025-04-10',
    rateApplied: 1,
    higherRateApplied: false,
    sourceVoucherIds: '[]',
    supersededAt: null,
    ...overrides,
  }
}

const aprilChallan: TdsChallan = {
  id: 'c-apr',
  paymentWorkflowId: 'pw-1',
  bsrCode: '1234567',
  depositDate: '2025-05-07',
  challanSerialNumber: '111',
  cin: null,
  tdsAmount: 1000,
  surcharge: 0,
  educationCess: 0,
  interest: 0,
  penalty: 0,
  totalAmount: 1000,
  nilChallan: false,
  status: 'APPROVED',
  proposedBy: 'op1',
  proposedAt: '2025-05-07T10:00:00Z',
  approvedBy: 'acct',
  approvedAt: '2025-05-08T10:00:00Z',
}

const months: QuarterChallans[] = [
  { paymentWorkflowId: 'pw-1', periodKey: '2025-04', stage: 'FINALIZED', challans: [aprilChallan] },
  { paymentWorkflowId: 'pw-2', periodKey: '2025-05', stage: 'FINALIZED', challans: [] },
]

function gate(overrides: Partial<TdsGateCondition> = {}): TdsGateCondition[] {
  return [
    {
      code: 'ALL_ENTRIES_MAPPED',
      label: 'All entries mapped',
      passed: false,
      detail: '2 entries unmapped',
      ...overrides,
    },
    { code: 'CHALLANS_RECONCILE', label: 'Challans reconcile', passed: true, detail: '' },
    { code: 'PANS_VALID', label: 'PANs valid', passed: true, detail: '0 entries without a valid PAN' },
    { code: 'DEDUCTOR_COMPLETE', label: 'Deductor complete', passed: true, detail: '' },
    { code: 'HAS_CHALLAN', label: 'Has approved challan', passed: true, detail: '' },
  ]
}

const allPassedGate: TdsGateCondition[] = [
  { code: 'ALL_ENTRIES_MAPPED', label: 'All entries mapped', passed: true, detail: '0 entries unmapped' },
  { code: 'CHALLANS_RECONCILE', label: 'Challans reconcile', passed: true, detail: '' },
  { code: 'PANS_VALID', label: 'PANs valid', passed: true, detail: '0 entries without a valid PAN' },
  { code: 'DEDUCTOR_COMPLETE', label: 'Deductor complete', passed: true, detail: '' },
  { code: 'HAS_CHALLAN', label: 'Has approved challan', passed: true, detail: '' },
]

function routeApi({
  entries = [entry()],
  gateConditions = gate(),
  exportResponse,
}: {
  entries?: TdsDeducteeEntry[]
  gateConditions?: TdsGateCondition[]
  exportResponse?: unknown
} = {}) {
  mockGet.mockImplementation((url: string) => {
    if (url === '/tds/sections') {
      return Promise.resolve({
        data: [
          {
            code: '194C',
            description: 'Payments to contractors and sub-contractors',
            legalReference: '393(1) [Table: Sl. No. 6(i)]',
            rpu26qCode: '94C',
            rpu140Code: '1024',
            sortOrder: 2,
          },
        ],
      })
    }
    if (url === '/v1/workflows/wf-1/tds/entries') return Promise.resolve({ data: entries })
    if (url === '/v1/workflows/wf-1/tds/quarter-challans') {
      return Promise.resolve({ data: months })
    }
    if (url === '/v1/workflows/wf-1/tds/gate') return Promise.resolve({ data: gateConditions })
    if (url === '/v1/tds/pan-overrides') return Promise.resolve({ data: [] })
    if (url === '/v1/workflows/wf-1/tds/export' && exportResponse) {
      return Promise.resolve(exportResponse)
    }
    return Promise.reject(new Error('Unexpected GET ' + url))
  })
}

function renderPanel(
  role = 'ROLE_ACCOUNTANT',
  stage: Workflow['stage'] = 'PREPARE',
  workflowOverrides: Partial<Workflow> = {},
) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const view = render(
    <QueryClientProvider client={client}>
      <QuarterPreparePanel workflow={workflow({ stage, ...workflowOverrides })} role={role} />
    </QueryClientProvider>,
  )
  return view
}

beforeEach(() => {
  vi.clearAllMocks()
  mockPost.mockResolvedValue({ data: [] })
})

describe('QuarterPreparePanel', () => {
  it('shows the gate conditions and the approved challans of each payment month', async () => {
    routeApi({ entries: [entry(), entry({ id: 'e2', deducteeName: 'XYZ Traders' })] })
    renderPanel()

    expect(await screen.findByText(/All entries mapped/)).toBeInTheDocument()
    expect(screen.getByText(/2 entries unmapped/)).toBeInTheDocument()
    expect(screen.getByText('No approved challan')).toBeInTheDocument()
    expect(screen.getByText('2025-04 · Finalized')).toBeInTheDocument()
  })

  it('maps an entry to an approved challan of the quarter', async () => {
    routeApi()
    renderPanel()

    const select = await screen.findByLabelText('Challan for ABC Contractors')
    await userEvent.selectOptions(select, 'c-apr')

    expect(mockPost).toHaveBeenCalledWith('/v1/workflows/wf-1/tds/entries/map', {
      challanId: 'c-apr',
      entryIds: ['e1'],
    })
    await waitFor(() =>
      expect(
        mockGet.mock.calls.filter(([url]) => url === '/v1/workflows/wf-1/tds/gate').length,
      ).toBeGreaterThan(1),
    )
  })

  it('shows the challan as plain text and no select once the quarter is finalized', async () => {
    routeApi({ entries: [entry({ tdsChallanId: 'c-apr' })] })
    renderPanel('ROLE_ACCOUNTANT', 'FINALIZED')

    expect(await screen.findByText('2025-04 / 111')).toBeInTheDocument()
    expect(screen.queryByRole('combobox')).not.toBeInTheDocument()
  })

  it('maps several selected entries in one request', async () => {
    routeApi({
      entries: [entry(), entry({ id: 'e2', deducteeName: 'XYZ Traders' })],
    })
    renderPanel()

    await userEvent.click(await screen.findByLabelText('Select ABC Contractors'))
    await userEvent.click(screen.getByLabelText('Select XYZ Traders'))
    await userEvent.selectOptions(screen.getByLabelText('Map selected to'), 'c-apr')
    await userEvent.click(screen.getByRole('button', { name: 'Map selected' }))

    expect(mockPost).toHaveBeenCalledWith('/v1/workflows/wf-1/tds/entries/map', {
      challanId: 'c-apr',
      entryIds: ['e1', 'e2'],
    })
  })

  it('shows the server error when mapping is refused', async () => {
    routeApi()
    mockPost.mockRejectedValue({
      response: { data: { error: 'Challan is not an approved challan of this quarter' } },
    })
    renderPanel()

    await userEvent.selectOptions(
      await screen.findByLabelText('Challan for ABC Contractors'),
      'c-apr',
    )
    expect(
      await screen.findByText('Challan is not an approved challan of this quarter'),
    ).toBeInTheDocument()
  })

  it('lets an accountant download the RPU file once the gate passes and keeps the button disabled while a condition fails', async () => {
    routeApi()
    const { unmount } = renderPanel()
    expect(await screen.findByRole('button', { name: 'Download RPU file' })).toBeDisabled()
    unmount()

    const createObjectURL = vi.fn(() => 'blob:rpu')
    const revokeObjectURL = vi.fn()
    Object.assign(URL, { createObjectURL, revokeObjectURL })
    const downloads: string[] = []
    const clickSpy = vi
      .spyOn(HTMLAnchorElement.prototype, 'click')
      .mockImplementation(function (this: HTMLAnchorElement) {
        downloads.push(this.download)
      })

    routeApi({
      gateConditions: allPassedGate,
      exportResponse: {
        data: new Blob(['1^FH^']),
        headers: {
          'content-disposition': 'attachment; filename="26Q_CHEA12345B_FY2025_Q1.txt"',
        },
      },
    })
    renderPanel()

    const button = await screen.findByRole('button', { name: 'Download RPU file' })
    await waitFor(() => expect(button).toBeEnabled())
    await userEvent.click(button)

    expect(mockGet).toHaveBeenCalledWith('/v1/workflows/wf-1/tds/export', {
      responseType: 'blob',
    })
    await waitFor(() => expect(downloads).toEqual(['26Q_CHEA12345B_FY2025_Q1.txt']))
    expect(createObjectURL).toHaveBeenCalled()
    clickSpy.mockRestore()
  })

  it('links the failed deductor gate condition to the deductor settings section', async () => {
    routeApi({
      gateConditions: [
        { code: 'ALL_ENTRIES_MAPPED', label: 'All entries mapped', passed: true, detail: '0 entries unmapped' },
        { code: 'CHALLANS_RECONCILE', label: 'Challans reconcile', passed: true, detail: '' },
        { code: 'PANS_VALID', label: 'PANs valid', passed: true, detail: '' },
        { code: 'DEDUCTOR_COMPLETE', label: 'Deductor complete', passed: false, detail: 'TAN missing' },
        { code: 'HAS_CHALLAN', label: 'Has approved challan', passed: true, detail: '' },
      ],
    })
    renderPanel()

    expect(await screen.findByText(/Deductor complete/)).toBeInTheDocument()
    const link = screen.getByRole('link', { name: 'deductor settings' })
    expect(link).toHaveAttribute('href', '/settings/compliance')
  })

  it('hides the download button from operators', async () => {
    routeApi({ gateConditions: allPassedGate })
    renderPanel('ROLE_OPERATOR')

    await screen.findByText(/All entries mapped/)
    expect(screen.queryByRole('button', { name: 'Download RPU file' })).not.toBeInTheDocument()
  })

  it('distinguishes missing PAN from invalid PAN in entries table', async () => {
    routeApi({
      entries: [
        entry({ id: 'e-missing', deducteeName: 'No PAN Party', deducteePan: null, panValid: false }),
        entry({ id: 'e-invalid', deducteeName: 'Bad PAN Party', deducteePan: 'INVALID123', panValid: false }),
      ],
    })
    renderPanel()

    await screen.findByText('No PAN Party')
    expect(screen.getByText('missing PAN')).toBeInTheDocument()
    expect(screen.getByText('invalid PAN')).toBeInTheDocument()
  })

  it('shows s.393 table references instead of section codes in new-Act quarters', async () => {
    routeApi({ entries: [entry({ section: '194C' })] })
    renderPanel('ROLE_ACCOUNTANT', 'PREPARE', { periodStart: '2026-04-01' })

    await screen.findByText('393(1) [Table: Sl. No. 6(i)]')
  })

  it('shows plain section codes in old-Act quarters', async () => {
    routeApi({ entries: [entry({ section: '194C' })] })
    renderPanel()

    await screen.findByText('194C')
  })
})
