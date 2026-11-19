import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useAuthStore } from '@/store/authStore'
import { apiErrorMessage } from '@/lib/apiError'
import {
  getFilerProfiles,
  putFilerProfile,
  type FilerProfileInput,
} from '@/lib/complianceApi'

export function FilerProfileSection() {
  const { user } = useAuthStore()
  const queryClient = useQueryClient()
  const canEdit = user?.role === 'ROLE_ACCOUNTANT'
  const [error, setError] = useState<string | null>(null)

  const { data: profiles } = useQuery({
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

  return (
    <section>
      <h2 className="text-lg font-semibold mb-2">Filer profile</h2>
      <p className="text-sm text-[var(--color-text-muted)] mb-3">
        GST filing frequency over time. Each row applies from its effective date.
      </p>
      {error && (
        <p role="alert" className="text-sm text-red-600 mb-2">
          {error}
        </p>
      )}
      {profiles && profiles.length > 0 && (
        <table className="w-full text-sm mb-4">
          <thead>
            <tr className="text-left border-b">
              <th className="py-2">Effective from</th>
              <th className="py-2">Frequency</th>
              <th className="py-2">IFF</th>
              <th className="py-2">E-invoice</th>
              <th className="py-2">HSN digits</th>
            </tr>
          </thead>
          <tbody>
            {profiles.map((p) => (
              <tr key={p.id} className="border-b">
                <td className="py-2">{p.effectiveFrom}</td>
                <td className="py-2">{p.frequency === 'QRMP' ? 'QRMP (quarterly)' : 'Monthly'}</td>
                <td className="py-2">{p.iffOptIn ? 'Yes' : 'No'}</td>
                <td className="py-2">{p.einvoiceEnabled ? 'Yes' : 'No'}</td>
                <td className="py-2">HSN: {p.hsnDigits} digits</td>
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
          className="border rounded px-2 py-1 text-sm"
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
          className="border rounded px-2 py-1 text-sm"
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
          className="border rounded px-2 py-1 text-sm"
        />
      </label>
      <button
        type="submit"
        disabled={!effectiveFrom}
        className="px-3 py-1.5 text-sm rounded bg-[var(--color-primary)] text-white disabled:opacity-50"
      >
        Add profile
      </button>
    </form>
  )
}
