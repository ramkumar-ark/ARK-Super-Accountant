---
phase: 1
plan: 5
subsystem: auth-multi-org
tags: [multi-org, invite-flow, jwt, membership, frontend-components]
dependency_graph:
  requires: [plan-04]
  provides: [UserOrganization, OrganizationInvite, invite-endpoints, org-switch, OrganizationSelector]
  affects: [AuthController, OrganizationController, JwtUtils, AuthTokenFilter, UserDetailsImpl, User, authStore, SignupPage]
tech_stack:
  added: []
  patterns: [UUID-token invite, JWT org claim, join-table multi-tenancy, invite-banner UX]
key_files:
  created:
    - Service/superaccountant/src/main/java/com/arktech/superaccountant/masters/models/UserOrganization.java
    - Service/superaccountant/src/main/java/com/arktech/superaccountant/masters/models/OrganizationInvite.java
    - Service/superaccountant/src/main/java/com/arktech/superaccountant/masters/repository/UserOrganizationRepository.java
    - Service/superaccountant/src/main/java/com/arktech/superaccountant/masters/repository/OrganizationInviteRepository.java
    - Client/src/components/OrganizationSelector.tsx
    - Client/src/components/InviteSignupBanner.tsx
    - Client/src/components/InviteTokenDisplay.tsx
  modified:
    - Service/superaccountant/src/main/java/com/arktech/superaccountant/login/models/User.java
    - Service/superaccountant/src/main/java/com/arktech/superaccountant/login/security/services/UserDetailsImpl.java
    - Service/superaccountant/src/main/java/com/arktech/superaccountant/login/security/services/UserDetailsServiceImpl.java
    - Service/superaccountant/src/main/java/com/arktech/superaccountant/login/security/jwt/JwtUtils.java
    - Service/superaccountant/src/main/java/com/arktech/superaccountant/login/security/jwt/AuthTokenFilter.java
    - Service/superaccountant/src/main/java/com/arktech/superaccountant/masters/controllers/OrganizationController.java
    - Service/superaccountant/src/main/java/com/arktech/superaccountant/login/controllers/AuthController.java
    - Client/src/store/authStore.ts
    - Client/src/pages/SignupPage.tsx
decisions:
  - "UserOrganization join table replaces User.organizationId for multi-org membership (D-01)"
  - "JWT organizationId claim is source of truth for active org; resolved in AuthTokenFilter per request"
  - "UUID.randomUUID() for invite tokens (122-bit entropy, T-05-01 mitigation)"
  - "Invite token consumed via usedAt=Instant.now() on signup; isValid() checks both usedAt==null and expiry (D-10, T-05-03/04)"
  - "org-switch endpoint validates UserOrganization membership before issuing new JWT (T-05-02)"
  - "vite.config.ts tsc error is pre-existing vitest/vite type conflict; source files pass tsc --noEmit --skipLibCheck"
metrics:
  duration_seconds: 669
  completed_date: "2026-04-12"
  tasks_completed: 3
  files_changed: 16
requirements: [ORG-02, ORG-03]
---

# Phase 1 Plan 5: Invite Flow + Multi-Org Membership Summary

## One-liner

Multi-org membership via UserOrganization join table with UUID invite tokens, 7-day expiry, org-switch JWT reissue, and OrganizationSelector/InviteSignupBanner frontend components.

## What Was Built

### Task 1 — Entities, repositories, and auth layer refactor

- **UserOrganization** (`user_organizations` table): join table linking users to organizations with `role` (ERole) and `joinedAt`. Hibernate auto-creates the table on next startup.
- **OrganizationInvite** (`organization_invites` table): invite token entity with UUID token (`UUID.randomUUID()`), 7-day `expiresAt`, `usedAt` for one-time-use enforcement, and `isValid()` helper.
- **UserOrganizationRepository**: `findByUserId` and `findByUserIdAndOrganizationId` query methods.
- **OrganizationInviteRepository**: `findByToken` query method.
- **User.java**: `organizationId` field and `import java.util.UUID` removed. Existing `organization_id` column in DB is unmapped but not dropped (Hibernate ddl-auto: update does not drop columns — intentional residual risk documented in threat model).
- **UserDetailsImpl**: `build(User, UUID activeOrganizationId)` replaces single-arg `build(User)`. Added `setOrganizationId(UUID)` setter.
- **UserDetailsServiceImpl**: calls `UserDetailsImpl.build(user, null)` — org ID resolved per-request from JWT.
- **AuthTokenFilter**: extracts `organizationId` claim from JWT via `jwtUtils.getOrganizationIdFromJwtToken(jwt)` and sets it on the principal before populating SecurityContext.
- **JwtUtils**: added `getOrganizationIdFromJwtToken`, `generateJwtTokenForOrg`, and `generateJwtTokenForUser` methods.
- **OrganizationController.createOrganization**: replaced `user.setOrganizationId(orgId)` with `new UserOrganization(user, org, ERole.ROLE_OWNER)` + save.

### Task 2 — New API endpoints

All endpoints in `OrganizationController`:

- **`POST /api/organizations/{id}/invites`** — OWNER/ACCOUNTANT only; verifies caller membership; creates OrganizationInvite with UUID token; returns `{token, expiresAt, role, organizationId}`.
- **`GET /api/organizations/me/list`** — authenticated; returns list of `{organizationId, organizationName, role, isActive}` for the calling user.
- **`POST /api/organizations/{id}/select`** — authenticated; validates membership (403 if absent); issues new JWT via `generateJwtTokenForUser`; returns `{token, organizationId, organizationName, role}`.

In `AuthController`:

- **`GET /api/auth/invite/{token}`** — public; pre-validates invite token before signup form renders; returns `{organizationName, role}` or 400 with error message.
- **`POST /api/auth/signup?invite={token}`** — extended to accept optional `invite` query param; validates and consumes the token on successful signup; creates UserOrganization membership; marks `usedAt = Instant.now()`.

### Task 3 — Frontend components

- **`authStore.ts`**: extended with `OrgMembership` interface, `organizations: OrgMembership[]` state, `setOrganizations`, and `switchOrganization` action (updates token, sets isActive flags, updates user.organizationId).
- **`InviteSignupBanner`**: renders `role="status"` (success with orgName + role) or `role="alert"` (error) above the signup form when `?invite` param is present.
- **`InviteTokenDisplay`**: shows generated token in read-only mono input with Copy button (Lucide Copy/CheckCircle, 14px); shows expiry date and privacy warning.
- **`OrganizationSelector`**: renders only when `organizations.length >= 2`; `role="listbox"` dropdown; per-row Switch button with `aria-label="Switch to {name}"`; loading state (Switching…) and error state per row.
- **`SignupPage`**: reads `?invite` param; calls `GET /auth/invite/{token}` on mount; renders InviteSignupBanner; disables form on invalid invite; posts with `?invite=` param; redirects to `/dashboard` (not `/login`) on invite signup success.

## Deviations from Plan

### Auto-fixed Issues

None — plan executed exactly as written with one minor scope adjustment:

**OrganizationController refactor scope**: The plan split OrganizationController changes across Task 1 (replace setOrganizationId) and Task 2 (add endpoints). Both were implemented in a single atomic commit since they touch the same file and compile as a unit. No behavioral difference.

**getOrganization guard updated**: The plan noted the `getOrganization` endpoint had a "Temporary org-scoping guard (Plan 5 replaces with UserOrganization membership check)" comment. Replaced with proper `userOrganizationRepository.findByUserIdAndOrganizationId` check as intended.

**OrganizationSelector `user` variable**: The plan included `user` destructuring from authStore in OrganizationSelector but the rendered JSX did not use it directly. Added `void user` to suppress TypeScript unused variable warning rather than removing the destructure (keeping it for future display use as the plan intended).

## Known Stubs

None — all data paths are wired. OrganizationSelector fetches live from `/api/organizations/{id}/select`. InviteSignupBanner renders real invite context from `/api/auth/invite/{token}`.

## Threat Flags

No new threat surface beyond what was documented in the plan's threat model.

## Build Verification

- Backend: `./mvnw compile -q` exits 0 (with JWT_SECRET set)
- Frontend: `tsc --noEmit --skipLibCheck` exits 0 (pre-existing `vite.config.ts` type error from vitest/vite version conflict is out of scope for this plan)

## Self-Check: PASSED
