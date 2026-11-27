import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { AlertCircle, Loader2 } from 'lucide-react'
import { useAuthStore } from '@/store/authStore'
import { apiErrorMessage } from '@/lib/apiError'
import {
  getFinancialYears,
  closeFinancialYear,
  type CloseFinancialYearInput,
  type FinancialYearStatus,
  type CloseFinancialYearResult,
} from '@/lib/complianceApi'
import {
  ALERT_ERROR_CLASS,
  INPUT_CLASS,
  LOADING_CLASS,
  NESTED_CARD_CLASS,
  PRIMARY_BUTTON_CLASS,
  SECTION_CARD_CLASS,
} from './sectionStyles'

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

  if (isLoading) {
    return (
      <div className={LOADING_CLASS}>
        <Loader2 size={16} className="animate-spin" />
        Loading…
      </div>
    )
  }

  return (
    <section className={SECTION_CARD_CLASS}>
      <h2 className="text-lg font-semibold mb-3">Financial years</h2>
      {mutation.isError && (
        <div role="alert" className={`${ALERT_ERROR_CLASS} mb-3`}>
          <AlertCircle size={16} className="flex-shrink-0" />
          {apiErrorMessage(mutation.error, 'Could not close financial year')}
        </div>
      )}
      {(years ?? []).length === 0 && (
        <p className="text-sm text-[var(--color-text-muted)]">No financial years yet.</p>
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
      <div className={`${NESTED_CARD_CLASS} px-4 py-3 text-sm text-[var(--color-text-secondary)]`}>
        <span className="font-medium text-[var(--color-text-primary)]">FY {year.financialYear}</span> — closed (
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
    <div className={`${NESTED_CARD_CLASS} p-4 text-sm space-y-3`}>
      <div className="font-medium">FY {year.financialYear}</div>
      {year.openItems.length > 0 && (
        <ul className="text-[var(--color-text-muted)] list-disc list-inside">
          {year.openItems.map((o) => (
            <li key={o}>{o}</li>
          ))}
        </ul>
      )}
      {year.blockers.length > 0 && (
        <ul className="text-[var(--color-danger)] list-disc list-inside">
          {year.blockers.map((b) => (
            <li key={b}>{b}</li>
          ))}
        </ul>
      )}
      {result && result.warnings.length > 0 && (
        <ul className="text-[var(--color-warning)] list-disc list-inside" aria-label="Warnings">
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
              className={INPUT_CLASS}
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
                  className={INPUT_CLASS}
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
                  className={INPUT_CLASS}
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
                className={INPUT_CLASS}
                required
              />
            </label>
          )}
          <button
            type="submit"
            disabled={submitDisabled}
            className={PRIMARY_BUTTON_CLASS}
          >
            Close year
          </button>
        </form>
      )}
    </div>
  )
}
