import { useState, useEffect } from 'react'
import { useNavigate, Link } from '@tanstack/react-router'
import { api } from '@/lib/api'
import { useAuthStore } from '@/store/authStore'
import { InviteSignupBanner } from '@/components/InviteSignupBanner'

const ROLES = [
  { value: 'owner', label: 'Owner' },
  { value: 'accountant', label: 'Accountant' },
  { value: 'operator', label: 'Operator' },
  { value: 'auditor_ca', label: 'CA Auditor' },
]

export function SignupPage() {
  const navigate = useNavigate()
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated)
  const switchOrganization = useAuthStore((s) => s.switchOrganization)
  const setOrganizations = useAuthStore((s) => s.setOrganizations)
  const [form, setForm] = useState({
    username: '',
    email: '',
    password: '',
    role: 'operator',
  })
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)
  const [inviteContext, setInviteContext] = useState<{ orgName: string; role: string } | null>(null)
  const [inviteError, setInviteError] = useState('')

  const search = new URLSearchParams(window.location.search)
  const inviteToken = search.get('invite')

  const ROLE_MAP: Record<string, string> = {
    OWNER: 'owner',
    ACCOUNTANT: 'accountant',
    DATA_ENTRY_OPERATOR: 'operator',
    CASHIER: 'operator',
    OPERATOR: 'operator',
    AUDITOR_CA: 'auditor_ca',
  }

  useEffect(() => {
    if (!inviteToken) return
    api.get(`/auth/invite/${inviteToken}`)
      .then((res) => {
        const rawRole: string = res.data.role ?? ''
        const normalizedRole = ROLE_MAP[rawRole] ?? rawRole.toLowerCase() ?? 'operator'
        setInviteContext({ orgName: res.data.organizationName, role: normalizedRole })
        setForm((prev) => ({ ...prev, role: normalizedRole }))
      })
      .catch((err: unknown) => {
        const msg =
          err instanceof Error && 'response' in err
            ? (err as { response?: { data?: { message?: string } } }).response?.data?.message
            : undefined
        setInviteError(msg ?? 'This invite link is invalid or has expired.')
      })
  }, [inviteToken])

  async function handleAcceptInvite() {
    if (!inviteToken) return
    setError('')
    setLoading(true)
    try {
      const { data } = await api.post(`/organizations/invites/${inviteToken}/accept`)
      const { data: orgs } = await api.get('/organizations/me/list')
      setOrganizations(orgs)
      switchOrganization(data.token, {
        organizationId: String(data.organizationId),
        organizationName: data.organizationName,
        role: data.role,
      })
      navigate({ to: '/dashboard' })
    } catch (err: unknown) {
      const msg =
        err instanceof Error && 'response' in err
          ? (err as { response?: { data?: { message?: string } } }).response?.data?.message
          : undefined
      setError(msg ?? 'Failed to join organization. Please try again.')
    } finally {
      setLoading(false)
    }
  }

  function set(field: keyof typeof form) {
    return (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) =>
      setForm((prev) => ({ ...prev, [field]: e.target.value }))
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    setError('')
    setLoading(true)
    try {
      const url = inviteToken ? `/auth/signup?invite=${inviteToken}` : '/auth/signup'
      await api.post(url, form)
      if (inviteToken) {
        navigate({ to: '/dashboard' })
      } else {
        navigate({ to: '/login' })
      }
    } catch (err: unknown) {
      const msg =
        err instanceof Error && 'response' in err
          ? (err as { response?: { data?: { message?: string } } }).response?.data?.message
          : undefined
      setError(msg ?? 'Registration failed. Please try again.')
    } finally {
      setLoading(false)
    }
  }

  const isValid =
    form.username.length >= 3 &&
    form.email.includes('@') &&
    form.password.length >= 6

  const formDisabled = loading || !!inviteError

  if (isAuthenticated && inviteToken) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-[var(--color-bg)] px-4">
        <div className="w-full max-w-sm">
          <div className="mb-8 text-center">
            <h1 className="text-2xl font-semibold text-[var(--color-text-primary)]">
              Super Accountant
            </h1>
            <p className="mt-1 text-sm text-[var(--color-text-muted)]">
              Join an organization
            </p>
          </div>

          {inviteToken && (
            <InviteSignupBanner
              orgName={inviteContext?.orgName}
              role={inviteContext?.role}
              error={inviteError || undefined}
            />
          )}

          <div className="bg-[var(--color-surface)] rounded-[var(--radius-xl)] shadow-[var(--shadow-md)] p-8">
            {inviteContext && (
              <p className="text-sm text-[var(--color-text-secondary)] mb-5">
                You're signed in. Click below to join <strong>{inviteContext.orgName}</strong> as <strong>{ROLES.find((r) => r.value === inviteContext.role)?.label ?? inviteContext.role}</strong>.
              </p>
            )}

            {error && (
              <p
                role="alert"
                className="text-sm text-[var(--color-danger)] bg-[var(--color-danger-bg)] rounded-[var(--radius-md)] px-3 py-2 mb-5"
              >
                {error}
              </p>
            )}

            <button
              onClick={handleAcceptInvite}
              disabled={loading || !!inviteError || !inviteContext}
              className="w-full h-11 rounded-[var(--radius-md)] bg-[var(--color-primary)] text-white text-sm font-medium hover:bg-[var(--color-primary-hover)] transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
            >
              {loading ? 'Joining…' : 'Join Organization'}
            </button>
          </div>

          <p className="mt-6 text-center text-sm text-[var(--color-text-muted)]">
            <Link to="/dashboard" className="text-[var(--color-primary)] font-medium hover:underline">
              Back to dashboard
            </Link>
          </p>
        </div>
      </div>
    )
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
            Create your account
          </p>
        </div>

        {inviteToken && (
          <InviteSignupBanner
            orgName={inviteContext?.orgName}
            role={inviteContext?.role}
            error={inviteError || undefined}
          />
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
                minLength={3}
                maxLength={20}
                disabled={formDisabled}
                value={form.username}
                onChange={set('username')}
                className="w-full h-11 px-3 rounded-[var(--radius-md)] border border-[var(--color-border)] bg-[var(--color-surface-raised)] text-[var(--color-text-primary)] text-sm placeholder:text-[var(--color-text-muted)] focus:outline-none focus:border-[var(--color-primary)] focus:ring-2 focus:ring-[var(--color-primary-subtle)] transition-colors disabled:opacity-50"
                placeholder="3–20 characters"
              />
            </div>

            <div>
              <label
                htmlFor="email"
                className="block text-sm font-medium text-[var(--color-text-primary)] mb-1.5"
              >
                Email
              </label>
              <input
                id="email"
                type="email"
                autoComplete="email"
                required
                disabled={formDisabled}
                value={form.email}
                onChange={set('email')}
                className="w-full h-11 px-3 rounded-[var(--radius-md)] border border-[var(--color-border)] bg-[var(--color-surface-raised)] text-[var(--color-text-primary)] text-sm placeholder:text-[var(--color-text-muted)] focus:outline-none focus:border-[var(--color-primary)] focus:ring-2 focus:ring-[var(--color-primary-subtle)] transition-colors disabled:opacity-50"
                placeholder="you@company.com"
              />
            </div>

            <div>
              <label
                htmlFor="role"
                className="block text-sm font-medium text-[var(--color-text-primary)] mb-1.5"
              >
                Role
              </label>
              <select
                id="role"
                value={form.role}
                onChange={set('role')}
                disabled={formDisabled || !!inviteContext}
                className="w-full h-11 px-3 rounded-[var(--radius-md)] border border-[var(--color-border)] bg-[var(--color-surface-raised)] text-[var(--color-text-primary)] text-sm focus:outline-none focus:border-[var(--color-primary)] focus:ring-2 focus:ring-[var(--color-primary-subtle)] transition-colors disabled:opacity-50"
              >
                {ROLES.map((r) => (
                  <option key={r.value} value={r.value}>
                    {r.label}
                  </option>
                ))}
              </select>
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
                autoComplete="new-password"
                required
                minLength={6}
                disabled={formDisabled}
                value={form.password}
                onChange={set('password')}
                className="w-full h-11 px-3 rounded-[var(--radius-md)] border border-[var(--color-border)] bg-[var(--color-surface-raised)] text-[var(--color-text-primary)] text-sm placeholder:text-[var(--color-text-muted)] focus:outline-none focus:border-[var(--color-primary)] focus:ring-2 focus:ring-[var(--color-primary-subtle)] transition-colors disabled:opacity-50"
                placeholder="Minimum 6 characters"
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
              disabled={formDisabled || !isValid}
              className="w-full h-11 rounded-[var(--radius-md)] bg-[var(--color-primary)] text-white text-sm font-medium hover:bg-[var(--color-primary-hover)] transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
            >
              {loading ? 'Creating account…' : 'Create account'}
            </button>
          </form>
        </div>

        <p className="mt-6 text-center text-sm text-[var(--color-text-muted)]">
          Already have an account?{' '}
          <Link
            to="/login"
            search={inviteToken ? { invite: inviteToken } : {}}
            className="text-[var(--color-primary)] font-medium hover:underline"
          >
            Sign in{inviteToken ? ' to join' : ''}
          </Link>
        </p>
      </div>
    </div>
  )
}
