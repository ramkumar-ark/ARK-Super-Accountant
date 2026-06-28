# Phase 4: Tally JSON Day Book Parser & Analysis Engine — Context

**Gathered:** 2026-05-07
**Status:** Ready for planning

<domain>
## Phase Boundary

Deliver an end-to-end flow: an OPERATOR uploads a Tally Prime day book JSON file, the system parses every voucher and ledger entry, persists them to DB, and returns a job summary. A new `/day-book` page shows the upload widget and a voucher-type breakdown table. TDS and GSTR-2B endpoints are gated until the org's masters have no unresolved HIGH-severity findings.

Out of scope for this phase:
- TDS computation (Phase 5)
- GST pre-reconciliation validation (Phase 6)
- GSTR-2B reconciliation (Phase 7)
- Async job polling / background processing
- File storage (uploaded bytes are not retained after parsing)

</domain>

<decisions>
## Implementation Decisions

### Voucher Persistence
- **D-01:** Parsed vouchers are stored as DB rows — new JPA entity (e.g., `ParsedVoucher`) with one row per voucher header.
- **D-02:** Ledger entries within each voucher are persisted as child rows — new JPA entity (e.g., `ParsedVoucherEntry`) linked to `ParsedVoucher`. Phase 5 TDS queries need ledger-level data (which ledger received the payment) so entry-level persistence is required.
- **D-03:** Uploaded bytes are discarded after parsing — no file storage. Downstream phases query the persisted rows, not the original file.

### Job Lifecycle
- **D-04:** Processing is **synchronous** — POST upload blocks until parsing + persistence completes, then returns the full job result in the response. No async queue, no polling endpoint needed for this phase.
- **D-05:** The upload response includes: job ID, final status (COMPLETED / FAILED), total vouchers parsed, error message if failed.
- **D-06:** Whether to extend the existing `UploadJob` entity (with a `jobType` discriminator) or create a separate `DayBookUploadJob` entity is **Claude's Discretion** — choose based on schema cleanliness. Note: existing `UploadJob` has masters-specific fields (`totalLedgersParsed`, `totalMismatches`) that don't map to day book semantics.

### Voucher Summary View
- **D-07:** New frontend route `/day-book` — a standalone page (not a tab on an existing page).
- **D-08:** Page layout: upload widget at top, summary table below (populated after a successful upload).
- **D-09:** Primary table breakdown: **by voucher type** — rows: Purchase, Sales, Journal, Payment, Receipt, Contra, Credit Note, Debit Note. Columns: count, total debit, total credit, date range (min–max date across vouchers of that type).
- **D-10:** Add `/day-book` to the sidebar in `AppShell` and as a route in `main.tsx`.

### Masters Prerequisite Gate
- **D-11:** Gate is enforced at **both API and UI layers**.
  - **API:** TDS and GSTR-2B endpoints return a structured gated payload (e.g., `{ "gated": true, "reason": "Unresolved HIGH severity masters findings", "unresolvedCount": N }`) rather than a 403. This gives the frontend the info needed to render a helpful locked state.
  - **UI:** Locked state component shows the reason text and a link to Masters → Findings tab so the user can resolve findings.
- **D-12:** Gate trigger: **unresolved HIGH severity findings only**. MEDIUM and LOW findings do not block access.
- **D-13:** Implement a `MastersGateService` (or equivalent) method that checks the org's unresolved HIGH finding count — reusable by TDS (Phase 5) and GST validation (Phase 6) endpoints.

### Claude's Discretion
- `UploadJob` reuse vs new entity — see D-06 above. Choose whichever keeps the schema cleaner.
- Exact API path for day book upload — suggest `/api/v1/day-book/upload` to keep it separate from `/api/v1/uploads` (which is the masters upload endpoint).
- Column sorting / filtering on the summary table — standard defaults are fine.
- Normalization details for string amounts (`BigDecimal.parseOrZero`) and date format (`YYYYMMDD → LocalDate`) — follow existing patterns in `TallyParserService`.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Roadmap & Requirements
- `.planning/ROADMAP.md` — Phase 4 goal, success criteria, and plan descriptions
- `.planning/PROJECT.md` — Project vision, constraints, and out-of-scope boundaries

### Existing Parsing Foundation
- `Service/superaccountant/src/main/java/com/arktech/superaccountant/tally/services/TallyParserService.java` — `parseJson()` already parses day book JSON → `TallyMessage`; `parseMastersJson()` shows the encoding detection + normalization pattern to replicate for day book
- `Service/superaccountant/src/main/java/com/arktech/superaccountant/tally/models/TallyMessage.java` — day book envelope (`tallymessage: List<Voucher>`)
- `Service/superaccountant/src/main/java/com/arktech/superaccountant/tally/models/Voucher.java` — full voucher model with all Tally fields; `getAllLedgerEntriesCombined()` handles the dual-key quirk (`allledgerentries` vs `ledgerentries`)
- `Service/superaccountant/src/main/java/com/arktech/superaccountant/tally/models/LedgerEntry.java` — ledger entry fields (ledger name, amount string)

### Job & Upload Pattern
- `Service/superaccountant/src/main/java/com/arktech/superaccountant/masters/models/UploadJob.java` — existing job entity to extend or use as pattern
- `Service/superaccountant/src/main/java/com/arktech/superaccountant/masters/models/UploadJobStatus.java` — current statuses (COMPLETED, COMPLETED_WITH_MISMATCHES, FAILED)
- `Service/superaccountant/src/main/java/com/arktech/superaccountant/masters/controllers/UploadController.java` — sync upload pattern: create job → parse → persist → return response; error paths; `@PreAuthorize` usage

### Frontend Patterns
- `Client/src/pages/MastersPage.tsx` — tab + filter + pagination pattern; `api` instance usage
- `Client/src/main.tsx` — manual TanStack Router route registration; auth guard pattern via `beforeLoad`
- `Client/src/components/AppShell.tsx` (if exists) — sidebar nav pattern; where to add Day Book entry

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `TallyParserService.parseJson(MultipartFile)` — parses full day book JSON; returns `TallyMessage` (List<Voucher>). Day book parser service should call this directly or replicate its encoding-detection logic.
- `TallyParserService` encoding detection (`isUtf32`, `convertToUtf8String`) — already handles UTF-8/16/32; reuse for day book uploads.
- `UploadJob` + `UploadJobRepository` — pattern for job tracking; decide reuse vs new entity (see D-06).
- `MastersPage.tsx` — upload → list → filter pattern; apply same UX flow for day book page.

### Established Patterns
- Controller: `@CrossOrigin(origins = "*") + @RestController + @RequestMapping("/api/v1")` — follow for new `DayBookController`.
- Auth: `@PreAuthorize("hasRole('OPERATOR') or hasRole('ACCOUNTANT')")` for upload; `isAuthenticated()` for read endpoints.
- Error response: `ResponseEntity.badRequest().body(Map.of("error", "...", "uploadId", job.getId()))`.
- Frontend data fetch: `api.post(...)` / `api.get(...)` from `@/lib/api`; TanStack Query or manual `useState`+`useEffect` (MastersPage uses manual pattern).

### Integration Points
- New `DayBookController` at `/api/v1/day-book/**` plugs into `WebSecurityConfig` — ensure the path is listed in `http.authorizeHttpRequests`.
- `MastersGateService.isGated(orgId)` will be called from `TdsReportController` (Phase 5) and `GstValidationController` (Phase 6) — design the service method to be reusable with no changes.
- Frontend sidebar: add Day Book nav item in `AppShell` between Dashboard and Masters.

</code_context>

<specifics>
## Specific Ideas

- Voucher type breakdown table: count + debit + credit + date-range columns. Totals row at the bottom.
- Gated API response shape: `{ "gated": true, "reason": "...", "unresolvedCount": N }` — frontend checks `gated: true` to switch to locked-state view.
- Error on malformed JSON: return failed job with `errorMessage` field (human-readable) — same pattern as masters upload.

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope.

</deferred>

---

*Phase: 04-tally-json-day-book-parser*
*Context gathered: 2026-05-07*
