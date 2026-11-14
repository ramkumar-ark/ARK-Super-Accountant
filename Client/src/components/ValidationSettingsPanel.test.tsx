import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ValidationSettingsPanel } from './ValidationSettingsPanel'
import { useAuthStore } from '@/store/authStore'

vi.mock('@/lib/api', () => ({ api: { get: vi.fn(), put: vi.fn() } }))
import { api } from '@/lib/api'

const mockGet = vi.mocked(api.get)
const mockPut = vi.mocked(api.put)

function setRole(role: string) {
  useAuthStore.setState({ user: { id: 1, username: 'u', email: 'u@t.com', role }, isAuthenticated: true })
}

const LABEL = /Require HSN\/SAC on GST-applicable expense ledgers/

describe('ValidationSettingsPanel', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockGet.mockResolvedValue({ data: { requireHsnSacOnExpense: true, updatedBy: null, updatedAt: null } })
  })

  it('shows the default (on) and lets an accountant turn it off', async () => {
    setRole('ROLE_ACCOUNTANT')
    mockPut.mockResolvedValue({ data: { requireHsnSacOnExpense: false, updatedBy: 'acc', updatedAt: null } })
    render(<ValidationSettingsPanel />)

    const box = await screen.findByRole('checkbox', { name: LABEL })
    expect(box).toBeChecked()
    await userEvent.click(box)

    await waitFor(() => expect(mockPut).toHaveBeenCalledWith('/v1/validation-settings', { requireHsnSacOnExpense: false }))
    await waitFor(() => expect(box).not.toBeChecked())
    expect(screen.getByText(/Last changed by acc/)).toBeInTheDocument()
  })

  it('owner sees the setting read-only', async () => {
    setRole('ROLE_OWNER')
    render(<ValidationSettingsPanel />)
    const box = await screen.findByRole('checkbox', { name: LABEL })
    expect(box).toBeDisabled()
    expect(screen.getByText('Only an accountant can change this.')).toBeInTheDocument()
    expect(mockPut).not.toHaveBeenCalled()
  })

  it.each(['ROLE_OPERATOR', 'ROLE_AUDITOR_CA'])('is read-only for %s', async (role) => {
    setRole(role)
    render(<ValidationSettingsPanel />)
    expect(await screen.findByRole('checkbox', { name: LABEL })).toBeDisabled()
    expect(screen.getByText('Only an accountant can change this.')).toBeInTheDocument()
    expect(mockPut).not.toHaveBeenCalled()
  })

  it('shows an error when saving fails and keeps the old value', async () => {
    setRole('ROLE_ACCOUNTANT')
    mockPut.mockRejectedValue(new Error('x'))
    render(<ValidationSettingsPanel />)
    const box = await screen.findByRole('checkbox', { name: LABEL })
    await userEvent.click(box)
    expect(await screen.findByRole('alert')).toHaveTextContent(/Could not save/)
    expect(box).toBeChecked()
  })
})
