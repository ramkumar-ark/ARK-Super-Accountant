import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MastersChangeList } from './MastersChangeList'
import { api } from '@/lib/api'

vi.mock('@/lib/api', () => ({ api: { get: vi.fn(), post: vi.fn() } }))

const mockGet = vi.mocked(api.get)

function renderList(uploadId = 'm1') {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={client}>
      <MastersChangeList uploadId={uploadId} />
    </QueryClientProvider>,
  )
}

describe('MastersChangeList', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('lists attribute changes with old and new value', async () => {
    mockGet.mockResolvedValueOnce({
      data: {
        changes: [
          {
            id: 'c1',
            changeKind: 'MASTER_MODIFIED',
            ledgerName: 'Acme Traders',
            attributeName: 'gstin',
            oldValue: '29ABCDE1234F1Z5',
            newValue: '29ABCDE1234F1Z6',
            touchedScopes: ['GSTR1', 'GSTR3B'],
            blocking: true,
            createdAt: '2025-05-10T10:00:00Z',
          },
        ],
      },
    })
    renderList()
    await waitFor(() =>
      expect(screen.getByText('GSTIN: 29ABCDE1234F1Z5 → 29ABCDE1234F1Z6')).toBeInTheDocument(),
    )
    expect(mockGet).toHaveBeenCalledWith('/v1/masters/uploads/m1/changes')
    expect(screen.getByText('Blocking')).toBeInTheDocument()
  })

  it('shows the empty state', async () => {
    mockGet.mockResolvedValueOnce({ data: { changes: [] } })
    renderList()
    await waitFor(() =>
      expect(screen.getByText('No changes against the frozen masters.')).toBeInTheDocument(),
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
