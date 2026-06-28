import { useState } from 'react'
import { useNavigate } from '@tanstack/react-router'
import { CheckCircle, Loader2 } from 'lucide-react'
import { api } from '@/lib/api'
import { useAuthStore } from '@/store/authStore'
import { Header } from '@/components/Header'

const GSTIN_REGEX = /^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z]{1}[1-9A-Z]{1}Z[0-9A-Z]{1}$/
const PAN_REGEX = /^[A-Z]{5}[0-9]{4}[A-Z]{1}$/

export function OrganizationSetupPage() {
  const navigate = useNavigate()
  const switchOrganization = useAuthStore((s) => s.switchOrganization)
  const setOrganizations = useAuthStore((s) => s.setOrganizations)

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
  const [step, setStep] = useState<1 | 2>(1)
  const [createdOrgId, setCreatedOrgId] = useState<string | null>(null)
  const [selectedTemplate, setSelectedTemplate] = useState<string>('standard')
  const [applyingTemplate, setApplyingTemplate] = useState(false)
  const [templateError, setTemplateError] = useState('')

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
      const response = await api.post('/organizations', { ...form })
      setCreatedOrgId(response.data.id)
      setStep(2)
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

  async function selectAndNavigate() {
    const res = await api.post(`/organizations/${createdOrgId}/select`)
    try {
      const { data: orgs } = await api.get<Array<{
        organizationId: string
        organizationName: string
        role: string
        isActive: boolean
      }>>('/organizations/me/list')
      setOrganizations(orgs)
    } catch {
      // non-blocking: org was created, switch proceeds regardless
    }
    switchOrganization(res.data.token, {
      organizationId: String(res.data.organizationId),
      organizationName: res.data.organizationName,
      role: res.data.role,
    })
    navigate({ to: '/dashboard' })
  }

  async function handleApplyTemplate() {
    setApplyingTemplate(true)
    setTemplateError('')
    try {
      await api.post('/v1/preconfigured-masters/onboard', { templateSlug: selectedTemplate })
      await selectAndNavigate()
    } catch {
      setTemplateError('Could not apply the template. You can skip for now and apply later.')
    } finally {
      setApplyingTemplate(false)
    }
  }

  async function handleSkip() {
    setApplyingTemplate(true)
    try {
      await selectAndNavigate()
    } catch {
      setTemplateError('Could not activate the organization. Please try again.')
      setApplyingTemplate(false)
    }
  }

  const TEMPLATES = [
    {
      slug: 'standard',
      name: 'Standard',
      description: 'Full ledger set for GST-registered businesses with sales, purchases, expenses, and TDS tracking.',
    },
    {
      slug: 'simplified',
      name: 'Simplified',
      description: 'Minimal ledger set for small businesses or sole proprietors without complex TDS requirements.',
    },
    {
      slug: 'manufacturing',
      name: 'Manufacturing',
      description: 'Extended ledger set for manufacturing entities with raw material, WIP, and finished goods accounts.',
    },
  ]

  if (step === 2 && createdOrgId) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-[var(--color-bg)] px-4 py-8">
        <div className="w-full max-w-lg">
          {/* Step indicator */}
          <div className="flex items-center gap-3 mb-8 justify-center">
            <div className="flex items-center gap-2">
              <CheckCircle size={20} className="text-[var(--color-success)]" />
              <span className="text-sm text-[var(--color-text-muted)]">Organization created</span>
            </div>
            <div className="h-px w-8 bg-[var(--color-border)]" />
            <div className="flex items-center gap-2">
              <span className="h-5 w-5 rounded-full bg-[var(--color-primary)] text-white text-xs flex items-center justify-center font-medium">2</span>
              <span className="text-sm font-medium text-[var(--color-text-primary)]">Select Template</span>
            </div>
          </div>

          <div className="mb-6 text-center">
            <h1 className="text-2xl font-semibold text-[var(--color-text-primary)]">Select a Master Template</h1>
            <p className="mt-1 text-sm text-[var(--color-text-muted)]">
              Pre-load your chart of accounts with a template. You can always add or remove ledgers later.
            </p>
          </div>

          <div className="flex flex-col gap-3 mb-6">
            {TEMPLATES.map((t) => (
              <button
                key={t.slug}
                type="button"
                onClick={() => setSelectedTemplate(t.slug)}
                className={`w-full text-left p-4 rounded-[var(--radius-lg)] border-2 transition-colors ${selectedTemplate === t.slug
                  ? 'border-[var(--color-primary)] bg-[var(--color-primary-light)]'
                  : 'border-[var(--color-border)] bg-[var(--color-surface)] hover:border-[var(--color-primary)]'
                  }`}
              >
                <div className="flex items-center justify-between">
                  <span className="text-sm font-semibold text-[var(--color-text-primary)]">{t.name}</span>
                  {selectedTemplate === t.slug && (
                    <CheckCircle size={16} className="text-[var(--color-primary)]" />
                  )}
                </div>
                <p className="text-xs text-[var(--color-text-muted)] mt-1">{t.description}</p>
              </button>
            ))}
          </div>

          {templateError && (
            <p role="alert" className="text-sm text-[var(--color-danger)] bg-[var(--color-danger-bg)] rounded-[var(--radius-md)] px-3 py-2 mb-4">
              {templateError}
            </p>
          )}

          <div className="flex flex-col gap-3">
            <button
              type="button"
              onClick={handleApplyTemplate}
              disabled={applyingTemplate}
              className="w-full h-11 rounded-[var(--radius-md)] bg-[var(--color-primary)] text-white text-sm font-medium hover:bg-[var(--color-primary-hover)] transition-colors disabled:opacity-50 disabled:cursor-not-allowed flex items-center justify-center gap-2"
            >
              {applyingTemplate ? (
                <><Loader2 size={16} className="animate-spin" /> Applying…</>
              ) : (
                'Apply Template'
              )}
            </button>
            <button
              type="button"
              onClick={handleSkip}
              disabled={applyingTemplate}
              className="w-full h-11 rounded-[var(--radius-md)] border border-[var(--color-border)] text-sm text-[var(--color-text-secondary)] hover:bg-[var(--color-surface-raised)] transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
            >
              Skip for now
            </button>
          </div>
        </div>
      </div>
    )
  }

  const inputClass =
    'w-full h-11 px-3 rounded-[var(--radius-md)] border border-[var(--color-border)] bg-[var(--color-surface-raised)] text-[var(--color-text-primary)] text-sm placeholder:text-[var(--color-text-muted)] focus:outline-none focus:border-[var(--color-primary)] focus:ring-2 focus:ring-[var(--color-primary-subtle)] transition-colors'

  return (
    <div className="flex-1 flex flex-col overflow-hidden">
      <Header />
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
    </div>
  )
}
