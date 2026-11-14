import type { WorkflowEvent } from '@/lib/workflowApi'

function kindLabel(kind: string): string {
  const lower = kind.toLowerCase().replace(/_/g, ' ')
  return lower.charAt(0).toUpperCase() + lower.slice(1)
}

export function WorkflowEventsTimeline({ events }: { events: WorkflowEvent[] }) {
  const newestFirst = [...events].sort(
    (a, b) => new Date(b.occurredAt).getTime() - new Date(a.occurredAt).getTime(),
  )

  return (
    <section data-testid="workflow-events" className="flex flex-col gap-3">
      <h2 className="text-sm font-semibold text-[var(--color-text-primary)]">Events</h2>
      {newestFirst.length === 0 ? (
        <p className="text-sm text-[var(--color-text-muted)]">No events yet.</p>
      ) : (
        <ul className="flex flex-col gap-2">
          {newestFirst.map((event) => (
            <li
              key={event.id}
              className="flex flex-col gap-1 rounded-[var(--radius-md)] border border-[var(--color-border)] bg-[var(--color-surface)] px-3 py-2 text-sm"
            >
              <div className="flex items-center justify-between gap-3">
                <span className="font-medium text-[var(--color-text-primary)]">
                  {kindLabel(event.kind)}
                </span>
                {event.actor && (
                  <span className="text-xs text-[var(--color-text-muted)]">{event.actor}</span>
                )}
              </div>
              {event.reason && (
                <span className="text-xs text-[var(--color-text-secondary)]">{event.reason}</span>
              )}
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}
