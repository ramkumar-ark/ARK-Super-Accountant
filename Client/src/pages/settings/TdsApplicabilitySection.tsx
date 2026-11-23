import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useAuthStore } from '@/store/authStore'
import { useTdsApplicability } from '@/hooks/useTdsApplicability'
import {
  fyLabel,
  updateTdsApplicability,
  type TdsApplicabilityYear,
  type TurnoverNature,
} from '@/lib/tdsApplicabilityApi'
import { apiErrorMessage } from '@/lib/apiError'

const BASIS_LABELS: Record<TdsApplicabilityYear['basis'], { on: string; off: string }> = {
  ENTITY_TYPE: { on: 'TDS applies (entity type)', off: 'Not a deductor (entity type)' },
  DECLARED: { on: 'TDS applies (declared)', off: 'Not a deductor (declared)' },
  CONFIRMED_FROM_BOOKS: {
    on: 'TDS applies (confirmed from books)',
    off: 'Not a deductor (confirmed from books)',
  },
  LEGACY_DEFAULT: { on: 'TDS applies (existing organization)', off: 'Not a deductor' },
  CARRIED_FORWARD: { on: 'TDS applies (carried forward)', off: 'Not a deductor (carried forward)' },
}

const ENTITY_CODE_LABELS: Record<string, string> = {
  K: 'Company',
  M: 'Branch / Division of Company',
  P: 'Association of Person (AOP)',
  T: 'Association of Person (Trust)',
  J: 'Artificial Juridical Person',
  B: 'Body of Individuals',
  Q: 'Individual/HUF',
  F: 'Firm',
}

export function TdsApplicabilitySection() {
  const { user } = useAuthStore()
  const { data } = useTdsApplicability(user?.organizationId)
  const canEdit = user?.role === 'ROLE_OWNER' || user?.role === 'ROLE_ACCOUNTANT'
  const [editingFy, setEditingFy] = useState<number | null>(null)

  if (!data?.years) return <section />

  return (
    <section>
      <h2 className="text-lg font-semibold mb-2">TDS applicability</h2>
      <p className="text-sm text-[var(--color-text-muted)] mb-3">
        {data.entityCode
          ? `Deductor type: ${ENTITY_CODE_LABELS[data.entityCode] ?? data.entityCode} (${data.entityCodeSource === 'PAN' ? 'from PAN' : 'declared'})`
          : 'Deductor type: not known yet'}
        {data.tan ? ` — TAN ${data.tan}` : ' — no TAN saved'}
        . TDS features are hidden for the years where TDS does not apply; existing data is kept.
      </p>
      <ul className="flex flex-col gap-1 text-sm">
        {data.years.map((year) => (
          <YearRow
            key={year.financialYear}
            year={year}
            canEdit={canEdit ?? false}
            editing={editingFy === year.financialYear}
            onEdit={() => setEditingFy(year.financialYear)}
            onDone={() => setEditingFy(null)}
          />
        ))}
      </ul>
    </section>
  )
}

function turnoverEstimateText(
  estimate: TdsApplicabilityYear['turnoverEstimate'],
): string | null {
  if (!estimate || estimate.source === 'NONE' || estimate.amount == null) return null
  const measuredLabel = fyLabel(estimate.measuredFinancialYear)
  const rupees = `₹${estimate.amount.toLocaleString('en-IN')}`
  if (estimate.source === 'BOOKS') {
    return estimate.complete
      ? `Books show ${rupees} for ${measuredLabel}`
      : `Estimate from part of ${measuredLabel}: ${rupees}`
  }
  return estimate.complete
    ? `GST returns show ${rupees} for ${measuredLabel} (GST returns of this GSTIN only)`
    : `Estimate from GST returns, part of ${measuredLabel}: ${rupees} (GST returns of this GSTIN only)`
}

function YearRow({
  year,
  canEdit,
  editing,
  onEdit,
  onDone,
}: {
  year: TdsApplicabilityYear
  canEdit: boolean
  editing: boolean
  onEdit: () => void
  onDone: () => void
}) {
  const { user } = useAuthStore()
  const labels = BASIS_LABELS[year.basis]

  return (
    <li>
      <div className="flex items-center gap-2">
        <span>
          {fyLabel(year.financialYear)} — {year.applicable ? labels.on : labels.off}
        </span>
        {year.needsAnswer && (
          <span className="text-xs px-1.5 py-0.5 rounded bg-amber-100 text-amber-800">
            Needs answer
          </span>
        )}
        {canEdit && !editing && (
          <button
            type="button"
            className="text-xs px-2 py-0.5 rounded border border-[var(--color-border)]"
            onClick={onEdit}
          >
            {year.needsAnswer ? 'Answer' : 'Change'}
          </button>
        )}
      </div>
      {turnoverEstimateText(year.turnoverEstimate) && (
        <p className="ml-4 text-xs text-[var(--color-text-muted)]">
          {turnoverEstimateText(year.turnoverEstimate)}
        </p>
      )}
      {canEdit && editing && <AnswerForm year={year} orgId={user?.organizationId} onDone={onDone} />}
    </li>
  )
}

function AnswerForm({
  year,
  orgId,
  onDone,
}: {
  year: TdsApplicabilityYear
  orgId: string | undefined
  onDone: () => void
}) {
  const queryClient = useQueryClient()
  const [applicable, setApplicable] = useState<boolean>(year.applicable)
  const [turnover, setTurnover] = useState(
    year.priorYearTurnover != null ? String(year.priorYearTurnover) : '',
  )
  const [nature, setNature] = useState<TurnoverNature>(year.turnoverNature ?? 'BUSINESS')
  const [error, setError] = useState<string | null>(null)

  const mutation = useMutation({
    mutationFn: () =>
      updateTdsApplicability(orgId as string, year.financialYear, {
        applicable,
        ...(applicable && turnover !== '' ? { priorYearTurnover: Number(turnover) } : {}),
        ...(applicable ? { turnoverNature: nature } : {}),
      }),
    onSuccess: () => {
      setError(null)
      queryClient.invalidateQueries({ queryKey: ['tds-applicability', orgId] })
      onDone()
    },
    onError: (err) => setError(apiErrorMessage(err, 'Could not save the answer')),
  })

  return (
    <form
      aria-label={`TDS applicability ${fyLabel(year.financialYear)}`}
      className="mt-1 ml-4 flex flex-col gap-2 max-w-sm"
      onSubmit={(e) => {
        e.preventDefault()
        mutation.mutate()
      }}
    >
      <div className="flex gap-4">
        <label className="flex items-center gap-1.5">
          <input
            type="radio"
            name={`tds-applicable-${year.financialYear}`}
            checked={applicable}
            onChange={() => setApplicable(true)}
          />
          Yes
        </label>
        <label className="flex items-center gap-1.5">
          <input
            type="radio"
            name={`tds-applicable-${year.financialYear}`}
            checked={!applicable}
            onChange={() => setApplicable(false)}
          />
          No
        </label>
      </div>
      {applicable && (
        <div className="grid grid-cols-2 gap-2">
          <label className="flex flex-col gap-1">
            Last year&rsquo;s turnover (optional)
            <input
              type="number"
              min={0}
              value={turnover}
              onChange={(e) => setTurnover(e.target.value)}
              className="border rounded px-2 py-1 text-sm"
              placeholder="₹"
            />
          </label>
          <label className="flex flex-col gap-1">
            Nature
            <select
              value={nature}
              onChange={(e) => setNature(e.target.value as TurnoverNature)}
              className="border rounded px-2 py-1 text-sm"
            >
              <option value="BUSINESS">Business</option>
              <option value="PROFESSION">Profession</option>
            </select>
          </label>
        </div>
      )}
      {error && (
        <p role="alert" className="text-sm text-red-600">
          {error}
        </p>
      )}
      <button
        type="submit"
        disabled={mutation.isPending}
        className="px-3 py-1.5 text-sm rounded bg-[var(--color-primary)] text-white disabled:opacity-50 w-fit"
      >
        Save answer
      </button>
    </form>
  )
}
