---
plan: 2
phase: 1
wave: 1
depends_on: []
files_modified:
  - Service/superaccountant/src/main/java/com/arktech/superaccountant/login/security/WebSecurityConfig.java
  - Service/superaccountant/src/main/java/com/arktech/superaccountant/masters/controllers/OrganizationController.java
  - Service/superaccountant/src/main/java/com/arktech/superaccountant/login/controllers/AuthController.java
  - Service/superaccountant/src/main/java/com/arktech/superaccountant/masters/controllers/UploadController.java
  - Service/superaccountant/src/main/java/com/arktech/superaccountant/gst/controllers/GstValidationController.java
autonomous: true
requirements:
  - SEC-02
must_haves:
  truths:
    - "Calling any non-public API endpoint without a JWT returns 401"
    - "Calling an endpoint with a valid JWT but wrong role returns 403"
    - "WebSecurityConfig.permitAll() lists only the exact public paths — no wildcards beyond /api/test/**"
    - "Every non-auth controller method has an explicit @PreAuthorize annotation"
  artifacts:
    - path: "Service/superaccountant/src/main/java/com/arktech/superaccountant/login/security/WebSecurityConfig.java"
      provides: "Locked-down security filter chain"
      contains: "permitAll"
    - path: "Service/superaccountant/src/main/java/com/arktech/superaccountant/masters/controllers/OrganizationController.java"
      provides: "Role-guarded org endpoints"
      contains: "@PreAuthorize"
  key_links:
    - from: "WebSecurityConfig"
      to: "@PreAuthorize annotations"
      via: "@EnableMethodSecurity"
      pattern: "@EnableMethodSecurity"
---

# Plan 2: Role Guards — @PreAuthorize on All Controllers

## Goal
Lock every API endpoint behind an explicit `@PreAuthorize` role guard; update `WebSecurityConfig` to permit only the exact public paths (D-15); ensure 401 for unauthenticated and 403 for wrong-role on all protected endpoints.

## Context
Currently only `WebSecurityConfig.authorizeHttpRequests` provides coarse-grained protection — "anything not in `/api/auth/**` requires authentication" — but there are no method-level role checks. Any authenticated user (regardless of role) can call any business API. D-11 through D-15 define the explicit role requirements per endpoint type. `@EnableMethodSecurity` is already configured in `WebSecurityConfig`, so `@PreAuthorize` annotations on controller methods work immediately. (D-11, D-12, D-13, D-14, D-15)

<threat_model>
## Threat Model (ASVS L1)

### Threats Addressed

- **[HIGH] T-02-01 — Elevation of Privilege: Missing method-level authorization** — A CASHIER user can currently call organization write endpoints, upload endpoints, and GST validation because no per-method guards exist. Mitigation: add `@PreAuthorize("hasRole('OWNER') or hasRole('ACCOUNTANT')")` on write operations and `@PreAuthorize("isAuthenticated()")` on read operations.

- **[MED] T-02-02 — Information Disclosure: Any authenticated user sees any organization's data** — Multi-tenant isolation relies on `organizationId` from JWT, but role check gaps allow cross-org reads if org ID were guessable. Mitigation: role guards add a layer on top of org scoping; org scoping enforcement addressed in Plan 5.

- **[LOW] T-02-03 — Spoofing: Implicit permit-all fallback** — `authorizeHttpRequests` with `.anyRequest().authenticated()` still processes requests if method-security fails to trigger. Mitigation: keep `.anyRequest().authenticated()` as the fallback; it catches anything not covered by explicit `@PreAuthorize`.

### Residual Risks

- `/api/test/**` public paths: retained as-is for local health checks. No sensitive data served from test endpoints. Acceptable for dev; remove in production hardening phase.
- CORS `origins = "*"` on all controllers: explicitly deferred per REQUIREMENTS.md Out of Scope. Not addressed in this plan.
</threat_model>

## Tasks

<task id="1">
<title>Update WebSecurityConfig permitted paths and add @PreAuthorize to OrganizationController</title>
<read_first>
- `Service/superaccountant/src/main/java/com/arktech/superaccountant/login/security/WebSecurityConfig.java` — current filter chain; `authorizeHttpRequests` block to be updated with exact path list
- `Service/superaccountant/src/main/java/com/arktech/superaccountant/masters/controllers/OrganizationController.java` — current controller with no @PreAuthorize; add per-method guards
- `Service/superaccountant/src/main/java/com/arktech/superaccountant/login/controllers/AuthController.java` — public auth endpoints; verify no @PreAuthorize needed here (they're already in permitAll)
</read_first>
<action>
**WebSecurityConfig.java — update `filterChain` method:**

Replace the `authorizeHttpRequests` block with explicit path declarations per D-15:
```java
.authorizeHttpRequests(auth -> auth
    .requestMatchers(
        "/api/auth/signin",
        "/api/auth/signup",
        "/api/test/**",
        "/error"
    ).permitAll()
    .anyRequest().authenticated()
)
```

Note: `/api/auth/signup?invite=*` query-parameter variant is handled in Plan 5 (invite endpoint). The base `/api/auth/signup` path remains public. Query params are not part of Spring Security path matching — the signup endpoint itself validates the invite token internally.

**OrganizationController.java — add @PreAuthorize per method (D-11, D-12, D-13):**

Add `import org.springframework.security.access.prepost.PreAuthorize;` to imports.

On the existing `createOrganization` POST method (org write operation):
```java
@PreAuthorize("hasRole('OWNER') or hasRole('ACCOUNTANT')")
@PostMapping
public ResponseEntity<?> createOrganization(...)
```

Plan 5 will add additional endpoints (`GET /api/organizations/{id}`, `GET /api/organizations/me/list`, `POST /api/organizations/{id}/select`, `POST /api/organizations/{id}/invites`) with their own guards. For this plan, guard only the existing method.

**AuthController.java — verify no @PreAuthorize needed:**
`/api/auth/signin` and `/api/auth/signup` are public (in `permitAll`). Do NOT add `@PreAuthorize` to these methods — it would conflict with `permitAll` and could break unauthenticated signup. Read the file and confirm no changes are needed.
</action>
<acceptance_criteria>
- `WebSecurityConfig.java` contains `"/api/auth/signin"` in a `requestMatchers(...)` block
- `WebSecurityConfig.java` contains `"/api/auth/signup"` in a `requestMatchers(...)` block
- `WebSecurityConfig.java` does NOT contain `.requestMatchers("/api/auth/**")` (the wildcard form — replaced by exact paths)
- `OrganizationController.java` contains `@PreAuthorize("hasRole('OWNER') or hasRole('ACCOUNTANT')")`
- `OrganizationController.java` contains `import org.springframework.security.access.prepost.PreAuthorize`
- Running `cd Service/superaccountant && ./mvnw compile -q` (with JWT_SECRET set) exits 0
</acceptance_criteria>
</task>

<task id="2">
<title>Add @PreAuthorize guards to UploadController and GstValidationController</title>
<read_first>
- `Service/superaccountant/src/main/java/com/arktech/superaccountant/masters/controllers/UploadController.java` — current upload controller; identify all handler methods to guard
- `Service/superaccountant/src/main/java/com/arktech/superaccountant/gst/controllers/GstValidationController.java` — current GST validation controller; identify all handler methods to guard
</read_first>
<action>
**UploadController.java:**

Read the file to identify all `@GetMapping`, `@PostMapping`, `@PutMapping`, `@DeleteMapping`, `@PatchMapping` methods. Apply `@PreAuthorize` per D-11/D-12/D-14:

- Upload creation (`POST` methods that create upload jobs): `@PreAuthorize("hasRole('OWNER') or hasRole('ACCOUNTANT') or hasRole('DATA_ENTRY_OPERATOR')")`
  - Rationale: data entry operators can upload data; owners and accountants can too.
- Upload read/status (`GET` methods): `@PreAuthorize("isAuthenticated()")`
  - Rationale: any authenticated user in the org can view upload status.
- Resolve/update (`PUT`, `PATCH` methods on findings/resolution): `@PreAuthorize("hasRole('OWNER') or hasRole('ACCOUNTANT') or hasRole('DATA_ENTRY_OPERATOR')")`

If UploadController does not exist yet (only OrganizationController was in the current listing), skip this file and note in the summary that it was absent.

**GstValidationController.java:**

Read the file to identify all handler methods. Apply `@PreAuthorize`:
- GST validation POST (validate vouchers): `@PreAuthorize("hasRole('OWNER') or hasRole('ACCOUNTANT') or hasRole('DATA_ENTRY_OPERATOR')")`
- GST validation GET (read results): `@PreAuthorize("isAuthenticated()")`

Add `import org.springframework.security.access.prepost.PreAuthorize;` to both files.

**General rule for any other controllers discovered during reading:** Any `@RestController` class not in `/api/auth/**` that lacks `@PreAuthorize` on its methods must have guards added following the same pattern:
- Write operations (POST/PUT/PATCH/DELETE): `hasRole('OWNER') or hasRole('ACCOUNTANT')` unless the operation is explicitly data-entry (then include `DATA_ENTRY_OPERATOR`)
- Read operations (GET): `isAuthenticated()`
</action>
<acceptance_criteria>
- If `GstValidationController.java` exists: it contains `@PreAuthorize` on at least one handler method
- If `UploadController.java` exists: it contains `@PreAuthorize` on at least one handler method
- Every `@RestController` in `src/main/java/com/arktech/superaccountant/` that is NOT `AuthController` has at least one `@PreAuthorize` annotation — verify with: `grep -rL "@PreAuthorize" src/main/java/com/arktech/superaccountant/ --include="*.java" | grep -i "Controller"` (output must be empty or only AuthController)
- Running `cd Service/superaccountant && ./mvnw compile -q` (with JWT_SECRET set) exits 0
</acceptance_criteria>
</task>

## Verification

```bash
cd "Service/superaccountant"
export JWT_SECRET="a-32-char-or-longer-dummy-secret-value"

# Compile clean
./mvnw compile -q

# WebSecurityConfig uses exact paths
grep '"/api/auth/signin"' src/main/java/com/arktech/superaccountant/login/security/WebSecurityConfig.java && echo "PASS" || echo "FAIL"

# No wildcard auth/** in permitAll
grep 'api/auth/\*\*' src/main/java/com/arktech/superaccountant/login/security/WebSecurityConfig.java && echo "FAIL: wildcard still present" || echo "PASS: no wildcard"

# OrganizationController has PreAuthorize
grep "@PreAuthorize" src/main/java/com/arktech/superaccountant/masters/controllers/OrganizationController.java && echo "PASS" || echo "FAIL"

# Find any controllers missing @PreAuthorize (should return only AuthController or empty)
grep -rL "@PreAuthorize" src/main/java/com/arktech/superaccountant/ --include="*Controller.java"
```

## must_haves
- `WebSecurityConfig` permits only `/api/auth/signin`, `/api/auth/signup`, `/api/test/**`, `/error` — no other public paths
- Every controller method outside `AuthController` has an explicit `@PreAuthorize` annotation
- Project compiles cleanly after changes

<output>
After completion, create `.planning/phases/01-security-hardening-foundation/01-02-SUMMARY.md`
</output>
