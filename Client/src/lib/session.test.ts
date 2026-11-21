import { describe, it, expect, vi, beforeEach } from 'vitest'
import { AxiosError, type AxiosResponse, type InternalAxiosRequestConfig } from 'axios'
import { validateSession } from '@/lib/session'
import { useAuthStore } from '@/store/authStore'

vi.mock('@/lib/api', () => ({
  api: { get: vi.fn() },
}))

import { api } from '@/lib/api'

const mockGet = vi.mocked(api.get)

const staleUser = {
  id: 1,
  username: 'staleuser',
  email: 'staleuser@test.local',
  role: 'ROLE_OWNER',
  organizationId: 'org-1',
  organizationName: 'Stale Test Org',
}

function axiosErrorWithStatus(status: number): AxiosError {
  const config = {} as InternalAxiosRequestConfig
  const response = { status, data: {}, config, headers: {} } as unknown as AxiosResponse
  return new AxiosError(
    `Request failed with status code ${status}`,
    'ERR_BAD_REQUEST',
    config,
    null,
    response,
  )
}

describe('validateSession', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    useAuthStore.setState({
      token: 'stale-token',
      user: staleUser,
      organizations: [],
      isAuthenticated: true,
    })
  })

  it('returns false and clears the store when the server rejects the session (401)', async () => {
    mockGet.mockRejectedValue(axiosErrorWithStatus(401))

    await expect(validateSession()).resolves.toBe(false)

    expect(useAuthStore.getState().isAuthenticated).toBe(false)
    expect(useAuthStore.getState().token).toBeNull()
    expect(useAuthStore.getState().user).toBeNull()
  })

  it('returns false and clears the store when access is forbidden (403)', async () => {
    mockGet.mockRejectedValue(axiosErrorWithStatus(403))

    await expect(validateSession()).resolves.toBe(false)

    expect(useAuthStore.getState().isAuthenticated).toBe(false)
    expect(useAuthStore.getState().user).toBeNull()
  })

  it('returns true and keeps the session when the server accepts it', async () => {
    mockGet.mockResolvedValue({ data: [] })

    await expect(validateSession()).resolves.toBe(true)

    expect(mockGet).toHaveBeenCalledWith('/organizations/me/list', { skipAuthRedirect: true })
    expect(useAuthStore.getState().isAuthenticated).toBe(true)
    expect(useAuthStore.getState().user).toEqual(staleUser)
  })

  it('keeps the session on a network error (cannot prove the session is dead)', async () => {
    mockGet.mockRejectedValue(new AxiosError('Network Error', 'ERR_NETWORK'))

    await expect(validateSession()).resolves.toBe(true)

    expect(useAuthStore.getState().isAuthenticated).toBe(true)
  })

  it('keeps the session on a server error (5xx)', async () => {
    mockGet.mockRejectedValue(axiosErrorWithStatus(500))

    await expect(validateSession()).resolves.toBe(true)

    expect(useAuthStore.getState().isAuthenticated).toBe(true)
  })

  it('returns false without a server call when there is no cached session', async () => {
    useAuthStore.setState({ token: null, user: null, organizations: [], isAuthenticated: false })

    await expect(validateSession()).resolves.toBe(false)

    expect(mockGet).not.toHaveBeenCalled()
  })
})
