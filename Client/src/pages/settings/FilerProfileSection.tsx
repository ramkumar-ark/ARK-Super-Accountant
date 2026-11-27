import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { AlertCircle, Loader2 } from 'lucide-react'
import { useAuthStore } from '@/store/authStore'
import { apiErrorMessage } from '@/lib/apiError'
import {
  getFilerProfiles,
  putFilerProfile,
  type FilerProfileInput,
} from '@/lib/complianceApi'
import {
  ALERT_ERROR_CLASS,
  INPUT_CLASS,
  LOADING_CLASS,
  PRIMARY_BUTTON_CLASS,
  SECTION_CARD_CLASS,
  STACKED_BODY_CLASS,
  STACKED_CELL_CLASS,
  STACKED_LABEL_CLASS,
  STACKED_ROW_CLASS,
  STACKED_TABLE_CLASS,
  STACKED_THEAD_CLASS,
  STACKED_VALUE_CLASS,
  TABLE_HEAD_CLASS,
} from './sectionStyles'

export function FilerProfileSection() {
  const { user } = useAuthStore()
  const queryClient = useQueryClient()
  const canEdit = user?.role === 'ROLE_ACCOUNTANT'
  const [error, setError] = useState<string | null>(null)

  const { data: profiles, isLoading } = useQuery({
    queryKey: ['filer-profiles'],
    queryFn: getFilerProfiles,
  })

  const mutation = useMutation({
    mutationFn: (body: FilerProfileInput) => putFilerProfile(body),
    onSuccess: () => {
      setError(null)
      queryClient.invalidateQueries({ queryKey: ['filer-profiles'] })
      queryClient.invalidateQueries({ queryKey: ['responsibility-starts'] })
    },
    onError: (err) => setError(apiErrorMessage(err, 'Could not save filer profile')),
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
      <h2 className="text-lg font-semibold mb-2">Filer profile</h2>
      <p className="text-sm text-[var(--color-text-muted)] mb-3">
        GST filing frequency over time. Each row applies from its effective date.
      </p>
      {error && (
        <div role="alert" className={`${ALERT_ERROR_CLASS} mb-3`}>
          <AlertCircle size={16} className="flex-shrink-0" />
          {error}
        </div>
      )}
      {profiles && profiles.length === 0 && (
        <p className="text-sm text-[var(--color-text-muted)] mb-4">
          No filer profiles yet — add the first one below.
        </p>
      )}
      {profiles && profiles.length > 0 && (
        <table className={`${STACKED_TABLE_CLASS} mb-4`}>
          <thead className={STACKED_THEAD_CLASS}>
            <tr className="text-left">
              <th className={TABLE_HEAD_CLASS}>Effective from</th>
              <th className={TABLE_HEAD_CLASS}>Frequency</th>
              <th className={TABLE_HEAD_CLASS}>IFF</th>
              <th className={TABLE_HEAD_CLASS}>E-invoice</th>
              <th className={TABLE_HEAD_CLASS}>HSN digits</th>
            </tr>
          </thead>
          <tbody className={STACKED_BODY_CLASS}>
            {profiles.map((p) => (
              <tr key={p.id} className={STACKED_ROW_CLASS}>
                <td className={STACKED_CELL_CLASS}>
                  <span className={STACKED_LABEL_CLASS}>Effective from</span>
                  <span className={STACKED_VALUE_CLASS}>{p.effectiveFrom}</span>
                </td>
                <td className={STACKED_CELL_CLASS}>
                  <span className={STACKED_LABEL_CLASS}>Frequency</span>
                  <span className={STACKED_VALUE_CLASS}>
                    {p.frequency === 'QRMP' ? 'QRMP (quarterly)' : 'Monthly'}
                  </span>
                </td>
                <td className={STACKED_CELL_CLASS}>
                  <span className={STACKED_LABEL_CLASS}>IFF</span>
                  <span className={STACKED_VALUE_CLASS}>{p.iffOptIn ? 'Yes' : 'No'}</span>
                </td>
                <td className={STACKED_CELL_CLASS}>
                  <span className={STACKED_LABEL_CLASS}>E-invoice</span>
                  <span className={STACKED_VALUE_CLASS}>{p.einvoiceEnabled ? 'Yes' : 'No'}</span>
                </td>
                <td className={STACKED_CELL_CLASS}>
                  <span className={STACKED_LABEL_CLASS}>HSN digits</span>
                  <span className={STACKED_VALUE_CLASS}>HSN: {p.hsnDigits} digits</span>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
      {canEdit && <AddProfileForm onSubmit={(b) => mutation.mutate(b)} />}
    </section>
  )
}

function AddProfileForm({ onSubmit }: { onSubmit: (b: FilerProfileInput) => void }) {
  const [frequency, setFrequency] = useState<'MONTHLY' | 'QRMP'>('MONTHLY')
  const [iffOptIn, setIffOptIn] = useState(false)
  const [einvoiceEnabled, setEinvoiceEnabled] = useState(false)
  const [hsnDigits, setHsnDigits] = useState(4)
  const [effectiveFrom, setEffectiveFrom] = useState('')
  return (
    <form
      className="flex flex-wrap items-end gap-3"
      aria-label="Add filer profile"
      onSubmit={(e) => {
        e.preventDefault()
        onSubmit({ frequency, iffOptIn, einvoiceEnabled, hsnDigits, effectiveFrom })
      }}
    >
      <label className="text-sm flex flex-col gap-1">
        Frequency
        <select
          aria-label="Frequency"
          value={frequency}
          onChange={(e) => setFrequency(e.target.value as 'MONTHLY' | 'QRMP')}
          className={INPUT_CLASS}
        >
          <option value="MONTHLY">Monthly</option>
          <option value="QRMP">QRMP (quarterly)</option>
        </select>
      </label>
      <label className="text-sm flex items-center gap-1">
        <input
          type="checkbox"
          checked={iffOptIn}
          onChange={(e) => setIffOptIn(e.target.checked)}
        />
        IFF opt-in
      </label>
      <label className="text-sm flex items-center gap-1">
        <input
          type="checkbox"
          checked={einvoiceEnabled}
          onChange={(e) => setEinvoiceEnabled(e.target.checked)}
        />
        E-invoice
      </label>
      <label className="text-sm flex flex-col gap-1">
        HSN digits
        <select
          aria-label="HSN digits"
          value={hsnDigits}
          onChange={(e) => setHsnDigits(Number(e.target.value))}
          className={INPUT_CLASS}
        >
          <option value={4}>4 digits</option>
          <option value={6}>6 digits</option>
        </select>
      </label>
      <label className="text-sm flex flex-col gap-1">
        Effective from
        <input
          type="date"
          aria-label="Effective from"
          value={effectiveFrom}
          onChange={(e) => setEffectiveFrom(e.target.value)}
          className={INPUT_CLASS}
        />
      </label>
      <button
        type="submit"
        disabled={!effectiveFrom}
        className={PRIMARY_BUTTON_CLASS}
      >
        Add profile
      </button>
    </form>
  )
}
