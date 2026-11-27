# TDS under the Income-tax Act, 2025 — research notes (FY 2026-27)

Task: date-driven support for the new Act in the TDS module. Primary sources first; secondary
sources marked explicitly.

## What changed (settled by primary sources)

The Income-tax Act, 2025 took effect 1 April 2026. Salary TDS is section 392; every other
TDS provision (old 192–194T, excluding TCS) is consolidated into **section 393**, table-driven:
393(1) residents, 393(2) non-residents, 393(3) any person. TCS moved to section 394 (out of scope).

- Transactions with the earlier of credit or payment on or before 31 Mar 2026 → Income-tax Act, 1961.
  On or after 1 Apr 2026 → Act 2025. (CBDT "FAQs on Interplay and Transition to the Income-tax
  Act, 2025", Q2.4–Q2.6, https://www.incometaxindia.gov.in/documents/81799/11848482/FAQs-on-Interplay-and-Transition.pdf)
- Rates and thresholds are "largely the same" per CBDT FAQ Q2.3, but the Act's own tables differ
  from our FY 2026-27 seeds in specific places (see below).

## Quarterly statement (replaces Form 26Q)

- Form 26Q (Rule 31A, s.200(3) of the 1961 Act) is replaced by **Form No. 140** under
  s.397(3)(b) of the Act, 2025 read with **Rule 219 of the Income-tax Rules, 2026**.
  (Income Tax Dept "Comprehensive Note on Form 26Q/140", 08.03.2026,
  https://www.incometaxindia.gov.in/documents/d/guest/fn-140)
- Salary 24Q → Form 138; non-resident 27Q → Form 144; TCS 27EQ → Form 143.
  26QB/QC/QD/QE (challan-cum-statement) → Form 141; certificates 16B/C/D/E → Form 132.
- Quarterly due dates unchanged (31 Jul / 31 Oct / 31 Jan / 31 May).

## File format (primary: Protean/TIN file-format spec)

Official file format, downloaded and read locally (saved as `f140.xlsx` during research;
source: https://tinpan.proteantech.in/downloads/e-tds/Form%20Number%20140%20-%2026Q%20-%20Q1%20to%20Q4_22072026.xlsx
linked from https://tinpan.proteantech.in/downloads/e-tds/eTDS-download-regular.html, "File Format
for Form Number 140 Q1 to Q4 Version 1.2"; the workbook's own title says Version 1.1):

- Same `^`-delimited ASCII layout family: FH 18 fields, BH 72, CD 30, DD 45
  (old 26Q v7.8 was CD 41, DD 54).
- FH file type `NS1` for Form 140 (was `NS1` for 26Q too); upload type `R`, uploader `D`.
- BH: form number literal `140`; **Assessment Year replaced by Tax Year** (BH has Tax Year
  `202627` for TY 2026-27; no AY field), period `Q1..Q4`; new deductor/responsible-person
  contact country-code fields; address line 6 (country); GSTIN at 69.
- CD: surcharge/education-cess/penalty columns removed; four components (tax, interest,
  fee, others); BSR/challan-serial sub-headings renumbered (401→A … 411→K); new
  Interest Allocation / Others Allocation fields; minor head 200/400 (Annexure 7).
- DD: "Section Code" is now a **4-digit Tax Code** (field no. 15) mapped to the section 393
  tables; old 197-certificate column replaced by s.395 certificate number (col O); new
  UIN-of-Form-121 field; date-of-payment restricted to the quarter and tax year.
- FVU 1.x (new series) validates TY 2026-27 onwards; FVU 9.5/2.191 keeps validating
  statements up to FY 2025-26. RPU 1.2 key-features note:
  https://tinpan.proteantech.in/downloads/e-tds/download/Key%20Features%20_RPU%20and%20FVU%20version%201.2.pdf

## Section 393 → 4-digit code mapping (Annexure 2 of the official spec — primary)

Only the codes our app's sections need are listed; full table read from the workbook.

| Our section | New reference | RPU code |
|---|---|---|
| 194D | 393(1) [Table: Sl. No. 1(i)] | 1005 |
| 194H | 393(1) [Table: Sl. No. 1(ii)] | 1006 |
| 194-I(a) machinery | 393(1) [Table: Sl. No. 2(ii).D(a)] | 1008 |
| 194-I(b) land/building/furniture | 393(1) [Table: Sl. No. 2(ii).D(b)] | 1009 |
| 193 | 393(1) [Table: Sl. No. 5(i)] | 1019 |
| 194A (payer bank/co-op/post office, senior-citizen payee) | 393(1) [Table: Sl. No. 5(ii).D(a)] | 1020 |
| 194A (payer bank/co-op/post office, other payee) | 393(1) [Table: Sl. No. 5(ii).D(b)] | 1021 |
| 194A (any other payer) | 393(1) [Table: Sl. No. 5(iii)] | 1022 |
| 194C (payee individual/HUF) | 393(1) [Table: Sl. No. 6(i).D(a)] | 1023 |
| 194C (payee other) | 393(1) [Table: Sl. No. 6(i).D(b)] | 1024 |
| 194J(a) technical/FTS | 393(1) [Table: Sl. No. 6(iii).D(a)] | 1026 |
| 194J(b) professional | 393(1) [Table: Sl. No. 6(iii).D(b)] | 1027 |
| 194J director remuneration | 393(1) [Table: Sl. No. 6(iii).D(b)] | 1028 |
| 194 dividend | 393(1) [Table: Sl. No. 7] | 1029 |
| 194Q purchase of goods | 393(1) [Table: Sl. No. 8(ii)] | 1031 |
| 192A accumulated PF balance | 392(7) | 1004 |
| 194T partner payments | 393(3) [Table: Sl. No. 7] | 1067 |
| 195 non-resident other sum | 393(2) [Table: Sl. No. 17] | 1057 (Form 144 annexure, not 140) |

**194M has no Form 140 code**: under the new Act, payments to contractors/professionals by an
individual/HUF not covered by 44AD/44ADA go on **Form 141 Schedule C** (challan-cum-statement),
like old 194-IB → Schedule A. (Comprehensive Note; Protean 140 Annexure 2 has no 194M row.)

## Rates/thresholds in the new Act vs our seeds (secondary-checked against Act text)

Our seeds claim "as of FY 2026-27" but are old-Act-shaped. Differences for post-1 Apr 2026
transaction dates (source: section 393 tables via KhataOS research with page cites
https://khataos.org/docs/research/india-tds-tcs/ — SECONDARY, Act text not independently verified):

- 194I rent: threshold becomes **₹50,000 per month or part-month** (not ₹2,40,000 annual);
  machinery 2%, other 10%.
- 194J: threshold **₹50,000** (both limbs) — our seed says 30,000.
- 194C: unchanged (₹30,000 per sum / ₹1,00,000 aggregate catch-up).
- 194H: unchanged (₹20,000).
- 194A: ₹10,000 for any-other-payer (5(iii)); banks ₹50,000 (₹1,00,000 senior citizen).
- 194Q goods: 0.1% only on the sum above ₹50 lakh (our seed deducts flat 0.1% with ₹50,00,000
  annual threshold — under the new Act the threshold is per-transaction excess). No-PAN rate
  for goods is 5% under s.397(2)(b)(i)(C), not 20%.
- No-PAN higher rate: 20% generally under s.397(2).

## Design adopted

Date-driven on the **earlier of payment or credit date** of the transaction:
- before 1 Apr 2026 → old-Act sections and old 26Q v7.8 RPU file (unchanged behaviour);
- on or after → new-Act section references/codes, new-Act thresholds, Form 140 v1.1 file.

## Open points (implemented conservatively, reported here)

1. Rent threshold granularity: the Act tests ₹50,000 **per month or part of a month** per landlord
   (Finance Act 2025 s.64 for 194-I from 1 Apr 2025; s.393 Table Sl. No. 2(ii) from 1 Apr 2026).
   Implemented as `statutory_section_rules.threshold_monthly`: the engine groups a payee's rent by the
   voucher's calendar month and the annual aggregate is not used. The earlier ₹6,00,000 annual figure
   in the first research pass was not the enacted wording. Periods before 1 Apr 2025 keep ₹2,40,000 annual.
2. 194M: no Form 140 code exists post-1 Apr 2026 (moves to Form 141 Schedule C, which this app
   does not generate). The RPU generator emits an explicit validation error rather than a wrong
   file; flagged to the captain rather than inventing a Form 141 generator.
3. The Protean workbook's own title says "Version 1.1" while the download page says 1.2 — the
   spec content used here is the downloaded 22072026 file; no behavioural difference observed.

## Statutory-config PR4 (194D / 193 / 194T, rent, 194Q, payee split)

- **Seeded sections** (`DataMigrationService.seedFinanceAct2025Sections`): 194D (2%, 10% domestic company,
  ₹20,000 — FA 2025 s.61), 193 (10%, ₹10,000 — FA 2025 s.56), 194T (10%, ₹20,000 — Finance (No. 2) Act 2024
  s.62, payer must be a firm). Rows start 1 Apr 2025, the date their values are verified for; earlier
  periods fall back to the earliest row. 26Q codes `94D`/`193`/`94T` (v7.8) and Form 140 codes
  `1005`/`1019`/`1067`. 194B (`94B` / `1058`) is verified but deliberately not seeded.
- **Finance Act 2025 corrections** (`applyFinanceAct2025RuleCorrections`): 194H ₹15,000 → ₹20,000 (s.63) and
  194J ₹30,000 → ₹50,000 (s.65) from 1 Apr 2025; 194-I per-month test from 1 Apr 2025 (s.64).
- **194Q** deducts only on the part of the aggregate above ₹50 lakh in every period
  (`threshold_excess_only`); no-PAN rate 5% under the 2025 Act. The old-regime no-PAN rate was not
  re-verified and stays at the seeded 20%.
- **Payee-split sections (194C)**: individual/HUF (PAN 4th character P or H) 1%, every other payee 2%.
  Previously only a company PAN got 2%, so firms, LLPs, AOPs and trusts were under-deducted at 1%.
- **194M** keeps the explicit "Form 141 not supported" export error; its 2025 Act rule row carries
  the reference `393(1) [Table: Sl. No. 6(ii)]`.
- A mapped section with no active statutory row raises `SECTION_NOT_CONFIGURED` once per section
  instead of being skipped silently.
