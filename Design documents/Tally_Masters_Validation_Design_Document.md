# Design Document: Tally Masters Upload, Validation & Mismatch Detection

| Field | Value |
|-------|-------|
| Document Version | 1.0 |
| Date | March 2026 |
| Status | Draft |
| Tech Stack | Java Spring Boot, React 19, Tailwind CSS, PostgreSQL, REST API |

---

## 1. Executive Summary

This document describes the design for a feature that allows users to upload a Tally-generated masters JSON file, validate the uploaded ledger masters against a set of configurable rules, and display any detected mismatches in the user interface. The primary validation compares uploaded ledger masters in the categories of Purchase, Expense, Income, GST, and TDS against a set of pre-configured master ledgers maintained by the user within the application.

The system is designed with extensibility as a core principle: validation rules are pluggable, versionable, and can be added, modified, or removed without requiring changes to the core processing pipeline.

---

## 2. Background & Context

### 2.1 Tally Masters Overview

TallyPrime is a widely-used accounting software in India. Accounting masters in Tally primarily consist of Groups, Ledgers, and Voucher Types. A Ledger is the fundamental accounting head used to identify transactions and generate reports. Every ledger must belong to a parent Group, and Tally provides 28 predefined groups (e.g., Sundry Debtors, Sundry Creditors, Purchase Accounts, Sales Accounts, Duties & Taxes, etc.).

From TallyPrime 7.0 onwards, native JSON data exchange is supported. When masters are exported from TallyPrime in JSON format, the file is wrapped in a `tallymessage` envelope that contains an array of ledger objects. Each ledger object carries fields such as the ledger name, parent group, GUID, master ID, opening balance, GST registration details, tax type, and various statutory fields.

### 2.2 Tally Masters JSON Structure

The following represents the expected structure of a Tally-exported masters JSON file. The system must be tolerant of both the native JSON structure and the JSONEX structure introduced in TallyPrime 7.0.

```json
{
  "tallymessage": [
    {
      "ledger": {
        "@name": "Purchase - Raw Materials",
        "@reservedname": "",
        "guid": "a1b2c3d4-...",
        "masterid": "12345",
        "alterid": "67890",
        "parent": "Purchase Accounts",
        "taxclassificationname": "",
        "taxtype": "GST",
        "gstregistrationtype": "Regular",
        "gstin": "27AAAAA0000A1Z5",
        "countryname": "India",
        "statename": "Maharashtra",
        "openingbalance": "0",
        "isdeemedpositive": "No",
        "affectsstock": "No",
        "istdsapplicable": "Yes",
        "istcsapplicable": "No",
        "isgstapplicable": "Applicable",
        "ledgerfbtcategory": "",
        "ledgermailingname": "Purchase - Raw Materials",
        "address.list": { "address": ["123 Main Street"] },
        "ledgerphone": "",
        "ledgeremail": ""
      }
    }
  ]
}
```

### 2.3 Ledger Category Classification

The system classifies uploaded ledger masters into categories based on their parent group in Tally. Only ledgers falling into the following five categories are subject to the mismatch comparison rule. All other ledgers are ignored during comparison.

| Category | Tally Parent Groups | Description |
|----------|-------------------|-------------|
| Purchase | `Purchase Accounts`, sub-groups under Purchase Accounts | Ledgers for raw material, goods, or services purchased |
| Expense | `Direct Expenses`, `Indirect Expenses`, and their sub-groups | Ledgers for operating and non-operating expenses |
| Income | `Direct Incomes`, `Indirect Incomes`, `Sales Accounts`, and their sub-groups | Ledgers for revenue streams and other income |
| GST | `Duties & Taxes` (where tax type = GST), CGST, SGST, IGST ledgers | GST-related statutory ledgers such as Input CGST, Output SGST, etc. |
| TDS | `Duties & Taxes` (where TDS applicable = Yes), TDS payable/receivable groups | TDS-related statutory ledgers for deduction and payment |

---

## 3. Functional Requirements

### 3.1 Pre-configured Masters Management

Users must be able to configure expected master ledgers for each of the five categories (Purchase, Expense, Income, GST, TDS) before uploading a Tally file. This configuration serves as the baseline against which uploaded masters are compared. Users can add, edit, and delete pre-configured masters. Each pre-configured master stores the ledger name, category, expected parent group, and optional metadata such as expected GST applicability or TDS nature.

### 3.2 File Upload

The system shall provide a file upload interface that accepts JSON files exported from TallyPrime. Upon successful upload, the UI shall immediately display the status as "Processing". The upload shall be asynchronous: the file is persisted, a processing job is enqueued, and the user receives a reference ID to track the status. Maximum file size shall be configurable (default 50 MB). Only `.json` files are accepted; other formats are rejected with an appropriate error message.

### 3.3 Validation Pipeline

The backend shall process the uploaded JSON in two phases:

**Phase 1 – Structural Validation**

- Verify the JSON is well-formed and parseable.
- Verify the presence of the `tallymessage` envelope.
- Verify each ledger object contains the mandatory fields: `name`, `parent`, and `guid`.
- Reject the file if structural validation fails, updating the status to "Failed" with descriptive error messages.

**Phase 2 – Rule-based Validation**

- Apply a configurable set of validation rules to the parsed ledgers.
- The primary rule (Mismatch Detection Rule) compares uploaded ledgers in the five categories against the pre-configured masters.
- Additional rules can be added in future (e.g., duplicate detection, group hierarchy validation, GST compliance checks).
- Each rule produces a list of findings (mismatches, warnings, or errors).
- The final status is set to "Completed" with a summary of findings or "Completed with Mismatches" if any mismatch is detected.

### 3.4 Mismatch Detection Logic

The mismatch detection rule compares uploaded ledgers with pre-configured masters within each category. The following types of mismatches are detected:

| Mismatch Type | Description | Severity |
|--------------|-------------|----------|
| Missing in Upload | A pre-configured master is not found in the uploaded file | Warning |
| Missing in Configuration | An uploaded ledger (in a relevant category) has no matching pre-configured master | Warning |
| Name Mismatch | Ledger names differ in casing or contain extra whitespace between uploaded and configured | Info |
| Parent Group Mismatch | The ledger exists in both but is assigned to a different parent group | Error |
| GST Applicability Mismatch | GST-related flags differ between uploaded and configured ledger | Error |
| TDS Applicability Mismatch | TDS applicability flags differ between uploaded and configured ledger | Error |

### 3.5 Mismatch Display

The UI shall display mismatches grouped by category (Purchase, Expense, Income, GST, TDS). Each mismatch entry shall show the ledger name, mismatch type, expected value (from configuration), actual value (from upload), and severity. Users can filter by category, severity, and mismatch type. Users can export the mismatch report as CSV or PDF.

---

## 4. System Architecture

### 4.1 High-Level Architecture

The system follows a standard three-tier architecture: a React 19 single-page application as the frontend, a Spring Boot REST API as the backend, and PostgreSQL as the persistent data store. File processing is handled asynchronously using Spring's task executor to avoid blocking the upload request.

```
┌─────────────────┐    REST API     ┌────────────────────┐    JDBC     ┌──────────────┐
│  React 19 SPA   │ ──────────────▶ │  Spring Boot API   │ ─────────▶ │  PostgreSQL  │
│  Tailwind CSS   │                 │  (REST + Async)    │            │              │
└─────────────────┘                 └────────────────────┘            └──────────────┘
                                            │
                                    ┌───────┴─────────┐
                                    │  Async Processor │
                                    │  (Rule Engine)   │
                                    └─────────────────┘
```

### 4.2 Component Diagram

| Component | Technology | Responsibility |
|-----------|-----------|----------------|
| Upload UI | React 19, Tailwind CSS | File selection, drag-and-drop upload, status polling, mismatch display |
| Pre-config UI | React 19, Tailwind CSS | CRUD interface for managing pre-configured master ledgers by category |
| Upload Controller | Spring Boot REST | Accepts multipart file upload, returns upload ID, triggers async processing |
| Status Controller | Spring Boot REST | Returns current processing status and results for a given upload ID |
| Pre-config Controller | Spring Boot REST | CRUD REST endpoints for pre-configured master management |
| File Parser Service | Java (Jackson) | Parses Tally JSON, extracts ledger objects into in-memory `ParsedLedger` DTOs; handles both native and JSONEX formats |
| Validation Orchestrator | Spring (Async) | Runs Phase 1 and Phase 2 validations sequentially, updates status in DB |
| Rule Engine | Java (Strategy Pattern) | Maintains a registry of validation rules; executes applicable rules on parsed data |
| Mismatch Detection Rule | Java | Compares uploaded ledgers with pre-configured masters by category |
| Database | PostgreSQL | Persists uploads, pre-configured masters, validation rules, and findings |

---

## 5. Database Design

### 5.1 Entity Relationship Overview

The database schema consists of four primary tables. The design supports multi-tenancy through an `organization_id` column on all user-facing tables.

Notably, uploaded ledger masters are **not** persisted to the database. Since users may upload the same or updated masters JSON file multiple times, storing parsed ledgers would create redundant data with no long-term value. Instead, the File Parser Service parses the JSON into in-memory `ParsedLedger` objects that are passed directly to the rule engine for validation. Only the resulting findings (mismatches) are persisted, keeping the database lean and the processing pipeline stateless.

### 5.2 Table: `upload_jobs`

| Column | Type | Constraints | Description |
|--------|------|------------|-------------|
| id | UUID | PK | Unique upload job identifier |
| organization_id | UUID | FK, NOT NULL | Tenant identifier |
| file_name | VARCHAR(500) | NOT NULL | Original uploaded file name |
| file_path | VARCHAR(1000) | NOT NULL | Storage path of the uploaded JSON file |
| file_size_bytes | BIGINT | NOT NULL | File size in bytes |
| status | VARCHAR(50) | NOT NULL | UPLOADED, PROCESSING, COMPLETED, COMPLETED_WITH_MISMATCHES, FAILED |
| error_message | TEXT | NULLABLE | Error details if status is FAILED |
| total_ledgers_parsed | INTEGER | DEFAULT 0 | Count of ledgers extracted from file |
| total_mismatches | INTEGER | DEFAULT 0 | Count of mismatches detected |
| uploaded_by | VARCHAR(255) | NOT NULL | User who uploaded the file |
| created_at | TIMESTAMP | NOT NULL | Upload timestamp |
| completed_at | TIMESTAMP | NULLABLE | Processing completion timestamp |

### 5.3 Table: `preconfigured_masters`

| Column | Type | Constraints | Description |
|--------|------|------------|-------------|
| id | UUID | PK | Unique record identifier |
| organization_id | UUID | FK, NOT NULL | Tenant identifier |
| ledger_name | VARCHAR(500) | NOT NULL | Expected ledger name |
| category | VARCHAR(50) | NOT NULL | PURCHASE, EXPENSE, INCOME, GST, or TDS |
| expected_parent_group | VARCHAR(500) | NOT NULL | Expected parent group in Tally |
| expected_gst_applicable | VARCHAR(50) | NULLABLE | Expected GST applicability |
| expected_tds_applicable | VARCHAR(50) | NULLABLE | Expected TDS applicability |
| is_active | BOOLEAN | DEFAULT true | Soft delete flag |
| created_at | TIMESTAMP | NOT NULL | Record creation timestamp |
| updated_at | TIMESTAMP | NOT NULL | Last modification timestamp |

### 5.4 Table: `validation_rules`

| Column | Type | Constraints | Description |
|--------|------|------------|-------------|
| id | UUID | PK | Unique rule identifier |
| rule_code | VARCHAR(100) | UNIQUE, NOT NULL | Machine-readable rule identifier (e.g., MISMATCH_DETECTION) |
| rule_name | VARCHAR(255) | NOT NULL | Human-readable rule name |
| description | TEXT | NULLABLE | Rule description |
| rule_class | VARCHAR(500) | NOT NULL | Fully qualified Java class implementing ValidationRule interface |
| is_active | BOOLEAN | DEFAULT true | Enable/disable rule without code changes |
| execution_order | INTEGER | NOT NULL | Order in which rules are executed |
| created_at | TIMESTAMP | NOT NULL | Record creation timestamp |

### 5.5 Table: `validation_findings`

| Column | Type | Constraints | Description |
|--------|------|------------|-------------|
| id | UUID | PK | Unique finding identifier |
| upload_job_id | UUID | FK → upload_jobs | Reference to the upload job |
| rule_code | VARCHAR(100) | NOT NULL | Rule that generated this finding |
| category | VARCHAR(50) | NULLABLE | PURCHASE, EXPENSE, INCOME, GST, TDS |
| mismatch_type | VARCHAR(100) | NOT NULL | e.g., MISSING_IN_UPLOAD, PARENT_GROUP_MISMATCH |
| ledger_name | VARCHAR(500) | NOT NULL | Affected ledger name |
| expected_value | TEXT | NULLABLE | Value from pre-configured master |
| actual_value | TEXT | NULLABLE | Value from uploaded master |
| severity | VARCHAR(20) | NOT NULL | ERROR, WARNING, or INFO |
| message | TEXT | NOT NULL | Human-readable finding description |
| created_at | TIMESTAMP | NOT NULL | Record creation timestamp |

### 5.6 Indexes

```sql
CREATE INDEX idx_findings_job_category ON validation_findings (upload_job_id, category);
CREATE INDEX idx_findings_job_severity ON validation_findings (upload_job_id, severity);
CREATE INDEX idx_preconfig_org_category ON preconfigured_masters (organization_id, category);
CREATE INDEX idx_upload_jobs_org_status ON upload_jobs (organization_id, status);
```

### 5.7 In-Memory DTO: `ParsedLedger`

This object is **not persisted**. It is constructed during JSON parsing and lives only for the duration of the validation pipeline.

```java
public record ParsedLedger(
    String tallyGuid,
    String tallyMasterId,
    String ledgerName,
    String parentGroup,
    LedgerCategory category,       // Derived from parentGroup
    String taxType,
    String isGstApplicable,
    String isTdsApplicable,
    String gstin,
    String openingBalance,
    Map<String, Object> rawFields  // All original fields for future rule access
) {}

public enum LedgerCategory {
    PURCHASE, EXPENSE, INCOME, GST, TDS, OTHER
}
```

---

## 6. REST API Design

### 6.1 API Endpoints

| Method | Endpoint | Description | Request / Response |
|--------|----------|-------------|-------------------|
| POST | `/api/v1/uploads` | Upload Tally masters JSON file | Multipart file → `{ uploadId, status }` |
| GET | `/api/v1/uploads/{id}/status` | Poll processing status | → `{ status, totalLedgers, totalMismatches }` |
| GET | `/api/v1/uploads/{id}/mismatches` | Get mismatch findings | Query: category, severity, page → Paginated findings |
| GET | `/api/v1/uploads/{id}/mismatches/export` | Export mismatches as CSV | Query: format=csv → File download |
| GET | `/api/v1/uploads` | List all uploads | Query: page, size, status → Paginated upload list |
| GET | `/api/v1/preconfigured-masters` | List pre-configured masters | Query: category, page → Paginated list |
| POST | `/api/v1/preconfigured-masters` | Create pre-configured master | JSON body → Created master |
| PUT | `/api/v1/preconfigured-masters/{id}` | Update pre-configured master | JSON body → Updated master |
| DELETE | `/api/v1/preconfigured-masters/{id}` | Soft-delete pre-configured master | → 204 No Content |
| POST | `/api/v1/preconfigured-masters/bulk` | Bulk import pre-configured masters | JSON array → Import summary |
| GET | `/api/v1/validation-rules` | List active validation rules | → List of rules with status |

### 6.2 Upload Response Example

```json
{
  "uploadId": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "fileName": "masters-export-2026.json",
  "status": "PROCESSING",
  "message": "File uploaded successfully. Processing has started.",
  "createdAt": "2026-03-20T14:30:00Z"
}
```

### 6.3 Mismatch Response Example

```json
{
  "uploadId": "f47ac10b-...",
  "status": "COMPLETED_WITH_MISMATCHES",
  "summary": {
    "totalLedgersParsed": 142,
    "totalMismatches": 7,
    "byCategory": { "PURCHASE": 2, "GST": 3, "TDS": 1, "EXPENSE": 1 },
    "bySeverity": { "ERROR": 4, "WARNING": 2, "INFO": 1 }
  },
  "findings": [
    {
      "id": "...",
      "category": "GST",
      "mismatchType": "MISSING_IN_UPLOAD",
      "ledgerName": "Input CGST @9%",
      "expectedValue": "Present in pre-configured masters",
      "actualValue": "Not found in uploaded file",
      "severity": "WARNING",
      "message": "Pre-configured GST ledger not found in upload"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 7
}
```

---

## 7. Validation Rule Engine Design

### 7.1 Design Pattern

The rule engine uses the **Strategy Pattern** combined with a Spring-managed registry. Each validation rule implements a common `ValidationRule` interface. Rules are discovered at application startup via Spring's component scanning and registered in the rule registry. The database table `validation_rules` controls which rules are active and their execution order, allowing rules to be enabled or disabled at runtime without redeployment.

### 7.2 ValidationRule Interface

```java
public interface ValidationRule {

    String getRuleCode();

    String getRuleName();

    List<ValidationFinding> validate(
        UploadJob job,
        List<ParsedLedger> parsedLedgers,
        ValidationContext context
    );

    boolean supports(UploadJob job);
}
```

### 7.3 ValidationContext

```java
public record ValidationContext(
    UUID organizationId,
    List<PreconfiguredMaster> preconfiguredMasters,
    Map<String, Object> settings
) {}
```

### 7.4 Rule Execution Flow

1. The Validation Orchestrator loads all active rules from the database, sorted by `execution_order`.
2. For each rule, it checks whether the rule supports the current upload job (via the `supports()` method).
3. Supported rules are executed in order; each rule receives the in-memory list of `ParsedLedger` objects and a `ValidationContext` that includes pre-configured masters and organization settings.
4. Each rule returns a list of `ValidationFinding` objects.
5. All findings are aggregated and persisted to the `validation_findings` table.
6. The upload job status is updated based on the aggregated findings.

### 7.5 Adding New Rules

To add a new validation rule in the future, a developer needs to:

1. Create a new Java class that implements the `ValidationRule` interface and annotate it with `@Component`.
2. Insert a corresponding row into the `validation_rules` table with the rule's class name, code, and execution order.
3. The rule will be automatically picked up on the next application restart or via a refresh endpoint.

No changes to the orchestrator, controller, or existing rules are required.

### 7.6 Mismatch Detection Rule Implementation Sketch

```java
@Component
public class MismatchDetectionRule implements ValidationRule {

    @Override
    public String getRuleCode() { return "MISMATCH_DETECTION"; }

    @Override
    public String getRuleName() { return "Pre-configured Masters Mismatch Detection"; }

    @Override
    public boolean supports(UploadJob job) { return true; }

    @Override
    public List<ValidationFinding> validate(
            UploadJob job,
            List<ParsedLedger> parsedLedgers,
            ValidationContext context) {

        List<ValidationFinding> findings = new ArrayList<>();
        List<PreconfiguredMaster> configuredMasters = context.preconfiguredMasters();

        // Filter uploaded ledgers to only the 5 relevant categories
        Map<LedgerCategory, List<ParsedLedger>> uploadedByCategory = parsedLedgers.stream()
            .filter(l -> l.category() != LedgerCategory.OTHER)
            .collect(Collectors.groupingBy(ParsedLedger::category));

        Map<LedgerCategory, List<PreconfiguredMaster>> configuredByCategory = configuredMasters.stream()
            .collect(Collectors.groupingBy(m -> LedgerCategory.valueOf(m.getCategory())));

        for (LedgerCategory category : LedgerCategory.values()) {
            if (category == LedgerCategory.OTHER) continue;

            List<ParsedLedger> uploaded = uploadedByCategory.getOrDefault(category, List.of());
            List<PreconfiguredMaster> configured = configuredByCategory.getOrDefault(category, List.of());

            // Check: Missing in Upload
            // Check: Missing in Configuration
            // Check: Parent Group Mismatch
            // Check: GST/TDS Applicability Mismatch
            // ... (comparison logic per mismatch type)
        }

        return findings;
    }
}
```

### 7.7 Planned Future Rules

| Rule Code | Description | Priority |
|-----------|-------------|----------|
| DUPLICATE_LEDGER_DETECTION | Detect duplicate ledger names within the uploaded file | Medium |
| GROUP_HIERARCHY_VALIDATION | Validate that ledger parent groups follow expected Tally hierarchy | Medium |
| GST_COMPLIANCE_CHECK | Verify GST registration type, GSTIN format, and state code consistency | High |
| OPENING_BALANCE_VALIDATION | Flag ledgers with unexpected or zero opening balances | Low |
| CROSS_CATEGORY_CONSISTENCY | Ensure no ledger appears under conflicting categories | Medium |

---

## 8. Frontend Design

### 8.1 Page Structure

| Page / Component | Route | Description |
|-----------------|-------|-------------|
| Pre-configured Masters | `/settings/masters` | CRUD table for managing expected masters by category with tabbed navigation |
| Upload Page | `/uploads/new` | Drag-and-drop upload zone, file validation, and status display |
| Upload History | `/uploads` | List of past uploads with status, date, and mismatch count |
| Upload Detail / Results | `/uploads/:id` | Processing status, summary cards, and mismatch table with filters |
| Mismatch Detail Modal | (overlay) | Side panel showing full detail of a single mismatch with expected vs actual |

### 8.2 Upload & Processing Flow (UI States)

| State | UI Treatment | User Action Available |
|-------|-------------|----------------------|
| Idle | Drag-and-drop zone with file browse button; accepted format reminder (.json) | Select or drag file |
| Uploading | Progress bar showing upload percentage; file name displayed | Cancel upload |
| Processing | Animated spinner with "Processing" label; auto-polls status every 3 seconds | Wait or navigate away (status persisted) |
| Completed | Green success banner; summary cards (total ledgers, categories, zero mismatches) | View details, upload another |
| Completed with Mismatches | Amber warning banner; summary cards with mismatch counts by category and severity | View mismatches, filter, export, upload another |
| Failed | Red error banner with error message from backend | Retry upload, view error details |

### 8.3 Mismatch Results Table

The mismatch table on the upload detail page includes the following columns:

- **Category** – color-coded badge (e.g., blue for Purchase, green for Income)
- **Ledger Name** – the affected ledger
- **Mismatch Type** – e.g., "Missing in Upload", "Parent Group Mismatch"
- **Expected Value** – from pre-configured masters
- **Actual Value** – from uploaded file
- **Severity** – icon: red circle for Error, yellow triangle for Warning, blue info for Info

Filtering: category (multi-select dropdown), severity (toggle chips), free-text search on ledger name. Pagination is server-side with configurable page size (default 20).

---

## 9. End-to-End Processing Flow

**Step 1:** The user navigates to the upload page and selects or drags a `.json` file. The React frontend sends a POST multipart request to `/api/v1/uploads`.

**Step 2:** The Upload Controller validates the file extension and size, persists the file to the configured storage location (local filesystem or cloud storage), creates an `upload_jobs` record with status `UPLOADED`, and returns the upload ID to the frontend.

**Step 3:** The controller dispatches an asynchronous event (`Spring ApplicationEvent`) that is picked up by the Validation Orchestrator running on a separate thread pool.

**Step 4:** The Orchestrator updates the job status to `PROCESSING`. It invokes the File Parser Service, which reads the JSON file, navigates the `tallymessage` structure, and extracts all ledger objects. Each ledger is mapped to an in-memory `ParsedLedger` object with its category derived from the parent group. These objects are held in memory for the duration of validation and are **not persisted** to the database, since the user may upload updated files multiple times and there is no need to retain raw parsed data.

**Step 5:** If parsing fails (malformed JSON, missing envelope, missing required fields), the job status is set to `FAILED` with a descriptive error message, and processing stops.

**Step 6:** If parsing succeeds, the Orchestrator fetches active validation rules from the database and executes them in order. The Mismatch Detection Rule loads the organization's pre-configured masters, groups uploaded ledgers by category (filtering to only PURCHASE, EXPENSE, INCOME, GST, TDS), and performs the comparison. Findings are generated and persisted to `validation_findings`.

**Step 7:** The job status is updated to `COMPLETED` (if no findings) or `COMPLETED_WITH_MISMATCHES`, along with summary counts.

**Step 8:** Meanwhile, the React frontend polls `GET /api/v1/uploads/{id}/status` every 3 seconds. Once the status is no longer `PROCESSING`, polling stops and the UI transitions to the appropriate state. If mismatches exist, the frontend fetches the paginated findings from the mismatches endpoint and renders the results table.

---

## 10. Non-Functional Requirements

| Aspect | Requirement |
|--------|------------|
| Performance | File parsing and validation of 10,000 ledgers shall complete within 30 seconds |
| Scalability | The async processor shall support configurable thread pool size (default 4) to handle concurrent uploads |
| File Size | Maximum upload size is configurable; default 50 MB; files exceeding this are rejected at the controller level |
| Security | All endpoints require authentication (JWT); file uploads are scanned for valid JSON before processing |
| Data Retention | Upload jobs and their associated findings are retained for 90 days by default (configurable); a scheduled cleanup job purges expired records |
| Error Handling | All exceptions during processing are caught, logged, and reflected in the job status; the user never sees a raw stack trace |
| Idempotency | Re-uploading the same file creates a new job; previous results are preserved for comparison |
| Observability | Key metrics exposed: upload count, processing time, mismatch rate, failure rate; structured logging for all pipeline stages |
| Database Indexing | Indexes on `upload_job_id + category` for findings; index on `organization_id + category` for pre-configured masters |

---

## 11. Migration & Deployment Strategy

Database migrations are managed via **Flyway**, with migration scripts versioned in the repository. The initial migration creates all four tables with indexes and seed data for the Mismatch Detection Rule in the `validation_rules` table. The application runs migrations automatically on startup.

Deployment pipeline:
1. Build (Maven for backend, npm for frontend)
2. Run unit and integration tests
3. Apply Flyway migrations against the target database
4. Deploy the Spring Boot JAR and the React static bundle behind an Nginx reverse proxy or a cloud load balancer

---

## 12. Testing Strategy

| Test Type | Scope | Tools |
|-----------|-------|-------|
| Unit Tests | Rule implementations, parser logic, category classification | JUnit 5, Mockito |
| Integration Tests | Full upload-to-mismatch pipeline with embedded DB | Spring Boot Test, Testcontainers (PostgreSQL) |
| API Tests | All REST endpoints with valid and invalid inputs | MockMvc, RestAssured |
| Frontend Tests | Component rendering, upload flow, polling behavior, mismatch table | Vitest, React Testing Library |
| E2E Tests | Full user journey from upload to mismatch display | Playwright |
| Performance Tests | Upload and validate a 50 MB file with 10,000 ledgers within SLA | JMeter or Gatling |

---

## 13. Risks & Mitigations

| Risk | Impact | Mitigation |
|------|--------|------------|
| Tally JSON format changes across versions | High | Use tolerant parsing with fallback field mappings; version-detect via envelope structure; the original file is retained on disk for re-processing if needed |
| Large file uploads cause timeouts | Medium | Async processing decouples upload from validation; configurable timeouts; chunked upload support in future |
| Rule execution takes too long | Medium | Timeout per rule (configurable); rules run independently so one failure does not block others |
| Pre-configured masters are incomplete | Medium | UI prompts user to review configuration before first upload; missing configuration generates warnings, not errors |
| Concurrent uploads by same organization | Low | Each upload creates an independent job; no shared state between jobs; DB-level isolation |

---

## 14. Glossary

| Term | Definition |
|------|-----------|
| Tally Masters | Foundational data entities in TallyPrime including Groups, Ledgers, and Voucher Types |
| Ledger | An accounting head in Tally that belongs to a parent group and is used to record transactions |
| Parent Group | The Tally group under which a ledger is classified (e.g., Purchase Accounts, Duties & Taxes) |
| Pre-configured Master | An expected ledger entry maintained in the application that uploaded masters are compared against |
| Mismatch | A discrepancy between an uploaded ledger master and its corresponding pre-configured master |
| GST | Goods and Services Tax – an indirect tax applied in India with components CGST, SGST, and IGST |
| TDS | Tax Deducted at Source – a mechanism for collecting income tax at the point of payment |
| GUID | Globally Unique Identifier assigned by Tally to each master and transaction object |
| tallymessage | The root envelope in Tally's native JSON export format that wraps all master/transaction objects |
| ParsedLedger | In-memory DTO representing a single ledger extracted from the uploaded JSON; not persisted to DB |
