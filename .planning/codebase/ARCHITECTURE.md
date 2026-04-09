# Architecture

**Analysis Date:** 2026-04-09

## Pattern Overview

**Overall:** Monorepo containing a decoupled frontend SPA and a backend REST API monolith.

**Key Characteristics:**
- Frontend (React SPA) and backend (Spring Boot REST API) are completely separate processes — no server-side rendering.
- Backend is organized as a single deployable Spring Boot application (monolith) with feature-based package grouping.
- All cross-boundary communication is HTTP REST with JSON payloads. File uploads use `multipart/form-data`.
- Stateless authentication: no sessions, JWT carried in `Authorization: Bearer` header on every request.
- Schema is code-managed via Hibernate `ddl-auto: update` — no separate migration tool.

## Layers (Backend)

**Controller Layer:**
- Purpose: Accept HTTP requests, validate input, delegate to services/repositories, return HTTP responses.
- Location: `Service/superaccountant/src/main/java/com/arktech/superaccountant/{feature}/controllers/`
- Contains: `@RestController` classes, DTO mapping helpers.
- Depends on: Service layer, Repository layer, security principal (`UserDetailsImpl`).
- Used by: HTTP clients (frontend).

**Service Layer:**
- Purpose: Encapsulate business logic that spans multiple repositories or requires orchestration.
- Location: `Service/superaccountant/src/main/java/com/arktech/superaccountant/{feature}/services/`
- Contains: `@Service` classes (`ValidationOrchestrator`, `TallyParserService`, `GstValidationService`).
- Depends on: Repository layer, classifier components, rule implementations.
- Used by: Controllers.

**Validation Rule Layer (Strategy Pattern):**
- Purpose: Pluggable, database-driven business validation rules executed against uploaded ledger data.
- Location: `Service/superaccountant/src/main/java/com/arktech/superaccountant/masters/rules/`
- Contains: `ValidationRule` interface + `MismatchDetectionRule` implementation, `ValidationContext` value object.
- Depends on: `PreconfiguredMaster` entities via `ValidationContext`, `ParsedLedger` DTOs.
- Used by: `ValidationOrchestrator` — discovers all `ValidationRule` beans via constructor injection list.

**Classifier Layer:**
- Purpose: Categorize parsed Tally ledgers into `LedgerCategory` enums by walking the group hierarchy.
- Location: `Service/superaccountant/src/main/java/com/arktech/superaccountant/masters/classifier/`
- Contains: `LedgerCategoryClassifier` (`@Component`), `ParsedLedger` (`@Builder` value object).
- Depends on: Nothing external.
- Used by: `TallyParserService`.

**Repository Layer:**
- Purpose: Data access via Spring Data JPA repositories.
- Location: `Service/superaccountant/src/main/java/com/arktech/superaccountant/{feature}/repository/`
- Contains: Interfaces extending `JpaRepository`.
- Depends on: JPA entities, PostgreSQL at runtime.
- Used by: Controllers, Services, `ValidationOrchestrator`.

**Entity / Model Layer:**
- Purpose: JPA-mapped domain objects persisted to PostgreSQL.
- Location: `Service/superaccountant/src/main/java/com/arktech/superaccountant/{feature}/models/`
- Contains: `@Entity` classes and enum types.

**DTO Layer (Payload):**
- Purpose: Request/response shapes decoupled from entities.
- Location: `Service/superaccountant/src/main/java/com/arktech/superaccountant/{feature}/payload/request/` and `.../payload/response/`
- Contains: Request POJOs (Jakarta Validation annotations), Response POJOs (often `@Builder`).

**Security Layer:**
- Location: `Service/superaccountant/src/main/java/com/arktech/superaccountant/login/security/`
- Contains: `WebSecurityConfig`, `AuthTokenFilter`, `JwtUtils`, `AuthEntryPointJwt`, `UserDetailsImpl`, `UserDetailsServiceImpl`.

## Data Flow

**Authenticated API Request:**

1. Client sends HTTP request with `Authorization: Bearer <jwt>` header.
2. `AuthTokenFilter` (`OncePerRequestFilter`) intercepts: extracts JWT, validates via `JwtUtils`, loads `UserDetailsImpl` via `UserDetailsServiceImpl`, sets Spring `SecurityContext`.
3. Spring Security checks `authorizeHttpRequests` rules — all endpoints except `/api/auth/**`, `/api/test/**`, `/error` require authentication.
4. Request reaches `@RestController` method with `@AuthenticationPrincipal UserDetailsImpl principal`.
5. Controller extracts `organizationId` from principal for multi-tenant data isolation.
6. Controller delegates to service or repository.
7. Response returned as JSON.

**Masters Upload Flow:**

1. `POST /api/v1/uploads` (multipart JSON file).
2. `UploadController` checks user has an `organizationId`, creates `UploadJob` record.
3. `TallyParserService.parseMastersJson()` reads file bytes, detects encoding (UTF-8/16/32), parses JSON envelope `tallymessage[]`, builds `groupHierarchy` map, produces `List<ParsedLedger>` with categories resolved via `LedgerCategoryClassifier`.
4. `ValidationOrchestrator.runAndPersist()` loads active `ValidationRuleConfig` rows ordered by `executionOrder`, resolves each to a `ValidationRule` Spring bean via a `Map<String, ValidationRule>` built at construction time (Strategy Pattern), executes each rule, collects `ValidationFinding` results.
5. `ValidationFinding` records and updated `UploadJob` persisted to PostgreSQL.
6. Response: `UploadJobResponse` JSON with embedded findings.

**GST Validation Flow:**

1. `POST /api/gst/validate` (multipart JSON file).
2. `GstValidationController` delegates to `TallyParserService.parseJson()` (voucher-level parse).
3. `GstValidationService.validate()` runs GST-specific checks, returns `List<GstValidationResult>`.
4. Controller aggregates error counts by `GstErrorType` and returns `GstValidationResponse`.

**Authentication Flow:**

1. `POST /api/auth/signin` with `LoginRequest` (username + password).
2. `AuthController` calls `AuthenticationManager.authenticate()`.
3. `UserDetailsServiceImpl.loadUserByUsername()` fetches `User` entity, builds `UserDetailsImpl`.
4. On success, `JwtUtils.generateJwtToken()` produces a signed JWT (HS512, 24h expiry).
5. Response: `JwtResponse` containing `token`, `id`, `username`, `email`, `role`.

## Authentication and Authorization Architecture

**Mechanism:** Stateless JWT (jjwt 0.11.5, algorithm HS512).

**Token Storage:** Client-side only — the server stores no session state.

**Request Authentication:**
- `AuthTokenFilter` extends `OncePerRequestFilter`.
- Reads `Authorization: Bearer <token>` header.
- Validates signature and expiry via `JwtUtils.validateJwtToken()`.
- Sets `SecurityContextHolder` with a `UsernamePasswordAuthenticationToken` containing `UserDetailsImpl`.

**Authorization:**
- `WebSecurityConfig` uses `@EnableMethodSecurity` and `authorizeHttpRequests`.
- Public: `/api/auth/signin`, `/api/auth/signup`, `/api/test/**`, `/error`.
- All other endpoints require an authenticated user.
- Role-based access control is configured via `ERole` enum (`ROLE_CASHIER`, `ROLE_ACCOUNTANT`, `ROLE_DATA_ENTRY_OPERATOR`, `ROLE_OWNER`). Each `User` has a single `Role` (ManyToOne).
- Organization-scoping: controllers enforce `organizationId` from the JWT principal — cross-org access is blocked in controller logic.

**Password Storage:** BCrypt via `BCryptPasswordEncoder`.

## Key Design Patterns

**Strategy Pattern (Validation Rules):**
- `ValidationRule` interface with `getRuleCode()` and `execute()`.
- Implementations are `@Component` beans (currently only `MismatchDetectionRule`).
- `ValidationOrchestrator` auto-discovers all beans of type `ValidationRule` via Spring DI constructor injection (`List<ValidationRule>`), builds a `Map<ruleCode, rule>`.
- Active rules and their execution order are stored in the `validation_rule_configs` DB table — adding a new rule requires a new `@Component` implementation and a DB row.

**Repository Pattern:**
- All persistence via Spring Data JPA `JpaRepository` interfaces.
- No custom SQL in application code — queries via derived method names or `@Query` annotations.

**DTO Pattern:**
- Strict separation of JPA entities from API shapes.
- Request DTOs in `payload/request/`, response DTOs in `payload/response/`.
- Response DTOs use Lombok `@Builder` for construction.

**Seed-on-Startup Pattern:**
- `DataInitializer` implements `CommandLineRunner`, seeds roles, validation rule config, and the Construction/Works Contractor master template on first run.

**Multi-Tenancy (Organization Scoping):**
- `User.organizationId` (UUID) links users to an `Organization`.
- All business data (uploads, findings, masters) is scoped to `organizationId`.
- The `organizationId` is carried in `UserDetailsImpl` and enforced in each controller.

## Module / Layer Boundaries

```
login/          ← Auth, user management, security infrastructure
masters/        ← Core business domain: organizations, preconfigured masters,
                   upload jobs, validation findings, rules, classifiers
gst/            ← GST-specific voucher validation (separate from masters flow)
tally/          ← Tally XML/JSON parsing, voucher models
```

Cross-module dependencies (intentional):
- `masters` controllers import `login.security.services.UserDetailsImpl` (auth principal).
- `gst` controller imports `tally.services.TallyParserService` (shared parser).
- `masters` controllers import `tally.services.TallyParserService` (shared parser).
- `tally.services.TallyParserService` imports `masters.classifier.LedgerCategoryClassifier`.

## Frontend Architecture (Client)

**Pattern:** SPA with file-based component decomposition. No state management library is actively used in the landing page (components are presentational). `zustand` and `@tanstack/react-query` are installed for future application pages.

**Routing:** TanStack Router v1 (`@tanstack/react-router`) with code-defined route tree in `Client/src/main.tsx`. Three routes defined: `/`, `/login`, `/signup`.

**Component Strategy:** Section-level components for the landing page under `Client/src/components/landing/`. Each section is a standalone component with its own `.test.tsx` co-located test file.

---

*Architecture analysis: 2026-04-09*
