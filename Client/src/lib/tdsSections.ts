// TDS sections the server accepts for a TDS_SECTION_MAPPING override (single and bulk).
// Values are what is stored; labels are what the user reads. A masters mapping applies across
// both Acts, so each mapped section shows the old section first and the s.393 reference after it.
// Display refs live in tdsSections393.ts — keep this file free of a second copy.
import { TDS_393_DISPLAY } from './tdsSections393'

const SECTION_DEFS: ReadonlyArray<{ value: string; nature: string; bare?: boolean }> = [
  { value: 'NOT_SUBJECT', nature: 'Not subject to TDS', bare: true },
  { value: '194C', nature: 'Payments to contractors and sub-contractors' },
  { value: '194J_A', nature: 'Fees for technical services, royalty for films, call centre' },
  { value: '194J_B', nature: 'Fees for professional services, royalty, non-compete fees, director fees' },
  { value: '194H', nature: 'Commission or brokerage' },
  { value: '194I', nature: 'Rent' },
  { value: '194Q', nature: 'Purchase of goods above the threshold' },
  { value: '194A', nature: 'Interest other than interest on securities' },
  { value: '194B', nature: 'Winnings from lottery, crossword puzzles, card games' },
  { value: '194D', nature: 'Insurance commission' },
  { value: '194M', nature: 'Payments by individuals/HUF to contractors and professionals' },
  { value: 'OTHER', nature: 'Other section', bare: true },
]

function labelFor(def: { value: string; nature: string; bare?: boolean }): string {
  if (def.bare) {
    return def.nature
  }
  const newActRef = TDS_393_DISPLAY[def.value]
  const names = newActRef ? `${def.value} / ${newActRef}` : def.value
  return `${names} – ${def.nature}`
}

export const TDS_SECTION_OPTIONS: ReadonlyArray<{ value: string; label: string }> = SECTION_DEFS.map(
  ({ value, nature, bare }) => ({ value, label: labelFor({ value, nature, bare }) }),
)

/** The combined old/new-Act label for a stored section code, for masters where no period is known. */
export function tdsSectionBothNames(section: string | null | undefined): string {
  if (!section) {
    return '—'
  }
  return TDS_SECTION_OPTIONS.find((o) => o.value === section)?.label ?? section
}
