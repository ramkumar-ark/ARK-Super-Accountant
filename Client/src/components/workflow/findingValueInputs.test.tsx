import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { TAN_PATTERN, valueInputFor } from './findingValueInputs'

function input(findingType: string, value: string) {
  return valueInputFor(
    { source: 'TDS', findingType },
    { value, onChange: () => undefined },
  )
}

describe('findingValueInputs', () => {
  it('renders a TAN input for a debtor TAN finding', () => {
    const result = input('DEBTOR_TDS_RECEIVABLE_MISSING_TAN', 'BLRA12345C')

    render(<>{result?.node}</>)

    expect(screen.getByLabelText('Corrected TAN')).toBeInTheDocument()
    expect(result?.valid).toBe(true)
  })

  it('accepts a lowercase TAN after trimming', () => {
    expect(input('DEBTOR_TDS_RECEIVABLE_MISSING_TAN', ' blra12345c ')?.valid).toBe(true)
  })

  it('rejects a malformed TAN', () => {
    expect(input('DEBTOR_TDS_RECEIVABLE_MISSING_TAN', 'not-a-tan')?.valid).toBe(false)
    expect(TAN_PATTERN.test('BLRA12345C')).toBe(true)
  })

  it('keeps the PAN input for PAN findings', () => {
    const result = input('PAN_MISSING', 'ABCDE1234F')

    render(<>{result?.node}</>)

    expect(screen.getByLabelText('Corrected PAN')).toBeInTheDocument()
  })

  it('has no input for other TDS findings', () => {
    expect(input('SHORT_DEDUCTED', '')).toBeNull()
  })
})
