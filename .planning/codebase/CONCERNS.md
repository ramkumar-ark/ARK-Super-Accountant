# Codebase Concerns

**Analysis Date:** 2026-04-09

---

## Security Concerns

### HIGH — Database credentials committed to source control

- Issue: `application.properties` contains hardcoded PostgreSQL credentials (`postgres` / `Arkpostgres@2020`) and a plaintext JWT secret key committed directly to the repository.
- Files: `Service/superaccountant/src/main/resources/application.properties`
- Impact: Any developer, CI system, or attacker with repository access has the production database password and can forge JWT tokens by knowing the secret. The JWT secret `superAccountantSecretKeyForJwtTokenGenerationPurposeOnly` is short, human-readable, and weak for HS256.
- Fix approach: Move all secrets to environment variables or a secrets manager. In Spring Boot, use `${DB_PASSWORD}` syntax in properties and inject at runtime. Rotate the exposed credentials immediately. Use a cryptographically random 256-bit secret for JWT.

---

### HIGH — JWT secret is not Base64-encoded correctly

- Issue: `JwtUtils.key()` calls `Decoders.BASE64.decode(jwtSecret)` — the secret must be valid Base64. The current value `superAccountantSecretKeyReferenceForJwtTokenGenerationPurposeOnly` is plain ASCII text, not Base64. This works by accident if the decoder doesn't throw, but the effective key entropy is far lower than HS256 requires (256 bits). The JJWT library recommends at least 32 random bytes encoded in Base64.
- Files: `Service/superaccountant/src/main/java/com/arktech/superaccountant/login/security/jwt/JwtUtils.java`
- Impact: Tokens could be forged if an attacker obtains the secret and the key is weak.
- Fix approach: Generate a cryptographically secure 32-byte random secret, Base64-encode it, and store it as an environment variable.

---

### HIGH — CORS wildcard on all endpoints

- Issue: Every controller uses `@CrossOrigin(origins = "*")`. This allows any origin — including malicious sites — to make credentialed requests to the API.
- Files: `Service/superaccountant/src/main/java/com/arktech/superaccountant/login/controllers/AuthController.java`, `...masters/controllers/UploadController.java`, `...masters/controllers/OrganizationController.java`, `...masters/controllers/PreconfiguredMastersController.java`, `...gst/controllers/GstValidationController.java`, `...tally/controllers/TallyImportController.java`
- Impact: Enables cross-site request forgery (CSRF) from any domain when combined with cookie-based sessions (less critical with JWT, but still a concern for future auth changes).
- Fix approach: Restrict `@CrossOrigin` to the deployed frontend origin, or configure a global CORS policy in `WebSecurityConfig` with specific allowed origins.

---

### HIGH — Open user registration with privileged role selection

- Issue: `POST /api/auth/signup` allows any caller to self-assign roles including `owner`, `accountant`, and `data_entry` by passing a `role` field in the request body. There is no restriction, invitation flow, or admin approval.
- Files: `Service/superaccountant/src/main/java/com/arktech/superaccountant/login/controllers/AuthController.java` (lines 84–108)
- Impact: Anyone can register as `ROLE_OWNER` without authorization, gaining unrestricted access to all organization data.
- Fix approach: Either remove the `role` field from `SignupRequest` (always default to CASHIER), or require an admin token/invitation code to assign elevated roles.

---

### HIGH — No role-based access control enforced on business endpoints

- Issue: `@EnableMethodSecurity` is configured in `WebSecurityConfig`, but no `@PreAuthorize`, `@RolesAllowed`, or `@Secured` annotations are used on any controller method. All authenticated users regardless of role (`CASHIER`, `DATA_ENTRY_OPERATOR`, `ACCOUNTANT`, `OWNER`) can call all endpoints.
- Files: All controller classes in `...masters/controllers/`, `...gst/controllers/`, `...tally/controllers/`
- Impact: A cashier can create organizations, delete preconfigured masters, export financial data, and run validation jobs — operations that should be restricted.
- Fix approach: Add `@PreAuthorize("hasRole('ROLE_OWNER') or hasRole('ROLE_ACCOUNTANT')")` (and similar) to sensitive endpoints.

---

### MEDIUM — No file type validation beyond filename extension

- Issue: `UploadController.uploadMasters()` checks only that the filename ends with `.json`. There is no MIME type check and no content validation before reading the file bytes. The 50 MB upload limit is the only other guard.
- Files: `Service/superaccountant/src/main/java/com/arktech/superaccountant/masters/controllers/UploadController.java` (lines 68–70)
- Impact: Malformed or malicious files (e.g., a ZIP disguised as `.json`) could be uploaded and may cause unexpected behavior in the parser.
- Fix approach: Check `file.getContentType()` and validate the first bytes are `{` or whitespace (JSON). Consider an allowlist of content types.

---

### MEDIUM — `GstValidationController` and `TallyImportController` have no authentication

- Issue: `POST /api/gst/validate` and `POST /api/tally/import` are REST endpoints but are not listed in the public permit list in `WebSecurityConfig`, so they theoretically require authentication. However, this relies on `anyRequest().authenticated()` as a catch-all — it is not explicitly tested and there are no test files verifying their auth behavior.
- Files: `Service/superaccountant/src/main/java/com/arktech/superaccountant/gst/controllers/GstValidationController.java`, `Service/superaccountant/src/main/java/com/arktech/superaccountant/tally/controllers/TallyImportController.java`
- Impact: If a misconfiguration occurs (e.g., accidentally adding to the permit list), sensitive voucher data could be exposed unauthenticated.
- Fix approach: Add explicit auth tests and consider adding `@PreAuthorize` to document the intent.

---

### LOW — Password validation is minimal

- Issue: `SignupRequest` accepts passwords between 6 and 40 characters with no complexity requirements.
- Files: `Service/superaccountant/src/main/java/com/arktech/superaccountant/login/payload/request/SignupRequest.java`
- Impact: Weak passwords (e.g., `123456`) are accepted for financial application accounts.
- Fix approach: Enforce at least one uppercase, lowercase, digit, and special character using a custom `@Password` validator or regex constraint.

---

## Performance Concerns

### MEDIUM — No database indexes on foreign key and filter columns

- Issue: None of the JPA entity classes declare `@Table(indexes = {...})`. High-frequency query columns — `upload_job_id` in `validation_findings`, `organization_id` in `upload_jobs`, `organization_id` in `preconfigured_masters`, and `is_active` in `preconfigured_masters` — have no declared indexes.
- Files: `Service/superaccountant/src/main/java/com/arktech/superaccountant/masters/models/ValidationFinding.java`, `...UploadJob.java`, `...PreconfiguredMaster.java`
- Impact: Full table scans on `findByUploadJobId()`, `findByOrganizationId()`, and `findFiltered()` will degrade as data grows. A single upload job with 500 ledgers emits hundreds of findings; fetching them without an index on `upload_job_id` is a table scan.
- Fix approach: Add `@Table(indexes = {@Index(columnList = "upload_job_id"), @Index(columnList = "organization_id"), @Index(columnList = "is_active")})` to the respective entity classes.

---

### MEDIUM — `findByUploadJobId` loads all findings unbounded into memory

- Issue: `ValidationFindingRepository.findByUploadJobId()` returns `List<ValidationFinding>` with no pagination. It is called in two places in `UploadController`: after a new upload to build the response, and in the CSV export endpoint.
- Files: `Service/superaccountant/src/main/java/com/arktech/superaccountant/masters/controllers/UploadController.java` (lines 111, 172)
- Impact: If an upload produces thousands of findings (large Tally exports), the entire result set is loaded into heap memory at once.
- Fix approach: For the upload response, limit the initial findings returned (e.g., first 100). For CSV export, use streaming (`Stream<ValidationFinding>`) with `@QueryHints` or a `ScrollableResults` approach.

---

### MEDIUM — Validation orchestrator loads all preconfigured masters into memory per upload

- Issue: `masterRepository.findByOrganizationIdAndActiveTrue(orgId)` returns a `List<PreconfiguredMaster>` (unbounded) and passes it to `ValidationContext`. For organizations with thousands of configured masters this is a heap allocation on every upload.
- Files: `Service/superaccountant/src/main/java/com/arktech/superaccountant/masters/controllers/UploadController.java` (line 100)
- Impact: Memory spike per request proportional to the number of configured masters.
- Fix approach: This is acceptable at current scale; add a monitoring alert if `preconfigured_masters` per org exceeds 10,000 rows.

---

### LOW — CSV export builds the entire response as a `String` in memory

- Issue: The CSV export endpoint (`GET /uploads/{id}/mismatches/export`) constructs a `StringBuilder` holding all findings, then returns it as a single `ResponseEntity<String>`.
- Files: `Service/superaccountant/src/main/java/com/arktech/superaccountant/masters/controllers/UploadController.java` (lines 173–187)
- Impact: For large result sets, the full CSV string must fit in heap before any bytes are written to the response.
- Fix approach: Use `StreamingResponseBody` or write directly to `HttpServletResponse.getOutputStream()` to stream the CSV.

---

## Technical Debt

### MEDIUM — `TallyParserServiceTest` and `GstValidationServiceTest` depend on a local file at `C:/Program Files/TallyPrime/DayBook.json`

- Issue: Both test classes read from a hardcoded absolute path on a Windows machine. These tests will fail on any other environment (CI, other developer machines, Linux).
- Files: `Service/superaccountant/src/test/java/com/arktech/superaccountant/tally/TallyParserServiceTest.java` (line 21), `Service/superaccountant/src/test/java/com/arktech/superaccountant/gst/GstValidationServiceTest.java` (line 25)
- Impact: Tests cannot run in CI. They only serve as manual smoke tests for one developer's machine.
- Fix approach: Move the sample JSON to `src/test/resources/` and load it via `getClass().getResourceAsStream()`. Consider marking these as `@Disabled` integration tests if they require a large real-world file.

---

### MEDIUM — `System.out.println` used instead of logger in `DataInitializer`

- Issue: `DataInitializer` uses `System.out.println` for three startup messages instead of SLF4J logger.
- Files: `Service/superaccountant/src/main/java/com/arktech/superaccountant/login/config/DataInitializer.java` (lines 40, 53, 105)
- Impact: These messages bypass the logging framework, cannot be filtered by log level, and will appear on stdout even in production.
- Fix approach: Replace with `private static final Logger logger = LoggerFactory.getLogger(DataInitializer.class)` and `logger.info(...)`.

---

### MEDIUM — `buildFinding` method is a trivial delegation to `buildFindingWithFix`

- Issue: In `MismatchDetectionRule`, `buildFinding()` is a one-liner that only calls `buildFindingWithFix()` with the same parameters. It adds no behavior and creates confusion about which method to use.
- Files: `Service/superaccountant/src/main/java/com/arktech/superaccountant/masters/rules/MismatchDetectionRule.java` (lines 121–125)
- Fix approach: Remove `buildFinding` and call `buildFindingWithFix` directly, or rename to a single unified method.

---

### MEDIUM — `PreconfiguredMastersController.listValidationRules` returns a 404 hint

- Issue: `GET /api/v1/preconfigured-masters/validation-rules` returns HTTP 404 with a redirect hint to use a different URL. This is misleading — returning 404 suggests the resource does not exist, not that it moved.
- Files: `Service/superaccountant/src/main/java/com/arktech/superaccountant/masters/controllers/PreconfiguredMastersController.java` (lines 195–199)
- Fix approach: Remove the ghost endpoint entirely, or return HTTP 301/308 Redirect to the correct URL.

---

### LOW — `pom.xml` artifact description is a Spring Initializr placeholder

- Issue: The `<description>` field reads "Demo project for Spring Boot". The `<name>` is `superaccountant` in lowercase with no display name. `<url>`, `<licenses>`, `<developers>`, and `<scm>` are all empty elements.
- Files: `Service/superaccountant/pom.xml` (lines 14–27)
- Fix approach: Update project metadata to reflect the actual product.

---

### LOW — `jjwt` pinned at 0.11.5 (not latest)

- Issue: `io.jsonwebtoken:jjwt-api` is at version `0.11.5`. The JJWT library has moved to a `0.12.x` series with API changes and security improvements (the `SignatureAlgorithm` enum used in `JwtUtils.generateJwtToken()` is deprecated in 0.12.x in favor of `Jwts.SIG`).
- Files: `Service/superaccountant/pom.xml` (lines 57–70), `Service/superaccountant/src/main/java/com/arktech/superaccountant/login/security/jwt/JwtUtils.java` (line 33)
- Impact: Using a deprecated API surface. No known active CVEs in 0.11.5 at time of analysis, but the 0.12.x series closes the `SignatureAlgorithm` enum deprecation.
- Fix approach: Upgrade to `0.12.x` and migrate to the new fluent API.

---

## Missing Features / Expected but Absent

### HIGH — No authorization check on cross-organization data access (organization isolation)

- Issue: `UploadController.listMismatches()` and `resolveFinding()` check `jobOpt.get().getOrganizationId().equals(orgId)` to prevent cross-org access. However, `GET /api/v1/validation-rules` (`listValidationRules()`) and all `PreconfiguredMastersController` endpoints that take a `UUID id` path variable do an org-scoped filter — but only because the repository query filters by `organizationId`. If a bug introduced a raw `findById` without filtering, data would leak across organizations.
- Files: `Service/superaccountant/src/main/java/com/arktech/superaccountant/masters/controllers/UploadController.java`, `...PreconfiguredMastersController.java`
- Impact: Latent risk of data leakage between organizations if any new endpoint omits the org filter.
- Fix approach: Extract a shared `OrganizationScopedService` layer that always applies org filtering, rather than scattering the check across controller methods.

---

### HIGH — No rate limiting on authentication endpoints

- Issue: `POST /api/auth/signin` and `POST /api/auth/signup` have no rate limiting or account lockout after repeated failed attempts.
- Files: `Service/superaccountant/src/main/java/com/arktech/superaccountant/login/controllers/AuthController.java`
- Impact: Brute-force attacks against user passwords are unrestricted.
- Fix approach: Add Spring Security's `DefaultSecurityFilterChain` rate limiting, or use a library like `bucket4j` to limit login attempts per IP.

---

### MEDIUM — No structured global exception handler

- Issue: There is no `@ControllerAdvice` / `@RestControllerAdvice` class. Individual controllers use bare `try/catch` returning ad-hoc `ResponseEntity` with inconsistent error structures (`Map.of("error", ...)`, `MessageResponse`, raw `String`).
- Files: All controllers in `...controllers/` packages
- Impact: API consumers receive inconsistent error payloads. Some errors leak internal exception messages (e.g., `GstValidationController` returns `e.getMessage()` directly in the response body).
- Fix approach: Add a `GlobalExceptionHandler` with `@RestControllerAdvice` that maps exception types to standardized error responses.

---

### MEDIUM — No audit logging of data mutations

- Issue: There is no audit log for who created/updated/deleted organizations, masters, or resolved findings beyond the `resolvedBy` column on `ValidationFinding`. The `uploadedBy` field on `UploadJob` is the only user attribution.
- Files: `Service/superaccountant/src/main/java/com/arktech/superaccountant/masters/models/`
- Impact: For a financial compliance application, lack of audit trail is a compliance gap.
- Fix approach: Add Spring Data's `@CreatedBy` / `@LastModifiedBy` `AuditingEntityListener`, or emit structured audit events.

---

### MEDIUM — Login and signup pages are stubbed placeholders in the frontend

- Issue: `main.tsx` defines `/login` and `/signup` routes that render `<div>Login — coming soon</div>` and `<div>Sign Up — coming soon</div>`. The `api/`, `store/`, `routes/`, `components/ui/`, `components/masters/`, `components/uploads/`, `pages/uploads/`, and `pages/settings/` directories are all empty.
- Files: `Client/src/main.tsx` (lines 21–28)
- Impact: The frontend cannot authenticate users or interact with any backend API. The client is currently a landing-page-only application.
- Fix approach: Implement auth flows and the application pages as planned features.

---

### LOW — No token refresh mechanism

- Issue: JWT tokens expire after 24 hours (`jwtExpirationMs=86400000`). There is no refresh token endpoint. Users must re-authenticate after expiry.
- Files: `Service/superaccountant/src/main/resources/application.properties`, `Service/superaccountant/src/main/java/com/arktech/superaccountant/login/security/jwt/JwtUtils.java`
- Fix approach: Add a `POST /api/auth/refresh` endpoint that issues a new token given a valid, not-yet-expired token, or implement refresh tokens with a separate long-lived token store.

---

### LOW — No `GET /api/organizations` endpoint

- Issue: `OrganizationController` only has `POST /api/organizations`. There is no way to retrieve the current user's organization details via the API.
- Files: `Service/superaccountant/src/main/java/com/arktech/superaccountant/masters/controllers/OrganizationController.java`
- Fix approach: Add `GET /api/organizations/me` that returns the organization linked to the authenticated user.

---

## Configuration Issues

### HIGH — No environment separation

- Issue: There is a single `application.properties` with production-like credentials. There are no `application-dev.properties`, `application-test.properties`, or `application-prod.properties` profiles. The same DB credentials and JWT secret are used everywhere.
- Files: `Service/superaccountant/src/main/resources/application.properties`
- Impact: Developers running locally use the same credentials as production; there is no safe test environment.
- Fix approach: Create Spring profiles (`spring.profiles.active=dev`) with separate properties files, and store production secrets outside version control.

---

### MEDIUM — `spring.jpa.hibernate.ddl-auto=update` in production properties

- Issue: `ddl-auto: update` allows Hibernate to automatically alter the schema on startup. This is risky in production — it can cause data loss if entities are renamed, columns are dropped, or types are changed.
- Files: `Service/superaccountant/src/main/resources/application.properties` (line 7)
- Impact: Schema migrations are uncontrolled. Renaming a field in a JPA entity could silently drop a column on the next startup.
- Fix approach: Switch to `ddl-auto=validate` for production and adopt a migration tool (Flyway or Liquibase) for controlled schema changes.

---

### MEDIUM — Vite dev server proxy target is hardcoded to `localhost:8080`

- Issue: `vite.config.ts` proxies `/api` to `http://localhost:8080` with no way to override it via environment variable.
- Files: `Client/vite.config.ts` (line 16)
- Impact: If the backend runs on a different port or host (e.g., in Docker), developers must manually edit the config file.
- Fix approach: Use `process.env.VITE_API_URL ?? 'http://localhost:8080'` for the proxy target.

---

## Test Coverage Gaps

### HIGH — No tests for controllers (integration or unit)

- Issue: No Spring MVC test (`@WebMvcTest`) or MockMvc test exists for any controller. `AuthController`, `UploadController`, `OrganizationController`, `PreconfiguredMastersController`, `GstValidationController`, and `TallyImportController` have zero test coverage.
- Files: `Service/superaccountant/src/test/java/com/arktech/superaccountant/`
- Impact: Auth flows, file upload handling, org-scoping, pagination, and error responses are entirely untested.
- Fix approach: Add `@WebMvcTest` tests for each controller with `@MockBean` for service/repository dependencies.

---

### HIGH — `GstValidationServiceTest` and `TallyParserServiceTest` are not real automated tests

- Issue: Both tests depend on `C:/Program Files/TallyPrime/DayBook.json` existing on the local machine. They contain no assertions — only `System.out.println` statements. They will silently pass or throw `FileNotFoundException` depending on environment.
- Files: `Service/superaccountant/src/test/java/com/arktech/superaccountant/gst/GstValidationServiceTest.java`, `Service/superaccountant/src/test/java/com/arktech/superaccountant/tally/TallyParserServiceTest.java`
- Impact: `GstValidationService` (422 lines, 6 validation rules) has no assertion-based test coverage.
- Fix approach: Add inline JSON fixtures to `src/test/resources/` and write assertion-based tests for each of the 6 GST validation rules.

---

### MEDIUM — `SuperaccountantApplicationTests.contextLoads` does nothing meaningful

- Issue: The context loads test is a single empty test that only verifies the Spring context starts. It requires a live database connection to pass, and contributes nothing to regression coverage.
- Files: `Service/superaccountant/src/test/java/com/arktech/superaccountant/SuperaccountantApplicationTests.java`
- Fix approach: Either mark as an integration test requiring a TestContainer PostgreSQL instance, or replace with targeted slice tests that do not need the full context.

---

### MEDIUM — No client-side tests for pages or routes

- Issue: The landing page sections have unit tests, but `main.tsx` (router config) has no test. The stub `/login` and `/signup` routes have no tests. The `api/`, `store/`, `routes/`, `components/ui/`, `components/masters/`, and `components/uploads/` directories are empty so there is nothing to test yet — but no test infrastructure pattern is established for future non-landing code.
- Files: `Client/src/main.tsx`
- Fix approach: Add a router integration test that verifies routes resolve to expected components as pages are built.

---

## Scalability Concerns

### MEDIUM — Single-threaded synchronous validation processing

- Issue: `ValidationOrchestrator.runAndPersist()` is a `@Transactional` synchronous method called inline within the HTTP request thread. For large Tally exports (1,000+ ledgers with multiple validation rules), the HTTP response is blocked for the entire duration.
- Files: `Service/superaccountant/src/main/java/com/arktech/superaccountant/masters/services/ValidationOrchestrator.java`
- Impact: Under concurrent upload load, request threads are held for the full validation duration, exhausting the Tomcat thread pool.
- Fix approach: Move validation to an async background job (Spring `@Async`, or a message queue). Return `202 Accepted` with a job ID, and let the client poll for completion via `GET /uploads/{id}`.

---

### LOW — Single-template onboarding limits multi-industry expansion

- Issue: `PreconfiguredMastersController.onboard()` hardcodes `"Construction/Works Contractor template"` as the only template. The `useTemplate` flag is binary — there is no template selection.
- Files: `Service/superaccountant/src/main/java/com/arktech/superaccountant/masters/controllers/PreconfiguredMastersController.java` (lines 154–193)
- Impact: Adding new industry templates requires code changes rather than data configuration.
- Fix approach: Add a `templateName` or `templateId` field to `OnboardRequest` and support multiple named template sets in the database.

---

*Concerns audit: 2026-04-09*
