import { useState } from 'react'
import { useNavigate } from '@tanstack/react-router'
import { CheckCircle } from 'lucide-react'
import { api } from '@/lib/api'

const GSTIN_REGEX = /^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z]{1}[1-9A-Z]{1}Z[0-9A-Z]{1}$/
const PAN_REGEX = /^[A-Z]{5}[0-9]{4}[A-Z]{1}$/

export function OrganizationSetupPage() {
  const navigate = useNavigate()

  const [form, setForm] = useState({
    name: '',
    gstin: '',
    pan: '',
    registeredAddress: '',
    financialYearStart: 4,
  })

  const [fieldErrors, setFieldErrors] = useState({ gstin: '', pan: '' })
  const [fieldValid, setFieldValid] = useState({ gstin: false, pan: false })
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  function handleChange(
    e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>
  ) {
    const { name, value } = e.target
    setForm((prev) => ({
      ...prev,
      [name]: name === 'financialYearStart' ? Number(value) : value,
    }))
  }

  function handleGstinBlur() {
    const val = form.gstin.trim()
    if (val === '') {
      setFieldErrors((prev) => ({ ...prev, gstin: '' }))
      setFieldValid((prev) => ({ ...prev, gstin: false }))
      return
    }
    if (GSTIN_REGEX.test(val)) {
      setFieldErrors((prev) => ({ ...prev, gstin: '' }))
      setFieldValid((prev) => ({ ...prev, gstin: true }))
    } else {
      setFieldErrors((prev) => ({
        ...prev,
        gstin: 'GSTIN must be 15 characters in the format: 22AAAAA0000A1Z5',
      }))
      setFieldValid((prev) => ({ ...prev, gstin: false }))
    }
  }

  function handlePanBlur() {
    const val = form.pan.trim()
    if (val === '') {
      setFieldErrors((prev) => ({ ...prev, pan: '' }))
      setFieldValid((prev) => ({ ...prev, pan: false }))
      return
    }
    if (PAN_REGEX.test(val)) {
      setFieldErrors((prev) => ({ ...prev, pan: '' }))
      setFieldValid((prev) => ({ ...prev, pan: true }))
    } else {
      setFieldErrors((prev) => ({
        ...prev,
        pan: 'PAN must be 10 characters in the format: ABCDE1234F',
      }))
      setFieldValid((prev) => ({ ...prev, pan: false }))
    }
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    setError('')
    setLoading(true)
    try {
      await api.post('/organizations', { ...form })
      window.alert('Organization created successfully')
      navigate({ to: '/dashboard' })
    } catch (err: unknown) {
      const msg =
        err instanceof Error && 'response' in err
          ? (err as { response?: { data?: { message?: string } } }).response?.data?.message
          : undefined
      setError(
        msg ?? 'Organization could not be created. Try again, or contact support if the problem continues.'
      )
    } finally {
      setLoading(false)
    }
  }

  const inputClass =
    'w-full h-11 px-3 rounded-[var(--radius-md)] border border-[var(--color-border)] bg-[var(--color-surface-raised)] text-[var(--color-text-primary)] text-sm placeholder:text-[var(--color-text-muted)] focus:outline-none focus:border-[var(--color-primary)] focus:ring-2 focus:ring-[var(--color-primary-subtle)] transition-colors'

  return (
    <div className="min-h-screen flex items-center justify-center bg-[var(--color-bg)] px-4 py-8">
      <div className="w-full max-w-lg">
        <div className="mb-8 text-center">
          <h1 className="text-2xl font-semibold text-[var(--color-text-primary)]">
            Create Organization
          </h1>
          <p className="mt-1 text-sm text-[var(--color-text-muted)]">
            Set up your organization details
          </p>
        </div>

        <div className="bg-[var(--color-surface)] rounded-[var(--radius-xl)] shadow-[var(--shadow-md)] p-8">
          <form onSubmit={handleSubmit} noValidate className="space-y-5">
            {/* Organization Name */}
            <div>
              <label
                htmlFor="org-name"
                className="block text-sm font-medium text-[var(--color-text-primary)] mb-1.5"
              >
                Organization Name <span className="text-[var(--color-danger)]">*</span>
              </label>
              <input
                id="org-name"
                name="name"
                type="text"
                required
                value={form.name}
                onChange={handleChange}
                className={inputClass}
                placeholder="Your organization name"
              />
            </div>

            {/* GSTIN */}
            <div>
              <label
                htmlFor="gstin"
                className="block text-sm font-medium text-[var(--color-text-primary)] mb-1.5"
              >
                GSTIN
              </label>
              <div className="relative">
                <input
                  id="gstin"
                  name="gstin"
                  type="text"
                  maxLength={15}
                  value={form.gstin}
                  onChange={handleChange}
                  onBlur={handleGstinBlur}
                  aria-describedby="gstin-hint"
                  className={`${inputClass} font-mono pr-8`}
                  placeholder="22AAAAA0000A1Z5"
                />
                {fieldValid.gstin && (
                  <CheckCircle
                    size={14}
                    className="absolute right-3 top-1/2 -translate-y-1/2 text-[var(--color-success)]"
                    aria-hidden="true"
                  />
                )}
              </div>
              {fieldErrors.gstin ? (
                <p
                  id="gstin-hint"
                  role="alert"
                  className="mt-1 text-xs text-[var(--color-danger)]"
                >
                  {fieldErrors.gstin}
                </p>
              ) : (
                <p id="gstin-hint" className="mt-1 text-xs text-[var(--color-text-muted)]">
                  15-character GST Identification Number
                </p>
              )}
            </div>

            {/* PAN */}
            <div>
              <label
                htmlFor="pan"
                className="block text-sm font-medium text-[var(--color-text-primary)] mb-1.5"
              >
                PAN
              </label>
              <div className="relative">
                <input
                  id="pan"
                  name="pan"
                  type="text"
                  maxLength={10}
                  value={form.pan}
                  onChange={handleChange}
                  onBlur={handlePanBlur}
                  aria-describedby="pan-hint"
                  className={`${inputClass} font-mono pr-8`}
                  placeholder="ABCDE1234F"
                />
                {fieldValid.pan && (
                  <CheckCircle
                    size={14}
                    className="absolute right-3 top-1/2 -translate-y-1/2 text-[var(--color-success)]"
                    aria-hidden="true"
                  />
                )}
              </div>
              {fieldErrors.pan ? (
                <p
                  id="pan-hint"
                  role="alert"
                  className="mt-1 text-xs text-[var(--color-danger)]"
                >
                  {fieldErrors.pan}
                </p>
              ) : (
                <p id="pan-hint" className="mt-1 text-xs text-[var(--color-text-muted)]">
                  10-character Permanent Account Number
                </p>
              )}
            </div>

            {/* Registered Address */}
            <div>
              <label
                htmlFor="registered-address"
                className="block text-sm font-medium text-[var(--color-text-primary)] mb-1.5"
              >
                Registered Address
              </label>
              <textarea
                id="registered-address"
                name="registeredAddress"
                rows={3}
                value={form.registeredAddress}
                onChange={handleChange}
                className="w-full px-3 py-2.5 rounded-[var(--radius-md)] border border-[var(--color-border)] bg-[var(--color-surface-raised)] text-[var(--color-text-primary)] text-sm placeholder:text-[var(--color-text-muted)] focus:outline-none focus:border-[var(--color-primary)] focus:ring-2 focus:ring-[var(--color-primary-subtle)] transition-colors resize-none"
                placeholder="Street, City, State, PIN"
              />
            </div>

            {/* Financial Year Start */}
            <div>
              <label
                htmlFor="fy-start"
                className="block text-sm font-medium text-[var(--color-text-primary)] mb-1.5"
              >
                Financial Year Start
              </label>
              <select
                id="fy-start"
                name="financialYearStart"
                value={form.financialYearStart}
                onChange={handleChange}
                className="w-full h-11 px-3 rounded-[var(--radius-md)] border border-[var(--color-border)] bg-[var(--color-surface-raised)] text-[var(--color-text-primary)] text-sm focus:outline-none focus:border-[var(--color-primary)] focus:ring-2 focus:ring-[var(--color-primary-subtle)] transition-colors"
              >
                <option value="4">April (Indian default)</option>
                <option value="1">January</option>
                <option value="7">July</option>
                <option value="10">October</option>
              </select>
            </div>

            {/* Error banner */}
            {error && (
              <p
                role="alert"
                className="text-sm text-[var(--color-danger)] bg-[var(--color-danger-bg)] rounded-[var(--radius-md)] px-3 py-2"
              >
                {error}
              </p>
            )}

            {/* Submit */}
            <button
              type="submit"
              disabled={loading || form.name.length === 0}
              className="w-full h-11 rounded-[var(--radius-md)] bg-[var(--color-primary)] text-white text-sm font-medium hover:bg-[var(--color-primary-hover)] transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
            >
              {loading ? 'Creating…' : 'Create Organization'}
            </button>
          </form>
        </div>
      </div>
    </div>
  )
}
