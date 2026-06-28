---
phase: 03-masters-tds-gst-mapping-extension
verified: 2026-05-07T00:00:00Z
status: gaps_found
score: 18/20 must-haves verified
overrides_applied: 0
gaps:
  - truth: "Existing validation_findings rows with INFO/WARNING/ERROR severity are backfilled to LOW/MEDIUM/HIGH on first startup"
    status: failed
    reason: "DataInitializer.java has no backfillFindingSeverities() method and no EntityManager injection. The must-have from Plan 01 was not implemented."
    artifacts:
      - path: "Service/superaccountant/src/main/java/com/arktech/superaccountant/login/config/DataInitializer.java"
        issue: "No backfillFindingSeverities() method, no @PersistenceContext EntityManager, no Transactional bulk UPDATE queries for INFO->LOW, WARNING->MEDIUM, ERROR->HIGH"
    missing:
      - "Add @PersistenceContext private EntityManager entityManager field to DataInitializer"
      - "Add @Transactional private void backfillFindingSeverities() with three JPQL UPDATE statements: INFO->LOW, WARNING->MEDIUM, ERROR->HIGH"
      - "Call backfillFindingSeverities() from run() after seedValidationRuleIfAbsent calls"
  - truth: "An OPERATOR navigating to /masters sees the Masters view (role guard allows ROLE_OPERATOR)"
    status: failed
    reason: "Frontend route guard and AppShell check for 'ROLE_DATA_ENTRY_OPERATOR' but the backend ERole enum is ROLE_OPERATOR. An OPERATOR JWT carries authority 'ROLE_OPERATOR' which is not matched — OPERATORS are silently redirected to /dashboard instead of seeing Masters."
    artifacts:
      - path: "Client/src/main.tsx"
        issue: "Line 78: role !== 'ROLE_ACCOUNTANT' && role !== 'ROLE_DATA_ENTRY_OPERATOR' — should be 'ROLE_OPERATOR'"
      - path: "Client/src/components/AppShell.tsx"
        issue: "Line 12: user?.role === 'ROLE_DATA_ENTRY_OPERATOR' — should be 'ROLE_OPERATOR'"
    missing:
      - "Replace 'ROLE_DATA_ENTRY_OPERATOR' with 'ROLE_OPERATOR' in main.tsx route guard beforeLoad"
      - "Replace 'ROLE_DATA_ENTRY_OPERATOR' with 'ROLE_OPERATOR' in AppShell.tsx canAccessMasters condition"
human_verification:
  - test: "Navigate to /organization/setup, complete Step 1 (create org), verify Step 2 renders three template cards (Standard, Simplified, Manufacturing) with radio selection, and clicking Apply Template calls onboard correctly"
    expected: "Three cards render, selecting one highlights it with primary border, Apply Template triggers POST /v1/preconfigured-masters/onboard with the selected slug, success navigates to /dashboard"
    why_human: "Multi-step UI flow with visual state transitions not testable in Vitest/happy-dom"
  - test: "Log in as AUDITOR_CA user, navigate to /dashboard, verify sidebar does NOT show Masters nav item"
    expected: "Masters nav item absent from sidebar DOM for AUDITOR_CA"
    why_human: "DOM visibility guard conditioned on role string — requires a real authenticated session to validate"
  - test: "Open /masters, click Edit on a ledger row, verify LedgerMappingPanel slides in from the right and keyboard Tab key stays inside the panel"
    expected: "Panel opens as a right-anchored side sheet with focus trapped inside; Escape or Cancel closes it"
    why_human: "CSS animation and focus trap behavior cannot be asserted in Vitest with happy-dom"
---

# Phase 3: Masters TDS & GST Mapping Extension Verification Report

**Phase Goal:** Every ledger in the masters is mapped to a TDS section and GST applicability classification, so the TDS and GSTR-2B engines have the metadata they need to compute correctly.
**Verified:** 2026-05-07
**Status:** gaps_found
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | PreconfiguredMaster entity has 5 new nullable columns: templateSlug, tdsSection, gstApplicabilityType, hsnSacCode, gstin | VERIFIED | PreconfiguredMaster.java lines 48-63 confirm all 5 fields with correct @Column and @Enumerated annotations |
| 2 | GstApplicabilityType enum has 6 values: TAXABLE, EXEMPT, ZERO_RATED, NON_GST, RCM, NOT_APPLICABLE | VERIFIED | GstApplicabilityType.java line 4 confirms all 6 constants |
| 3 | FindingSeverity enum has HIGH, MEDIUM, LOW in addition to INFO, WARNING, ERROR | VERIFIED | FindingSeverity.java contains all 6 values (INFO, WARNING, ERROR, HIGH, MEDIUM, LOW) |
| 4 | ResolveStatus enum has ACCEPTED, OVERRIDDEN in addition to OPEN, ACKNOWLEDGED, RESOLVED | VERIFIED | ResolveStatus.java contains all 5 values |
| 5 | ValidationFindingRepository.findFiltered uses positive OPEN/ACKNOWLEDGED whitelist (not negative exclusion) | VERIFIED | ValidationFindingRepository.java lines 23-25 show positive whitelist: `resolveStatus = OPEN OR resolveStatus = ACKNOWLEDGED` |
| 6 | DataInitializer seeds 5 validation rules idempotently via existsByRuleCode guard | VERIFIED | DataInitializer.java lines 31-40 call seedValidationRuleIfAbsent 5 times; existsByRuleCode used in helper |
| 7 | DataInitializer.backfillFindingSeverities() migrates INFO->LOW, WARNING->MEDIUM, ERROR->HIGH on startup | FAILED | No backfillFindingSeverities() method exists in DataInitializer.java. No EntityManager injection present. |
| 8 | PUT /api/v1/preconfigured-masters/{id} accepts and persists the 4 new fields via null-check partial update | VERIFIED | PreconfiguredMastersController.java lines 103-108 confirm 4 new null-checks for tdsSection, gstApplicabilityType, hsnSacCode, gstin |
| 9 | POST /api/v1/preconfigured-masters/onboard accepts templateSlug and copies template rows to org | VERIFIED | PreconfiguredMastersController.java lines 182-211 confirm slug-based branch with valid slug validation |
| 10 | GET /api/v1/preconfigured-masters returns the 4 new fields in response | VERIFIED | PreconfiguredMasterResponse.java has tdsSection, gstApplicabilityType, hsnSacCode, gstin fields; toResponse() in controller maps all 4 |
| 11 | TdsSectionMappingRule, GstApplicabilityRule, HsnSacCodeRule, GstinPresenceRule are @Component beans with correct severity logic | VERIFIED | All 4 rules exist under masters/rules/ with @Component("RULE_CODE") annotations; TdsSectionMappingRule verified in detail |
| 12 | MismatchDetectionRule updated to use HIGH/MEDIUM/LOW (no ERROR/WARNING/INFO references) | VERIFIED | MismatchDetectionRule.java uses FindingSeverity.HIGH, MEDIUM, LOW only; grep for ERROR/WARNING/INFO returns no matches |
| 13 | DataInitializer seeds standard (60+), simplified (30+), manufacturing (80+) templates with per-slug idempotent guards | VERIFIED | Grep counts: standard=63 rows, simplified=32 rows, manufacturing=83 rows; 3 findByTemplateTrueAndTemplateSlug guards present in run() |
| 14 | GET /api/v1/uploads/latest/mismatches exists and is org-scoped with @PreAuthorize | VERIFIED | UploadController.java lines 165-193 confirm endpoint with @PreAuthorize and orgId-scoped UploadJob lookup |
| 15 | AppShell.tsx exists as shared layout, used by DashboardPage and MastersPage with no sidebar duplication | VERIFIED | AppShell.tsx exists with sidebar markup; DashboardPage.tsx imports AppShell (line 5, 17); MastersPage.tsx imports AppShell (line 5) |
| 16 | /masters route registered with auth guard in main.tsx | VERIFIED | main.tsx lines 71-83 confirm mastersRoute with isAuthenticated check and redirect to /login |
| 17 | OPERATOR and ACCOUNTANT can access /masters; OWNER and AUDITOR_CA are redirected to /dashboard | FAILED | main.tsx line 78 and AppShell.tsx line 12 check for 'ROLE_DATA_ENTRY_OPERATOR' but backend ERole is ROLE_OPERATOR. OPERATORS cannot access /masters. |
| 18 | LedgerMappingPanel validates GSTIN and HSN/SAC on blur and calls PUT on save | VERIFIED | LedgerMappingPanel.tsx has GSTIN_REGEX, HSN_SAC_REGEX, api.put call to /v1/preconfigured-masters/{id}, role="dialog", aria-modal="true" |
| 19 | OrganizationSetupPage has two-step flow with template selector; no window.alert | VERIFIED | OrganizationSetupPage.tsx has setStep(2), templateSlug, "Skip for now"; no window.alert |
| 20 | Integration tests for named template onboarding and fixture-based finding count assertions exist | VERIFIED | PreconfiguredMastersControllerIT.java has 4 test methods; ValidationOrchestratorIT.java has 2 test methods; masters-with-tds-gaps.json fixture exists |

**Score:** 18/20 truths verified

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `Service/.../masters/models/GstApplicabilityType.java` | New enum with 6 values | VERIFIED | 6 constants confirmed |
| `Service/.../masters/models/FindingSeverity.java` | Extended enum with 6 values | VERIFIED | INFO, WARNING, ERROR, HIGH, MEDIUM, LOW |
| `Service/.../masters/models/ResolveStatus.java` | Extended enum with 5 values | VERIFIED | OPEN, ACKNOWLEDGED, RESOLVED, ACCEPTED, OVERRIDDEN |
| `Service/.../masters/models/PreconfiguredMaster.java` | 5 new nullable columns | VERIFIED | All 5 fields with correct JPA annotations |
| `Service/.../masters/repository/ValidationFindingRepository.java` | Positive whitelist JPQL | VERIFIED | OPEN/ACKNOWLEDGED whitelist confirmed |
| `Service/.../masters/repository/ValidationRuleConfigRepository.java` | existsByRuleCode method | VERIFIED | Method present on line 13 |
| `Service/.../masters/repository/PreconfiguredMasterRepository.java` | findByTemplateTrueAndTemplateSlug | VERIFIED | Method present on line 20 |
| `Service/.../masters/controllers/PreconfiguredMastersController.java` | 4 new null-checks + onboard slug routing | VERIFIED | Lines 103-108 and 182-211 confirmed |
| `Service/.../masters/controllers/UploadController.java` | GET /uploads/latest/mismatches | VERIFIED | Lines 165-193 confirmed |
| `Service/.../login/config/DataInitializer.java` | 5 idempotent rule seeds + 3 template methods + backfill | STUB | existsByRuleCode guard and 3 template methods present; backfillFindingSeverities() MISSING |
| `Service/.../masters/rules/TdsSectionMappingRule.java` | @Component("TDS_SECTION_MAPPING") | VERIFIED | Exists with correct annotation and severity logic |
| `Service/.../masters/rules/GstApplicabilityRule.java` | @Component("GST_APPLICABILITY") | VERIFIED | Exists |
| `Service/.../masters/rules/HsnSacCodeRule.java` | @Component("HSN_SAC_CODE") | VERIFIED | Exists |
| `Service/.../masters/rules/GstinPresenceRule.java` | @Component("GSTIN_PRESENCE") | VERIFIED | Exists |
| `Client/src/components/AppShell.tsx` | Shared sidebar layout | VERIFIED | Exists with Masters nav role-gated |
| `Client/src/pages/MastersPage.tsx` | Masters page with Ledgers + Findings tabs | VERIFIED | Exists with both tabs, API calls confirmed |
| `Client/src/components/LedgerMappingPanel.tsx` | Right-anchored side sheet | VERIFIED | Exists with validation and PUT call |
| `Client/src/pages/OrganizationSetupPage.tsx` | Two-step flow with template selector | VERIFIED | setStep(2), templateSlug, Skip for now confirmed |
| `Client/src/main.tsx` | /masters route with auth + role guard | PARTIAL | Route registered; role string wrong ('ROLE_DATA_ENTRY_OPERATOR' instead of 'ROLE_OPERATOR') |
| `Service/.../test/.../PreconfiguredMastersControllerIT.java` | 4 IT scenarios for onboard | VERIFIED | All 4 test methods confirmed |
| `Service/.../test/.../ValidationOrchestratorIT.java` | 2 IT scenarios for finding counts | VERIFIED | Both test methods confirmed |
| `Service/.../test/resources/fixtures/masters-with-tds-gaps.json` | Fixture with TDS and GSTIN gaps | VERIFIED | File exists at expected path |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| PreconfiguredMastersController.onboard | PreconfiguredMasterRepository.findByTemplateTrueAndTemplateSlug | templateSlug parameter routing | VERIFIED | Lines 182-188 in controller |
| ValidationFindingRepository.findFiltered | ResolveStatus.OPEN / ACKNOWLEDGED whitelist | JPQL positive whitelist condition | VERIFIED | Lines 23-25 in repository |
| DataInitializer.run | seedValidationRuleIfAbsent | per-rule idempotent guard | VERIFIED | Lines 31-40 in DataInitializer |
| DataInitializer.run | backfillFindingSeverities | JPQL bulk UPDATE on startup | NOT_WIRED | Method does not exist in DataInitializer |
| MastersPage Ledgers tab | GET /api/v1/preconfigured-masters | api.get in useEffect | VERIFIED | MastersPage.tsx line 61 |
| MastersPage Findings tab | GET /api/v1/uploads/latest/mismatches | api.get in useEffect | VERIFIED | MastersPage.tsx line 77 |
| LedgerMappingPanel.handleSave | PUT /api/v1/preconfigured-masters/{id} | api.put | VERIFIED | LedgerMappingPanel.tsx line 113 |
| Findings Accept Fix | PATCH resolve endpoint | api.patch with {status: 'ACCEPTED'} | VERIFIED | MastersPage.tsx line 457 |
| OrganizationSetupPage Step 2 | POST /api/v1/preconfigured-masters/onboard | api.post with {templateSlug} | VERIFIED | OrganizationSetupPage.tsx line 103 |
| AppShell Masters nav | /masters route | ROLE_OPERATOR check | NOT_WIRED | AppShell.tsx checks 'ROLE_DATA_ENTRY_OPERATOR'; backend sends 'ROLE_OPERATOR' in JWT |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|--------------|--------|-------------------|--------|
| MastersPage.tsx | masters state | GET /v1/preconfigured-masters via api.get | Yes — backend queries DB via masterRepository | FLOWING |
| MastersPage.tsx | findings state | GET /v1/uploads/latest/mismatches via api.get | Yes — backend queries findingRepository against latest completed job | FLOWING |
| LedgerMappingPanel.tsx | form state | Initialized from master prop, PUT persists to DB | Yes — api.put calls controller which saves to masterRepository | FLOWING |

### Behavioral Spot-Checks

Step 7b: SKIPPED (requires running server to verify HTTP endpoints; no runnable entry points available for static verification).

### Requirements Coverage

REQUIREMENTS.md does not exist in this project. Requirements are defined in ROADMAP.md Phase 3 section. Mapping to Success Criteria:

| Requirement ID | ROADMAP Success Criterion | Status | Evidence |
|---------------|--------------------------|--------|---------|
| MSTR-01 | OPERATOR can onboard using preconfigured master template with pre-classified ledgers | SATISFIED | onboard endpoint with templateSlug routing; 63/32/83 template rows seeded; OrganizationSetupPage step 2 |
| MSTR-02 | Validation pipeline flags ledgers with unexpected/missing category as finding with HIGH/MEDIUM/LOW severity | SATISFIED | TdsSectionMappingRule, MismatchDetectionRule both use HIGH/MEDIUM/LOW |
| MSTR-03 | Every ledger has TDS section assigned or marked "not subject to TDS"; masters view shows this column | PARTIALLY SATISFIED | Backend column exists and populated in templates; UI TDS Section column rendered in MastersPage table; OPERATOR cannot reach /masters due to role string bug |
| MSTR-04 | Every sales/income ledger has GST applicability type and HSN/SAC code; missing codes flagged | SATISFIED | GstApplicabilityRule, HsnSacCodeRule implemented; gstApplicabilityType column in response |
| MSTR-05 | Every purchase ledger linked to registered dealer has GSTIN; missing GSTIN is HIGH severity finding | SATISFIED | GstinPresenceRule emits HIGH for PURCHASE with null gstin |
| MSTR-06 | Masters page accessible for ACCOUNTANT and OPERATOR; OWNER/AUDITOR_CA redirected | PARTIALLY SATISFIED | ACCOUNTANT access works; OPERATOR blocked by 'ROLE_DATA_ENTRY_OPERATOR' string mismatch |
| MSTR-07 | Findings filter excludes ACCEPTED and OVERRIDDEN when showResolved=false | SATISFIED | ValidationFindingRepository JPQL positive whitelist; UploadController PATCH sets ACCEPTED/OVERRIDDEN |
| MSTR-08 | Integration tests assert finding counts for known fixture files | SATISFIED | ValidationOrchestratorIT has fixture-based assertions; PreconfiguredMastersControllerIT has onboard count assertions |

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|---------|--------|
| `Client/src/main.tsx` | 78 | `role !== 'ROLE_DATA_ENTRY_OPERATOR'` — wrong role string | BLOCKER | OPERATOR role users (backend ERole.ROLE_OPERATOR, JWT authority 'ROLE_OPERATOR') are blocked from accessing /masters and redirected to /dashboard |
| `Client/src/components/AppShell.tsx` | 12 | `user?.role === 'ROLE_DATA_ENTRY_OPERATOR'` — wrong role string | BLOCKER | Masters nav item is hidden from all OPERATOR users in the sidebar |
| `Service/.../login/config/DataInitializer.java` | — | backfillFindingSeverities() method absent | WARNING | Existing validation_findings rows with INFO/WARNING/ERROR severity values will NOT be migrated to LOW/MEDIUM/HIGH on startup — findings from prior upload runs will appear with unrecognized severity values in the UI |

### Human Verification Required

**1. OrganizationSetupPage Two-Step Template Selection**

**Test:** Log in as OWNER or ACCOUNTANT, navigate to /organization/setup, fill out Step 1 org form and submit, verify Step 2 renders.
**Expected:** Three template cards (Standard, Simplified, Manufacturing) display with radio-style selection; Standard is pre-selected; clicking Apply Template calls POST /v1/preconfigured-masters/onboard with the selected slug and navigates to /dashboard on success.
**Why human:** Multi-step form UI flow with visual selection state and navigation cannot be tested in Vitest/happy-dom.

**2. AUDITOR_CA Masters Nav Visibility**

**Test:** Log in as AUDITOR_CA, navigate to /dashboard, inspect the sidebar.
**Expected:** Masters nav item is absent from the sidebar — not rendered in DOM.
**Why human:** Requires real authenticated session with AUDITOR_CA role to validate role-conditional rendering.

**3. LedgerMappingPanel Slide Animation and Focus Trap**

**Test:** Log in as ACCOUNTANT, navigate to /masters (Ledgers tab), click Edit on any ledger.
**Expected:** A right-anchored side sheet slides in from the right; Tab key cycles through form elements inside the panel only (focus trap); Escape or Cancel button closes the panel.
**Why human:** CSS transition and focus management are not assertable in Vitest with happy-dom environment.

### Gaps Summary

Two gaps block full goal achievement:

**Gap 1: Missing severity backfill in DataInitializer** (Warning-level gap)
The Plan 01 must-have required that `DataInitializer.backfillFindingSeverities()` runs on startup to migrate existing `INFO/WARNING/ERROR` severity values to `LOW/MEDIUM/HIGH`. This method was specified in the plan with explicit JPQL UPDATE queries but was never implemented. The DataInitializer has no EntityManager injection and no backfill method. This is a data migration gap — any organization that had upload findings before Phase 3 deployment will have findings with the old enum values (INFO/WARNING/ERROR) which are still valid enum constants but will not match the new UI severity filter (which shows HIGH/MEDIUM/LOW options). The risk is low for new installations but material for any existing deployed instance.

**Gap 2: Wrong role string in frontend guards** (Blocker-level gap)
The Plan 04 frontend implementation uses `'ROLE_DATA_ENTRY_OPERATOR'` for the OPERATOR role check in both `Client/src/main.tsx` (route guard) and `Client/src/components/AppShell.tsx` (sidebar visibility). The backend `ERole` enum is `ROLE_OPERATOR`, and the JWT signin response returns Spring Security authority strings verbatim — so OPERATOR users receive `role: 'ROLE_OPERATOR'` in their auth store. The string `'ROLE_DATA_ENTRY_OPERATOR'` never matches, meaning:
- OPERATOR users who navigate to `/masters` are redirected to `/dashboard`
- The Masters nav item is never shown to OPERATOR users in the sidebar

This directly blocks MSTR-03 and MSTR-06 success criteria. The fix is a one-line string replacement in each file.

---

_Verified: 2026-05-07_
_Verifier: Claude (gsd-verifier)_
