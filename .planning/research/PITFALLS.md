# Domain Pitfalls

**Domain:** Indian GST/TDS compliance accounting application (CA + SME)
**Researched:** 2026-04-09
**Confidence note:** External search tools were unavailable during this research session. All findings are based on training knowledge of Indian GST law, Income Tax Act TDS provisions, Tally Prime behavior, LLM hallucination patterns, and multi-tenant Java/Spring architecture. Confidence levels are assigned honestly. Claims about specific GST portal API schemas, Tally JSON field names, and CBIC circular thresholds should be verified against official sources during phase implementation.

---

## Critical Pitfalls

Mistakes in this category cause reconciliation errors that lead to incorrect ITC claims, tax demand notices, or data leakage across tenants. They typically require rewrites of core logic.

---

### Pitfall 1: GSTR-2B Matching on Invoice Number Strings Directly

**What goes wrong:** The reconciliation engine compares the invoice number from the GSTR-2B JSON (`docNo` or equivalent field) against the Tally voucher's reference number as a plain string equality check. This fails in the majority of real-world cases.

**Why it happens:** Invoice numbers in GSTR-2B come from the supplier's GSTR-1 filing, while the same invoice number in Tally is entered manually by the purchaser's data-entry team. The two representations diverge in predictable ways:
- Leading/trailing whitespace (`INV-001` vs `INV-001 `)
- Case differences (`inv-001` vs `INV-001`)
- Separator style (`INV001` vs `INV-001` vs `INV/001`)
- Prefix inconsistency (supplier files `2324/001`, purchaser enters `001`)
- Encoding artifacts from Excel copy-paste (non-breaking spaces, smart quotes)

**Consequences:** A reconciliation engine doing exact string match will report every invoice as a mismatch when many are genuine matches. CAs will distrust the tool immediately.

**Prevention:**
- Normalize both sides before comparison: uppercase, strip all non-alphanumeric characters, trim whitespace.
- Use a normalized match first; fall back to a fuzzy match (e.g., Levenshtein distance <= 2) with a "possible match" confidence flag.
- Make normalization rules configurable per organization — some businesses have consistent enough numbering that strict matching is preferred.
- Log the raw values and normalized values separately in the mismatch report so CAs can audit the matching logic.

**Detection (warning signs):**
- During QA: feed a real GSTR-2B JSON and Tally export for the same period; mismatch rate above 20% on invoices that should match is a signal the matching is too strict.
- CAs say "this is wrong, I can see this invoice in both" — they are matching visually what the system misses programmatically.

**Confidence:** HIGH — this is a universally documented pain point in Indian reconciliation tooling.

**Phase:** GSTR-2B Reconciliation phase. Must be solved before shipping any mismatch report to users.

---

### Pitfall 2: Ignoring the GSTR-2B Cut-off and Amendment Cycle

**What goes wrong:** The engine treats GSTR-2B as final and static for a given month. In practice, GSTR-2B for month M is auto-generated from supplier GSTR-1/IFF filings up to the 14th of M+1. Suppliers who file late, or who file amendments (GSTR-1A), cause the GSTR-2B to be revised. An ITC claim based on an early download of GSTR-2B will differ from the final version.

**Why it happens:** Developers design reconciliation as a one-time upload-and-match workflow, not a versioned one. The portal issues revised GSTR-2B files but most tools don't model the concept of "which version of GSTR-2B was used for this reconciliation run."

**Consequences:**
- A CA reconciles in week 2 of M+1, then the supplier amends — the reconciliation is now stale.
- ITC is claimed based on the stale reconciliation; the portal's auto-generated ITC may differ; the business faces ITC mismatch notices (DRC-01C).
- No way to audit which version of GSTR-2B produced the result.

**Prevention:**
- Store the GSTR-2B upload with a user-recorded period label (e.g., "GSTR-2B for April 2026, downloaded 14-May-2026") and treat each upload as an immutable, versioned artifact.
- Allow multiple GSTR-2B uploads for the same period (versioning); the reconciliation run is always tied to a specific upload version.
- Surface the download date to the CA prominently in the report header.
- Warn if the reconciliation period and the file's internal period indicator don't match.

**Detection:** CAs re-running reconciliation for the same month get different results — a clear sign versioning is missing.

**Confidence:** HIGH — GSTR-2B amendment behavior is documented in CBIC circulars.

**Phase:** GSTR-2B Reconciliation phase. Model the upload as a versioned artifact from the start; retrofitting this is painful.

---

### Pitfall 3: Treating Blocked ITC Entries as Reconciliation Mismatches

**What goes wrong:** Section 17(5) of the CGST Act blocks ITC on certain categories: motor vehicles (personal use), food and beverages, club memberships, works contract for immovable property, etc. These purchases will appear in GSTR-2B (the supplier filed correctly) but ITC cannot be claimed on them. A naive reconciliation that marks "appears in GSTR-2B but not claimed in books" as a mismatch will generate false positives for every blocked ITC purchase.

**Why it happens:** The reconciliation engine doesn't have a concept of ITC eligibility — it only knows "GSTR-2B says X, books say Y."

**Consequences:** Every blocked-ITC purchase generates a spurious mismatch. CAs dismiss the tool as generating noise. Trust is destroyed.

**Prevention:**
- Build an ITC eligibility classification into the reconciliation logic. The Tally ledger's nature (expense vs. asset), the HSN code range, and the voucher type are all signals.
- Allow CAs to mark specific ledgers or expense heads as "blocked ITC — Section 17(5)" and exclude them from the mismatch report.
- Report blocked ITC as a separate category in the output: "Ineligible ITC (Section 17(5))" — not as a mismatch.

**Detection:** If blocked-ITC-category purchases all show as mismatches after initial testing, this pitfall is present.

**Confidence:** HIGH — Section 17(5) is statutory law; the list of blocked categories is well defined.

**Phase:** GSTR-2B Reconciliation phase. Must be modeled before shipping.

---

### Pitfall 4: TDS Section Misclassification at the Ledger Level

**What goes wrong:** TDS section assignment (194C vs. 194J vs. 194H vs. 194I, etc.) is determined by the nature of the payment, not just the ledger name. The application infers TDS sections from Tally ledger names or voucher narrations. A ledger called "Professional Fees" is mapped to 194J. But the same ledger is used for both "professional services" (194J, 10%) and "technical services" (also 194J but has had differentiated rate discussions — was 2% for technical services under 194J(b) until Finance Act 2020 changes). If the section sub-categorization is wrong, the TDS report produces incorrect liability numbers.

**Why it happens:** Tally does not enforce GST-compliant TDS section tagging at the ledger level — it relies on user setup. Tally's TDS nature-of-payment configuration can differ between businesses. Developers assume ledger name → section is a simple lookup.

**Consequences:**
- TDS deducted at wrong rate. For example, 194J(b) technical services: 2% rate; 194J(a) professional services: 10% rate. Confusing them underestimates or overestimates liability.
- The TDS report submitted to the CA for 26Q preparation is wrong. The CA may not catch it. The 26Q filed with incorrect section codes triggers demand notices from the IT department.
- Section 194Q (TDS on purchase of goods, introduced in Finance Act 2021, 0.1%) is commonly missing in older tools; if not handled, high-value goods purchases go unreported.

**Prevention:**
- Do not rely solely on ledger name for section classification. Use a multi-signal approach: ledger name + HSN/SAC code + threshold amount + Tally's TDS nature-of-payment field if present in the JSON.
- Build a configurable section mapping table: organizations can override the default ledger-to-section mapping.
- Include 194Q, 194R, 194S (TDS on virtual digital assets) in the section registry — these are post-2021 additions that many legacy tools miss.
- Validate rate against threshold: 194C has a per-transaction threshold of Rs 30,000 and an annual aggregate threshold of Rs 1,00,000. Entries below threshold should not appear in the TDS liability column.
- Surface ambiguous classifications to the CA for manual review rather than silently applying a guess.

**Detection:** A TDS report where all 194J entries use a single rate (10%) with no 2% entries for technical services is a warning sign. Ask domain expert to validate against a known-good 26Q.

**Confidence:** HIGH for the structural classification problem. MEDIUM for exact rate thresholds (verify against current Finance Act provisions — rates change in the annual budget).

**Phase:** TDS Reports phase. Validate section-to-rate mapping against current CBIC/IT department documentation at phase start.

---

### Pitfall 5: Multi-Tenant Data Leakage via Missing Organization Scope in New Endpoints

**What goes wrong:** Every new API endpoint that fetches, processes, or exports data must filter by the authenticated user's organization. The current codebase already has one instance of this pattern implemented inconsistently (see CONCERNS.md: "No authorization check on cross-organization data access"). As new endpoints are added for GSTR-2B uploads, TDS reports, and invoice AI outputs, a single missed `WHERE organization_id = ?` clause causes one organization's financial data to be visible to another.

**Why it happens:** With Hibernate and Spring Data JPA, it is very easy to write `repository.findById(id)` — which returns data regardless of org — instead of `repository.findByIdAndOrganizationId(id, orgId)`. There is no framework-level enforcement; it is a developer discipline problem.

**Consequences:** For a CA managing 10–50 client businesses: Client A's GSTIN, invoice data, bank details, and ITC position are visible to Client B's users. This is a DPDP Act (India's data privacy law) violation, a trust-destroying event, and a potential legal liability.

**Prevention:**
- Implement the `OrganizationScopedService` layer recommended in CONCERNS.md before adding any new endpoints. This is the highest-priority architectural fix.
- Add a Spring Security `@PostFilter` or `@PreAuthorize` on all data-returning methods that enforces `returnObject.organizationId == principal.organizationId`.
- Write integration tests for every new endpoint that verify: (a) authenticated user from Org A cannot retrieve Org B's resources by guessing IDs.
- Consider row-level security at the PostgreSQL layer (PostgreSQL RLS policies) as a defense-in-depth measure for the most sensitive tables (GSTR-2B uploads, TDS reports, invoice extractions).
- Use UUIDs for all primary keys (already in use) to prevent sequential ID enumeration attacks.

**Detection:** Write a cross-org access test during the CA multi-tenant phase: create two organizations, log in as user of Org A, attempt to `GET /api/gstr2b-reconciliations/{id}` where the ID belongs to Org B. If you get a 200, the pitfall is present.

**Confidence:** HIGH — this is a direct consequence of the architecture pattern already flagged in CONCERNS.md.

**Phase:** CA Multi-Tenant phase AND every preceding phase. Must be addressed at the OrganizationScopedService level before any data endpoint is added.

---

### Pitfall 6: AI Invoice Extraction Hallucinating GSTIN and Tax Amounts

**What goes wrong:** An LLM or OCR+LLM pipeline extracts structured data from an invoice image. The model has high confidence in its output even when the source document is low-quality (blurry scan, skewed photo, handwritten amounts). It "fills in" plausible-looking but incorrect values: a GSTIN that passes the checksum algorithm but belongs to a different entity; a tax amount that is mathematically consistent with the subtotal but wrong because the subtotal was misread; an HSN code that exists in the HSN master but is wrong for this product.

**Why it happens:** LLMs are trained to produce coherent, plausible outputs. In an accounting context, "plausible" can mean technically valid (passes format checks) but factually wrong. The model does not know it is wrong because it cannot verify the source document against a ground truth.

**Consequences:**
- A wrong GSTIN means ITC is claimed from a supplier who is either non-existent or different from the actual supplier. GSTR-2B reconciliation will fail to match. If undetected, it constitutes fraudulent ITC claim.
- A wrong tax amount results in incorrect ITC claimed. Even small per-invoice errors compound across hundreds of invoices per month.
- A wrong TDS-relevant amount (in expense invoices) produces an incorrect TDS liability calculation.
- In a financial/legal context, "the AI hallucinated" is not a defense against a tax demand notice.

**Prevention:**
- Mandatory human review before any AI-extracted entry is committed to the ledger. Design the workflow as: AI generates a draft entry → CA/accountant reviews and approves/rejects → only approved entries proceed.
- Never auto-commit AI extractions. The audit trail required (already in v1 scope) must log: original extracted values, who reviewed, what was changed during review.
- GSTIN validation: after extraction, validate the GSTIN checksum (15-character alphanumeric with specific position rules) and cross-reference against the GST portal's public GSTIN search API if network access is available. Surface the supplier name returned by the portal alongside the extracted GSTIN so the reviewer can catch mismatches.
- Amount consistency check: verify that IGST = CGST + SGST (for intra-state, CGST = SGST = tax/2), and that the total invoice amount = taxable value + total GST + other charges. Flag the extraction as low confidence if this doesn't hold within a tolerance.
- Confidence scoring: the AI pipeline must return a per-field confidence score. Fields below a threshold (e.g., 0.8) must be highlighted in the review UI as requiring manual verification.
- Use structured output (JSON mode / function calling) rather than free-text generation to reduce hallucination surface.

**Detection:** In QA testing: use deliberately low-quality test invoice images (blurred, rotated, partially obscured). Measure extraction accuracy against ground truth. If GSTIN accuracy < 95% on clean documents, the pipeline is not ready for production.

**Confidence:** HIGH — LLM hallucination in financial document extraction is well-documented. The GSTIN checksum validation and consistency checks are domain-specific mitigations.

**Phase:** AI Invoice Processing phase. The mandatory-review workflow must be designed into the UX from the start, not added as an afterthought.

---

### Pitfall 7: Tally JSON Re-import Breaking on Tally Prime Version Differences

**What goes wrong:** The JSON format that Tally Prime accepts for import (via the HTTP API or the import utility) is not formally versioned with a public schema. It evolved between Tally ERP 9, Tally Prime 1.x, Tally Prime 2.x, and Tally Prime 3.x. Fields that are required in one version may be optional in another; enum values for voucher types and ledger configurations changed. A re-import JSON built against one version of the format will silently fail or create corrupt vouchers in a different version.

**Why it happens:** Tally does not publish a formal JSON schema with version identifiers. Developers reverse-engineer the format from a specific installation and assume it is universal.

**Consequences:**
- The CA runs the re-import on a client's Tally Prime installation. Vouchers are created with wrong voucher types or missing fields. The client's books are corrupted. This is the worst possible outcome for a compliance tool.
- Silent failures: Tally's import API may return success even when individual vouchers fail to import.

**Prevention:**
- Treat the Tally JSON re-import as a high-risk operation. Add a prominent disclaimer in the UI: the output JSON is tested against Tally Prime [version range]; verify on a test company before importing into production books.
- Test the re-import format against multiple Tally Prime versions (at minimum 2.x and 3.x) using a real Tally installation before shipping.
- Include only the minimal required fields in the export JSON — avoid using fields that are version-specific unless absolutely necessary.
- Always include a dry-run / preview mode: import into a Tally test company first, verify the results, then import into production.
- Research the Tally Developer documentation (developer.tallysolutions.com) and the Tally Prime REST API spec at phase start — do not rely on reverse-engineering alone.
- Version-pin the output format and document which Tally Prime version it targets.

**Detection:** Import the generated JSON into a freshly installed Tally Prime and verify every voucher appears correctly. Run this check on at least two different Tally Prime versions.

**Confidence:** MEDIUM — the version fragility of Tally's import format is a well-known practitioner complaint. The specific field differences between versions should be verified against Tally documentation at phase start.

**Phase:** Tally Re-import JSON phase. Must verify format against Tally Prime documentation before any implementation begins.

---

## Moderate Pitfalls

These cause incorrect reports or poor UX but are recoverable without full rewrites.

---

### Pitfall 8: QRMP Scheme Ambiguity in Monthly Reconciliation

**What goes wrong:** Under the QRMP (Quarterly Return Monthly Payment) scheme, small taxpayers (turnover < Rs 5 crore) file GSTR-1 quarterly but pay GST monthly. Their invoices appear in GSTR-2B in the month of filing (quarterly), not the month of supply. A monthly reconciliation window will miss these invoices for 2 out of 3 months in the quarter, creating false mismatches.

**Prevention:**
- The reconciliation report must surface whether a supplier is under QRMP. This information is not in the GSTR-2B JSON directly — it must be inferred from filing pattern or stored as metadata.
- When a purchase invoice has no matching GSTR-2B entry, the tool should check whether the supplier's filing pattern suggests QRMP (no GSTR-1 for this month, GSTR-1 last filed at quarter end) before flagging as a mismatch. Flag it as "QRMP — expected in quarter-end GSTR-2B" rather than a mismatch.
- Allow CAs to tag specific GSTINs as QRMP filers in the organization's supplier master.

**Confidence:** HIGH — QRMP was introduced in 2021 and is a common source of false mismatches.

**Phase:** GSTR-2B Reconciliation phase.

---

### Pitfall 9: IGST vs. CGST/SGST Split Errors in Reconciliation Matching

**What goes wrong:** An inter-state purchase carries IGST. The same transaction in Tally may be recorded with IGST split into CGST+SGST (or vice versa) due to a data-entry error or a Tally ledger misconfiguration. The reconciliation engine compares total tax amounts and they match numerically, masking a classification error that affects ITC credit (IGST ITC is usable against IGST, CGST, and SGST liability in a specific order; CGST/SGST ITC cannot be cross-used).

**Prevention:**
- Reconcile IGST, CGST, and SGST separately, not just the total tax amount.
- Flag vouchers where a supplier's GSTIN place-of-supply (derived from the first two digits of GSTIN being the supplier's state code) differs from the tax type recorded in Tally (IGST expected for inter-state, CGST+SGST for intra-state).
- The mismatch report should categorize by: amount mismatch, tax type mismatch (IGST vs. CGST/SGST), and combined.

**Confidence:** HIGH — IGST/CGST/SGST split errors are a standard GST audit finding.

**Phase:** GSTR-2B Reconciliation phase.

---

### Pitfall 10: Reverse Charge Mechanism Invoices Excluded from GSTR-2B

**What goes wrong:** Under Reverse Charge Mechanism (RCM), the purchaser is liable to pay GST — the supplier does not collect and remit. These RCM purchases do not appear in GSTR-2B (because the supplier did not file them in GSTR-1). An RCM purchase in Tally books that has no GSTR-2B counterpart will be flagged as a mismatch by a naive reconciliation engine.

**Prevention:**
- RCM vouchers in Tally are identifiable by the "Reverse Charge" flag or by the RCM ledger used. The reconciliation engine must exclude RCM entries from the "expected to appear in GSTR-2B" population.
- Report RCM entries as a separate section: "Inward RCM supplies — not expected in GSTR-2B."
- Verify that RCM liability (IGST/CGST/SGST payable by the purchaser) is correctly calculated and surfaced as a compliance alert.

**Confidence:** HIGH — RCM treatment in GSTR-2B reconciliation is a well-known exception.

**Phase:** GSTR-2B Reconciliation phase.

---

### Pitfall 11: LLM Context Window and Token Cost for Invoice Batching

**What goes wrong:** CAs upload batches of 50–200 invoices at once for AI extraction. A naive implementation sends each invoice as a separate LLM call, or worse, batches too many pages into one call. Either approach leads to: excessive API costs per upload session, context window overflow (for multi-page PDFs), or rate limiting from the LLM provider.

**Prevention:**
- Process one invoice (or one logical document boundary) per LLM call. Do not batch multiple invoices into a single prompt.
- Implement a job queue (Spring `@Async` or a lightweight queue like a database-backed job table) for invoice processing. Return a job ID immediately, process asynchronously, notify when complete.
- For multi-page PDFs: determine if it is a single invoice (all pages) or multiple invoices (one per page). Use a classification step before the extraction step.
- Cache structured OCR output (from PDF text extraction layer) so that if the LLM call fails and retries, OCR is not re-run.
- Set cost guardrails: estimate token count before sending; alert if a single document would exceed a threshold (e.g., 50,000 tokens) and reject or paginate.

**Confidence:** HIGH — async job processing for AI workloads is a standard pattern. Token cost management is a well-documented concern.

**Phase:** AI Invoice Processing phase.

---

### Pitfall 12: Hardcoded JWT Secret and CORS Wildcard in Compliance Context

**What goes wrong (specific to this codebase):** The existing codebase has a hardcoded JWT secret in `application.properties` that is committed to version control (see CONCERNS.md). With `@CrossOrigin(origins = "*")` on all controllers, any web page can make authenticated API calls to this service if it obtains a token. Given that the application handles GSTIN, PAN-linked TDS data, and invoice financial data, this combination is a serious data exposure risk.

**Why it's worse than generic apps:** Indian financial data (GSTIN, PAN, bank account details in invoices, TDS liability amounts) is sensitive under the DPDP Act 2023. A data breach involving this data category carries regulatory consequences beyond reputational damage.

**Prevention:**
- Rotate the JWT secret immediately. Use a cryptographically random 256-bit key stored in an environment variable, not in `application.properties`.
- Replace `@CrossOrigin(origins = "*")` with a centralized CORS configuration in `WebSecurityConfig` that whitelists only the deployed frontend origin. Use an environment variable for the allowed origin.
- Add role-based method-level security (`@PreAuthorize`) to all endpoints before adding new features — do not let the new GSTR-2B, TDS, and invoice endpoints ship without method-level authorization.
- Add rate limiting to the auth endpoints using `bucket4j` or Spring Security's built-in mechanisms before exposing the application to the internet.

**Confidence:** HIGH — directly observed in CONCERNS.md analysis. No external research needed.

**Phase:** Security hardening should be Phase 1 of this milestone, before building new features.

---

## Minor Pitfalls

---

### Pitfall 13: GSTR-2B JSON Schema Changes After GSTN Portal Updates

**What goes wrong:** The GSTN portal periodically updates the GSTR-2B JSON download format — adding fields, restructuring sections (e.g., the B2B, B2BA, CDNR, CDNRA sections), or changing field names. A parser hardcoded against an older schema silently drops new fields or throws parse errors.

**Prevention:**
- Use a permissive JSON parser that ignores unknown fields rather than failing. In Jackson, `@JsonIgnoreProperties(ignoreUnknown = true)` on all GSTR-2B DTOs.
- Design the GSTR-2B parser with a clear version detection step: check the portal-provided schema version field (if any) or the structure of the top-level keys.
- Write a parser unit test against a real (anonymized) GSTR-2B JSON file stored in `src/test/resources/`. This test will catch schema drift when the portal updates.
- Subscribe to GSTN developer notifications or periodically check the GSTN help desk for format change announcements.

**Confidence:** MEDIUM — GSTN does update portal exports periodically; exact versioning behavior requires verification against current GSTN documentation.

**Phase:** GSTR-2B Reconciliation phase.

---

### Pitfall 14: TDS Threshold Aggregation Across Vouchers

**What goes wrong:** TDS deduction thresholds under most sections (e.g., 194C: Rs 30,000 per contract, Rs 1,00,000 aggregate per year) are aggregate across all payments to the same deductee during the financial year. An implementation that checks only the individual voucher amount against the threshold will miss cases where multiple small payments to the same vendor cross the aggregate threshold mid-year.

**Prevention:**
- The TDS report generation must aggregate payments to the same PAN/deductee across all vouchers in the financial year (April–March), not just the current month.
- When generating a monthly TDS report, include a running total column per deductee showing cumulative payments in the year-to-date.
- Flag the first voucher that crosses the aggregate threshold — this is the point where TDS should have been deducted.

**Confidence:** HIGH — threshold aggregation is statutory; the rule is in the Income Tax Act.

**Phase:** TDS Reports phase.

---

### Pitfall 15: Test Infrastructure That Cannot Run in CI

**What goes wrong (specific to this codebase):** Two existing tests reference `C:/Program Files/TallyPrime/DayBook.json`. The test suite cannot run in CI. As the new GSTR-2B parser, TDS calculation logic, and invoice extraction pipeline are developed, if the same pattern is followed, no automated regression testing occurs.

**Prevention:**
- Move the existing local-file-dependent tests to `src/test/resources/` with sample fixtures before writing any new service tests.
- Establish the pattern that every new parser (GSTR-2B JSON parser, TDS voucher parser) has a corresponding test with a synthetic fixture in `src/test/resources/`. Use Java text blocks for small fixtures; store larger sample files as resource files.
- Add a Testcontainers PostgreSQL setup for integration tests so `@SpringBootTest` tests can run in CI without a local database.
- Add a CI pipeline check (GitHub Actions or equivalent) that runs `./mvnw test` on every push. This forces the team to keep the test suite clean.

**Confidence:** HIGH — directly observed in CONCERNS.md and TESTING.md.

**Phase:** Test Infrastructure cleanup should precede or run alongside the first new feature phase.

---

### Pitfall 16: Tally Day Book JSON Inconsistency Across Export Methods

**What goes wrong:** Tally Prime exports JSON via at least two mechanisms: the REST API (HTTP server mode) and the ODBC/TDL export. The JSON structure differs between the two. A parser built against one export method will fail against the other. Additionally, some Tally configurations (language settings, number format) affect numeric representation in exports: amounts may be formatted as strings with commas (`"1,00,000.00"`) rather than JSON numbers, or dates may use the Tally internal date format (`20240401`) rather than ISO 8601.

**Prevention:**
- At the start of the Tally JSON parsing phase, obtain sample JSON exports from both the REST API and the ODBC/TDL method. Do not assume they are identical.
- Write a normalization layer that handles both numeric-as-string and numeric JSON values, and that normalizes date formats to ISO 8601 before the data reaches business logic.
- Store the raw uploaded JSON alongside the parsed result so that if the parser has a bug, the original data is available for re-parsing.

**Confidence:** MEDIUM — practitioners report inconsistencies; the exact differences between Tally REST API and ODBC export formats should be verified with Tally documentation at phase start.

**Phase:** Tally JSON Upload and Analysis Engine phase (first new feature phase).

---

### Pitfall 17: Financial Year Boundary Errors in Reports

**What goes wrong:** India's financial year runs April to March (FY 2025–26 = April 2025 to March 2026). TDS thresholds reset on April 1. GSTR-2B reconciliation is monthly but ITC eligibility is tied to the filing year. Implementations that use calendar year (January–December) boundaries, or that miscalculate the financial year from a given date, produce wrong threshold aggregations and period labels.

**Prevention:**
- Implement a `FinancialYear` utility class that determines the FY from any date: FY is the calendar year in which April 1 falls (April 1, 2025 → FY 2025–26).
- All period selectors in the UI should use "Financial Year" and "Month" dropdowns, not calendar year selectors.
- Test the boundary cases: March 31 is the last day of the FY; April 1 resets thresholds and starts a new FY.

**Confidence:** HIGH — Indian FY = April–March is statutory. Boundary bugs are trivial to introduce with date arithmetic.

**Phase:** TDS Reports phase and any report with period-based aggregation.

---

## Phase-Specific Warnings

| Phase Topic | Likely Pitfall | Mitigation |
|-------------|----------------|------------|
| Security hardening (before new features) | JWT secret in version control, CORS wildcard | Rotate secret, centralize CORS, add method-level auth before any new endpoint ships |
| Test infrastructure cleanup | CI cannot run existing tests; new tests will follow same broken pattern | Move fixtures to `src/test/resources/`, add Testcontainers, add CI pipeline |
| Tally JSON Upload + Analysis | Inconsistent JSON between Tally export methods; numeric-as-string amounts; Tally date format | Build normalization layer first; obtain sample exports from multiple Tally installations |
| GSTR-2B Reconciliation | String matching failures; QRMP false mismatches; RCM excluded from 2B; blocked ITC false positives; IGST/CGST/SGST split errors; GSTR-2B versioning | Normalize invoice numbers; model upload as versioned artifact; classify ITC eligibility; separate RCM and blocked ITC categories |
| TDS Reports | Section misclassification; wrong rate for 194J(a) vs 194J(b); missing post-2021 sections (194Q, 194R, 194S); threshold aggregation across vouchers; FY boundary errors | Verify section-to-rate table against current Finance Act; aggregate by PAN across full FY; implement FinancialYear utility |
| AI Invoice Processing | GSTIN hallucination; tax amount inconsistency; LLM cost for batches; mandatory review workflow missing | Validate GSTIN checksum + portal lookup; enforce consistency checks; async job queue; never auto-commit AI extractions |
| Tally Re-import JSON | Format incompatible with target Tally Prime version; silent import failures | Test against real Tally Prime installations; version-pin the output format; add dry-run mode |
| CA Multi-Tenant Switching | Cross-org data leakage via missing org filter on new endpoints | Implement OrganizationScopedService before adding any new data endpoints; write cross-org access integration tests |

---

## Sources

All findings derived from:
- Training knowledge of CGST Act 2017, Income Tax Act TDS provisions (Sections 194C, 194J, 194H, 194I, 194Q, 194R, 194S), and QRMP scheme circulars
- Training knowledge of Tally Prime behavior and community-reported JSON format issues
- Training knowledge of LLM hallucination characteristics in structured data extraction tasks
- Direct analysis of codebase via `.planning/codebase/CONCERNS.md` and `.planning/codebase/TESTING.md`
- Standard multi-tenant SaaS data isolation patterns (Spring Data JPA)

**Verification required before implementation:**
- Exact Tally Prime JSON schema for current versions (developer.tallysolutions.com)
- Current TDS section rates and thresholds (incometaxindia.gov.in — verify against most recent Finance Act)
- GSTR-2B JSON structure and field names (gstn.org.in developer documentation)
- GSTIN validation checksum algorithm (publicly documented but confirm against current GSTN spec)
- DPDP Act 2023 applicability and data handling obligations for GST/PAN data (meity.gov.in)
