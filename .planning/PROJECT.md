# Super Accountant

## What This Is

Super Accountant is a web application for CAs (managing multiple business clients) and SME business owners that turns Tally JSON exports into actionable compliance workflows. It processes uploaded Tally data to generate GST and TDS reports, reconciles GSTR-2B against purchase records, uses AI to convert uploaded invoices into Tally-ready accounting entries, and exports corrected data back to Tally — all with role-based access for the full accounting team.

## Core Value

A CA or business owner uploads Tally JSON and immediately gets actionable GST and TDS compliance reports — without manual re-entry or spreadsheet juggling.

## Requirements

### Validated

<!-- Shipped and confirmed from existing codebase. -->

- ✓ User authentication with JWT (signup + signin) — existing
- ✓ Role-based access: CASHIER, ACCOUNTANT, DATA_ENTRY_OPERATOR, OWNER — existing
- ✓ Tally XML import and parsing (TallyParserService) — existing
- ✓ GST validation of Tally voucher data (GstValidationService) — existing
- ✓ Organization management with multi-tenant foundation — existing
- ✓ Tally masters upload, validation pipeline, and preconfigured masters — existing
- ✓ Marketing landing page (React, TanStack Router) — existing

### Active

<!-- Current v1 scope. These are hypotheses until shipped and validated. -->

- [ ] Tally JSON upload and analysis engine (parse day book, vouchers, ledgers from JSON format)
- [ ] TDS reports: liability summary, deductee-wise breakdown, section-wise (194C, 194J, etc.)
- [ ] GSTR-2B reconciliation: upload portal JSON, match against Tally purchase entries, output mismatch report
- [ ] AI invoice processing: extract data from uploaded invoice photos/PDFs → generate accounting entry
- [ ] Tally re-import JSON: export corrected vouchers, AI-generated entries, and updated masters
- [ ] Extended role set: Manager, Auditor/CA — with appropriate permission boundaries
- [ ] Multi-tenant CA mode: CA account can manage and switch between multiple isolated client organizations
- [ ] AI audit trail: log what each AI invoice extraction captured and the reasoning behind each entry

### Out of Scope

<!-- Explicit v1 boundaries. -->

- GSTR-2B auto-fix / corrected import JSON — mismatch report only for v1; auto-correction adds complexity before reconciliation logic is proven
- TDS challan generation and 26Q/27Q return file prep — reports only for v1; filing automation requires TRACES integration
- Full data-change audit trail (all edits) — AI decisions only for v1; full audit trail is v2 compliance scope
- Login/access audit log — deferred to v2
- P&L and Balance Sheet reports — not the primary compliance pain point; deferred after GST/TDS workflow is validated
- GSTR-1 / GSTR-3B filing automation — reconciliation comes before filing automation
- Tally XML re-import (XML format) — JSON round-trip only; XML export is a separate Tally integration concern

## Context

### Codebase State (as of 2026-04-11)

- **Backend:** Spring Boot 4 / Java 25, PostgreSQL, JWT auth, Hibernate auto-DDL
- **Frontend:** React + TypeScript, Vite, TanStack Router, Tailwind v4
- **Existing parsing:** TallyParserService handles Tally XML format; JSON format needs a parallel parser
- **Existing GST:** GstValidationService validates GST fields from Tally data; GSTR-2B reconciliation is new scope
- **Masters pipeline:** Upload → parse → validate → find mismatches → resolve; this pattern can be reused for GSTR-2B recon and invoice processing workflows
- **Multi-tenant:** Organization model exists; CA ↔ multi-client relationship not yet modeled
- **Roles:** ERole enum has 4 roles (CASHIER, ACCOUNTANT, DATA_ENTRY_OPERATOR, OWNER); Manager and Auditor/CA need to be added
- **Test coverage:** Frontend: 75 unit tests. Backend: thin (smoke test + a few unit tests). Integration tests absent.
- **Known concerns:** `@CrossOrigin(origins = "*")` on all controllers (too permissive); JWT secret likely hardcoded in application.properties

### Domain Context

- Tally Prime exports data as XML (via ODBC/API) or JSON (day book format); both formats are in use by Indian SMEs
- GSTR-2B is the auto-drafted Input Tax Credit statement from the GST portal; reconciliation against purchase records is a mandatory monthly CA task
- TDS (Tax Deducted at Source) reports are required for quarterly 26Q/27Q filings; section-wise breakdowns (194C, 194J, 194H, etc.) are the unit of work
- Indian CAs typically manage 10–50 client businesses; switching between clients without re-logging is a UX requirement
- Invoice AI processing must handle: GST invoices (with GSTIN, HSN codes), purchase bills, expense receipts — mixed quality scans

## Constraints

- **Tech Stack:** Java 25 + Spring Boot 4 backend; React + TypeScript frontend — established, not up for debate
- **Database:** PostgreSQL only — Hibernate manages schema via ddl-auto: update
- **Auth:** JWT stateless — no session storage
- **Tally compatibility:** Re-import JSON must conform to Tally Prime's import format (JSON schema defined by Tally)
- **AI invoice processing:** Must handle both image (JPEG/PNG) and PDF inputs; OCR + LLM pipeline
- **Indian compliance:** GST rules (IGST/CGST/SGST split, reverse charge, exempt supplies), TDS sections per Income Tax Act — domain logic must be accurate, not approximate

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Brownfield — extend existing Spring Boot backend | Auth, org management, and masters pipeline already built; greenfield rewrite would discard working code | — Pending |
| JSON parser alongside XML parser | Tally JSON (day book format) is the primary input for analysis; XML is separate Tally integration | — Pending |
| Masters upload pattern reused for GSTR-2B and invoice workflows | Upload → parse → validate → findings is proven in the masters pipeline | — Pending |
| TDS and GSTR-2B reports before AI invoice processing | Primary v1 success criterion is compliance workflow; AI invoice is valuable but secondary | — Pending |
| CA multi-client via Organization switching | CAs need isolated client data; Organization model already exists as the tenant boundary | — Pending |

## Evolution

This document evolves at phase transitions and milestone boundaries.

**After each phase transition** (via `/gsd-transition`):
1. Requirements invalidated? → Move to Out of Scope with reason
2. Requirements validated? → Move to Validated with phase reference
3. New requirements emerged? → Add to Active
4. Decisions to log? → Add to Key Decisions
5. "What This Is" still accurate? → Update if drifted

**After each milestone** (via `/gsd-complete-milestone`):
1. Full review of all sections
2. Core Value check — still the right priority?
3. Audit Out of Scope — reasons still valid?
4. Update Context with current state

---
*Last updated: 2026-04-11 after initialization*
