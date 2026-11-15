import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { UploadChangeList } from './UploadChangeList'
import { api } from '@/lib/api'

vi.mock('@/lib/api', () => ({ api: { get: vi.fn(), post: vi.fn() } }))

const mockGet = vi.mocked(api.get)

function renderList(uploadId = 'u1') {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={client}>
      <UploadChangeList uploadId={uploadId} />
    </QueryClientProvider>,
  )
}

describe('UploadChangeList', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('renders blocking and informational changes', async () => {
    mockGet.mockResolvedValueOnce({
      data: {
        changes: [
          {
            id: 'c1',
            changeKind: 'MODIFIED',
            voucherKey: 'G:g-1',
            voucherTypeName: 'Sales',
            voucherNumber: 'SB/001',
            partyLedgerName: 'Client ABC',
            oldDate: '2025-04-15',
            newDate: '2025-04-15',
            touchedScopes: ['GSTR1'],
            blocking: true,
          },
          {
            id: 'c2',
            changeKind: 'INSERTED',
            voucherKey: 'G:g-9',
            voucherTypeName: 'Sales',
            voucherNumber: 'SB/009',
            partyLedgerName: 'Client ABC',
            oldDate: null,
            newDate: '2025-04-28',
            touchedScopes: ['GSTR3B'],
            blocking: false,
          },
        ],
      },
    })
    renderList()
    await waitFor(() => expect(screen.getByText(/SB\/001/)).toBeInTheDocument())
    expect(screen.getByText('Blocking')).toBeInTheDocument()
    expect(screen.getByText('Informational')).toBeInTheDocument()
    expect(mockGet).toHaveBeenCalledWith('/v1/day-book/uploads/u1/changes')
  })

  it('shows the empty state', async () => {
    mockGet.mockResolvedValueOnce({ data: { changes: [] } })
    renderList()
    await waitFor(() =>
      expect(screen.getByText('No changes against the frozen day book.')).toBeInTheDocument(),
    )
  })

  it('shows the API error message', async () => {
    mockGet.mockRejectedValueOnce({
      response: { data: { error: 'Upload not found' } },
    })
    renderList()
    await waitFor(() => expect(screen.getByText('Upload not found')).toBeInTheDocument())
  })
})
