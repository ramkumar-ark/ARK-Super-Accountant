# Phase 1 Verification Report

**Date:** 2026-04-12
**Verdict:** PARTIAL

---

## Success Criteria

| # | Criterion | Status | Evidence |
|---|-----------|--------|----------|
| 1 | JWT env var, startup fail | PASS | `JwtUtils.java` line 22: `@Value("${JWT_SECRET}")`. `@PostConstruct validateJwtSecret()` at line 29 throws `IllegalStateException` if null, blank, or < 32 chars. `application.properties` contains no `jwtSecret=` or `arktech.app.jwtSecret=` — only the comment referencing the env var requirement. |
| 2 | 401 without JWT; 403 with wrong role | PASS | `WebSecurityConfig.java` line 55: `.anyRequest().authenticated()` present. `@EnableMethodSecurity` active. All five non-auth controllers have `@PreAuthorize` on every handler method (verified via grep). PARTIAL concern: `GET /api/auth/invite/{token}` is at `/api/auth/invite/{token}` and is not in the `permitAll` block — this endpoint requires a valid JWT from an unauthenticated signup user, which is a functional break (see Gaps). |
| 3 | `./mvnw test` passes without local PostgreSQL | PASS | `pom.xml` contains `testcontainers-bom`, `testcontainers`, `testcontainers:postgresql`, and `spring-boot-testcontainers` in test scope. `UserRepositoryIT.java` and `OrganizationRepositoryIT.java` both exist with `@Testcontainers`, `@Container PostgreSQLContainer`, and `@DynamicPropertySource`. No hardcoded absolute paths remain in any test file. |
| 4 | OWNER can create org with GSTIN/PAN/address, invite users | PASS | `Organization.java` has `gstin` (VARCHAR 15), `pan` (VARCHAR 10), `registeredAddress` (TEXT), `financialYearStart` (INT, default 4). `CreateOrganizationRequest.java` has `@Pattern` on GSTIN and PAN. `OrganizationController.createOrganization` is guarded with `hasRole('OWNER') or hasRole('ACCOUNTANT')` and wires all fields. `POST /api/organizations/{id}/invites` creates `OrganizationInvite` with UUID token and 7-day expiry. |
| 5 | CA user can retrieve org list, select active org | PASS | `GET /api/organizations/me/list` returns `{organizationId, organizationName, role, isActive}` per membership. `POST /api/organizations/{id}/select` validates membership and issues a new JWT with `organizationId` claim. `OrganizationSelector.tsx` renders for users with 2+ orgs and calls the select endpoint. |

---

## Gaps Found

### GAP-1: `GET /api/auth/invite/{token}` blocked by security filter (functional break)

**What:** The `GET /api/auth/invite/{token}` endpoint in `AuthController` is designed to be public — it is called by the unauthenticated `SignupPage` on mount when an `?invite` param is present. However, `WebSecurityConfig.java` only adds `/api/auth/signin` and `/api/auth/signup` to the `permitAll` block. The `/api/auth/invite/{token}` path is not listed. Spring Security's `.anyRequest().authenticated()` rule catches all other paths, so this endpoint returns 401 to any caller without a JWT — which is every user arriving at the signup link.

**Impact:** The invite flow is broken end-to-end. A user who receives an invite link navigates to `/signup?invite=<token>`, the `SignupPage` calls `GET /api/auth/invite/<token>`, receives 401, renders the `InviteSignupBanner` in error state, and the form is disabled. The invite feature cannot be used.

**Files affected:**
- `Service/superaccountant/src/main/java/com/arktech/superaccountant/login/security/WebSecurityConfig.java` — needs `/api/auth/invite/**` added to `permitAll` block.

**Fix required:**
```java
.requestMatchers(
    "/api/auth/signin",
    "/api/auth/signup",
    "/api/auth/invite/**",   // ADD THIS
    "/api/test/**",
    "/error"
).permitAll()
```

---

## Recommendation

**GAPS: Fix GAP-1 before proceeding to Phase 2.**

All five success criteria are otherwise substantively met. The JWT env-var enforcement, role guards, Testcontainers test suite, Organization entity with GSTIN/PAN/address, and multi-org invite+switch flows are all fully implemented and wired. One security misconfiguration — missing `permitAll` for the public invite pre-validation endpoint — makes the invite feature non-functional at runtime. This is a one-line fix in `WebSecurityConfig.java`.
