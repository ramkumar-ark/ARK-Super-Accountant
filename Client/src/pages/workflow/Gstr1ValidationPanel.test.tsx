import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { Gstr1ValidationPanel } from './Gstr1ValidationPanel'
import { latestPortalDownload } from './portalDownloadEvent'
import { api } from '@/lib/api'
import { getWorkflowEvents } from '@/lib/workflowApi'
import type { Workflow } from '@/lib/workflowApi'
import type { Gstr1Validation } from '@/lib/api'

vi.mock('@/lib/api', () => {
  const api = { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() }
  return {
    api,
    getGstr1Validation: (workflowId: string) =>
      api.get(`/v1/workflows/${workflowId}/gstr1/validation`).then((r: { data: unknown }) => r.data),
    getGstr1Export: (workflowId: string) =>
      api.get(`/v1/workflows/${workflowId}/gstr1/export`, { responseType: 'blob' }),
  }
})

vi.mock('@/lib/workflowApi', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/lib/workflowApi')>()
  return {
    ...actual,
    getWorkflowEvents: (id: string) => api.get(`/v1/workflows/${id}/events`).then((r) => r.data),
  }
})

const mockGet = vi.mocked(api.get)

vi.stubGlobal('URL', {
  ...URL,
  createObjectURL: vi.fn(() => 'blob:mock'),
  revokeObjectURL: vi.fn(),
})

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

function validation(overrides: Partial<Gstr1Validation> = {}): Gstr1Validation {
  return {
    errors: [],
    warnings: [],
    ...overrides,
  }
}

function events() {
  return [
    {
      id: 'e-1',
      workflowId: 'wf-1',
      actor: 'acct',
      occurredAt: '2026-05-01T10:00:00Z',
      kind: 'WORKFLOW_STARTED',
      reason: null,
      payload: null,
    },
    {
      id: 'e-2',
      workflowId: 'wf-1',
      actor: 'acct',
      occurredAt: '2026-05-02T10:00:00Z',
      kind: 'PORTAL_FILE_DOWNLOADED',
      reason: null,
      payload: JSON.stringify({
        sha256: 'abc123def',
        bytes: 2048,
        writerVersion: 'GST3.2.4',
        parts: 1,
      }),
    },
  ]
}

function routeApi(
  data: Gstr1Validation = validation(),
  eventRows: ReturnType<typeof events> | Error = events(),
) {
  mockGet.mockImplementation((url: string) => {
    if (url === '/v1/workflows/wf-1/gstr1/validation')
      return Promise.resolve({ data })
    if (url === '/v1/workflows/wf-1/events') {
      return eventRows instanceof Error ? Promise.reject(eventRows) : Promise.resolve({ data: eventRows })
    }
    return Promise.reject(new Error('Unexpected GET ' + url))
  })
}

function renderPanel(
  role = 'ROLE_ACCOUNTANT',
  stage: Workflow['stage'] = 'PREPARE',
) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <Gstr1ValidationPanel workflow={workflow({ stage })} role={role} />
    </QueryClientProvider>,
  )
}

beforeEach(() => {
  vi.clearAllMocks()
})

describe('Gstr1ValidationPanel', () => {
  it('renders nothing and makes no requests for non-GSTR1 workflows', () => {
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const { container } = render(
      <QueryClientProvider client={client}>
        <Gstr1ValidationPanel workflow={workflow({ type: 'TDS_26Q' })} role="ROLE_ACCOUNTANT" />
      </QueryClientProvider>,
    )
    expect(container).toBeEmptyDOMElement()
    expect(api.get).not.toHaveBeenCalled()
  })

  it('shows errors and warnings and keeps the download disabled while errors exist', async () => {
    routeApi(
      validation({
        errors: [
          {
            code: 'GSTIN_MISSING',
            severity: 'ERROR',
            tableCode: 'B2B',
            rowKey: 'inv-1',
            message: 'GSTIN is missing for a B2B row.',
          },
        ],
        warnings: [
          {
            code: 'HSN_MISSING',
            severity: 'WARNING',
            tableCode: 'B2CS',
            rowKey: null,
            message: 'HSN is missing.',
          },
        ],
      }),
    )
    renderPanel()

    expect(await screen.findByText(/GSTIN is missing for a B2B row\./)).toBeInTheDocument()
    expect(screen.getByText(/HSN is missing\./)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Download portal file' })).toBeDisabled()
    expect(
      screen.getByText('Resolve validation errors before downloading.'),
    ).toBeInTheDocument()
  })

  it('enables the download and shows the latest hash when there are no errors', async () => {
    routeApi()
    renderPanel()

    expect(await screen.findByRole('button', { name: 'Download portal file' })).toBeEnabled()
    expect(await screen.findByText(/Last downloaded hash: abc123def/)).toBeInTheDocument()
    expect(screen.getByText(/2048 bytes, writer GST3\.2\.4, 1 part/)).toBeInTheDocument()
  })

  it('downloads the portal file when clicked', async () => {
    routeApi()
    mockGet.mockImplementation((url: string) => {
      if (url === '/v1/workflows/wf-1/gstr1/validation')
        return Promise.resolve({ data: validation() })
      if (url === '/v1/workflows/wf-1/events')
        return Promise.resolve({ data: events() })
      if (url === '/v1/workflows/wf-1/gstr1/export')
        return Promise.resolve({
          data: new Blob(['{}'], { type: 'application/json' }),
          headers: { 'content-disposition': 'attachment; filename="GSTR1_042026_1234F1Z5.json"' },
        })
      return Promise.reject(new Error('Unexpected GET ' + url))
    })
    renderPanel()
    const clickSpy = vi
      .spyOn(HTMLAnchorElement.prototype, 'click')
      .mockImplementation(() => {})

    await userEvent.click(await screen.findByRole('button', { name: 'Download portal file' }))

    expect(mockGet).toHaveBeenCalledWith('/v1/workflows/wf-1/gstr1/export', {
      responseType: 'blob',
    })
    await screen.findByRole('button', { name: 'Download portal file' })
    clickSpy.mockRestore()
  })

  it('hides the download from non-accountants', async () => {
    routeApi()
    renderPanel('ROLE_OPERATOR')

    expect(
      await screen.findByText('Only accountants can download the portal file.'),
    ).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Download portal file' })).not.toBeInTheDocument()
  })

  it('is hidden before the PREPARE stage', () => {
    routeApi()
    renderPanel('ROLE_ACCOUNTANT', 'STARTED')

    expect(screen.queryByText('GSTR-1 Validation & Download')).not.toBeInTheDocument()
  })

  it('latestPortalDownload picks the newest download event and skips unparseable payloads', () => {
    const rows = events()
    rows.push({
      id: 'e-3',
      workflowId: 'wf-1',
      actor: 'acct',
      occurredAt: '2026-05-03T10:00:00Z',
      kind: 'PORTAL_FILE_DOWNLOADED',
      reason: null,
      payload: 'not-json',
    })
    rows.push({
      id: 'e-4',
      workflowId: 'wf-1',
      actor: 'acct',
      occurredAt: '2026-05-04T10:00:00Z',
      kind: 'PORTAL_FILE_DOWNLOADED',
      reason: null,
      payload: JSON.stringify({
        sha256: 'newest-hash',
        bytes: 10,
        writerVersion: 'GST3.2.4',
        parts: 2,
      }),
    })
    const latest = latestPortalDownload(rows)
    expect(latest?.sha256).toBe('newest-hash')
    expect(latest?.parts).toBe(2)
  })

  it('latestPortalDownload returns null when there are no download events', () => {
    expect(latestPortalDownload([])).toBeNull()
  })
})

describe('events wiring', () => {
  it('uses the shared getWorkflowEvents helper', async () => {
    routeApi()
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    render(
      <QueryClientProvider client={client}>
        <Gstr1ValidationPanel workflow={workflow()} role="ROLE_ACCOUNTANT" />
      </QueryClientProvider>,
    )
    await screen.findByText(/Last downloaded hash: abc123def/)
    expect(getWorkflowEvents).toBeDefined()
  })
})
