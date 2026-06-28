import { useState, useEffect, useRef } from 'react'
import { api } from '@/lib/api'
import { X, CheckCircle } from 'lucide-react'

const GSTIN_REGEX = /^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z]{1}[1-9A-Z]{1}Z[0-9A-Z]{1}$/
const HSN_SAC_REGEX = /^\d{4,8}$/

const TDS_SECTIONS = [
  { value: 'NOT_SUBJECT', label: 'Not Subject to TDS' },
  { value: '194C', label: '194C — Contractors & Sub-contractors' },
  { value: '194J_A', label: '194J(a) — Technical Services' },
  { value: '194J_B', label: '194J(b) — Professional Services' },
  { value: '194H', label: '194H — Commission & Brokerage' },
  { value: '194I', label: '194I — Rent' },
  { value: '194Q', label: '194Q — Purchase of Goods' },
  { value: '194A', label: '194A — Interest' },
  { value: '194B', label: '194B — Winnings' },
  { value: '194D', label: '194D — Insurance Commission' },
  { value: '194M', label: '194M — Payments by Individuals/HUF' },
  { value: 'OTHER', label: 'Other Section' },
]

const GST_OPTIONS = [
  { value: 'TAXABLE', label: 'Taxable' },
  { value: 'EXEMPT', label: 'Exempt' },
  { value: 'ZERO_RATED', label: 'Zero-rated' },
  { value: 'NON_GST', label: 'Non-GST' },
  { value: 'RCM', label: 'RCM (Reverse Charge)' },
  { value: 'NOT_APPLICABLE', label: 'Not Applicable' },
]

interface Master {
  id: string
  ledgerName: string
  tdsSection: string | null
  gstApplicabilityType: string | null
  hsnSacCode: string | null
  gstin: string | null
}

export function LedgerMappingPanel({
  master,
  onClose,
  onSaved,
}: {
  master: Master
  onClose: () => void
  onSaved: () => void
}) {
  const closeButtonRef = useRef<HTMLButtonElement>(null)
  const [form, setForm] = useState({
    tdsSection: master.tdsSection ?? '',
    gstApplicabilityType: master.gstApplicabilityType ?? '',
    hsnSacCode: master.hsnSacCode ?? '',
    gstin: master.gstin ?? '',
  })
  const [fieldErrors, setFieldErrors] = useState({ hsnSacCode: '', gstin: '' })
  const [fieldValid, setFieldValid] = useState({ gstin: false })
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [isDirty, setIsDirty] = useState(false)
  const [confirmDiscard, setConfirmDiscard] = useState(false)

  useEffect(() => {
    closeButtonRef.current?.focus()
  }, [])

  function handleChange(field: string, value: string) {
    setForm((prev) => ({ ...prev, [field]: value }))
    setIsDirty(true)
  }

  function handleHsnBlur() {
    const val = form.hsnSacCode.trim()
    if (val && !HSN_SAC_REGEX.test(val)) {
      setFieldErrors((prev) => ({ ...prev, hsnSacCode: 'HSN/SAC code must be 4–8 digits.' }))
    } else {
      setFieldErrors((prev) => ({ ...prev, hsnSacCode: '' }))
    }
  }

  function handleGstinBlur() {
    const val = form.gstin.trim().toUpperCase()
    if (val === '') {
      setFieldValid((prev) => ({ ...prev, gstin: false }))
      return
    }
    if (GSTIN_REGEX.test(val)) {
      setFieldErrors((prev) => ({ ...prev, gstin: '' }))
      setFieldValid((prev) => ({ ...prev, gstin: true }))
    } else {
      setFieldErrors((prev) => ({
        ...prev,
        gstin: 'GSTIN must be 15 characters in the format: 22AAAAA0000A1Z5.',
      }))
      setFieldValid((prev) => ({ ...prev, gstin: false }))
    }
  }

  function handleClose() {
    if (isDirty) {
      setConfirmDiscard(true)
    } else {
      onClose()
    }
  }

  async function handleSave() {
    if (fieldErrors.hsnSacCode || fieldErrors.gstin) return
    setSaving(true)
    setError('')
    try {
      await api.put(`/v1/preconfigured-masters/${master.id}`, {
        tdsSection: form.tdsSection || null,
        gstApplicabilityType: form.gstApplicabilityType || null,
        hsnSacCode: form.hsnSacCode || null,
        gstin: form.gstin || null,
      })
      onSaved()
    } catch (err: unknown) {
      const msg =
        err instanceof Error && 'response' in err
          ? (err as { response?: { data?: { message?: string } } }).response?.data?.message
          : undefined
      setError(msg ?? 'Could not save the mapping. Check your values and try again.')
    } finally {
      setSaving(false)
    }
  }

  const hasErrors = !!(fieldErrors.hsnSacCode || fieldErrors.gstin)
  const inputClass =
    'w-full h-11 px-3 rounded-[var(--radius-md)] border border-[var(--color-border)] bg-[var(--color-surface-raised)] text-sm text-[var(--color-text-primary)] focus:outline-none focus:border-[var(--color-primary)] focus:ring-2 focus:ring-[var(--color-primary-subtle)] transition-colors'

  return (
    <>
      {/* Backdrop */}
      <div
        className="fixed inset-0 bg-black/30 z-[var(--z-modal-backdrop)]"
        onClick={handleClose}
      />
      {/* Panel */}
      <div
        role="dialog"
        aria-modal="true"
        aria-label={`Edit mapping for ${master.ledgerName}`}
        className="fixed inset-y-0 right-0 w-96 bg-[var(--color-surface)] border-l border-[var(--color-border)] shadow-[var(--shadow-lg)] flex flex-col z-[var(--z-modal)]"
      >
        {/* Header */}
        <div className="h-16 flex items-center justify-between px-6 border-b border-[var(--color-border)] flex-shrink-0">
          <h2 className="text-lg font-semibold text-[var(--color-text-primary)] truncate">
            {master.ledgerName}
          </h2>
          <button
            ref={closeButtonRef}
            onClick={handleClose}
            aria-label="Close mapping panel"
            className="h-9 w-9 flex items-center justify-center rounded-[var(--radius-md)] text-[var(--color-text-secondary)] hover:bg-[var(--color-surface-raised)]"
          >
            <X size={18} />
          </button>
        </div>
        {/* Confirm discard banner */}
        {confirmDiscard && (
          <div className="px-6 py-3 bg-[var(--color-warning-bg)] border-b border-[var(--color-border)] text-sm">
            You have unsaved changes. Close anyway?{' '}
            <button onClick={onClose} className="text-[var(--color-danger)] font-medium ml-1">
              Yes, discard
            </button>
            <button
              onClick={() => setConfirmDiscard(false)}
              className="text-[var(--color-primary)] font-medium ml-3"
            >
              Keep editing
            </button>
          </div>
        )}
        {/* Content */}
        <div className="flex-1 overflow-y-auto p-6 flex flex-col gap-5">
          {/* TDS Section */}
          <div>
            <label className="block text-sm font-semibold text-[var(--color-text-primary)] mb-1">
              TDS Section
            </label>
            <select
              value={form.tdsSection}
              onChange={(e) => handleChange('tdsSection', e.target.value)}
              className={inputClass}
            >
              <option value="" disabled>
                Select TDS section…
              </option>
              {TDS_SECTIONS.map((s) => (
                <option key={s.value} value={s.value}>
                  {s.label}
                </option>
              ))}
            </select>
            <p className="text-xs text-[var(--color-text-muted)] mt-1">
              Assign the applicable TDS deduction section, or select 'Not Subject to TDS'.
            </p>
          </div>
          {/* GST Applicability */}
          <div>
            <label className="block text-sm font-semibold text-[var(--color-text-primary)] mb-1">
              GST Applicability
            </label>
            <select
              value={form.gstApplicabilityType}
              onChange={(e) => handleChange('gstApplicabilityType', e.target.value)}
              className={inputClass}
            >
              <option value="" disabled>
                Select GST applicability…
              </option>
              {GST_OPTIONS.map((o) => (
                <option key={o.value} value={o.value}>
                  {o.label}
                </option>
              ))}
            </select>
            <p className="text-xs text-[var(--color-text-muted)] mt-1">
              Required for all income and sales ledgers.
            </p>
          </div>
          {/* HSN / SAC Code */}
          <div>
            <label className="block text-sm font-semibold text-[var(--color-text-primary)] mb-1">
              HSN / SAC Code
            </label>
            <input
              type="text"
              value={form.hsnSacCode}
              onChange={(e) => handleChange('hsnSacCode', e.target.value)}
              onBlur={handleHsnBlur}
              maxLength={8}
              placeholder="e.g. 998311"
              className={`${inputClass} font-mono`}
            />
            {fieldErrors.hsnSacCode && (
              <p className="text-xs text-[var(--color-danger)] mt-1">{fieldErrors.hsnSacCode}</p>
            )}
            <p className="text-xs text-[var(--color-text-muted)] mt-1">
              Required for taxable sales/income ledgers. Leave blank if not applicable.
            </p>
          </div>
          {/* Vendor GSTIN */}
          <div>
            <label className="block text-sm font-semibold text-[var(--color-text-primary)] mb-1">
              Vendor GSTIN
            </label>
            <div className="relative">
              <input
                type="text"
                value={form.gstin}
                onChange={(e) => handleChange('gstin', e.target.value.toUpperCase())}
                onBlur={handleGstinBlur}
                maxLength={15}
                placeholder="22AAAAA0000A1Z5"
                className={`${inputClass} font-mono uppercase pr-8`}
              />
              {fieldValid.gstin && (
                <CheckCircle
                  size={14}
                  className="absolute right-3 top-1/2 -translate-y-1/2 text-[var(--color-success)]"
                />
              )}
            </div>
            {fieldErrors.gstin && (
              <p className="text-xs text-[var(--color-danger)] mt-1">{fieldErrors.gstin}</p>
            )}
            <p className="text-xs text-[var(--color-text-muted)] mt-1">
              Required for purchase ledgers from registered dealers.
            </p>
          </div>
        </div>
        {/* Footer */}
        <div className="sticky bottom-0 bg-[var(--color-surface)] border-t border-[var(--color-border)] p-6 flex items-center justify-between flex-shrink-0">
          {error && (
            <p className="text-xs text-[var(--color-danger)] mb-2 absolute bottom-20 left-6 right-6 bg-[var(--color-danger-bg)] px-3 py-2 rounded-[var(--radius-md)]">
              {error}
            </p>
          )}
          <button
            onClick={handleClose}
            className="h-11 px-4 rounded-[var(--radius-md)] border border-[var(--color-border)] text-sm text-[var(--color-text-secondary)] hover:bg-[var(--color-surface-raised)]"
          >
            Cancel Changes
          </button>
          <button
            onClick={handleSave}
            disabled={saving || hasErrors || !isDirty}
            className="h-11 px-4 bg-[var(--color-primary)] text-white rounded-[var(--radius-md)] text-sm hover:bg-[var(--color-primary-hover)] disabled:opacity-50"
          >
            {saving ? 'Saving…' : 'Save Mapping'}
          </button>
        </div>
      </div>
    </>
  )
}
