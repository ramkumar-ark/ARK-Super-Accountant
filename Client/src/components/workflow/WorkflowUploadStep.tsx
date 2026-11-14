import { useEffect, useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { Loader2, Upload } from 'lucide-react'
import { apiErrorMessage } from '@/lib/apiError'
import {
  invalidateWorkflow,
  uploadToWorkflow,
  bindExistingUpload,
  type UploadHistoryRow,
} from '@/lib/workflowApi'

const inputClass =
  'w-full h-11 px-3 rounded-[var(--radius-md)] border border-[var(--color-border)] bg-[var(--color-surface-raised)] text-sm text-[var(--color-text-primary)] focus:outline-none focus:border-[var(--color-primary)] focus:ring-2 focus:ring-[var(--color-primary-subtle)] transition-colors'

export function WorkflowUploadStep({
  workflowId,
  requiredFrom,
  requiredTo,
  uploads,
  beforePrepare,
}: {
  workflowId: string
  requiredFrom: string
  requiredTo: string
  uploads: UploadHistoryRow[]
  beforePrepare: boolean
}) {
  const queryClient = useQueryClient()
  const [file, setFile] = useState<File | null>(null)
  const [coverageFrom, setCoverageFrom] = useState(requiredFrom)
  const [coverageTo, setCoverageTo] = useState(requiredTo)
  const [uploading, setUploading] = useState(false)
  const [usingUpload, setUsingUpload] = useState(false)
  const [warnings, setWarnings] = useState<string[]>([])
  const [error, setError] = useState('')

  useEffect(() => {
    setCoverageFrom(requiredFrom)
  }, [requiredFrom])

  useEffect(() => {
    setCoverageTo(requiredTo)
  }, [requiredTo])

  const latestCompleted = uploads.find((row) => row.status === 'COMPLETED')

  const handleFileChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    const selected = event.target.files?.[0]
    if (!selected) return
    if (!selected.name.toLowerCase().endsWith('.json')) {
      setError('Only .json files are supported. Please select a Tally JSON export.')
      setFile(null)
      return
    }
    if (selected.size > 50 * 1024 * 1024) {
      setError('File exceeds the 50 MB limit. Please use a smaller file.')
      setFile(null)
      return
    }
    setError('')
    setWarnings([])
    setFile(selected)
  }

  const handleUpload = async () => {
    if (!file) return
    setUploading(true)
    setError('')
    try {
      const result = await uploadToWorkflow(workflowId, file, coverageFrom, coverageTo)
      setWarnings(result.warnings ?? [])
      setFile(null)
      await invalidateWorkflow(queryClient, workflowId)
    } catch (err) {
      setError(
        apiErrorMessage(err, 'Could not upload the day book. Check your connection and try again.'),
      )
    } finally {
      setUploading(false)
    }
  }

  const handleUseLatest = async () => {
    if (!latestCompleted) return
    setUsingUpload(true)
    setError('')
    try {
      await bindExistingUpload(workflowId, latestCompleted.id)
      await invalidateWorkflow(queryClient, workflowId)
    } catch (err) {
      setError(apiErrorMessage(err, 'Could not use the latest upload. Try again.'))
    } finally {
      setUsingUpload(false)
    }
  }

  return (
    <section className="flex flex-col gap-4 rounded-[var(--radius-lg)] border border-[var(--color-border)] bg-[var(--color-surface)] p-4">
      <div>
        <h2 className="text-sm font-semibold text-[var(--color-text-primary)]">Day book upload</h2>
        {beforePrepare && (
          <p className="mt-1 text-xs text-[var(--color-text-muted)]">
            Replacing the upload returns the workflow to Data uploaded.
          </p>
        )}
      </div>

      <div className="grid gap-3 sm:grid-cols-2">
        <label className="flex flex-col gap-1 text-sm text-[var(--color-text-secondary)]">
          Coverage from
          <input
            type="date"
            value={coverageFrom}
            onChange={(e) => setCoverageFrom(e.target.value)}
            className={inputClass}
          />
        </label>
        <label className="flex flex-col gap-1 text-sm text-[var(--color-text-secondary)]">
          Coverage to
          <input
            type="date"
            value={coverageTo}
            onChange={(e) => setCoverageTo(e.target.value)}
            className={inputClass}
          />
        </label>
      </div>

      <label className="flex flex-col gap-1 text-sm text-[var(--color-text-secondary)]">
        Day book file
        <input
          type="file"
          accept=".json,application/json"
          data-testid="workflow-file-input"
          onChange={handleFileChange}
          className="text-sm text-[var(--color-text-secondary)]"
        />
      </label>

      <div className="flex flex-wrap items-center gap-3">
        <button
          type="button"
          onClick={handleUpload}
          disabled={!file || uploading}
          className="inline-flex h-10 items-center gap-2 rounded-[var(--radius-md)] bg-[var(--color-primary)] px-4 text-sm font-medium text-white hover:bg-[var(--color-primary-hover)] disabled:cursor-not-allowed disabled:opacity-50"
        >
          {uploading ? <Loader2 size={16} className="animate-spin" /> : <Upload size={16} />}
          Upload and bind
        </button>

        {latestCompleted && (
          <button
            type="button"
            onClick={handleUseLatest}
            disabled={usingUpload}
            className="inline-flex h-10 items-center gap-2 rounded-[var(--radius-md)] border border-[var(--color-border-strong)] px-4 text-sm font-medium text-[var(--color-text-primary)] hover:bg-[var(--color-surface-raised)] disabled:cursor-not-allowed disabled:opacity-50"
          >
            Use latest upload ({latestCompleted.fileName})
          </button>
        )}
      </div>

      {error && (
        <p role="alert" className="text-sm text-[var(--color-danger)]">
          {error}
        </p>
      )}

      {warnings.length > 0 && (
        <ul role="status" className="flex flex-col gap-1 text-sm text-[var(--color-text-secondary)]">
          {warnings.map((warning) => (
            <li key={warning}>{warning}</li>
          ))}
        </ul>
      )}
    </section>
  )
}
