import { useQuery } from '@tanstack/react-query'
import { getGstr1Validation, type Gstr1ValidationIssue } from '@/lib/api'
import { getWorkflowEvents, isAccountant } from '@/lib/workflowApi'
import type { Workflow } from '@/lib/workflowApi'
import { Gstr1DownloadButton } from './Gstr1DownloadButton'
import { latestPortalDownload } from './portalDownloadEvent'

const PREPARE_ONWARD = ['PREPARE', 'FINALIZED', 'FILED']

function IssueList({ title, issues }: { title: string; issues: Gstr1ValidationIssue[] }) {
  return (
    <div>
      <h3 className="mb-2 text-sm font-semibold text-[var(--color-text-primary)]">{title}</h3>
      {issues.length === 0 ? (
        <p className="text-sm text-[var(--color-text-muted)]">None.</p>
      ) : (
        <ul className="flex flex-col gap-1 text-sm">
          {issues.map((issue, index) => (
            <li
              key={`${issue.code}-${issue.tableCode ?? ''}-${issue.rowKey ?? ''}-${index}`}
            >
              <span className="text-[var(--color-text-secondary)]">
                {issue.tableCode ?? '—'}
                {issue.rowKey ? ` · ${issue.rowKey}` : ''}: {issue.message} ({issue.code})
              </span>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

interface Props {
  workflow: Workflow
  role: string
}

export function Gstr1ValidationPanel({ workflow, role }: Props) {
  if (!PREPARE_ONWARD.includes(workflow.stage)) return null

  return (
    <section className="flex flex-col gap-6 rounded-[var(--radius-lg)] border border-[var(--color-border)] bg-[var(--color-surface)] p-4">
      <header>
        <h2 className="text-base font-semibold text-[var(--color-text-primary)]">
          GSTR-1 Validation & Download
        </h2>
      </header>
      <Gstr1ValidationPanelBody workflow={workflow} role={role} />
    </section>
  )
}

function Gstr1ValidationPanelBody({ workflow, role }: Props) {
  const validationQuery = useQuery({
    queryKey: ['gstr1-validation', workflow.id],
    queryFn: () => getGstr1Validation(workflow.id),
  })
  const eventsQuery = useQuery({
    queryKey: ['workflow-events', workflow.id],
    queryFn: () => getWorkflowEvents(workflow.id),
  })

  const errors = validationQuery.data?.errors ?? []
  const warnings = validationQuery.data?.warnings ?? []
  const latest = eventsQuery.data ? latestPortalDownload(eventsQuery.data) : null
  const canDownload = isAccountant(role)
  const blockedByErrors = errors.length > 0

  return (
    <>
      {validationQuery.isError ? (
        <p role="alert" className="text-sm text-[var(--color-danger)]">
          Could not load validation results.
        </p>
      ) : (
        <>
          <IssueList title="Errors" issues={errors} />
          <IssueList title="Warnings" issues={warnings} />
        </>
      )}

      <div>
        <h3 className="mb-2 text-sm font-semibold text-[var(--color-text-primary)]">
          Portal file
        </h3>
        {latest ? (
          <p className="mb-2 break-all font-mono text-xs text-[var(--color-text-secondary)]">
            Last downloaded hash: {latest.sha256} ({latest.bytes} bytes, writer{' '}
            {latest.writerVersion}, {latest.parts} part{latest.parts === 1 ? '' : 's'})
          </p>
        ) : (
          <p className="mb-2 text-sm text-[var(--color-text-muted)]">
            Not downloaded yet.
          </p>
        )}
        {canDownload ? (
          <Gstr1DownloadButton
            workflowId={workflow.id}
            disabled={blockedByErrors}
            disabledReason="Resolve validation errors before downloading"
          />
        ) : (
          <p className="text-sm text-[var(--color-text-muted)]">
            Only accountants can download the portal file.
          </p>
        )}
        {blockedByErrors && canDownload && (
          <p className="mt-1 text-sm text-[var(--color-danger)]">
            Resolve validation errors before downloading.
          </p>
        )}
      </div>
    </>
  )
}
