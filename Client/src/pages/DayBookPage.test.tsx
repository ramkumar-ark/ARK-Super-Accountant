import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { DayBookPage } from './DayBookPage'

vi.mock('@/components/AppShell', () => ({
  AppShell: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
}))
vi.mock('@/lib/api', () => ({
  api: {
    get: vi.fn(),
    post: vi.fn(),
  },
}))
vi.mock('@tanstack/react-router', () => ({
  Link: ({ to, children }: { to: string; children: React.ReactNode }) => (
    <a href={to}>{children}</a>
  ),
}))

import { api } from '@/lib/api'

const mockGet = vi.mocked(api.get)
const mockPost = vi.mocked(api.post)

const uploadsPage = {
  data: {
    content: [
      {
        id: 'job-1',
        fileName: 'daybook-april.json',
        status: 'COMPLETED',
        totalVouchersParsed: 7,
        coverageFrom: '2025-04-01',
        coverageTo: '2025-05-31',
      },
    ],
    totalElements: 1,
  },
}

describe('DayBookPage — upload history', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockGet.mockResolvedValue(uploadsPage as never)
  })

  it('does not query masters gate-status', async () => {
    render(<DayBookPage />)

    await waitFor(() => {
      expect(mockGet).not.toHaveBeenCalledWith('/v1/masters/gate-status')
    })
  })

  it('lists previous uploads with their coverage', async () => {
    render(<DayBookPage />)

    await waitFor(() => {
      expect(screen.getByText('daybook-april.json')).toBeInTheDocument()
      expect(screen.getByText('2025-04-01 – 2025-05-31')).toBeInTheDocument()
    })
  })

  it('has no upload form', async () => {
    render(<DayBookPage />)

    await waitFor(() => {
      expect(mockGet).toHaveBeenCalledWith('/v1/day-book/uploads', {
        params: { page: 0, size: 20 },
      })
    })

    expect(screen.queryByRole('button', { name: 'Upload Day Book' })).not.toBeInTheDocument()
    expect(screen.queryByTestId('file-input')).not.toBeInTheDocument()
    expect(mockPost).not.toHaveBeenCalled()
  })

  it('links to workflows', async () => {
    render(<DayBookPage />)

    expect(screen.getByText('Uploads now happen inside a workflow.')).toBeInTheDocument()
    const link = screen.getByRole('link', { name: 'Go to Workflows' })
    expect(link).toHaveAttribute('href', '/workflows')
  })

  it('shows the change count and expands the change list', async () => {
    const user = userEvent.setup()
    mockGet.mockImplementation((url: string) => {
      if (url === '/v1/day-book/uploads') {
        return Promise.resolve({
          data: {
            content: [
              {
                id: 'u1',
                fileName: 'daybook-april.json',
                status: 'COMPLETED',
                totalVouchersParsed: 7,
                coverageFrom: '2025-04-01',
                coverageTo: '2025-04-30',
                tamperChangeCount: 2,
              },
            ],
          },
        })
      }
      if (url === '/v1/day-book/uploads/u1/changes') {
        return Promise.resolve({
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
            ],
          },
        })
      }
      return Promise.reject(new Error('Unexpected GET ' + url))
    })
    render(
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <DayBookPage />
      </QueryClientProvider>,
    )

    const toggle = await screen.findByRole('button', { name: '2 changes' })
    expect(screen.queryByText('Changes against the frozen day book')).not.toBeInTheDocument()
    await user.click(toggle)

    expect(await screen.findByText('Changes against the frozen day book')).toBeInTheDocument()
    expect(await screen.findByText(/SB\/001/)).toBeInTheDocument()
    expect(mockGet).toHaveBeenCalledWith('/v1/day-book/uploads/u1/changes')
  })
})
