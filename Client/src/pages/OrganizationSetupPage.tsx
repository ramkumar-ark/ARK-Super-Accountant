import { useState } from 'react'
import { useNavigate } from '@tanstack/react-router'
import { CheckCircle, Loader2 } from 'lucide-react'
import { api } from '@/lib/api'
import { useAuthStore } from '@/store/authStore'
import { Header } from '@/components/Header'

const GSTIN_REGEX = /^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z]{1}[1-9A-Z]{1}Z[0-9A-Z]{1}$/
const PAN_REGEX = /^[A-Z]{5}[0-9]{4}[A-Z]{1}$/
const TAN_REGEX = /^[A-Z]{4}[0-9]{5}[A-Z]$/
const TAN_ERROR = 'TAN must be 10 characters in the format: ABCD12345E'

// Non-government deductor categories per e-TDS file format Annexure 4 (Form 26Q v5.8),
// matching the entity-type select in settings (DeductorSection.tsx).
const ENTITY_TYPES = [
  { code: 'K', label: 'Company' },
  { code: 'M', label: 'Branch / Division of Company' },
  { code: 'P', label: 'Association of Person (AOP)' },
  { code: 'T', label: 'Association of Person (Trust)' },
  { code: 'J', label: 'Artificial Juridical Person' },
  { code: 'B', label: 'Body of Individuals' },
  { code: 'Q', label: 'Individual/HUF' },
  { code: 'F', label: 'Firm' },
]

// PAN 4th character → deductor class, per the income-tax PAN structure
// (mirrors EntityClass on the backend). Individual/HUF is code Q.
const PAN_CLASS_TO_CODE: Record<string, string> = {
  C: 'K',
  F: 'F',
  A: 'P',
  T: 'T',
  B: 'B',
  J: 'J',
  P: 'Q',
  H: 'Q',
}

function deductorCodeFromPan(pan: string): string | null {
  if (!PAN_REGEX.test(pan)) return null
  return PAN_CLASS_TO_CODE[pan.charAt(3)] ?? null
}

/**
 * Extracts a human-readable message from an unknown axios error.
 * The backend is inconsistent: some endpoints return `{ message: string }`,
 * others (e.g. preconfigured-masters onboard) return a plain-text string body.
 */
function extractErrorMessage(err: unknown, fallback: string): string {
  if (err && typeof err === 'object' && 'response' in err) {
    const data = (err as { response?: { data?: unknown } }).response?.data
    if (typeof data === 'string' && data.trim().length > 0) {
      return data
    }
    if (data && typeof data === 'object' && 'message' in data) {
      const message = (data as { message?: unknown }).message
      if (typeof message === 'string' && message.trim().length > 0) {
        return message
      }
    }
  }
  return fallback
}

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
  const [panDerived, setPanDerived] = useState(false)
  const [tan, setTan] = useState('')
  const [tanError, setTanError] = useState('')
  const [entityType, setEntityType] = useState('')
  const [tdsTurnoverExceeded, setTdsTurnoverExceeded] = useState<boolean | null>(null)
  const [priorYearTurnover, setPriorYearTurnover] = useState('')
  const [turnoverNature, setTurnoverNature] = useState<'BUSINESS' | 'PROFESSION'>('BUSINESS')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)
  const [step, setStep] = useState<1 | 2>(1)
  const [createdOrgId, setCreatedOrgId] = useState<string | null>(null)
  const [selectedTemplate, setSelectedTemplate] = useState<string>('standard')
  const [applyingTemplate, setApplyingTemplate] = useState(false)
  const [templateError, setTemplateError] = useState('')
  const [orgActivated, setOrgActivated] = useState(false)

  function handleChange(
    e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>
  ) {
    const { name, value } = e.target

    if (name === 'gstin') {
      const gstin = value.trim().toUpperCase()
      const derivedPan = GSTIN_REGEX.test(gstin) ? gstin.slice(2, 12) : null
      setForm((prev) => ({
        ...prev,
        gstin,
        pan: derivedPan ?? (panDerived ? '' : prev.pan),
      }))
      if (derivedPan) {
        setPanDerived(true)
        setFieldErrors((prev) => ({ ...prev, pan: '' }))
        setFieldValid((prev) => ({ ...prev, pan: true }))
      } else if (panDerived) {
        // The locked value was derived from a GSTIN that is now gone/invalid:
        // drop it and hand the field back to the user. A PAN the user typed
        // themselves (panDerived === false) is never cleared here.
        setPanDerived(false)
        setFieldErrors((prev) => ({ ...prev, pan: '' }))
        setFieldValid((prev) => ({ ...prev, pan: false }))
      }
      return
    }

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

  function handleTanChange(e: React.ChangeEvent<HTMLInputElement>) {
    setTan(e.target.value.trim().toUpperCase())
  }

  function handleTanBlur() {
    if (tan !== '' && !TAN_REGEX.test(tan)) {
      setTanError(TAN_ERROR)
    } else {
      setTanError('')
    }
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    setError('')
    if (tan !== '' && !TAN_REGEX.test(tan)) {
      setTanError(TAN_ERROR)
      return
    }
    setTanError('')
    const derivedCode = deductorCodeFromPan(form.pan)
    const isIndividual = (derivedCode ?? entityType) === 'Q'

    const payload: Record<string, unknown> = { ...form }
    if (tan !== '') payload.tan = tan
    if (derivedCode === null && entityType !== '') payload.deductorType = entityType
    if (isIndividual) {
      if (tdsTurnoverExceeded !== null) payload.tdsTurnoverExceeded = tdsTurnoverExceeded
      if (priorYearTurnover !== '') payload.priorYearTurnover = Number(priorYearTurnover)
      payload.turnoverNature = turnoverNature
    }
    setLoading(true)
    try {
      const response = await api.post('/organizations', payload)
      setCreatedOrgId(response.data.id)
      setStep(2)
    } catch (err: unknown) {
      setError(
        extractErrorMessage(
          err,
          'Organization could not be created. Try again, or contact support if the problem continues.'
        )
      )
    } finally {
      setLoading(false)
    }
  }

  async function activateOrganization() {
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
    setOrgActivated(true)
  }

  async function handleApplyTemplate() {
    setApplyingTemplate(true)
    setTemplateError('')
    try {
      await activateOrganization()
    } catch {
      setTemplateError('Could not activate the organization. Please try again.')
      setApplyingTemplate(false)
      return
    }
    try {
      await api.post('/v1/preconfigured-masters/onboard', { templateSlug: selectedTemplate })
      navigate({ to: '/dashboard' })
    } catch (err: unknown) {
      const backendMessage = extractErrorMessage(err, 'Could not apply the template.')
      setTemplateError(
        `${backendMessage} Your organization is active — you can continue and configure ledgers from the Masters page.`
      )
      setApplyingTemplate(false)
    }
  }

  async function handleSkip() {
    setApplyingTemplate(true)
    try {
      await activateOrganization()
      navigate({ to: '/dashboard' })
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
            {orgActivated && templateError ? (
              <button
                type="button"
                onClick={() => navigate({ to: '/dashboard' })}
                className="w-full h-11 rounded-[var(--radius-md)] bg-[var(--color-primary)] text-white text-sm font-medium hover:bg-[var(--color-primary-hover)] transition-colors"
              >
                Continue to Dashboard
              </button>
            ) : (
              <>
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
              </>
            )}
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
                    readOnly={panDerived}
                    aria-describedby="pan-hint"
                    className={`${inputClass} font-mono pr-8${panDerived ? ' text-[var(--color-text-muted)]' : ''}`}
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
                ) : panDerived ? (
                  <p id="pan-hint" className="mt-1 text-xs text-[var(--color-text-muted)]">
                    Derived from GSTIN
                  </p>
                ) : (
                  <p id="pan-hint" className="mt-1 text-xs text-[var(--color-text-muted)]">
                    10-character Permanent Account Number
                  </p>
                )}
              </div>

              {/* Entity class: TDS question, always-deducts note, entity-type fallback */}
              {(() => {
                const derivedCode = deductorCodeFromPan(form.pan)
                const effectiveCode = derivedCode ?? (entityType === '' ? null : entityType)
                const isIndividual = effectiveCode === 'Q'
                const showTan = effectiveCode !== null && (!isIndividual || tdsTurnoverExceeded === true)
                return (
                  <>
                    {derivedCode === null && (
                      <div>
                        <label
                          htmlFor="entity-type"
                          className="block text-sm font-medium text-[var(--color-text-primary)] mb-1.5"
                        >
                          Entity type
                        </label>
                        <select
                          id="entity-type"
                          aria-label="Entity type"
                          value={entityType}
                          onChange={(e) => setEntityType(e.target.value)}
                          className="w-full h-11 px-3 rounded-[var(--radius-md)] border border-[var(--color-border)] bg-[var(--color-surface-raised)] text-[var(--color-text-primary)] text-sm focus:outline-none focus:border-[var(--color-primary)] focus:ring-2 focus:ring-[var(--color-primary-subtle)] transition-colors"
                        >
                          <option value="">Select</option>
                          {ENTITY_TYPES.map((t) => (
                            <option key={t.code} value={t.code}>
                              {t.label}
                            </option>
                          ))}
                        </select>
                        <p className="mt-1 text-xs text-[var(--color-text-muted)]">
                          Needed to work out whether TDS applies to you
                        </p>
                      </div>
                    )}

                    {effectiveCode !== null && !isIndividual && (
                      <div className="rounded-[var(--radius-md)] border border-[var(--color-border)] bg-[var(--color-surface-raised)] px-3 py-2.5">
                        <p className="text-sm text-[var(--color-text-primary)]">
                          Companies, firms, trusts and AOPs always deduct TDS.
                        </p>
                      </div>
                    )}

                    {isIndividual && (
                      <fieldset>
                        <legend className="block text-sm font-medium text-[var(--color-text-primary)] mb-1.5">
                          Did your turnover last financial year exceed ₹1 crore (business) or ₹50
                          lakh (profession)?
                        </legend>
                        <div className="flex gap-4">
                          <label className="flex items-center gap-1.5 text-sm text-[var(--color-text-primary)]">
                            <input
                              type="radio"
                              name="tds-turnover-exceeded"
                              value="yes"
                              checked={tdsTurnoverExceeded === true}
                              onChange={() => setTdsTurnoverExceeded(true)}
                            />
                            Yes
                          </label>
                          <label className="flex items-center gap-1.5 text-sm text-[var(--color-text-primary)]">
                            <input
                              type="radio"
                              name="tds-turnover-exceeded"
                              value="no"
                              checked={tdsTurnoverExceeded === false}
                              onChange={() => setTdsTurnoverExceeded(false)}
                            />
                            No
                          </label>
                        </div>
                        {tdsTurnoverExceeded === true && (
                          <div className="mt-3 grid grid-cols-2 gap-3">
                            <div>
                              <label
                                htmlFor="prior-year-turnover"
                                className="block text-sm font-medium text-[var(--color-text-primary)] mb-1.5"
                              >
                                Last year&rsquo;s turnover (optional)
                              </label>
                              <input
                                id="prior-year-turnover"
                                type="number"
                                min={0}
                                value={priorYearTurnover}
                                onChange={(e) => setPriorYearTurnover(e.target.value)}
                                className={inputClass}
                                placeholder="₹"
                              />
                            </div>
                            <div>
                              <label
                                htmlFor="turnover-nature"
                                className="block text-sm font-medium text-[var(--color-text-primary)] mb-1.5"
                              >
                                Nature
                              </label>
                              <select
                                id="turnover-nature"
                                value={turnoverNature}
                                onChange={(e) =>
                                  setTurnoverNature(e.target.value as 'BUSINESS' | 'PROFESSION')
                                }
                                className="w-full h-11 px-3 rounded-[var(--radius-md)] border border-[var(--color-border)] bg-[var(--color-surface-raised)] text-[var(--color-text-primary)] text-sm focus:outline-none focus:border-[var(--color-primary)] focus:ring-2 focus:ring-[var(--color-primary-subtle)] transition-colors"
                              >
                                <option value="BUSINESS">Business</option>
                                <option value="PROFESSION">Profession</option>
                              </select>
                            </div>
                          </div>
                        )}
                      </fieldset>
                    )}

                    {showTan && (
                      <div>
                        <label
                          htmlFor="tan"
                          className="block text-sm font-medium text-[var(--color-text-primary)] mb-1.5"
                        >
                          TAN
                        </label>
                        <input
                          id="tan"
                          type="text"
                          maxLength={10}
                          value={tan}
                          onChange={handleTanChange}
                          onBlur={handleTanBlur}
                          aria-describedby="tan-hint"
                          className={`${inputClass} font-mono`}
                          placeholder="ABCD12345E"
                        />
                        {tanError ? (
                          <p id="tan-hint" role="alert" className="mt-1 text-xs text-[var(--color-danger)]">
                            {tanError}
                          </p>
                        ) : (
                          <p id="tan-hint" className="mt-1 text-xs text-[var(--color-text-muted)]">
                            10-character Tax Deduction and Collection Account Number
                          </p>
                        )}
                      </div>
                    )}
                  </>
                )
              })()}

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
