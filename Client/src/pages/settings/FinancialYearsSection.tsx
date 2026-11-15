import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useAuthStore } from '@/store/authStore'
import { apiErrorMessage } from '@/lib/apiError'
import {
  getFinancialYears,
  closeFinancialYear,
  type CloseFinancialYearInput,
  type FinancialYearStatus,
  type CloseFinancialYearResult,
} from '@/lib/complianceApi'

export function FinancialYearsSection() {
  const { user } = useAuthStore()
  const queryClient = useQueryClient()
  const canEdit = user?.role === 'ROLE_ACCOUNTANT'
  const { data: years, isLoading } = useQuery({
    queryKey: ['financial-years'],
    queryFn: getFinancialYears,
  })

  const mutation = useMutation({
    mutationFn: ({ fy, body }: { fy: number; body: CloseFinancialYearInput }) =>
      closeFinancialYear(fy, body),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['financial-years'] }),
  })

  if (isLoading) return <p className="text-sm text-[var(--color-text-muted)]">Loading…</p>

  return (
    <section>
      <h2 className="text-lg font-semibold mb-2">Financial years</h2>
      {mutation.isError && (
        <p role="alert" className="text-sm text-red-600 mb-2">
          {apiErrorMessage(mutation.error, 'Could not close financial year')}
        </p>
      )}
      <div className="space-y-4">
        {(years ?? []).map((y) => (
          <YearCard
            key={y.financialYear}
            year={y}
            canEdit={canEdit}
            onSubmit={(body) => mutation.mutate({ fy: y.financialYear, body })}
            pending={mutation.isPending && mutation.variables?.fy === y.financialYear}
            result={
              mutation.variables?.fy === y.financialYear ? mutation.data ?? null : null
            }
          />
        ))}
      </div>
    </section>
  )
}

function YearCard({
  year,
  canEdit,
  onSubmit,
  pending,
  result,
}: {
  year: FinancialYearStatus
  canEdit: boolean
  onSubmit: (body: CloseFinancialYearInput) => void
  pending: boolean
  result: CloseFinancialYearResult | null
}) {
  const [closeType, setCloseType] = useState<'ITR_FILED' | 'HANDLED_ELSEWHERE'>(
    'HANDLED_ELSEWHERE',
  )
  const [itrAckNo, setItrAckNo] = useState('')
  const [itrAckDate, setItrAckDate] = useState('')
  const [note, setNote] = useState('')

  if (year.closed) {
    return (
      <div className="border rounded p-3 text-sm">
        <span className="font-medium">FY {year.financialYear}</span> — closed (
        {year.closeType === 'ITR_FILED' ? 'ITR filed' : 'Handled elsewhere'})
      </div>
    )
  }

  const itrDisabled = !year.closable || year.openItems.length > 0 || year.blockers.length > 0
  const submitDisabled =
    !canEdit ||
    pending ||
    (closeType === 'ITR_FILED' ? !itrAckNo.trim() || !itrAckDate : !note.trim())

  return (
    <div className="border rounded p-3 text-sm space-y-2">
      <div className="font-medium">FY {year.financialYear}</div>
      {year.openItems.length > 0 && (
        <ul className="text-[var(--color-text-muted)] list-disc list-inside">
          {year.openItems.map((o) => (
            <li key={o}>{o}</li>
          ))}
        </ul>
      )}
      {year.blockers.length > 0 && (
        <ul className="text-red-600 list-disc list-inside">
          {year.blockers.map((b) => (
            <li key={b}>{b}</li>
          ))}
        </ul>
      )}
      {result && result.warnings.length > 0 && (
        <ul className="text-amber-700 list-disc list-inside" aria-label="Warnings">
          {result.warnings.map((w) => (
            <li key={w}>{w}</li>
          ))}
        </ul>
      )}
      {canEdit && (
        <form
          aria-label={`Close FY ${year.financialYear}`}
          className="flex flex-wrap items-end gap-3"
          onSubmit={(e) => {
            e.preventDefault()
            if (closeType === 'ITR_FILED') {
              onSubmit({ closeType, itrAckNo, itrAckDate })
            } else {
              onSubmit({ closeType, note })
            }
          }}
        >
          <label className="flex flex-col gap-1">
            Close type
            <select
              aria-label={`Close type FY ${year.financialYear}`}
              value={closeType}
              onChange={(e) => setCloseType(e.target.value as 'ITR_FILED' | 'HANDLED_ELSEWHERE')}
              className="border rounded px-2 py-1 text-sm"
            >
              <option value="HANDLED_ELSEWHERE">Handled elsewhere</option>
              <option value="ITR_FILED" disabled={itrDisabled}>
                ITR filed
              </option>
            </select>
          </label>
          {itrDisabled && (
            <p className="text-xs text-[var(--color-text-muted)] self-center">
              Every period must be filed
            </p>
          )}
          {closeType === 'ITR_FILED' ? (
            <>
              <label className="flex flex-col gap-1">
                Acknowledgement no.
                <input
                  aria-label="Acknowledgement no."
                  value={itrAckNo}
                  onChange={(e) => setItrAckNo(e.target.value)}
                  className="border rounded px-2 py-1 text-sm"
                  required
                />
              </label>
              <label className="flex flex-col gap-1">
                Acknowledgement date
                <input
                  type="date"
                  aria-label="Acknowledgement date"
                  value={itrAckDate}
                  onChange={(e) => setItrAckDate(e.target.value)}
                  className="border rounded px-2 py-1 text-sm"
                  required
                />
              </label>
            </>
          ) : (
            <label className="flex flex-col gap-1">
              Note
              <input
                aria-label="Note"
                value={note}
                onChange={(e) => setNote(e.target.value)}
                className="border rounded px-2 py-1 text-sm"
                required
              />
            </label>
          )}
          <button
            type="submit"
            disabled={submitDisabled}
            className="px-3 py-1.5 text-sm rounded bg-[var(--color-primary)] text-white disabled:opacity-50"
          >
            Close year
          </button>
        </form>
      )}
    </div>
  )
}
