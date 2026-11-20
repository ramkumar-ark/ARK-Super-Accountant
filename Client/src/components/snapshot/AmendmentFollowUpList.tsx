import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { api } from '@/lib/api'
import { apiErrorMessage } from '@/lib/apiError'
import type { ReviewRole } from '@/components/review/FindingReviewPanel'

interface ProducedRow {
  tableCode: string
  rowKey: string
  lateReporting: boolean
}

interface FollowUp {
  id: string
  voucherKey: string
  summary: string
  status: 'OPEN' | 'DONE'
  change: 'CHANGED' | 'DELETED' | 'LATE' | null
  producedRow?: ProducedRow | null
  doneBy: string | null
  doneAt: string | null
  doneNote: string | null
  createdAt: string
}

/**
 * Amendment follow-ups for one workflow: one row per change accepted as a
 * correction while a finalized workflow covered the affected period. The
 * accountant marks each done; other roles read only. Rendered inside the
 * workflow screens worker O4 mounts.
 */
export function AmendmentFollowUpList({
  workflowId,
  role,
}: {
  workflowId: string
  role: ReviewRole
}) {
  const queryClient = useQueryClient()
  const followUpsQuery = useQuery({
    queryKey: ['workflow-amendments', workflowId],
    queryFn: async () => {
      const res = await api.get(`/v1/workflows/${workflowId}/amendments`)
      return res.data as { followUps: FollowUp[] }
    },
  })
  const [markingId, setMarkingId] = useState<string | null>(null)
  const [note, setNote] = useState('')
  const [error, setError] = useState<string | null>(null)

  async function markDone(id: string) {
    try {
      await api.post(`/v1/workflows/${workflowId}/amendments/${id}/done`, { note })
      setMarkingId(null)
      setNote('')
      setError(null)
      await queryClient.invalidateQueries({ queryKey: ['workflow-amendments', workflowId] })
    } catch (err) {
      setError(apiErrorMessage(err, 'Could not mark this follow-up done. Try again.'))
    }
  }

  const followUps = followUpsQuery.data?.followUps ?? []

  return (
    <div>
      <h3 className="text-sm font-semibold text-[var(--color-text-primary)] mb-3">
        Amendment follow-ups
      </h3>
      {followUpsQuery.isLoading && (
        <p className="text-sm text-[var(--color-text-secondary)]" role="status">
          Loading follow-ups…
        </p>
      )}
      {followUpsQuery.isError && (
        <p className="text-sm text-[var(--color-danger)]" role="alert">
          {apiErrorMessage(followUpsQuery.error, 'Could not load follow-ups. Try again.')}
        </p>
      )}
      {!followUpsQuery.isLoading && !followUpsQuery.isError && followUps.length === 0 && (
        <p className="text-sm text-[var(--color-text-muted)]">No amendment follow-ups.</p>
      )}
      <div className="flex flex-col gap-3">
        {followUps.map((f) => (
          <div
            key={f.id}
            className="bg-[var(--color-surface)] rounded-[var(--radius-md)] border border-[var(--color-border)] p-4"
          >
            <div className="flex items-start gap-3">
              <div className="flex-1 min-w-0">
                <div className="flex items-center gap-2 mb-1">
                  <span className="text-xs px-2 py-0.5 rounded-full bg-[var(--color-info-bg)] text-[var(--color-info)]">
                    {f.status}
                  </span>
                  {f.change === 'LATE' ? (
                    <span className="text-xs px-2 py-0.5 rounded-full bg-[var(--color-warning-bg)] text-[var(--color-warning)]">
                      late reporting
                    </span>
                  ) : f.change ? (
                    <span className="text-xs px-2 py-0.5 rounded-full bg-[var(--color-surface-raised)] text-[var(--color-text-secondary)]">
                      {f.change}
                    </span>
                  ) : null}
                  <span className="text-xs text-[var(--color-text-muted)] font-mono">
                    {f.voucherKey}
                  </span>
                </div>
                <p className="text-sm text-[var(--color-text-secondary)]">{f.summary}</p>
                {f.producedRow && (
                  <p className="text-xs text-[var(--color-text-muted)] mt-1">
                    {f.producedRow.lateReporting ? 'late ' : ''}
                    {f.producedRow.tableCode} row {f.producedRow.rowKey}
                  </p>
                )}
                {f.status === 'DONE' && f.doneNote && (
                  <p className="text-xs text-[var(--color-text-muted)] mt-1">
                    Done by {f.doneBy}
                    {f.doneAt ? ` on ${f.doneAt.slice(0, 10)}` : ''}: {f.doneNote}
                  </p>
                )}
              </div>
              {role === 'ROLE_ACCOUNTANT' && f.status === 'OPEN' && markingId !== f.id && (
                <button
                  type="button"
                  onClick={() => {
                    setMarkingId(f.id)
                    setNote('')
                    setError(null)
                  }}
                  className="text-xs px-3 py-1.5 rounded-[var(--radius-sm)] border border-[var(--color-border)] text-[var(--color-text-primary)] hover:bg-[var(--color-surface-raised)]"
                >
                  Mark done
                </button>
              )}
            </div>
            {markingId === f.id && (
              <div className="mt-3 flex flex-col gap-2">
                <textarea
                  role="textbox"
                  value={note}
                  onChange={(e) => setNote(e.target.value)}
                  placeholder="How was this amended?"
                  className="w-full text-sm rounded-[var(--radius-sm)] border border-[var(--color-border)] bg-[var(--color-surface)] p-2 text-[var(--color-text-primary)]"
                />
                {error && (
                  <p className="text-sm text-[var(--color-danger)]" role="alert">
                    {error}
                  </p>
                )}
                <div className="flex gap-2">
                  <button
                    type="button"
                    onClick={() => markDone(f.id)}
                    className="text-xs px-3 py-1.5 rounded-[var(--radius-sm)] bg-[var(--color-primary)] text-[var(--color-primary-contrast)]"
                  >
                    Save
                  </button>
                  <button
                    type="button"
                    onClick={() => {
                      setMarkingId(null)
                      setError(null)
                    }}
                    className="text-xs px-3 py-1.5 rounded-[var(--radius-sm)] border border-[var(--color-border)] text-[var(--color-text-secondary)]"
                  >
                    Cancel
                  </button>
                </div>
              </div>
            )}
          </div>
        ))}
      </div>
    </div>
  )
}
