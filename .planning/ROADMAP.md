# Roadmap: Super Accountant v1

**Created:** 2026-04-09
**Phases:** 7
**Requirements covered:** 55/55

---

## Milestone 1: Compliance Workflow — GST & TDS from Tally

### Phase 1: Security Hardening & Foundation

**Goal:** The application is safe to store GSTIN and PAN data — JWT secret is environment-driven, all endpoints are role-guarded, and the test suite can run in CI without local dependencies.

**Requirements:**
- SEC-01, SEC-02, SEC-03, SEC-04
- ORG-01, ORG-02, ORG-03

**Success Criteria:**
1. Deploying the application without setting the JWT_SECRET environment variable causes startup to fail with a clear error — the secret is never read from application.properties.
2. Calling any non-public API endpoint without a valid JWT returns 401; calling an endpoint with a valid JWT but wrong role returns 403.
3. Running `./mvnw test` on a clean CI machine (no local PostgreSQL) passes all backend tests via Testcontainers.
4. An OWNER can create an organization with GSTIN, PAN, and registered address, and invite users by assigning them a role.
5. A CA user associated with multiple organizations can retrieve their organization list and select an active organization via the API.

**Plans:** 5 plans

Plans:
- [ ] 01-PLAN-01-jwt-secret-env.md — JWT secret to env var; jjwt 0.12.x upgrade; startup validation
- [ ] 01-PLAN-02-role-guards.md — @PreAuthorize on all controllers; WebSecurityConfig path lockdown
- [ ] 01-PLAN-03-testcontainers.md — Testcontainers PostgreSQL; fixtures to src/test/resources/; remove absolute paths
- [ ] 01-PLAN-04-org-entity.md — Organization entity extension (GSTIN/PAN/address/fyStart); OrganizationSetupPage UI
- [ ] 01-PLAN-05-invite-membership.md — UserOrganization join table; invite flow; org-list + org-switch endpoints; OrganizationSelector UI

**UI hint**: yes

---

### Phase 2: Role Restructuring & CA Multi-Client Switching

**Goal:** The right roles exist with the right permission boundaries, and a CA user can switch between client organizations in the UI without logging out.

**Requirements:**
- AUTH-01, AUTH-02, AUTH-03, AUTH-04, AUTH-05, AUTH-06

**Success Criteria:**
1. The four active roles (OWNER, ACCOUNTANT, OPERATOR, AUDITOR_CA) are the only roles accepted at signup and in `@PreAuthorize` guards — CASHIER and DATA_ENTRY_OPERATOR no longer exist.
2. An OWNER or MANAGER can view all reports and dashboard job status but cannot perform data entry or resolve mismatches.
3. An ACCOUNTANT can view all data and approve AI-generated entries; an OPERATOR can perform reconciliation and resolve mismatches but cannot approve AI entries.
4. An AUDITOR_CA user can view all data within a client organization but cannot create, edit, or approve anything.
5. A CA user with the AUDITOR_CA role sees an organization selector; selecting a different organization switches all subsequent API calls to that organization's data scope without requiring re-authentication.

**Plans:** 3 plans

Plans:
- [ ] 02-PLAN-01.md — Backend role migration: V2 SQL, ERole target values, DataInitializer upsert, signup validation, GAP-1 permitAll fix
- [ ] 02-PLAN-02.md — Permission matrix doc + @PreAuthorize rewrite across all controllers; role×endpoint integration tests
- [ ] 02-PLAN-03.md — Frontend: signup ROLES, authStore.switchOrganization role propagation, AUDITOR_CA-gated OrganizationSelector, RoleBadge in DashboardPage

**UI hint**: yes

---

### Phase 3: Masters TDS & GST Mapping Extension

**Goal:** Every ledger in the masters is mapped to a TDS section and GST applicability classification, so the TDS and GSTR-2B engines have the metadata they need to compute correctly.

**Requirements:**
- MSTR-01, MSTR-02, MSTR-03, MSTR-04, MSTR-05, MSTR-06, MSTR-07, MSTR-08

**Success Criteria:**
1. An OPERATOR can onboard a new organization using a preconfigured master template and sees all standard Indian accounting ledger groups pre-classified.
2. The validation pipeline flags any ledger with an unexpected or missing category as a finding with a severity level (HIGH / MEDIUM / LOW); an OPERATOR can resolve each finding by accepting or overriding the suggested category.
3. Every ledger has a TDS section assigned (194C, 194J(a), 194J(b), 194H, 194I, 194Q, etc.) or is explicitly marked "not subject to TDS" — the masters view shows this column for every ledger.
4. Every sales/income ledger has a GST applicability type (taxable rate, exempt, zero-rated, non-GST, or RCM) and an HSN/SAC code; missing codes are flagged as validation findings.
5. Every purchase ledger linked to a registered dealer has a GSTIN recorded; a missing GSTIN is surfaced as a HIGH severity finding that blocks GSTR-2B reconciliation.

**Plans:**
1. Extend `PreconfiguredMaster` and `ParsedLedger` models with TDS section, GST applicability, HSN/SAC code, and GSTIN fields.
2. Add new `ValidationRule` implementations: `TdsSectionMappingRule`, `GstApplicabilityRule`, `HsnSacCodeRule`, `GstinPresenceRule` — register in `ValidationOrchestrator`.
3. Build OPERATOR review UI: findings list with drill-down, inline resolution form (accept / assign correct value).
4. Implement preconfigured master template selector at organization onboarding; seed templates for standard Indian accounting ledger groups.
5. Expose bulk and per-ledger mapping endpoints; add integration tests asserting finding counts for known fixture files.

**UI hint**: yes

---

### Phase 4: Tally JSON Day Book Parser & Analysis Engine

**Goal:** A user can upload a Tally Prime day book JSON file and immediately see a structured summary of all vouchers — and downstream compliance features are gated on masters having no critical unresolved findings.

**Requirements:**
- TALLY-01, TALLY-02, TALLY-03, TALLY-04, TALLY-05, TALLY-06

**Success Criteria:**
1. An OPERATOR uploads a Tally day book JSON file and the system creates an upload job visible in the UI; the job progresses through uploading → parsing → validated (or failed) with a status the UI can poll.
2. After a successful parse, the user can view a voucher summary table broken down by type (purchase, sales, journal, payment, receipt), date range, and ledger.
3. Tally-specific data quirks — numeric amounts encoded as strings, Tally date format (YYYYMMDD), and UTF-16 encoding — are normalized transparently; the parsed data is numerically correct.
4. Attempting to access TDS reports or GSTR-2B reconciliation for an organization with unresolved HIGH severity masters findings shows a gated message explaining the prerequisite rather than displaying empty or incorrect data.
5. A malformed or schema-incompatible JSON file produces a failed job with a human-readable error message rather than a server exception.

**Plans:**
1. Implement `TallyJsonParserService` parallel to existing `TallyParserService` (XML) — handles day book JSON envelope, voucher array, ledger entries.
2. Implement normalization layer: string-to-BigDecimal amounts, YYYYMMDD-to-LocalDate, encoding detection (UTF-8/16/32).
3. Implement `UploadJob` lifecycle for JSON uploads: status polling endpoint, error state persistence, job summary response.
4. Build voucher summary API endpoint with filter parameters (type, date range, ledger); add frontend voucher summary page.
5. Implement masters-prerequisite gate: service method that returns gating status per organization; apply to TDS and GSTR endpoints.

**UI hint**: yes

---

### Phase 5: TDS Computation & Reports

**Goal:** A user can view complete TDS liability reports — deductee-wise, section-wise, with interest calculations and PAN validation flags — derived from the uploaded Tally day book.

**Requirements:**
- TDS-01, TDS-02, TDS-03, TDS-04, TDS-05, TDS-06, TDS-07, TDS-08, TDS-09, TDS-10

**Success Criteria:**
1. The TDS report shows every payment to a TDS-applicable ledger; payments where no corresponding TDS deduction entry exists in the same period are flagged in red as missing deductions.
2. The deductee-wise TDS report lists: PAN, deductee name, TDS section, total payment amount, TDS amount deducted, and applicable rate — sorted by TDS liability descending.
3. The section-wise summary shows 194C, 194J(a), 194J(b), 194H, 194I, 194Q and other applicable sections with total payment, total TDS, and number of deductees per section.
4. The outstanding TDS payable figure accounts for TDS already deposited (as recorded in Tally) and shows cumulative liability from the start of the financial year to the selected period.
5. Entries with missing or invalid PAN show 20% rate applied and are highlighted; entries where 194J(a) vs 194J(b) is ambiguous are flagged for ACCOUNTANT review.
6. An OPERATOR or ACCOUNTANT can export the TDS report as Excel (.xlsx) and CSV.

**Plans:**
1. Implement `TdsComputationService`: compute TDS liability from parsed vouchers using ledger-to-section mapping from masters; flag missing deduction entries.
2. Implement interest calculation rules: 1% per month for late deduction (Section 201(1A)), 1.5% per month for late deposit; compute from voucher dates.
3. Implement PAN validation: checksum format check; flag missing/invalid PAN at 20% rate; implement PAN–Aadhaar operative status check (external API call or manual flag).
4. Build TDS report API endpoints (deductee-wise, section-wise, outstanding payable); build React report pages with filter controls.
5. Implement Excel and CSV export using Apache POI; add section ambiguity flagging for 194J(a)/(b) with ACCOUNTANT review queue.

**UI hint**: yes

---

### Phase 6: Pre-Reconciliation GST Validation

**Goal:** Before GSTR-2B reconciliation begins, every GST entry in the uploaded day book is validated for correctness — amount splits, ledger usage, reverse charge gaps, and backdated entries — so the reconciliation runs on clean data.

**Requirements:**
- GSTV-01, GSTV-02, GSTV-03, GSTV-04, GSTV-05, GSTV-06, GSTV-07

**Success Criteria:**
1. After uploading a Tally day book, the OPERATOR can open a GST Validation view that lists all findings categorized by type: amount error, wrong ledger, unregistered vendor (RCM gap), and backdated entry.
2. A purchase voucher where CGST + SGST does not equal the IGST amount, or where CGST ≠ SGST for an intra-state transaction, is flagged as an amount error with the specific voucher reference shown.
3. A purchase voucher using Input IGST for an intra-state transaction (or Input CGST/SGST for an inter-state transaction) is flagged as a wrong ledger usage finding.
4. A purchase from a vendor with no GSTIN in masters, where no RCM self-invoice entry exists in the period, is flagged as a missing RCM entry.
5. Any GST entry modified or posted in a period that has been previously finalized is flagged as a backdated entry requiring ACCOUNTANT acknowledgment.
6. The GSTR-2B reconciliation option is disabled with an explanatory message until all HIGH severity GST validation findings are resolved or explicitly acknowledged by an ACCOUNTANT.

**Plans:**
1. Extend `GstValidationService` with new rule implementations: `GstAmountSplitRule`, `GstLedgerUsageRule`, `RcmGapRule`, `BackdatedEntryRule`.
2. Implement period finalization tracking: store finalized periods per organization; detect entries posted against finalized periods.
3. Build GST Validation findings UI: categorized list, severity badges, drill-down to voucher detail, acknowledge/resolve actions.
4. Implement GSTR-2B gate: service method checks for unresolved HIGH findings; integrate gate check into GSTR-2B upload endpoint.
5. Add integration tests for all four finding types using known fixture day book files with seeded masters.

**UI hint**: yes

---

### Phase 7: GSTR-2B Reconciliation

**Goal:** A user can upload a GSTR-2B JSON from the GST portal, reconcile it against validated Tally purchase entries with fuzzy matching, view the 3-bucket mismatch report, resolve discrepancies, finalize the period, and export to Excel.

**Requirements:**
- GSTR-01, GSTR-02, GSTR-03, GSTR-04, GSTR-05, GSTR-06, GSTR-07, GSTR-08, GSTR-09, GSTR-10, GSTR-11

**Success Criteria:**
1. An OPERATOR can upload a GSTR-2B JSON file downloaded from the GST portal; the system parses it and starts a reconciliation job against the validated Tally purchase vouchers for the same period.
2. The reconciliation report shows three buckets: matched invoices, invoices in GSTR-2B but not in Tally, and invoices in Tally but not in GSTR-2B — with IGST, CGST, and SGST amounts reconciled separately per invoice.
3. Section 17(5) blocked ITC items (motor vehicles, food, club membership, etc.) appear in a separate "Blocked ITC" section and are not counted as mismatches.
4. An OPERATOR can resolve an "in-2B-not-in-Tally" mismatch by creating a new Tally entry; resolved corrections are downloadable as a Tally import JSON file.
5. An ACCOUNTANT can finalize a reconciliation period; once finalized, the period is locked and appears as a carry-forward reference for the next month with any unmatched in-2B-not-in-Tally items surfaced automatically.
6. An OPERATOR or ACCOUNTANT can export the final reconciliation report as Excel (.xlsx).

**Plans:**
1. Implement GSTR-2B JSON parser: map portal JSON fields to normalized invoice objects (GSTIN, invoice number, date, IGST/CGST/SGST amounts).
2. Implement reconciliation engine: normalize both sides (uppercase, strip separators); exact match first, fuzzy fallback (edit distance with configurable threshold); Section 17(5) classifier.
3. Build 3-bucket report API and React reconciliation report UI with drill-down to individual invoice comparisons and mismatch detail.
4. Implement OPERATOR resolution workflow: create Tally entry from in-2B-not-in-Tally invoice; generate Tally import JSON for download.
5. Implement ACCOUNTANT finalization: period lock, carry-forward surfacing of prior unmatched items, backdated-entry gate check (GSTR-10); Excel export via Apache POI.

**UI hint**: yes

---

## Phases

- [x] **Phase 1: Security Hardening & Foundation** - JWT secret is environment-driven, endpoints are role-guarded, CI tests run without local dependencies, org management is complete.
- [x] **Phase 2: Role Restructuring & CA Multi-Client Switching** - New role set is live, permission boundaries are enforced, CA users can switch organizations without re-login.
- [ ] **Phase 3: Masters TDS & GST Mapping Extension** - Every ledger has TDS section and GST applicability metadata; missing GSTIN and HSN/SAC codes are flagged as findings.
- [ ] **Phase 4: Tally JSON Day Book Parser & Analysis Engine** - Users can upload Tally JSON, track job status, view voucher summaries, and are gated from compliance features until masters are clean.
- [ ] **Phase 5: TDS Computation & Reports** - Full deductee-wise and section-wise TDS reports with interest calculations, PAN validation, and Excel/CSV export.
- [ ] **Phase 6: Pre-Reconciliation GST Validation** - All GST entries validated for amount splits, ledger usage, RCM gaps, and backdated entries before reconciliation proceeds.
- [ ] **Phase 7: GSTR-2B Reconciliation** - Full reconciliation workflow: upload, fuzzy match, 3-bucket report, resolve, finalize, carry-forward, and Excel export.

## Progress Table

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 1. Security Hardening & Foundation | 5/5 | Complete | 2026-04-12 |
| 2. Role Restructuring & CA Multi-Client Switching | 3/3 | Complete | 2026-04-24 |
| 3. Masters TDS & GST Mapping Extension | 0/5 | Not started | - |
| 4. Tally JSON Day Book Parser & Analysis Engine | 0/5 | Not started | - |
| 5. TDS Computation & Reports | 0/5 | Not started | - |
| 6. Pre-Reconciliation GST Validation | 0/5 | Not started | - |
| 7. GSTR-2B Reconciliation | 0/5 | Not started | - |
