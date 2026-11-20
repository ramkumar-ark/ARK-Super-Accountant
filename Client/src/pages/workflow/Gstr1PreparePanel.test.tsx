import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { Gstr1PreparePanel } from './Gstr1PreparePanel'
import { api } from '@/lib/api'
import type { Workflow } from '@/lib/workflowApi'
import type { Gstr1Tables } from '@/lib/api'

vi.mock('@/lib/api', () => {
  const api = { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() }
  return {
    api,
    getGstr1Tables: (workflowId: string) =>
      api.get(`/v1/workflows/${workflowId}/gstr1/tables`).then((r: { data: unknown }) => r.data),
    putGstr1FillIn: (workflowId: string, fillIn: unknown) =>
      api.put(`/v1/workflows/${workflowId}/gstr1/fill-ins`, fillIn),
  }
})

const mockGet = vi.mocked(api.get)
const mockPut = vi.mocked(api.put)

function workflow(overrides: Partial<Workflow> = {}): Workflow {
  return {
    id: 'wf-1',
    type: 'GSTR1',
    periodStart: '2026-04-01',
    periodEnd: '2026-04-30',
    periodKey: '2026-04',
    financialYear: 2026,
    revision: 0,
    stage: 'PREPARE',
    active: true,
    uploadJobId: 'uj-1',
    mastersJobId: null,
    baselineConfirmedAt: null,
    finalizedAt: null,
    finalizedBy: null,
    filedAt: null,
    filingRef: null,
    filingRefDate: null,
    filingChannel: null,
    createdBy: 'op1',
    createdAt: '2026-04-01T00:00:00Z',
    ...overrides,
  }
}

function tables(): Gstr1Tables {
  return {
    workflowId: 'wf-1',
    stage: 'PREPARE',
    builtAt: '2026-04-30T00:00:00Z',
    frozenAt: null,
    tables: [
      {
        tableCode: 'B2B',
        rowCount: 2,
        totals: { taxableValue: 100000, igst: 0, cgst: 9000, sgst: 9000, cess: 0 },
      },
      {
        tableCode: 'B2CS',
        rowCount: 1,
        totals: { taxableValue: 5000, igst: 0, cgst: 225, sgst: 225, cess: 0 },
      },
    ],
    fillIns: [
      {
        scope: 'LEDGER',
        key: 'Input GST - CGST',
        attribute: 'HSN',
        value: '996334',
        enteredBy: 'acct',
        enteredAt: '2026-05-01T10:00:00Z',
        fixedInPrepare: true,
      },
    ],
  }
}

function routeApi(data = tables()) {
  mockGet.mockImplementation((url: string) => {
    if (url === '/v1/workflows/wf-1/gstr1/tables') return Promise.resolve({ data })
    return Promise.reject(new Error('Unexpected GET ' + url))
  })
}

function renderPanel(role = 'ROLE_ACCOUNTANT', stage: Workflow['stage'] = 'PREPARE') {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <Gstr1PreparePanel workflow={workflow({ stage })} role={role} />
    </QueryClientProvider>,
  )
}

beforeEach(() => {
  vi.clearAllMocks()
  mockPut.mockResolvedValue({ data: {} })
})

describe('Gstr1PreparePanel', () => {
  it('renders the table tabs with row counts and totals and the fixed-in-Prepare list', async () => {
    routeApi()
    renderPanel()

    expect(await screen.findByRole('tab', { name: /B2B · 2 rows/ })).toBeInTheDocument()
    expect(screen.getByRole('tab', { name: /B2CS · 1 row/ })).toBeInTheDocument()
    expect(screen.getByText('Fixed in Prepare')).toBeInTheDocument()
    expect(screen.getByText(/Input GST - CGST/)).toBeInTheDocument()
    expect(screen.getByText(/HSN = 996334/)).toBeInTheDocument()
    expect(screen.getByText(/Gross taxable value/)).toBeInTheDocument()
  })

  it('lets an accountant save a fill-in, which calls the API and refreshes the tables', async () => {
    routeApi()
    renderPanel()

    await screen.findByRole('tab', { name: /B2B · 2 rows/ })
    await userEvent.selectOptions(screen.getByLabelText('Attribute'), 'HSN')
    await userEvent.type(screen.getByLabelText('Key'), 'Input GST - CGST')
    await userEvent.type(screen.getByLabelText('Value'), '996334')
    await userEvent.click(screen.getByRole('button', { name: 'Save fill-in' }))

    expect(mockPut).toHaveBeenCalledWith('/v1/workflows/wf-1/gstr1/fill-ins', {
      scope: 'LEDGER',
      key: 'Input GST - CGST',
      attribute: 'HSN',
      value: '996334',
    })
    await waitFor(() =>
      expect(
        mockGet.mock.calls.filter(([url]) => url === '/v1/workflows/wf-1/gstr1/tables').length,
      ).toBeGreaterThan(1),
    )
  })

  it('hides the fill-in editor from an operator', async () => {
    routeApi()
    renderPanel('ROLE_OPERATOR')

    await screen.findByRole('tab', { name: /B2B · 2 rows/ })
    expect(screen.queryByRole('button', { name: 'Save fill-in' })).not.toBeInTheDocument()
  })

  it('shows the server conflict error when a fill-in is refused', async () => {
    routeApi()
    mockPut.mockRejectedValue({
      response: { data: { error: 'Fill-ins can only be edited while the workflow is in PREPARE stage' } },
    })
    renderPanel()

    await screen.findByRole('tab', { name: /B2B · 2 rows/ })
    await userEvent.selectOptions(screen.getByLabelText('Attribute'), 'HSN')
    await userEvent.type(screen.getByLabelText('Key'), 'Input GST - CGST')
    await userEvent.type(screen.getByLabelText('Value'), '996334')
    await userEvent.click(screen.getByRole('button', { name: 'Save fill-in' }))

    expect(
      await screen.findByText('Fill-ins can only be edited while the workflow is in PREPARE stage'),
    ).toBeInTheDocument()
  })
})
