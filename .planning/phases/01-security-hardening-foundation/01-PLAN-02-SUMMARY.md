---
phase: 1
plan: 2
subsystem: backend-security
tags: [role-guards, preauthorize, spring-security, authorization]
dependency_graph:
  requires: [01-PLAN-01]
  provides: [method-level-authorization, locked-permitall-paths]
  affects: [all-api-controllers]
tech_stack:
  added: []
  patterns: [@PreAuthorize method security, explicit permitAll path list]
key_files:
  created: []
  modified:
    - Service/superaccountant/src/main/java/com/arktech/superaccountant/login/security/WebSecurityConfig.java
    - Service/superaccountant/src/main/java/com/arktech/superaccountant/masters/controllers/OrganizationController.java
    - Service/superaccountant/src/main/java/com/arktech/superaccountant/gst/controllers/GstValidationController.java
    - Service/superaccountant/src/main/java/com/arktech/superaccountant/masters/controllers/UploadController.java
    - Service/superaccountant/src/main/java/com/arktech/superaccountant/masters/controllers/PreconfiguredMastersController.java
    - Service/superaccountant/src/main/java/com/arktech/superaccountant/tally/controllers/TallyImportController.java
decisions:
  - "@PreAuthorize on every non-auth controller method — no implicit 'authenticated = authorized'"
  - "DELETE and onboard restricted to OWNER/ACCOUNTANT only (destructive/setup ops); DATA_ENTRY_OPERATOR excluded"
  - "WebSecurityConfig permitAll consolidated to single requestMatchers block with 4 exact paths"
metrics:
  duration: ~15 minutes
  completed: 2026-04-12
  tasks_completed: 2
  files_modified: 6
---

# Phase 1 Plan 2: Role Guards — @PreAuthorize on All Controllers Summary

**One-liner:** Explicit `@PreAuthorize` role guards added to all 5 non-auth controllers; `WebSecurityConfig` permitAll locked to exact 4-path list per D-15.

## What Was Built

All API endpoints now have explicit method-level authorization via `@PreAuthorize`. The `WebSecurityConfig` filter chain's `permitAll()` block was consolidated into a single `requestMatchers(...)` call listing exactly 4 public paths. Every `@RestController` outside `AuthController` has at least one `@PreAuthorize` annotation on each handler method.

### Controllers Guarded

| Controller | Path | Methods Guarded |
|------------|------|-----------------|
| `OrganizationController` | `/api/organizations` | POST createOrganization — OWNER/ACCOUNTANT |
| `GstValidationController` | `/api/gst` | POST validate — OWNER/ACCOUNTANT/DATA_ENTRY_OPERATOR |
| `UploadController` | `/api/v1` | POST uploads — OWNER/ACCOUNTANT/DATA_ENTRY_OPERATOR; GET uploads/mismatches/export/validation-rules — isAuthenticated(); PATCH resolve — OWNER/ACCOUNTANT/DATA_ENTRY_OPERATOR |
| `PreconfiguredMastersController` | `/api/v1/preconfigured-masters` | GET list/validation-rules — isAuthenticated(); POST create/bulk — OWNER/ACCOUNTANT/DATA_ENTRY_OPERATOR; PUT update — OWNER/ACCOUNTANT/DATA_ENTRY_OPERATOR; DELETE/POST onboard — OWNER/ACCOUNTANT |
| `TallyImportController` | `/api/tally` | POST import — OWNER/ACCOUNTANT/DATA_ENTRY_OPERATOR |

### WebSecurityConfig Change

Replaced three separate `requestMatchers(...).permitAll()` chains with one consolidated block:
```java
.requestMatchers(
    "/api/auth/signin",
    "/api/auth/signup",
    "/api/test/**",
    "/error"
).permitAll()
```

No wildcard `/api/auth/**` — only exact paths per D-15.

## Tasks Completed

| # | Task | Commit | Files |
|---|------|--------|-------|
| 1 | Update WebSecurityConfig + @PreAuthorize on OrganizationController | ed493ed | WebSecurityConfig.java, OrganizationController.java |
| 2 | @PreAuthorize on UploadController, GstValidationController, PreconfiguredMastersController, TallyImportController | 8ca84e6 | 4 controller files |

## Verification Results

```
PASS: "/api/auth/signin" in single requestMatchers block
PASS: no /api/auth/** wildcard in permitAll
PASS: @PreAuthorize on OrganizationController
PASS: Only AuthController returned by grep -rL "@PreAuthorize" *Controller.java
PASS: ./mvnw compile -q exits 0
```

## Deviations from Plan

### Auto-added scope (Rule 2 — Missing critical functionality)

**PreconfiguredMastersController and TallyImportController not in plan's files_modified list**

- **Found during:** Task 2 — discovery of all controllers via `find`
- **Issue:** The plan listed 5 files to modify but only named OrganizationController, UploadController, GstValidationController explicitly. Two additional controllers (`PreconfiguredMastersController`, `TallyImportController`) existed without any `@PreAuthorize` guards, violating D-14 ("all non-auth controllers get explicit @PreAuthorize").
- **Fix:** Added `@PreAuthorize` to all handler methods in both controllers following the same role patterns (write = OWNER/ACCOUNTANT/DATA_ENTRY_OPERATOR, destructive/setup = OWNER/ACCOUNTANT, read = isAuthenticated()).
- **Files modified:** PreconfiguredMastersController.java, TallyImportController.java
- **Commits:** 8ca84e6

**DELETE and onboard in PreconfiguredMastersController restricted to OWNER/ACCOUNTANT only**

- **Found during:** Task 2 — review of operation semantics
- **Issue:** Plan D-11 specifies OWNER/ACCOUNTANT for organization write operations. Applying the same principle: deleting a preconfigured master (soft-delete) and onboarding (one-time setup) are destructive/administrative operations that should not be available to DATA_ENTRY_OPERATOR.
- **Fix:** `@PreAuthorize("hasRole('OWNER') or hasRole('ACCOUNTANT')")` on `delete()` and `onboard()`.
- **Commit:** 8ca84e6

## Known Stubs

None — all controller methods are fully implemented business logic; guards are additive annotations.

## Threat Flags

None — no new network endpoints, auth paths, or trust boundary changes introduced. This plan reduces attack surface by adding method-level authorization; it does not expand it.

## Self-Check: PASSED

- WebSecurityConfig.java modified: FOUND
- OrganizationController.java has @PreAuthorize: FOUND
- GstValidationController.java has @PreAuthorize: FOUND
- UploadController.java has @PreAuthorize: FOUND
- PreconfiguredMastersController.java has @PreAuthorize: FOUND
- TallyImportController.java has @PreAuthorize: FOUND
- Task 1 commit ed493ed: FOUND
- Task 2 commit 8ca84e6: FOUND
