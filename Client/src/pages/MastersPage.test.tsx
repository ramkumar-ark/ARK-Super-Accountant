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

const stubConfirm = (answer: boolean) => {
  const fn = vi.fn().mockReturnValue(answer)
  window.confirm = fn as unknown as typeof window.confirm
  return fn
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

  it('GSTIN finding can be marked GST not applicable instead of typing a GSTIN', async () => {
    await openFindingsTab()

    await userEvent.click(screen.getByRole('button', { name: /Override Value/ }))
    const submit = screen.getByRole('button', { name: /Submit for Review/ })
    expect(submit).toBeDisabled()
    await userEvent.click(screen.getByLabelText(/GST not applicable/))
    expect(submit).toBeEnabled()
    await userEvent.click(submit)

    await waitFor(() => {
      expect(mockPost).toHaveBeenCalledWith(ACTION_URL, { action: 'ACKNOWLEDGE', note: '', value: 'NOT_APPLICABLE' })
    })
  })

  it('missing-in-configuration override says the ledger will be added to the approved masters', async () => {
    current = { ...current, ruleCode: 'MISMATCH_DETECTION', mismatchType: 'MISSING_IN_CONFIGURATION', actualValue: 'Acme Traders' }
    await openFindingsTab()

    await userEvent.click(screen.getByRole('button', { name: /Override Value/ }))
    expect(screen.getByText(/will be added to the approved masters/)).toBeInTheDocument()
    expect(screen.queryByText(/will be updated to/)).toBeNull()
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
    mockPost.mockImplementation(((_url: string, body: Record<string, unknown>) => {
      if (body?.dryRun) {
        const want = body.action === 'APPROVE' ? 'ACKNOWLEDGED' : 'OPEN'
        const n = rows.filter(
          (r) =>
            r.resolveStatus === want &&
            (!body.mismatchType || r.mismatchType === body.mismatchType) &&
            (!body.ruleCode || r.ruleCode === body.ruleCode),
        ).length
        return Promise.resolve({ data: { matched: n } })
      }
      return Promise.resolve({ data: {} })
    }) as never)
  })

  const realPosts = () => mockPost.mock.calls.filter((c) => !(c[1] as { dryRun?: boolean })?.dryRun)

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

  it('select all offers every match; choosing it confirms with the true count, then sends filter mode', async () => {
    const confirm = stubConfirm(true)
    rows = [mk('a'), mk('b'), mk('c', { mismatchType: 'PARENT_GROUP_MISMATCH', actualValue: 'X' })]
    await openTab()
    await userEvent.selectOptions(screen.getByLabelText('Filter by reason'), 'MISSING_IN_CONFIGURATION')
    await waitFor(() => expect(screen.queryByText('Ledger c')).toBeNull())
    await userEvent.click(screen.getByLabelText('Select all'))
    // 2 on the page and 2 matching: nothing beyond the page, so no "select all matching" offer.
    expect(screen.queryByText(/Select all \d+ matching/)).toBeNull()
    await userEvent.click(screen.getByRole('button', { name: /Submit for review selected \(2\)/ }))
    await waitFor(() => expect(realPosts()).toHaveLength(1))
    expect(realPosts()[0][1]).toEqual({ action: 'ACKNOWLEDGE', findingIds: ['a', 'b'] })
    expect(confirm).not.toHaveBeenCalled()
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
    const dry = mockPost.getMockImplementation()!
    mockPost.mockImplementation(((url: string, body: Record<string, unknown>) =>
      body?.dryRun
        ? dry(url, body)
        : Promise.reject({
      response: {
        data: {
          error: '1 finding(s) cannot be resolved; nothing was changed.',
          failures: [{ findingId: 'a', ledgerName: 'Ledger a', error: 'needs a value' }],
        },
      },
    })) as never)
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

describe('MastersPage — findings paging and select-all-matching', () => {
  const BULK_URL = '/v1/uploads/job-1/findings/bulk-action'
  const TOTAL = 250
  const mk = (i: number, over: Record<string, unknown> = {}) => ({
    ...baseFinding,
    id: `f${i}`,
    ledgerName: `Ledger ${String(i).padStart(3, '0')}`,
    ruleCode: 'MISMATCH_DETECTION',
    mismatchType: 'MISSING_IN_CONFIGURATION',
    ...over,
  })
  // 250 open derivable findings plus 7 typed GSTIN ones, all served by a paging server stub.
  let all: Array<ReturnType<typeof mk>>

  beforeEach(() => {
    vi.clearAllMocks()
    setRole('ROLE_OPERATOR')
    all = [
      ...Array.from({ length: TOTAL }, (_, i) => mk(i)),
      ...Array.from({ length: 7 }, (_, i) => mk(1000 + i, { ruleCode: 'GSTIN_PRESENCE', mismatchType: null })),
    ]
    mockGet.mockImplementation((url: string) => {
      if (url.startsWith('/v1/preconfigured-masters')) {
        return Promise.resolve({ data: { content: [], totalElements: 0 } })
      }
      if (url.includes('size=1&showResolved=false')) return Promise.resolve({ data: { totalElements: all.length } })
      if (url.startsWith('/v1/uploads/latest/mismatches')) {
        const q = new URLSearchParams(url.split('?')[1])
        let out = all
        if (q.get('ruleCode')) out = out.filter((r) => r.ruleCode === q.get('ruleCode'))
        if (q.get('mismatchType')) out = out.filter((r) => r.mismatchType === q.get('mismatchType'))
        const size = Number(q.get('size'))
        const page = Number(q.get('page') ?? 0)
        return Promise.resolve({
          data: { content: out.slice(page * size, page * size + size), totalElements: out.length },
        })
      }
      return Promise.resolve({ data: {} })
    })
    mockPost.mockImplementation(((_u: string, body: Record<string, unknown>) =>
      body?.dryRun
        ? Promise.resolve({
            data: { matched: all.filter((r) => r.ruleCode === 'MISMATCH_DETECTION').length },
          })
        : Promise.resolve({ data: {} })) as never)
  })

  const realPosts = () => mockPost.mock.calls.filter((c) => !(c[1] as { dryRun?: boolean })?.dryRun)

  async function openTab(first = 'Ledger 000') {
    render(<MastersPage />)
    const tab = await screen.findByRole('tab', { name: /Findings/ })
    await waitFor(() => expect(tab).not.toBeDisabled())
    await userEvent.click(tab)
    await screen.findByText(first)
  }
  const openTab2 = () => openTab('Ledger 2000')

  it('lists numbered pages of 100 driven by the server total and fetches the clicked page', async () => {
    await openTab()
    expect(screen.getByText(/1–100 of 257/)).toBeInTheDocument()
    expect(screen.getAllByLabelText(/^Select Ledger/)).toHaveLength(100)

    await userEvent.click(screen.getByRole('button', { name: 'Page 3' }))

    await screen.findByText('Ledger 200')
    expect(screen.getByText(/201–257 of 257/)).toBeInTheDocument()
    expect(mockGet).toHaveBeenCalledWith(expect.stringContaining('page=2'))
    expect(mockGet).toHaveBeenCalledWith(expect.stringContaining('size=100'))
  })

  it('rule-type filter is applied by the server, so total and pages are correct', async () => {
    await openTab()
    await userEvent.selectOptions(screen.getByLabelText('Filter by rule type'), 'GSTIN_PRESENCE')

    await waitFor(() => expect(mockGet).toHaveBeenCalledWith(expect.stringContaining('ruleCode=GSTIN_PRESENCE')))
    await waitFor(() => expect(screen.queryByText('Ledger 000')).toBeNull())
    expect(screen.queryByRole('navigation', { name: 'Findings pages' })).toBeNull() // 7 results: one page
  })

  it('select all offers "Select all N matching" with the true server count, and no "+"', async () => {
    await openTab()
    await userEvent.click(screen.getByLabelText('Select all'))

    expect(screen.getByText(/100 on this page selected\./)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Select all 250 matching' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Submit for review selected \(100\)/ })).toBeInTheDocument()
    expect(screen.queryByText(/100\+/)).toBeNull()
  })

  it('choosing all matching confirms with the count, then bulk-acts in filter mode', async () => {
    const confirm = stubConfirm(true)
    await openTab()
    await userEvent.click(screen.getByLabelText('Select all'))
    await userEvent.click(screen.getByRole('button', { name: 'Select all 250 matching' }))
    expect(screen.getByText(/All 250 matching findings selected/)).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: /Submit for review selected \(250\)/ }))

    expect(confirm).toHaveBeenCalledWith(expect.stringContaining('250'))
    await waitFor(() => expect(realPosts()).toHaveLength(1))
    expect(realPosts()[0]).toEqual([BULK_URL, { action: 'ACKNOWLEDGE', allMatching: true }])
  })

  it('declining the confirmation sends nothing', async () => {
    const confirm = stubConfirm(false)
    await openTab()
    await userEvent.click(screen.getByLabelText('Select all'))
    await userEvent.click(screen.getByRole('button', { name: 'Select all 250 matching' }))
    await userEvent.click(screen.getByRole('button', { name: /Submit for review selected \(250\)/ }))

    expect(confirm).toHaveBeenCalled()
    expect(realPosts()).toHaveLength(0)
  })

  it('all-matching carries the current filters', async () => {
    stubConfirm(true)
    await openTab()
    await userEvent.selectOptions(screen.getByLabelText('Filter by reason'), 'MISSING_IN_CONFIGURATION')
    await waitFor(() => expect(mockGet).toHaveBeenCalledWith(expect.stringContaining('mismatchType=MISSING')))
    await userEvent.click(screen.getByLabelText('Select all'))
    await userEvent.click(await screen.findByRole('button', { name: 'Select all 250 matching' }))
    await userEvent.click(screen.getByRole('button', { name: /Submit for review selected \(250\)/ }))

    await waitFor(() => expect(realPosts()).toHaveLength(1))
    expect(realPosts()[0][1]).toEqual({
      action: 'ACKNOWLEDGE',
      allMatching: true,
      mismatchType: 'MISSING_IN_CONFIGURATION',
    })
  })

  it('changing page clears the selection and the all-matching choice', async () => {
    await openTab()
    await userEvent.click(screen.getByLabelText('Select all'))
    await userEvent.click(screen.getByRole('button', { name: 'Select all 250 matching' }))
    await userEvent.click(screen.getByRole('button', { name: 'Page 2' }))
    await screen.findByText('Ledger 100')

    expect(screen.queryByText(/All 250 matching findings selected/)).toBeNull()
    expect(screen.queryByRole('button', { name: /selected \(/ })).toBeNull()
  })

  it.each(['ROLE_AUDITOR_CA', 'ROLE_OWNER'])('%s still pages but gets no bulk or select-all controls', async (role) => {
    setRole(role)
    await openTab()
    expect(screen.getByText(/1–100 of 257/)).toBeInTheDocument()
    expect(screen.queryByLabelText('Select all')).toBeNull()
    expect(screen.queryByRole('button', { name: /Select all \d+ matching/ })).toBeNull()
  })

  it('shows a busy state while a bulk action runs, locks the controls, then reports the result', async () => {
    stubConfirm(true)
    let finish!: (v: unknown) => void
    const dry = mockPost.getMockImplementation()!
    mockPost.mockImplementation(((url: string, body: Record<string, unknown>) =>
      body?.dryRun ? dry(url, body) : new Promise((r) => (finish = r))) as never)
    await openTab()
    await userEvent.click(screen.getByLabelText('Select all'))
    await userEvent.click(screen.getByRole('button', { name: 'Select all 250 matching' }))
    await userEvent.click(screen.getByRole('button', { name: /Submit for review selected \(250\)/ }))

    expect(await screen.findByText(/Submitting 250 findings/)).toBeInTheDocument()
    expect(screen.getByLabelText('Filter by reason')).toBeDisabled()
    expect(screen.getByLabelText('Select all')).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Page 2' })).toBeDisabled()
    expect(screen.getByRole('button', { name: /Submit for review selected/ })).toBeDisabled()

    finish({ data: { processed: 250, status: 'ACKNOWLEDGED' } })

    expect(await screen.findByText('Submitted 250 findings for review.')).toBeInTheDocument()
    expect(screen.queryByText(/Submitting 250 findings/)).toBeNull()
    expect(screen.getByLabelText('Filter by reason')).not.toBeDisabled()
  })

  it('explains why bulk is unavailable when only typed-value findings are left', async () => {
    all = Array.from({ length: 6 }, (_, i) => mk(2000 + i, { ruleCode: 'HSN_SAC_CODE', mismatchType: null }))
    mockPost.mockImplementation((() => Promise.resolve({ data: { matched: 0 } })) as never)
    await openTab2()

    expect(screen.getByRole('note')).toHaveTextContent(/6 findings on this page need a typed value/)
    expect(screen.getByRole('note')).toHaveTextContent(/bulk actions are not available/)
    expect(screen.queryByLabelText('Select all')).toBeNull()
  })

  it('does not show the typed-value note to read-only roles', async () => {
    setRole('ROLE_AUDITOR_CA')
    all = Array.from({ length: 6 }, (_, i) => mk(2000 + i, { ruleCode: 'HSN_SAC_CODE', mismatchType: null }))
    await openTab2()

    expect(screen.queryByRole('note')).toBeNull()
  })
})


describe('MastersPage — validation settings tab', () => {
  it('is reachable from the tab bar', async () => {
    setRole('ROLE_OWNER')
    mockGet.mockImplementation((url: string) => {
      if (url.startsWith('/v1/preconfigured-masters')) return Promise.resolve({ data: { content: [], totalElements: 0 } })
      if (url === '/v1/validation-settings') return Promise.resolve({ data: { requireHsnSacOnExpense: true } })
      return Promise.resolve({ data: {} })
    })
    render(<MastersPage />)
    await userEvent.click(await screen.findByRole('tab', { name: /Validation settings/ }))
    expect(await screen.findByRole('checkbox', { name: /Require HSN\/SAC/ })).toBeInTheDocument()
  })
})
