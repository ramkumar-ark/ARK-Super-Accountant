import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { WorkflowFindingsTab } from './WorkflowFindingsTab'
import { api } from '@/lib/api'

vi.mock('@/lib/api', () => ({
  api: { get: vi.fn(), post: vi.fn() },
}))

const mockGet = vi.mocked(api.get)
const mockPost = vi.mocked(api.post)

const finding = {
  id: 'f1',
  source: 'FAKE',
  findingType: 'FAKE_TYPE',
  severity: 'ERROR',
  blocking: true,
  message: 'Ledger name mismatch',
  expectedValue: null,
  actualValue: null,
  voucherNumber: 'V1',
  voucherDate: '2025-04-05',
  ledgerName: 'Acme',
  resolveStatus: 'OPEN',
  resolveNote: null,
  proposedValue: null,
  submittedBy: null,
  submittedAt: null,
  reviewedBy: null,
  reviewedAt: null,
  reviewNote: null,
  resolvedBy: null,
  resolvedAt: null,
  carriedFromFindingId: null,
}

function renderTab() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={client}>
      <WorkflowFindingsTab workflowId="wf-1" role="ROLE_ACCOUNTANT" />
    </QueryClientProvider>,
  )
}

beforeEach(() => {
  vi.clearAllMocks()
})

describe('WorkflowFindingsTab', () => {
  it('shows blocking count and lists findings', async () => {
    mockGet.mockResolvedValue({ data: { findings: [finding], blockingCount: 1 } })

    renderTab()

    await screen.findByText('Ledger name mismatch')
    expect(screen.getByText(/1 blocking finding/)).toBeInTheDocument()
    expect(screen.getByText('FAKE')).toBeInTheDocument()
  })

  it('shows "No findings" when empty', async () => {
    mockGet.mockResolvedValue({ data: { findings: [], blockingCount: 0 } })

    renderTab()

    await waitFor(() => expect(screen.getByText('No findings')).toBeInTheDocument())
  })

  it('posts an action through the finding panel', async () => {
    mockGet.mockResolvedValue({
      data: { findings: [{ ...finding, resolveStatus: 'ACKNOWLEDGED', proposedValue: 'V', submittedBy: 'op' }], blockingCount: 1 },
    })
    mockPost.mockResolvedValue({ data: {} })

    renderTab()

    await userEvent.click(await screen.findByRole('button', { name: 'Approve f1' }))
    mockGet.mockResolvedValue({ data: { findings: [], blockingCount: 0 } })

    await waitFor(() =>
      expect(mockPost).toHaveBeenCalledWith('/v1/workflows/wf-1/findings/f1/action', {
        action: 'APPROVE',
      }),
    )
  })
})
