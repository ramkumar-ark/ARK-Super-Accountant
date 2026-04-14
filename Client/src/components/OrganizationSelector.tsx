import { ChevronDown, X } from 'lucide-react'
import { useState } from 'react'
import { useAuthStore } from '@/store/authStore'
import { api } from '@/lib/api'

export function OrganizationSelector() {
  const { user, organizations, switchOrganization } = useAuthStore()
  const [open, setOpen] = useState(false)
  const [switchingId, setSwitchingId] = useState<string | null>(null)
  const [rowError, setRowError] = useState<string | null>(null)

  if (organizations.length < 2) return null

  async function handleSwitch(orgId: string, orgName: string) {
    setSwitchingId(orgId)
    setRowError(null)
    try {
      const res = await api.post(`/organizations/${orgId}/select`, {})
      switchOrganization(res.data.token, {
        organizationId: orgId,
        organizationName: orgName,
        role: res.data.role,
      })
      setOpen(false)
    } catch {
      setRowError(orgId)
    } finally {
      setSwitchingId(null)
    }
  }

  const activeOrg = organizations.find(o => o.isActive)

  // suppress unused variable warning — user may be used for future display
  void user

  return (
    <div className="relative">
      <button
        onClick={() => setOpen(prev => !prev)}
        className="flex items-center gap-1.5 h-9 px-3 rounded-[var(--radius-md)] border border-[var(--color-border)] bg-[var(--color-surface)] hover:bg-[var(--color-surface-raised)] text-sm text-[var(--color-text-primary)] transition-colors"
      >
        <span>{activeOrg?.organizationName ?? 'Select Organization'}</span>
        <ChevronDown size={18} />
      </button>

      {open && (
        <div
          role="listbox"
          className="absolute right-0 mt-1 w-72 max-h-80 overflow-y-auto bg-[var(--color-surface)] rounded-[var(--radius-md)] shadow-[var(--shadow-lg)] border border-[var(--color-border)] z-50"
        >
          {organizations.map(org => (
            <div
              key={org.organizationId}
              role="option"
              aria-selected={org.isActive}
              className={`flex items-center justify-between px-4 py-3 ${org.isActive ? 'bg-[var(--color-surface-raised)]' : 'hover:bg-[var(--color-surface-raised)] cursor-pointer'}`}
            >
              <div>
                <p className="text-sm text-[var(--color-text-primary)]">{org.organizationName}</p>
                <p className="text-xs text-[var(--color-text-muted)]">{org.role.replace('ROLE_', '')}</p>
              </div>
              <div className="flex items-center gap-2">
                {org.isActive && (
                  <span className="w-2 h-2 rounded-full bg-[var(--color-primary)]" title="Active" />
                )}
                {!org.isActive && (
                  switchingId === org.organizationId ? (
                    <span className="text-xs text-[var(--color-text-muted)]">Switching…</span>
                  ) : rowError === org.organizationId ? (
                    <span className="flex items-center gap-1 text-xs text-[var(--color-danger)]">
                      <X size={14} />
                      <span>Could not switch to that organization. You may no longer be a member. Refresh and try again.</span>
                    </span>
                  ) : (
                    <button
                      onClick={() => handleSwitch(org.organizationId, org.organizationName)}
                      aria-label={`Switch to ${org.organizationName}`}
                      className="text-xs text-[var(--color-primary)] font-medium hover:underline"
                    >
                      Switch to {org.organizationName}
                    </button>
                  )
                )}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
