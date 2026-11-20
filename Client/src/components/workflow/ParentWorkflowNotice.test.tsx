import { describe, it, expect, vi } from 'vitest'
import { render, screen, within } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { ParentWorkflowNotice } from './ParentWorkflowNotice'
import { api } from '@/lib/api'
import type { Workflow } from '@/lib/workflowApi'
import type { WorkflowDependency } from '@/lib/workflowApi'

vi.mock('@/lib/api', () => ({
  api: { get: vi.fn(), post: vi.fn() },
}))

vi.mock('@tanstack/react-router', () => ({
  Link: ({ children, params }: { children: React.ReactNode; params: { workflowId: string } }) => (
    <a href={'/workflows/' + params.workflowId}>{children}</a>
  ),
}))

const mockGet = vi.mocked(api.get)

function workflow(overrides: Partial<Workflow> = {}): Workflow {
  return {
    id: 'wf-1',
    type: 'GSTR3B',
    periodStart: '2025-04-01',
    periodEnd: '2025-04-30',
    periodKey: '2025-04',
    financialYear: 2025,
    revision: 0,
    stage: 'FINDINGS_REVIEW',
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

function renderNotice(w: Workflow, dependencies: WorkflowDependency[]) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={client}>
      <ParentWorkflowNotice workflow={w} dependencies={dependencies} />
    </QueryClientProvider>,
  )
}

describe('ParentWorkflowNotice', () => {
  it('points a GSTR-3B workflow at its GSTR-1 of the same period', async () => {
    mockGet.mockResolvedValue({
      data: [
        workflow({
          id: 'p1',
          type: 'GSTR1',
          periodKey: '2025-04',
          stage: 'FINALIZED',
        }),
      ],
    })
    renderNotice(workflow({ id: 'wf-3b', type: 'GSTR3B', periodKey: '2025-04' }), [
      { type: 'GSTR1', periodKey: '2025-04', satisfied: true },
      { type: 'GSTR3B', periodKey: '2025-03', satisfied: true },
    ])

    const line = (await screen.findByText(/Sales-side findings were reviewed on/)).closest('li')!
    const link = await within(line).findByRole('link', { name: 'GSTR-1 2025-04' })
    expect(link).toHaveAttribute('href', '/workflows/p1')
    expect(line).toHaveTextContent('(Finalized)')
    expect(screen.queryByText(/GSTR-3B 2025-03/)).not.toBeInTheDocument()
  })

  it('lists the three payment months for a 26Q workflow and ignores superseded revisions', async () => {
    mockGet.mockResolvedValue({
      data: [
        workflow({ id: 'p-apr', type: 'TDS_PAYMENT', periodKey: '2025-04' }),
        workflow({ id: 'p-may-old', type: 'TDS_PAYMENT', periodKey: '2025-05', active: false }),
        workflow({ id: 'p-may', type: 'TDS_PAYMENT', periodKey: '2025-05', revision: 1 }),
        workflow({ id: 'p-jun', type: 'TDS_PAYMENT', periodKey: '2025-06' }),
      ],
    })
    renderNotice(workflow({ id: 'wf-26q', type: 'TDS_26Q', periodKey: '2025-Q1' }), [
      { type: 'TDS_PAYMENT', periodKey: '2025-04', satisfied: true },
      { type: 'TDS_PAYMENT', periodKey: '2025-05', satisfied: true },
      { type: 'TDS_PAYMENT', periodKey: '2025-06', satisfied: true },
    ])

    const lines = await screen.findAllByText(/Monthly findings were reviewed on/)
    expect(lines).toHaveLength(3)
    expect(
      await screen.findByRole('link', { name: 'TDS payment 2025-05' }),
    ).toHaveAttribute('href', '/workflows/p-may')
  })
})
