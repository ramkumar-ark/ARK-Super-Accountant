import { useState, type ReactNode } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { X } from 'lucide-react'
import { apiErrorMessage } from '@/lib/apiError'
import {
  FILING_REF_LABEL,
  WORKFLOW_TYPE_LABEL,
  advanceWorkflow,
  finalizeWorkflow,
  invalidateWorkflow,
  isAccountant,
  recordFiling,
  unfinalizeWorkflow,
  type WorkflowDetail,
} from '@/lib/workflowApi'

interface Props {
  detail: WorkflowDetail
  role: string
}

const buttonClass =
  'inline-flex h-9 w-fit items-center justify-center rounded-[var(--radius-md)] bg-[var(--color-primary)] px-3 text-sm font-medium text-white hover:bg-[var(--color-primary-hover)] disabled:cursor-not-allowed disabled:opacity-50'
const secondaryButtonClass =
  'inline-flex h-9 items-center justify-center rounded-[var(--radius-md)] border border-[var(--color-border)] px-3 text-sm font-medium text-[var(--color-text-primary)] hover:bg-[var(--color-surface-raised)]'
const inputClass =
  'w-full h-11 px-3 rounded-[var(--radius-md)] border border-[var(--color-border)] bg-[var(--color-surface-raised)] text-sm text-[var(--color-text-primary)] focus:outline-none focus:border-[var(--color-primary)] focus:ring-2 focus:ring-[var(--color-primary-subtle)] transition-colors'

function Drawer({
  label,
  onClose,
  children,
}: {
  label: string
  onClose: () => void
  children: ReactNode
}) {
  return (
    <>
      <div
        className="fixed inset-0 bg-black/30 z-[var(--z-modal-backdrop)]"
        onClick={onClose}
      />
      <div
        role="dialog"
        aria-modal="true"
        aria-label={label}
        className="fixed inset-y-0 right-0 w-96 bg-[var(--color-surface)] border-l border-[var(--color-border)] shadow-[var(--shadow-lg)] flex flex-col z-[var(--z-modal)]"
      >
        <div className="h-16 flex items-center justify-between px-6 border-b border-[var(--color-border)] flex-shrink-0">
          <h2 className="text-lg font-semibold text-[var(--color-text-primary)] truncate">
            {label}
          </h2>
          <button
            type="button"
            onClick={onClose}
            aria-label="Close"
            className="h-9 w-9 flex items-center justify-center rounded-[var(--radius-md)] text-[var(--color-text-secondary)] hover:bg-[var(--color-surface-raised)]"
          >
            <X size={18} />
          </button>
        </div>
        <div className="flex-1 overflow-y-auto p-6 flex flex-col gap-4">{children}</div>
      </div>
    </>
  )
}

export function WorkflowStageActions({ detail, role }: Props) {
  const queryClient = useQueryClient()
  const workflow = detail.workflow
  const [advanceError, setAdvanceError] = useState('')
  const [drawer, setDrawer] = useState<'finalize' | 'unfinalize' | 'filing' | null>(null)
  const [drawerError, setDrawerError] = useState('')
  const [reason, setReason] = useState('')
  const [filingRef, setFilingRef] = useState('')
  const [filingDate, setFilingDate] = useState('')

  const closeDrawer = () => {
    setDrawer(null)
    setDrawerError('')
  }

  const advanceMutation = useMutation({
    mutationFn: () => advanceWorkflow(workflow.id),
    onSuccess: () => {
      setAdvanceError('')
      return invalidateWorkflow(queryClient, workflow.id)
    },
    onError: (err) =>
      setAdvanceError(apiErrorMessage(err, 'Could not move the workflow to Prepare.')),
  })

  const finalizeMutation = useMutation({
    mutationFn: () => finalizeWorkflow(workflow.id),
    onSuccess: () => {
      closeDrawer()
      return invalidateWorkflow(queryClient, workflow.id)
    },
    onError: (err) => setDrawerError(apiErrorMessage(err, 'Could not finalize the workflow.')),
  })

  const unfinalizeMutation = useMutation({
    mutationFn: () => unfinalizeWorkflow(workflow.id, reason),
    onSuccess: () => {
      closeDrawer()
      setReason('')
      return invalidateWorkflow(queryClient, workflow.id)
    },
    onError: (err) => setDrawerError(apiErrorMessage(err, 'Could not un-finalize the workflow.')),
  })

  const filingMutation = useMutation({
    mutationFn: () => recordFiling(workflow.id, filingRef, filingDate),
    onSuccess: () => {
      closeDrawer()
      return invalidateWorkflow(queryClient, workflow.id)
    },
    onError: (err) => setDrawerError(apiErrorMessage(err, 'Could not record the filing.')),
  })

  if (workflow.stage === 'FILED') {
    return (
      <section
        data-testid="workflow-filed-reference"
        className="rounded-[var(--radius-lg)] border border-[var(--color-border)] bg-[var(--color-surface)] p-4"
      >
        <p className="text-sm text-[var(--color-text-primary)]">
          Filed: {workflow.filingRef} on {workflow.filingRefDate}
        </p>
      </section>
    )
  }

  if (!isAccountant(role)) {
    return null
  }

  if (workflow.stage === 'FINDINGS_REVIEW') {
    const blockingReason =
      detail.blockingFindings > 0
        ? `Resolve ${detail.blockingFindings} blocking findings first`
        : ''
    const baselineReason = !detail.baselineConfirmed
      ? 'Baseline must be confirmed before Prepare'
      : ''
    const disabledReason = blockingReason || baselineReason

    return (
      <section className="flex flex-col gap-2 rounded-[var(--radius-lg)] border border-[var(--color-border)] bg-[var(--color-surface)] p-4">
        <button
          type="button"
          onClick={() => advanceMutation.mutate()}
          disabled={Boolean(disabledReason) || advanceMutation.isPending}
          className={buttonClass}
        >
          Move to Prepare
        </button>
        {disabledReason && (
          <p className="text-sm text-[var(--color-text-muted)]">{disabledReason}</p>
        )}
        {advanceError && (
          <p role="alert" className="text-sm text-[var(--color-danger)]">
            {advanceError}
          </p>
        )}
      </section>
    )
  }

  if (workflow.stage === 'PREPARE') {
    return (
      <section className="flex flex-col gap-2 rounded-[var(--radius-lg)] border border-[var(--color-border)] bg-[var(--color-surface)] p-4">
        <button type="button" onClick={() => setDrawer('finalize')} className={buttonClass}>
          Finalize
        </button>
        {drawer === 'finalize' && (
          <Drawer label="Finalize workflow" onClose={closeDrawer}>
            <p className="text-sm text-[var(--color-text-primary)]">
              Finalizing freezes this period&apos;s day book.
            </p>
            {drawerError && (
              <p role="alert" className="text-sm text-[var(--color-danger)]">
                {drawerError}
              </p>
            )}
            <div className="flex gap-3">
              <button
                type="button"
                onClick={() => finalizeMutation.mutate()}
                disabled={finalizeMutation.isPending}
                className={buttonClass}
              >
                Confirm finalize
              </button>
              <button type="button" onClick={closeDrawer} className={secondaryButtonClass}>
                Cancel
              </button>
            </div>
          </Drawer>
        )}
      </section>
    )
  }

  if (workflow.stage === 'FINALIZED') {
    return (
      <section className="flex flex-col gap-3 rounded-[var(--radius-lg)] border border-[var(--color-border)] bg-[var(--color-surface)] p-4">
        <div className="flex flex-wrap gap-3">
          <button type="button" onClick={() => setDrawer('unfinalize')} className={buttonClass}>
            Un-finalize
          </button>
          <button type="button" onClick={() => setDrawer('filing')} className={secondaryButtonClass}>
            Record filing
          </button>
        </div>
        {drawer === 'unfinalize' && (
          <Drawer label="Un-finalize workflow" onClose={closeDrawer}>
            <div>
              <label
                htmlFor="unfinalize-reason"
                className="mb-1 block text-sm font-semibold text-[var(--color-text-primary)]"
              >
                Reason
              </label>
              <textarea
                id="unfinalize-reason"
                value={reason}
                onChange={(e) => setReason(e.target.value)}
                rows={4}
                className={inputClass}
              />
            </div>
            {drawerError && (
              <p role="alert" className="text-sm text-[var(--color-danger)]">
                {drawerError}
              </p>
            )}
            <div className="flex gap-3">
              <button
                type="button"
                onClick={() => unfinalizeMutation.mutate()}
                disabled={reason.trim() === '' || unfinalizeMutation.isPending}
                className={buttonClass}
              >
                Confirm un-finalize
              </button>
              <button type="button" onClick={closeDrawer} className={secondaryButtonClass}>
                Cancel
              </button>
            </div>
          </Drawer>
        )}
        {drawer === 'filing' && (
          <Drawer label={`Record filing — ${WORKFLOW_TYPE_LABEL[workflow.type]}`} onClose={closeDrawer}>
            <div>
              <label
                htmlFor="filing-ref"
                className="mb-1 block text-sm font-semibold text-[var(--color-text-primary)]"
              >
                {FILING_REF_LABEL[workflow.type]}
              </label>
              <input
                id="filing-ref"
                value={filingRef}
                onChange={(e) => setFilingRef(e.target.value)}
                className={inputClass}
              />
            </div>
            <div>
              <label
                htmlFor="filing-date"
                className="mb-1 block text-sm font-semibold text-[var(--color-text-primary)]"
              >
                Filing date
              </label>
              <input
                id="filing-date"
                type="date"
                value={filingDate}
                onChange={(e) => setFilingDate(e.target.value)}
                className={inputClass}
              />
            </div>
            {drawerError && (
              <p role="alert" className="text-sm text-[var(--color-danger)]">
                {drawerError}
              </p>
            )}
            <div className="flex gap-3">
              <button
                type="button"
                onClick={() => filingMutation.mutate()}
                disabled={filingRef.trim() === '' || filingDate === '' || filingMutation.isPending}
                className={buttonClass}
              >
                Confirm filing
              </button>
              <button type="button" onClick={closeDrawer} className={secondaryButtonClass}>
                Cancel
              </button>
            </div>
          </Drawer>
        )}
      </section>
    )
  }

  return null
}
