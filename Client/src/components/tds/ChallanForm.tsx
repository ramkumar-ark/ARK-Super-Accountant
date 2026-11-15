import { useState, type FormEvent } from 'react'
import type { ChallanRequest, TdsChallan } from '@/lib/tdsApi'

const inputClass =
  'w-full h-10 px-3 rounded-[var(--radius-md)] border border-[var(--color-border)] bg-[var(--color-surface-raised)] text-sm text-[var(--color-text-primary)] focus:outline-none focus:border-[var(--color-primary)] focus:ring-2 focus:ring-[var(--color-primary-subtle)] transition-colors'

interface Props {
  initial?: TdsChallan
  onCancel: () => void
  onSubmit: (req: ChallanRequest) => void
}

function numberOrNull(value: string): number | null {
  return value.trim() === '' ? null : Number(value)
}

export function ChallanForm({ initial, onCancel, onSubmit }: Props) {
  const [bsrCode, setBsrCode] = useState(initial?.bsrCode ?? '')
  const [depositDate, setDepositDate] = useState(initial?.depositDate ?? '')
  const [serial, setSerial] = useState(initial?.challanSerialNumber ?? '')
  const [cin, setCin] = useState(initial?.cin ?? '')
  const [tdsAmount, setTdsAmount] = useState(String(initial?.tdsAmount ?? ''))
  const [surcharge, setSurcharge] = useState(String(initial?.surcharge ?? ''))
  const [educationCess, setEducationCess] = useState(String(initial?.educationCess ?? ''))
  const [interest, setInterest] = useState(String(initial?.interest ?? ''))
  const [penalty, setPenalty] = useState(String(initial?.penalty ?? ''))
  const [nilChallan, setNilChallan] = useState(initial?.nilChallan ?? false)
  const [clientError, setClientError] = useState('')

  const amounts = [tdsAmount, surcharge, educationCess, interest, penalty]

  function submit(e: FormEvent) {
    e.preventDefault()
    if (!/^\d{7}$/.test(bsrCode)) {
      setClientError('BSR code must be exactly 7 digits')
      return
    }
    if (!/^\d{1,5}$/.test(serial)) {
      setClientError('Challan serial number must be 1 to 5 digits')
      return
    }
    if (!depositDate) {
      setClientError('Deposit date is required')
      return
    }
    if (amounts.some((a) => a.trim() !== '' && Number(a) < 0)) {
      setClientError('Amounts must be zero or positive')
      return
    }
    setClientError('')
    onSubmit({
      bsrCode,
      depositDate,
      challanSerialNumber: serial,
      cin: cin.trim() === '' ? null : cin,
      tdsAmount: nilChallan ? 0 : numberOrNull(tdsAmount),
      surcharge: numberOrNull(surcharge),
      educationCess: numberOrNull(educationCess),
      interest: numberOrNull(interest),
      penalty: numberOrNull(penalty),
      nilChallan,
    })
  }

  return (
    <form onSubmit={submit} className="flex flex-col gap-3">
      {initial?.status === 'APPROVED' && (
        <p className="text-sm text-[var(--color-warning-text,var(--color-text-secondary))]">
          Editing an approved challan returns it to Proposed.
        </p>
      )}
      <div className="grid grid-cols-2 gap-3">
        <label className="flex flex-col gap-1 text-sm text-[var(--color-text-secondary)]">
          BSR code
          <input
            className={inputClass}
            value={bsrCode}
            onChange={(e) => setBsrCode(e.target.value)}
          />
        </label>
        <label className="flex flex-col gap-1 text-sm text-[var(--color-text-secondary)]">
          Deposit date
          <input
            type="date"
            className={inputClass}
            value={depositDate}
            onChange={(e) => setDepositDate(e.target.value)}
          />
        </label>
        <label className="flex flex-col gap-1 text-sm text-[var(--color-text-secondary)]">
          Challan serial number
          <input
            className={inputClass}
            value={serial}
            onChange={(e) => setSerial(e.target.value)}
          />
        </label>
        <label className="flex flex-col gap-1 text-sm text-[var(--color-text-secondary)]">
          CIN
          <input className={inputClass} value={cin} onChange={(e) => setCin(e.target.value)} />
        </label>
        <label className="flex flex-col gap-1 text-sm text-[var(--color-text-secondary)]">
          TDS amount
          <input
            type="number"
            min="0"
            className={inputClass}
            value={nilChallan ? '0' : tdsAmount}
            onChange={(e) => setTdsAmount(e.target.value)}
            disabled={nilChallan}
          />
        </label>
        <label className="flex flex-col gap-1 text-sm text-[var(--color-text-secondary)]">
          Surcharge
          <input
            type="number"
            min="0"
            className={inputClass}
            value={surcharge}
            onChange={(e) => setSurcharge(e.target.value)}
            disabled={nilChallan}
          />
        </label>
        <label className="flex flex-col gap-1 text-sm text-[var(--color-text-secondary)]">
          Education cess
          <input
            type="number"
            min="0"
            className={inputClass}
            value={educationCess}
            onChange={(e) => setEducationCess(e.target.value)}
            disabled={nilChallan}
          />
        </label>
        <label className="flex flex-col gap-1 text-sm text-[var(--color-text-secondary)]">
          Interest
          <input
            type="number"
            min="0"
            className={inputClass}
            value={interest}
            onChange={(e) => setInterest(e.target.value)}
            disabled={nilChallan}
          />
        </label>
        <label className="flex flex-col gap-1 text-sm text-[var(--color-text-secondary)]">
          Penalty
          <input
            type="number"
            min="0"
            className={inputClass}
            value={penalty}
            onChange={(e) => setPenalty(e.target.value)}
            disabled={nilChallan}
          />
        </label>
      </div>
      <label className="flex items-center gap-2 text-sm text-[var(--color-text-secondary)]">
        <input
          type="checkbox"
          checked={nilChallan}
          onChange={(e) => {
            setNilChallan(e.target.checked)
            if (e.target.checked) {
              setTdsAmount('0')
            }
          }}
        />
        Nil challan
      </label>
      {clientError && (
        <p role="alert" className="text-sm text-[var(--color-danger)]">
          {clientError}
        </p>
      )}
      <div className="flex gap-2">
        <button
          type="submit"
          className="inline-flex h-9 items-center justify-center rounded-[var(--radius-md)] bg-[var(--color-primary)] px-3 text-sm font-medium text-white hover:bg-[var(--color-primary-hover)]"
        >
          Save challan
        </button>
        <button
          type="button"
          onClick={onCancel}
          className="inline-flex h-9 items-center justify-center rounded-[var(--radius-md)] border border-[var(--color-border)] px-3 text-sm font-medium text-[var(--color-text-primary)] hover:bg-[var(--color-surface-raised)]"
        >
          Cancel
        </button>
      </div>
    </form>
  )
}
