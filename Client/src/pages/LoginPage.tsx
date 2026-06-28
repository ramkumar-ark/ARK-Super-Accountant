import { useState, useEffect } from 'react'
import { useNavigate, Link } from '@tanstack/react-router'
import { api } from '@/lib/api'
import { useAuthStore } from '@/store/authStore'
import { InviteSignupBanner } from '@/components/InviteSignupBanner'

export function LoginPage() {
  const navigate = useNavigate()
  const login = useAuthStore((s) => s.login)
  const setOrganizations = useAuthStore((s) => s.setOrganizations)
  const switchOrganization = useAuthStore((s) => s.switchOrganization)
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  const search = new URLSearchParams(window.location.search)
  const inviteToken = search.get('invite')
  const [inviteContext, setInviteContext] = useState<{ orgName: string; role: string } | null>(null)

  useEffect(() => {
    if (!inviteToken) return
    api.get(`/auth/invite/${inviteToken}`)
      .then((res) => {
        setInviteContext({ orgName: res.data.organizationName, role: res.data.role })
      })
      .catch(() => {})
  }, [inviteToken])

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    setError('')
    setLoading(true)
    try {
      const { data } = await api.post('/auth/signin', { username, password })
      login(data.token, {
        id: data.id,
        username: data.username,
        email: data.email,
        role: data.role,
      })

      if (inviteToken) {
        navigate({ to: '/signup', search: { invite: inviteToken } })
        return
      }

      const { data: orgs } = await api.get<Array<{
        organizationId: string
        organizationName: string
        role: string
        isActive: boolean
      }>>('/organizations/me/list')
      setOrganizations(orgs)

      if (orgs.length === 0) {
        navigate({ to: '/organization/setup' })
        return
      }

      const target = orgs.find((o) => o.isActive) ?? orgs[0]
      const { data: selected } = await api.post(`/organizations/${target.organizationId}/select`)
      switchOrganization(selected.token, {
        organizationId: String(selected.organizationId),
        organizationName: selected.organizationName,
        role: selected.role,
      })

      navigate({ to: '/dashboard' })
    } catch (err: unknown) {
      const msg =
        err instanceof Error && 'response' in err
          ? (err as { response?: { data?: { message?: string } } }).response?.data?.message
          : undefined
      setError(msg ?? 'Sign in failed. Please try again.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-[var(--color-bg)] px-4">
      <div className="w-full max-w-sm">
        {/* Logo / brand */}
        <div className="mb-8 text-center">
          <h1 className="text-2xl font-semibold text-[var(--color-text-primary)]">
            Super Accountant
          </h1>
          <p className="mt-1 text-sm text-[var(--color-text-muted)]">
            {inviteToken ? 'Sign in to join the organization' : 'Sign in to your account'}
          </p>
        </div>

        {inviteToken && inviteContext && (
          <InviteSignupBanner orgName={inviteContext.orgName} role={inviteContext.role} />
        )}

        <div className="bg-[var(--color-surface)] rounded-[var(--radius-xl)] shadow-[var(--shadow-md)] p-8">
          <form onSubmit={handleSubmit} noValidate className="space-y-5">
            <div>
              <label
                htmlFor="username"
                className="block text-sm font-medium text-[var(--color-text-primary)] mb-1.5"
              >
                Username
              </label>
              <input
                id="username"
                type="text"
                autoComplete="username"
                required
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                className="w-full h-11 px-3 rounded-[var(--radius-md)] border border-[var(--color-border)] bg-[var(--color-surface-raised)] text-[var(--color-text-primary)] text-sm placeholder:text-[var(--color-text-muted)] focus:outline-none focus:border-[var(--color-primary)] focus:ring-2 focus:ring-[var(--color-primary-subtle)] transition-colors"
                placeholder="Enter your username"
              />
            </div>

            <div>
              <label
                htmlFor="password"
                className="block text-sm font-medium text-[var(--color-text-primary)] mb-1.5"
              >
                Password
              </label>
              <input
                id="password"
                type="password"
                autoComplete="current-password"
                required
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                className="w-full h-11 px-3 rounded-[var(--radius-md)] border border-[var(--color-border)] bg-[var(--color-surface-raised)] text-[var(--color-text-primary)] text-sm placeholder:text-[var(--color-text-muted)] focus:outline-none focus:border-[var(--color-primary)] focus:ring-2 focus:ring-[var(--color-primary-subtle)] transition-colors"
                placeholder="Enter your password"
              />
            </div>

            {error && (
              <p
                role="alert"
                className="text-sm text-[var(--color-danger)] bg-[var(--color-danger-bg)] rounded-[var(--radius-md)] px-3 py-2"
              >
                {error}
              </p>
            )}

            <button
              type="submit"
              disabled={loading || !username || !password}
              className="w-full h-11 rounded-[var(--radius-md)] bg-[var(--color-primary)] text-white text-sm font-medium hover:bg-[var(--color-primary-hover)] transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
            >
              {loading ? 'Signing in…' : 'Sign in'}
            </button>
          </form>
        </div>

        <p className="mt-6 text-center text-sm text-[var(--color-text-muted)]">
          Don't have an account?{' '}
          <Link
            to="/signup"
            search={inviteToken ? { invite: inviteToken } : {}}
            className="text-[var(--color-primary)] font-medium hover:underline"
          >
            Sign up
          </Link>
        </p>
      </div>
    </div>
  )
}
