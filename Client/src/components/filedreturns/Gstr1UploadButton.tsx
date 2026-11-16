import { useRef, useState } from 'react'
import { apiErrorMessage } from '@/lib/apiError'
import { uploadGstr1Json } from '@/lib/filedReturnsApi'

export function Gstr1UploadButton({ onUploaded }: { onUploaded: () => void }) {
  const inputRef = useRef<HTMLInputElement>(null)
  const [form, setForm] = useState<'GSTR1' | 'IFF'>('GSTR1')
  const [error, setError] = useState<string | null>(null)
  const [uploading, setUploading] = useState(false)
  const [selected, setSelected] = useState<File | null>(null)

  async function handleUpload() {
    if (!selected) return
    setError(null)
    setUploading(true)
    try {
      await uploadGstr1Json(selected, form)
      setSelected(null)
      if (inputRef.current) inputRef.current.value = ''
      onUploaded()
    } catch (err) {
      setError(apiErrorMessage(err, 'Could not import the GSTR-1 JSON'))
    } finally {
      setUploading(false)
    }
  }

  return (
    <div className="flex flex-col gap-1">
      <span className="text-sm font-medium">Import GSTR-1 JSON</span>
      <div className="flex items-center gap-2 text-sm">
        <input
          ref={inputRef}
          type="file"
          accept=".json"
          aria-label="Import GSTR-1 JSON file"
          onChange={(e) => setSelected(e.target.files?.[0] ?? null)}
        />
        <label className="flex items-center gap-1">
          <input
            type="radio"
            name="gstr1-form"
            checked={form === 'GSTR1'}
            onChange={() => setForm('GSTR1')}
          />
          GSTR-1
        </label>
        <label className="flex items-center gap-1">
          <input
            type="radio"
            name="gstr1-form"
            checked={form === 'IFF'}
            onChange={() => setForm('IFF')}
          />
          IFF
        </label>
        <button
          type="button"
          className="px-3 py-1.5 text-sm rounded bg-[var(--color-primary)] text-white disabled:opacity-50"
          disabled={!selected || uploading}
          onClick={handleUpload}
        >
          Upload
        </button>
      </div>
      {error && (
        <p role="alert" className="text-sm text-red-600">
          {error}
        </p>
      )}
    </div>
  )
}
