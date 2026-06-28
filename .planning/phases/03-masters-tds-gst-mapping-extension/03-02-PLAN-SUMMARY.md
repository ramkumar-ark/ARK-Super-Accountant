---
phase: 03-masters-tds-gst-mapping-extension
plan: "02"
subsystem: backend/masters/rules
tags: [tdd, validation-rules, tds, gst, severity-refactor, spring-component]
dependency_graph:
  requires:
    - plan 03-01 (GstApplicabilityType enum, FindingSeverity HIGH/MEDIUM/LOW, PreconfiguredMaster fields)
  provides:
    - TdsSectionMappingRule @Component("TDS_SECTION_MAPPING")
    - GstApplicabilityRule @Component("GST_APPLICABILITY")
    - HsnSacCodeRule @Component("HSN_SAC_CODE")
    - GstinPresenceRule @Component("GSTIN_PRESENCE")
    - MismatchDetectionRule updated to HIGH/MEDIUM/LOW severity (no more INFO/WARNING/ERROR)
    - 50 new unit tests across 4 test classes (RED-GREEN discipline verified)
  affects:
    - ValidationOrchestrator (auto-discovers all 5 rule beans via Spring List<ValidationRule> injection)
    - plans 03-03, 03-04, 03-05 (depend on rule correctness for integration and frontend)
tech_stack:
  added: []
  patterns:
    - TDD RED-GREEN-REFACTOR with separate commits per phase
    - context.preconfiguredMasters() iteration (not parsedLedgers) for metadata completeness rules
    - switch expression for category-based severity assignment
    - @Component bean name = ruleCode string for ValidationOrchestrator auto-discovery
key_files:
  created:
    - Service/superaccountant/src/main/java/com/arktech/superaccountant/masters/rules/TdsSectionMappingRule.java
    - Service/superaccountant/src/main/java/com/arktech/superaccountant/masters/rules/GstApplicabilityRule.java
    - Service/superaccountant/src/main/java/com/arktech/superaccountant/masters/rules/HsnSacCodeRule.java
    - Service/superaccountant/src/main/java/com/arktech/superaccountant/masters/rules/GstinPresenceRule.java
    - Service/superaccountant/src/test/java/com/arktech/superaccountant/masters/rules/TdsSectionMappingRuleTest.java
    - Service/superaccountant/src/test/java/com/arktech/superaccountant/masters/rules/GstApplicabilityRuleTest.java
    - Service/superaccountant/src/test/java/com/arktech/superaccountant/masters/rules/HsnSacCodeRuleTest.java
    - Service/superaccountant/src/test/java/com/arktech/superaccountant/masters/rules/GstinPresenceRuleTest.java
  modified:
    - Service/superaccountant/src/main/java/com/arktech/superaccountant/masters/rules/MismatchDetectionRule.java
    - Service/superaccountant/src/test/java/com/arktech/superaccountant/masters/rules/MismatchDetectionRuleTest.java
decisions:
  - All 4 new rules iterate context.preconfiguredMasters() not parsedLedgers; Tally JSON does not export TDS section, GST applicability type, HSN/SAC, or GSTIN per ledger
  - HsnSacCodeRule emits findings only for INCOME and GST categories; PURCHASE/EXPENSE are not required to carry HSN/SAC codes
  - GstinPresenceRule emits HIGH findings for PURCHASE category only; all other categories are excluded
  - MismatchDetectionRule severity updated simultaneously with new rule addition to avoid dual-severity-scheme in UI filter
metrics:
  duration: "280 seconds"
  completed: "2026-05-07"
  tasks_completed: 3
  files_changed: 10
---

# Phase 3 Plan 02: Validation Rule Implementations Summary

Four new `@Component` ValidationRule classes implemented with strict TDD RED-GREEN-REFACTOR discipline: `TdsSectionMappingRule`, `GstApplicabilityRule`, `HsnSacCodeRule`, `GstinPresenceRule`. All use HIGH/MEDIUM/LOW severity aligned with the UI spec. `MismatchDetectionRule` refactored from INFO/WARNING/ERROR to LOW/MEDIUM/HIGH in the same wave to eliminate dual-severity-scheme in the findings filter.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| RED | Add failing unit tests for 4 new validation rules | abc407a | 4 test files created (50 tests, all fail to compile — classes absent) |
| GREEN | Implement TdsSectionMappingRule, GstApplicabilityRule, HsnSacCodeRule, GstinPresenceRule | e1b530a | 4 rule implementation files created; 50 tests pass |
| REFACTOR | Update MismatchDetectionRule severity to HIGH/MEDIUM/LOW | 6fdacbe | MismatchDetectionRule.java + MismatchDetectionRuleTest.java updated; 64 total rule tests pass |

## TDD Gate Compliance

- RED gate commit present: `abc407a` — `test(03-02): add failing unit tests for 4 new validation rules`
- GREEN gate commit present: `e1b530a` — `feat(03-02): implement TdsSectionMappingRule, GstApplicabilityRule, HsnSacCodeRule, GstinPresenceRule`
- REFACTOR gate commit present: `6fdacbe` — `refactor(03-02): update MismatchDetectionRule severity to HIGH/MEDIUM/LOW`

RED phase confirmed: test-compile failed with 4 "cannot find symbol" errors (one per missing rule class). GREEN phase confirmed: 50 tests pass. REFACTOR phase confirmed: 64 total rule tests pass, full suite 96 tests pass.

## Rule Severity Matrix

| Rule | Category | Severity |
|------|----------|----------|
| TDS_SECTION_MAPPING | TDS | HIGH |
| TDS_SECTION_MAPPING | PURCHASE / EXPENSE / INCOME | MEDIUM |
| TDS_SECTION_MAPPING | GST / OTHER | LOW |
| GST_APPLICABILITY | INCOME / GST | MEDIUM |
| GST_APPLICABILITY | PURCHASE / EXPENSE / TDS / OTHER | LOW |
| HSN_SAC_CODE | INCOME / GST | MEDIUM |
| HSN_SAC_CODE | PURCHASE / EXPENSE / TDS / OTHER | no finding |
| GSTIN_PRESENCE | PURCHASE | HIGH |
| GSTIN_PRESENCE | EXPENSE / INCOME / GST / TDS / OTHER | no finding |

## Deviations from Plan

None — plan executed exactly as written.

## Known Stubs

None. The 4 new rule classes emit real findings from PreconfiguredMaster data. No hardcoded empty values or placeholder messages flow to UI rendering. All finding fields (ruleCode, ledgerName, category, severity, resolveStatus, message, suggestedFix) are populated with meaningful content.

## Threat Flags

None — all threats in the plan's threat model (T-03-07, T-03-08, T-03-09) were accepted. Rule output consists entirely of server-generated string literals and enum values; no user-supplied data reaches finding construction.

## Self-Check

- TdsSectionMappingRule.java: FOUND at Service/superaccountant/src/main/java/com/arktech/superaccountant/masters/rules/TdsSectionMappingRule.java
- GstApplicabilityRule.java: FOUND at Service/superaccountant/src/main/java/com/arktech/superaccountant/masters/rules/GstApplicabilityRule.java
- HsnSacCodeRule.java: FOUND at Service/superaccountant/src/main/java/com/arktech/superaccountant/masters/rules/HsnSacCodeRule.java
- GstinPresenceRule.java: FOUND at Service/superaccountant/src/main/java/com/arktech/superaccountant/masters/rules/GstinPresenceRule.java
- Commits abc407a, e1b530a, 6fdacbe: all present in git log
- ./mvnw test -Dtest=TdsSectionMappingRuleTest,GstApplicabilityRuleTest,HsnSacCodeRuleTest,GstinPresenceRuleTest: 50 tests, 0 failures
- ./mvnw test (full suite): 96 tests, 0 failures
- No FindingSeverity.ERROR/WARNING/INFO in MismatchDetectionRule.java: confirmed (grep returned empty)
- All 5 @Component annotations present in rules package: confirmed

## Self-Check: PASSED
