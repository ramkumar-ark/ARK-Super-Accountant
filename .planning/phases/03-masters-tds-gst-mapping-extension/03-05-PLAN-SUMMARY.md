---
phase: 03-masters-tds-gst-mapping-extension
plan: 05
status: complete
completed_at: 2026-05-07
commit: bd5fe83
---

# Plan 03-05 Summary — Integration Tests

## What Was Built

| File | Change |
|------|--------|
| `Service/.../fixtures/masters-with-tds-gaps.json` | NEW — 5-ledger fixture (3 TDS gaps: PURCHASE/EXPENSE/INCOME; 2 GSTIN gaps: PURCHASE) |
| `Service/.../controllers/PreconfiguredMastersControllerIT.java` | NEW — 4 MockMvc IT tests for named template onboarding |
| `Service/.../orchestrator/ValidationOrchestratorIT.java` | NEW — 2 IT tests calling TdsSectionMappingRule and GstinPresenceRule beans directly |

## Key Decisions

- **ValidationOrchestratorIT calls rule beans directly** (not `runAndPersist()`) because `runAndPersist()` requires a persisted `UploadJob` entity. Calling `execute()` on the Spring-wired rule beans achieves the same coverage goal with simpler setup.
- **Auth flow in PreconfiguredMastersControllerIT**: signup (accountant) → signin → create org → select org (returns org-scoped JWT) → onboard. The `onboard` endpoint needs `getOrganizationId() != null` so the org-select step is mandatory.
- **Random username suffix per test** to avoid `@Transactional` isolation edge cases across the 4 controller tests.

## Test Results

- Total tests run: 96
- Failures: 0
- Errors: 1 (pre-existing `SuperaccountantApplicationTests.contextLoads` — JWT_SECRET env var not set in test scope; locked decision from Phase 1)
- All 4 `PreconfiguredMastersControllerIT` methods passed
- Both `ValidationOrchestratorIT` methods passed
