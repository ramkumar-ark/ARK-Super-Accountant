import { describe, it, expect, vi, beforeEach } from 'vitest'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { StartWorkflowDialog } from './StartWorkflowDialog'
import { api } from '@/lib/api'
import type { Workflow } from '@/lib/workflowApi'

vi.mock('@/lib/api', () => ({
  api: { get: vi.fn(), post: vi.fn() },
}))

function started(overrides: Partial<Workflow> = {}): Workflow {
  return {
    id: 'wf-1',
    type: 'GSTR1',
    periodStart: '2025-04-01',
    periodEnd: '2025-04-30',
    periodKey: '2025-04',
    financialYear: 2025,
    revision: 0,
    stage: 'STARTED',
    active: true,
    uploadJobId: null,
    mastersJobId: null,
    baselineConfirmedAt: null,
    finalizedAt: null,
    finalizedBy: null,
    filedAt: null,
    filingRef: null,
    filingRefDate: null,
    createdBy: 'owner',
    createdAt: '2025-05-01T10:00:00Z',
    ...overrides,
  }
}

describe('StartWorkflowDialog', () => {
  const onClose = vi.fn()
  const onStarted = vi.fn()

  beforeEach(() => {
    vi.clearAllMocks()
  })

  function renderDialog() {
    return render(<StartWorkflowDialog open onClose={onClose} onStarted={onStarted} />)
  }

  it('start_tdsPayment_postsMonthlyKey', async () => {
    const user = userEvent.setup()
    const response = started({ type: 'TDS_PAYMENT' })
    vi.mocked(api.post).mockResolvedValue({ data: response })

    renderDialog()

    await user.selectOptions(screen.getByLabelText('Type'), 'TDS_PAYMENT')
    fireEvent.change(screen.getByLabelText('Period'), { target: { value: '2025-04' } })
    await user.click(screen.getByRole('button', { name: 'Start' }))

    await waitFor(() =>
      expect(api.post).toHaveBeenCalledWith('/v1/workflows', {
        type: 'TDS_PAYMENT',
        periodKey: '2025-04',
      }),
    )
    expect(onStarted).toHaveBeenCalledWith(response)
  })

  it('start_tds26q_postsQuarterKey', async () => {
    const user = userEvent.setup()
    vi.mocked(api.post).mockResolvedValue({ data: started({ type: 'TDS_26Q' }) })

    renderDialog()

    await user.selectOptions(screen.getByLabelText('Type'), 'TDS_26Q')
    expect(screen.queryByLabelText('Period grain')).not.toBeInTheDocument()
    expect(screen.queryByLabelText('Period')).not.toBeInTheDocument()

    fireEvent.change(screen.getByLabelText('FY start year'), { target: { value: '2025' } })
    await user.selectOptions(screen.getByLabelText('Quarter'), 'Q2')
    await user.click(screen.getByRole('button', { name: 'Start' }))

    await waitFor(() =>
      expect(api.post).toHaveBeenCalledWith('/v1/workflows', {
        type: 'TDS_26Q',
        periodKey: '2025-Q2',
      }),
    )
  })

  it('start_gstQuarterly_allowsGrainChoice', async () => {
    const user = userEvent.setup()
    vi.mocked(api.post).mockResolvedValue({ data: started({ type: 'GSTR3B' }) })

    renderDialog()

    await user.selectOptions(screen.getByLabelText('Type'), 'GSTR3B')
    await user.selectOptions(screen.getByLabelText('Period grain'), 'QUARTERLY')
    fireEvent.change(screen.getByLabelText('FY start year'), { target: { value: '2025' } })
    await user.selectOptions(screen.getByLabelText('Quarter'), 'Q1')
    await user.click(screen.getByRole('button', { name: 'Start' }))

    await waitFor(() =>
      expect(api.post).toHaveBeenCalledWith('/v1/workflows', {
        type: 'GSTR3B',
        periodKey: '2025-Q1',
      }),
    )
  })

  it('start_conflict_showsServerMessage', async () => {
    const user = userEvent.setup()
    vi.mocked(api.post).mockRejectedValue({
      response: { data: { error: 'A workflow for this period is already active' } },
    })

    renderDialog()

    await user.selectOptions(screen.getByLabelText('Type'), 'TDS_PAYMENT')
    fireEvent.change(screen.getByLabelText('Period'), { target: { value: '2025-04' } })
    await user.click(screen.getByRole('button', { name: 'Start' }))

    expect(
      await screen.findByText('A workflow for this period is already active'),
    ).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Start' })).toBeInTheDocument()
    expect(onStarted).not.toHaveBeenCalled()
  })
})
