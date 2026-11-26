import type { TdsSectionDef } from '@/lib/tdsSections'

/**
 * What GET /api/tds/sections returns for the seeded statutory tables — the display
 * descriptions the backend canonicalizes (DataMigrationService.DISPLAY_DESCRIPTIONS),
 * the s.393 references from the dated rule rows, and the RPU codes from the backfill.
 */
export const TDS_SECTIONS_FIXTURE: TdsSectionDef[] = [
  {
    code: '194A',
    description: 'Interest other than interest on securities',
    legalReference: '393(1) [Table: Sl. No. 5(ii) and 5(iii)]',
    rpu26qCode: '94A',
    rpu140Code: '1022',
    sortOrder: 1,
  },
  {
    code: '194C',
    description: 'Payments to contractors and sub-contractors',
    legalReference: '393(1) [Table: Sl. No. 6(i)]',
    rpu26qCode: '94C',
    rpu140Code: '1024',
    sortOrder: 2,
  },
  {
    code: '194H',
    description: 'Commission or brokerage',
    legalReference: '393(1) [Table: Sl. No. 1(ii)]',
    rpu26qCode: '94H',
    rpu140Code: '1006',
    sortOrder: 3,
  },
  {
    code: '194I_A',
    description: 'Rent of plant, machinery or equipment',
    legalReference: '393(1) [Table: Sl. No. 2(ii).D(a)]',
    rpu26qCode: '4IA',
    rpu140Code: '1008',
    sortOrder: 4,
  },
  {
    code: '194I_B',
    description: 'Rent of land, building, furniture or fittings',
    legalReference: '393(1) [Table: Sl. No. 2(ii).D(b)]',
    rpu26qCode: '4IB',
    rpu140Code: '1009',
    sortOrder: 5,
  },
  {
    code: '194J_A',
    description: 'Fees for technical services, royalty for films, call centre',
    legalReference: '393(1) [Table: Sl. No. 6(iii).D(a)]',
    rpu26qCode: '4JA',
    rpu140Code: '1026',
    sortOrder: 6,
  },
  {
    code: '194J_B',
    description: 'Fees for professional services, royalty, non-compete fees',
    legalReference: '393(1) [Table: Sl. No. 6(iii).D(b)]',
    rpu26qCode: '4JB',
    rpu140Code: '1027',
    sortOrder: 7,
  },
  {
    code: '194J_C',
    description: 'Remuneration, fees or commission to a director (not salary)',
    legalReference: '393(1) [Table: Sl. No. 6(iii).D(b)]',
    rpu26qCode: '4JB',
    rpu140Code: '1028',
    sortOrder: 8,
  },
  {
    code: '194M',
    description: 'Payments by individuals/HUF to contractors and professionals',
    legalReference: '194M',
    rpu26qCode: '94M',
    rpu140Code: null,
    sortOrder: 9,
  },
  {
    code: '194Q',
    description: 'Purchase of goods above the threshold',
    legalReference: '393(1) [Table: Sl. No. 8(ii)]',
    rpu26qCode: '94Q',
    rpu140Code: '1031',
    sortOrder: 10,
  },
]
