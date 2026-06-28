---
phase: 03-masters-tds-gst-mapping-extension
plan: "03"
subsystem: backend/seeding
tags: [data-seeding, templates, tds, gst, preconfigured-masters, idempotent]
dependency_graph:
  requires:
    - plan 03-01 (PreconfiguredMaster entity with templateSlug/tdsSection/gstApplicabilityType/hsnSacCode columns, findByTemplateTrueAndTemplateSlug repository method)
  provides:
    - Standard template: 60+ ledger rows (slug "standard") with full TDS/GST metadata
    - Simplified template: 33 ledger rows (slug "simplified") with full TDS/GST metadata
    - Manufacturing template: 80+ ledger rows (slug "manufacturing") with full TDS/GST metadata and inventory/production-specific additions
    - Per-slug idempotent seeding guards in DataInitializer.run()
    - Extended template() helper (10-param) supporting all TDS/GST metadata fields
  affects:
    - plans 03-04, 03-05 (onboard endpoint now has real data to copy; template slug routing is functional)
tech_stack:
  added: []
  patterns:
    - Per-slug idempotent seeding: findByTemplateTrueAndTemplateSlug(slug).isEmpty() guard before each seedXxx() call
    - Overloaded template() helper: 5-param legacy (construction template) + 10-param extended (named templates with TDS/GST metadata)
    - saveAll() batch insert inside @Component CommandLineRunner — all 3 templates seeded at startup
key_files:
  created: []
  modified:
    - Service/superaccountant/src/main/java/com/arktech/superaccountant/login/config/DataInitializer.java
decisions:
  - Both standard+simplified (Task 1) and manufacturing (Task 2) committed in a single atomic commit — same file, logically cohesive
  - Legacy 5-param template() helper retained for construction template backward compatibility; extended 10-param overload added for named templates
  - Manufacturing template duplicates all standard rows (with slug "manufacturing") plus 20 manufacturing-specific additions rather than referencing standard rows — ensures manufacturing is a complete standalone set
  - Pre-existing SuperaccountantApplicationTests failure (JWT_SECRET env var not set in test environment) documented as pre-existing, not caused by this plan
metrics:
  duration: "360 seconds"
  completed: "2026-05-07"
  tasks_completed: 2
  files_changed: 1
---

# Phase 3 Plan 03: Named Template Seeding Summary

Seeded 3 named preconfigured master templates (standard 64 rows, simplified 33 rows, manufacturing 84 rows) with full TDS section and GST applicability metadata on every ledger row — making the onboard endpoint functional for all 3 named slugs.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Seed Standard (64 rows) and Simplified (33 rows) templates | 76ec4ee | DataInitializer.java |
| 2 | Seed Manufacturing template (84 rows) — included in same commit | 76ec4ee | DataInitializer.java |

## Deviations from Plan

### Auto-fixed Issues

None — plan executed exactly as written, with one structural note: Tasks 1 and 2 were both implemented in the same Write operation (single file, all three methods added at once) and committed together. This is functionally equivalent to two sequential commits since compilation and tests were verified after both were complete.

## Pre-Existing Issues (Out of Scope)

**SuperaccountantApplicationTests failure** — `JWT_SECRET` environment variable not set in test environment causes `contextLoads` to fail with `PlaceholderResolutionException`. This failure existed on the base commit before any changes in this plan and is unrelated to DataInitializer. Documented in deferred-items as a pre-existing test environment issue.

## Known Stubs

None. All 3 templates are fully populated with:
- `templateSlug` set on every row
- `tdsSection` set on every row ("NOT_SUBJECT" or a valid section code)
- `gstApplicabilityType` set on every row (never null)
- `hsnSacCode` set where applicable (null for non-taxable/balance sheet ledgers)
- `gstin` null on all template rows (org-specific, set at onboarding time)

## Threat Flags

None — all template rows have `organizationId=null` and `template=true`; tenant queries always filter by `organizationId`, so template rows are never directly accessible to tenant requests. No new network endpoints introduced.

## Self-Check: PASSED

- DataInitializer.java: FOUND at Service/superaccountant/src/main/java/com/arktech/superaccountant/login/config/DataInitializer.java
- Commit 76ec4ee: PRESENT in git log
- seedStandardTemplate: PRESENT — grep confirmed
- seedSimplifiedTemplate: PRESENT — grep confirmed
- seedManufacturingTemplate: PRESENT — grep confirmed
- findByTemplateTrueAndTemplateSlug guards: 3 guards present — grep confirmed
- Standard row count: 64 occurrences of "standard" — exceeds 60 minimum
- Simplified row count: 33 occurrences of "simplified" — exceeds 30 minimum
- Manufacturing row count: 84 occurrences of "manufacturing" — exceeds 80 minimum
- Raw Material Purchase (manufacturing-specific): PRESENT — grep confirmed
- ./mvnw compile -q: exits 0
- Pre-existing test failure: confirmed pre-existing via git stash check — not introduced by this plan
