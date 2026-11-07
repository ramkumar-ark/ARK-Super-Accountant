// TDS sections the server accepts for a TDS_SECTION_MAPPING override (single and bulk).
// Values are what is stored; labels are what the user reads.
export const TDS_SECTION_OPTIONS: ReadonlyArray<{ value: string; label: string }> = [
  { value: 'NOT_SUBJECT', label: 'Not subject to TDS' },
  { value: '194C', label: '194C - Payments to contractors and sub-contractors' },
  { value: '194J_A', label: '194J(a) - Fees for technical services, royalty for films, call centre (2%)' },
  {
    value: '194J_B',
    label: '194J(b) - Fees for professional services, royalty, non-compete fees, director fees (10%)',
  },
  { value: '194H', label: '194H - Commission or brokerage' },
  { value: '194I', label: '194I - Rent' },
  { value: '194Q', label: '194Q - Purchase of goods above the threshold' },
  { value: '194A', label: '194A - Interest other than interest on securities' },
  { value: '194B', label: '194B - Winnings from lottery, crossword puzzles, card games' },
  { value: '194D', label: '194D - Insurance commission' },
  { value: '194M', label: '194M - Payments by individuals/HUF to contractors and professionals' },
  { value: 'OTHER', label: 'Other section' },
]
