import { useMemo, useState } from 'react'
import { apiErrorMessage } from '@/lib/apiError'
import { saveGstr3b, GSTR3B_ROWS, type Gstr3bParseResponse, type Gstr3bRow } from '@/lib/filedReturnsApi'

const AMOUNT_COLS = ['taxable', 'igst', 'cgst', 'sgst', 'cess'] as const

export function Gstr3bEntryForm({
  filingPeriod: initialPeriod,
  initial,
  parseError,
  fileId,
  onSaved,
  onCancel,
}: {
  filingPeriod: string
  initial?: Gstr3bParseResponse
  parseError?: string
  fileId?: string
  onSaved: () => void
  onCancel?: () => void
}) {
  const [filingPeriod, setFilingPeriod] = useState(initial?.filingPeriod ?? initialPeriod)
  const [values, setValues] = useState<Record<string, Record<string, string>>>(() => {
    const seed: Record<string, Record<string, string>> = {}
    if (initial) {
      for (const row of initial.rows) {
        seed[row.rowCode] = {}
        for (const col of AMOUNT_COLS) {
          const v = row[col]
          seed[row.rowCode][col] = v === null || v === undefined ? '' : String(v)
        }
      }
    }
    return seed
  })
  const [error, setError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)

  const warningsByCode = useMemo(() => {
    const map = new Map<string, string>()
    for (const w of initial?.warnings ?? []) {
      const code = w.match(/^Row (\S+):/)?.[1]
      if (code) map.set(code, w)
    }
    return map
  }, [initial])

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    setError(null)
    setSaving(true)
    const rows: Gstr3bRow[] = []
    for (const def of GSTR3B_ROWS) {
      const entered = values[def.code] ?? {}
      const row: Gstr3bRow = {
        rowCode: def.code,
        taxable: null,
        igst: null,
        cgst: null,
        sgst: null,
        cess: null,
      }
      let any = false
      for (const col of def.cols) {
        const raw = entered[col] ?? ''
        if (raw !== '') {
          const n = Number(raw)
          if (!Number.isFinite(n) || n < 0) {
            setError(`Row ${def.code}: "${raw}" is not a valid amount`)
            setSaving(false)
            return
          }
          row[col] = n
          any = true
        }
      }
      if (any) rows.push(row)
    }
    try {
      await saveGstr3b({
        filingPeriod,
        gstin: initial?.gstin ?? null,
        fileId: initial?.fileId ?? fileId,
        parserVersion: initial?.parserVersion,
        rows,
      })
      onSaved()
    } catch (err) {
      setError(apiErrorMessage(err, 'Could not save the GSTR-3B entry'))
    } finally {
      setSaving(false)
    }
  }

  function setValue(code: string, col: string, value: string) {
    setValues((prev) => ({
      ...prev,
      [code]: { ...(prev[code] ?? {}), [col]: value },
    }))
  }

  return (
    <form onSubmit={handleSubmit} className="space-y-3" data-testid="gstr3b-entry-form">
      {parseError && (
        <p role="alert" className="text-sm text-red-600">
          {parseError}
        </p>
      )}
      <div className="flex items-center gap-4 text-sm">
        <span>
          GSTIN: <strong>{initial?.gstin ?? '—'}</strong>
        </span>
        {initial ? (
          <span>
            Period: <strong>{filingPeriod}</strong>
          </span>
        ) : (
          <label className="flex items-center gap-2">
            Filing period
            <input
              type="month"
              value={filingPeriod}
              onChange={(e) => setFilingPeriod(e.target.value)}
              className="border rounded px-2 py-1"
            />
          </label>
        )}
      </div>
      <table className="w-full text-sm">
        <thead>
          <tr className="text-left border-b">
            <th className="py-2">Row</th>
            <th className="py-2">Taxable</th>
            <th className="py-2">IGST</th>
            <th className="py-2">CGST</th>
            <th className="py-2">SGST</th>
            <th className="py-2">Cess</th>
          </tr>
        </thead>
        <tbody>
          {GSTR3B_ROWS.map((def) => {
            const warning = warningsByCode.get(def.code)
            const row = values[def.code] ?? {}
            return (
              <tr
                key={def.code}
                className={warning ? 'border-b border-amber-300 bg-amber-50' : 'border-b'}
              >
                <td className="py-2">
                  {def.label}
                  {warning && (
                    <span className="block text-xs text-amber-700">{warning}</span>
                  )}
                </td>
                {AMOUNT_COLS.map((col) => (
                  <td key={col} className="py-2">
                    {def.cols.includes(col) ? (
                      <input
                        aria-label={`${def.code} ${col}`}
                        inputMode="decimal"
                        value={row[col] ?? ''}
                        onChange={(e) => setValue(def.code, col, e.target.value)}
                        className="border rounded px-2 py-1 w-28 text-sm"
                      />
                    ) : (
                      <span className="text-[var(--color-text-muted)]">—</span>
                    )}
                  </td>
                ))}
              </tr>
            )
          })}
        </tbody>
      </table>
      {error && (
        <p role="alert" className="text-sm text-red-600">
          {error}
        </p>
      )}
      <button
        type="submit"
        className="px-3 py-1.5 text-sm rounded bg-[var(--color-primary)] text-white disabled:opacity-50"
        disabled={saving}
      >
        Save GSTR-3B
      </button>
      {onCancel && (
        <button
          type="button"
          className="mt-2 text-sm text-[var(--color-text-muted)] underline"
          onClick={onCancel}
        >
          Cancel
        </button>
      )}
    </form>
  )
}
