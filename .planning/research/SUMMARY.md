# Research Summary — Super Accountant Compliance Milestone

**Synthesized from:** STACK.md, FEATURES.md, ARCHITECTURE.md, PITFALLS.md
**Date:** 2026-04-11

---

## Executive Summary

Super Accountant targets Indian CAs who reconcile GSTR-2B and prepare TDS reports monthly using Tally Prime as the source of truth. The product succeeds or fails on reconciliation accuracy — CAs evaluate tools by running one month's data through them, and a high false-positive rate from naive exact-string invoice matching will disqualify the tool in minutes.

Build on existing patterns (ValidationOrchestrator, UploadJob lifecycle, Strategy beans) — no new structural concepts needed. Three new Maven dependencies cover all new functionality: PDFBox 3.x, Apache POI 5.x, jjwt 0.12.x.

The critical risk is **correctness, not scalability**. Wrong ITC claims and wrong TDS section classifications produce tax demand notices and CA liability.

---

## Key Stack Decisions

- **No new infrastructure** — PDFBox + POI + Spring RestClient for LLM calls covers everything. No message broker, no ML infra.
- **LLM via HTTP only** — Anthropic Messages API via Spring RestClient; `InvoiceExtractionClient` interface hides vendor so GPT-4o can be substituted.
- **jjwt 0.11.5 → 0.12.x upgrade** is a breaking change — plan as dedicated task, not bundled with a feature.
- **Apache POI for Excel, not JasperReports** — CAs work in Excel; JasperReports is wrong for tabular data.

---

## Table Stakes (CA will not evaluate without these)

1. GSTR-2B reconciliation with 3-bucket output (matched / in-2B-not-Tally / in-Tally-not-2B) using fuzzy invoice number matching
2. IGST/CGST/SGST reconciled separately per invoice; Section 17(5) blocked ITC shown as separate category (not mismatches)
3. TDS deductee-wise report with section mapping (194C, 194J, 194H, 194I, 194Q, 194R, 194S) from Tally ledger names
4. TDS section-wise liability summary with PAN validation and 20%-rate flag for missing PAN
5. Multi-client switching for CA role (no re-login per client)
6. Excel/CSV export of all reports

## Differentiators

- Fuzzy invoice number matching with configurable tolerance (solves 30–40% false-mismatch rate from exact-match tools)
- AI invoice extraction with mandatory human review, per-field confidence scoring, and AI audit trail
- GSTR-2B soft mismatch detection (invoice matched but ITC amount differs — most actionable finding type)
- TDS section ambiguity flagging (194J(a) 10% vs 194J(b) 2% — most tools silently apply wrong rate)
- Manager approval workflow for AI-generated entries

## Anti-Features (do NOT build in v1)

| Anti-Feature | Why |
|---|---|
| GSTR-1/3B portal auto-filing | Requires GSP license from GSTN (months of regulatory process) |
| 26Q/27Q FVU file generation | Moving target; CA uses dedicated TDS software |
| Real-time GSTN API polling | 10 req/min rate limit, reliability incidents at filing deadlines |
| Auto e-invoice IRN generation | IRP API + JWS digital signature — a separate product |
| AI-generated tax advice | CA Act 1949 liability |

---

## Critical Pitfalls

1. **Invoice number exact-string matching** — Normalize both sides (uppercase, strip non-alphanumeric); fuzzy fallback with "possible match" flag. >20% false-mismatch rate in QA = red flag.

2. **Multi-tenant data leakage** — Every repository query must filter by `organizationId`. Write cross-org integration test as mandatory acceptance criterion for every new endpoint.

3. **AI GSTIN and tax amount hallucination** — Validate GSTIN checksum post-extraction; verify IGST = CGST + SGST consistency; flag below 0.85 confidence for mandatory review. Never auto-commit AI extractions.

4. **Section 17(5) blocked ITC treated as mismatches** — Classify ITC eligibility at reconciliation time; blocked ITC is a separate output bucket.

5. **JWT secret in version control + CORS wildcard** — DPDP Act 2023 liability for GSTIN/PAN data. Fix before Phase 2.

---

## Recommended Build Order

| Phase | Focus | Key Dependency |
|-------|-------|----------------|
| 1 | Security hardening + test infrastructure | Prerequisite for all |
| 2 | Tally JSON day book parser + analysis engine | Sample Tally JSON file needed |
| 3 | TDS computation and reports | Phase 2 |
| 4 | GSTR-2B reconciliation | Phase 2 + real GSTR-2B portal JSON sample |
| 5 | CA multi-tenant switching + role extension | Before AI features |
| 6 | AI invoice processing | Phase 5, LLM vendor chosen |
| 7 | Tally re-import JSON export | All phases + verified Tally schema |

---

## Open Questions (resolve before implementation)

1. What Tally Prime version(s) are the CA pilot users running? (day book JSON schema is version-sensitive)
2. Real GSTR-2B JSON export from current portal needed before Phase 4 (cannot build parser on assumed field names)
3. Have TDS section rates changed in Finance Act 2025–26? (verify 194J(a)/(b) split, 194Q threshold)
4. Cloud-hosted SaaS or self-hosted? (affects file storage, CORS config, DPDP Act obligations)
5. ROLE_MANAGER — new role or repurpose existing? (affects Phase 5 + Phase 6 interaction)

---

## Confidence Assessment

| Area | Confidence |
|------|------------|
| Stack decisions | HIGH |
| GST/TDS regulatory requirements | HIGH |
| Architecture patterns | HIGH (existing codebase-grounded) |
| Tally JSON schema specifics | MEDIUM (needs real sample files) |
| LLM extraction accuracy on Indian invoices | MEDIUM (needs prototype spike) |
| TDS rates for FY 2025–26 | MEDIUM (verify Finance Act before seeding rules table) |
