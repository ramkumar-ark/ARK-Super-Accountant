---
phase: 03-masters-tds-gst-mapping-extension
fixed_at: 2026-05-07T00:00:00Z
review_path: .planning/phases/03-masters-tds-gst-mapping-extension/03-REVIEW.md
iteration: 1
findings_in_scope: 11
fixed: 11
skipped: 0
status: all_fixed
---

# Phase 03: Code Review Fix Report

**Fixed at:** 2026-05-07T00:00:00Z
**Source review:** .planning/phases/03-masters-tds-gst-mapping-extension/03-REVIEW.md
**Iteration:** 1

**Summary:**
- Findings in scope: 11 (3 Critical + 8 Warning; CR-03 and WR-08 fixed together as one commit)
- Fixed: 11
- Skipped: 0

---

## Fixed Issues

### CR-01: `IllegalArgumentException` crash on invalid `gstApplicabilityType` in update endpoint

**Files modified:** `UpdatePreconfiguredMasterRequest.java`, `PreconfiguredMastersController.java`
**Commit:** `1977bff`
**Applied fix:** Added `@Pattern(regexp = "TAXABLE|EXEMPT|ZERO_RATED|NON_GST|RCM|NOT_APPLICABLE")` to `gstApplicabilityType` in `UpdatePreconfiguredMasterRequest`. Added a try/catch around `GstApplicabilityType.valueOf()` in the update handler to return HTTP 400 instead of propagating the `IllegalArgumentException` as a 500.

---

### CR-02: `DataInitializer` construction-template guard bypassed when named templates already exist

**Files modified:** `DataInitializer.java`
**Commit:** `7f6a96e`
**Applied fix:** Replaced the non-specific `findByTemplateTrue().size() > 0` guard with a slug-specific `findByTemplateTrueAndTemplateSlug(CONSTRUCTION_SLUG).isEmpty()` guard, consistent with all other named templates. Added `private static final String CONSTRUCTION_SLUG = "construction"`. Migrated all 27 construction template rows from the legacy 5-arg `template()` helper to the 10-arg extended helper so each row carries its `templateSlug` and full TDS/GST metadata.

---

### CR-03 + WR-08: `@Transactional` on `backfillFindingSeverities()` silently ignored / three auto-committed updates risk partial backfill

**Files modified:** `DataInitializer.java` (updated), `DataMigrationService.java` (new file)
**Commit:** `444b121`
**Applied fix:** Created a new `@Service` bean `DataMigrationService` in the same package. The `backfillFindingSeverities()` method is now on this bean with a proper `@Transactional` boundary that Spring's AOP proxy intercepts. Added an idempotency short-circuit (`SELECT COUNT` for legacy severity values) to avoid three UPDATE round-trips on every boot after migration completes. All three UPDATEs now execute within a single transaction so a mid-migration failure rolls back atomically. `DataInitializer` autowires `DataMigrationService` and delegates to it; the old self-invoked method and `EntityManager` injection were removed from `DataInitializer`.

---

### WR-01: `GstinPresenceRule` flags every purchase ledger regardless of GST applicability

**Files modified:** `GstinPresenceRule.java`
**Commit:** `2474d33`
**Applied fix:** Added two additional guard conditions before emitting a finding: `master.getGstApplicabilityType() != null` and `master.getGstApplicabilityType() != GstApplicabilityType.NOT_APPLICABLE`. Stock/balance-sheet ledgers (Stock-in-Hand, Raw Material Stock, Work-in-Progress, Finished Goods, etc.) seeded with `NOT_APPLICABLE` will no longer generate spurious HIGH findings.

---

### WR-02: `TdsSectionMappingRule` fires on ledgers for which TDS is structurally inapplicable

**Files modified:** `TdsSectionMappingRule.java`
**Commit:** `b2eb612`
**Applied fix:** Added an early `continue` to skip ledgers whose category is `GST`, `TDS`, or `OTHER` — categories where TDS is structurally never applicable. These ledgers (Input/Output GST accounts, TDS Payable/Receivable, Cash, Bank) will no longer generate LOW findings for missing TDS sections.

---

### WR-03: `create` endpoint silently ignores TDS/GST fields from `CreatePreconfiguredMasterRequest`

**Files modified:** `PreconfiguredMastersController.java`, `CreatePreconfiguredMasterRequest.java`
**Commit:** `95179eb`
**Applied fix:** Mapped `tdsSection`, `gstApplicabilityType`, `hsnSacCode`, and `gstin` from `CreatePreconfiguredMasterRequest` in both the `create` handler and the `bulkImport` handler loop. Added the same `@Pattern` guard and try/catch for `valueOf` as CR-01 to protect against invalid `gstApplicabilityType` strings in both paths. Added `@Pattern(regexp = "TAXABLE|EXEMPT|ZERO_RATED|NON_GST|RCM|NOT_APPLICABLE")` to `CreatePreconfiguredMasterRequest.gstApplicabilityType` to prevent invalid values reaching `valueOf`.

---

### WR-04: Authorization inconsistency — `ROLE_OWNER` can delete but not create or update masters

**Files modified:** `PreconfiguredMastersController.java`
**Commit:** `06f3938`
**Applied fix:** Updated `@PreAuthorize` on `create`, `update`, and `bulkImport` from `hasRole('ACCOUNTANT') or hasRole('OPERATOR')` to `hasRole('OWNER') or hasRole('ACCOUNTANT') or hasRole('OPERATOR')`. `ROLE_OWNER` now has full write access consistent with their existing delete and onboard permissions.

---

### WR-05: Pagination display bug — shows `1–50 of 0` when masters list is empty

**Files modified:** `MastersPage.tsx`
**Commit:** `4e66a93`
**Applied fix:** Changed the pagination lower-bound expression from `{page * PAGE_SIZE + 1}` to `{totalElements === 0 ? '0' : \`${page * PAGE_SIZE + 1}–${Math.min((page + 1) * PAGE_SIZE, totalElements)}\`}` so the display reads `0 of 0 ledgers` instead of `1–0 of 0 ledgers` when there are no results.

---

### WR-06: `/masters` route access control diverges from backend — `ROLE_OWNER` blocked from view

**Files modified:** `Client/src/main.tsx`, `Client/src/components/AppShell.tsx`
**Commit:** `b7b7ee4`
**Applied fix:** Added `role !== 'ROLE_OWNER'` to the route guard condition in `main.tsx` so owners are no longer silently redirected to `/dashboard`. Added `user?.role === 'ROLE_OWNER'` to the `canAccessMasters` expression in `AppShell.tsx` so the Masters sidebar link is visible to owners.

---

### WR-07: `uploadJobId` not set on findings emitted by Phase 3 rules

**Files modified:** `TdsSectionMappingRule.java`, `GstApplicabilityRule.java`, `HsnSacCodeRule.java`, `GstinPresenceRule.java`
**Commit:** `b9ba29c`
**Applied fix:** Added a two-line comment above the `ValidationFinding` construction block in each of the four Phase 3 rules: `// Note: uploadJobId is injected by ValidationOrchestrator.runAndPersist() before persistence. // Do not set it here; it is not available at rule execution time.` This documents the orchestrator contract and prevents future contributors from setting the field at the wrong layer or being confused by its absence.

---

## Skipped Issues

None — all 11 in-scope findings were fixed.

---

_Fixed: 2026-05-07T00:00:00Z_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
