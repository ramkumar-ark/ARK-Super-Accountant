import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MastersPage } from './MastersPage'

vi.mock('@/components/AppShell', () => ({
  AppShell: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
}))
vi.mock('@/lib/api', () => ({
  api: {
    get: vi.fn(),
    post: vi.fn(),
    patch: vi.fn(),
  },
}))

import { api } from '@/lib/api'

const mockGet = vi.mocked(api.get)
const mockPatch = vi.mocked(api.patch)

const finding = {
  id: 'finding-1',
  uploadJobId: 'job-1',
  ruleCode: 'GSTIN_PRESENCE',
  ledgerName: 'Acme Traders',
  category: 'SUNDRY_DEBTOR',
  severity: 'HIGH' as const,
  message: 'GSTIN missing',
  suggestedFix: null,
  resolveStatus: 'OPEN' as const,
  resolveNote: null,
  resolvedBy: null,
}

describe('MastersPage — findings resolution', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockGet.mockImplementation((url: string) => {
      if (url.startsWith('/v1/preconfigured-masters')) {
        return Promise.resolve({ data: { content: [], totalElements: 0 } })
      }
      if (url.includes('size=1&showResolved=false')) {
        return Promise.resolve({ data: { totalElements: 1 } })
      }
      if (url.startsWith('/v1/uploads/latest/mismatches')) {
        return Promise.resolve({ data: { content: [finding] } })
      }
      return Promise.resolve({ data: {} })
    })
    mockPatch.mockResolvedValue({ data: {} })
  })

  async function openFindingsTab() {
    render(<MastersPage />)
    const findingsTab = await screen.findByRole('tab', { name: /Findings/ })
    await waitFor(() => expect(findingsTab).not.toBeDisabled())
    await userEvent.click(findingsTab)
    await screen.findByText('Acme Traders')
  }

  it('sends APPROVED when accepting a suggested fix', async () => {
    await openFindingsTab()

    await userEvent.click(screen.getByRole('button', { name: /Accept Fix/ }))

    await waitFor(() => {
      expect(mockPatch).toHaveBeenCalledWith(
        '/v1/uploads/job-1/mismatches/finding-1/resolve',
        { status: 'APPROVED' },
      )
    })
  })

  it('sends APPROVED with the operator note when overriding a value', async () => {
    await openFindingsTab()

    await userEvent.click(screen.getByRole('button', { name: /Override Value/ }))
    await userEvent.type(screen.getByPlaceholderText(/Add a note/), 'manual override')
    await userEvent.click(screen.getByRole('button', { name: /Confirm Override/ }))

    await waitFor(() => {
      expect(mockPatch).toHaveBeenCalledWith(
        '/v1/uploads/job-1/mismatches/finding-1/resolve',
        { status: 'APPROVED', note: 'manual override' },
      )
    })
  })
})
