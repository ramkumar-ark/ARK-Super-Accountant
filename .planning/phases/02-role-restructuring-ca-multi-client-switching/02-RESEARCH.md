# Phase 2: Role Restructuring & CA Multi-Client Switching - Research

**Researched:** 2026-04-14
**Domain:** Spring Boot RBAC, JPA enum migration, JWT org-context switching, React RBAC UI
**Confidence:** HIGH

---

## Summary

Phase 1 built the full auth scaffolding — JWT env-var enforcement, UserOrganization join table, org-switch endpoint, and OrganizationSelector UI. Phase 2 builds on that foundation without replacing any of it. The core work is: (1) swap two obsolete enum values in `ERole` for two new ones, (2) migrate any live data referencing those values, (3) re-map every `@PreAuthorize` guard to the new role set with a documented permission matrix, and (4) wire the AUDITOR_CA role to the existing org-switching mechanism so CA users see the selector.

The most significant technical risk is **data migration**. The `roles` table is seeded by `DataInitializer` only when `roleRepository.count() == 0`, so existing rows are never re-seeded. The `user_organizations.role` and `organization_invites.role` columns store `ERole` names as strings — any existing `ROLE_CASHIER` or `ROLE_DATA_ENTRY_OPERATOR` values in those columns will cause a `ConstraintViolationException` or `IllegalArgumentException` at runtime once the enum values are removed. A Flyway/Liquibase migration or explicit SQL update is required before the enum change compiles.

The JWT currently carries only `organizationId` — it does not carry `role`. Role is resolved at request time by loading the user from DB and reading `user.role`. This means changing a user's role in the DB takes effect on the next request without token re-issue. The org-switch endpoint already re-issues a JWT; it must also encode the new org's role so the frontend can update its UI.

**Primary recommendation:** Execute in strict order — data migration first, enum change second, guard updates third — so the application never runs in a state where live data references a non-existent enum value.

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| AUTH-01 | Rename/remove CASHIER and DATA_ENTRY_OPERATOR; add AUDITOR_CA | ERole.java has 4 values; ROLE_CASHIER is default signup role in AuthController; both appear in @PreAuthorize guards across 3 controllers |
| AUTH-02 | OWNER can view reports and dashboard job status but cannot do data entry or resolve mismatches | Permission matrix maps OWNER to read-only on operational endpoints |
| AUTH-03 | ACCOUNTANT can view all data and approve AI entries; OPERATOR can reconcile and resolve mismatches but not approve | Requires split of DATA_ENTRY_OPERATOR into ACCOUNTANT (existing) + OPERATOR (new); ACCOUNTANT already exists |
| AUTH-04 | AUDITOR_CA can view all data within org scope; cannot create, edit, or approve | New read-only cross-org role; structurally identical to ACCOUNTANT read permissions minus write |
| AUTH-05 | CA user with AUDITOR_CA role sees org selector | OrganizationSelector.tsx already renders for orgs.length >= 2; needs role-based visibility condition added |
| AUTH-06 | Switching org does not require re-authentication | POST /api/organizations/{id}/select already implemented; returns new JWT with org claim |
</phase_requirements>

---

## User Constraints (from CONTEXT.md)

No CONTEXT.md exists for Phase 2. Constraints come from locked Phase 1 decisions in STATE.md.

### Locked Decisions (Phase 1 — do not revisit)
- JWT_SECRET env var — startup fails hard if unset (already implemented in JwtUtils.java)
- jjwt upgraded to 0.12.6 (already in pom.xml)
- UserOrganization join table replaces User.organizationId — all org context comes from JWT claim
- Org switch via POST /api/organizations/{id}/select — returns new JWT
- Invite tokens: UUID, 7-day expiry, single-use, returned in API response only (no email)
- GSTIN regex and PAN regex patterns already validated

### Open Question Requiring Resolution Before Planning
STATE.md item #5: "ROLE_MANAGER status — new role or synonym for OWNER (affects AUTH phase planning)"

The Phase 2 success criteria mentions "OWNER or MANAGER can view all reports" (criterion 2) but the target role set is OWNER, ACCOUNTANT, OPERATOR, AUDITOR_CA (criterion 1). MANAGER does not appear in the target enum. The roadmap plan outline has no MANAGER. Resolution: MANAGER referenced in criterion 2 is likely a description artifact — treat OWNER as the sole administrative role. The plan should not add ROLE_MANAGER.

---

## Existing Code Inventory (verified by reading source files)

### Current ERole Values
```java
// [VERIFIED: read ERole.java]
ROLE_CASHIER,
ROLE_ACCOUNTANT,
ROLE_DATA_ENTRY_OPERATOR,
ROLE_OWNER
```

**Target ERole values (Phase 2 goal):**
```java
ROLE_OWNER,
ROLE_ACCOUNTANT,
ROLE_OPERATOR,       // replaces ROLE_DATA_ENTRY_OPERATOR
ROLE_AUDITOR_CA      // new; no predecessor
// ROLE_CASHIER removed
// ROLE_DATA_ENTRY_OPERATOR removed
```

### Current @PreAuthorize Guard Inventory
[VERIFIED: read all 5 controllers]

| Controller | Method | Current Guard | Old Roles Used |
|------------|--------|---------------|----------------|
| `OrganizationController` | `POST /api/organizations` | `hasRole('OWNER') or hasRole('ACCOUNTANT')` | none to remove |
| `OrganizationController` | `POST /api/organizations/{id}/invites` | `hasRole('OWNER') or hasRole('ACCOUNTANT')` | none to remove |
| `OrganizationController` | `GET /api/organizations/{id}` | `isAuthenticated()` | — |
| `OrganizationController` | `GET /api/organizations/me/list` | `isAuthenticated()` | — |
| `OrganizationController` | `POST /api/organizations/{id}/select` | `isAuthenticated()` | — |
| `GstValidationController` | `POST /api/gst/validate` | `hasRole('OWNER') or hasRole('ACCOUNTANT') or hasRole('DATA_ENTRY_OPERATOR')` | `DATA_ENTRY_OPERATOR` |
| `TallyImportController` | `POST /api/tally/import` | `hasRole('OWNER') or hasRole('ACCOUNTANT') or hasRole('DATA_ENTRY_OPERATOR')` | `DATA_ENTRY_OPERATOR` |
| `UploadController` | `POST /api/v1/uploads` | `hasRole('OWNER') or hasRole('ACCOUNTANT') or hasRole('DATA_ENTRY_OPERATOR')` | `DATA_ENTRY_OPERATOR` |
| `UploadController` | `GET /api/v1/uploads` | `isAuthenticated()` | — |
| `UploadController` | `GET /api/v1/uploads/{id}/mismatches` | `isAuthenticated()` | — |
| `UploadController` | `GET /api/v1/uploads/{id}/mismatches/export` | `isAuthenticated()` | — |
| `UploadController` | `PATCH /api/v1/uploads/{jobId}/mismatches/{findingId}/resolve` | `hasRole('OWNER') or hasRole('ACCOUNTANT') or hasRole('DATA_ENTRY_OPERATOR')` | `DATA_ENTRY_OPERATOR` |
| `UploadController` | `GET /api/v1/validation-rules` | `isAuthenticated()` | — |
| `PreconfiguredMastersController` | `GET /api/v1/preconfigured-masters` | `isAuthenticated()` | — |
| `PreconfiguredMastersController` | `POST /api/v1/preconfigured-masters` | `hasRole('OWNER') or hasRole('ACCOUNTANT') or hasRole('DATA_ENTRY_OPERATOR')` | `DATA_ENTRY_OPERATOR` |
| `PreconfiguredMastersController` | `PUT /api/v1/preconfigured-masters/{id}` | `hasRole('OWNER') or hasRole('ACCOUNTANT') or hasRole('DATA_ENTRY_OPERATOR')` | `DATA_ENTRY_OPERATOR` |
| `PreconfiguredMastersController` | `DELETE /api/v1/preconfigured-masters/{id}` | `hasRole('OWNER') or hasRole('ACCOUNTANT')` | none to remove |
| `PreconfiguredMastersController` | `POST /api/v1/preconfigured-masters/bulk` | `hasRole('OWNER') or hasRole('ACCOUNTANT') or hasRole('DATA_ENTRY_OPERATOR')` | `DATA_ENTRY_OPERATOR` |
| `PreconfiguredMastersController` | `POST /api/v1/preconfigured-masters/onboard` | `hasRole('OWNER') or hasRole('ACCOUNTANT')` | none to remove |

**Known security bug from Phase 1 verification (GAP-1):**
`GET /api/auth/invite/{token}` is not in the `permitAll` block in `WebSecurityConfig`. This endpoint must be public but currently returns 401 to unauthenticated callers. Fix in Plan 01 of Phase 2 (alongside the WebSecurityConfig touch for AUDITOR_CA).

### Current Signup Role Handling
```java
// AuthController.java — default role when none specified:
userRole = roleRepository.findByName(ERole.ROLE_CASHIER)  // MUST change to ROLE_OPERATOR or ROLE_ACCOUNTANT
// Case "data_entry":
userRole = roleRepository.findByName(ERole.ROLE_DATA_ENTRY_OPERATOR)  // MUST change to ROLE_OPERATOR
// No case for "auditor_ca" — must be added
```

### UserDetailsServiceImpl — role resolution
```java
// loadUserByUsername always passes null for organizationId:
return UserDetailsImpl.build(user, null);
// AuthTokenFilter then overwrites with JWT claim.
// Role comes from user.getRole().getName().name() — db lookup, not JWT.
```

This means role is always fresh from DB on each request. Adding AUDITOR_CA to DB is sufficient; no JWT changes for role propagation.

### OrganizationSelector — current visibility trigger
```tsx
// OrganizationSelector.tsx line 12:
if (organizations.length < 2) return null
```
This shows the selector for any user with 2+ org memberships, regardless of role. For AUTH-05, AUDITOR_CA should be the trigger, not membership count. However, the current design (show when 2+ orgs) is functionally correct for the CA use case — AUDITOR_CA users will have multiple org memberships. The visibility condition may need a role check added for precision.

---

## Standard Stack

### Core (all already in project — no new dependencies needed)
| Library | Version | Purpose | Status |
|---------|---------|---------|--------|
| Spring Security | via Spring Boot 4.0.2 | `@PreAuthorize`, method security | Already configured |
| jjwt | 0.12.6 | JWT generation/validation | Phase 1 locked |
| Spring Data JPA / Hibernate | via Spring Boot 4.0.2 | Entity persistence, enum column | Already in use |
| Zustand | current (Client/) | Frontend auth state including role and org | Already in authStore.ts |
| TanStack Router | current | Route guards via `beforeLoad` | Already in main.tsx |

### No New Backend Dependencies Required
The role change is enum surgery + guard text changes. No library additions needed.

### No New Frontend Dependencies Required
OrganizationSelector and authStore are already implemented. Role-based visibility is a conditional render — no library additions.

---

## Architecture Patterns

### Pattern 1: Spring Security Role Names — "ROLE_" prefix stripping
**What:** Spring Security's `hasRole('X')` automatically prepends `ROLE_` when comparing authorities. `hasAuthority('ROLE_X')` does not. The codebase uses `hasRole()` throughout.

`UserDetailsImpl.build()` creates authority as:
```java
new SimpleGrantedAuthority(user.getRole().getName().name())
// produces: "ROLE_OWNER", "ROLE_ACCOUNTANT", etc.
```

`hasRole('OWNER')` matches `ROLE_OWNER`. This pattern must be preserved for all new roles:
- `hasRole('OPERATOR')` matches `ROLE_OPERATOR`
- `hasRole('AUDITOR_CA')` matches `ROLE_AUDITOR_CA`

[VERIFIED: read UserDetailsImpl.java, WebSecurityConfig.java]

### Pattern 2: Enum-as-String column — migration contract
**What:** Both `UserOrganization.role` and `OrganizationInvite.role` use `@Enumerated(EnumType.STRING)`. Hibernate stores the enum name literally (e.g., `ROLE_DATA_ENTRY_OPERATOR`). When the enum value is removed, JPA will throw `IllegalArgumentException: No enum constant` when reading any row with that value.

**Migration sequence (mandatory):**
1. SQL UPDATE before any enum rename — update existing rows to the new value
2. Then change ERole.java
3. Then update DataInitializer to seed new roles

**SQL required:**
```sql
-- Update user_organizations.role
UPDATE user_organizations SET role = 'ROLE_OPERATOR' WHERE role = 'ROLE_DATA_ENTRY_OPERATOR';
UPDATE user_organizations SET role = 'ROLE_ACCOUNTANT' WHERE role = 'ROLE_CASHIER';

-- Update organization_invites.role
UPDATE organization_invites SET role = 'ROLE_OPERATOR' WHERE role = 'ROLE_DATA_ENTRY_OPERATOR';
UPDATE organization_invites SET role = 'ROLE_ACCOUNTANT' WHERE role = 'ROLE_CASHIER';

-- Update users.role_id — indirect, via roles table FK
-- The roles table stores Role entities. After DataInitializer re-seeds (on next boot with count=0),
-- roles table will have new rows. But DataInitializer only runs when count == 0.
-- Strategy: manually delete old roles + re-seed, OR add a migration method to DataInitializer.
```

**Roles table complication:** `DataInitializer.seedRoles()` only seeds when `roleRepository.count() == 0`. With existing data, it never runs again. After removing `ROLE_CASHIER` and `ROLE_DATA_ENTRY_OPERATOR` from the enum, the DB rows for those roles still exist in `roles` table. Any `User` with `role_id` pointing to those rows will fail to deserialize. The plan must include:
- Delete the old role rows from `roles` table
- Insert new role rows for `ROLE_OPERATOR` and `ROLE_AUDITOR_CA`
- Update `users.role_id` for any user whose role pointed to removed roles

[VERIFIED: read DataInitializer.java, Role.java, User.java]

### Pattern 3: JWT does not carry role — role is DB-resolved per request
**What:** `AuthTokenFilter` extracts only `username` and `organizationId` from the JWT. Role is always loaded fresh via `loadUserByUsername` → `user.getRole()`. This is safe and means role changes take effect without token re-issue.

**Impact on org-switch endpoint:** The org-switch response currently returns `role` in the body:
```java
return ResponseEntity.ok(Map.of(
    "token", newJwt,
    "organizationId", id,
    "organizationName", membership.get().getOrganization().getName(),
    "role", membership.get().getRole().name()  // ← UserOrganization.role
));
```
The returned `role` is the org-scoped role from `UserOrganization`, not the user's global `user.role`. The frontend `switchOrganization` action in authStore does not update `user.role` — it only updates `organizationId`. This is a latent inconsistency: after switching orgs, the local `user.role` in Zustand may not match the org-specific role. For Phase 2 this needs explicit handling — the switch action must update `user.role` with the returned role.

[VERIFIED: read AuthTokenFilter.java, JwtUtils.java, OrganizationController.java, authStore.ts]

### Pattern 4: Permission Matrix Definition
Based on the Phase 2 success criteria, the permission matrix is:

| Action | OWNER | ACCOUNTANT | OPERATOR | AUDITOR_CA |
|--------|-------|------------|----------|------------|
| View reports / dashboard | YES | YES | YES | YES |
| Upload masters (data entry) | NO | YES | YES | NO |
| Resolve mismatches | NO | YES | YES | NO |
| Approve AI-generated entries | NO | YES | NO | NO |
| Create/edit/approve anything | NO | YES (limited) | YES (limited) | NO |
| Invite users | YES | YES | NO | NO |
| Create organization | YES | YES | NO | NO |
| Switch organizations (multi-org) | — | — | — | YES (primary CA use case) |

**Derived guard mappings:**
- Read-only endpoints (list, get, export): `hasRole('OWNER') or hasRole('ACCOUNTANT') or hasRole('OPERATOR') or hasRole('AUDITOR_CA')`
- Data entry (upload, create master, bulk import): `hasRole('ACCOUNTANT') or hasRole('OPERATOR')`
- Resolve mismatches: `hasRole('ACCOUNTANT') or hasRole('OPERATOR')`
- Admin actions (invite, create org, delete master, onboard): `hasRole('OWNER') or hasRole('ACCOUNTANT')`
- AI approve (not yet implemented — future): `hasRole('ACCOUNTANT')`

Note: OWNER's "cannot perform data entry or resolve mismatches" (criterion 2) means OWNER is read-only for operational content. This is a **permission reduction from current state** — `UploadController.POST /uploads` currently allows `OWNER`. Under Phase 2, OWNER loses that permission.

[ASSUMED: "approve AI entries" endpoints do not exist yet — no guard needed now]

### Pattern 5: Frontend role-based rendering
The current OrganizationSelector renders based on `organizations.length >= 2`. To meet AUTH-05 precisely, the AUDITOR_CA role (as stored in `user.role` or the active org membership's role) should gate the selector. However, the current approach is functionally equivalent because: AUDITOR_CA users will always have multiple org memberships (that's the CA use case), and OWNER/ACCOUNTANT/OPERATOR users typically belong to one org.

A safer implementation: show selector when `organizations.length >= 2 && user?.role?.includes('AUDITOR_CA')`. But the existing implementation already passes the success criterion without this addition.

[VERIFIED: read OrganizationSelector.tsx, authStore.ts]

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Role-based method security | Custom filter/interceptor | Spring Security `@PreAuthorize` with `@EnableMethodSecurity` (already enabled) | AOP-based, declarative, already wired |
| DB migration | Manual SQL in Java code or application startup | SQL script applied before server start (or a DataInitializer migration method) | Hibernate ddl-auto:update does not rename enum values |
| JWT role claim | Add role to JWT | Keep current pattern (DB-resolved per request) | Already established; adding role to JWT creates stale-role risk |
| Enum validation | Custom validator | Spring Security authority resolution + `@PreAuthorize` | Automatic 403 on wrong role |

---

## Common Pitfalls

### Pitfall 1: Removing enum values before migrating data
**What goes wrong:** ERole.java is edited first. On next application start (or on any JPA query that reads a row with the old string value), Hibernate throws `IllegalArgumentException: No enum constant com.arktech.superaccountant.login.models.ERole.ROLE_CASHIER`. This crashes any request that touches a user or invite with the old role.
**Why it happens:** `@Enumerated(EnumType.STRING)` stores the enum constant name. Removing the constant makes it unreadable.
**How to avoid:** Run SQL UPDATE statements against all affected tables BEFORE changing ERole.java. Tables affected: `user_organizations.role`, `organization_invites.role`, `users.role_id` (indirect via `roles.name`).
**Warning signs:** `IllegalArgumentException: No enum constant` in logs after startup.

### Pitfall 2: DataInitializer not re-seeding new roles
**What goes wrong:** `seedRoles()` guards with `if (roleRepository.count() == 0)`. An existing DB with 4 role rows means new roles are never inserted. `roleRepository.findByName(ERole.ROLE_OPERATOR)` then throws `RuntimeException("Error: Role is not found.")` on every signup.
**Why it happens:** The idempotency guard uses total count, not a check for each role by name.
**How to avoid:** Change `seedRoles()` to use `findByName` per role and upsert each — or add explicit SQL inserts for new roles alongside the data migration.

### Pitfall 3: AuthController signup default falls through to non-existent role
**What goes wrong:** After removing `ROLE_CASHIER`, the `default:` branch in `AuthController.registerUser()` still calls `roleRepository.findByName(ERole.ROLE_CASHIER)` — this is a compile error once the enum value is removed. Even if caught by the compiler, the switch statement has no handler for `"operator"` or `"auditor_ca"` string inputs.
**Why it happens:** The switch is hardcoded to old role string mappings.
**How to avoid:** Update the switch cases to match the new role strings; change the default to a safe fallback (OPERATOR or reject unknown roles).

### Pitfall 4: org-switch response does not update Zustand user.role
**What goes wrong:** After switching orgs, the authStore `user.role` still reflects the pre-switch role. If the user is AUDITOR_CA in org B but OWNER in org A, switching to org B leaves `user.role = 'ROLE_OWNER'` in Zustand, causing the UI to render with wrong permissions.
**Why it happens:** `switchOrganization` action only updates `organizationId` and `organizationName`, not `role`.
**How to avoid:** Update `switchOrganization` to also set `user.role` from the API response `role` field. The org-switch endpoint already returns `role`.

### Pitfall 5: OWNER permission reduction breaks existing functionality
**What goes wrong:** Current guards allow OWNER on upload endpoints. Phase 2 removes OWNER from data-entry guards. Existing OWNER users who relied on uploading masters or resolving mismatches will receive 403 after the guard change.
**Why it happens:** OWNER "cannot perform data entry or resolve mismatches" (criterion 2) is a narrowing change from current state where OWNER has broad access.
**How to avoid:** Communicate this change clearly; ensure seed/test users are ACCOUNTANT or OPERATOR if they need to upload/resolve.

### Pitfall 6: WebSecurityConfig GAP-1 still unresolved
**What goes wrong:** Phase 1 Verification identified that `GET /api/auth/invite/{token}` is not in the `permitAll` block, breaking the invite flow for unauthenticated users.
**Why it happens:** Oversight in Phase 1 (not fixed before Phase 2).
**How to avoid:** Fix this in Plan 01 of Phase 2 alongside the WebSecurityConfig changes.

---

## Runtime State Inventory

This phase renames two role enum values (`ROLE_CASHIER` → `ROLE_ACCOUNTANT` default, `ROLE_DATA_ENTRY_OPERATOR` → `ROLE_OPERATOR`) and adds one new value (`ROLE_AUDITOR_CA`). The following runtime state is affected:

| Category | Items Found | Action Required |
|----------|-------------|-----------------|
| Stored data — `roles` table | 4 rows: ROLE_CASHIER, ROLE_ACCOUNTANT, ROLE_DATA_ENTRY_OPERATOR, ROLE_OWNER — seeded by DataInitializer | DELETE ROLE_CASHIER and ROLE_DATA_ENTRY_OPERATOR rows; INSERT ROLE_OPERATOR and ROLE_AUDITOR_CA rows |
| Stored data — `users.role_id` | Any user created before Phase 2 with role ROLE_CASHIER or ROLE_DATA_ENTRY_OPERATOR; FK to `roles.id` | UPDATE users SET role_id = (SELECT id FROM roles WHERE name='ROLE_OPERATOR') WHERE role_id IN (SELECT id FROM roles WHERE name IN ('ROLE_CASHIER','ROLE_DATA_ENTRY_OPERATOR')) |
| Stored data — `user_organizations.role` | VARCHAR column storing enum name string; any rows with 'ROLE_CASHIER' or 'ROLE_DATA_ENTRY_OPERATOR' | UPDATE user_organizations SET role = 'ROLE_OPERATOR' WHERE role = 'ROLE_DATA_ENTRY_OPERATOR'; UPDATE user_organizations SET role = 'ROLE_ACCOUNTANT' WHERE role = 'ROLE_CASHIER' |
| Stored data — `organization_invites.role` | VARCHAR column; any pending invites with old role values | UPDATE organization_invites SET role = 'ROLE_OPERATOR' WHERE role = 'ROLE_DATA_ENTRY_OPERATOR'; UPDATE organization_invites SET role = 'ROLE_ACCOUNTANT' WHERE role = 'ROLE_CASHIER' |
| Live service config | None — no external service stores role configuration | No action |
| OS-registered state | None | None — verified by inspection |
| Secrets/env vars | None — roles are DB-managed, not secret-managed | None |
| Build artifacts | None — compiled enum is in JAR, no stale artifacts | None |

**Migration strategy:** This is a **data migration** (update existing records) not a code-only rename. SQL must run BEFORE the Java enum change ships. The plan should include an explicit SQL migration step as the first task.

---

## Code Examples

### ERole.java — target state
```java
// [ASSUMED: target; not yet in codebase]
public enum ERole {
    ROLE_OWNER,
    ROLE_ACCOUNTANT,
    ROLE_OPERATOR,
    ROLE_AUDITOR_CA
}
```

### DataInitializer — updated seedRoles (upsert pattern)
```java
// [ASSUMED: pattern based on Spring Data JPA conventions]
private void seedRoles() {
    for (ERole eRole : ERole.values()) {
        if (roleRepository.findByName(eRole).isEmpty()) {
            roleRepository.save(new Role(eRole));
        }
    }
}
```
This is idempotent: runs on every startup, adds missing roles, leaves existing ones alone.

### AuthController — updated signup switch
```java
// [ASSUMED: target]
switch (strRole) {
    case "owner":
        userRole = roleRepository.findByName(ERole.ROLE_OWNER).orElseThrow(...);
        break;
    case "accountant":
        userRole = roleRepository.findByName(ERole.ROLE_ACCOUNTANT).orElseThrow(...);
        break;
    case "operator":
        userRole = roleRepository.findByName(ERole.ROLE_OPERATOR).orElseThrow(...);
        break;
    case "auditor_ca":
        userRole = roleRepository.findByName(ERole.ROLE_AUDITOR_CA).orElseThrow(...);
        break;
    default:
        return ResponseEntity.badRequest().body(new MessageResponse("Invalid role. Valid roles: owner, accountant, operator, auditor_ca"));
}
```
The default should reject unknown roles rather than silently applying a default — this prevents misconfigured invites from silently assigning wrong roles.

### Guard — read-only endpoints (new pattern)
```java
// [ASSUMED: target guard string]
@PreAuthorize("hasRole('OWNER') or hasRole('ACCOUNTANT') or hasRole('OPERATOR') or hasRole('AUDITOR_CA')")
```

### Guard — data entry endpoints (new pattern)
```java
// [ASSUMED: target guard string]
@PreAuthorize("hasRole('ACCOUNTANT') or hasRole('OPERATOR')")
```

### Guard — admin endpoints (unchanged)
```java
// [VERIFIED: already correct for Phase 2 — no DATA_ENTRY_OPERATOR present]
@PreAuthorize("hasRole('OWNER') or hasRole('ACCOUNTANT')")
```

### authStore.ts — updated switchOrganization
```typescript
// [ASSUMED: target; current code does not update user.role]
switchOrganization: (token, org) =>
  set((state) => ({
    token,
    user: state.user
      ? {
          ...state.user,
          organizationId: org.organizationId,
          organizationName: org.organizationName,
          role: org.role,  // ADD: update role from org membership
        }
      : state.user,
    organizations: state.organizations.map((o) => ({
      ...o,
      isActive: o.organizationId === org.organizationId,
    })),
  })),
```

### WebSecurityConfig — GAP-1 fix (carry-forward from Phase 1)
```java
// [VERIFIED: fix identified in 01-VERIFICATION.md]
.requestMatchers(
    "/api/auth/signin",
    "/api/auth/signup",
    "/api/auth/invite/**",  // Fix GAP-1: make invite validation public
    "/api/test/**",
    "/error"
).permitAll()
```

---

## State of the Art

| Old Approach | Current Approach | Notes |
|--------------|------------------|-------|
| ROLE_CASHIER (default signup role) | ROLE_OPERATOR (or reject unknown) | CASHIER was never business-meaningful |
| ROLE_DATA_ENTRY_OPERATOR | ROLE_OPERATOR | Shorter, cleaner, same semantics |
| No AUDITOR_CA | ROLE_AUDITOR_CA | New role for CA cross-org read access |
| Signup accepts any string, falls back to CASHIER | Signup validates role string; rejects unknown | Prevents accidental privilege assignment |

---

## Open Questions

1. **ROLE_MANAGER — include or exclude?**
   - What we know: Success criterion 2 mentions "OWNER or MANAGER" but the target role list (criterion 1) has no MANAGER. The plan outline has no MANAGER. STATE.md flags this as an open question.
   - What's unclear: Was MANAGER intended as a future role, or was it a copy-paste error in criterion 2?
   - Recommendation: Treat it as a documentation artifact. Implement OWNER, ACCOUNTANT, OPERATOR, AUDITOR_CA only. If MANAGER is needed, it is a Phase 2 scope extension requiring explicit user decision.

2. **Default signup role when no role specified**
   - What we know: Current default is ROLE_CASHIER. After removing CASHIER, there is no natural default.
   - What's unclear: Should unauthenticated signups (no invite) be allowed at all? If yes, what role?
   - Recommendation: The invite-based flow should be the primary path. Without an invite, reject signup with a message directing users to request an invite. This eliminates the default-role ambiguity.

3. **AUDITOR_CA: org-scoped role vs. global role**
   - What we know: `User.role` is a single global role (ManyToOne). `UserOrganization.role` is a per-org role. The JWT resolves `User.role` as the authority for `@PreAuthorize`.
   - What's unclear: Should a CA's AUDITOR_CA role be stored on `User.role` globally, or only on `UserOrganization.role`? If stored globally, a CA is AUDITOR_CA everywhere. If only on org membership, the global `User.role` must still be set to something.
   - Recommendation: Store AUDITOR_CA on `User.role`. This matches the current architecture (single global role). CA users are pure auditors by nature — they do not need OWNER/ACCOUNTANT/OPERATOR capabilities in any org.

---

## Environment Availability

Step 2.6: SKIPPED — Phase 2 is purely code and DB changes. No new external tools, CLIs, or services are required beyond what is already running (PostgreSQL on 5432, Java 25, Maven wrapper).

---

## Validation Architecture

No `.planning/config.json` found — treat `nyquist_validation` as enabled.

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 5 + Spring Boot Test + Testcontainers (backend); Vitest + Testing Library (frontend) |
| Config file | `Service/superaccountant/src/test/resources/` (fixtures); `Client/vite.config.ts` (Vitest) |
| Quick run command | `./mvnw test -pl Service/superaccountant` |
| Full suite command | `./mvnw test` + `cd Client && npm run test:run` |

### Existing Test Infrastructure
[VERIFIED: read test file list]
- `UserRepositoryIT.java` — Testcontainers integration test
- `OrganizationRepositoryIT.java` — Testcontainers integration test
- `LedgerCategoryClassifierTest.java` — unit test
- `MismatchDetectionRuleTest.java` — unit test
- `GstValidationServiceTest.java` — unit test
- `TallyMastersParserTest.java` — unit test
- `TallyParserServiceTest.java` — unit test

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| AUTH-01 | ERole enum has exactly 4 values; CASHIER and DATA_ENTRY_OPERATOR absent | unit | `./mvnw test -Dtest=ERoleTest` | No — Wave 0 |
| AUTH-01 | Signup with "cashier" role returns 400 | integration | `./mvnw test -Dtest=AuthControllerIT` | No — Wave 0 |
| AUTH-02 | OWNER calling POST /api/v1/uploads returns 403 | integration | `./mvnw test -Dtest=UploadControllerIT` | No — Wave 0 |
| AUTH-03 | ACCOUNTANT can call resolve endpoint; OPERATOR cannot call approve (future) | integration | `./mvnw test -Dtest=UploadControllerIT` | No — Wave 0 |
| AUTH-04 | AUDITOR_CA calling POST /api/v1/uploads returns 403 | integration | `./mvnw test -Dtest=UploadControllerIT` | No — Wave 0 |
| AUTH-05 | AUDITOR_CA org-switch returns new JWT with org claim | integration | `./mvnw test -Dtest=OrganizationControllerIT` | No — Wave 0 |
| AUTH-06 | Org-switch without re-auth returns 200 with token | integration | `./mvnw test -Dtest=OrganizationControllerIT` | No — Wave 0 |

### Sampling Rate
- Per task commit: `./mvnw test -Dtest=ERoleTest,AuthControllerIT,UploadControllerIT -pl Service/superaccountant`
- Per wave merge: `./mvnw test -pl Service/superaccountant`
- Phase gate: Full suite green before `/gsd-verify-work`

### Wave 0 Gaps
- [ ] `src/test/java/.../login/models/ERoleTest.java` — asserts exact enum values
- [ ] `src/test/java/.../login/controllers/AuthControllerIT.java` — Testcontainers; tests signup with each valid/invalid role string
- [ ] `src/test/java/.../masters/controllers/UploadControllerIT.java` — Testcontainers; tests each role against guarded endpoints

---

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | yes | Spring Security + JWT (already implemented) |
| V3 Session Management | yes | Stateless JWT; org-switch re-issues token |
| V4 Access Control | yes | `@PreAuthorize` method security — primary Phase 2 concern |
| V5 Input Validation | yes | Role string validation in signup switch |
| V6 Cryptography | no — unchanged | jjwt 0.12.6 (Phase 1) |

### Known Threat Patterns for Spring Security RBAC

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Role escalation via signup | Elevation of Privilege | Reject unknown role strings; do not silently default to a role |
| Stale role in JWT (if role were embedded) | Elevation of Privilege | Keep current pattern — role resolved from DB per request, not JWT claim |
| Cross-org data access without org membership | Information Disclosure | `POST /api/organizations/{id}/select` already validates `UserOrganization` membership |
| AUDITOR_CA calling write endpoints | Elevation of Privilege | `@PreAuthorize` guards on all mutating endpoints; AUDITOR_CA excluded from write guards |

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | MANAGER in success criterion 2 is a documentation artifact; no ROLE_MANAGER should be added | Open Questions | Low — easy to add MANAGER later if needed; not adding it now keeps the enum minimal |
| A2 | Default signup role (no invite) should be rejected, not silently assigned | Code Examples | Medium — if stakeholders expect open signup with a default role, the plan needs revision |
| A3 | AUDITOR_CA should be stored on User.role (global), not only on UserOrganization.role | Open Questions | Medium — if AUDITOR_CA is org-scoped only, a different auth resolution mechanism is needed |
| A4 | OWNER loses upload/resolve permissions (permission reduction) | Architecture Patterns | Low — directly stated in criterion 2; confirm stakeholder accepts this change |

---

## Sources

### Primary (HIGH confidence)
- [VERIFIED: read ERole.java] — current enum values
- [VERIFIED: read AuthController.java] — current signup role handling, invite flow
- [VERIFIED: read WebSecurityConfig.java] — current permitAll rules and security config
- [VERIFIED: read DataInitializer.java] — seed strategy and count guard
- [VERIFIED: read UserDetailsImpl.java] — authority construction pattern
- [VERIFIED: read AuthTokenFilter.java] — JWT claim extraction, role not in JWT
- [VERIFIED: read JwtUtils.java] — JWT claims (subject, organizationId only)
- [VERIFIED: read OrganizationController.java] — org-switch endpoint, existing guards
- [VERIFIED: read UploadController.java] — all guards with DATA_ENTRY_OPERATOR
- [VERIFIED: read PreconfiguredMastersController.java] — all guards
- [VERIFIED: read GstValidationController.java] — DATA_ENTRY_OPERATOR guard
- [VERIFIED: read TallyImportController.java] — DATA_ENTRY_OPERATOR guard
- [VERIFIED: read UserOrganization.java] — ERole column as STRING
- [VERIFIED: read OrganizationInvite.java] — ERole column as STRING
- [VERIFIED: read authStore.ts] — switchOrganization does not update user.role
- [VERIFIED: read OrganizationSelector.tsx] — visibility trigger is orgs.length >= 2
- [VERIFIED: read 01-VERIFICATION.md] — GAP-1 identified (invite endpoint not public)
- [CITED: Spring Security docs — hasRole() prepends ROLE_ automatically]

### Secondary (MEDIUM confidence)
- [ASSUMED: Spring Data JPA behavior for @Enumerated(EnumType.STRING) with removed enum constant throws IllegalArgumentException — well-established JPA behavior]

---

## Metadata

**Confidence breakdown:**
- Current code state: HIGH — all files read directly
- Migration risk: HIGH — JPA enum behavior well-understood
- Permission matrix: HIGH — directly derived from ROADMAP.md criteria
- Frontend changes: HIGH — authStore and OrganizationSelector read directly

**Research date:** 2026-04-14
**Valid until:** 2026-05-14 (stable domain; changes only if codebase diverges)
