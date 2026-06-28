import { useEffect, useState } from 'react'
import { UserPlus } from 'lucide-react'
import { api } from '@/lib/api'
import { useAuthStore } from '@/store/authStore'
import { AppShell } from '@/components/AppShell'
import { InviteTokenDisplay } from '@/components/InviteTokenDisplay'

const INVITE_ROLES = [
  { value: 'OPERATOR', label: 'Operator' },
  { value: 'ACCOUNTANT', label: 'Accountant' },
  { value: 'AUDITOR_CA', label: 'CA Auditor' },
  { value: 'OWNER', label: 'Owner' },
]

const ROLE_LABELS: Record<string, string> = {
  ROLE_OWNER: 'Owner',
  ROLE_ACCOUNTANT: 'Accountant',
  ROLE_OPERATOR: 'Operator',
  ROLE_AUDITOR_CA: 'CA Auditor',
}

interface Member {
  username: string
  email: string
  role: string
}

interface InviteResult {
  token: string
  expiresAt: string
  role: string
}

export function TeamPage() {
  const { user } = useAuthStore()
  const [tab, setTab] = useState<'members' | 'invite'>('members')

  const [members, setMembers] = useState<Member[]>([])
  const [membersLoading, setMembersLoading] = useState(false)
  const [membersError, setMembersError] = useState('')

  const [role, setRole] = useState('OPERATOR')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [result, setResult] = useState<InviteResult | null>(null)

  const canInvite = user?.role === 'ROLE_OWNER' || user?.role === 'ROLE_ACCOUNTANT'

  useEffect(() => {
    if (!canInvite || !user?.organizationId) return
    setMembersLoading(true)
    setMembersError('')
    api.get(`/organizations/${user.organizationId}/members`)
      .then((res) => setMembers(res.data))
      .catch(() => setMembersError('Could not load members. Please try again.'))
      .finally(() => setMembersLoading(false))
  }, [canInvite, user?.organizationId])

  async function handleGenerate(e: React.FormEvent) {
    e.preventDefault()
    if (!user?.organizationId) return
    setError('')
    setResult(null)
    setLoading(true)
    try {
      const res = await api.post(`/organizations/${user.organizationId}/invites?role=${role}`)
      setResult({ token: res.data.token, expiresAt: res.data.expiresAt, role: res.data.role })
    } catch (err: unknown) {
      const msg =
        err instanceof Error && 'response' in err
          ? (err as { response?: { data?: { message?: string } } }).response?.data?.message
          : undefined
      setError(msg ?? 'Could not generate invite link. Please try again.')
    } finally {
      setLoading(false)
    }
  }

  const selectClass =
    'w-full h-11 px-3 rounded-[var(--radius-md)] border border-[var(--color-border)] bg-[var(--color-surface-raised)] text-[var(--color-text-primary)] text-sm focus:outline-none focus:border-[var(--color-primary)] focus:ring-2 focus:ring-[var(--color-primary-subtle)] transition-colors'

  return (
    <AppShell>
      <main className="flex-1 overflow-y-auto p-6">
        <div className="max-w-lg">
          <p className="text-sm text-[var(--color-text-muted)] mb-6">
            Manage your organization's members and invite new ones.
          </p>

          {canInvite ? (
            <>
              {/* Pill tabs */}
              <div
                role="tablist"
                className="flex gap-1 p-1 mb-6 bg-[var(--color-surface-raised)] rounded-full w-fit"
              >
                {(['members', 'invite'] as const).map((t) => (
                  <button
                    key={t}
                    role="tab"
                    aria-selected={tab === t}
                    onClick={() => setTab(t)}
                    className={[
                      'px-4 py-1.5 rounded-full text-sm font-medium transition-colors',
                      tab === t
                        ? 'bg-[var(--color-primary)] text-white shadow-sm'
                        : 'text-[var(--color-text-secondary)] hover:text-[var(--color-text-primary)]',
                    ].join(' ')}
                  >
                    {t === 'members' ? 'Members' : 'Invite'}
                  </button>
                ))}
              </div>

              {/* Members tab */}
              {tab === 'members' && (
                <div className="bg-[var(--color-surface)] rounded-[var(--radius-xl)] shadow-[var(--shadow-md)] overflow-hidden">
                  {membersLoading && (
                    <p className="text-sm text-[var(--color-text-muted)] px-6 py-4">Loading…</p>
                  )}
                  {membersError && (
                    <p
                      role="alert"
                      className="text-sm text-[var(--color-danger)] bg-[var(--color-danger-bg)] rounded-[var(--radius-md)] mx-6 my-4 px-3 py-2"
                    >
                      {membersError}
                    </p>
                  )}
                  {!membersLoading && !membersError && members.length === 0 && (
                    <p className="text-sm text-[var(--color-text-muted)] px-6 py-4">
                      No other members yet.
                    </p>
                  )}
                  {!membersLoading && !membersError && members.length > 0 && (
                    <ul>
                      {members.map((m, i) => (
                        <li
                          key={m.username}
                          className={[
                            'flex items-center justify-between px-6 py-3',
                            i < members.length - 1
                              ? 'border-b border-[var(--color-border)]'
                              : '',
                          ].join(' ')}
                        >
                          <div>
                            <p className="text-sm font-medium text-[var(--color-text-primary)]">
                              {m.username}
                            </p>
                            <p className="text-xs text-[var(--color-text-muted)]">{m.email}</p>
                          </div>
                          <span className="text-xs font-medium text-[var(--color-text-secondary)] bg-[var(--color-surface-raised)] rounded-full px-2.5 py-1">
                            {ROLE_LABELS[m.role] ?? m.role}
                          </span>
                        </li>
                      ))}
                    </ul>
                  )}
                </div>
              )}

              {/* Invite tab */}
              {tab === 'invite' && (
                <div className="bg-[var(--color-surface)] rounded-[var(--radius-xl)] shadow-[var(--shadow-md)] p-6">
                  <div className="flex items-center gap-2 mb-5">
                    <UserPlus size={18} className="text-[var(--color-text-secondary)]" />
                    <h2 className="text-sm font-semibold text-[var(--color-text-primary)]">
                      Generate Invite Link
                    </h2>
                  </div>

                  <form onSubmit={handleGenerate} noValidate className="space-y-4">
                    <div>
                      <label
                        htmlFor="invite-role"
                        className="block text-sm font-medium text-[var(--color-text-primary)] mb-1.5"
                      >
                        Role
                      </label>
                      <select
                        id="invite-role"
                        value={role}
                        onChange={(e) => { setRole(e.target.value); setResult(null); setError('') }}
                        className={selectClass}
                      >
                        {INVITE_ROLES.filter((r) => user?.role === 'ROLE_OWNER' || r.value !== 'OWNER').map((r) => (
                          <option key={r.value} value={r.value}>
                            {r.label}
                          </option>
                        ))}
                      </select>
                      <p className="mt-1 text-xs text-[var(--color-text-muted)]">
                        The invited user will sign up with this role in your organization.
                      </p>
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
                      disabled={loading}
                      className="w-full h-11 rounded-[var(--radius-md)] bg-[var(--color-primary)] text-white text-sm font-medium hover:bg-[var(--color-primary-hover)] transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
                    >
                      {loading ? 'Generating…' : 'Generate Invite Link'}
                    </button>
                  </form>

                  {result && (
                    <InviteTokenDisplay token={result.token} expiresAt={result.expiresAt} />
                  )}
                </div>
              )}
            </>
          ) : (
            <div className="bg-[var(--color-surface)] rounded-[var(--radius-xl)] shadow-[var(--shadow-md)] p-6">
              <p className="text-sm text-[var(--color-text-muted)]">
                Only Owners and Accountants can invite new members.
              </p>
            </div>
          )}
        </div>
      </main>
    </AppShell>
  )
}
