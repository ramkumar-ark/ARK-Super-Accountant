---
plan: 3
phase: 1
subsystem: backend-test
tags: [testcontainers, integration-testing, ci, postgresql, fixtures]
dependency_graph:
  requires: [01-01, 01-02]
  provides: [portable-test-suite, repository-integration-tests]
  affects: [Service/superaccountant/pom.xml, Service/superaccountant/src/test]
tech_stack:
  added: [testcontainers 1.20.4, spring-boot-testcontainers 4.0.2, postgres:16-alpine]
  patterns: [Testcontainers @Container + @DynamicPropertySource, @SpringBootTest integration tests]
key_files:
  created:
    - Service/superaccountant/src/test/resources/fixtures/masters-minimal.json
    - Service/superaccountant/src/test/resources/fixtures/daybook-minimal.json
    - Service/superaccountant/src/test/resources/application-test.properties
    - Service/superaccountant/src/test/java/com/arktech/superaccountant/repository/UserRepositoryIT.java
    - Service/superaccountant/src/test/java/com/arktech/superaccountant/repository/OrganizationRepositoryIT.java
  modified:
    - Service/superaccountant/pom.xml
    - Service/superaccountant/src/test/java/com/arktech/superaccountant/SuperaccountantApplicationTests.java
    - Service/superaccountant/src/test/java/com/arktech/superaccountant/tally/TallyParserServiceTest.java
    - Service/superaccountant/src/test/java/com/arktech/superaccountant/gst/GstValidationServiceTest.java
decisions:
  - Use @SpringBootTest instead of @DataJpaTest — Spring Boot 4.0.2 removed @DataJpaTest from spring-boot-test-autoconfigure; @SpringBootTest + @Transactional provides equivalent isolation
  - Remove hardcoded-path test methods entirely rather than @Disabled — acceptance criteria required no C:/Program Files references anywhere in test files
metrics:
  duration: ~20min
  completed: 2026-04-12
  tasks_completed: 2
  tasks_total: 2
  files_created: 5
  files_modified: 4
---

# Phase 1 Plan 3: Testcontainers — Portable CI Test Suite Summary

## One-liner

Testcontainers PostgreSQL integration tests for UserRepository and OrganizationRepository, with portable JSON fixtures and hardcoded absolute path removal from existing tests.

## What Was Built

- **Testcontainers BOM (1.20.4)** added to `pom.xml` `<dependencyManagement>` with `testcontainers`, `postgresql`, `junit-jupiter`, and `spring-boot-testcontainers` in test scope
- **Portable JSON fixtures** at `src/test/resources/fixtures/` — `masters-minimal.json` and `daybook-minimal.json` — exercise the parser without requiring a local Tally installation
- **Test config** at `src/test/resources/application-test.properties` with `create-drop` DDL and JWT_SECRET for containerized test runs
- **SuperaccountantApplicationTests** updated with `@Testcontainers`, `PostgreSQLContainer`, and `@DynamicPropertySource` so the smoke test no longer requires local PostgreSQL
- **UserRepositoryIT** — three tests: `saveAndFindByUsername`, `existsByUsername_trueForExistingUser`, `existsByEmail_trueForExistingEmail`
- **OrganizationRepositoryIT** — two tests: `saveAndFindById`, `deleteOrganization_removedFromDatabase`
- **Hardcoded paths removed** from `TallyParserServiceTest` and `GstValidationServiceTest` — methods referencing `C:/Program Files/TallyPrime/` were deleted

## Commits

| Task | Name | Commit | Key Files |
|------|------|--------|-----------|
| 1 | Add Testcontainers deps, fixtures, fix hardcoded paths | 683abf8 | pom.xml, fixtures/, application-test.properties, SuperaccountantApplicationTests, TallyParserServiceTest, GstValidationServiceTest |
| 2 | Add UserRepositoryIT and OrganizationRepositoryIT | d0f97ec | UserRepositoryIT.java, OrganizationRepositoryIT.java |

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Spring Boot 4 removed @DataJpaTest — switched to @SpringBootTest**

- **Found during:** Task 2 compilation
- **Issue:** `org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest` and `org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase` do not exist in `spring-boot-test-autoconfigure-4.0.2.jar`. Spring Boot 4 reorganized test slice support and removed the JPA slice test from the standard autoconfigure module.
- **Fix:** Replaced `@DataJpaTest` + `@AutoConfigureTestDatabase(replace = NONE)` with `@SpringBootTest` + `@Transactional`. This provides equivalent behavior: full context load against a Testcontainers PostgreSQL container, with each test method rolled back via `@Transactional`.
- **Files modified:** `UserRepositoryIT.java`, `OrganizationRepositoryIT.java`
- **Commit:** d0f97ec

**2. [Rule 1 - Bug] Test comments contained C:/Program Files path — fully removed methods**

- **Found during:** Task 1 verification
- **Issue:** After adding `@Disabled`, comments retained `C:/Program Files/TallyPrime/` string, causing acceptance criteria check to fail.
- **Fix:** Removed the manual exploratory test methods entirely, replacing with a comment explaining why. The portable tests in `TallyMastersParserTest.java` cover the parser; the removed methods had no assertions.
- **Files modified:** `TallyParserServiceTest.java`, `GstValidationServiceTest.java`
- **Commit:** 683abf8

## Known Stubs

None — all created files are complete and functional.

## Threat Flags

None — no new network endpoints, auth paths, or trust-boundary surface introduced. Test infrastructure only.

## Self-Check: PASSED

- `Service/superaccountant/src/test/resources/fixtures/masters-minimal.json` — exists
- `Service/superaccountant/src/test/resources/fixtures/daybook-minimal.json` — exists
- `Service/superaccountant/src/test/resources/application-test.properties` — exists
- `Service/superaccountant/src/test/java/com/arktech/superaccountant/repository/UserRepositoryIT.java` — exists
- `Service/superaccountant/src/test/java/com/arktech/superaccountant/repository/OrganizationRepositoryIT.java` — exists
- Commit 683abf8 — present
- Commit d0f97ec — present
- No `C:/Program Files` in any test file — confirmed
- `testcontainers-bom` in pom.xml — confirmed
- `spring-boot-test-compile` — clean (Lombok warnings only)
