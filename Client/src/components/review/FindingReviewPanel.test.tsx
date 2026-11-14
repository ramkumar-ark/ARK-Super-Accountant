import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { FindingReviewPanel, type ReviewFinding } from './FindingReviewPanel'

const baseFinding: ReviewFinding = {
  id: 'finding-1',
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
}

beforeEach(() => {
  vi.clearAllMocks()
})

function renderPanel(overrides: Partial<ReviewFinding> = {}, role = 'ROLE_OPERATOR') {
  const finding = { ...baseFinding, ...overrides }
  const onAction = vi.fn().mockResolvedValue(undefined)
  render(
    <FindingReviewPanel
      finding={finding}
      role={role as never}
      label="Acme Traders"
      valueInput={<input aria-label="Override value" />}
      onAction={onAction}
    />,
  )
  return onAction
}

describe('FindingReviewPanel', () => {
  it('operator sees Submit on OPEN, no Approve', () => {
    renderPanel()

    expect(screen.getByRole('button', { name: /Override Value/ })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /^Approve/ })).toBeNull()
    expect(screen.queryByRole('button', { name: /^Reject/ })).toBeNull()
  })

  it('accountant sees Approve and Reject on ACKNOWLEDGED; Reject requires a note', async () => {
    const onAction = renderPanel(
      {
        resolveStatus: 'ACKNOWLEDGED',
        proposedValue: '29ABCDE1234F1Z5',
        submittedBy: 'op1',
      },
      'ROLE_ACCOUNTANT',
    )

    expect(screen.getByRole('button', { name: /^Approve/ })).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: /^Reject/ }))
    const confirm = screen.getByRole('button', { name: /Confirm Reject/ })
    expect(confirm).toBeDisabled()
    await userEvent.type(screen.getByPlaceholderText(/Explain why/), 'not a valid GSTIN')
    expect(confirm).toBeEnabled()
    await userEvent.click(confirm)
    expect(onAction).toHaveBeenCalledWith('REJECT', { note: 'not a valid GSTIN' })
  })

  it('auditor sees no action buttons', () => {
    renderPanel({}, 'ROLE_AUDITOR_CA')

    expect(screen.queryByRole('button', { name: /Override Value/ })).toBeNull()
    expect(screen.queryByRole('button', { name: /^Approve/ })).toBeNull()
    expect(screen.queryByRole('button', { name: /^Reject/ })).toBeNull()
  })

  it('shows previous reviewer note after a rejection', () => {
    renderPanel({
      resolveStatus: 'OPEN',
      reviewNote: 'not a valid GSTIN',
      reviewedBy: 'acct1',
    })

    expect(screen.getByText(/Rejected by acct1: not a valid GSTIN/)).toBeInTheDocument()
  })

  it('owner sees no action buttons', () => {
    renderPanel({}, 'ROLE_OWNER')

    expect(screen.queryByRole('button', { name: /Override Value/ })).toBeNull()
    expect(screen.queryByRole('button', { name: /^Approve/ })).toBeNull()
  })
})
