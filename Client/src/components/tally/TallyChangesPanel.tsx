import { useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { AlertCircle, Loader2 } from 'lucide-react'
import { useAuthStore } from '@/store/authStore'
import { apiErrorMessage } from '@/lib/apiError'
import {
  exportTallyChanges,
  listTallyChanges,
  retryTallyChange,
  type TallyChangeItem,
  type TallyChangeStatus,
} from '@/lib/tallyChangesApi'

type TabId = 'pending' | 'exported' | 'failed' | 'history'

const TAB_STATUSES: Record<TabId, TallyChangeStatus[]> = {
  pending: ['PENDING'],
  exported: ['EXPORTED'],
  failed: ['FAILED'],
  history: ['APPLIED', 'SUPERSEDED'],
}

const TABS: { id: TabId; label: string }[] = [
  { id: 'pending', label: 'Pending' },
  { id: 'exported', label: 'Exported' },
  { id: 'failed', label: 'Failed' },
  { id: 'history', label: 'History' },
]

const STATUS_LABEL: Record<TallyChangeStatus, string> = {
  PENDING: 'Pending',
  EXPORTED: 'Exported',
  APPLIED: 'Applied',
  FAILED: 'Failed',
  SUPERSEDED: 'Superseded',
}

const STATUS_BADGE: Record<TallyChangeStatus, string> = {
  PENDING: 'text-[var(--color-warning)] bg-[var(--color-warning-bg)]',
  EXPORTED: 'text-[var(--color-info)] bg-[var(--color-info-bg)]',
  APPLIED: 'text-[var(--color-success)] bg-[var(--color-success-bg)]',
  FAILED: 'text-[var(--color-danger)] bg-[var(--color-danger-bg)]',
  SUPERSEDED: 'text-[var(--color-text-muted)] bg-[var(--color-surface-raised)]',
}

const EXPORT_ERROR_MESSAGE: Record<string, string> = {
  NO_MASTERS_UPLOAD: 'Upload a masters file before downloading the Tally import file.',
  STALE_CONFIRMATION_REQUIRED:
    'The latest masters upload is stale. Tick the confirmation box and try again.',
  NOTHING_ELIGIBLE:
    'No eligible ledger changes to export. Ledgers must be found or renamed in the latest masters upload.',
}

function filenameFrom(headers: Record<string, unknown> | undefined): string {
  const raw = headers?.['content-disposition']
  if (typeof raw !== 'string') return 'tally-pan-changes.json'
  const match = /filename="?([^";]+)"?/.exec(raw)
  return match?.[1] ?? 'tally-pan-changes.json'
}

function displayPan(value: string | null | undefined): string {
  return value && value.trim() !== '' ? value : 'No PAN'
}

function displayNewValue(value: string | null | undefined): string {
  return value && value.trim() !== '' ? value : 'Clears PAN'
}

async function exportErrorMessage(err: unknown): Promise<string> {
  const data = (err as { response?: { data?: unknown } })?.response?.data
  if (data instanceof Blob) {
    try {
      const parsed = JSON.parse(await data.text()) as { code?: string }
      if (parsed.code && EXPORT_ERROR_MESSAGE[parsed.code]) return EXPORT_ERROR_MESSAGE[parsed.code]
      if (parsed.code) return parsed.code
    } catch {
      // fall through to the generic message
    }
  }
  return apiErrorMessage(err, 'Could not download the Tally import file.')
}

export function TallyChangesPanel() {
  const queryClient = useQueryClient()
  const role = useAuthStore((st) => st.user?.role)
  const canWrite = role === 'ROLE_ACCOUNTANT' || role === 'ROLE_OWNER'

  const [activeTab, setActiveTab] = useState<TabId>('pending')
  const [confirmStale, setConfirmStale] = useState(false)
  const [downloading, setDownloading] = useState(false)
  const [downloadError, setDownloadError] = useState('')
  const [retryingId, setRetryingId] = useState<string | null>(null)
  const [retryError, setRetryError] = useState('')

  const changesQuery = useQuery({
    queryKey: ['tally-changes'],
    queryFn: () => listTallyChanges(),
  })

  const items = changesQuery.data?.items ?? []
  const mastersUpload = changesQuery.data?.mastersUpload ?? null
  const filtered = items.filter((item) => TAB_STATUSES[activeTab].includes(item.status))
  const countFor = (tab: TabId) =>
    items.filter((item) => TAB_STATUSES[tab].includes(item.status)).length

  const hasEligible = items.some(
    (item) =>
      (item.status === 'PENDING' || item.status === 'EXPORTED') &&
      (item.checkOutcome === 'FOUND' || item.checkOutcome === 'RENAMED'),
  )
  const stale = mastersUpload?.stale === true
  const downloadDisabled =
    downloading || !hasEligible || !mastersUpload || (stale && !confirmStale)

  async function handleDownload() {
    setDownloadError('')
    setDownloading(true)
    try {
      const res = await exportTallyChanges({ confirmStale: stale && confirmStale })
      const filename = filenameFrom(res.headers as Record<string, unknown> | undefined)
      const url = URL.createObjectURL(res.data as Blob)
      const anchor = document.createElement('a')
      anchor.href = url
      anchor.download = filename
      document.body.appendChild(anchor)
      anchor.click()
      anchor.remove()
      URL.revokeObjectURL(url)
    } catch (err) {
      setDownloadError(await exportErrorMessage(err))
    } finally {
      setDownloading(false)
    }
  }

  async function handleRetry(item: TallyChangeItem) {
    setRetryError('')
    setRetryingId(item.id)
    try {
      await retryTallyChange(item.id)
      await queryClient.invalidateQueries({ queryKey: ['tally-changes'] })
    } catch (err) {
      setRetryError(apiErrorMessage(err, `Could not retry ${item.ledgerName}.`))
    } finally {
      setRetryingId(null)
    }
  }

  return (
    <section className="flex flex-col gap-4">
      <div className="bg-[var(--color-surface)] border border-[var(--color-border)] rounded-[var(--radius-md)] p-4 shadow-[var(--shadow-sm)]">
        <h2 className="text-base font-semibold text-[var(--color-text-primary)] mb-2">
          Download PAN changes for Tally
        </h2>
        <div className="text-sm text-[var(--color-text-secondary)]">
          <p className="mb-2">Before importing the file into Tally:</p>
          <ol className="list-decimal pl-5 flex flex-col gap-1">
            <li>Back up the company.</li>
            <li>
              In Tally, press <span className="font-mono">Alt+O</span> and choose Import Masters.
            </li>
            <li>Import the file into the loaded company.</li>
            <li>Upload masters to confirm the imported changes.</li>
          </ol>
        </div>
        {mastersUpload === null ? (
          <p className="mt-3 text-sm text-[var(--color-danger)]" role="note">
            A masters upload is required before you can download the Tally import file.
          </p>
        ) : (
          <p className="mt-3 text-xs text-[var(--color-text-muted)]">
            Latest masters upload: {new Date(mastersUpload.uploadedAt).toLocaleString()}
          </p>
        )}
        {stale && mastersUpload && (
          <label className="mt-3 flex items-center gap-2 text-sm text-[var(--color-text-secondary)]">
            <input
              type="checkbox"
              checked={confirmStale}
              onChange={(e) => setConfirmStale(e.target.checked)}
            />
            The latest masters upload is more than 24 hours old. Tick to confirm you are exporting
            against it.
          </label>
        )}
        {canWrite && (
          <div className="mt-3">
            <button
              type="button"
              onClick={handleDownload}
              disabled={downloadDisabled}
              className="h-10 px-4 inline-flex items-center gap-2 rounded-[var(--radius-md)] bg-[var(--color-primary)] text-white text-sm hover:bg-[var(--color-primary-hover)] disabled:opacity-50 disabled:cursor-not-allowed"
            >
              {downloading ? (
                <>
                  <Loader2 size={16} className="animate-spin" /> Preparing…
                </>
              ) : (
                'Download Tally import file'
              )}
            </button>
          </div>
        )}
        {downloadError && (
          <p role="alert" className="mt-2 text-sm text-[var(--color-danger)]">
            {downloadError}
          </p>
        )}
      </div>

      <div role="tablist" className="flex border-b border-[var(--color-border)]">
        {TABS.map((tab) => (
          <button
            key={tab.id}
            role="tab"
            aria-selected={activeTab === tab.id}
            aria-controls={`tally-changes-${tab.id}-panel`}
            onClick={() => setActiveTab(tab.id)}
            className={`h-11 px-4 text-sm font-normal border-b-2 transition-colors flex items-center gap-2 ${
              activeTab === tab.id
                ? 'border-[var(--color-primary)] text-[var(--color-primary)]'
                : 'border-transparent text-[var(--color-text-secondary)]'
            }`}
          >
            {tab.label}
            {countFor(tab.id) > 0 && (
              <span className="px-2 py-0.5 rounded-full text-xs bg-[var(--color-surface-raised)] text-[var(--color-text-secondary)]">
                {countFor(tab.id)}
              </span>
            )}
          </button>
        ))}
      </div>

      <div id={`tally-changes-${activeTab}-panel`} role="tabpanel">
        {retryError && (
          <p role="alert" className="mb-2 text-sm text-[var(--color-danger)]">
            {retryError}
          </p>
        )}
        {changesQuery.isLoading ? (
          <div className="flex justify-center py-16">
            <Loader2 className="animate-spin text-[var(--color-text-muted)]" size={28} />
          </div>
        ) : changesQuery.isError ? (
          <p role="alert" className="text-sm text-[var(--color-danger)] py-8 text-center">
            {apiErrorMessage(changesQuery.error, 'Could not load Tally changes.')}
          </p>
        ) : filtered.length === 0 ? (
          <p className="text-sm text-[var(--color-text-muted)] py-10 text-center">
            No {TABS.find((tab) => tab.id === activeTab)?.label.toLowerCase()} changes.
          </p>
        ) : (
          <div className="bg-[var(--color-surface)] rounded-[var(--radius-lg)] border border-[var(--color-border)] shadow-[var(--shadow-sm)] overflow-x-auto">
            <table className="w-full">
              <thead>
                <tr>
                  {['Ledger', 'Tally PAN', 'New PAN', 'Status', 'Check', ''].map((col, i) => (
                    <th
                      key={col || `col-${i}`}
                      scope="col"
                      className="px-4 py-3 text-left text-[0.75rem] font-semibold uppercase tracking-[0.05em] text-[var(--color-text-secondary)]"
                    >
                      {col}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {filtered.map((item) => (
                  <tr
                    key={item.id}
                    className="border-t border-[var(--color-surface-raised)] align-top"
                  >
                    <td className="px-4 py-3 text-sm text-[var(--color-text-primary)] min-w-[200px]">
                      {item.currentTallyName ?? item.ledgerName}
                    </td>
                    <td className="px-4 py-3 text-sm font-mono text-[var(--color-text-secondary)]">
                      {displayPan(item.currentTallyPan)}
                    </td>
                    <td className="px-4 py-3 text-sm font-mono text-[var(--color-text-primary)]">
                      {displayNewValue(item.newValue)}
                    </td>
                    <td className="px-4 py-3">
                      <span
                        className={`text-xs px-2 py-0.5 rounded-full ${STATUS_BADGE[item.status]}`}
                      >
                        {STATUS_LABEL[item.status]}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-sm min-w-[220px]">
                      {item.checkOutcome === 'NOT_FOUND' ? (
                        <div className="flex items-start gap-1 text-[var(--color-danger)]">
                          <AlertCircle size={14} className="mt-0.5 flex-shrink-0" />
                          <span>
                            Ledger not found in the latest masters upload
                            {item.checkReason === 'NO_MASTERS_UPLOAD'
                              ? ' — upload a masters file to check whether this ledger still exists.'
                              : '.'}
                          </span>
                        </div>
                      ) : item.checkOutcome === 'RENAMED' && item.currentTallyName ? (
                        <span className="text-[var(--color-text-secondary)]">
                          Renamed to {item.currentTallyName}
                        </span>
                      ) : (
                        <span className="text-[var(--color-text-muted)]">—</span>
                      )}
                    </td>
                    <td className="px-4 py-3 text-right">
                      {canWrite && item.status === 'FAILED' && (
                        <button
                          type="button"
                          onClick={() => handleRetry(item)}
                          disabled={retryingId === item.id}
                          className="text-sm text-[var(--color-primary)] hover:underline disabled:opacity-50"
                        >
                          {retryingId === item.id ? 'Retrying…' : 'Retry'}
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </section>
  )
}
