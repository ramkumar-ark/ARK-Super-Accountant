import { describe, it, expect, vi, beforeEach } from 'vitest'
import { fireEvent, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { PaymentPreparePanel } from './PaymentPreparePanel'
import { api } from '@/lib/api'
import type { Workflow } from '@/lib/workflowApi'
import type { ChallanListResponse, TdsChallan } from '@/lib/tdsApi'

vi.mock('@/lib/api', () => ({
  api: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
}))

const mockGet = vi.mocked(api.get)
const mockPost = vi.mocked(api.post)
const mockPut = vi.mocked(api.put)
const mockDelete = vi.mocked(api.delete)

function workflow(overrides: Partial<Workflow> = {}): Workflow {
  return {
    id: 'wf-1',
    type: 'TDS_PAYMENT',
    periodStart: '2025-04-01',
    periodEnd: '2025-04-30',
    periodKey: '2025-04',
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

function challan(overrides: Partial<TdsChallan> = {}): TdsChallan {
  return {
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
    ...overrides,
  }
}

function route(payload: ChallanListResponse) {
  mockGet.mockResolvedValue({ data: payload })
}

function renderPanel(role = 'ROLE_ACCOUNTANT', stage: Workflow['stage'] = 'PREPARE') {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={client}>
      <PaymentPreparePanel workflow={workflow({ stage })} role={role} />
    </QueryClientProvider>,
  )
}

async function fillNewChallan(serial = '111', tds = '5000') {
  await userEvent.click(await screen.findByRole('button', { name: 'Add challan' }))
  await userEvent.type(screen.getByLabelText('BSR code'), '1234567')
  fireEvent.change(screen.getByLabelText('Deposit date'), { target: { value: '2025-05-07' } })
  await userEvent.type(screen.getByLabelText('Challan serial number'), serial)
  await userEvent.type(screen.getByLabelText('TDS amount'), tds)
}

beforeEach(() => {
  vi.clearAllMocks()
  mockPost.mockResolvedValue({ data: challan() })
  mockPut.mockResolvedValue({ data: challan() })
  mockDelete.mockResolvedValue({ data: {} })
})

describe('PaymentPreparePanel', () => {
  it('shows TDS deducted, approved cover and the shortfall', async () => {
    route({
      challans: [challan(), challan({ id: 'c2', status: 'PROPOSED', approvedBy: null })],
      tdsDeducted: 5000,
      approvedTdsTotal: 3000,
      excess: 0,
    })
    renderPanel()

    expect(await screen.findByText('Short by 2,000')).toBeInTheDocument()
    expect(screen.getByText('Approved')).toBeInTheDocument()
    expect(screen.getByText('Proposed')).toBeInTheDocument()
  })

  it('shows an excess without blocking and "not computed" before analysis', async () => {
    route({
      challans: [challan()],
      tdsDeducted: 4000,
      approvedTdsTotal: 4500,
      excess: 500,
    })
    const { unmount } = render(
      <QueryClientProvider
        client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}
      >
        <PaymentPreparePanel workflow={workflow()} role="ROLE_ACCOUNTANT" />
      </QueryClientProvider>,
    )

    expect(await screen.findByText('Excess 500')).toBeInTheDocument()
    expect(screen.queryByText(/Short by/)).not.toBeInTheDocument()
    unmount()

    route({ challans: [], tdsDeducted: null, approvedTdsTotal: 0, excess: null })
    renderPanel()
    expect(await screen.findByText('Not computed yet: run analysis')).toBeInTheDocument()
  })

  it('lets an operator propose a challan but not approve it', async () => {
    route({ challans: [challan({ status: 'PROPOSED', approvedBy: null })], tdsDeducted: 5000, approvedTdsTotal: 3000, excess: 0 })
    renderPanel('ROLE_OPERATOR')

    await screen.findByText('Proposed')
    expect(screen.queryByRole('button', { name: 'Approve' })).not.toBeInTheDocument()

    await fillNewChallan()
    await userEvent.click(screen.getByRole('button', { name: 'Save challan' }))

    expect(mockPost).toHaveBeenCalledWith(
      '/v1/workflows/wf-1/tds/challans',
      expect.objectContaining({
        bsrCode: '1234567',
        challanSerialNumber: '111',
        tdsAmount: 5000,
        nilChallan: false,
      }),
    )
  })

  it('lets an accountant approve a proposed challan', async () => {
    route({ challans: [challan({ status: 'PROPOSED', approvedBy: null })], tdsDeducted: 5000, approvedTdsTotal: 3000, excess: 0 })
    renderPanel('ROLE_ACCOUNTANT')

    await userEvent.click(await screen.findByRole('button', { name: 'Approve' }))
    expect(mockPost).toHaveBeenCalledWith('/v1/workflows/wf-1/tds/challans/c1/approve')
  })

  it('records a nil challan with zero TDS', async () => {
    route({ challans: [], tdsDeducted: null, approvedTdsTotal: 0, excess: null })
    renderPanel('ROLE_OPERATOR')

    await userEvent.click(await screen.findByRole('button', { name: 'Add challan' }))
    await userEvent.type(screen.getByLabelText('BSR code'), '1234567')
    fireEvent.change(screen.getByLabelText('Deposit date'), { target: { value: '2025-05-07' } })
    await userEvent.type(screen.getByLabelText('Challan serial number'), '222')
    await userEvent.click(screen.getByLabelText('Nil challan'))
    expect(screen.getByLabelText('TDS amount')).toBeDisabled()
    await userEvent.click(screen.getByRole('button', { name: 'Save challan' }))

    expect(mockPost).toHaveBeenCalledWith(
      '/v1/workflows/wf-1/tds/challans',
      expect.objectContaining({ nilChallan: true, tdsAmount: 0 }),
    )
  })

  it('shows the server refusal and keeps the form open', async () => {
    route({ challans: [], tdsDeducted: null, approvedTdsTotal: 0, excess: null })
    mockPost.mockRejectedValue({
      response: {
        data: { error: 'Challans can only be changed while the payment workflow is in Prepare' },
      },
    })
    renderPanel('ROLE_OPERATOR')

    await fillNewChallan()
    await userEvent.click(screen.getByRole('button', { name: 'Save challan' }))

    expect(
      await screen.findByText('Challans can only be changed while the payment workflow is in Prepare'),
    ).toBeInTheDocument()
    expect(screen.getByLabelText('BSR code')).toBeInTheDocument()
  })

  it('is read-only for an auditor and after finalize', async () => {
    route({ challans: [challan({ status: 'PROPOSED', approvedBy: null })], tdsDeducted: 5000, approvedTdsTotal: 3000, excess: 0 })
    const { unmount } = render(
      <QueryClientProvider
        client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}
      >
        <PaymentPreparePanel workflow={workflow({ stage: 'PREPARE' })} role="ROLE_AUDITOR_CA" />
      </QueryClientProvider>,
    )
    await screen.findByText('Proposed')
    expect(screen.queryByRole('button', { name: 'Add challan' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Edit' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Delete' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Approve' })).not.toBeInTheDocument()
    unmount()

    route({ challans: [challan({ status: 'PROPOSED', approvedBy: null })], tdsDeducted: 5000, approvedTdsTotal: 3000, excess: 0 })
    renderPanel('ROLE_ACCOUNTANT', 'FINALIZED')
    await screen.findByText('Proposed')
    expect(screen.queryByRole('button', { name: 'Add challan' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Approve' })).not.toBeInTheDocument()
  })
})
