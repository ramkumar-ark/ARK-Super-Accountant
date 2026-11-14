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

const mastersFinding = (overrides: Record<string, unknown>) => ({
  ...finding,
  source: 'MASTERS',
  ...overrides,
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

  describe('MASTERS findings', () => {
    it('MASTERS finding shows an "Open in Masters" link to /masters', async () => {
      mockGet.mockResolvedValue({
        data: { findings: [mastersFinding({})], blockingCount: 1 },
      })

      renderTab()

      const link = await screen.findByRole('link', { name: 'Open Acme in Masters' })
      expect(link).toHaveAttribute('href', '/masters')
    })

    it('MASTERS mismatch finding (NAME_MISMATCH, OPEN): operator can submit in place with value = actualValue', async () => {
      mockGet.mockResolvedValue({
        data: {
          findings: [
            mastersFinding({
              findingType: 'NAME_MISMATCH',
              actualValue: 'Acme Pvt Ltd',
            }),
          ],
          blockingCount: 1,
        },
      })
      mockPost.mockResolvedValue({ data: {} })

      render(
        <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
          <WorkflowFindingsTab workflowId="wf-1" role="ROLE_OPERATOR" />
        </QueryClientProvider>,
      )

      await userEvent.click(await screen.findByRole('button', { name: 'Override Value for f1' }))
      await userEvent.click(screen.getByRole('button', { name: 'Submit for Review' }))

      await waitFor(() =>
        expect(mockPost).toHaveBeenCalledWith('/v1/workflows/wf-1/findings/f1/action', {
          action: 'ACKNOWLEDGE',
          note: '',
          value: 'Acme Pvt Ltd',
        }),
      )
    })

    it('MASTERS typed finding (HSN_SAC_CODE, OPEN): no Override Value button; shows "Enter the corrected value in Masters."', async () => {
      mockGet.mockResolvedValue({
        data: {
          findings: [mastersFinding({ findingType: 'HSN_SAC_CODE' })],
          blockingCount: 1,
        },
      })

      renderTab()

      await screen.findByRole('link', { name: 'Open Acme in Masters' })
      expect(screen.getByText('Enter the corrected value in Masters.')).toBeInTheDocument()
      expect(screen.queryByRole('button', { name: 'Override Value for f1' })).not.toBeInTheDocument()
    })

    it('MASTERS typed finding ACKNOWLEDGED: accountant approves in place and the findings query is invalidated', async () => {
      mockPost.mockResolvedValue({ data: {} })
      mockGet.mockResolvedValue({ data: { findings: [], blockingCount: 0 } })
      mockGet.mockResolvedValueOnce({
        data: {
          findings: [
            mastersFinding({
              findingType: 'HSN_SAC_CODE',
              resolveStatus: 'ACKNOWLEDGED',
              proposedValue: '1101',
              submittedBy: 'op',
            }),
          ],
          blockingCount: 1,
        },
      })

      renderTab()

      await userEvent.click(await screen.findByRole('button', { name: 'Approve f1' }))

      await waitFor(() =>
        expect(mockPost).toHaveBeenCalledWith('/v1/workflows/wf-1/findings/f1/action', {
          action: 'APPROVE',
        }),
      )
      await waitFor(() => expect(screen.getByText('No findings')).toBeInTheDocument())
    })

    it('non-MASTERS finding has no Masters link', async () => {
      mockGet.mockResolvedValue({ data: { findings: [finding], blockingCount: 1 } })

      renderTab()

      await screen.findByText('Ledger name mismatch')
      expect(screen.queryByRole('link', { name: /Open .* in Masters/ })).not.toBeInTheDocument()
    })
  })
})
