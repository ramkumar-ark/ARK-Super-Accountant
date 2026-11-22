import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { z } from 'zod'
import { useAuthStore } from '@/store/authStore'
import { apiErrorMessage } from '@/lib/apiError'
import { getOrganization, updateDeductor, type DeductorDetails } from '@/lib/tdsApi'

const deductorSchema = z.object({
  tan: z
    .string()
    .regex(
      /^[A-Z]{4}[0-9]{5}[A-Z]$/,
      'Invalid TAN format. Expected [A-Z]{4}[0-9]{5}[A-Z].',
    ),
  deductorType: z.string(),
  responsiblePersonName: z.string(),
  responsiblePersonDesignation: z.string(),
  interestPayerType: z.string(),
})

const INTEREST_PAYER_OPTIONS = [
  { value: 'OTHER', label: 'Other payer (Rs 10,000 threshold)' },
  { value: 'BANK', label: 'Bank (Rs 50,000 threshold)' },
  { value: 'CO_OPERATIVE_BANK', label: 'Co-operative bank (Rs 50,000 threshold)' },
  { value: 'POST_OFFICE', label: 'Post office (Rs 50,000 threshold)' },
]

export function DeductorSection() {
  const { user } = useAuthStore()
  const orgId = user?.organizationId
  const canEdit = user?.role === 'ROLE_OWNER' || user?.role === 'ROLE_ACCOUNTANT'

  const { data: org } = useQuery({
    queryKey: ['organization', orgId],
    queryFn: () => getOrganization(orgId as string),
    enabled: !!orgId,
  })

  if (!org) return <section />

  return <DeductorForm orgId={orgId as string} org={org} canEdit={canEdit} />
}

function DeductorForm({
  orgId,
  org,
  canEdit,
}: {
  orgId: string
  org: DeductorDetails & Record<string, unknown>
  canEdit: boolean
}) {
  const queryClient = useQueryClient()
  const [tan, setTan] = useState(org.tan ?? '')
  const [deductorType, setDeductorType] = useState(org.deductorType ?? '')
  const [responsiblePersonName, setResponsiblePersonName] = useState(
    org.responsiblePersonName ?? '',
  )
  const [responsiblePersonDesignation, setResponsiblePersonDesignation] = useState(
    org.responsiblePersonDesignation ?? '',
  )
  const [interestPayerType, setInterestPayerType] = useState(org.interestPayerType ?? 'OTHER')
  const [error, setError] = useState<string | null>(null)

  const mutation = useMutation({
    mutationFn: (body: DeductorDetails) => updateDeductor(orgId, body),
    onSuccess: () => {
      setError(null)
      queryClient.invalidateQueries({ queryKey: ['organization', orgId] })
    },
    onError: (err) => setError(apiErrorMessage(err, 'Could not save deductor details')),
  })

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    const parsed = deductorSchema.safeParse({
      tan,
      deductorType,
      responsiblePersonName,
      responsiblePersonDesignation,
      interestPayerType,
    })
    if (!parsed.success) {
      setError(parsed.error.issues[0].message)
      return
    }
    mutation.mutate(parsed.data)
  }

  return (
    <section>
      <h2 className="text-lg font-semibold mb-2">Deductor details</h2>
      <p className="text-sm text-[var(--color-text-muted)] mb-3">
        TAN and responsible person as reported in TDS returns (26Q).
      </p>
      {error && (
        <p role="alert" className="text-sm text-red-600 mb-2">
          {error}
        </p>
      )}
      <form
        aria-label="Deductor details"
        className="flex flex-col gap-3 max-w-md"
        onSubmit={handleSubmit}
      >
        <label className="text-sm flex flex-col gap-1">
          TAN
          <input
            aria-label="TAN"
            value={tan}
            onChange={(e) => setTan(e.target.value.toUpperCase())}
            disabled={!canEdit}
            className="border rounded px-2 py-1 text-sm"
          />
        </label>
        <label className="text-sm flex flex-col gap-1">
          Deductor type
          <select
            aria-label="Deductor type"
            value={deductorType}
            onChange={(e) => setDeductorType(e.target.value)}
            disabled={!canEdit}
            className="border rounded px-2 py-1 text-sm"
          >
            <option value="">Select</option>
            <option value="K">Company</option>
            <option value="P">Individual / HUF</option>
          </select>
        </label>
        <label className="text-sm flex flex-col gap-1">
          Interest payer type
          <select
            aria-label="Interest payer type"
            value={interestPayerType}
            onChange={(e) => setInterestPayerType(e.target.value)}
            disabled={!canEdit}
            className="border rounded px-2 py-1 text-sm"
          >
            {INTEREST_PAYER_OPTIONS.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
          <span className="text-xs text-[var(--color-text-muted)]">
            Sets the section 194A threshold and Form 140 code. The Rs 1,00,000 senior-citizen
            threshold needs each customer&apos;s date of birth, which is not captured yet.
          </span>
        </label>
        <label className="text-sm flex flex-col gap-1">
          Responsible person name
          <input
            aria-label="Responsible person name"
            value={responsiblePersonName}
            onChange={(e) => setResponsiblePersonName(e.target.value)}
            disabled={!canEdit}
            className="border rounded px-2 py-1 text-sm"
          />
        </label>
        <label className="text-sm flex flex-col gap-1">
          Responsible person designation
          <input
            aria-label="Responsible person designation"
            value={responsiblePersonDesignation}
            onChange={(e) => setResponsiblePersonDesignation(e.target.value)}
            disabled={!canEdit}
            className="border rounded px-2 py-1 text-sm"
          />
        </label>
        {canEdit && (
          <button
            type="submit"
            disabled={mutation.isPending}
            className="px-3 py-1.5 text-sm rounded bg-[var(--color-primary)] text-white disabled:opacity-50 w-fit"
          >
            Save
          </button>
        )}
      </form>
    </section>
  )
}
