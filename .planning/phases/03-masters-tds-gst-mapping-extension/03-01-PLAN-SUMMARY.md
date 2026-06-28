---
phase: 03-masters-tds-gst-mapping-extension
plan: "01"
subsystem: backend/masters
tags: [data-model, enums, jpa, validation, repository, controller, seeding]
dependency_graph:
  requires: []
  provides:
    - GstApplicabilityType enum (6 values)
    - FindingSeverity extended (HIGH/MEDIUM/LOW added)
    - ResolveStatus extended (ACCEPTED/OVERRIDDEN added)
    - PreconfiguredMaster 5 new nullable columns
    - ParsedLedger 4 new fields
    - CreatePreconfiguredMasterRequest/UpdatePreconfiguredMasterRequest 4 new fields + @Pattern validation
    - OnboardRequest templateSlug field
    - PreconfiguredMasterResponse 4 new fields
    - PreconfiguredMasterRepository.findByTemplateTrueAndTemplateSlug
    - ValidationFindingRepository JPQL positive whitelist (OPEN/ACKNOWLEDGED)
    - ValidationRuleConfigRepository.existsByRuleCode
    - PreconfiguredMastersController.update() 4 new null-checks
    - PreconfiguredMastersController.onboard() templateSlug routing branch
    - DataInitializer idempotent seedValidationRuleIfAbsent() for 5 rules
    - GET /api/v1/uploads/latest/mismatches org-scoped endpoint
  affects:
    - plans 02, 03, 04, 05 (all depend on this model layer)
tech_stack:
  added: []
  patterns:
    - Positive JPQL whitelist instead of negative exclusion for resolved status filtering
    - Idempotent per-rule seeding via existsByRuleCode() guard
    - Nullable JPA enum columns via @Enumerated(EnumType.STRING) with ddl-auto:update
    - templateSlug-based named template routing in onboard endpoint
key_files:
  created:
    - Service/superaccountant/src/main/java/com/arktech/superaccountant/masters/models/GstApplicabilityType.java
  modified:
    - Service/superaccountant/src/main/java/com/arktech/superaccountant/masters/models/FindingSeverity.java
    - Service/superaccountant/src/main/java/com/arktech/superaccountant/masters/models/ResolveStatus.java
    - Service/superaccountant/src/main/java/com/arktech/superaccountant/masters/models/PreconfiguredMaster.java
    - Service/superaccountant/src/main/java/com/arktech/superaccountant/masters/classifier/ParsedLedger.java
    - Service/superaccountant/src/main/java/com/arktech/superaccountant/masters/payload/request/CreatePreconfiguredMasterRequest.java
    - Service/superaccountant/src/main/java/com/arktech/superaccountant/masters/payload/request/UpdatePreconfiguredMasterRequest.java
    - Service/superaccountant/src/main/java/com/arktech/superaccountant/masters/payload/request/OnboardRequest.java
    - Service/superaccountant/src/main/java/com/arktech/superaccountant/masters/payload/response/PreconfiguredMasterResponse.java
    - Service/superaccountant/src/main/java/com/arktech/superaccountant/masters/repository/PreconfiguredMasterRepository.java
    - Service/superaccountant/src/main/java/com/arktech/superaccountant/masters/repository/ValidationFindingRepository.java
    - Service/superaccountant/src/main/java/com/arktech/superaccountant/masters/repository/ValidationRuleConfigRepository.java
    - Service/superaccountant/src/main/java/com/arktech/superaccountant/masters/controllers/PreconfiguredMastersController.java
    - Service/superaccountant/src/main/java/com/arktech/superaccountant/masters/controllers/UploadController.java
    - Service/superaccountant/src/main/java/com/arktech/superaccountant/login/config/DataInitializer.java
decisions:
  - tdsSection stored as String (not enum) on PreconfiguredMaster to avoid Java enum digit-prefix naming constraint; validated via @Pattern in DTO layer
  - gstApplicabilityType stored as GstApplicabilityType enum with @Enumerated(EnumType.STRING); serialized as String name in response
  - ValidationFindingRepository uses positive OPEN/ACKNOWLEDGED whitelist rather than negative RESOLVED exclusion — safer and handles ACCEPTED/OVERRIDDEN automatically
  - DataInitializer uses existsByRuleCode() per-rule guard instead of count()==0 so adding new rules to existing DB never fails
  - GET /api/v1/uploads/latest/mismatches returns Page.empty() when no completed job exists (not 404) to simplify frontend handling
metrics:
  duration: "275 seconds"
  completed: "2026-05-06"
  tasks_completed: 3
  files_changed: 15
---

# Phase 3 Plan 01: Masters Data Model Extension Summary

Extended the backend data model with 4 new TDS/GST metadata columns on `preconfigured_masters`, created the `GstApplicabilityType` enum (6 values), extended `FindingSeverity` and `ResolveStatus` enums, fixed the JPQL positive whitelist, replaced the broken DataInitializer count guard with idempotent per-rule seeding, and added the org-scoped latest-job findings endpoint.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | New GstApplicabilityType enum + extend FindingSeverity and ResolveStatus | 4abf7b8 | GstApplicabilityType.java (new), FindingSeverity.java, ResolveStatus.java |
| 2 | Extend PreconfiguredMaster entity + DTOs + repository + JPQL fix + controller + DataInitializer | 1be363e | 11 files modified |
| 3 | Add org-scoped latest-job findings endpoint | fb6a694 | UploadController.java, UploadJobRepository.java |

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing Critical Functionality] Added @Pattern validation for tdsSection (T-03-01)**
- **Found during:** Post-task threat surface scan against plan's threat model
- **Issue:** Plan's `<interfaces>` section specified @Pattern only for hsnSacCode and gstin, but the threat model (T-03-01) explicitly required tdsSection to be validated against an allowlist to prevent arbitrary string injection
- **Fix:** Added `@Pattern(regexp = "NOT_SUBJECT|194C|194J_A|194J_B|194H|194I|194Q|194A|194B|194D|194M|OTHER")` to tdsSection in both CreatePreconfiguredMasterRequest and UpdatePreconfiguredMasterRequest; added `@Valid` to `update()` @RequestBody in PreconfiguredMastersController
- **Files modified:** CreatePreconfiguredMasterRequest.java, UpdatePreconfiguredMasterRequest.java, PreconfiguredMastersController.java
- **Commit:** 6b4c71b

## Known Stubs

None. All fields are wired to persistence. The `ParsedLedger` new fields (`tdsSection`, `gstApplicabilityType`, `hsnSacCode`, `gstin`) will always be null from Tally JSON uploads until `TallyParserService.parseMastersJson()` is extended to parse those fields — this is a known architectural constraint documented in RESEARCH.md (Tally JSON does not currently export these fields). The fields are present in the model and ready for future use.

## Threat Flags

None — all threats in the plan's threat model have been mitigated as part of this plan's implementation.

## Self-Check: PASSED

- GstApplicabilityType.java: FOUND at Service/superaccountant/src/main/java/com/arktech/superaccountant/masters/models/GstApplicabilityType.java
- Commits: 4abf7b8, 1be363e, fb6a694, 6b4c71b all present in git log
- ./mvnw compile -q exits 0
- existsByRuleCode present in DataInitializer and ValidationRuleConfigRepository
- count()==0 guard NOT present in DataInitializer
- ACKNOWLEDGED present in ValidationFindingRepository JPQL positive whitelist
- GET /uploads/latest/mismatches present in UploadController with @PreAuthorize
- tdsSection @Pattern present in both request DTOs
