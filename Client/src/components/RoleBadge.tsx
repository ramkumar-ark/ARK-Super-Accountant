type Role = 'ROLE_OWNER' | 'ROLE_ACCOUNTANT' | 'ROLE_OPERATOR' | 'ROLE_AUDITOR_CA'

interface RoleBadgeProps {
  role: string | undefined
}

const ROLE_CONFIG: Record<Role, { label: string; bg: string; fg: string }> = {
  ROLE_OWNER:      { label: 'Owner',      bg: 'var(--color-warning-bg)', fg: 'var(--color-warning)' },
  ROLE_ACCOUNTANT: { label: 'Accountant', bg: 'var(--color-success-bg)', fg: 'var(--color-success)' },
  ROLE_OPERATOR:   { label: 'Operator',   bg: 'var(--color-primary-light)', fg: 'var(--color-primary)' },
  ROLE_AUDITOR_CA: { label: 'CA Auditor', bg: 'var(--color-info-bg)',    fg: 'var(--color-info)' },
}

export function RoleBadge({ role }: RoleBadgeProps) {
  if (!role || !(role in ROLE_CONFIG)) return null
  const cfg = ROLE_CONFIG[role as Role]
  return (
    <span
      role="status"
      aria-label={`Your role: ${cfg.label}`}
      className="inline-flex items-center px-2 py-0.5 rounded-[var(--radius-sm)] text-xs font-medium uppercase tracking-wide"
      style={{ backgroundColor: cfg.bg, color: cfg.fg }}
    >
      {cfg.label}
    </span>
  )
}