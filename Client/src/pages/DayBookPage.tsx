import { useState, useEffect } from 'react'
import { AppShell } from '@/components/AppShell'
import { GateLockedBanner } from '@/components/GateLocked'
import { api } from '@/lib/api'
import { UploadCloud, FileJson, X, CheckCircle2, AlertCircle } from 'lucide-react'

interface VoucherTypeSummaryRow {
  voucherTypeName: string
  count: number
  totalDebit: number
  totalCredit: number
  minDate: string | null
  maxDate: string | null
}

interface UploadResult {
  id: string
  fileName: string
  status: 'COMPLETED' | 'FAILED'
  totalVouchersParsed: number
  errorMessage: string | null
  voucherSummary: VoucherTypeSummaryRow[]
}

interface GateStatus {
  gated: boolean
  reason?: string
  unresolvedCount?: number
}

const VOUCHER_TYPE_ORDER = [
  'Purchase', 'Sales', 'Journal', 'Payment',
  'Receipt', 'Contra', 'Credit Note', 'Debit Note',
]

const formatAmount = (amount: number): string =>
  new Intl.NumberFormat('en-IN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(amount)

export function DayBookPage() {
  const [selectedFile, setSelectedFile] = useState<File | null>(null)
  const [dragOver, setDragOver] = useState(false)
  const [uploading, setUploading] = useState(false)
  const [uploadResult, setUploadResult] = useState<UploadResult | null>(null)
  const [uploadError, setUploadError] = useState('')
  const [dropZoneError, setDropZoneError] = useState('')
  const [gateStatus, setGateStatus] = useState<GateStatus | null>(null)
  const [gateLoading, setGateLoading] = useState(true)

  useEffect(() => {
    const loadGateStatus = async () => {
      setGateLoading(true)
      try {
        const res = await api.get('/v1/masters/gate-status')
        setGateStatus(res.data)
      } catch {
        // Fail open — gate check error does not block the page
      } finally {
        setGateLoading(false)
      }
    }
    loadGateStatus()
  }, [])

  const handleDrop = (e: React.DragEvent<HTMLDivElement>) => {
    e.preventDefault()
    if (gateStatus?.gated) return
    setDragOver(false)
    const file = e.dataTransfer.files[0]
    if (!file) return
    if (!file.name.toLowerCase().endsWith('.json')) {
      setDropZoneError('Only .json files are supported. Please select a Tally JSON export.')
      setTimeout(() => setDropZoneError(''), 3000)
      return
    }
    if (file.size > 50 * 1024 * 1024) {
      setDropZoneError('File exceeds the 50 MB limit. Please use a smaller file.')
      setTimeout(() => setDropZoneError(''), 3000)
      return
    }
    setSelectedFile(file)
    setUploadResult(null)
    setUploadError('')
  }

  const handleFileInput = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (!file) return
    setSelectedFile(file)
    setUploadResult(null)
    setUploadError('')
  }

  const handleUpload = async () => {
    if (!selectedFile) return
    setUploading(true)
    setUploadError('')
    try {
      const formData = new FormData()
      formData.append('file', selectedFile)
      const res = await api.post('/v1/day-book/upload', formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
      })
      setUploadResult(res.data)
    } catch (err: unknown) {
      const axiosErr = err as { response?: { data?: { error?: string; errorMessage?: string } } }
      const message =
        axiosErr?.response?.data?.error ||
        axiosErr?.response?.data?.errorMessage ||
        'Could not connect to the server. Check your connection and try again.'
      setUploadError(message)
    } finally {
      setUploading(false)
    }
  }

  return (
    <AppShell>
      <main className="flex-1 overflow-y-auto p-6">
        {gateLoading && (
          <div className="h-12 bg-[var(--color-surface-raised)] rounded-[var(--radius-md)] animate-pulse mb-6" />
        )}

        {!gateLoading && gateStatus?.gated && (
          <GateLockedBanner
            unresolvedCount={gateStatus.unresolvedCount ?? 0}
            reason={gateStatus.reason ?? 'Unresolved masters findings must be resolved before uploading the day book.'}
          />
        )}

        <div className="bg-[var(--color-surface)] rounded-[var(--radius-lg)] border border-[var(--color-border)] p-6 shadow-[var(--shadow-sm)] mb-6">
          <div
            role="button"
            tabIndex={0}
            aria-label={
              selectedFile
                ? `Tally JSON file selected: ${selectedFile.name}. Click Upload Day Book to proceed.`
                : 'Upload Tally JSON file — click or drag to add file'
            }
            onDrop={handleDrop}
            onDragOver={(e) => {
              e.preventDefault()
              if (!gateStatus?.gated) setDragOver(true)
            }}
            onDragLeave={() => setDragOver(false)}
            onKeyDown={(e) => {
              if (gateStatus?.gated) return
              if (e.key === 'Enter' || e.key === ' ')
                document.getElementById('file-input')?.click()
            }}
            className={[
              'min-h-[160px] flex flex-col items-center justify-center gap-3 py-10 px-6',
              'rounded-[var(--radius-md)] transition-colors cursor-pointer',
              gateStatus?.gated ? 'cursor-not-allowed opacity-75' : '',
              dropZoneError ? 'border-2 border-[var(--color-danger)]' : '',
              !dropZoneError && !selectedFile && !dragOver
                ? 'border-2 border-dashed border-[var(--color-border)] bg-[var(--color-bg)]'
                : '',
              dragOver ? 'border-2 border-dashed border-[var(--color-primary)] bg-[var(--color-primary-light)]' : '',
              selectedFile && !dragOver && !dropZoneError
                ? 'border border-[var(--color-border-strong)] bg-[var(--color-surface)]'
                : '',
            ].join(' ')}
            onClick={() => !selectedFile && !gateStatus?.gated && document.getElementById('file-input')?.click()}
          >
            {uploading ? (
              <p className="text-sm text-[var(--color-text-secondary)]">Uploading…</p>
            ) : dropZoneError ? (
              <p className="text-sm text-[var(--color-danger)]">{dropZoneError}</p>
            ) : selectedFile ? (
              <>
                <FileJson size={40} className="text-[var(--color-success)]" />
                <p className="text-sm font-medium text-[var(--color-text-primary)] truncate max-w-[40ch]">
                  {selectedFile.name}
                </p>
                <p className="text-xs text-[var(--color-text-muted)]">
                  ({(selectedFile.size / (1024 * 1024)).toFixed(1)} MB)
                </p>
                <button
                  aria-label={`Remove selected file ${selectedFile.name}`}
                  onClick={(e) => {
                    e.stopPropagation()
                    setSelectedFile(null)
                    setUploadResult(null)
                    setUploadError('')
                  }}
                  className="h-8 px-3 flex items-center gap-1 text-sm text-[var(--color-text-secondary)] border border-[var(--color-border)] rounded-[var(--radius-sm)] hover:bg-[var(--color-surface-raised)]"
                >
                  <X size={14} /> Remove
                </button>
              </>
            ) : (
              <>
                <UploadCloud size={40} className="text-[var(--color-text-muted)]" />
                <p className="text-sm text-[var(--color-text-secondary)]">
                  Drag and drop your Tally JSON file here
                </p>
                <p className="text-xs text-[var(--color-text-muted)]">or</p>
                <button
                  aria-label="Browse for Tally JSON file"
                  disabled={gateStatus?.gated}
                  onClick={(e) => {
                    e.stopPropagation()
                    if (gateStatus?.gated) {

                      return
                    }
                    document.getElementById('file-input')?.click()
                  }}
                  className="h-9 px-4 text-sm border border-[var(--color-border)] rounded-[var(--radius-md)] hover:bg-[var(--color-surface-raised)] disabled:opacity-50 disabled:cursor-not-allowed"
                >
                  Browse file
                </button>
                <p className="text-xs text-[var(--color-text-muted)]">
                  Supported format: .json · Max size: 50 MB
                </p>
              </>
            )}
          </div>

          <input
            id="file-input"
            type="file"
            accept=".json"
            className="hidden"
            onChange={handleFileInput}
          />

          <div className="flex justify-end mt-4">
            <button
              aria-label={uploading ? 'Uploading Day Book file, please wait' : 'Upload Day Book'}
              disabled={!selectedFile || uploading || !!gateStatus?.gated}
              onClick={handleUpload}
              className="h-11 px-6 bg-[var(--color-primary)] text-white rounded-[var(--radius-md)] hover:bg-[var(--color-primary-hover)] text-sm disabled:opacity-50 disabled:cursor-not-allowed"
            >
              {uploading ? 'Uploading…' : 'Upload Day Book'}
            </button>
          </div>

          {uploadResult?.status === 'COMPLETED' && (
            <div className="mt-4 bg-[var(--color-success-bg)] border border-[var(--color-success)]/20 rounded-[var(--radius-md)] p-4 flex items-start gap-3">
              <CheckCircle2 size={18} className="text-[var(--color-success)] flex-shrink-0 mt-0.5" />
              <div>
                <p className="text-sm font-semibold text-[var(--color-success)]">Upload successful</p>
                <p className="text-sm text-[var(--color-text-secondary)]">
                  Parsed {uploadResult.totalVouchersParsed} voucher
                  {uploadResult.totalVouchersParsed !== 1 ? 's' : ''} across{' '}
                  {uploadResult.voucherSummary.filter((r) => r.count > 0).length} type
                  {uploadResult.voucherSummary.filter((r) => r.count > 0).length !== 1 ? 's' : ''}.
                </p>
                <p className="text-xs font-mono text-[var(--color-text-muted)] mt-1">
                  Job ID: {uploadResult.id}
                </p>
              </div>
            </div>
          )}

          {(uploadResult?.status === 'FAILED' || uploadError) && (
            <div className="mt-4 bg-[var(--color-danger-bg)] border border-[var(--color-danger)]/20 rounded-[var(--radius-md)] p-4 flex items-start gap-3">
              <AlertCircle size={18} className="text-[var(--color-danger)] flex-shrink-0 mt-0.5" />
              <div>
                <p className="text-sm font-semibold text-[var(--color-danger)]">Upload failed</p>
                <p className="text-sm text-[var(--color-text-secondary)]">
                  {uploadResult?.errorMessage || uploadError}
                </p>
                <button
                  onClick={() => {
                    setSelectedFile(null)
                    setUploadResult(null)
                    setUploadError('')
                  }}
                  className="text-sm text-[var(--color-primary)] hover:underline mt-2"
                >
                  Try again
                </button>
              </div>
            </div>
          )}
        </div>

        {uploadResult?.status === 'COMPLETED' && (
          <section>
            <div className="flex items-center justify-between mb-4">
              <h2 className="text-xl font-semibold text-[var(--color-text-primary)]">
                Voucher Summary
              </h2>
            </div>
            <div className="bg-[var(--color-surface)] rounded-[var(--radius-lg)] border border-[var(--color-border)] shadow-[var(--shadow-sm)] overflow-x-auto">
              <table className="w-full">
                <thead className="sticky top-0 z-[var(--z-sticky)] bg-[var(--color-bg)]">
                  <tr>
                    {['Voucher Type', 'Count', 'Total Debit (₹)', 'Total Credit (₹)', 'Date Range'].map(
                      (col) => (
                        <th
                          key={col}
                          scope="col"
                          className="px-4 py-3 text-left text-[0.75rem] font-semibold uppercase tracking-[0.05em] text-[var(--color-text-secondary)]"
                          style={
                            col === 'Count' || col.startsWith('Total')
                              ? { textAlign: 'right' }
                              : {}
                          }
                        >
                          {col}
                        </th>
                      )
                    )}
                  </tr>
                </thead>
                <tbody>
                  {VOUCHER_TYPE_ORDER.map((typeName) => {
                    const row = uploadResult.voucherSummary.find(
                      (r) => r.voucherTypeName === typeName
                    )
                    return (
                      <tr
                        key={typeName}
                        className="h-12 border-t border-[var(--color-surface-raised)] hover:bg-[var(--color-surface-raised)]"
                      >
                        <td className="px-4 py-2 text-sm text-[var(--color-text-primary)]">
                          {typeName}
                        </td>
                        <td className="px-4 py-2 text-sm font-mono text-[var(--color-text-primary)] text-right">
                          {row ? row.count : 0}
                        </td>
                        <td className="px-4 py-2 text-sm font-mono text-[var(--color-text-primary)] text-right">
                          {formatAmount(row ? row.totalDebit : 0)}
                        </td>
                        <td className="px-4 py-2 text-sm font-mono text-[var(--color-text-primary)] text-right">
                          {formatAmount(row ? row.totalCredit : 0)}
                        </td>
                        <td className="px-4 py-2 text-xs font-mono text-[var(--color-text-muted)]">
                          {row && row.minDate && row.maxDate
                            ? `${row.minDate} – ${row.maxDate}`
                            : '—'}
                        </td>
                      </tr>
                    )
                  })}
                  <tr className="h-12 border-t border-[var(--color-surface-raised)] bg-[var(--color-surface-raised)]">
                    <th
                      scope="row"
                      className="px-4 py-2 text-sm font-semibold text-[var(--color-text-primary)] text-left"
                    >
                      Total
                    </th>
                    <td className="px-4 py-2 text-sm font-semibold font-mono text-[var(--color-text-primary)] text-right">
                      {uploadResult.voucherSummary.reduce((sum, r) => sum + r.count, 0)}
                    </td>
                    <td className="px-4 py-2 text-sm font-semibold font-mono text-[var(--color-text-primary)] text-right">
                      {formatAmount(
                        uploadResult.voucherSummary.reduce((sum, r) => sum + r.totalDebit, 0)
                      )}
                    </td>
                    <td className="px-4 py-2 text-sm font-semibold font-mono text-[var(--color-text-primary)] text-right">
                      {formatAmount(
                        uploadResult.voucherSummary.reduce((sum, r) => sum + r.totalCredit, 0)
                      )}
                    </td>
                    <td className="px-4 py-2 text-xs text-[var(--color-text-muted)]">—</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </section>
        )}
      </main>
    </AppShell>
  )
}
