import { Fragment, useEffect, useState } from 'react'
import { Link } from '@tanstack/react-router'
import { AppShell } from '@/components/AppShell'
import { UploadChangeList } from '@/components/snapshot/UploadChangeList'
import { api } from '@/lib/api'

interface UploadHistoryRow {
  id: string
  fileName: string
  status: 'COMPLETED' | 'FAILED'
  totalVouchersParsed: number
  coverageFrom: string | null
  coverageTo: string | null
  tamperChangeCount?: number | null
  createdAt: string
}

export function DayBookPage() {
  const [history, setHistory] = useState<UploadHistoryRow[]>([])
  const [expandedId, setExpandedId] = useState<string | null>(null)

  useEffect(() => {
    const loadHistory = async () => {
      try {
        const res = await api.get('/v1/day-book/uploads', { params: { page: 0, size: 20 } })
        setHistory(res.data.content ?? [])
      } catch {
        // History is informational; a load failure does not block the page
      }
    }
    loadHistory()
  }, [])

  return (
    <AppShell>
      <main className="flex-1 overflow-y-auto p-6">
        <div className="bg-[var(--color-surface)] rounded-[var(--radius-lg)] border border-[var(--color-border)] p-6 shadow-[var(--shadow-sm)] mb-6">
          <h1 className="text-xl font-semibold text-[var(--color-text-primary)]">Day Book</h1>
          <p className="mt-2 text-sm text-[var(--color-text-secondary)]">
            Uploads now happen inside a workflow.{' '}
            <Link to="/workflows" className="text-[var(--color-primary)] hover:underline">
              Go to Workflows
            </Link>
          </p>
        </div>

        <section className="mt-8">
          <h2 className="text-xl font-semibold text-[var(--color-text-primary)] mb-4">
            Previous Uploads
          </h2>
          <div className="bg-[var(--color-surface)] rounded-[var(--radius-lg)] border border-[var(--color-border)] shadow-[var(--shadow-sm)] overflow-x-auto">
            <table className="w-full">
              <thead>
                <tr>
                  {['File', 'Uploaded', 'Coverage', 'Vouchers', 'Status'].map((col) => (
                    <th
                      key={col}
                      scope="col"
                      className="px-4 py-3 text-left text-[0.75rem] font-semibold uppercase tracking-[0.05em] text-[var(--color-text-secondary)]"
                    >
                      {col}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {history.map((row) => (
                  <Fragment key={row.id}>
                    <tr className="h-12 border-t border-[var(--color-surface-raised)] hover:bg-[var(--color-surface-raised)]">
                      <td className="px-4 py-2 text-sm text-[var(--color-text-primary)]">
                        {row.fileName}
                        {row.tamperChangeCount != null && row.tamperChangeCount > 0 && (
                          <button
                            type="button"
                            aria-expanded={expandedId === row.id}
                            onClick={() => setExpandedId(expandedId === row.id ? null : row.id)}
                            className="ml-2 text-xs px-2 py-0.5 rounded-full bg-[var(--color-info-bg)] text-[var(--color-info)]"
                          >
                            {row.tamperChangeCount} changes
                          </button>
                        )}
                      </td>
                      <td className="px-4 py-2 text-sm text-[var(--color-text-secondary)]">
                        {new Date(row.createdAt).toLocaleString()}
                      </td>
                      <td className="px-4 py-2 text-sm font-mono text-[var(--color-text-secondary)]">
                        {row.coverageFrom && row.coverageTo ? `${row.coverageFrom} – ${row.coverageTo}` : '—'}
                      </td>
                      <td className="px-4 py-2 text-sm font-mono text-[var(--color-text-primary)] text-right">
                        {row.totalVouchersParsed ?? 0}
                      </td>
                      <td className="px-4 py-2 text-sm text-[var(--color-text-secondary)]">{row.status}</td>
                    </tr>
                    {expandedId === row.id && (
                      <tr>
                        <td colSpan={5} className="px-4 py-3 bg-[var(--color-surface-raised)]">
                          <UploadChangeList uploadId={row.id} />
                        </td>
                      </tr>
                    )}
                  </Fragment>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      </main>
    </AppShell>
  )
}
