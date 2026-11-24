import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { WorkflowUploadStep } from './WorkflowUploadStep'

vi.mock('@/lib/api', () => ({
  api: {
    get: vi.fn(),
    post: vi.fn(),
  },
}))

import { api } from '@/lib/api'

const mockGet = vi.mocked(api.get)

function mockMapping(view: { complete: boolean; blockers: string[] }) {
  mockGet.mockImplementation((url: string) => {
    if (url === '/v1/tds/ledger-mapping') {
      return Promise.resolve({
        data: { ...view, mastersJobId: null, receivableWarnings: [], ledgers: [] },
      })
    }
    return Promise.resolve({ data: {} })
  })
}

function renderStep() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={queryClient}>
      <WorkflowUploadStep
        workflowId="wf-1"
        requiredFrom="2025-04-01"
        requiredTo="2025-04-30"
        uploads={[]}
        beforePrepare
      />
    </QueryClientProvider>,
  )
}

describe('WorkflowUploadStep TDS mapping gate', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('disablesDayBookUploadUntilTdsMappingSaved', async () => {
    mockMapping({ complete: false, blockers: ['2 TDS ledgers not mapped: Rent, TDS Payable 194C'] })
    renderStep()

    expect(await screen.findByText(/disabled until the TDS ledger mapping is saved/)).toBeInTheDocument()
    expect(screen.getByText(/2 TDS ledgers not mapped/)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Open the TDS ledger mapping section' })).toHaveAttribute(
      'href',
      '/settings/compliance',
    )
    expect(screen.getByTestId('workflow-file-input')).toBeDisabled()
    expect(screen.getByRole('button', { name: /Upload and bind/ })).toBeDisabled()
  })

  it('leavesUploadEnabledWhenMappingComplete', async () => {
    mockMapping({ complete: true, blockers: [] })
    renderStep()

    await vi.waitFor(() => expect(mockGet).toHaveBeenCalledWith('/v1/tds/ledger-mapping'))
    expect(screen.queryByText(/disabled until the TDS ledger mapping is saved/)).not.toBeInTheDocument()
    expect(screen.getByTestId('workflow-file-input')).toBeEnabled()
  })
})
