import { TDS_SECTION_OPTIONS } from '@/lib/tdsSections'

interface Props {
  value: string
  onChange: (value: string) => void
  ariaLabel?: string
  disabled?: boolean
  placeholder?: string
  className?: string
}

/** The one TDS section dropdown, used by the single-finding override and the bulk action. */
export function TdsSectionSelect({ value, onChange, ariaLabel, disabled, placeholder = 'Select…', className }: Props) {
  return (
    <select
      aria-label={ariaLabel}
      value={value}
      disabled={disabled}
      onChange={(e) => onChange(e.target.value)}
      className={className}
    >
      <option value="">{placeholder}</option>
      {TDS_SECTION_OPTIONS.map((o) => (
        <option key={o.value} value={o.value}>
          {o.label}
        </option>
      ))}
    </select>
  )
}
