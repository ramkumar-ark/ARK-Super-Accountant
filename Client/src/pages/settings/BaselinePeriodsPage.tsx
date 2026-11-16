import { useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useAuthStore } from '@/store/authStore'
import { AppShell } from '@/components/AppShell'
import { apiErrorMessage } from '@/lib/apiError'
import { listBaselinePeriods, type BaselinePeriodView } from '@/lib/filedReturnsApi'
import { Gstr1UploadButton } from '@/components/filedreturns/Gstr1UploadButton'
import { Gstr3bPdfImport } from '@/components/filedreturns/Gstr3bPdfImport'
import { Gstr3bEntryForm } from '@/components/filedreturns/Gstr3bEntryForm'
import { BaselinePeriodDetail } from '@/components/filedreturns/BaselinePeriodDetail'

const STATUS_LABELS: Record<BaselinePeriodView['status'], string> = {
  NOT_IMPORTED: 'Not imported',
  MAPPING_INCOMPLETE: 'Mapping incomplete',
  NOT_RECONCILED: 'Not reconciled',
  RECONCILED: 'Reconciled',
}

const SOURCE_LABELS: Record<string, string> = {
  PORTAL_JSON: 'portal JSON',
  PORTAL_PDF: 'portal PDF',
  MANUAL_ENTRY: 'manual entry',
}

const FORM_LABELS: Record<string, string> = {
  GSTR1: 'GSTR-1',
  IFF: 'IFF',
  GSTR3B: 'GSTR-3B',
}

export function BaselinePeriodsPage() {
  const { user } = useAuthStore()
  const queryClient = useQueryClient()
  const canUpload = user?.role === 'ROLE_ACCOUNTANT' || user?.role === 'ROLE_OPERATOR'
  const [expanded, setExpanded] = useState<string | null>(null)
  const [manualEntryOpen, setManualEntryOpen] = useState(false)

  const { data: periods, isLoading, isError, error } = useQuery({
    queryKey: ['baseline-periods'],
    queryFn: listBaselinePeriods,
  })

  function refresh() {
    queryClient.invalidateQueries({ queryKey: ['baseline-periods'] })
  }

  const rows = periods ?? []
  const gstr1Rows = rows.filter((r) => r.type === 'GSTR1')
  const gstr3bRows = rows.filter((r) => r.type === 'GSTR3B')

  return (
    <AppShell>
      <main className="flex-1 overflow-y-auto">
        <div className="max-w-5xl mx-auto p-6 space-y-6">
          <div className="flex items-center justify-between">
            <h1 className="text-xl font-semibold">Baseline periods</h1>
            <a href="/settings/compliance" className="text-sm underline">
              Compliance settings
            </a>
          </div>

          {canUpload && (
            <div className="flex flex-col gap-4 rounded-[var(--radius-lg)] border border-[var(--color-border)] bg-[var(--color-surface)] p-4">
              <Gstr1UploadButton onUploaded={refresh} />
              <Gstr3bPdfImport onSaved={refresh} />
              <div className="flex flex-col gap-1">
                <span className="text-sm font-medium">Enter GSTR-3B manually</span>
                <button
                  type="button"
                  className="self-start px-3 py-1.5 text-sm rounded border"
                  onClick={() => setManualEntryOpen((v) => !v)}
                >
                  {manualEntryOpen ? 'Hide form' : 'Open form'}
                </button>
                {manualEntryOpen && (
                  <div className="border rounded p-3">
                    <Gstr3bEntryForm
                      filingPeriod=""
                      onSaved={() => {
                        setManualEntryOpen(false)
                        refresh()
                      }}
                      onCancel={() => setManualEntryOpen(false)}
                    />
                  </div>
                )}
              </div>
            </div>
          )}

          {isLoading && <p className="text-sm text-[var(--color-text-muted)]">Loading…</p>}
          {isError && (
            <p role="alert" className="text-sm text-red-600">
              {apiErrorMessage(error, 'Could not load baseline periods')}
            </p>
          )}

          {[
            { label: 'GSTR-1', rows: gstr1Rows },
            { label: 'GSTR-3B', rows: gstr3bRows },
          ].map((group) => (
            <section key={group.label}>
              <h2 className="text-lg font-semibold mb-2">{group.label}</h2>
              <table className="w-full text-sm">
                <thead>
                  <tr className="text-left border-b">
                    <th className="py-2">Period</th>
                    <th className="py-2">Imported</th>
                    <th className="py-2">Status</th>
                    <th className="py-2">Differences to explain</th>
                    <th className="py-2">Explained</th>
                    <th className="py-2">Info</th>
                  </tr>
                </thead>
                <tbody>
                  {group.rows.map((r) => (
                    <tr
                      key={r.workflowId}
                      className="border-b cursor-pointer hover:bg-[var(--color-surface)]"
                      onClick={() => setExpanded(expanded === r.workflowId ? null : r.workflowId)}
                    >
                      <td className="py-2">{r.periodKey}</td>
                      <td className="py-2">
                        {r.snapshots
                          .filter((s) => !s.superseded)
                          .map((s) => (
                            <span key={s.id} className="block">
                              {FORM_LABELS[s.formType]} · {SOURCE_LABELS[s.sourceKind]} ·{' '}
                              {new Date(s.importedAt).toLocaleDateString('en-IN')}
                            </span>
                          ))}
                        {r.snapshots.filter((s) => !s.superseded).length === 0 && '—'}
                      </td>
                      <td className="py-2">{STATUS_LABELS[r.status]}</td>
                      <td className="py-2">{r.materialOpen}</td>
                      <td className="py-2">{r.materialExplained}</td>
                      <td className="py-2">{r.info}</td>
                    </tr>
                  ))}
                  {group.rows.length === 0 && (
                    <tr>
                      <td colSpan={6} className="py-2 text-[var(--color-text-muted)]">
                        No periods
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </section>
          ))}

          {expanded &&
            rows
              .filter((r) => r.workflowId === expanded)
              .map((r) => <BaselinePeriodDetail key={r.workflowId} row={r} onRerun={refresh} />)}
        </div>
      </main>
    </AppShell>
  )
}
