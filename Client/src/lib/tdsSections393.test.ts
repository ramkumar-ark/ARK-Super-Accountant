import { describe, expect, it } from 'vitest'
import { isNewActPeriod, tdsSectionDisplay } from './tdsSections393'

describe('isNewActPeriod', () => {
  it('is true on or after 1 April 2026', () => {
    expect(isNewActPeriod('2026-04-01')).toBe(true)
    expect(isNewActPeriod('2027-01-01')).toBe(true)
  })

  it('is false before 1 April 2026 and for missing values', () => {
    expect(isNewActPeriod('2026-03-31')).toBe(false)
    expect(isNewActPeriod(null)).toBe(false)
    expect(isNewActPeriod(undefined)).toBe(false)
  })
})

describe('tdsSectionDisplay', () => {
  it('shows the s.393 table reference in new-Act periods', () => {
    expect(tdsSectionDisplay('194C', '2026-04-01')).toBe('393(1) [Table: Sl. No. 6(i)]')
    expect(tdsSectionDisplay('194I', '2026-04-01')).toBe('393(1) [Table: Sl. No. 2(ii)]')
    expect(tdsSectionDisplay('194A', '2026-04-01')).toBe('393(1) [Table: Sl. No. 5(ii) and 5(iii)]')
    expect(tdsSectionDisplay('194J_B', '2026-06-15')).toBe('393(1) [Table: Sl. No. 6(iii).D(b)]')
  })

  it('shows the plain internal code in old-Act periods', () => {
    expect(tdsSectionDisplay('194C', '2025-06-15')).toBe('194C')
  })

  it('falls back to the internal code for sections without a new-Act ref', () => {
    expect(tdsSectionDisplay('194M', '2026-06-15')).toBe('194M')
  })

  it('renders an em dash for empty sections', () => {
    expect(tdsSectionDisplay(null, '2026-06-15')).toBe('—')
  })
})
