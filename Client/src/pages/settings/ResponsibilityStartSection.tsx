import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useAuthStore } from '@/store/authStore'
import { apiErrorMessage } from '@/lib/apiError'
import {
  getResponsibilityStarts,
  putResponsibilityStart,
  type ResponsibilityStart,
} from '@/lib/complianceApi'
import { WORKFLOW_TYPE_LABEL, WORKFLOW_TYPES, type WorkflowType } from '@/lib/workflowApi'

const ORDER: WorkflowType[] = [...WORKFLOW_TYPES]

export function ResponsibilityStartSection() {
  const { user } = useAuthStore()
  const queryClient = useQueryClient()
  const canEdit = user?.role === 'ROLE_ACCOUNTANT'
  const [error, setError] = useState<string | null>(null)

  const { data: rows, isLoading } = useQuery({
    queryKey: ['responsibility-starts'],
    queryFn: getResponsibilityStarts,
  })

  const mutation = useMutation({
    mutationFn: ({ type, date }: { type: WorkflowType; date: string }) =>
      putResponsibilityStart(type, date),
    onSuccess: () => {
      setError(null)
      queryClient.invalidateQueries({ queryKey: ['responsibility-starts'] })
    },
    onError: (err) => setError(apiErrorMessage(err, 'Could not save responsibility start')),
  })

  const byType = new Map<WorkflowType, ResponsibilityStart>(
    (rows ?? []).map((r) => [r.type, r]),
  )

  if (isLoading) return <p className="text-sm text-[var(--color-text-muted)]">Loading…</p>

  return (
    <section>
      <h2 className="text-lg font-semibold mb-2">Responsibility start</h2>
      <p className="text-sm text-[var(--color-text-muted)] mb-3">
        First period your firm prepares for each return. Earlier periods in open years become
        baseline.
      </p>
      {error && (
        <p role="alert" className="text-sm text-red-600 mb-2">
          {error}
        </p>
      )}
      <table className="w-full text-sm">
        <thead>
          <tr className="text-left border-b">
            <th className="py-2">Return</th>
            <th className="py-2">First period</th>
            <th className="py-2">Baseline</th>
            <th className="py-2"></th>
          </tr>
        </thead>
        <tbody>
          {ORDER.map((type) => {
            const row = byType.get(type)
            const date = row?.firstPeriodStart ?? ''
            return (
              <tr key={type} className="border-b">
                <td className="py-2">{WORKFLOW_TYPE_LABEL[type]}</td>
                <td className="py-2">
                  {row?.firstPeriodStart ? (
                    <span data-testid={`resp-${type}`}>{row.firstPeriodStart}</span>
                  ) : (
                    <span className="text-[var(--color-text-muted)]">Not set</span>
                  )}
                </td>
                <td className="py-2">
                  {row && row.baselineRowCount > 0 && (
                    <span>
                      {row.baselineRowCount} baseline periods
                    </span>
                  )}
                  {row?.locked && (
                    <span className="ml-2 px-1.5 py-0.5 text-xs rounded bg-amber-100 text-amber-800">
                      Locked
                    </span>
                  )}
                </td>
                <td className="py-2">
                  {canEdit && (
                    <RespEditControl
                      type={type}
                      initialDate={date}
                      disabled={row?.locked ?? false}
                      onSave={(d) => mutation.mutate({ type, date: d })}
                    />
                  )}
                </td>
              </tr>
            )
          })}
        </tbody>
      </table>
    </section>
  )
}

function RespEditControl({
  type,
  initialDate,
  disabled,
  onSave,
}: {
  type: WorkflowType
  initialDate: string
  disabled: boolean
  onSave: (date: string) => void
}) {
  const [date, setDate] = useState(initialDate)
  return (
    <span className="flex items-center gap-2 justify-end">
      <input
        type="date"
        aria-label={`${WORKFLOW_TYPE_LABEL[type]} responsibility start date`}
        value={date}
        disabled={disabled}
        onChange={(e) => setDate(e.target.value)}
        className="border rounded px-2 py-1 text-sm"
      />
      <button
        type="button"
        disabled={disabled || !date || date === initialDate}
        onClick={() => onSave(date)}
        className="px-3 py-1 text-sm rounded bg-[var(--color-primary)] text-white disabled:opacity-50"
      >
        Save
      </button>
    </span>
  )
}
