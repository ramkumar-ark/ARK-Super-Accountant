import { useEffect, useState } from 'react'
import { X } from 'lucide-react'
import { apiErrorMessage } from '@/lib/apiError'
import {
  WORKFLOW_TYPE_LABEL,
  startWorkflow,
  type Workflow,
  type WorkflowType,
} from '@/lib/workflowApi'

interface StartWorkflowDialogProps {
  open: boolean
  onClose: () => void
  onStarted: (workflow: Workflow) => void
}

type Grain = 'MONTHLY' | 'QUARTERLY'

const inputClass =
  'w-full h-11 px-3 rounded-[var(--radius-md)] border border-[var(--color-border)] bg-[var(--color-surface-raised)] text-sm text-[var(--color-text-primary)] focus:outline-none focus:border-[var(--color-primary)] focus:ring-2 focus:ring-[var(--color-primary-subtle)] transition-colors'

export function StartWorkflowDialog({ open, onClose, onStarted }: StartWorkflowDialogProps) {
  const [type, setType] = useState<WorkflowType>('GSTR1')
  const [grain, setGrain] = useState<Grain>('MONTHLY')
  const [month, setMonth] = useState('')
  const [year, setYear] = useState('')
  const [quarter, setQuarter] = useState('Q1')
  const [error, setError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    if (open) {
      setType('GSTR1')
      setGrain('MONTHLY')
      setMonth('')
      setYear('')
      setQuarter('Q1')
      setError(null)
      setSaving(false)
    }
  }, [open])

  if (!open) return null

  const effectiveGrain: Grain =
    type === 'TDS_PAYMENT' ? 'MONTHLY' : type === 'TDS_26Q' ? 'QUARTERLY' : grain
  const periodKey = effectiveGrain === 'QUARTERLY' ? `${year}-${quarter}` : month

  async function handleStart() {
    setError(null)
    if (!periodKey || (effectiveGrain === 'QUARTERLY' && !year)) {
      setError('Select a period.')
      return
    }
    setSaving(true)
    try {
      const workflow = await startWorkflow({ type, periodKey })
      onStarted(workflow)
    } catch (err) {
      setError(apiErrorMessage(err, 'Could not start the workflow.'))
    } finally {
      setSaving(false)
    }
  }

  return (
    <>
      <div
        className="fixed inset-0 bg-black/30 z-[var(--z-modal-backdrop)]"
        onClick={onClose}
      />
      <div
        role="dialog"
        aria-modal="true"
        aria-label="Start workflow"
        className="fixed inset-y-0 right-0 w-96 bg-[var(--color-surface)] border-l border-[var(--color-border)] shadow-[var(--shadow-lg)] flex flex-col z-[var(--z-modal)]"
      >
        <div className="h-16 flex items-center justify-between px-6 border-b border-[var(--color-border)] flex-shrink-0">
          <h2 className="text-lg font-semibold text-[var(--color-text-primary)]">Start workflow</h2>
          <button
            onClick={onClose}
            aria-label="Close start workflow"
            className="h-9 w-9 flex items-center justify-center rounded-[var(--radius-md)] text-[var(--color-text-secondary)] hover:bg-[var(--color-surface-raised)]"
          >
            <X size={18} />
          </button>
        </div>

        <div className="flex-1 overflow-y-auto p-6 flex flex-col gap-5">
          <div>
            <label className="block text-sm font-semibold text-[var(--color-text-primary)] mb-1">
              Type
            </label>
            <select
              aria-label="Type"
              value={type}
              onChange={(e) => setType(e.target.value as WorkflowType)}
              className={inputClass}
            >
              {(Object.keys(WORKFLOW_TYPE_LABEL) as WorkflowType[]).map((value) => (
                <option key={value} value={value}>
                  {WORKFLOW_TYPE_LABEL[value]}
                </option>
              ))}
            </select>
          </div>

          {(type === 'GSTR1' || type === 'GSTR3B') && (
            <div>
              <label className="block text-sm font-semibold text-[var(--color-text-primary)] mb-1">
                Period grain
              </label>
              <select
                aria-label="Period grain"
                value={grain}
                onChange={(e) => setGrain(e.target.value as Grain)}
                className={inputClass}
              >
                <option value="MONTHLY">Monthly</option>
                <option value="QUARTERLY">Quarterly</option>
              </select>
            </div>
          )}

          {effectiveGrain === 'MONTHLY' ? (
            <div>
              <label className="block text-sm font-semibold text-[var(--color-text-primary)] mb-1">
                Period
              </label>
              <input
                aria-label="Period"
                type="month"
                value={month}
                onChange={(e) => setMonth(e.target.value)}
                className={inputClass}
              />
            </div>
          ) : (
            <>
              <div>
                <label className="block text-sm font-semibold text-[var(--color-text-primary)] mb-1">
                  FY start year
                </label>
                <input
                  aria-label="FY start year"
                  type="number"
                  min={2000}
                  max={2100}
                  value={year}
                  onChange={(e) => setYear(e.target.value)}
                  className={inputClass}
                />
              </div>
              <div>
                <label className="block text-sm font-semibold text-[var(--color-text-primary)] mb-1">
                  Quarter
                </label>
                <select
                  aria-label="Quarter"
                  value={quarter}
                  onChange={(e) => setQuarter(e.target.value)}
                  className={inputClass}
                >
                  <option value="Q1">Q1</option>
                  <option value="Q2">Q2</option>
                  <option value="Q3">Q3</option>
                  <option value="Q4">Q4</option>
                </select>
              </div>
            </>
          )}

          {error && (
            <p role="alert" className="text-sm text-[var(--color-danger)]">
              {error}
            </p>
          )}
        </div>

        <div className="p-6 border-t border-[var(--color-border)] flex justify-end gap-3 flex-shrink-0">
          <button
            onClick={onClose}
            className="h-11 px-4 rounded-[var(--radius-md)] border border-[var(--color-border)] text-sm text-[var(--color-text-secondary)] hover:bg-[var(--color-surface-raised)]"
          >
            Cancel
          </button>
          <button
            onClick={handleStart}
            disabled={saving}
            className="h-11 px-4 rounded-[var(--radius-md)] bg-[var(--color-primary)] text-sm text-white hover:bg-[var(--color-primary-hover)] disabled:opacity-50"
          >
            Start
          </button>
        </div>
      </div>
    </>
  )
}
