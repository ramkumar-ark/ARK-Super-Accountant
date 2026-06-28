---
phase: 03-masters-tds-gst-mapping-extension
plan: 06
subsystem: database
tags: [java, spring-boot, hibernate, jpql, data-migration, startup-runner]

# Dependency graph
requires:
  - phase: 03-masters-tds-gst-mapping-extension
    provides: ValidationFinding entity with FindingSeverity enum (INFO/WARNING/ERROR/HIGH/MEDIUM/LOW)
provides:
  - DataInitializer.backfillFindingSeverities() with JPQL bulk UPDATE + @PersistenceContext EntityManager injection
  - Idempotent startup migration: INFO->LOW, WARNING->MEDIUM, ERROR->HIGH for validation_findings rows
affects:
  - Phase 5 (TDS reports) — findings display relies on HIGH/MEDIUM/LOW severity values
  - Phase 6 (GST validation) — same findings table and severity filter

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "@PersistenceContext EntityManager injection in @Component for JPQL bulk updates"
    - "Package-private @Transactional method on CommandLineRunner for idempotent startup migration"

key-files:
  created: []
  modified:
    - Service/superaccountant/src/main/java/com/arktech/superaccountant/login/config/DataInitializer.java

key-decisions:
  - "backfillFindingSeverities() is package-private (not private) so Spring CGLIB proxy can apply @Transactional — private methods cannot be intercepted by Spring AOP"
  - "Method called last in run() — all seeding happens before the backfill so the DB is in a consistent state"

patterns-established:
  - "Idempotent startup migration: JPQL UPDATE with old-value WHERE clause affects 0 rows on subsequent startups"

requirements-completed: [MSTR-02]

# Metrics
duration: 8min
completed: 2026-05-07
---

# Phase 03 Plan 06: DataInitializer Finding Severity Backfill Summary

**Idempotent startup JPQL backfill in DataInitializer migrates INFO/WARNING/ERROR severity values to LOW/MEDIUM/HIGH so prior findings remain visible in the Findings UI after Phase 3 deployment**

## Performance

- **Duration:** ~8 min
- **Started:** 2026-05-07T00:00:00Z
- **Completed:** 2026-05-07T00:08:00Z
- **Tasks:** 1
- **Files modified:** 1

## Accomplishments

- Added `@PersistenceContext EntityManager` field to `DataInitializer` for JPQL bulk update capability
- Implemented `backfillFindingSeverities()` method with three JPQL UPDATE statements (INFO→LOW, WARNING→MEDIUM, ERROR→HIGH)
- Wired the call as the last statement in `run()` — executes after all seed operations, idempotent on repeat startups
- Backend compiles cleanly; all acceptance criteria verified

## Task Commits

Each task was committed atomically:

1. **Task 1: Add EntityManager injection and backfillFindingSeverities() to DataInitializer** - `316af6e` (feat)

**Plan metadata:** _(committed with SUMMARY below)_

## Files Created/Modified

- `Service/superaccountant/src/main/java/com/arktech/superaccountant/login/config/DataInitializer.java` — Added EntityManager injection, backfillFindingSeverities() method, and call from run()

## Decisions Made

- `backfillFindingSeverities()` is package-private (not private) so Spring CGLIB can proxy the `@Transactional` annotation — private methods bypass Spring AOP interception
- Method placed last in `run()` to ensure all role/rule/template seeding completes before the migration runs

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None.

## User Setup Required

None - no external service configuration required. The backfill runs automatically on next application startup.

## Known Stubs

None. The method operates directly on the database at startup — no UI or stub values introduced.

## Threat Flags

None. The backfill uses fixed string literals and no user input (T-03-GAP1 accepted per plan threat model).

## Self-Check

- [x] `DataInitializer.java` modified and committed at `316af6e`
- [x] `./mvnw compile -q` exits 0
- [x] `@PersistenceContext` annotation present (count: 1)
- [x] `EntityManager entityManager` field present (count: 1)
- [x] `backfillFindingSeverities` appears twice (method definition + call from run())
- [x] All three JPQL UPDATE statements present (INFO→LOW, WARNING→MEDIUM, ERROR→HIGH)
- [x] `import jakarta.persistence.EntityManager` exactly once (no duplication)

## Self-Check: PASSED

## Next Phase Readiness

- Gap 1 from the Phase 3 verification report is closed
- Any organization with pre-Phase-3 findings (stored as INFO/WARNING/ERROR) will have them migrated to LOW/MEDIUM/HIGH on next startup — Findings UI filters will work correctly
- Gap 2 (from verification report) is addressed in plan 03-07

---
*Phase: 03-masters-tds-gst-mapping-extension*
*Completed: 2026-05-07*
