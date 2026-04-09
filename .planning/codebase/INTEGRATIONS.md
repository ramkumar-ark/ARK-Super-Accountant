# External Integrations

**Analysis Date:** 2026-04-09

---

## APIs & External Services

**No third-party external SaaS APIs** are integrated. All processing is self-contained within the Spring Boot backend. There are no calls to external HTTP APIs (no Stripe, no SendGrid, no cloud AI, etc.).

---

## Data Storage

**Primary Database:**
- PostgreSQL 5432
  - Database name: `superaccountant`
  - Host: `localhost` (dev configuration in `application.properties`)
  - Connection property: `spring.datasource.url=jdbc:postgresql://localhost:5432/superaccountant`
  - Username property: `spring.datasource.username`
  - Password property: `spring.datasource.password`
  - Client: Spring Data JPA with Hibernate (`spring-boot-starter-data-jpa`)
  - Dialect: `org.hibernate.dialect.PostgreSQLDialect`
  - Schema management: `ddl-auto=update` (Hibernate auto-migrates on startup)

**File Storage:**
- No external file storage (no S3, no GCS). Uploaded files are received as `MultipartFile` in-memory and parsed immediately — not persisted to disk or object storage.
- Max upload: 50MB per file, 50MB per request (`spring.servlet.multipart.*`)

**Caching:**
- None detected. No Redis, Memcached, or Spring Cache abstraction in use.

---

## Authentication & Identity

**Auth Provider:** Custom — self-hosted JWT implementation

- Library: `io.jsonwebtoken:jjwt` 0.11.5
- Implementation files:
  - `Service/superaccountant/src/main/java/com/arktech/superaccountant/login/security/jwt/JwtUtils.java` — token generation and validation
  - `Service/superaccountant/src/main/java/com/arktech/superaccountant/login/security/jwt/AuthTokenFilter.java` — per-request JWT extraction and authentication
  - `Service/superaccountant/src/main/java/com/arktech/superaccountant/login/security/jwt/AuthEntryPointJwt.java` — unauthorized response handler
- JWT secret: `arktech.app.jwtSecret` (property in `application.properties`)
- JWT expiry: `arktech.app.jwtExpirationMs` (86400000 ms = 24 hours)
- Password hashing: BCrypt (`BCryptPasswordEncoder`)
- Session strategy: Stateless (no server-side session)

**User Roles (ERole enum):**
- `ROLE_CASHIER` (default on signup)
- `ROLE_ACCOUNTANT`
- `ROLE_DATA_ENTRY_OPERATOR`
- `ROLE_OWNER`

---

## Internal Service-to-Service Communication

**Frontend → Backend:**
- Protocol: HTTP REST (JSON)
- Dev proxy: Vite dev server forwards `/api/*` to `http://localhost:8080` (configured in `Client/vite.config.ts`)
- HTTP client in frontend: `axios` 1.14.0
- Auth header: Bearer JWT token (sent with authenticated requests)

**No microservices.** There is a single Spring Boot service. All features (auth, masters, GST validation, Tally import) are modules within the same JVM process.

---

## REST API Endpoints (Backend)

All controllers annotated with `@CrossOrigin(origins = "*")`.

**Auth (`/api/auth/**`) — public:**
- `POST /api/auth/signin` — Login, returns JWT
- `POST /api/auth/signup` — Register new user (default role: CASHIER)

**Tally Import (`/api/tally/**`) — authenticated:**
- `POST /api/tally/import` — Upload Tally JSON export file, returns parsed voucher data grouped by type
  - Controller: `tally/controllers/TallyImportController.java`
  - Service: `tally/services/TallyParserService.java`

**GST Validation (`/api/gst/**`) — authenticated:**
- `POST /api/gst/validate` — Upload Tally JSON file, returns GST validation errors grouped by type
  - Controller: `gst/controllers/GstValidationController.java`
  - Service: `gst/services/GstValidationService.java`

**Organizations (`/api/organizations`) — authenticated:**
- `POST /api/organizations` — Create organization, links creating user to it
  - Controller: `masters/controllers/OrganizationController.java`

**Masters Upload & Validation (`/api/v1/**`) — authenticated:**
- `POST /api/v1/uploads` — Upload Tally masters JSON, run validation rules, persist findings
- `GET /api/v1/uploads` — List upload jobs (paginated, filterable by status)
- `GET /api/v1/uploads/{id}/mismatches` — List validation findings (paginated, filterable)
- `GET /api/v1/uploads/{id}/mismatches/export` — Export findings as CSV download
- `PATCH /api/v1/uploads/{jobId}/mismatches/{findingId}/resolve` — Resolve a finding
- `GET /api/v1/validation-rules` — List active validation rules
  - Controller: `masters/controllers/UploadController.java`

**Preconfigured Masters (`/api/v1/preconfigured-masters`) — authenticated:**
- `GET /api/v1/preconfigured-masters` — List (paginated, filterable by category)
- `POST /api/v1/preconfigured-masters` — Create single master
- `PUT /api/v1/preconfigured-masters/{id}` — Update master
- `DELETE /api/v1/preconfigured-masters/{id}` — Soft-delete master
- `POST /api/v1/preconfigured-masters/bulk` — Bulk create masters
- `POST /api/v1/preconfigured-masters/onboard` — Apply template or start custom setup
  - Controller: `masters/controllers/PreconfiguredMastersController.java`

**Test (`/api/test/**`) — public:**
- Open for test/health use (no controller file observed in source listing)

---

## File Uploads

**Format:** JSON only (`.json` extension enforced in `UploadController`)

**Source:** Tally ERP data export in JSON format (custom schema with `TALLYMESSAGE` root)

**Processing pipeline:**
1. `MultipartFile` received in controller
2. `TallyParserService.parseMastersJson()` or `parseJson()` parses JSON in-memory
3. `LedgerCategoryClassifier` categorizes ledgers
4. `ValidationOrchestrator.runAndPersist()` runs rule chain, persists `ValidationFinding` records
5. Response returned immediately — no async processing or message queues

---

## Webhooks & Callbacks

**Incoming webhooks:** None
**Outgoing webhooks:** None

---

## Monitoring & Observability

**Error Tracking:** None detected (no Sentry, Datadog, etc.)

**Logging:** Spring Boot default logging (Logback via Spring Boot autoconfiguration). No custom log configuration file found.

**APM:** None

---

## CI/CD & Deployment

**Hosting:** Not configured (no Dockerfile, no docker-compose, no CI pipeline files detected in the repository root)

**CI Pipeline:** None detected

---

## Environment Configuration

**Backend properties** (`Service/superaccountant/src/main/resources/application.properties`):

| Property | Purpose |
|---|---|
| `spring.datasource.url` | PostgreSQL JDBC connection string |
| `spring.datasource.username` | DB username |
| `spring.datasource.password` | DB password |
| `spring.jpa.hibernate.ddl-auto` | Schema management strategy |
| `spring.jpa.properties.hibernate.dialect` | Hibernate dialect |
| `arktech.app.jwtSecret` | JWT signing secret |
| `arktech.app.jwtExpirationMs` | JWT token TTL in milliseconds |
| `spring.servlet.multipart.max-file-size` | Max uploaded file size (50MB) |
| `spring.servlet.multipart.max-request-size` | Max request size (50MB) |

**Frontend environment:**
- No `.env` files detected in `Client/`
- API base URL determined by Vite dev proxy (`/api` → `localhost:8080`)
- No runtime environment variable injection observed in source

**No `.env` files are present** — all config is in `application.properties` directly (credentials are not externalized in dev).

---

*Integration audit: 2026-04-09*
