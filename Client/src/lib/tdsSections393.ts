// Form 140 display references under the Income-tax Act 2025 (s.393), effective 1 Apr 2026.
// Frontend copy of the s.393 refs (cannot read the DB); Form 140 codes live in Rpu140FormatSpec.SECTION_CODES — see docs/tds-income-tax-act-2025-research.md.
export const NEW_ACT_EFFECTIVE = '2026-04-01'

export function isNewActPeriod(periodStart: string | null | undefined): boolean {
  return !!periodStart && periodStart >= NEW_ACT_EFFECTIVE
}

export const TDS_393_DISPLAY: Readonly<Record<string, string>> = {
  '194A': '393(1) [Table: Sl. No. 5(ii) and 5(iii)]',
  '194C': '393(1) [Table: Sl. No. 6(i)]',
  '194H': '393(1) [Table: Sl. No. 1(ii)]',
  '194I': '393(1) [Table: Sl. No. 2(ii).D(b)]',
  '194J_A': '393(1) [Table: Sl. No. 6(iii).D(a)]',
  '194J_B': '393(1) [Table: Sl. No. 6(iii).D(b)]',
  '194Q': '393(1) [Table: Sl. No. 8(ii)]',
}

export function tdsSectionDisplay(section: string | null | undefined, periodStart: string | null | undefined): string {
  const code = section ?? ''
  if (isNewActPeriod(periodStart) && TDS_393_DISPLAY[code]) {
    return TDS_393_DISPLAY[code]
  }
  return code || '—'
}
