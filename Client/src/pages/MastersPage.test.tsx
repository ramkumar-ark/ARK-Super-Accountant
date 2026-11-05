import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MastersPage } from './MastersPage'
import { useAuthStore } from '@/store/authStore'

vi.mock('@/components/AppShell', () => ({
  AppShell: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
}))
vi.mock('@/lib/api', () => ({
  api: {
    get: vi.fn(),
    post: vi.fn(),
    patch: vi.fn(),
  },
}))

import { api } from '@/lib/api'

const mockGet = vi.mocked(api.get)
const mockPost = vi.mocked(api.post)
const mockPatch = vi.mocked(api.patch)

const ACTION_URL = '/v1/uploads/job-1/findings/finding-1/action'

const baseFinding = {
  id: 'finding-1',
  uploadJobId: 'job-1',
  ruleCode: 'GSTIN_PRESENCE',
  mismatchType: null as string | null,
  ledgerName: 'Acme Traders',
  category: 'SUNDRY_DEBTOR',
  actualValue: null as string | null,
  severity: 'HIGH' as const,
  message: 'GSTIN missing',
  suggestedFix: null,
  resolveStatus: 'OPEN' as 'OPEN' | 'ACKNOWLEDGED' | 'APPROVED',
  resolveNote: null as string | null,
  resolvedBy: null,
  proposedValue: null as string | null,
  submittedBy: null as string | null,
  reviewedBy: null as string | null,
  reviewNote: null as string | null,
}

function setRole(role: string) {
  useAuthStore.setState({
    user: { id: 1, username: 'u', email: 'u@t.com', role },
    isAuthenticated: true,
  })
}

describe('MastersPage — findings resolution (two-stage)', () => {
  let current: typeof baseFinding

  beforeEach(() => {
    vi.clearAllMocks()
    current = { ...baseFinding }
    setRole('ROLE_OPERATOR')
    mockGet.mockImplementation((url: string) => {
      if (url.startsWith('/v1/preconfigured-masters')) {
        return Promise.resolve({ data: { content: [], totalElements: 0 } })
      }
      if (url.includes('size=1&showResolved=false')) {
        return Promise.resolve({ data: { totalElements: 1 } })
      }
      if (url.startsWith('/v1/uploads/latest/mismatches')) {
        return Promise.resolve({ data: { content: [current] } })
      }
      return Promise.resolve({ data: {} })
    })
    mockPost.mockResolvedValue({ data: {} })
  })

  async function openFindingsTab() {
    render(<MastersPage />)
    const findingsTab = await screen.findByRole('tab', { name: /Findings/ })
    await waitFor(() => expect(findingsTab).not.toBeDisabled())
    await userEvent.click(findingsTab)
    await screen.findByText('Acme Traders')
  }

  it('no longer offers Accept Fix and never calls the removed PATCH endpoint', async () => {
    await openFindingsTab()

    expect(screen.queryByRole('button', { name: /Accept Fix/ })).toBeNull()
    expect(mockPatch).not.toHaveBeenCalled()
  })

  it('operator submits an auto-derived override for review with only the note', async () => {
    current = { ...current, ruleCode: 'MISMATCH_DETECTION', mismatchType: 'PARENT_GROUP_MISMATCH', actualValue: 'Direct Expenses' }
    await openFindingsTab()

    await userEvent.click(screen.getByRole('button', { name: /Override Value/ }))
    expect(screen.queryByLabelText(/required/)).toBeNull() // no value input for auto-derived types
    expect(screen.getByText('Direct Expenses')).toBeInTheDocument() // shows what will be applied
    await userEvent.type(screen.getByPlaceholderText(/Add a note/), 'manual override')
    await userEvent.click(screen.getByRole('button', { name: /Submit for Review/ }))

    await waitFor(() => {
      expect(mockPost).toHaveBeenCalledWith(ACTION_URL, { action: 'ACKNOWLEDGE', note: 'manual override' })
    })
  })

  it('accountant sees Confirm Override instead of Submit for Review', async () => {
    setRole('ROLE_ACCOUNTANT')
    current = { ...current, ruleCode: 'MISMATCH_DETECTION', mismatchType: 'NAME_MISMATCH' }
    await openFindingsTab()

    await userEvent.click(screen.getByRole('button', { name: /Override Value/ }))

    expect(screen.getByRole('button', { name: /Confirm Override/ })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Submit for Review/ })).toBeNull()
  })

  it('GSTIN override keeps submit disabled until a value is typed, then posts it', async () => {
    await openFindingsTab()

    await userEvent.click(screen.getByRole('button', { name: /Override Value/ }))
    const submit = screen.getByRole('button', { name: /Submit for Review/ })
    expect(submit).toBeDisabled()

    await userEvent.type(screen.getByLabelText(/GSTIN/), '29ABCDE1234F1Z5')
    expect(submit).toBeEnabled()
    await userEvent.click(submit)

    await waitFor(() => {
      expect(mockPost).toHaveBeenCalledWith(ACTION_URL, {
        action: 'ACKNOWLEDGE',
        note: '',
        value: '29ABCDE1234F1Z5',
      })
    })
  })

  it('GST applicability override renders a select of the six enum values', async () => {
    current = { ...current, ruleCode: 'GST_APPLICABILITY' }
    await openFindingsTab()

    await userEvent.click(screen.getByRole('button', { name: /Override Value/ }))
    const select = screen.getByLabelText(/GST applicability/)
    const options = Array.from(select.querySelectorAll('option')).map((o) => o.value).filter(Boolean)

    expect(options).toEqual(['TAXABLE', 'EXEMPT', 'ZERO_RATED', 'NON_GST', 'RCM', 'NOT_APPLICABLE'])
    await userEvent.selectOptions(select, 'RCM')
    await userEvent.click(screen.getByRole('button', { name: /Submit for Review/ }))
    await waitFor(() => {
      expect(mockPost).toHaveBeenCalledWith(ACTION_URL, { action: 'ACKNOWLEDGE', note: '', value: 'RCM' })
    })
  })

  it('shows the server error message in the finding row', async () => {
    mockPost.mockRejectedValue({ response: { data: { error: 'Master changed since submission; reject and resubmit.' } } })
    current = { ...current, ruleCode: 'MISMATCH_DETECTION', mismatchType: 'NAME_MISMATCH' }
    await openFindingsTab()

    await userEvent.click(screen.getByRole('button', { name: /Override Value/ }))
    await userEvent.click(screen.getByRole('button', { name: /Submit for Review/ }))

    expect(await screen.findByText(/Master changed since submission/)).toBeInTheDocument()
  })

  it('operator sees a pending finding as awaiting accountant review with no action buttons', async () => {
    current = { ...current, resolveStatus: 'ACKNOWLEDGED', proposedValue: '29ABCDE1234F1Z5', submittedBy: 'op1' }
    await openFindingsTab()

    expect(screen.getByText('Pending accountant review')).toBeInTheDocument()
    expect(screen.getByText('29ABCDE1234F1Z5')).toBeInTheDocument()
    expect(screen.getByText(/submitted by op1/)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Approve/ })).toBeNull()
    expect(screen.queryByRole('button', { name: /Reject/ })).toBeNull()
    expect(screen.queryByRole('button', { name: /Override Value/ })).toBeNull()
  })

  it('accountant approves a pending finding', async () => {
    setRole('ROLE_ACCOUNTANT')
    current = { ...current, resolveStatus: 'ACKNOWLEDGED', proposedValue: '29ABCDE1234F1Z5', submittedBy: 'op1' }
    await openFindingsTab()

    await userEvent.click(screen.getByRole('button', { name: /^Approve Acme/ }))

    await waitFor(() => expect(mockPost).toHaveBeenCalledWith(ACTION_URL, { action: 'APPROVE' }))
  })

  it('accountant must give a note to reject, then posts REJECT with it', async () => {
    setRole('ROLE_ACCOUNTANT')
    current = { ...current, resolveStatus: 'ACKNOWLEDGED', proposedValue: 'BAD', submittedBy: 'op1' }
    await openFindingsTab()

    await userEvent.click(screen.getByRole('button', { name: /^Reject/ }))
    const confirm = screen.getByRole('button', { name: /Confirm Reject/ })
    expect(confirm).toBeDisabled()

    await userEvent.type(screen.getByPlaceholderText(/Explain why/), 'not a valid GSTIN')
    expect(confirm).toBeEnabled()
    await userEvent.click(confirm)

    await waitFor(() => {
      expect(mockPost).toHaveBeenCalledWith(ACTION_URL, { action: 'REJECT', note: 'not a valid GSTIN' })
    })
  })

  it('rejected finding shows reviewer note and pre-fills the previous proposal', async () => {
    current = {
      ...current,
      proposedValue: 'BAD',
      resolveNote: 'my reason',
      reviewedBy: 'acct1',
      reviewNote: 'not a valid GSTIN',
    }
    await openFindingsTab()

    expect(screen.getByText(/Rejected by acct1: not a valid GSTIN/)).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: /Override Value/ }))
    expect(screen.getByLabelText(/GSTIN/)).toHaveValue('BAD')
    expect(screen.getByPlaceholderText(/Add a note/)).toHaveValue('my reason')
  })

  it.each(['ROLE_AUDITOR_CA', 'ROLE_OWNER'])('%s sees no action buttons on open or pending findings', async (role) => {
    setRole(role)
    current = { ...current, resolveStatus: 'ACKNOWLEDGED', proposedValue: 'V', submittedBy: 'op1' }
    await openFindingsTab()

    expect(screen.queryByRole('button', { name: /Override Value/ })).toBeNull()
    expect(screen.queryByRole('button', { name: /Approve/ })).toBeNull()
    expect(screen.queryByRole('button', { name: /Reject/ })).toBeNull()
  })

  it('auditor sees no Override button on an open finding', async () => {
    setRole('ROLE_AUDITOR_CA')
    await openFindingsTab()

    expect(screen.queryByRole('button', { name: /Override Value/ })).toBeNull()
  })
})

describe('MastersPage — show resolved, reason filter and bulk actions', () => {
  const BULK_URL = '/v1/uploads/job-1/findings/bulk-action'
  const mk = (id: string, over: Record<string, unknown> = {}) => ({
    ...baseFinding,
    id,
    ledgerName: `Ledger ${id}`,
    ruleCode: 'MISMATCH_DETECTION',
    mismatchType: 'MISSING_IN_CONFIGURATION',
    ...over,
  })
  let rows: Array<ReturnType<typeof mk>>

  beforeEach(() => {
    vi.clearAllMocks()
    setRole('ROLE_OPERATOR')
    rows = [
      mk('a'),
      mk('b'),
      mk('done', { resolveStatus: 'APPROVED' }),
      mk('pend', { resolveStatus: 'ACKNOWLEDGED', proposedValue: 'V', submittedBy: 'op1' }),
    ]
    mockGet.mockImplementation((url: string) => {
      if (url.startsWith('/v1/preconfigured-masters')) {
        return Promise.resolve({ data: { content: [], totalElements: 0 } })
      }
      if (url.includes('size=1&showResolved=false')) return Promise.resolve({ data: { totalElements: 3 } })
      if (url.startsWith('/v1/uploads/latest/mismatches')) {
        const q = new URLSearchParams(url.split('?')[1])
        const closed = (s: string) => ['APPROVED', 'RESOLVED', 'DISCARDED'].includes(s)
        let out = rows.filter((r) => (q.get('resolvedOnly') === 'true' ? closed(r.resolveStatus) : q.get('showResolved') === 'true' || !closed(r.resolveStatus)))
        if (q.get('mismatchType')) out = out.filter((r) => r.mismatchType === q.get('mismatchType'))
        return Promise.resolve({ data: { content: out, totalElements: out.length } })
      }
      return Promise.resolve({ data: {} })
    })
    mockPost.mockResolvedValue({ data: {} })
  })

  async function openTab() {
    render(<MastersPage />)
    const tab = await screen.findByRole('tab', { name: /Findings/ })
    await waitFor(() => expect(tab).not.toBeDisabled())
    await userEvent.click(tab)
    await screen.findByText('Ledger a')
  }

  it('show resolved unchecked hides resolved findings; checked lists only resolved', async () => {
    await openTab()
    expect(screen.queryByText('Ledger done')).toBeNull()
    expect(screen.getByText('Ledger pend')).toBeInTheDocument()

    await userEvent.click(screen.getByLabelText(/Show resolved/i))

    await screen.findByText('Ledger done')
    expect(screen.queryByText('Ledger a')).toBeNull()
    expect(screen.queryByText('Ledger pend')).toBeNull()

    await userEvent.click(screen.getByLabelText(/Show resolved/i))
    await screen.findByText('Ledger a')
    expect(screen.queryByText('Ledger done')).toBeNull()
  })

  it('reason filter is sent as the mismatchType param', async () => {
    await openTab()
    await userEvent.selectOptions(screen.getByLabelText('Filter by reason'), 'MISSING_IN_CONFIGURATION')
    await waitFor(() =>
      expect(mockGet).toHaveBeenCalledWith(expect.stringContaining('mismatchType=MISSING_IN_CONFIGURATION')),
    )
  })

  it('operator bulk-submits selected findings for review', async () => {
    await openTab()
    await userEvent.click(screen.getByLabelText('Select Ledger a'))
    await userEvent.click(screen.getByLabelText('Select Ledger b'))
    await userEvent.click(screen.getByRole('button', { name: /Submit for review selected \(2\)/ }))

    await waitFor(() =>
      expect(mockPost).toHaveBeenCalledWith(BULK_URL, { action: 'ACKNOWLEDGE', findingIds: ['a', 'b'] }),
    )
    expect(screen.queryByRole('button', { name: /Approve/ })).toBeNull()
  })

  it('operator bulk-submits all matching the current filter', async () => {
    await openTab()
    await userEvent.selectOptions(screen.getByLabelText('Filter by reason'), 'MISSING_IN_CONFIGURATION')
    await userEvent.click(await screen.findByRole('button', { name: /Submit for review all matching \(2\)/ }))

    await waitFor(() =>
      expect(mockPost).toHaveBeenCalledWith(BULK_URL, {
        action: 'ACKNOWLEDGE',
        mismatchType: 'MISSING_IN_CONFIGURATION',
      }),
    )
  })

  it('accountant can bulk resolve and bulk approve pending', async () => {
    setRole('ROLE_ACCOUNTANT')
    await openTab()
    await userEvent.click(screen.getByLabelText('Select Ledger a'))
    await userEvent.click(screen.getByRole('button', { name: /Resolve selected \(1\)/ }))
    await waitFor(() =>
      expect(mockPost).toHaveBeenCalledWith(BULK_URL, { action: 'ACKNOWLEDGE', findingIds: ['a'] }),
    )

    await userEvent.click(screen.getByLabelText('Select Ledger pend'))
    await userEvent.click(screen.getByRole('button', { name: /Approve selected \(1\)/ }))
    await waitFor(() =>
      expect(mockPost).toHaveBeenCalledWith(BULK_URL, { action: 'APPROVE', findingIds: ['pend'] }),
    )
  })

  it('shows every failure when a bulk action is rejected', async () => {
    mockPost.mockRejectedValueOnce({
      response: {
        data: {
          error: '1 finding(s) cannot be resolved; nothing was changed.',
          failures: [{ findingId: 'a', ledgerName: 'Ledger a', error: 'needs a value' }],
        },
      },
    })
    await openTab()
    await userEvent.click(screen.getByLabelText('Select Ledger a'))
    await userEvent.click(screen.getByRole('button', { name: /Submit for review selected/ }))

    expect(await screen.findByText(/nothing was changed/)).toBeInTheDocument()
    expect(screen.getByText(/Ledger a: needs a value/)).toBeInTheDocument()
  })

  it.each(['ROLE_AUDITOR_CA', 'ROLE_OWNER'])('%s sees no bulk controls', async (role) => {
    setRole(role)
    await openTab()
    expect(screen.queryByLabelText(/Select all/)).toBeNull()
    expect(screen.queryByLabelText('Select Ledger a')).toBeNull()
    expect(screen.queryByRole('button', { name: /selected|all matching|all pending/ })).toBeNull()
  })
})
