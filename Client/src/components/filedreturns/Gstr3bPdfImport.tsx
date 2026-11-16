import { useRef, useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { apiErrorMessage } from '@/lib/apiError'
import { parseGstr3bPdf, type Gstr3bParseResponse } from '@/lib/filedReturnsApi'
import { Gstr3bEntryForm } from './Gstr3bEntryForm'

type FormState = {
  initial?: Gstr3bParseResponse
  parseError?: string
  fileId?: string
  parserVersion?: string
  filingPeriod: string
}

export function Gstr3bPdfImport({ onSaved }: { onSaved: () => void }) {
  const queryClient = useQueryClient()
  const inputRef = useRef<HTMLInputElement>(null)
  const [parsing, setParsing] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [formState, setFormState] = useState<FormState | null>(null)

  async function handleFile(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0]
    if (!file) return
    setError(null)
    setParsing(true)
    try {
      const parsed = await parseGstr3bPdf(file)
      setFormState({ initial: parsed, filingPeriod: parsed.filingPeriod })
    } catch (err) {
      const data = (err as { response?: { data?: { message?: string; fileId?: string } } })
        ?.response?.data
      setFormState({
        parseError:
          data?.message ??
          apiErrorMessage(err, 'Could not read a GSTR-3B from this PDF; enter the values manually'),
        fileId: data?.fileId,
        filingPeriod: '',
      })
    } finally {
      setParsing(false)
      if (inputRef.current) inputRef.current.value = ''
    }
  }

  return (
    <div className="flex flex-col gap-1">
      <span className="text-sm font-medium">Import GSTR-3B PDF</span>
      <input
        ref={inputRef}
        type="file"
        accept=".pdf"
        aria-label="Import GSTR-3B PDF file"
        onChange={handleFile}
        disabled={parsing}
      />
      {parsing && <p className="text-sm text-[var(--color-text-muted)]">Reading PDF…</p>}
      {error && (
        <p role="alert" className="text-sm text-red-600">
          {error}
        </p>
      )}
      {formState && (
        <div className="border rounded p-3">
          <Gstr3bEntryForm
            filingPeriod={formState.filingPeriod}
            initial={formState.initial}
            parseError={formState.parseError}
            fileId={formState.fileId}
            onSaved={() => {
              setFormState(null)
              onSaved()
              queryClient.invalidateQueries({ queryKey: ['baseline-periods'] })
            }}
            onCancel={() => setFormState(null)}
          />
        </div>
      )}
    </div>
  )
}
