# Phase 4: Tally JSON Day Book Parser & Analysis Engine — Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-05-07
**Phase:** 04-tally-json-day-book-parser
**Areas discussed:** Voucher persistence, Job lifecycle model, Voucher summary view, Masters prerequisite gate

---

## Voucher Persistence

| Option | Description | Selected |
|--------|-------------|----------|
| DB rows (new entity) | Each voucher stored as a row; enables Phase 5/6 to query without re-parsing | ✓ |
| Aggregates only in DB | Summary rows only; voucher detail lost | |
| In-memory, no persistence | Stateless; compliance phases can't work without re-uploading | |

**User's choice:** DB rows (new entity)

---

| Option | Description | Selected |
|--------|-------------|----------|
| Voucher + ledger entries | One voucher row + child ledger entry rows; needed for Phase 5 TDS queries | ✓ |
| Voucher header only | One row per voucher; Phase 5 can't identify ledger targets without entries | |

**User's choice:** Voucher + ledger entries

---

## Job Lifecycle Model

| Option | Description | Selected |
|--------|-------------|----------|
| Async with polling | Spring @Async; UPLOADING → PARSING → VALIDATED; frontend polls status | |
| Synchronous (current pattern) | Parse inline; return completed response; no polling needed | ✓ |

**User's choice:** Synchronous

---

| Option | Description | Selected |
|--------|-------------|----------|
| Return full job response on upload | POST returns completed UploadJob; UI shows result immediately | ✓ |
| Return job ID, add a status endpoint | POST returns ID; GET /uploads/{id} returns final status | |

**User's choice:** Return full job response on upload

---

## Voucher Summary View

| Option | Description | Selected |
|--------|-------------|----------|
| By voucher type | Rows: Purchase/Sales/Journal/Payment/Receipt etc.; count + debit + credit + date range | ✓ |
| By date (daily/monthly) | Rows per day/month; trend-focused | |
| By ledger | One row per ledger; dense | |

**User's choice:** By voucher type

---

| Option | Description | Selected |
|--------|-------------|----------|
| New /day-book page | Standalone route with upload widget + summary table | ✓ |
| Tab on DashboardPage | Day Book tab within existing Dashboard | |
| Tab on existing page | Extend MastersPage or similar | |

**User's choice:** New /day-book page

---

## Masters Prerequisite Gate

| Option | Description | Selected |
|--------|-------------|----------|
| Both API + UI | Backend gated payload + frontend locked-state component with link to fix | ✓ |
| UI-only soft gate | Frontend warning only; direct API calls bypass gate | |
| API-only (hard 403) | Backend 403; no user-friendly context | |

**User's choice:** Both API + UI

---

| Option | Description | Selected |
|--------|-------------|----------|
| HIGH severity only | Only unresolved HIGH findings trigger gate | ✓ |
| Any unresolved finding | Any OPEN finding (HIGH/MEDIUM/LOW) blocks access | |

**User's choice:** HIGH severity only

---

## Claude's Discretion

- `UploadJob` reuse vs new entity — choose based on schema cleanliness
- Exact API path for day book upload endpoint
- Column sorting/filtering defaults on the summary table
- Amount normalization details

## Deferred Ideas

None.
