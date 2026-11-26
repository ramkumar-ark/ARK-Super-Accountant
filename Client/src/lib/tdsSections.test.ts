import { describe, expect, it } from 'vitest'
import {
  NEW_ACT_EFFECTIVE,
  buildTdsSectionOptions,
  isNewActPeriod,
  tdsSectionBothNames,
  tdsSectionDisplay,
  type TdsSectionDef,
} from './tdsSections'

function def(overrides: Partial<TdsSectionDef>): TdsSectionDef {
  return {
    code: '194C',
    description: 'Payments to contractors and sub-contractors',
    legalReference: '393(1) [Table: Sl. No. 6(i)]',
    rpu26qCode: '94C',
    rpu140Code: '1024',
    sortOrder: 2,
    ...overrides,
  }
}

const SECTIONS: TdsSectionDef[] = [
  def({ code: '194A', description: 'Interest other than interest on securities', legalReference: '393(1) [Table: Sl. No. 5(ii) and 5(iii)]', rpu26qCode: '94A', rpu140Code: '1022', sortOrder: 1 }),
  def({ code: '194C', sortOrder: 2 }),
  def({ code: '194I_A', description: 'Rent of plant, machinery or equipment', legalReference: '393(1) [Table: Sl. No. 2(ii).D(a)]', rpu26qCode: '4IA', rpu140Code: '1008', sortOrder: 4 }),
  def({ code: '194I_B', description: 'Rent of land, building, furniture or fittings', legalReference: '393(1) [Table: Sl. No. 2(ii).D(b)]', rpu26qCode: '4IB', rpu140Code: '1009', sortOrder: 5 }),
  def({ code: '194J_A', description: 'Fees for technical services, royalty for films, call centre', legalReference: '393(1) [Table: Sl. No. 6(iii).D(a)]', rpu26qCode: '4JA', rpu140Code: '1026', sortOrder: 6 }),
  def({ code: '194J_B', description: 'Fees for professional services, royalty, non-compete fees', legalReference: '393(1) [Table: Sl. No. 6(iii).D(b)]', rpu26qCode: '4JB', rpu140Code: '1027', sortOrder: 7 }),
  def({ code: '194J_C', description: 'Remuneration, fees or commission to a director (not salary)', legalReference: '393(1) [Table: Sl. No. 6(iii).D(b)]', rpu26qCode: '4JB', rpu140Code: '1028', sortOrder: 8 }),
  def({ code: '194M', description: 'Payments by individuals/HUF to contractors and professionals', legalReference: '194M', rpu26qCode: '94M', rpu140Code: null, sortOrder: 9 }),
]

describe('buildTdsSectionOptions', () => {
  it('shows the old section and the s.393 reference together, old first', () => {
    const options = buildTdsSectionOptions(SECTIONS)
    expect(options.find((o) => o.value === '194C')?.label).toBe(
      '194C / 393(1) [Table: Sl. No. 6(i)] – Payments to contractors and sub-contractors',
    )
    expect(options.some((o) => o.value === '194I')).toBe(false)
    expect(options.find((o) => o.value === '194A')?.label).toBe(
      '194A / 393(1) [Table: Sl. No. 5(ii) and 5(iii)] – Interest other than interest on securities',
    )
    expect(options.find((o) => o.value === '194J_A')?.label).toBe(
      '194J_A / 393(1) [Table: Sl. No. 6(iii).D(a)] – Fees for technical services, royalty for films, call centre',
    )
    expect(options.find((o) => o.value === '194I_A')?.label).toBe(
      '194I_A / 393(1) [Table: Sl. No. 2(ii).D(a)] – Rent of plant, machinery or equipment',
    )
    expect(options.find((o) => o.value === '194I_B')?.label).toBe(
      '194I_B / 393(1) [Table: Sl. No. 2(ii).D(b)] – Rent of land, building, furniture or fittings',
    )
  })

  it('keeps the stored value separate from the display label', () => {
    const options = buildTdsSectionOptions(SECTIONS)
    expect(options.find((o) => o.value === '194J_B')?.value).toBe('194J_B')
    expect(options[0]).toEqual({ value: 'NOT_SUBJECT', label: 'Not subject to TDS' })
    expect(options[options.length - 1]).toEqual({ value: 'OTHER', label: 'Other section' })
  })

  it('follows the server order and hides nothing the endpoint sends', () => {
    const options = buildTdsSectionOptions(SECTIONS)
    expect(options.slice(1, -1).map((o) => o.value)).toEqual([
      '194A',
      '194C',
      '194I_A',
      '194I_B',
      '194J_A',
      '194J_B',
      '194J_C',
      '194M',
    ])
  })

  it('leaves sections without a new-Act reference on their own name', () => {
    const options = buildTdsSectionOptions(SECTIONS)
    expect(options.find((o) => o.value === '194M')?.label).toBe(
      '194M – Payments by individuals/HUF to contractors and professionals',
    )
  })

  it('renders only the fixed options before the endpoint answers', () => {
    const options = buildTdsSectionOptions(undefined)
    expect(options).toHaveLength(2)
  })
})

describe('tdsSectionBothNames', () => {
  it('returns the combined label for a stored section code', () => {
    expect(tdsSectionBothNames('194C', SECTIONS)).toBe(
      '194C / 393(1) [Table: Sl. No. 6(i)] – Payments to contractors and sub-contractors',
    )
  })

  it('falls back to the code and renders an em dash when empty', () => {
    expect(tdsSectionBothNames('CUSTOM', SECTIONS)).toBe('CUSTOM')
    expect(tdsSectionBothNames(null, SECTIONS)).toBe('—')
  })

  it('shows legacy 194I mappings as the land/building half', () => {
    expect(tdsSectionBothNames('194I', SECTIONS)).toBe(
      '194I_B / 393(1) [Table: Sl. No. 2(ii).D(b)] – Rent of land, building, furniture or fittings',
    )
  })
})

describe('isNewActPeriod', () => {
  it('is true on and after 1 April 2026', () => {
    expect(isNewActPeriod(NEW_ACT_EFFECTIVE)).toBe(true)
    expect(isNewActPeriod('2027-01-01')).toBe(true)
  })

  it('is false before, or without, a period', () => {
    expect(isNewActPeriod('2026-03-31')).toBe(false)
    expect(isNewActPeriod(null)).toBe(false)
    expect(isNewActPeriod(undefined)).toBe(false)
  })
})

describe('tdsSectionDisplay', () => {
  it('shows the s.393 reference on and after 1 April 2026', () => {
    expect(tdsSectionDisplay('194C', '2026-04-01', SECTIONS)).toBe('393(1) [Table: Sl. No. 6(i)]')
    expect(tdsSectionDisplay('194A', '2026-04-01', SECTIONS)).toBe('393(1) [Table: Sl. No. 5(ii) and 5(iii)]')
    expect(tdsSectionDisplay('194J_B', '2026-06-15', SECTIONS)).toBe('393(1) [Table: Sl. No. 6(iii).D(b)]')
    expect(tdsSectionDisplay('194I_A', '2026-06-15', SECTIONS)).toBe('393(1) [Table: Sl. No. 2(ii).D(a)]')
    expect(tdsSectionDisplay('194I_B', '2026-06-15', SECTIONS)).toBe('393(1) [Table: Sl. No. 2(ii).D(b)]')
    expect(tdsSectionDisplay('194J_C', '2026-06-15', SECTIONS)).toBe('393(1) [Table: Sl. No. 6(iii).D(b)]')
  })

  it('reads the legacy 194I code as the land/building half', () => {
    expect(tdsSectionDisplay('194I', '2026-06-15', SECTIONS)).toBe('393(1) [Table: Sl. No. 2(ii).D(b)]')
  })

  it('shows the bare section before the new Act', () => {
    expect(tdsSectionDisplay('194C', '2025-06-15', SECTIONS)).toBe('194C')
  })

  it('shows sections without a new-Act reference on their own name', () => {
    expect(tdsSectionDisplay('194M', '2026-06-15', SECTIONS)).toBe('194M')
  })

  it('falls back to the code when the endpoint has not answered', () => {
    expect(tdsSectionDisplay('194C', '2026-06-15', undefined)).toBe('194C')
  })

  it('renders an em dash when empty', () => {
    expect(tdsSectionDisplay(null, '2026-06-15', SECTIONS)).toBe('—')
  })
})
