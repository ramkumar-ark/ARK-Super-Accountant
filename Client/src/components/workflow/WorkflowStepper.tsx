import { Check } from 'lucide-react'
import {
  STAGE_LABEL,
  WORKFLOW_TYPE_LABEL,
  type WorkflowDependency,
  type WorkflowStage,
} from '@/lib/workflowApi'

const STEPS: WorkflowStage[] = [
  'STARTED',
  'DATA_UPLOADED',
  'FINDINGS_REVIEW',
  'PREPARE',
  'FINALIZED',
  'FILED',
]

function typeLabel(type: string): string {
  return (WORKFLOW_TYPE_LABEL as Record<string, string>)[type] ?? type
}

export function WorkflowStepper({
  stage,
  dependencies,
}: {
  stage: WorkflowStage
  dependencies: WorkflowDependency[]
}) {
  if (stage === 'BASELINE') {
    return (
      <section className="rounded-[var(--radius-lg)] border border-[var(--color-border)] bg-[var(--color-surface)] p-4">
        <span className="inline-flex items-center rounded-[var(--radius-sm)] bg-[var(--color-primary-light)] px-2 py-1 text-xs font-medium text-[var(--color-primary)]">
          Baseline period
        </span>
      </section>
    )
  }

  const currentIndex = Math.max(0, STEPS.indexOf(stage))

  return (
    <section className="flex flex-col gap-4 rounded-[var(--radius-lg)] border border-[var(--color-border)] bg-[var(--color-surface)] p-4">
      <ol className="flex flex-wrap items-center gap-2">
        {STEPS.map((step, index) => {
          const state = index < currentIndex ? 'done' : index === currentIndex ? 'current' : 'upcoming'
          const base =
            'flex items-center gap-2 rounded-[var(--radius-md)] border px-3 py-2 text-xs font-medium'
          const styling =
            state === 'done'
              ? 'border-[var(--color-success)] bg-[var(--color-success-bg)] text-[var(--color-success)]'
              : state === 'current'
                ? 'border-[var(--color-primary)] bg-[var(--color-primary-light)] text-[var(--color-primary)]'
                : 'border-[var(--color-border)] bg-[var(--color-surface-raised)] text-[var(--color-text-muted)]'
          return (
            <li
              key={step}
              data-state={state}
              aria-current={index === currentIndex ? 'step' : undefined}
              className={`${base} ${styling}`}
            >
              {state === 'done' ? (
                <Check size={14} aria-hidden="true" />
              ) : (
                <span aria-hidden="true">{index + 1}</span>
              )}
              <span>{STAGE_LABEL[step]}</span>
            </li>
          )
        })}
      </ol>

      {dependencies.length > 0 && (
        <div>
          <h3 className="mb-2 text-sm font-semibold text-[var(--color-text-primary)]">
            Dependencies
          </h3>
          <ul data-testid="workflow-dependencies" className="flex flex-col gap-2">
            {dependencies.map((dependency) => (
              <li
                key={`${dependency.type}-${dependency.periodKey}`}
                className="flex items-center justify-between gap-3 rounded-[var(--radius-md)] border border-[var(--color-border)] px-3 py-2 text-sm"
              >
                <span className="text-[var(--color-text-primary)]">
                  {typeLabel(dependency.type)} {dependency.periodKey}
                </span>
                <span
                  className={
                    dependency.satisfied
                      ? 'rounded-[var(--radius-sm)] bg-[var(--color-success-bg)] px-2 py-0.5 text-xs font-medium text-[var(--color-success)]'
                      : 'rounded-[var(--radius-sm)] bg-[var(--color-warning-bg)] px-2 py-0.5 text-xs font-medium text-[var(--color-text-secondary)]'
                  }
                >
                  {dependency.satisfied ? 'Finalized' : 'Not finalized'}
                </span>
              </li>
            ))}
          </ul>
        </div>
      )}
    </section>
  )
}
