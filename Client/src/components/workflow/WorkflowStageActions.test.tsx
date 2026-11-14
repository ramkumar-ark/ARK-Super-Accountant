import { describe, expect, it, vi, beforeEach } from 'vitest'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { WorkflowStageActions } from './WorkflowStageActions'
import type { WorkflowDetail, WorkflowStage, WorkflowType } from '@/lib/workflowApi'

vi.mock('@/lib/api', () => ({
  api: {
    get: vi.fn(),
    post: vi.fn(),
  },
}))

import { api } from '@/lib/api'

type DetailOverrides = {
  stage?: WorkflowStage
  type?: WorkflowType
  blockingFindings?: number
  baselineConfirmed?: boolean
  filingRef?: string | null
  filingRefDate?: string | null
}

function makeDetail(overrides: DetailOverrides = {}): WorkflowDetail {
  const stage = overrides.stage ?? 'FINDINGS_REVIEW'
  const type = overrides.type ?? 'GSTR1'
  return {
    id: 'wf-1',
    workflow: {
      id: 'wf-1',
      type,
      periodStart: '2025-04-01',
      periodEnd: '2025-04-30',
      periodKey: '2025-04',
      financialYear: 2025,
      revision: 0,
      stage,
      active: true,
      uploadJobId: null,
      mastersJobId: null,
      baselineConfirmedAt: null,
      finalizedAt: null,
      finalizedBy: null,
      filedAt: null,
      filingRef: overrides.filingRef ?? null,
      filingRefDate: overrides.filingRefDate ?? null,
      createdBy: 'operator',
      createdAt: '2025-04-01T00:00:00Z',
    },
    dependencies: [],
    blockingFindings: overrides.blockingFindings ?? 0,
    baselineConfirmed: overrides.baselineConfirmed ?? true,
    analysisAvailable: false,
    requiredCoverageFrom: '2025-04-01',
    requiredCoverageTo: '2025-04-30',
  }
}

function renderActions(detail: WorkflowDetail, role = 'ROLE_ACCOUNTANT') {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  const invalidateSpy = vi.spyOn(client, 'invalidateQueries')
  const view = render(
    <QueryClientProvider client={client}>
      <WorkflowStageActions detail={detail} role={role} />
    </QueryClientProvider>,
  )
  return { ...view, client, invalidateSpy }
}

beforeEach(() => {
  vi.clearAllMocks()
})

describe('WorkflowStageActions', () => {
  it('advance_onlyAccountantInFindingsReview', () => {
    const { unmount } = renderActions(makeDetail({ stage: 'FINDINGS_REVIEW' }), 'ROLE_ACCOUNTANT')
    expect(screen.getByRole('button', { name: 'Move to Prepare' })).toBeInTheDocument()
    unmount()

    renderActions(makeDetail({ stage: 'FINDINGS_REVIEW' }), 'ROLE_OPERATOR')
    expect(screen.queryByRole('button', { name: 'Move to Prepare' })).not.toBeInTheDocument()
  })

  it('advance_disabledWithReasonWhenBlocking', () => {
    renderActions(makeDetail({ stage: 'FINDINGS_REVIEW', blockingFindings: 2 }), 'ROLE_ACCOUNTANT')
    expect(screen.getByRole('button', { name: 'Move to Prepare' })).toBeDisabled()
    expect(screen.getByText('Resolve 2 blocking findings first')).toBeInTheDocument()
  })

  it('advance_disabledWithReasonWhenBaselineUnconfirmed', () => {
    renderActions(
      makeDetail({ stage: 'FINDINGS_REVIEW', baselineConfirmed: false, blockingFindings: 0 }),
      'ROLE_ACCOUNTANT',
    )
    expect(screen.getByRole('button', { name: 'Move to Prepare' })).toBeDisabled()
    expect(screen.getByText('Baseline must be confirmed before Prepare')).toBeInTheDocument()
  })

  it('advance_conflict_showsServerMessage', async () => {
    vi.mocked(api.post).mockRejectedValue({
      response: { data: { error: 'Masters job is not the latest completed masters job' } },
    })
    const user = userEvent.setup()
    renderActions(makeDetail({ stage: 'FINDINGS_REVIEW' }), 'ROLE_ACCOUNTANT')

    await user.click(screen.getByRole('button', { name: 'Move to Prepare' }))

    expect(
      await screen.findByText('Masters job is not the latest completed masters job'),
    ).toBeInTheDocument()
  })

  it('finalize_confirmsThenPosts', async () => {
    vi.mocked(api.post).mockResolvedValue({ data: makeDetail({ stage: 'FINALIZED' }).workflow })
    const user = userEvent.setup()
    renderActions(makeDetail({ stage: 'PREPARE' }), 'ROLE_ACCOUNTANT')

    await user.click(screen.getByRole('button', { name: 'Finalize' }))
    expect(screen.getByText("Finalizing freezes this period's day book.")).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Confirm finalize' }))

    await waitFor(() => {
      expect(api.post).toHaveBeenCalledWith('/v1/workflows/wf-1/finalize')
    })
  })

  it('finalize_success_refetchesDetailAndEvents', async () => {
    vi.mocked(api.post).mockResolvedValue({ data: makeDetail({ stage: 'FINALIZED' }).workflow })
    const user = userEvent.setup()
    const { invalidateSpy } = renderActions(makeDetail({ stage: 'PREPARE' }), 'ROLE_ACCOUNTANT')

    await user.click(screen.getByRole('button', { name: 'Finalize' }))
    await user.click(screen.getByRole('button', { name: 'Confirm finalize' }))

    await waitFor(() => {
      expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['workflow', 'wf-1'] })
      expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['workflow-events', 'wf-1'] })
      expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['workflow-findings', 'wf-1'] })
    })
  })

  it('unfinalize_requiresReason', async () => {
    vi.mocked(api.post).mockResolvedValue({ data: makeDetail({ stage: 'PREPARE' }).workflow })
    const user = userEvent.setup()
    renderActions(makeDetail({ stage: 'FINALIZED' }), 'ROLE_ACCOUNTANT')

    await user.click(screen.getByRole('button', { name: 'Un-finalize' }))
    const confirm = screen.getByRole('button', { name: 'Confirm un-finalize' })
    expect(confirm).toBeDisabled()

    const reason = screen.getByLabelText('Reason')
    await user.type(reason, '   ')
    expect(confirm).toBeDisabled()

    await user.clear(reason)
    await user.type(reason, 'Missed invoice 42')
    expect(confirm).toBeEnabled()

    await user.click(confirm)

    await waitFor(() => {
      expect(api.post).toHaveBeenCalledWith('/v1/workflows/wf-1/unfinalize', {
        reason: 'Missed invoice 42',
      })
    })
  })

  it('filing_labelPerType', async () => {
    const user = userEvent.setup()
    const { unmount } = renderActions(
      makeDetail({ stage: 'FINALIZED', type: 'TDS_26Q' }),
      'ROLE_ACCOUNTANT',
    )
    await user.click(screen.getByRole('button', { name: 'Record filing' }))
    expect(screen.getByText('Token / acknowledgement no.')).toBeInTheDocument()
    unmount()

    renderActions(makeDetail({ stage: 'FINALIZED', type: 'GSTR1' }), 'ROLE_ACCOUNTANT')
    await user.click(screen.getByRole('button', { name: 'Record filing' }))
    expect(screen.getByText('ARN')).toBeInTheDocument()
  })

  it('filing_postsRefAndDate', async () => {
    vi.mocked(api.post).mockResolvedValue({ data: makeDetail({ stage: 'FILED' }).workflow })
    const user = userEvent.setup()
    renderActions(makeDetail({ stage: 'FINALIZED', type: 'GSTR1' }), 'ROLE_ACCOUNTANT')

    await user.click(screen.getByRole('button', { name: 'Record filing' }))
    await user.type(screen.getByLabelText('ARN'), 'AA0704250012345')
    await user.type(screen.getByLabelText('Filing date'), '2025-05-11')
    await user.click(screen.getByRole('button', { name: 'Confirm filing' }))

    await waitFor(() => {
      expect(api.post).toHaveBeenCalledWith('/v1/workflows/wf-1/filing', {
        ref: 'AA0704250012345',
        date: '2025-05-11',
      })
    })
  })

  it('filed_showsReferenceReadOnly', () => {
    renderActions(
      makeDetail({ stage: 'FILED', filingRef: 'AA07', filingRefDate: '2025-05-11' }),
      'ROLE_ACCOUNTANT',
    )
    const section = screen.getByTestId('workflow-filed-reference')
    expect(within(section).getByText('Filed: AA07 on 2025-05-11')).toBeInTheDocument()
    expect(screen.queryAllByRole('button')).toHaveLength(0)
  })

  it('readOnlyRoles_seeNoActions', () => {
    const stages: WorkflowStage[] = ['FINDINGS_REVIEW', 'PREPARE', 'FINALIZED']
    for (const role of ['ROLE_AUDITOR_CA', 'ROLE_OWNER']) {
      for (const stage of stages) {
        const { unmount } = renderActions(makeDetail({ stage }), role)
        expect(screen.queryAllByRole('button')).toHaveLength(0)
        unmount()
      }
    }
  })
})
