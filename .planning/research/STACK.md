# Technology Stack — New Feature Additions

**Project:** Super Accountant
**Researched:** 2026-04-09
**Scope:** New libraries/services needed for GSTR-2B reconciliation, TDS computation, AI invoice OCR, and report generation on top of the existing Spring Boot 4 / Java 25 backend.

**Note on web access:** External web search and WebFetch were unavailable during this research session. All recommendations are drawn from training knowledge (cutoff August 2025). Confidence levels reflect this constraint. Verify versions in Maven Central and official docs before pinning.

---

## 1. AI Invoice Extraction (OCR + LLM)

### Recommendation: Claude claude-sonnet-4-5 via Anthropic Java SDK (or HTTP client)

**Why Claude over GPT-4o:**
- Claude's vision models accept both image bytes (JPEG/PNG/WebP) and PDF documents natively — no separate OCR step needed for digital PDFs.
- Claude produces structured JSON output reliably when prompted with a schema; this is critical for extracting GSTIN, HSN codes, invoice number, line items, tax amounts, and date — all required fields for Indian GST invoices.
- Claude claude-sonnet-4-5 (as of mid-2025) hits the right cost/accuracy tradeoff. claude-opus-4-5 is more accurate but ~5x the cost; claude-haiku-4-5 is cheaper but less reliable on degraded scans or handwritten fields.
- Anthropic's API is vendor-managed, meaning no GPU infrastructure to run. This matches the project's current posture (no ML infra).
- GPT-4o is a valid alternative (comparable vision quality), but mixing two LLM providers for a single feature adds unnecessary operational complexity.

**Confidence: MEDIUM** — Quality comparisons between frontier models shift with each release. Benchmark before committing; both Claude claude-sonnet-4-5 and GPT-4o are credible choices as of August 2025. The architectural recommendation (single LLM vendor, structured output via prompt) is HIGH confidence regardless of which model wins.

**Java integration:**
- Anthropic does not publish an official Java SDK (as of August 2025). Use Spring's `RestClient` (Spring 6+, included in Spring Boot 4) or `WebClient` to call the Anthropic Messages API directly.
- The API is a simple HTTP POST with a JSON body — no SDK required.
- Alternatively, `anthropic-java` community clients exist on GitHub but are not officially supported.

**What NOT to use:**
- Tesseract OCR (via `tess4j` or `tesseract-maven-plugin`): produces raw text without semantic understanding. Extracting structured fields (GSTIN, HSN, tax breakdown) from raw text requires fragile regex/NLP post-processing. A vision-capable LLM replaces this entire pipeline.
- AWS Textract or Google Document AI: these are credible services but add a second cloud vendor dependency and require additional auth/credential management. The LLM-first approach extracts and structures in a single call.

### PDF Preprocessing: Apache PDFBox 3.x

Before sending a PDF to the LLM, check if it is text-based or image-based:
- Text-based PDFs (digital invoices from accounting software): extract text with PDFBox, then send text to the LLM. Cheaper and faster than sending image bytes.
- Image-based PDFs (scanned invoices): render page(s) to PNG (PDFBox `PDFRenderer`), then send the image to the LLM vision endpoint.

**Library:** `org.apache.pdfbox:pdfbox:3.0.3`

**Why PDFBox 3.x over iText:**
- Apache License 2.0 — no LGPL/AGPL dual-licensing concerns (iText 7 is AGPL for open/commercial; licensing cost for a commercial SaaS).
- PDFBox 3.0 is a major rewrite with modern API; actively maintained by Apache.
- `PDFTextStripper` for text extraction and `PDFRenderer` for image rendering cover both branches of the pipeline with a single dependency.

**Confidence: HIGH** — PDFBox is the standard Apache-licensed Java PDF library. The 3.x GA release is production-ready.

**Maven dependency:**
```xml
<dependency>
    <groupId>org.apache.pdfbox</groupId>
    <artifactId>pdfbox</artifactId>
    <version>3.0.3</version>
</dependency>
```

**Image format for LLM submission:** PNG (lossless). Render at 150–200 DPI for scanned invoices — sufficient for LLM vision without excessive payload size. Use `javax.imageio.ImageIO` (JDK stdlib) to write `BufferedImage` to PNG bytes; no additional library required.

---

## 2. GSTR-2B Reconciliation

### No external library — domain logic built in-house

There is no established open-source Java library for GSTR-2B reconciliation as of August 2025. Indian fintech libraries in Java are sparse and often abandoned. The reconciliation algorithm is not complex enough to justify a dependency:

1. Parse uploaded GSTR-2B JSON (from the GST portal) — this is a well-documented JSON schema from GSTN.
2. Parse Tally purchase voucher data (already done by `TallyParserService` / new JSON parser).
3. Match on: supplier GSTIN + invoice number + invoice date + taxable amount (with configurable tolerance).
4. Output: matched, unmatched in GSTR-2B, unmatched in Tally — three buckets.

**Jackson** (`com.fasterxml.jackson.core:jackson-databind`, already in the project) handles GSTR-2B JSON parsing. No new JSON library needed.

**GSTR-2B JSON schema reference:** https://developer.gst.gov.in — verify the current portal export schema before implementing the parser, as GSTN has revised it multiple times.

**Confidence: HIGH** (for the build-in-house recommendation) / LOW (for schema stability — always verify against current GSTN portal exports).

---

## 3. TDS Computation

### No external library — rule table in application

TDS computation logic (rate lookup by section: 194C, 194J, 194H, 194I, etc.; threshold checks; surcharge for companies vs individuals) changes annually via Finance Act amendments. An external library that encodes these rules would be stale within months and would be a trust risk for compliance software.

**Recommendation:** Implement a `TdsRuleEngine` service backed by a DB table (`tds_section_rules`) with columns: `section_code`, `payee_type`, `threshold_amount`, `rate_percentage`, `effective_from`, `effective_to`. Seed the table from `DataInitializer` (same pattern as validation rule configs already in the project). Rates can be updated via admin API without code deployment.

This is the same Strategy Pattern already in the codebase for validation rules — apply it to TDS sections.

**Why not ClearTax / Cleartax GST SDK or similar:** These are SaaS platforms, not embeddable Java libraries. Integration would mean sending taxpayer data to a third party — a non-starter for a compliance application.

**Confidence: HIGH** (architectural recommendation). TDS rule tables and their seeding are standard practice in Indian fintech.

---

## 4. Report Generation (TDS Summary, Mismatch Reports)

### Recommendation: Apache POI for Excel, plain Java for JSON export

**Apache POI 5.x** for Excel output:

```xml
<dependency>
    <groupId>org.apache.poi</groupId>
    <artifactId>poi-ooxml</artifactId>
    <version>5.3.0</version>
</dependency>
```

**Why POI over JasperReports:**
- JasperReports is optimized for pixel-perfect PDF reports with a design-time layout tool (Jasper Studio). The reports here — TDS liability summary, deductee-wise breakdown, GSTR-2B mismatch — are tabular data, not designed documents. CAs expect Excel, not PDF.
- POI is a lightweight, well-maintained Apache library with no external tooling dependency.
- The SXSSF streaming writer (`SXSSFWorkbook`) handles large datasets (thousands of voucher rows) without memory issues.
- POI 5.3.0 is the current stable release (verify on Maven Central).

**For PDF report output (if needed later):** Add `org.apache.pdfbox:pdfbox:3.0.3` (already recommended above) — PDFBox can generate simple PDFs. For complex layouts, iText 7 Community or OpenPDF are options, but defer until PDF is a stated requirement.

**Confidence: HIGH** (POI for Excel is the standard choice). MEDIUM on the version number — verify current stable on https://poi.apache.org.

**What NOT to use:**
- JasperReports: overcomplicated for tabular export; requires `.jrxml` design files; large dependency footprint.
- iText 7: AGPL license — commercial use requires a paid license. Only add if absolutely necessary.

---

## 5. Tally JSON Parsing (Day Book Format)

### Recommendation: Jackson with custom deserializers — no new library

**Jackson** is already in the project. The Tally JSON day book format is a non-standard JSON structure (Tally's own schema, not a well-known standard). The correct approach:

1. Define POJOs mirroring the Tally JSON structure (`TallyDayBook`, `TallyVoucher`, `TallyLedgerEntry`).
2. Write `@JsonDeserializer` custom classes for any non-standard field shapes (Tally JSON sometimes uses arrays-as-objects).
3. `TallyJsonParserService` parallel to the existing `TallyParserService` (XML), same contract, different format.

No additional JSON library (Gson, Moshi, etc.) is warranted.

**Confidence: HIGH** — Jackson is already a transitive dependency; adding a parallel parser using the same library keeps the dependency graph clean.

---

## 6. File Upload Handling

### Already in place — no new library needed

The project already configures Spring's multipart support with 50MB limits. For AI invoice processing, the upload endpoint will accept `multipart/form-data` with an image or PDF file — same pattern as the existing masters upload endpoint.

**Size consideration for AI:** Rendered PDF pages as PNG at 150 DPI can be 1–3 MB per page. A 10-page PDF would be ~30 MB. The existing 50MB limit accommodates single invoices; set a page count guard (e.g., max 5 pages per invoice upload) to prevent abuse.

**Confidence: HIGH**.

---

## 7. Async Processing for AI Calls

### Recommendation: Spring `@Async` with a bounded `ThreadPoolTaskExecutor`

LLM API calls for invoice extraction will take 3–15 seconds. These must not block the HTTP request thread.

**Pattern:**
- Controller accepts the upload, creates an `InvoiceProcessingJob` record (same pattern as `UploadJob`), returns immediately with a job ID.
- A `@Service` method annotated `@Async` calls the Anthropic API, updates the job record with extracted data and status.
- Frontend polls `GET /api/invoice-jobs/{id}` for status, or a WebSocket push can be added later.

**Spring's `@Async`** is already available (Spring Boot 4 / Spring Framework 6). No additional library needed. Configure a dedicated `ThreadPoolTaskExecutor` bean with a bounded queue (not the default unbounded) to prevent memory pressure under load.

**What NOT to use right now:**
- Spring Batch: heavyweight; designed for large scheduled batch workloads, not per-request async jobs.
- Kafka/RabbitMQ: message broker adds significant infrastructure complexity. The polling pattern with `@Async` is sufficient until throughput demands a queue (hundreds of concurrent uploads).

**Confidence: HIGH** — `@Async` with job status polling is the standard Spring pattern for long-running tasks without adding broker infrastructure.

---

## 8. JWT Upgrade

### Recommendation: Upgrade jjwt from 0.11.5 to 0.12.x

The current stack uses `io.jsonwebtoken:jjwt-api:0.11.5`. jjwt 0.12.x (released 2023, stable as of 2024) introduced a substantially cleaner builder API, dropped deprecated methods, and improved algorithm support. The 0.11.x API will eventually be removed.

This is not strictly required for the new features but is the right time to do it since the auth layer will be touched to add new roles.

```xml
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.12.6</version>
</dependency>
```

**Confidence: MEDIUM** — Verify current stable version at https://github.com/jwtk/jjwt/releases. The migration between 0.11 and 0.12 is breaking (different builder method signatures) — plan a dedicated task.

---

## Complete Dependency Summary

### New Backend Dependencies (Maven)

| Artifact | Version | Purpose | Confidence |
|---|---|---|---|
| `org.apache.pdfbox:pdfbox` | 3.0.3 | PDF text extraction + image rendering for invoice OCR | HIGH |
| `org.apache.poi:poi-ooxml` | 5.3.0 | Excel report generation (TDS summary, GSTR-2B mismatch) | HIGH |
| `io.jsonwebtoken:jjwt-api` | 0.12.6 | JWT upgrade (replace 0.11.5) | MEDIUM |

**No new library needed for:**
- GSTR-2B JSON parsing (Jackson, already present)
- Tally JSON day book parsing (Jackson, already present)
- TDS computation (DB-driven rule table, Spring service)
- Async processing (Spring `@Async`, already in Spring Boot)
- LLM HTTP calls (Spring `RestClient`, already in Spring Boot 4)

### New Infrastructure (No additional Java library)

| Service | Provider | Integration Method |
|---|---|---|
| LLM vision + structured extraction | Anthropic Messages API (Claude claude-sonnet-4-5) | HTTP via Spring `RestClient` |

---

## Alternatives Considered

| Category | Recommended | Alternative | Why Not |
|---|---|---|---|
| AI extraction | Claude claude-sonnet-4-5 (Anthropic API) | GPT-4o (OpenAI API) | Both are valid; Claude is recommended here — reassess if costs shift significantly |
| AI extraction | Claude claude-sonnet-4-5 (Anthropic API) | AWS Textract + NLP pipeline | Second cloud vendor; Textract doesn't understand GST semantics; LLM single-call is simpler |
| AI extraction | Claude claude-sonnet-4-5 (Anthropic API) | Tesseract OCR (tess4j) | Raw text only; fragile post-processing required to extract structured fields |
| PDF processing | Apache PDFBox 3.x | iText 7 | iText is AGPL; licensing cost for commercial SaaS |
| Report generation | Apache POI 5.x | JasperReports | JasperReports is design-file-heavy; tabular Excel is what CAs actually use |
| Report generation | Apache POI 5.x | OpenPDF / iText | PDF not the primary format needed; add when explicitly required |
| TDS/GST rules | In-house DB rule table | ClearTax SDK | ClearTax is a SaaS platform, not embeddable; sends taxpayer data to third party |
| Async processing | Spring `@Async` + polling | Kafka / RabbitMQ | Message broker is premature for this throughput; adds infra complexity |
| GSTR-2B parsing | Jackson (existing) | Any additional JSON library | No benefit; Jackson is already a managed dependency |

---

## Installation

```xml
<!-- Add to Service/superaccountant/pom.xml <dependencies> -->

<!-- PDF processing for invoice OCR pipeline -->
<dependency>
    <groupId>org.apache.pdfbox</groupId>
    <artifactId>pdfbox</artifactId>
    <version>3.0.3</version>
</dependency>

<!-- Excel report generation -->
<dependency>
    <groupId>org.apache.poi</groupId>
    <artifactId>poi-ooxml</artifactId>
    <version>5.3.0</version>
</dependency>

<!-- JWT upgrade (replace existing 0.11.5 entries) -->
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.12.6</version>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-impl</artifactId>
    <version>0.12.6</version>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-jackson</artifactId>
    <version>0.12.6</version>
    <scope>runtime</scope>
</dependency>
```

**New environment variable (do not hardcode in application.properties):**
```
ANTHROPIC_API_KEY=sk-ant-...
```

Inject via `@Value("${anthropic.api.key}")` and bind from env in `application.properties`:
```properties
anthropic.api.key=${ANTHROPIC_API_KEY}
```

---

## Sources

- Apache PDFBox: https://pdfbox.apache.org (training knowledge, version ~3.0.x confirmed stable as of Aug 2025)
- Apache POI: https://poi.apache.org (training knowledge, 5.x series confirmed stable as of Aug 2025)
- Anthropic Messages API: https://docs.anthropic.com/en/api/messages (training knowledge)
- jjwt changelog: https://github.com/jwtk/jjwt/releases (training knowledge; verify current version before pinning)
- GSTN developer portal: https://developer.gst.gov.in (GSTR-2B schema; must verify current schema version independently)
- Spring RestClient (Spring 6 / Spring Boot 3+): https://docs.spring.io/spring-framework/reference/integration/rest-clients.html (HIGH confidence — RestClient is the modern replacement for RestTemplate in Spring 6)

**Versions flagged for verification before pinning:**
- PDFBox 3.0.3 — check https://mvnrepository.com/artifact/org.apache.pdfbox/pdfbox
- POI 5.3.0 — check https://mvnrepository.com/artifact/org.apache.poi/poi-ooxml
- jjwt 0.12.6 — check https://mvnrepository.com/artifact/io.jsonwebtoken/jjwt-api
- Claude claude-sonnet-4-5 model ID — check https://docs.anthropic.com/en/docs/about-claude/models for the current recommended model slug
