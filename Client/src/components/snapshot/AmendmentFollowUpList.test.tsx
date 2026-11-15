import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { AmendmentFollowUpList } from './AmendmentFollowUpList'
import type { ReviewRole } from '@/components/review/FindingReviewPanel'
import { api } from '@/lib/api'

vi.mock('@/lib/api', () => ({ api: { get: vi.fn(), post: vi.fn() } }))

const mockGet = vi.mocked(api.get)
const mockPost = vi.mocked(api.post)

function renderList(role: ReviewRole = 'ROLE_ACCOUNTANT', workflowId = 'w1') {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={client}>
      <AmendmentFollowUpList workflowId={workflowId} role={role} />
    </QueryClientProvider>,
  )
}

function followUp(overrides: Partial<Record<string, unknown>> = {}) {
  return {
    id: 'a1',
    voucherKey: 'G:g-1',
    summary: 'Sales SB/001 dated 2025-04-15 was modified after GSTR1 2025-04 was finalized',
    status: 'OPEN',
    doneBy: null,
    doneAt: null,
    doneNote: null,
    createdAt: '2025-05-01T00:00:00Z',
    ...overrides,
  }
}

describe('AmendmentFollowUpList', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('lists follow-ups', async () => {
    mockGet.mockResolvedValueOnce({ data: { followUps: [followUp()] } })
    renderList()
    await waitFor(() =>
      expect(
        screen.getByText(/Sales SB\/001 dated 2025-04-15 was modified/),
      ).toBeInTheDocument(),
    )
    expect(mockGet).toHaveBeenCalledWith('/v1/workflows/w1/amendments')
  })

  it('accountant marks a follow-up done with a note', async () => {
    mockGet.mockResolvedValueOnce({ data: { followUps: [followUp()] } })
    mockGet.mockResolvedValueOnce({
      data: { followUps: [followUp({ status: 'DONE', doneBy: 'acc', doneNote: 'Amended in GSTR-1 May' })] },
    })
    mockPost.mockResolvedValueOnce({
      data: followUp({ status: 'DONE', doneBy: 'acc', doneNote: 'Amended in GSTR-1 May' }),
    })
    renderList()
    const user = userEvent.setup()
    await waitFor(() => expect(screen.getByRole('button', { name: 'Mark done' })).toBeInTheDocument())
    await user.click(screen.getByRole('button', { name: 'Mark done' }))
    await user.type(screen.getByRole('textbox'), 'Amended in GSTR-1 May')
    await user.click(screen.getByRole('button', { name: 'Save' }))
    await waitFor(() =>
      expect(mockPost).toHaveBeenCalledWith('/v1/workflows/w1/amendments/a1/done', {
        note: 'Amended in GSTR-1 May',
      }),
    )
    await waitFor(() =>
      expect(screen.getByText(/Amended in GSTR-1 May/)).toBeInTheDocument(),
    )
  })

  it('hides Mark done for operator, owner and auditor', async () => {
    mockGet.mockResolvedValue({ data: { followUps: [followUp()] } })
    for (const role of ['ROLE_OPERATOR', 'ROLE_OWNER', 'ROLE_AUDITOR_CA'] as const) {
      renderList(role)
      await waitFor(() => expect(screen.getByText(/Sales SB\/001/)).toBeInTheDocument())
      expect(screen.queryByRole('button', { name: 'Mark done' })).not.toBeInTheDocument()
    }
  })

  it('shows the empty state', async () => {
    mockGet.mockResolvedValueOnce({ data: { followUps: [] } })
    renderList()
    await waitFor(() => expect(screen.getByText('No amendment follow-ups.')).toBeInTheDocument())
  })
})
