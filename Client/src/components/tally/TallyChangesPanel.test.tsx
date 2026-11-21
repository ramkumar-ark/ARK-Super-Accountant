import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { TallyChangesPanel } from './TallyChangesPanel'
import { api } from '@/lib/api'
import { useAuthStore } from '@/store/authStore'
import type { TallyChangeItem, TallyChangesResponse } from '@/lib/tallyChangesApi'

vi.mock('@/lib/api', () => ({
  api: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
}))

const mockGet = vi.mocked(api.get)
const mockPost = vi.mocked(api.post)

function setRole(role: string) {
  useAuthStore.setState({
    user: { id: 1, username: 'u', email: 'u@t.com', role },
    isAuthenticated: true,
  })
}

function item(overrides: Partial<TallyChangeItem> = {}): TallyChangeItem {
  return {
    id: 'item-1',
    ledgerKey: 'anandha timber depot',
    ledgerName: 'ANANDHA TIMBER DEPOT',
    changeType: 'ALTER_FIELD',
    field: 'PAN',
    baselineValue: 'AAPPK2262D',
    newValue: 'ABCDE1234F',
    status: 'PENDING',
    sourceType: 'PAN_OVERRIDE',
    approvedBy: 'acct',
    approvedAt: '2026-11-22T09:00:00Z',
    observedValue: null,
    failureReason: null,
    lastCheckOutcome: 'FOUND',
    lastCheckedAt: '2026-11-22T09:30:00Z',
    createdAt: '2026-11-22T09:00:00Z',
    resolvedAt: null,
    checkOutcome: 'FOUND',
    checkReason: null,
    currentTallyName: 'ANANDHA TIMBER DEPOT',
    currentTallyPan: 'AAPPK2262D',
    ...overrides,
  }
}

function mockChanges(response: Partial<TallyChangesResponse> = {}) {
  const data: TallyChangesResponse = {
    mastersUpload: {
      jobId: 'job-1',
      uploadedAt: '2026-11-22T10:00:00Z',
      stale: false,
    },
    items: [item()],
    ...response,
  }
  mockGet.mockResolvedValue({ data })
}

function renderPanel() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <TallyChangesPanel />
    </QueryClientProvider>,
  )
}

beforeEach(() => {
  vi.clearAllMocks()
  setRole('ROLE_ACCOUNTANT')
  mockChanges()
  mockPost.mockResolvedValue({
    data: new Blob(['{}']),
    headers: {
      'content-disposition': 'attachment; filename="tally-pan-changes-2026-11-22.json"',
    },
  })
})

describe('TallyChangesPanel', () => {
  it('shows the Tally import instructions', async () => {
    renderPanel()
    expect(await screen.findByText(/Back up the company/)).toBeInTheDocument()
    expect(screen.getByText(/Alt\+O/)).toBeInTheDocument()
    expect(screen.getByText(/Import Masters/)).toBeInTheDocument()
    expect(screen.getByText(/into the loaded company/)).toBeInTheDocument()
    expect(screen.getByText(/Upload masters to confirm/)).toBeInTheDocument()
  })

  it('filters rows into status tabs and shows ledger, current PAN, new PAN and status chip', async () => {
    mockChanges({
      items: [
        item({
          id: 'p1',
          ledgerName: 'Pending Ledger',
          currentTallyName: 'Pending Ledger',
          status: 'PENDING',
          currentTallyPan: 'AAPPK2262D',
        }),
        item({
          id: 'e1',
          ledgerName: 'Exported Ledger',
          currentTallyName: 'Exported Ledger',
          status: 'EXPORTED',
          currentTallyPan: 'BBBPK2262D',
          newValue: 'CCCPK2262D',
        }),
      ],
    })
    renderPanel()

    expect(await screen.findByText('Pending Ledger')).toBeInTheDocument()
    expect(screen.getByText('AAPPK2262D')).toBeInTheDocument()
    expect(screen.getByText('ABCDE1234F')).toBeInTheDocument()
    expect(within(screen.getByText('Pending Ledger').closest('tr')!).getByText('Pending')).toBeInTheDocument()
    expect(screen.queryByText('Exported Ledger')).not.toBeInTheDocument()

    await userEvent.click(screen.getByRole('tab', { name: /^Exported/ }))
    expect(screen.getByText('Exported Ledger')).toBeInTheDocument()
    expect(screen.queryByText('Pending Ledger')).not.toBeInTheDocument()
  })

  it('labels an empty new value as Clears PAN and an empty Tally PAN as No PAN', async () => {
    mockChanges({ items: [item({ newValue: '', currentTallyPan: '' })] })
    renderPanel()

    expect(await screen.findByText('Clears PAN')).toBeInTheDocument()
    expect(screen.getByText('No PAN')).toBeInTheDocument()
  })

  it('shows the blocked reason for a NOT_FOUND ledger', async () => {
    mockChanges({
      items: [
        item({ id: 'n1', ledgerName: 'Missing Ledger', checkOutcome: 'NOT_FOUND' }),
        item({
          id: 'n2',
          ledgerName: 'No Upload Ledger',
          checkOutcome: 'NOT_FOUND',
          checkReason: 'NO_MASTERS_UPLOAD',
        }),
      ],
    })
    renderPanel()

    expect(
      (await screen.findAllByText(/Ledger not found in the latest masters upload/)).length,
    ).toBe(2)
    expect(screen.getByText(/upload a masters file to check whether this ledger still exists/)).toBeInTheDocument()
  })

  it('disables download when no eligible item exists and enables it for a FOUND pending item', async () => {
    mockChanges({ items: [item({ checkOutcome: 'NOT_FOUND' })] })
    const first = renderPanel()
    expect(await screen.findByRole('button', { name: 'Download Tally import file' })).toBeDisabled()
    first.unmount()

    mockChanges({ items: [item({ checkOutcome: 'FOUND' })] })
    renderPanel()
    const button = await screen.findByRole('button', { name: 'Download Tally import file' })
    await waitFor(() => expect(button).toBeEnabled())
  })

  it('exports all eligible items as a blob and downloads it with the server filename', async () => {
    const createObjectURL = vi.fn(() => 'blob:tally')
    const revokeObjectURL = vi.fn()
    Object.assign(URL, { createObjectURL, revokeObjectURL })
    const downloads: string[] = []
    const clickSpy = vi
      .spyOn(HTMLAnchorElement.prototype, 'click')
      .mockImplementation(function (this: HTMLAnchorElement) {
        downloads.push(this.download)
      })

    renderPanel()
    const button = await screen.findByRole('button', { name: 'Download Tally import file' })
    await waitFor(() => expect(button).toBeEnabled())
    await userEvent.click(button)

    expect(mockPost).toHaveBeenCalledWith(
      '/v1/tally-changes/export',
      { confirmStale: false },
      { responseType: 'blob' },
    )
    await waitFor(() => expect(downloads).toEqual(['tally-pan-changes-2026-11-22.json']))
    expect(createObjectURL).toHaveBeenCalled()
    clickSpy.mockRestore()
  })

  it('requires ticking the stale confirmation and sends confirmStale true', async () => {
    mockChanges({
      mastersUpload: { jobId: 'job-1', uploadedAt: '2026-11-20T10:00:00Z', stale: true },
    })
    renderPanel()

    const checkbox = await screen.findByRole('checkbox', { name: /more than 24 hours old/ })
    const button = screen.getByRole('button', { name: 'Download Tally import file' })
    expect(button).toBeDisabled()

    await userEvent.click(checkbox)
    await waitFor(() => expect(button).toBeEnabled())
    await userEvent.click(button)

    await waitFor(() =>
      expect(mockPost).toHaveBeenCalledWith(
        '/v1/tally-changes/export',
        { confirmStale: true },
        { responseType: 'blob' },
      ),
    )
  })

  it('shows a readable message for a 409 export error blob', async () => {
    mockPost.mockRejectedValue({
      response: {
        status: 409,
        data: new Blob([JSON.stringify({ code: 'STALE_CONFIRMATION_REQUIRED' })]),
      },
    })
    renderPanel()

    await userEvent.click(await screen.findByRole('button', { name: 'Download Tally import file' }))

    expect(
      await screen.findByRole('alert'),
    ).toHaveTextContent(/Tick the confirmation box and try again/)
  })

  it('retries a FAILED item and refetches the list', async () => {
    mockChanges({ items: [item({ id: 'failed-1', status: 'FAILED' })] })
    renderPanel()

    await userEvent.click(await screen.findByRole('tab', { name: /^Failed/ }))
    await userEvent.click(await screen.findByRole('button', { name: 'Retry' }))
    expect(mockPost).toHaveBeenCalledWith('/v1/tally-changes/failed-1/retry')
    await waitFor(() => expect(mockGet).toHaveBeenCalledTimes(2))
  })

  it('hides export and retry controls from operators and auditors', async () => {
    mockChanges({ items: [item({ status: 'FAILED' })] })
    setRole('ROLE_OPERATOR')
    const { unmount } = renderPanel()

    await userEvent.click(await screen.findByRole('tab', { name: /^Failed/ }))
    expect(await screen.findByText('ANANDHA TIMBER DEPOT')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Download Tally import file' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Retry' })).not.toBeInTheDocument()
    unmount()

    setRole('ROLE_AUDITOR_CA')
    renderPanel()
    await userEvent.click(await screen.findByRole('tab', { name: /^Failed/ }))
    expect(await screen.findByText('ANANDHA TIMBER DEPOT')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Download Tally import file' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Retry' })).not.toBeInTheDocument()
  })

  it('notes that a masters upload is required and disables download when none exists', async () => {
    mockChanges({ mastersUpload: null })
    renderPanel()

    expect(
      await screen.findByText(/A masters upload is required before you can download/),
    ).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Download Tally import file' })).toBeDisabled()
  })
})
