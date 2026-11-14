# RPU 26Q Regular Return — File Format Notes

Research artifact for `RpuFormatSpec`. All column positions used by the generator come
from this document, never from memory.

## Source

- **Form:** 26Q Regular (TDS on payments other than salary), Q1–Q4.
- **Format version:** 7.8 (release 27-05-2025), valid for FY 2007-08 onward, and the
  current regular 26Q format up to FY 2025-26. (FY 2026-27 onward moves to Form 140 /
  RPU 1.2, which is out of scope for this worker; the plan fixes `FORM = "26Q"`.)
- **Source URL:**
  `https://tinpan.proteantech.in/downloads/e-tds/File_Format_26Q_Regular_Q1_to_Q4_Version_7.8_27052025_201011.xls`
- **Retrieved:** 2026-11-14.
- Downloaded copy: `/home/ram/firstmate/data/sa-wf-td/research/format-26q-v7.8.xls`
  (cleaned TSV: `format-26q-v7.8-clean.txt`). Not committed; this file is the committed
  record.

## Encoding rules

| Rule | Value |
|---|---|
| Character set | ASCII |
| Record terminator | CR LF (`0D 0A`) after every record, including the last |
| Delimiter | `^` (caret), variable-width fields |
| Delimiter count | one fewer than the field count of the record (empty fields still need their delimiter) |
| Constant values | BLOCK letters |
| Dates | `DDMMYYYY`, no future date |
| Amounts | two decimals, e.g. `2345.00` |
| Rate | four decimals, e.g. `2.0000` |
| Record order | FH, BH, CD, DD… (each CD immediately followed by its DD records) |

Field counts: **FH = 18, BH = 72, CD = 41, DD = 54.**

## FH — File Header (18 fields)

| # | Field | Type/Len | M/O | Value |
|---|---|---|---|---|
| 1 | Line Number | Int 9 | M | sequential from 1 |
| 2 | Record Type | Char 2 | M | `FH` |
| 3 | File Type | Char 4 | M | `NS1` |
| 4 | Upload Type | Char 2 | M | `R` (regular) |
| 5 | File Creation Date | Date 8 | M | `DDMMYYYY` |
| 6 | File Sequence No. | Int | M | unique sequence across files; `1` for a single file |
| 7 | Uploader Type | Char 1 | M | `D` (deductor) |
| 8 | TAN of Deductor | Char 10 | M | from `RpuHeader.tan` |
| 9 | Total No. of Batches | Int 9 | M | `1` |
| 10 | Name of Return Preparation Utility | Char 75 | M | `SUPER ACCOUNTANT` |
| 11 | Record Hash | — | NA | blank |
| 12 | FVU Version | — | NA | blank |
| 13 | File Hash | — | NA | blank |
| 14 | Sam Version | — | NA | blank |
| 15 | SAM Hash | — | NA | blank |
| 16 | SCM Version | — | NA | blank |
| 17 | SCM Hash | — | NA | blank |
| 18 | Consolidated File Hash | — | NA | blank |

## BH — Batch Header (72 fields)

| # | Field | Type/Len | M/O | Value / code list |
|---|---|---|---|---|
| 1 | Line Number | Int 9 | M | sequential |
| 2 | Record Type | Char 2 | M | `BH` |
| 3 | Batch Number | Int 9 | M | starts at 1 |
| 4 | Count of Challan Records | Int 9 | M | number of CD records |
| 5 | Form Number | Char 4 | M | `26Q` |
| 6 | Transaction Type | — | NA | blank |
| 7 | Batch Updation Indicator | — | NA | blank |
| 8 | Original Token Number | — | NA | blank |
| 9 | Token No. of Previous Regular 26Q Statement | Int 15 | O | mandatory when field 52 = `Y`; source `RpuHeader.originalReceiptNumber` |
| 10 | Token Number of Statement Submitted | — | NA | blank |
| 11 | Token Number Date | — | NA | blank |
| 12 | Last TAN | — | NA | blank |
| 13 | TAN of Deductor | Char 10 | M | capitals |
| 14 | Receipt Number | Char 8 | NA | blank |
| 15 | PAN of Deductor | Char 10 | M | `PANNOTREQD` when no PAN is required |
| 16 | Assessment Year | Int 6 | M | FY start + 1, e.g. `202728` for FY 2026-27 |
| 17 | Financial Year | Int 6 | M | `YYYY` + 2-digit end year, e.g. `202627` for FY 2026-27 |
| 18 | Period | Char 2 | M | `Q1`, `Q2`, `Q3`, `Q4` |
| 19 | Name of Deductor | Char 75 | M | `RpuHeader.deductorName` |
| 20 | Deductor Branch/Division | Char 75 | M | `NA` when none |
| 21 | Deductor Address 1 | Char 25 | M | derived from `deductorAddress` |
| 22 | Deductor Address 2 | Char 25 | O | derived |
| 23 | Deductor Address 3 | Char 25 | O | derived |
| 24 | Deductor Address 4 | Char 25 | O | derived |
| 25 | Deductor Address 5 | Char 25 | O | derived |
| 26 | Deductor State | Int 2 | M | Annexure 1 state code |
| 27 | Deductor Pincode | Int 6 | M | 6 digits |
| 28 | Deductor Email ID | Char 75 | M | unless field 42 is given |
| 29 | Deductor STD Code | Char 5 | O | blank |
| 30 | Deductor Telephone | Char 10 | O | blank |
| 31 | Change of Address since Last Return | Char 1 | M | `Y`/`N` |
| 32 | Deductor Type | Char 1 | M | Annexure 4 |
| 33 | Name of Person Responsible | Char 75 | M | |
| 34 | Designation of Person Responsible | Char 20 | M | truncated to 20 |
| 35 | Responsible Person Address 1 | Char 25 | M | derived from `responsiblePersonAddress` |
| 36 | Responsible Person Address 2 | Char 25 | O | derived |
| 37 | Responsible Person Address 3 | Char 25 | O | derived |
| 38 | Responsible Person Address 4 | Char 25 | O | derived |
| 39 | Responsible Person Address 5 | Char 25 | O | derived |
| 40 | Responsible Person State | Int 2 | M | Annexure 1 |
| 41 | Responsible Person PIN | Int 6 | M | 6 digits |
| 42 | Responsible Person Email-1 | Char 75 | M | unless field 28 is given |
| 43 | Mobile Number | Char 10 | M | mandatory for non-government deductors |
| 44 | Responsible Person STD | Char 5 | O | blank |
| 45 | Responsible Person Telephone | Char 10 | O | blank |
| 46 | Change of Responsible Person Address | Char 1 | M | `Y`/`N` |
| 47 | Batch Total of Total Deposit Amount | Int 15 | M | sum of CD field 27 |
| 48 | Unmatched Challan Count | Int 9 | O | blank |
| 49 | Count of Salary Detail Records | — | NA | blank |
| 50 | Batch Total Gross Salary | — | NA | blank |
| 51 | AO Approval | Char 1 | M | `N` |
| 52 | Whether Regular 26Q Filed for Earlier Period | Char 1 | M | `Y`/`N` |
| 53 | Last Deductor Type | — | NA | blank |
| 54 | State Name | Char 2 | O | Annexure 5; state-government deductors only |
| 55 | PAO Code | Char 20 | O | blank |
| 56 | DDO Code | Char 20 | O | blank |
| 57 | Ministry Name | Char 3 | O | blank |
| 58 | Ministry Name Other | Char 150 | O | blank |
| 59 | PAN of Responsible Person | Char 10 | M | |
| 60 | PAO Registration No. | Char 7 | O | blank |
| 61 | DDO Registration No. | Char 10 | O | blank |
| 62 | Deductor STD (alt) | Char 5 | O | blank |
| 63 | Deductor Telephone (alt) | Char 10 | O | blank |
| 64 | Deductor Email (alt) | Char 75 | O | blank |
| 65 | Responsible STD (alt) | Char 5 | O | blank |
| 66 | Responsible Telephone (alt) | Char 10 | O | blank |
| 67 | Responsible Email (alt) | Char 75 | O | blank |
| 68 | AIN of PAO/TO/CDDO | Char 7 | O | blank |
| 69 | GSTN | Char 15 | O | blank |
| 70 | Count of 194P Records | — | NA | blank |
| 71 | Batch Total 194P | — | NA | blank |
| 72 | Record Hash | — | NA | blank |

## CD — Challan Detail (41 fields)

| # | Field | Type/Len | M/O | Value / code list |
|---|---|---|---|---|
| 1 | Line Number | Int 9 | M | sequential |
| 2 | Record Type | Char 2 | M | `CD` |
| 3 | Batch Number | Int 9 | M | `1` |
| 4 | Challan-Detail Record Number | Int 9 | M | serial in batch, from 1 |
| 5 | Count of Deductee/Party Records | Int 9 | M | DDs under this challan |
| 6 | NIL Challan Indicator | Char 1 | M | `Y`/`N` |
| 7 | Challan Updation Indicator | — | NA | blank |
| 8 | Filler 3 | — | NA | blank |
| 9 | Filler 4 | — | NA | blank |
| 10 | Filler 5 | — | NA | blank |
| 11 | Last Bank Challan No. | — | NA | blank |
| 12 | Bank Challan No. | Char 5 | O | blank for nil challan |
| 13 | NA | — | NA | blank |
| 14 | DDO Serial Number (Form 24G) | Char 9 | O | government book entry only |
| 15 | NA | — | NA | blank |
| 16 | Bank-Branch Code (BSR) | Char 7 | M | blank for nil challan |
| 17 | NA | — | NA | blank |
| 18 | Date of Bank Challan | Date 8 | M | deposit date; last date of quarter for nil |
| 19 | Filler 6 | — | NA | blank |
| 20 | Filler 7 | — | NA | blank |
| 21 | Section | — | NA | blank for FY 2013-14 onward |
| 22 | Oltas Income Tax | Int 15 | M | challan TDS |
| 23 | Oltas Surcharge | Int 15 | M | challan surcharge |
| 24 | Oltas Cess | Int 15 | M | challan education cess |
| 25 | Oltas Interest | Int 15 | M | challan interest |
| 26 | Oltas Others | Int 15 | M | challan penalty (mapped as "others") |
| 27 | Total Deposit Amount per Challan | Int 15 | M | = 22+23+24+25+39+26 |
| 28 | NA | — | NA | blank |
| 29 | Total Tax Deposit as per Deductee Annexure | Dec 15 | M | sum of DD field 19 |
| 30 | TDS Income Tax | Dec 15 | M | sum of DD field 14 |
| 31 | TDS Surcharge | Dec 15 | M | sum of DD field 15 |
| 32 | TDS Cess | Dec 15 | M | sum of DD field 16 |
| 33 | Sum of Total Income Tax Deducted | Dec 15 | M | sum of DD field 17 |
| 34 | TDS Interest Amount | Int 15 | M | same as CD field 25 |
| 35 | TDS Others Amount | Int 15 | M | same as CD field 26 |
| 36 | Cheque/DD No. | — | O | blank for FY 2013-14 onward |
| 37 | By Book Entry/Cash | Char 1 | O | `N` for bank challan, `Y` for transfer voucher, blank for nil |
| 38 | Remarks | Char 14 | O | blank |
| 39 | Fee | Int 15 | O | `0.00` when not applicable |
| 40 | Minor Head of Challan | Char 3 | O | `200` for a challan; blank for nil; `100` for advance-tax sections |
| 41 | Record Hash | — | NA | blank |

## DD — Deductee Detail (54 fields)

| # | Field | Type/Len | M/O | Value / code list |
|---|---|---|---|---|
| 1 | Line Number | Int 9 | M | sequential |
| 2 | Record Type | Char 2 | M | `DD` |
| 3 | Batch Number | Int 9 | M | `1` |
| 4 | Challan-Detail Record Number | Int 9 | M | matches CD field 4 |
| 5 | Deductee/Party Detail Record No. | Int 9 | M | restarts at 1 per challan |
| 6 | Mode | Char 1 | M | `O` |
| 7 | Employee Serial Number | — | NA | blank |
| 8 | Deductee Code | Char 1 | M | `01` company, `02` other |
| 9 | Last Party PAN | — | NA | blank |
| 10 | Deductee PAN | Char 10 | M | `PANNOTAVBL` / `PANAPPLIED` / `PANINVALID` defaults |
| 11 | NA | — | NA | blank |
| 12 | Deductee Reference No. | Char 10 | O | mandatory when field 10 is a default PAN |
| 13 | Name of Deductee | Char 75 | M | sanitised |
| 14 | TDS Income Tax for Period | Dec 15 | M | `tdsDeducted` |
| 15 | TDS Surcharge | Dec 15 | M | `0.00` (not split in our model) |
| 16 | Health and Education Cess | Dec 15 | M | `0.00` (not split in our model) |
| 17 | Total Income Tax Deducted | Dec 15 | M | = 14+15+16 |
| 18 | NA | — | NA | blank |
| 19 | Total Tax Deposited | Dec 15 | M | = field 17 for a normal challan |
| 20 | NA | — | NA | blank |
| 21 | Total Value of Purchase | — | NA | blank (26Q) |
| 22 | Amount of Payment/Credit | Dec 15 | M | > 0.00 |
| 23 | Date Amount Paid/Credited | Date 8 | M | `dateOfPayment` |
| 24 | Date Tax Deducted/Collected | Date 8 | O | mandatory when field 17 > 0 |
| 25 | Date of Deposit | — | NA | blank |
| 26 | Rate at which Tax Deducted | Dec 7 | M | 4 decimals; `0.0000` when field 17 = 0 |
| 27 | Grossing Up Indicator | — | NA | blank |
| 28 | Book Entry/Cash Indicator | Char 1 | O | blank for FY 2013-14 onward |
| 29 | Date of Furnishing Certificate | — | NA | blank |
| 30 | Remarks 1 (Reason) | Char 1 | O | Annexure 6 |
| 31 | Remarks 2 | Char 1 | O | blank |
| 32 | Remarks 3 | Char 14 | O | blank |
| 33 | Section Code | Char 3 | O | mandatory for FY 2013-14 onward; Annexure 2 |
| 34 | Certificate Number u/s 197 | Char 10 | O | blank |
| 35 | Filler 1 | — | NA | blank |
| 36 | Filler 2 | — | NA | blank |
| 37 | Filler 3 | — | NA | blank |
| 38 | Filler 4 | — | NA | blank |
| 39 | Filler 5 | — | NA | blank |
| 40 | Filler 6 | — | NA | blank |
| 41 | Filler 7 | — | NA | blank |
| 42 | Filler 8 | — | NA | blank |
| 43 | 194N Cash Withdrawal (1) | Int 15 | O | blank |
| 44 | 194N Cash Withdrawal (2) | Int 15 | O | blank |
| 45 | 194N Cash Withdrawal (3) | Int 15 | O | blank |
| 46 | 194N Cash Withdrawal (4) | Int 15 | O | blank |
| 47 | 194N Cash Withdrawal (5) | Int 15 | O | blank |
| 48 | 194N Cash Withdrawal (6) | Int 15 | O | blank |
| 49 | Filler 14 | — | NA | blank |
| 50 | Filler 15 | — | NA | blank |
| 51 | Filler 16 | — | NA | blank |
| 52 | Filler 17 | — | NA | blank |
| 53 | Filler 18 | — | NA | blank |
| 54 | Record Hash | — | NA | blank |

## Section-code table (Annexure 2) for internal codes

Internal section codes stay in `194C` style (as seeded in `DataInitializer`). Translation
happens only in `RpuFormatSpec.sectionCode`.

| Internal | RPU code | Notes |
|---|---|---|
| `194A` | `94A` | interest other than securities |
| `194C` | `94C` | contractor/sub-contractor |
| `194H` | `94H` | commission/brokerage |
| `194I` | `4IB` | rent — land/building (`4IA` is plant/machinery/equipment; seed is "Rent" @10%) |
| `194J_A` | `4JA` | professional/technical fees |
| `194J_B` | `4JB` | royalty / non-compete etc. |
| `194M` | `94M` | **not in the v7.8 Annexure 2** — 194M is reported in Form 26QD, not 26Q. Mapped so the seeded internal code can never crash the generator; the export selection must exclude such entries. |
| `194Q` | `94Q` | purchase of goods |

Unknown internal code → `IllegalArgumentException`.

## Literals and markers

- **PAN not available:** `PANNOTAVBL` (other defaults allowed by the format: `PANAPPLIED`, `PANINVALID`).
- **Deductee code:** `01` company (`COMPANY`), `02` other (`NON_COMPANY`).
- **Remark code for higher rate without PAN:** `C` — "higher deduction due to non-availability of PAN", valid only when the PAN column carries a structurally invalid/default value.
- **Nil-challan marker:** CD field 6 = `Y`.
- **Revised/previous-statement marker:** BH field 52 = `Y` with BH field 9 = the 15-digit token of the previous regular statement. A first regular statement is `N` with BH field 9 blank.
- **Minor head:** `200` (TDS payable by taxpayer); nil challan blank.

## Name charset

The format allows letters, digits, spaces and the punctuation `. , - & ( ) /`. Other
characters (quotes, newlines, etc.) are removed, whitespace runs collapse to one space,
and the result is trimmed and truncated to 75 characters (DD field 13 / BH fields 19, 20).

## Deviations from plan interface

The plan says the record shapes are a floor and that research may add fields. Additions:

- `RpuHeader` gains optional `deductorBranch`, `deductorStateCode`, `deductorPincode`,
  `deductorEmail`, `deductorMobile`, `responsiblePersonPan`, `responsiblePersonAddress`,
  `responsiblePersonStateCode`, `responsiblePersonPin`, `responsiblePersonEmail`,
  `responsiblePersonMobile`, `deductorAddressChanged`, `responsiblePersonAddressChanged`
  and `regularStatementFiledEarlier`. All are null/false when Tc has no source. A
  convenience constructor with the plan's original 11 arguments fills them with defaults.
- `BhData` gains `batchTotalDeposit` (sum of CD field 27).
- `CdData` gains the deductee-annexure sums (fields 29–33), computed by the generator
  from the DD values, because those columns are mandatory statements of the DD totals.
- `DdData` matches the plan.
- `RpuFormatSpec` also exposes `addressLines(String)` (5 × 25-char lines) and
  `rate(BigDecimal)`.

Value derivations:

- DD fields 15/16 are `0.00` and field 17 equals field 14 because `TdsDeducteeEntry`
  stores a single `tdsDeducted` amount with no surcharge/cess split.
- CD field 26 ("others") receives `TdsChallan.penalty`; CD fields 34/35 mirror 25/26.
- CD field 39 (fee) is always `0.00`; CD field 40 is `200` for a challan and blank for a
  nil challan.
- BH field 52 is `Y` when `originalReceiptNumber` is present or the quarter is after Q1,
  otherwise `N`. Tc may refine this by supplying `regularStatementFiledEarlier`.
- DD field 12 (deductee reference number) is left blank: the input model has no reference
  number source. Flagged as a known gap for Tc.
- Nil challans are emitted with zero DD records per the worker plan's pinned test. This
  deliberately differs from the format's general note 11, which asks for deductees with
  threshold/lower-deduction remarks on a nil challan; the plan is the contract here.

## FVU version notes

The downloaded workbook is the format definition for version 7.8. The site's FVU release
notes list version 7.8 as the current FVU for this format; no field-layout change applies
within the FY range this worker targets.
