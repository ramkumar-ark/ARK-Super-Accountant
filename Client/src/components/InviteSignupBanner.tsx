export function InviteSignupBanner({ orgName, role, error }: {
  orgName?: string
  role?: string
  error?: string
}) {
  if (error) {
    return (
      <div role="alert" className="mb-4 border-l-[3px] border-[var(--color-danger)] bg-[var(--color-danger-bg)] px-4 py-3 rounded-[var(--radius-md)]">
        <p className="text-sm text-[var(--color-danger)]">{error}</p>
      </div>
    )
  }
  return (
    <div role="status" className="mb-4 border-l-[3px] border-[var(--color-primary)] bg-[var(--color-surface)] px-4 py-3 rounded-[var(--radius-md)] shadow-sm">
      <p className="text-sm text-[var(--color-text-primary)]">
        You've been invited to join <strong>{orgName}</strong> as <strong>{role}</strong>
      </p>
    </div>
  )
}
