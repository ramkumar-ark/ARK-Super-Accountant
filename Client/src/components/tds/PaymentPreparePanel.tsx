import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiErrorMessage } from '@/lib/apiError'
import {
  canOperate,
  invalidateWorkflow,
  isAccountant,
  STAGE_LABEL,
  type Workflow,
} from '@/lib/workflowApi'
import {
  approveChallan,
  createChallan,
  deleteChallan,
  listChallans,
  updateChallan,
  type ChallanRequest,
  type TdsChallan,
} from '@/lib/tdsApi'
import { ChallanForm } from './ChallanForm'

const buttonClass =
  'inline-flex h-9 w-fit items-center justify-center rounded-[var(--radius-md)] bg-[var(--color-primary)] px-3 text-sm font-medium text-white hover:bg-[var(--color-primary-hover)] disabled:cursor-not-allowed disabled:opacity-50'
const secondaryButtonClass =
  'inline-flex h-9 w-fit items-center justify-center rounded-[var(--radius-md)] border border-[var(--color-border)] px-3 text-sm font-medium text-[var(--color-text-primary)] hover:bg-[var(--color-surface-raised)]'

function money(value: number | null): string {
  if (value == null) return '—'
  return value.toLocaleString('en-IN')
}

function dateLabel(value: string | null): string {
  return value ?? '—'
}

interface Props {
  workflow: Workflow
  role: string
}

export function PaymentPreparePanel({ workflow, role }: Props) {
  const queryClient = useQueryClient()
  const [editing, setEditing] = useState<TdsChallan | 'new' | null>(null)
  const [mutationError, setMutationError] = useState('')

  const challansQuery = useQuery({
    queryKey: ['tds-challans', workflow.id],
    queryFn: () => listChallans(workflow.id),
  })

  const editable = canOperate(role) && workflow.stage === 'PREPARE'

  const refresh = () => {
    void queryClient.invalidateQueries({ queryKey: ['tds-challans', workflow.id] })
    return invalidateWorkflow(queryClient, workflow.id)
  }

  const createMutation = useMutation({
    mutationFn: (req: ChallanRequest) => createChallan(workflow.id, req),
    onSuccess: () => {
      setEditing(null)
      setMutationError('')
      return refresh()
    },
    onError: (err) => setMutationError(apiErrorMessage(err, 'Could not save the challan.')),
  })
  const updateMutation = useMutation({
    mutationFn: ({ id, req }: { id: string; req: ChallanRequest }) =>
      updateChallan(workflow.id, id, req),
    onSuccess: () => {
      setEditing(null)
      setMutationError('')
      return refresh()
    },
    onError: (err) => setMutationError(apiErrorMessage(err, 'Could not save the challan.')),
  })
  const deleteMutation = useMutation({
    mutationFn: (id: string) => deleteChallan(workflow.id, id),
    onSuccess: () => {
      setMutationError('')
      return refresh()
    },
    onError: (err) => setMutationError(apiErrorMessage(err, 'Could not delete the challan.')),
  })
  const approveMutation = useMutation({
    mutationFn: (id: string) => approveChallan(workflow.id, id),
    onSuccess: () => {
      setMutationError('')
      return refresh()
    },
    onError: (err) => setMutationError(apiErrorMessage(err, 'Could not approve the challan.')),
  })

  if (challansQuery.isLoading) {
    return <p className="text-sm text-[var(--color-text-muted)]">Loading challans…</p>
  }
  if (challansQuery.isError || !challansQuery.data) {
    return (
      <p role="alert" className="text-sm text-[var(--color-danger)]">
        Could not load the challans.
      </p>
    )
  }

  const { challans, tdsDeducted, approvedTdsTotal, excess } = challansQuery.data
  const shortfall = tdsDeducted == null ? null : tdsDeducted - approvedTdsTotal

  return (
    <section className="flex flex-col gap-4 rounded-[var(--radius-lg)] border border-[var(--color-border)] bg-[var(--color-surface)] p-4">
      <header className="flex items-center justify-between">
        <h2 className="text-base font-semibold text-[var(--color-text-primary)]">
          TDS challans · {STAGE_LABEL[workflow.stage]}
        </h2>
        {editable && (
          <button type="button" className={buttonClass} onClick={() => setEditing('new')}>
            Add challan
          </button>
        )}
      </header>

      <dl className="grid grid-cols-3 gap-3 text-sm">
        <div>
          <dt className="text-[var(--color-text-muted)]">TDS deducted</dt>
          <dd className="text-[var(--color-text-primary)]">
            {tdsDeducted == null ? 'Not computed yet: run analysis' : money(tdsDeducted)}
          </dd>
        </div>
        <div>
          <dt className="text-[var(--color-text-muted)]">Approved challans</dt>
          <dd className="text-[var(--color-text-primary)]">{money(approvedTdsTotal)}</dd>
        </div>
        <div>
          <dt className="text-[var(--color-text-muted)]">Cover</dt>
          <dd>
            {shortfall != null && shortfall > 0 && (
              <span className="text-[var(--color-danger)]">Short by {money(shortfall)}</span>
            )}
            {excess != null && excess > 0 && (
              <span className="text-[var(--color-text-secondary)]">Excess {money(excess)}</span>
            )}
            {shortfall != null && shortfall <= 0 && (excess == null || excess <= 0) && (
              <span className="text-[var(--color-text-secondary)]">Fully covered</span>
            )}
          </dd>
        </div>
      </dl>

      <table className="w-full text-left text-sm">
        <thead>
          <tr className="border-b border-[var(--color-border)] text-[var(--color-text-muted)]">
            <th className="py-2 pr-2 font-medium">BSR</th>
            <th className="py-2 pr-2 font-medium">Deposit date</th>
            <th className="py-2 pr-2 font-medium">Serial</th>
            <th className="py-2 pr-2 font-medium">CIN</th>
            <th className="py-2 pr-2 font-medium">TDS</th>
            <th className="py-2 pr-2 font-medium">Total</th>
            <th className="py-2 pr-2 font-medium">Status</th>
            <th className="py-2 pr-2 font-medium">Proposed by</th>
            <th className="py-2 pr-2 font-medium">Approved by</th>
            {editable && <th className="py-2 font-medium">Actions</th>}
          </tr>
        </thead>
        <tbody>
          {challans.length === 0 && (
            <tr>
              <td colSpan={editable ? 10 : 9} className="py-3 text-[var(--color-text-muted)]">
                No challans proposed yet.
              </td>
            </tr>
          )}
          {challans.map((challan) => (
            <tr key={challan.id} className="border-b border-[var(--color-border)]">
              <td className="py-2 pr-2">{challan.bsrCode ?? '—'}</td>
              <td className="py-2 pr-2">{dateLabel(challan.depositDate)}</td>
              <td className="py-2 pr-2">
                {challan.challanSerialNumber ?? '—'}
                {challan.nilChallan && (
                  <span className="ml-2 text-xs text-[var(--color-text-muted)]">Nil challan</span>
                )}
              </td>
              <td className="py-2 pr-2">{challan.cin ?? '—'}</td>
              <td className="py-2 pr-2">{money(challan.tdsAmount)}</td>
              <td className="py-2 pr-2">{money(challan.totalAmount)}</td>
              <td className="py-2 pr-2">
                <span className="rounded-[var(--radius-sm)] border border-[var(--color-border)] px-2 py-0.5 text-xs">
                  {challan.status === 'APPROVED' ? 'Approved' : 'Proposed'}
                </span>
              </td>
              <td className="py-2 pr-2">{challan.proposedBy ?? '—'}</td>
              <td className="py-2 pr-2">{challan.approvedBy ?? '—'}</td>
              {editable && (
                <td className="flex gap-2 py-2">
                  {isAccountant(role) && challan.status === 'PROPOSED' && (
                    <button
                      type="button"
                      className={secondaryButtonClass}
                      onClick={() => approveMutation.mutate(challan.id)}
                    >
                      Approve
                    </button>
                  )}
                  <button
                    type="button"
                    className={secondaryButtonClass}
                    onClick={() => setEditing(challan)}
                  >
                    Edit
                  </button>
                  <button
                    type="button"
                    className={secondaryButtonClass}
                    onClick={() => deleteMutation.mutate(challan.id)}
                  >
                    Delete
                  </button>
                </td>
              )}
            </tr>
          ))}
        </tbody>
      </table>

      {mutationError && (
        <p role="alert" className="text-sm text-[var(--color-danger)]">
          {mutationError}
        </p>
      )}

      {editing && (
        <div className="rounded-[var(--radius-md)] border border-[var(--color-border)] p-3">
          <h3 className="mb-2 text-sm font-semibold text-[var(--color-text-primary)]">
            {editing === 'new' ? 'Add challan' : 'Edit challan'}
          </h3>
          <ChallanForm
            initial={editing === 'new' ? undefined : editing}
            onCancel={() => {
              setEditing(null)
              setMutationError('')
            }}
            onSubmit={(req) => {
              setMutationError('')
              if (editing === 'new') createMutation.mutate(req)
              else updateMutation.mutate({ id: editing.id, req })
            }}
          />
        </div>
      )}
    </section>
  )
}
