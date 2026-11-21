import { describe, expect, it } from 'vitest'
import { TDS_SECTION_OPTIONS, tdsSectionBothNames } from './tdsSections'

describe('TDS_SECTION_OPTIONS', () => {
  it('shows the old section and the s.393 reference together, old first', () => {
    expect(TDS_SECTION_OPTIONS.find((o) => o.value === '194C')?.label).toBe(
      '194C / 393(1) [Table: Sl. No. 6(i)] – Payments to contractors and sub-contractors',
    )
    expect(TDS_SECTION_OPTIONS.find((o) => o.value === '194I')?.label).toBe(
      '194I / 393(1) [Table: Sl. No. 2(ii).D(b)] – Rent',
    )
    expect(TDS_SECTION_OPTIONS.find((o) => o.value === '194A')?.label).toBe(
      '194A / 393(1) [Table: Sl. No. 5(ii) and 5(iii)] – Interest other than interest on securities',
    )
    expect(TDS_SECTION_OPTIONS.find((o) => o.value === '194J_A')?.label).toBe(
      '194J_A / 393(1) [Table: Sl. No. 6(iii).D(a)] – Fees for technical services, royalty for films, call centre',
    )
    expect(TDS_SECTION_OPTIONS.find((o) => o.value === '194I_A')?.label).toBe(
      '194I_A / 393(1) [Table: Sl. No. 2(ii).D(a)] – Rent of plant, machinery or equipment',
    )
    expect(TDS_SECTION_OPTIONS.find((o) => o.value === '194I_B')?.label).toBe(
      '194I_B / 393(1) [Table: Sl. No. 2(ii).D(b)] – Rent of land, building, furniture or fittings',
    )
  })

  it('keeps the stored value separate from the display label', () => {
    expect(TDS_SECTION_OPTIONS.find((o) => o.value === '194J_B')?.value).toBe('194J_B')
    expect(TDS_SECTION_OPTIONS.find((o) => o.value === 'NOT_SUBJECT')?.label).toBe('Not subject to TDS')
    expect(TDS_SECTION_OPTIONS.find((o) => o.value === 'OTHER')?.label).toBe('Other section')
  })

  it('leaves sections without a new-Act reference on their own name', () => {
    expect(TDS_SECTION_OPTIONS.find((o) => o.value === '194M')?.label).toBe(
      '194M – Payments by individuals/HUF to contractors and professionals',
    )
  })
})

describe('tdsSectionBothNames', () => {
  it('returns the combined label for a stored section code', () => {
    expect(tdsSectionBothNames('194C')).toBe(
      '194C / 393(1) [Table: Sl. No. 6(i)] – Payments to contractors and sub-contractors',
    )
  })

  it('falls back to the code and renders an em dash when empty', () => {
    expect(tdsSectionBothNames('CUSTOM')).toBe('CUSTOM')
    expect(tdsSectionBothNames(null)).toBe('—')
  })

  it('shows legacy 194I mappings as the land/building half', () => {
    expect(tdsSectionBothNames('194I')).toBe(
      '194I_B / 393(1) [Table: Sl. No. 2(ii).D(b)] – Rent of land, building, furniture or fittings',
    )
  })
})
