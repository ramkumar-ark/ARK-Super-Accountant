/**
 * A warning for non-blocking masters tamper findings: the frozen masters value
 * behind a finalized return of another type changed, but this workflow is not
 * blocked by it.
 */
export function MastersTamperWarningBanner({
  findings,
}: {
  findings: Array<{ findingType: string; blocking: boolean; message: string }>
}) {
  const affected = findings.some(
    (f) => f.findingType.startsWith('TAMPER_MASTER_') && f.blocking === false,
  )
  if (!affected) return null
  return (
    <div
      role="note"
      className="flex flex-col gap-2 rounded-[var(--radius-lg)] border border-[var(--color-info)] bg-[var(--color-info-bg)] p-4"
    >
      <p className="text-sm text-[var(--color-text-primary)]">
        A masters change affects a finalized return of another type. It does not block this
        workflow.
      </p>
      {findings
        .filter((f) => f.findingType.startsWith('TAMPER_MASTER_') && f.blocking === false)
        .map((f, i) => (
          <p key={i} className="text-xs text-[var(--color-text-secondary)]">
            {f.message}
          </p>
        ))}
    </div>
  )
}
