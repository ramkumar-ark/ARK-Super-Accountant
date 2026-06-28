---
phase: 1
plan: 4
subsystem: org-entity
tags: [organization, gstin, pan, validation, frontend, spring-boot]
dependency_graph:
  requires: [01-PLAN-02-role-guards]
  provides: [org-entity-extension, org-setup-ui]
  affects: [Organization entity, OrganizationController, OrganizationSetupPage]
tech_stack:
  added: []
  patterns: [Jakarta @Pattern validation, @Min/@Max constraints, TanStack Router route guard, blur-time field validation]
key_files:
  created:
    - Client/src/pages/OrganizationSetupPage.tsx
  modified:
    - Service/superaccountant/src/main/java/com/arktech/superaccountant/masters/models/Organization.java
    - Service/superaccountant/src/main/java/com/arktech/superaccountant/masters/payload/request/CreateOrganizationRequest.java
    - Service/superaccountant/src/main/java/com/arktech/superaccountant/masters/controllers/OrganizationController.java
    - Client/src/main.tsx
decisions:
  - "@Pattern allows null GSTIN/PAN — nullable at org creation per D-16; validation only fires on non-null values"
  - "GET /api/organizations/{id} uses principal.getOrganizationId() for org-scoping guard; Plan 5 will replace with UserOrganization membership check"
  - "window.alert used as success toast placeholder — no toast library in package.json"
metrics:
  duration: "~25 minutes"
  completed: "2026-04-12"
  tasks_completed: 2
  files_changed: 5
---

# Phase 1 Plan 4: Organization Entity Extension + Setup UI Summary

## One-liner

Extended Organization entity with GSTIN/PAN/address/financialYearStart columns, added Jakarta @Pattern validation on the DTO, exposed GET /api/organizations/{id} with an org-scoping guard, and built the OrganizationSetupPage React form with blur-time regex validation.

## What Was Built

### Task 1 — Backend entity, DTO validation, GET endpoint

- **Organization.java**: Added `gstin` (VARCHAR 15, nullable), `pan` (VARCHAR 10, nullable), `registeredAddress` (TEXT, nullable), `financialYearStart` (INT, NOT NULL, default 4). Hibernate `ddl-auto: update` will add these columns on next startup.
- **CreateOrganizationRequest.java**: Added `@Pattern` for GSTIN (`[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z]{1}[1-9A-Z]{1}Z[0-9A-Z]{1}`), `@Pattern` for PAN (`[A-Z]{5}[0-9]{4}[A-Z]{1}`), `@Min(1)/@Max(12)` for `financialYearStart`. New fields are nullable in request — no `@NotNull` on GSTIN/PAN per D-16.
- **OrganizationController.java**: POST updated to map new fields from request to entity and return full org in response. Added `GET /api/organizations/{id}` with `@PreAuthorize("isAuthenticated()")` and org-scoping check (403 if `principal.getOrganizationId()` doesn't match the requested id). `@PreAuthorize` on POST preserved from Plan 2 (`hasRole('OWNER') or hasRole('ACCOUNTANT')`).

### Task 2 — OrganizationSetupPage frontend form

- **OrganizationSetupPage.tsx**: Full-page centered form (`max-w-lg` card). Fields: Organization Name (required), GSTIN (monospace, maxLength 15, blur validation), PAN (monospace, maxLength 10, blur validation), Registered Address (textarea rows 3), Financial Year Start (select, default April/4). GSTIN/PAN show Lucide `CheckCircle` on valid blur, `role="alert"` error message on invalid blur. Submit disabled when name is empty. Calls `api.post('/organizations', form)`, uses `window.alert` as toast placeholder, navigates to `/dashboard` on success.
- **main.tsx**: Added `/organization/setup` route with `beforeLoad` auth guard (redirects to `/login` if not authenticated).

## Commits

| Hash | Message |
|------|---------|
| abd12a1 | feat(01-04): extend Organization entity with GSTIN/PAN/address/fyStart fields |
| b5b60e5 | chore(01-04): restore files accidentally deleted by soft reset |
| 3a224d9 | feat(01-04): add OrganizationSetupPage form and /organization/setup route |

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Soft reset staged deletion of planning/test files**
- **Found during:** Task 1 commit
- **Issue:** `git reset --soft c2b51f0` left planning files (ROADMAP.md, PLAN-01 through PLAN-05, test fixtures, integration tests) as staged deletions because the worktree tree didn't contain them (they existed in main repo `.planning/` which is a separate directory). The Task 1 commit accidentally deleted them.
- **Fix:** Restored all accidentally deleted files from the base commit using `git checkout c2b51f0 -- <files>` and committed as a separate chore commit.
- **Files modified:** 14 planning/test files restored
- **Commit:** b5b60e5

### Pre-existing Issues (Out of Scope)

- `vite.config.ts` has a pre-existing TypeScript error (`test` property not in `UserConfigExport` — requires `@vitest/...` reference types). This error existed before this plan and is not caused by any change here. App-level TypeScript (`tsconfig.app.json`) compiles cleanly.

## Known Stubs

- `window.alert('Organization created successfully')` — placeholder success notification. No toast library is installed (`sonner` not in package.json). A proper toast/notification component should replace this in a future plan.

## Threat Flags

None — all new endpoints and validation are within the threat model defined in the plan.

## Self-Check: PASSED

Files exist:
- `Client/src/pages/OrganizationSetupPage.tsx` — FOUND
- `Service/.../masters/models/Organization.java` has `gstin` — FOUND
- `Service/.../masters/payload/request/CreateOrganizationRequest.java` has `@Pattern` — FOUND
- `Service/.../masters/controllers/OrganizationController.java` has `@GetMapping` — FOUND
- `Client/src/main.tsx` has `OrganizationSetupPage` and `/organization/setup` — FOUND

Commits exist: abd12a1, b5b60e5, 3a224d9 — all in `git log`.
