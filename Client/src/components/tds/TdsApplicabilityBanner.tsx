import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useAuthStore } from '@/store/authStore'
import {
  confirmTdsApplicabilityPrompt,
  dismissTdsApplicabilityPrompt,
  fyLabel,
  getTdsApplicabilityPrompts,
  type TdsApplicabilityPrompt,
} from '@/lib/tdsApplicabilityApi'

function booksExceedMessage(prompt: TdsApplicabilityPrompt): string {
  const estimate = prompt.estimate
  const rupees =
    estimate && estimate.amount != null
      ? `₹${estimate.amount.toLocaleString('en-IN')}`
      : 'an amount'
  const measuredFy = estimate?.measuredFinancialYear ?? prompt.financialYear - 1
  return (
    `Books show receipts of ${rupees} for ${fyLabel(measuredFy)}. ` +
    `If this exceeds ₹1 crore (business) or ₹50 lakh (profession), ` +
    `TDS has applied since 1 April ${measuredFy} and deductions for this year are due.`
  )
}

export function TdsApplicabilityBanner() {
  const { user } = useAuthStore()
  const orgId = user?.organizationId
  const queryClient = useQueryClient()
  const { data: prompts } = useQuery({
    queryKey: ['tds-applicability-prompts', orgId],
    queryFn: () => getTdsApplicabilityPrompts(orgId as string),
    enabled: !!orgId,
  })

  if (!prompts?.length) return null

  const invalidate = () => {
    void queryClient.invalidateQueries({ queryKey: ['tds-applicability-prompts', orgId] })
    void queryClient.invalidateQueries({ queryKey: ['tds-applicability', orgId] })
  }

  return (
    <div className="flex flex-col gap-3">
      {prompts.map((prompt) => (
        <PromptCard key={prompt.id} orgId={orgId as string} prompt={prompt} onDone={invalidate} />
      ))}
    </div>
  )
}

function PromptCard({
  orgId,
  prompt,
  onDone,
}: {
  orgId: string
  prompt: TdsApplicabilityPrompt
  onDone: () => void
}) {
  const [dismissing, setDismissing] = useState(false)
  const [reason, setReason] = useState('')
  const [error, setError] = useState<string | null>(null)

  const confirm = useMutation({
    mutationFn: () =>
      confirmTdsApplicabilityPrompt(orgId, prompt.id, {
        applicable: true,
        priorYearTurnover: prompt.estimate?.amount ?? undefined,
      }),
    onSuccess: onDone,
  })

  const dismiss = useMutation({
    mutationFn: (why: string) => dismissTdsApplicabilityPrompt(orgId, prompt.id, why),
    onSuccess: onDone,
  })

  const message =
    prompt.kind === 'BOOKS_EXCEED_LIMIT' ? booksExceedMessage(prompt) : prompt.message

  const submitDismiss = () => {
    if (!reason.trim()) {
      setError('Please give a reason')
      return
    }
    setError(null)
    dismiss.mutate(reason.trim())
  }

  return (
    <div
      data-testid="tds-applicability-banner"
      className="rounded border border-amber-400 bg-amber-50 p-4 text-sm text-[var(--color-text)]"
    >
      <p className="mb-2">{message}</p>
      {error && <p className="mb-2 text-red-600">{error}</p>}
      {dismissing ? (
        <div className="flex flex-col gap-2">
          <label htmlFor={`dismiss-reason-${prompt.id}`} className="sr-only">
            Reason
          </label>
          <textarea
            id={`dismiss-reason-${prompt.id}`}
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            placeholder="Why does TDS not apply?"
            className="rounded border p-2"
          />
          <div className="flex gap-2">
            <button
              type="button"
              onClick={submitDismiss}
              className="rounded bg-red-600 px-3 py-1.5 text-white"
            >
              Dismiss
            </button>
            <button
              type="button"
              onClick={() => {
                setDismissing(false)
                setError(null)
              }}
              className="rounded border px-3 py-1.5"
            >
              Cancel
            </button>
          </div>
        </div>
      ) : (
        <div className="flex gap-2">
          <button
            type="button"
            onClick={() => confirm.mutate()}
            className="rounded bg-emerald-600 px-3 py-1.5 text-white"
          >
            TDS applies
          </button>
          <button
            type="button"
            onClick={() => setDismissing(true)}
            className="rounded border px-3 py-1.5"
          >
            It does not (give reason)
          </button>
        </div>
      )}
    </div>
  )
}
