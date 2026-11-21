import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { PanOverridePanel } from './PanOverridePanel'

vi.mock('@/lib/api', () => ({
  api: { get: vi.fn() },
}))

import { api } from '@/lib/api'

const mockGet = vi.mocked(api.get)

const OVERRIDE = {
  partyLedgerName: 'ABC Contractors',
  pan: 'ABCDE1234F',
  approvedBy: 'alice',
  approvedAt: '2025-05-01T10:00:00Z',
  mastersPan: 'ABCDE1234F',
  mastersDiffers: false,
}

function renderPanel() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <PanOverridePanel />
    </QueryClientProvider>,
  )
}

describe('PanOverridePanel', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('lists overrides with deductee, PAN and approved by/at', async () => {
    mockGet.mockResolvedValue({ data: [OVERRIDE] })

    renderPanel()

    expect(await screen.findByText('ABC Contractors')).toBeInTheDocument()
    expect(mockGet).toHaveBeenCalledWith('/v1/tds/pan-overrides')
    expect(screen.getByText('ABCDE1234F')).toBeInTheDocument()
    expect(screen.getByText('alice')).toBeInTheDocument()
    expect(screen.getByText('2025-05-01')).toBeInTheDocument()
  })

  it('shows No PAN for an override with a blank PAN', async () => {
    mockGet.mockResolvedValue({ data: [{ ...OVERRIDE, pan: '' }] })

    renderPanel()

    expect(await screen.findByText('ABC Contractors')).toBeInTheDocument()
    expect(screen.getByText('No PAN')).toBeInTheDocument()
  })

  it('shows an empty state when there are no overrides', async () => {
    mockGet.mockResolvedValue({ data: [] })

    renderPanel()

    expect(await screen.findByText(/No PAN overrides/)).toBeInTheDocument()
  })

  it('offers no edit controls — overrides are created by approving a finding', async () => {
    mockGet.mockResolvedValue({ data: [OVERRIDE] })

    renderPanel()

    await screen.findByText('ABC Contractors')
    expect(screen.queryByRole('button')).not.toBeInTheDocument()
    expect(screen.queryByRole('textbox')).not.toBeInTheDocument()
  })
})
