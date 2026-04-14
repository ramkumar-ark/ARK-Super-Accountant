# Architecture Patterns

**Project:** Super Accountant — Compliance Milestone
**Researched:** 2026-04-09
**Confidence:** HIGH (derived from direct codebase analysis; AI pipeline section is MEDIUM)

---

## Existing Architecture Baseline

The backend is a Spring Boot monolith with feature-based package grouping. The pattern is:

```
{feature}/
  controllers/    ← HTTP boundary, no business logic
  services/       ← Orchestration and business logic
  models/         ← JPA entities
  rules/          ← Strategy implementations (where applicable)
  repository/     ← JPA repositories
  payload/
    request/      ← Inbound DTOs
    response/     ← Outbound DTOs
```

This is the proven container for all new features. No new structural patterns are needed — extend the monolith following the same feature-package convention.

---

## Recommended Architecture for New Features

### Module Map (New + Existing)

```
login/          ← (existing) Auth, users, roles — extend ERole with MANAGER, CA_AUDITOR
masters/        ← (existing) Organizations, upload jobs, validation pipeline
tally/          ← (existing) Tally XML/JSON parsing — add TallyJsonDayBookParser alongside TallyParserService
gst/            ← (existing) GST validation — extend with GSTR-2B reconciliation logic
tds/            ← (NEW) TDS computation and reports
invoice/        ← (NEW) AI invoice processing pipeline
export/         ← (NEW) Tally re-import JSON generation
```

Cross-cutting additions:
- `common/` — shared utilities (file storage abstraction, AI client wrapper)
- `login/security/WebSecurityConfig.java` — add new role permissions for MANAGER, CA_AUDITOR

---

## Component Boundaries

### What Talks to What

```
HTTP Client
    │
    ▼
[Controller Layer]  — enforces organizationId from JWT principal
    │
    ▼
[Service Layer]     — orchestrates domain logic, calls parsers, calls rules
    │
    ├──▶ [Parser Components]     — stateless, no DB access, return domain objects
    │       TallyParserService (existing — XML + masters JSON)
    │       TallyDayBookParser (new — day book JSON → List<Voucher>)
    │       Gstr2bParser (new — portal JSON → List<Gstr2bEntry>)
    │
    ├──▶ [Domain Rule Components]  — Strategy pattern, @Component beans
    │       ValidationRule implementations (existing)
    │       ReconciliationRule implementations (new — for GSTR-2B)
    │       TdsComputationRule implementations (new — per section)
    │
    ├──▶ [AI Client]             — wraps LLM call, isolated behind interface
    │       InvoiceExtractionClient (new)
    │
    ├──▶ [Export Assembler]      — builds Tally-schema JSON for re-import
    │       TallyExportAssembler (new)
    │
    └──▶ [Repository Layer]      — PostgreSQL via Spring Data JPA
```

Key boundary rules:
- Controllers NEVER call repositories directly (current UploadController violates this — it accesses repositories directly; new code should route through services).
- Parsers are stateless components with no @Autowired repositories. They receive bytes or strings, return domain objects.
- AI client is hidden behind an interface so it can be swapped (OpenAI → Gemini → local model) without touching the pipeline.

---

## Data Flow: Each Major Workflow

### 1. Tally JSON Day Book Upload and Analysis

This is the primary data ingestion pipeline for the compliance milestone.

```
POST /api/v1/tally/daybook  (multipart JSON)
    │
    ▼
TallyDaybookController
    ├── validates organizationId
    ├── creates DaybookUploadJob record (status=PROCESSING)
    │
    ▼
TallyDayBookParser.parse(bytes)
    ├── detects encoding (reuse existing convertToUtf8String logic)
    ├── parses tallymessage[] envelope
    └── returns List<Voucher> (existing Voucher model)
    │
    ▼
DaybookAnalysisService.analyze(orgId, List<Voucher>)
    ├── routes vouchers by vouchertypename
    ├── persists summary to DaybookUploadJob
    └── returns DaybookAnalysisResult (counts by type, date range, party list)
    │
    ▼
Response: DaybookUploadJobResponse (job id, summary stats)
```

The DaybookUploadJob is a new entity modeled after UploadJob — same status lifecycle (PROCESSING → COMPLETED / COMPLETED_WITH_MISMATCHES / FAILED). This is the anchor record that GSTR-2B reconciliation and TDS computation both reference.

### 2. GSTR-2B Reconciliation

Reuse the masters pipeline pattern exactly. The unit of work is a reconciliation job, not a masters upload job, but the shape is identical.

```
POST /api/v1/gstr2b/reconcile  (multipart JSON — GSTR-2B portal export)
    │
    ▼
Gstr2bController
    ├── validates organizationId
    ├── requires a completed DaybookUploadJob id (request parameter)
    ├── creates ReconJob record (status=PROCESSING)
    │
    ▼
Gstr2bParser.parse(bytes)
    └── returns List<Gstr2bEntry> (GSTIN, invoice no, taxable value, IGST, CGST, SGST, period)
    │
    ▼
ReconciliationOrchestrator.run(orgId, List<Gstr2bEntry>, daybookJobId)
    ├── loads purchase vouchers from that DaybookUploadJob
    ├── executes ReconciliationRule beans (Strategy, same pattern as ValidationOrchestrator)
    │     InvoiceMatchRule — match by invoice number + GSTIN + amount
    │     AmountToleranceRule — flag if amounts differ within tolerance
    │     MissingInTallyRule — portal entry with no matching purchase
    │     MissingInPortalRule — Tally purchase with no matching portal entry
    └── persists List<ReconFinding> with mismatch details
    │
    ▼
Response: ReconJobResponse (job id, matched count, mismatch count, embedded findings)
```

GET /api/v1/gstr2b/{jobId}/mismatches — paginated, filterable (same pattern as /uploads/{id}/mismatches)
GET /api/v1/gstr2b/{jobId}/mismatches/export — CSV export (same pattern as existing)

### 3. TDS Computation and Reporting

TDS computation operates on the day book vouchers already parsed in workflow 1. It does not require a new file upload.

```
POST /api/v1/tds/compute  (body: { daybookJobId, financialYear, quarter })
    │
    ▼
TdsController
    ├── validates organizationId owns that daybookJobId
    ├── creates TdsComputationJob record
    │
    ▼
TdsComputationService.compute(daybookJobId, quarter)
    ├── loads payment/journal vouchers from that DaybookUploadJob
    ├── filters by TDS-applicable ledgers (uses existing LedgerCategoryClassifier result)
    ├── applies section rules (194C, 194J, 194H, 194I, etc.) via TdsRule Strategy beans
    │     each TdsSectionRule: getRuleCode() → "TDS_194C", execute() → List<TdsLiability>
    └── persists TdsLiabilityRecord per deductee per section
    │
    ▼
GET /api/v1/tds/{jobId}/summary      — section-wise aggregate
GET /api/v1/tds/{jobId}/deductee     — deductee-wise breakdown
GET /api/v1/tds/{jobId}/export       — CSV/PDF
```

TDS reuses the Strategy pattern (TdsRule interface mirrors ValidationRule). The TdsComputationService mirrors ValidationOrchestrator.

### 4. AI Invoice Processing Pipeline

This is the only workflow that should be async. Rationale is in the section below.

```
POST /api/v1/invoices/process  (multipart: image/PDF file)
    │
    ▼
InvoiceController
    ├── validates organizationId
    ├── stores file to local disk or object storage (not in DB as blob)
    ├── creates InvoiceJob record (status=QUEUED)
    └── returns 202 Accepted { invoiceJobId }
    │
    ▼ (async — Spring @Async or virtual thread executor)
InvoiceProcessingService.processAsync(invoiceJobId)
    │
    ├── Step 1: Pre-process
    │     If PDF: extract first page as image (Apache PDFBox)
    │     Resize image if > 2MB (Java ImageIO)
    │
    ├── Step 2: LLM Extraction
    │     InvoiceExtractionClient.extract(imageBytes)
    │     Sends base64 image to vision LLM (OpenAI GPT-4o or Gemini 1.5)
    │     Returns structured InvoiceExtraction DTO:
    │       { vendorName, vendorGstin, invoiceNumber, invoiceDate,
    │         lineItems[], taxBreakdown{igst, cgst, sgst}, totalAmount }
    │
    ├── Step 3: Accounting Entry Generation
    │     AccountingEntryMapper.map(extraction, orgId)
    │     Resolves vendor ledger (lookup existing or flag as new)
    │     Generates debit/credit entries per GST rules
    │     Returns List<ProposedEntry>
    │
    ├── Step 4: Persist
    │     Saves InvoiceExtraction + ProposedEntry records
    │     Saves AiAuditLog (prompt, response, model, token counts, reasoning)
    │     Updates InvoiceJob status=COMPLETED
    │
    └── Step 5: On failure
          Updates InvoiceJob status=FAILED, errorMessage
          AiAuditLog records the failure with raw LLM response if available
    │
    ▼
GET /api/v1/invoices/{jobId}          — poll for status and results
GET /api/v1/invoices/{jobId}/entries  — proposed accounting entries for review
POST /api/v1/invoices/{jobId}/approve — mark entries accepted, flag for Tally export
POST /api/v1/invoices/{jobId}/reject  — mark with rejection reason
```

### 5. Tally Re-Import JSON Generation

The export assembler runs on demand after invoice entries are approved or after manual corrections.

```
POST /api/v1/export/tally  (body: { invoiceJobIds[], daybookJobId?, correctionIds[] })
    │
    ▼
ExportController
    ├── validates all referenced job ids belong to the organization
    ├── collects approved ProposedEntry records
    │
    ▼
TallyExportAssembler.build(List<ProposedEntry>)
    ├── transforms each ProposedEntry into Tally JSON voucher format
    │     (tallymessage[] envelope, voucher shape matching existing Voucher model)
    ├── includes updated master entries if ledger names were corrected
    └── returns TallyExportBundle (JSON string)
    │
    ▼
Response: application/json download
    Content-Disposition: attachment; filename="tally-import-{orgId}-{date}.json"
```

The export assembler knows the Tally JSON schema from the existing Voucher, LedgerEntry, GstEntry models in tally/models/. It is the inverse of TallyDayBookParser: domain objects in, Tally JSON out.

---

## Async vs Sync Decision: AI Invoice Processing

**Decision: Async.**

**Rationale:**

LLM API calls are the decisive factor. A GPT-4o vision call for a single invoice image takes 3–15 seconds depending on image size and model load. PDF pre-processing (PDFBox page rasterization) adds 0.5–2 seconds. Synchronous handling means the HTTP connection stays open for 5–20 seconds per invoice, which is:
- Hostile to mobile clients on variable connections
- A risk for load balancer timeouts (common default: 30s, Nginx default: 60s)
- Unscalable when batch-processing 10+ invoices

The masters upload pipeline (synchronous) works because file parsing is CPU-bound with no external I/O and completes in under 1 second for typical files. That condition does not hold for AI invoice processing.

**Implementation on Java 25 + Spring Boot 4:**

Spring Boot 4 enables virtual threads by default (spring.threads.virtual.enabled=true). This means the @Async thread pool can use virtual threads with no special configuration. The implementation is:

1. Annotate InvoiceProcessingService.processAsync() with @Async
2. Enable async in configuration with @EnableAsync
3. Return void from the async method (fire-and-forget after job id is returned to client)
4. Client polls GET /api/v1/invoices/{jobId} for status

Do not use Spring Events or a message broker (Kafka, RabbitMQ) for v1. The volume does not justify the operational overhead. Virtual thread + @Async is sufficient for single-instance deployment with tens of concurrent invoice jobs.

**What stays synchronous:**

Everything else. GSTR-2B reconciliation, TDS computation, and Tally export are all in-process computations on already-uploaded data. They complete in milliseconds to low seconds and should remain synchronous — simpler code, simpler error handling, consistent with the existing masters pipeline.

---

## Multi-Tenant CA Switching

### Current State

The `User` entity has a single `organizationId` (UUID). `Organization` is a flat entity. There is no modeled relationship between a CA account and multiple organizations.

### Required Model Change

Add a join table `ca_organization_memberships`:

```
ca_organization_memberships
  ca_user_id       BIGINT (FK → users.id)
  organization_id  UUID   (FK → organizations.id)
  granted_at       TIMESTAMP
  PRIMARY KEY (ca_user_id, organization_id)
```

This is a many-to-many: one CA user can be a member of many organizations; one organization can have multiple CA users.

### Switching Mechanism

CA users do NOT embed organizationId in the JWT at login time. Instead:

1. Login returns JWT with userId only (no organizationId) when the user's role is CA_AUDITOR.
2. A new endpoint: `POST /api/v1/ca/switch-organization { organizationId }` validates that the CA is a member of that organization and returns a new short-lived context token (or stores the active org in a header convention).
3. Preferred approach: return a short-lived (2h) JWT with the selected organizationId embedded, issued by the switch endpoint. The client replaces its stored token. All existing controllers continue to work unchanged — they read organizationId from the JWT principal.

This approach requires minimal changes to existing controllers. `AuthController` gets a new `/api/v1/ca/switch-organization` endpoint. `JwtUtils.generateJwtToken()` gets an overload that accepts an optional organizationId to embed. `WebSecurityConfig` allows that endpoint for authenticated CA_AUDITOR role only.

### Data Isolation

CA users see only the organizations they are members of. The standard `organizationId` enforcement in controllers is unchanged. The CA membership check happens only at switch time, not on every request.

### Existing users (OWNER, ACCOUNTANT, etc.)

Their flow is unchanged. They have a single organizationId in their User record. They receive it embedded in their JWT at login. The switch endpoint is not available to these roles.

---

## Suggested Build Order (Dependencies First)

```
1. Tally JSON Day Book Parser (tally/)
   ├── No dependencies on other new features
   ├── Unlocks: GSTR-2B recon, TDS computation, Tally export
   └── Risk: Tally JSON schema variations — build with real sample files early

2. TDS Computation (tds/)
   ├── Depends on: Day Book Parser (needs parsed vouchers)
   ├── Domain logic is well-defined (Income Tax Act sections)
   └── No AI dependency — pure computation

3. GSTR-2B Reconciliation (gst/ extension)
   ├── Depends on: Day Book Parser (needs purchase vouchers)
   ├── Depends on: GSTR-2B portal JSON schema (verify with real portal export)
   └── Matching logic is the hard part — build matching rules incrementally

4. Multi-Tenant CA Mode (login/ + masters/)
   ├── Depends on: Nothing else in this milestone
   ├── Required before: AI invoice processing (CAs are the primary users)
   └── Schema change to users/organizations — do early to avoid migration pain

5. AI Invoice Processing (invoice/)
   ├── Depends on: Async infrastructure (@Async + virtual threads)
   ├── Depends on: AccountingEntryMapper which depends on ledger master data
   └── Integration with LLM API is the highest-risk item — prototype the extraction
       call in isolation first, then integrate

6. Tally Re-Import JSON Export (export/)
   ├── Depends on: AI invoice processing (for AI-generated entries)
   ├── Depends on: Tally JSON schema knowledge from step 1
   └── Build last — it assembles outputs of all prior features
```

### Dependency Graph

```
Day Book Parser ──────────────┬──▶ GSTR-2B Recon
                              ├──▶ TDS Computation
                              └──▶ Tally Export (knows the schema)

CA Multi-Tenant ──────────────▶ (prerequisite context for all CA workflows)

AI Invoice Processing ─────────▶ Tally Export (approved entries)
```

---

## Key Architecture Decisions

| Decision | Rationale |
|----------|-----------|
| New features as new packages (tally-daybook in tally/, tds/, invoice/, export/) | Consistent with feature-based package convention; no structural disruption |
| GSTR-2B reconciliation as a ReconciliationOrchestrator + ReconciliationRule Strategy | Direct reuse of the proven ValidationOrchestrator + ValidationRule pattern; same DB shape, same API shape |
| TDS as TdsComputationService + TdsRule Strategy beans | Section-specific rules (194C vs 194J) map naturally to the Strategy pattern already established |
| AI invoice processing is async (@Async + virtual threads) | LLM calls are 3–15s; synchronous would be hostile to clients and fragile under load |
| LLM client behind an interface (InvoiceExtractionClient) | Swap OpenAI for Gemini or a local model without touching the pipeline |
| CA switching returns a new JWT with organizationId | Zero controller changes; re-uses all existing organizationId enforcement |
| Tally export is the inverse of the day book parser | Existing Voucher/LedgerEntry/GstEntry models serve double duty: parse in, serialize out |
| File storage on disk (not DB blob) for invoice images | Invoice images (JPEG, PDF) can be multi-MB; storing in PostgreSQL as bytea is expensive for reads; use a configured directory or S3-compatible store |

---

## Anti-Patterns to Avoid

### Anti-Pattern: Controllers Accessing Repositories Directly

The existing `UploadController` accesses `UploadJobRepository`, `ValidationFindingRepository`, `PreconfiguredMasterRepository`, and `ValidationRuleConfigRepository` directly. This is already a smell. New controllers should delegate all persistence to service classes. Do not replicate this pattern in `TdsController`, `Gstr2bController`, or `InvoiceController`.

### Anti-Pattern: Bloating TallyParserService

`TallyParserService` already handles two parse modes (masters JSON, voucher JSON) and encoding detection. Adding day book parsing here would make it unmanageable. Create `TallyDayBookParser` as a separate `@Component` in `tally/services/` or a new `tally/parsers/` sub-package.

### Anti-Pattern: Synchronous AI Calls in the Request Thread

Even with virtual threads, a blocked virtual thread during an LLM call holds a carrier thread if any downstream I/O is not virtual-thread-aware. More importantly, the user experience degrades severely. Use `@Async` and return 202 immediately.

### Anti-Pattern: Hardcoding LLM Provider

Do not call OpenAI SDK directly from `InvoiceProcessingService`. Define an `InvoiceExtractionClient` interface and put the HTTP/SDK call in an `OpenAiInvoiceExtractionClient` implementation. This is necessary when prompt tuning reveals the need to switch models.

### Anti-Pattern: Storing the Tally Export JSON as a Blob

The export JSON can be tens of kilobytes to megabytes for large voucher sets. Build it on demand (the source data is already in PostgreSQL) and stream it as a download response. Do not store the assembled export in the database.

---

## Scalability Notes (Current Scope)

At the scale this application targets (10–50 client organizations per CA, monthly compliance cycles), the current monolith with synchronous-except-AI design is appropriate. The AI invoice processing async queue is the only place where volume could spike (e.g., a CA bulk-uploads 200 invoices at month end).

Mitigation without a message broker: use a bounded thread pool for the @Async executor (e.g., 4–8 threads) so LLM calls do not saturate the process. A `ThreadPoolTaskExecutor` named `invoiceTaskExecutor` with queue capacity of 500 is sufficient for v1.

---

*Sources: direct codebase analysis — ValidationOrchestrator.java, UploadController.java, TallyParserService.java, Voucher.java, Organization.java, User.java, ValidationRule.java, UploadJob.java*
