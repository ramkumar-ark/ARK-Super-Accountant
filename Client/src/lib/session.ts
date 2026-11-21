import axios from 'axios'
import { api } from '@/lib/api'
import { useAuthStore } from '@/store/authStore'

/**
 * Verifies the persisted auth session against the server before a guarded
 * route renders. Returns false when the cached session is missing or the
 * server rejects it (401/403), clearing the store so no stale user data can
 * be rendered. Transient failures (network, 5xx) keep the session: we cannot
 * prove it is dead, and logging users out on a flaky network is worse than
 * the status quo.
 */
export async function validateSession(): Promise<boolean> {
  const { isAuthenticated, token } = useAuthStore.getState()
  if (!isAuthenticated || !token) return false
  try {
    await api.get('/organizations/me/list', { skipAuthRedirect: true })
    return true
  } catch (err) {
    if (axios.isAxiosError(err)) {
      const status = err.response?.status
      if (status === 401 || status === 403) {
        useAuthStore.getState().logout()
        return false
      }
    }
    return true
  }
}
