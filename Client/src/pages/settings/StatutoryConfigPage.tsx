import { useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Scale } from 'lucide-react'
import { AppShell } from '@/components/AppShell'
import { apiErrorMessage } from '@/lib/apiError'
import {
  getStatutorySections,
  getStatutoryAudit,
  createStatutoryRule,
  setStatutorySectionActive,
  deleteStatutorySection,
  invalidateStatutoryCaches,
  STATUTORY_SECTIONS_QUERY_KEY,
  type CreateStatutoryRuleInput,
  type StatutoryRule,
  type StatutorySectionView,
} from '@/lib/statutoryAdminApi'
import { api } from '@/lib/api'
import {
  ALERT_ERROR_CLASS,
  ALERT_WARNING_CLASS,
  GHOST_BUTTON_CLASS,
  INPUT_CLASS,
  PRIMARY_BUTTON_CLASS,
  PILL_WARNING_CLASS,
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

type TabId = 'tds' | 'gst' | 'audit'

type CloneForm = {
  sectionId: string
  ruleContext: string
  effectiveDateKind: 'FINANCIAL_YEAR_START' | 'STATUTORY_DATE'
  effectiveFrom: string
  sourceCitation: string
  legalReference: string
  rateCompany: string
  rateNonCompany: string
  higherRateIfNoPan: string
  thresholdAnnual: string
  thresholdMonthly: string
}

const TAB_CLASS_ACTIVE =
  'border-[var(--color-primary)] text-[var(--color-primary)]'
const TAB_CLASS_INACTIVE =
  'border-transparent text-[var(--color-text-secondary)] hover:text-[var(--color-text-primary)]'

const fmt = (v: number | null | undefined) => (v === null || v === undefined ? '—' : String(v))
const dt = (v: string | null | undefined) => v ?? '—'

function toBody(form: CloneForm): CreateStatutoryRuleInput {
  const num = (v: string) => (v.trim() === '' ? null : Number(v))
  return {
    ruleContext: form.ruleContext,
    effectiveDateKind: form.effectiveDateKind,
    effectiveFrom: form.effectiveFrom,
    sourceCitation: form.sourceCitation,
    legalReference: form.legalReference.trim() === '' ? null : form.legalReference,
    rateCompany: num(form.rateCompany),
    rateNonCompany: num(form.rateNonCompany),
    higherRateIfNoPan: num(form.higherRateIfNoPan),
    thresholdAnnual: num(form.thresholdAnnual),
    thresholdMonthly: num(form.thresholdMonthly),
  }
}

/** Client-side mirror of the server validation, for fast feedback only. */
function clientValidate(form: CloneForm): string[] {
  const errors: string[] = []
  if (!form.effectiveFrom.trim()) errors.push('effectiveFrom is required')
  else if (form.effectiveDateKind === 'FINANCIAL_YEAR_START' && !/^\d{4}-04-01$/.test(form.effectiveFrom)) {
    errors.push('effectiveFrom must be a financial-year start (1 April) for a regime change')
  }
  if (!form.sourceCitation.trim()) errors.push('sourceCitation is required')
  else if (form.sourceCitation.length > 255) errors.push('sourceCitation must be at most 255 characters')
  if (form.legalReference.length > 255) errors.push('legalReference must be at most 255 characters')
  for (const [label, v] of [
    ['rateCompany', form.rateCompany],
    ['rateNonCompany', form.rateNonCompany],
    ['higherRateIfNoPan', form.higherRateIfNoPan],
  ] as const) {
    if (v.trim() !== '') {
      const n = Number(v)
      if (Number.isNaN(n) || n < 0 || n > 100) errors.push(`${label} must be between 0 and 100`)
    }
  }
  for (const [label, v] of [
    ['thresholdAnnual', form.thresholdAnnual],
    ['thresholdMonthly', form.thresholdMonthly],
  ] as const) {
    if (v.trim() !== '') {
      const n = Number(v)
      if (Number.isNaN(n) || n < 0) errors.push(`${label} must be at least 0`)
    }
  }
  if (form.effectiveFrom && form.effectiveFrom < '2026-01-01') {
    errors.push('BACKDATE_WARNING')
  }
  return errors
}

function prefilledClone(section: StatutorySectionView, rule: StatutoryRule): CloneForm {
  return {
    sectionId: section.id,
    ruleContext: rule.ruleContext,
    effectiveDateKind: 'FINANCIAL_YEAR_START',
    effectiveFrom: '',
    sourceCitation: '',
    legalReference: '',
    rateCompany: rule.rateCompany === null ? '' : String(rule.rateCompany),
    rateNonCompany: rule.rateNonCompany === null ? '' : String(rule.rateNonCompany),
    higherRateIfNoPan: rule.higherRateIfNoPan === null ? '' : String(rule.higherRateIfNoPan),
    thresholdAnnual: rule.thresholdAnnual === null ? '' : String(rule.thresholdAnnual),
    thresholdMonthly: rule.thresholdMonthly === null ? '' : String(rule.thresholdMonthly),
  }
}

export function StatutoryConfigPage() {
  const qc = useQueryClient()
  const [tab, setTab] = useState<TabId>('tds')
  const [clone, setClone] = useState<CloneForm | null>(null)
  const [openSource, setOpenSource] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  const sections = useQuery({
    queryKey: STATUTORY_SECTIONS_QUERY_KEY,
    queryFn: getStatutorySections,
  })
  const canEdit = sections.data?.canEdit ?? false

  const addRule = useMutation({
    mutationFn: ({ sectionId, body }: { sectionId: string; body: CreateStatutoryRuleInput }) =>
      createStatutoryRule(sectionId, body),
    onSuccess: () => {
      setClone(null)
      setError(null)
      invalidateStatutoryCaches(qc)
    },
    onError: (err) => setError(apiErrorMessage(err)),
  })
  const setSectionActive = useMutation({
    mutationFn: ({ id, active }: { id: string; active: boolean }) =>
      setStatutorySectionActive(id, active),
    onSuccess: () => {
      setError(null)
      invalidateStatutoryCaches(qc)
    },
    onError: (err) => setError(apiErrorMessage(err)),
  })
  const deleteSection = useMutation({
    mutationFn: (id: string) => deleteStatutorySection(id),
    onSuccess: () => {
      setError(null)
      invalidateStatutoryCaches(qc)
    },
    onError: (err) => setError(apiErrorMessage(err)),
  })

  const { tds, gst } = useMemo(() => {
    const all = sections.data?.sections ?? []
    return {
      tds: all.filter((s) => s.statute === 'INCOME_TAX'),
      gst: all.filter((s) => s.statute === 'CGST'),
    }
  }, [sections.data])

  const shown = tab === 'gst' ? gst : tds

  const formErrors = clone ? clientValidate(clone).filter((e) => e !== 'BACKDATE_WARNING') : []
  const backdating = clone ? clientValidate(clone).includes('BACKDATE_WARNING') : false

  return (
    <AppShell>
      <main className="flex-1 overflow-y-auto">
        <div className="max-w-4xl mx-auto p-6">
          <div className="flex items-center gap-2 mb-1">
            <Scale size={20} aria-hidden="true" />
            <h1 className="text-xl font-semibold">Statutory configuration</h1>
          </div>
          <p className="text-sm text-[var(--color-text-secondary)] mb-6">
            Applies to every company. Only app administrators can change it.
          </p>

          {error && (
            <div role="alert" className={`${ALERT_ERROR_CLASS} mb-4`}>
              {error}
            </div>
          )}

          <div
            role="tablist"
            aria-label="Statutory configuration sections"
            className="flex border-b border-[var(--color-border)] mb-8 overflow-x-auto"
          >
            {(
              [
                { id: 'tds' as const, label: 'TDS sections' },
                { id: 'gst' as const, label: 'GST parameters' },
                ...(canEdit ? [{ id: 'audit' as const, label: 'Audit log' }] : []),
              ] as const
            ).map((t) => (
              <button
                key={t.id}
                role="tab"
                aria-selected={tab === t.id}
                onClick={() => setTab(t.id)}
                className={`h-11 px-4 text-sm border-b-2 whitespace-nowrap ${
                  tab === t.id ? TAB_CLASS_ACTIVE : TAB_CLASS_INACTIVE
                }`}
              >
                {t.label}
              </button>
            ))}
          </div>

          {tab === 'audit' ? (
            <AuditLog />
          ) : (
            <div className="space-y-4">
              {sections.isLoading && <p>Loading sections…</p>}
              {shown.map((section) => (
                <SectionCard
                  key={section.id}
                  section={section}
                  canEdit={canEdit}
                  clone={clone}
                  formErrors={formErrors}
                  backdating={backdating}
                  openSource={openSource}
                  pending={addRule.isPending || setSectionActive.isPending || deleteSection.isPending}
                  onClone={() => {
                    const latest =
                      section.rules[section.rules.length - 1] ?? null
                    if (latest) setClone(prefilledClone(section, latest))
                  }}
                  onCancelClone={() => setClone(null)}
                  onFormChange={setClone}
                  onSubmitClone={() => {
                    if (!clone || formErrors.length > 0) return
                    addRule.mutate({ sectionId: clone.sectionId, body: toBody(clone) })
                  }}
                  onToggleActive={(active) =>
                    setSectionActive.mutate({ id: section.id, active })
                  }
                  onDelete={() => deleteSection.mutate(section.id)}
                  sourceOpen={(ruleId) => openSource === ruleId}
                  onToggleSource={(ruleId) =>
                    setOpenSource((cur) => (cur === ruleId ? null : ruleId))
                  }
                />
              ))}
              {!sections.isLoading && shown.length === 0 && <p>No sections.</p>}
            </div>
          )}
        </div>
      </main>
    </AppShell>
  )
}

function SectionCard(props: {
  section: StatutorySectionView
  canEdit: boolean
  clone: CloneForm | null
  formErrors: string[]
  backdating: boolean
  openSource: string | null
  pending: boolean
  onClone: () => void
  onCancelClone: () => void
  onFormChange: (form: CloneForm) => void
  onSubmitClone: () => void
  onToggleActive: (active: boolean) => void
  onDelete: () => void
  sourceOpen: (ruleId: string) => boolean
  onToggleSource: (ruleId: string) => void
}) {
  const { section, canEdit } = props
  return (
    <div className={SECTION_CARD_CLASS}>
      <div className="flex flex-wrap items-center gap-2 mb-3">
        <span className="font-semibold">{section.code}</span>
        <span className="text-sm text-[var(--color-text-secondary)]">{section.description}</span>
        {!section.active && <span className={PILL_WARNING_CLASS}>Inactive</span>}
        <span className={section.seeded ? PILL_WARNING_CLASS : PILL_WARNING_CLASS}>
          {section.seeded ? 'Seeded' : 'Added by admin'}
        </span>
        {(section.rpu26qCode || section.rpu140Code) && (
          <span className="text-xs text-[var(--color-text-muted)]">
            {section.rpu26qCode ? `RPU 26Q ${section.rpu26qCode}` : ''}
            {section.rpu26qCode && section.rpu140Code ? ' · ' : ''}
            {section.rpu140Code ? `RPU 140 ${section.rpu140Code}` : ''}
          </span>
        )}
      </div>

      {section.rules.length > 0 ? (
        <table className={STACKED_TABLE_CLASS}>
          <thead className={STACKED_THEAD_CLASS}>
            <tr>
              <th className={TABLE_HEAD_CLASS}>Context</th>
              <th className={TABLE_HEAD_CLASS}>From–to</th>
              <th className={TABLE_HEAD_CLASS}>Rates</th>
              <th className={TABLE_HEAD_CLASS}>Thresholds</th>
              <th className={TABLE_HEAD_CLASS}>Legal reference</th>
              <th className={TABLE_HEAD_CLASS}>Source</th>
            </tr>
          </thead>
          <tbody className={STACKED_BODY_CLASS}>
            {section.rules.map((rule) => (
              <tr key={rule.id} className={STACKED_ROW_CLASS}>
                <td className={STACKED_CELL_CLASS}>
                  <span className={STACKED_LABEL_CLASS}>Context</span>
                  <span className={STACKED_VALUE_CLASS}>{rule.ruleContext}</span>
                </td>
                <td className={STACKED_CELL_CLASS}>
                  <span className={STACKED_LABEL_CLASS}>From–to</span>
                  <span className={STACKED_VALUE_CLASS}>
                    {rule.effectiveFrom} – {rule.effectiveTo ?? 'open'}
                  </span>
                </td>
                <td className={STACKED_CELL_CLASS}>
                  <span className={STACKED_LABEL_CLASS}>Rates</span>
                  <span className={STACKED_VALUE_CLASS}>
                    {fmt(rule.rateCompany)} / {fmt(rule.rateNonCompany)} / no-PAN{' '}
                    {fmt(rule.higherRateIfNoPan)}
                  </span>
                </td>
                <td className={STACKED_CELL_CLASS}>
                  <span className={STACKED_LABEL_CLASS}>Thresholds</span>
                  <span className={STACKED_VALUE_CLASS}>
                    {[
                      ['txn', rule.thresholdPerTransaction],
                      ['annual', rule.thresholdAnnual],
                      ['monthly', rule.thresholdMonthly],
                    ]
                      .filter(([, v]) => v !== null)
                      .map(([k, v]) => `${k} ${v}`)
                      .join(' · ') || '—'}
                  </span>
                </td>
                <td className={STACKED_CELL_CLASS}>
                  <span className={STACKED_LABEL_CLASS}>Legal reference</span>
                  <span className={STACKED_VALUE_CLASS}>{dt(rule.legalReference)}</span>
                </td>
                <td className={STACKED_CELL_CLASS}>
                  <span className={STACKED_LABEL_CLASS}>Source</span>
                  <span className={STACKED_VALUE_CLASS}>
                    <button
                      type="button"
                      aria-expanded={props.sourceOpen(rule.id)}
                      onClick={() => props.onToggleSource(rule.id)}
                      className={GHOST_BUTTON_CLASS}
                    >
                      Source
                    </button>
                  </span>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      ) : (
        <p className="text-sm text-[var(--color-text-secondary)]">No rule rows yet.</p>
      )}

      {section.rules.map((rule) =>
        props.sourceOpen(rule.id) ? (
          <div
            key={`src-${rule.id}`}
            className="mt-2 text-xs text-[var(--color-text-secondary)] border-l-2 border-[var(--color-border)] pl-3 py-1"
          >
            <p>{rule.sourceCitation}</p>
            <p>
              {rule.seeded ? 'Seeded row' : `Added by ${rule.createdBy}`} · updated{' '}
              {dt(rule.updatedAt)}
            </p>
          </div>
        ) : null,
      )}

      {canEdit && (
        <div className="flex flex-wrap gap-2 mt-3">
          {section.rules.length > 0 && (
            <button type="button" onClick={props.onClone} className={GHOST_BUTTON_CLASS}>
              Clone to new dated row
            </button>
          )}
          {section.statute === 'INCOME_TAX' && (
            <button
              type="button"
              className={GHOST_BUTTON_CLASS}
              disabled={props.pending}
              onClick={() => props.onToggleActive(!section.active)}
            >
              {section.active ? 'Deactivate' : 'Activate'}
            </button>
          )}
          {!section.seeded && (
            <button
              type="button"
              className={GHOST_BUTTON_CLASS}
              disabled={props.pending}
              onClick={props.onDelete}
            >
              Delete
            </button>
          )}
        </div>
      )}

      {props.clone && props.clone.sectionId === section.id && (
        <CloneForm
          form={props.clone}
          errors={props.formErrors}
          backdating={props.backdating}
          pending={props.pending}
          serverError={null}
          onChange={props.onFormChange}
          onCancel={props.onCancelClone}
          onSubmit={props.onSubmitClone}
        />
      )}
    </div>
  )
}

function CloneForm(props: {
  form: CloneForm
  errors: string[]
  backdating: boolean
  pending: boolean
  serverError: string | null
  onChange: (form: CloneForm) => void
  onCancel: () => void
  onSubmit: () => void
}) {
  const { form, onChange } = props
  const set = (patch: Partial<CloneForm>) => onChange({ ...form, ...patch })
  return (
    <form
      className="mt-4 grid sm:grid-cols-2 gap-3 border border-[var(--color-border)] rounded-[var(--radius-md)] p-3"
      onSubmit={(e) => {
        e.preventDefault()
        props.onSubmit()
      }}
      aria-label="Clone to new dated row"
    >
      <fieldset className="contents">
        <legend className="sr-only">Effective date kind</legend>
        <label className="flex items-center gap-2 text-sm">
          <input
            type="radio"
            name={`kind-${form.sectionId}`}
            checked={form.effectiveDateKind === 'FINANCIAL_YEAR_START'}
            onChange={() => set({ effectiveDateKind: 'FINANCIAL_YEAR_START' })}
          />
          Financial-year start (1 April)
        </label>
        <label className="flex items-center gap-2 text-sm">
          <input
            type="radio"
            name={`kind-${form.sectionId}`}
            checked={form.effectiveDateKind === 'STATUTORY_DATE'}
            onChange={() => set({ effectiveDateKind: 'STATUTORY_DATE' })}
          />
          Exact statutory date
        </label>
      </fieldset>
      <label className="text-sm">
        Effective from
        <input
          aria-label="Effective from"
          className={INPUT_CLASS}
          value={form.effectiveFrom}
          onChange={(e) => set({ effectiveFrom: e.target.value })}
          placeholder="YYYY-MM-DD"
        />
      </label>
      <label className="text-sm">
        Source citation
        <input
          aria-label="Source citation"
          className={INPUT_CLASS}
          value={form.sourceCitation}
          onChange={(e) => set({ sourceCitation: e.target.value })}
        />
      </label>
      <label className="text-sm">
        Legal reference
        <input
          aria-label="Legal reference"
          className={INPUT_CLASS}
          value={form.legalReference}
          onChange={(e) => set({ legalReference: e.target.value })}
        />
      </label>
      <label className="text-sm">
        Company rate
        <input
          aria-label="Company rate"
          className={INPUT_CLASS}
          value={form.rateCompany}
          onChange={(e) => set({ rateCompany: e.target.value })}
        />
      </label>
      <label className="text-sm">
        Non-company rate
        <input
          aria-label="Non-company rate"
          className={INPUT_CLASS}
          value={form.rateNonCompany}
          onChange={(e) => set({ rateNonCompany: e.target.value })}
        />
      </label>
      <label className="text-sm">
        No-PAN rate
        <input
          aria-label="No-PAN rate"
          className={INPUT_CLASS}
          value={form.higherRateIfNoPan}
          onChange={(e) => set({ higherRateIfNoPan: e.target.value })}
        />
      </label>
      <label className="text-sm">
        Annual threshold
        <input
          aria-label="Annual threshold"
          className={INPUT_CLASS}
          value={form.thresholdAnnual}
          onChange={(e) => set({ thresholdAnnual: e.target.value })}
        />
      </label>
      <label className="text-sm">
        Monthly threshold
        <input
          aria-label="Monthly threshold"
          className={INPUT_CLASS}
          value={form.thresholdMonthly}
          onChange={(e) => set({ thresholdMonthly: e.target.value })}
        />
      </label>
      {props.backdating && (
        <div className={`${ALERT_WARNING_CLASS} sm:col-span-2`}>
          Past periods re-analysed after this will use the new values.
        </div>
      )}
      {props.errors.length > 0 && (
        <div role="alert" className={`${ALERT_ERROR_CLASS} sm:col-span-2`}>
          {props.errors.join('; ')}
        </div>
      )}
      {props.serverError && (
        <div role="alert" className={`${ALERT_ERROR_CLASS} sm:col-span-2`}>
          {props.serverError}
        </div>
      )}
      <div className="flex gap-2 sm:col-span-2">
        <button type="submit" className={PRIMARY_BUTTON_CLASS} disabled={props.pending}>
          Add rule
        </button>
        <button type="button" className={GHOST_BUTTON_CLASS} onClick={props.onCancel}>
          Cancel
        </button>
      </div>
    </form>
  )
}

function AuditLog() {
  const audit = useQuery({
    queryKey: ['statutory-admin', 'audit'],
    queryFn: () => getStatutoryAudit(100),
  })
  if (audit.isLoading) return <p>Loading audit log…</p>
  return (
    <div className={SECTION_CARD_CLASS}>
      <table className={STACKED_TABLE_CLASS}>
        <thead className={STACKED_THEAD_CLASS}>
          <tr>
            <th className={TABLE_HEAD_CLASS}>When</th>
            <th className={TABLE_HEAD_CLASS}>Who</th>
            <th className={TABLE_HEAD_CLASS}>Action</th>
            <th className={TABLE_HEAD_CLASS}>Section</th>
            <th className={TABLE_HEAD_CLASS}>Details</th>
          </tr>
        </thead>
        <tbody className={STACKED_BODY_CLASS}>
          {(audit.data ?? []).map((e) => (
            <tr key={e.id} className={STACKED_ROW_CLASS}>
              <td className={STACKED_CELL_CLASS}>
                <span className={STACKED_LABEL_CLASS}>When</span>
                <span className={STACKED_VALUE_CLASS}>{e.occurredAt}</span>
              </td>
              <td className={STACKED_CELL_CLASS}>
                <span className={STACKED_LABEL_CLASS}>Who</span>
                <span className={STACKED_VALUE_CLASS}>{e.actorUsername}</span>
              </td>
              <td className={STACKED_CELL_CLASS}>
                <span className={STACKED_LABEL_CLASS}>Action</span>
                <span className={STACKED_VALUE_CLASS}>{e.action}</span>
              </td>
              <td className={STACKED_CELL_CLASS}>
                <span className={STACKED_LABEL_CLASS}>Section</span>
                <span className={STACKED_VALUE_CLASS}>{e.sectionCode}</span>
              </td>
              <td className={STACKED_CELL_CLASS}>
                <span className={STACKED_LABEL_CLASS}>Details</span>
                <span className={STACKED_VALUE_CLASS}>{e.details ?? '—'}</span>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
