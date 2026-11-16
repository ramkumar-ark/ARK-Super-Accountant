import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useAuthStore } from '@/store/authStore'
import { apiErrorMessage } from '@/lib/apiError'
import { reconcileBaseline, type BaselinePeriodView } from '@/lib/filedReturnsApi'
import { WorkflowFindingsTab } from '@/components/workflow/WorkflowFindingsTab'

const HEADS: ('IGST' | 'CGST' | 'SGST' | 'CESS')[] = ['IGST', 'CGST', 'SGST', 'CESS']

function formatAmount(n: number): string {
  return n.toLocaleString('en-IN', { minimumFractionDigits: 2 })
}

export function BaselinePeriodDetail({
  row,
  onRerun,
}: {
  row: BaselinePeriodView
  onRerun: () => void
}) {
  const { user } = useAuthStore()
  const queryClient = useQueryClient()
  const canUpload = user?.role === 'ROLE_ACCOUNTANT' || user?.role === 'ROLE_OPERATOR'

  const mutation = useMutation({
    mutationFn: () => reconcileBaseline(row.workflowId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['baseline-periods'] })
      queryClient.invalidateQueries({ queryKey: ['workflow-findings', row.workflowId] })
    },
  })

  const outcome = mutation.data

  return (
    <div className="mt-2 rounded-[var(--radius-lg)] border border-[var(--color-border)] bg-[var(--color-surface)] p-4 space-y-4">
      <h3 className="text-base font-semibold">Baseline {row.periodKey} — differences</h3>

      <table className="w-full text-sm" data-testid="variance-strip">
        <thead>
          <tr className="text-left border-b">
            <th className="py-2">Head</th>
            <th className="py-2">Filed</th>
            <th className="py-2">Books</th>
            <th className="py-2">Diff</th>
          </tr>
        </thead>
        <tbody>
          {HEADS.map((head) => {
            const v = row.variance[head]
            return (
              <tr key={head} className="border-b">
                <td className="py-2">{head}</td>
                <td className="py-2">{formatAmount(v.filed)}</td>
                <td className="py-2">{formatAmount(v.books)}</td>
                <td className={`py-2 ${v.diff !== 0 ? 'text-red-600' : ''}`}>
                  {formatAmount(v.diff)}
                </td>
              </tr>
            )
          })}
        </tbody>
      </table>

      {row.status === 'NOT_IMPORTED' && (
        <p className="text-sm text-[var(--color-text-muted)]">No filed return imported yet</p>
      )}

      {row.status === 'MAPPING_INCOMPLETE' && (
        <p className="text-sm">
          <a href="/settings/compliance" className="underline">
            Map all tax ledgers first
          </a>
        </p>
      )}

      {row.status !== 'NOT_IMPORTED' && row.status !== 'MAPPING_INCOMPLETE' && (
        <>
          <p className="text-sm text-[var(--color-text-muted)]">
            Explain each difference with a note; an accountant approves it. These differences do not
            block anything.
          </p>
          <WorkflowFindingsTab workflowId={row.workflowId} role={user?.role as never} />
        </>
      )}

      {canUpload && (
        <div className="space-y-2">
          {mutation.isError && (
            <p role="alert" className="text-sm text-red-600">
              {apiErrorMessage(mutation.error, 'Could not re-run the comparison')}
            </p>
          )}
          {outcome?.status === 'MAPPING_INCOMPLETE' && (
            <p className="text-sm">
              <a href="/settings/compliance" className="underline">
                Map all tax ledgers first
              </a>
            </p>
          )}
          {outcome?.status === 'NO_BOOKS' && (
            <p className="text-sm">Upload a day book covering this period first</p>
          )}
          <button
            type="button"
            className="px-3 py-1.5 text-sm rounded border disabled:opacity-50"
            disabled={mutation.isPending}
            onClick={() => mutation.mutate()}
          >
            Re-run comparison
          </button>
        </div>
      )}
    </div>
  )
}
