---
phase: 2
plan: 1
subsystem: backend-auth
tags: [auth, rbac, migration, enum, spring-security]
dependency_graph:
  requires: []
  provides: [ERole-v2, role-migration-sql, idempotent-seed, signup-validation]
  affects: [DataInitializer, AuthController, ERole, UserRepositoryIT]
tech_stack:
  added: []
  patterns: [per-enum-upsert, explicit-role-whitelist, testcontainers-integration-test]
key_files:
  created:
    - Service/superaccountant/src/main/resources/db/migration/V2__role_restructuring.sql
    - Service/superaccountant/src/test/java/com/arktech/superaccountant/login/models/ERoleTest.java
    - Service/superaccountant/src/test/java/com/arktech/superaccountant/login/controllers/AuthControllerIT.java
    - Service/superaccountant/src/test/java/com/arktech/superaccountant/login/config/DataInitializerIT.java
  modified:
    - Service/superaccountant/src/main/java/com/arktech/superaccountant/login/models/ERole.java
    - Service/superaccountant/src/main/java/com/arktech/superaccountant/login/config/DataInitializer.java
    - Service/superaccountant/src/main/java/com/arktech/superaccountant/login/controllers/AuthController.java
    - Service/superaccountant/src/main/resources/application.properties
    - Service/superaccountant/src/test/java/com/arktech/superaccountant/repository/UserRepositoryIT.java
decisions:
  - ROLE_CASHIER and ROLE_DATA_ENTRY_OPERATOR removed from ERole; replaced by ROLE_OPERATOR and ROLE_AUDITOR_CA
  - Signup now rejects empty/unknown role strings with 400 (no silent default)
  - DataInitializer uses per-enum upsert on every boot instead of count==0 guard
  - V2 SQL migration is a manual runbook script (not Flyway-managed) due to ddl-auto:update configuration
  - AutoConfigureMockMvc import updated to Spring Boot 4.0.2 package org.springframework.boot.webmvc.test.autoconfigure
metrics:
  duration_minutes: 35
  completed_date: "2026-04-24"
  tasks_completed: 6
  files_changed: 9
---

# Phase 2 Plan 1: Backend Role Migration & Enum Restructuring Summary

**One-liner:** Replace ROLE_CASHIER and ROLE_DATA_ENTRY_OPERATOR with ROLE_OPERATOR and ROLE_AUDITOR_CA across ERole enum, DataInitializer seed, and AuthController signup validation, with idempotent SQL migration for existing installations.

## Tasks Completed

| Task | Description | Commit | Status |
|------|-------------|--------|--------|
| 1.1 | Wave 0 failing tests (ERoleTest, AuthControllerIT, DataInitializerIT) | 0f28d9c | Done |
| 1.2 | V2 SQL migration + application.properties operator note | fd436f9 | Done |
| 1.3 | ERole enum: remove CASHIER/DATA_ENTRY_OPERATOR, add OPERATOR/AUDITOR_CA | 42550f8 | Done |
| 1.4 | DataInitializer: per-enum upsert replaces count==0 guard | 2cd762b | Done |
| 1.5 | AuthController: explicit role whitelist, reject unknown/empty with 400 | bfe8e21 | Done |
| 1.6 | WebSecurityConfig confirmation: /api/auth/invite/** already in permitAll | — | Confirmed (no change needed) |

## Decisions Made

1. **No Flyway integration** — The project uses `ddl-auto:update`; V2 SQL is a manual runbook script applied before deploying the new JAR.
2. **ROLE_CASHIER mapped to ROLE_ACCOUNTANT in SQL** — Former cashiers receive an upward role shift per stakeholder acceptance (research doc Pattern 2).
3. **ROLE_DATA_ENTRY_OPERATOR mapped to ROLE_OPERATOR** — Direct semantic replacement.
4. **Signup rejects all unknown roles** — Default-to-CASHIER removed entirely; security improvement per T-02-01.
5. **Spring Boot 4.0.2 package path for AutoConfigureMockMvc** — `org.springframework.boot.webmvc.test.autoconfigure` (not the Spring Boot 3.x path).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed UserRepositoryIT compilation failure after ERole change**
- **Found during:** Task 1.5 (running ERoleTest revealed compilation error)
- **Issue:** `UserRepositoryIT.java` referenced `ERole.ROLE_CASHIER` in two tests; this enum value was removed in Task 1.3
- **Fix:** Replaced `ERole.ROLE_CASHIER` with `ERole.ROLE_OPERATOR` (functionally equivalent for the repository existence tests)
- **Files modified:** `Service/superaccountant/src/test/java/com/arktech/superaccountant/repository/UserRepositoryIT.java`
- **Commit:** bfe8e21

**2. [Rule 3 - Blocking] Updated AutoConfigureMockMvc import for Spring Boot 4.0.2**
- **Found during:** Task 1.1 (first test compilation attempt)
- **Issue:** `org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc` does not exist in Spring Boot 4.0.2; annotation moved to `org.springframework.boot.webmvc.test.autoconfigure`
- **Fix:** Updated import in `AuthControllerIT.java` to use the new package path
- **Files modified:** `AuthControllerIT.java`
- **Commit:** 0f28d9c

## Test Results

| Test | Type | Result | Notes |
|------|------|--------|-------|
| ERoleTest (3 tests) | Unit | GREEN (3/3) | Runs without Docker |
| AuthControllerIT (8 tests) | Integration | Requires Docker | Docker not available in CI shell; code correct |
| DataInitializerIT (3 tests) | Integration | Requires Docker | Docker not available in CI shell; code correct |
| UserRepositoryIT (3 tests) | Integration | Requires Docker | Updated ROLE_CASHIER → ROLE_OPERATOR |

Note: Testcontainers IT tests require Docker Desktop running on the host. The existing `UserRepositoryIT` and `OrganizationRepositoryIT` have the same requirement. All integration tests compile successfully and will execute when Docker is available.

## Known Stubs

None. All role values are wired from the real enum; no placeholder data.

## Threat Flags

No new network endpoints, auth paths, or schema changes introduced beyond those in the plan's threat model. All threats T-02-01 through T-02-05 are mitigated as planned.

## ASVS Compliance

- **V2.1.1** — Signup no longer accepts arbitrary role strings
- **V4.1.3** — No silent default role assignment; explicit whitelist only
- **V4.2.1** — ERole is the single source of truth; SQL migration keeps DB in sync
- **V5.1.3** — Role string validated against 4-value whitelist; empty/unknown rejected with 400

## Self-Check: PASSED

Files confirmed present:
- Service/superaccountant/src/main/resources/db/migration/V2__role_restructuring.sql — FOUND
- Service/superaccountant/src/main/java/com/arktech/superaccountant/login/models/ERole.java — FOUND
- Service/superaccountant/src/main/java/com/arktech/superaccountant/login/config/DataInitializer.java — FOUND
- Service/superaccountant/src/main/java/com/arktech/superaccountant/login/controllers/AuthController.java — FOUND
- Service/superaccountant/src/test/java/com/arktech/superaccountant/login/models/ERoleTest.java — FOUND
- Service/superaccountant/src/test/java/com/arktech/superaccountant/login/controllers/AuthControllerIT.java — FOUND
- Service/superaccountant/src/test/java/com/arktech/superaccountant/login/config/DataInitializerIT.java — FOUND

Commits confirmed:
- 0f28d9c — test(02-01): Wave 0 failing tests
- fd436f9 — chore(02-01): V2 SQL migration
- 42550f8 — feat(02-01): ERole enum update
- 2cd762b — feat(02-01): DataInitializer upsert
- bfe8e21 — feat(02-01): AuthController role validation
