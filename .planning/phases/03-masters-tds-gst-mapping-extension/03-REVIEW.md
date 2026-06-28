---
phase: 03-masters-tds-gst-mapping-extension
reviewed: 2026-05-07T00:00:00Z
depth: standard
files_reviewed: 25
files_reviewed_list:
  - Service/superaccountant/src/main/java/com/arktech/superaccountant/masters/models/GstApplicabilityType.java
  - Service/superaccountant/src/main/java/com/arktech/superaccountant/masters/models/FindingSeverity.java
  - Service/superaccountant/src/main/java/com/arktech/superaccountant/masters/models/ResolveStatus.java
  - Service/superaccountant/src/main/java/com/arktech/superaccountant/masters/models/PreconfiguredMaster.java
  - Service/superaccountant/src/main/java/com/arktech/superaccountant/masters/payload/request/UpdatePreconfiguredMasterRequest.java
  - Service/superaccountant/src/main/java/com/arktech/superaccountant/masters/payload/request/CreatePreconfiguredMasterRequest.java
  - Service/superaccountant/src/main/java/com/arktech/superaccountant/masters/payload/request/OnboardRequest.java
  - Service/superaccountant/src/main/java/com/arktech/superaccountant/masters/payload/response/PreconfiguredMasterResponse.java
  - Service/superaccountant/src/main/java/com/arktech/superaccountant/masters/repository/ValidationFindingRepository.java
  - Service/superaccountant/src/main/java/com/arktech/superaccountant/masters/repository/PreconfiguredMasterRepository.java
  - Service/superaccountant/src/main/java/com/arktech/superaccountant/masters/controllers/PreconfiguredMastersController.java
  - Service/superaccountant/src/main/java/com/arktech/superaccountant/masters/controllers/UploadController.java
  - Service/superaccountant/src/main/java/com/arktech/superaccountant/login/config/DataInitializer.java
  - Service/superaccountant/src/main/java/com/arktech/superaccountant/masters/rules/TdsSectionMappingRule.java
  - Service/superaccountant/src/main/java/com/arktech/superaccountant/masters/rules/GstApplicabilityRule.java
  - Service/superaccountant/src/main/java/com/arktech/superaccountant/masters/rules/HsnSacCodeRule.java
  - Service/superaccountant/src/main/java/com/arktech/superaccountant/masters/rules/GstinPresenceRule.java
  - Service/superaccountant/src/main/java/com/arktech/superaccountant/masters/rules/MismatchDetectionRule.java
  - Client/src/components/AppShell.tsx
  - Client/src/pages/MastersPage.tsx
  - Client/src/components/LedgerMappingPanel.tsx
  - Client/src/pages/OrganizationSetupPage.tsx
  - Client/src/pages/DashboardPage.tsx
  - Client/src/main.tsx
  - Service/superaccountant/src/main/java/com/arktech/superaccountant/masters/models/ValidationFinding.java
findings:
  critical: 3
  warning: 8
  info: 5
  total: 16
status: issues_found
---

# Phase 03: Code Review Report

**Reviewed:** 2026-05-07T00:00:00Z
**Depth:** standard
**Files Reviewed:** 25
**Status:** issues_found

## Summary

This phase introduces the TDS/GST mapping extension across 22 files: new validation rules (TDS section, GST applicability, HSN/SAC, GSTIN presence), preconfigured master CRUD and bulk-import controllers, onboarding/template seeding, upload/mismatch controllers, and the corresponding React pages and components.

The implementation is generally solid. The rule architecture is clean, template data is thorough, and the frontend UX covers the main flows. However, two critical issues require attention: an unguarded `IllegalArgumentException` crash in the update endpoint when an invalid `gstApplicabilityType` string is supplied, and a race-condition-prone idempotency guard in `DataInitializer` that can seed the construction template redundantly if any named template exists. Seven warnings address logic gaps in validation rules (noisy findings on non-applicable ledgers), missing input validation on the bulk-import path, an authorization gap in the `/masters` route, and a pagination display bug. Four informational items cover code quality.

A gap-closure pass (reviewed 2026-05-07) re-examined three files changed as remediation for previously-reported gaps: `DataInitializer.java` (new `backfillFindingSeverities()` method), `Client/src/main.tsx` (role name fix for `/masters` guard), and `Client/src/components/AppShell.tsx` (matching sidebar role name fix). The role-name typo fix is correct and verified against the `ERole` enum. One new critical issue was found in the backfill method, one new warning, and one informational item.

---

## Critical Issues

### CR-01: `IllegalArgumentException` crash on invalid `gstApplicabilityType` in update endpoint

**File:** `Service/superaccountant/src/main/java/com/arktech/superaccountant/masters/controllers/PreconfiguredMastersController.java:105`

**Issue:** `GstApplicabilityType.valueOf(request.getGstApplicabilityType())` will throw an unchecked `IllegalArgumentException` if the caller sends any string that is not an exact enum constant (e.g., `"taxable"`, `"UNKNOWN"`, or a typo). There is no `@Pattern` or enum-type validation on the `gstApplicabilityType` field in `UpdatePreconfiguredMasterRequest` (the field is typed as `String`, not `GstApplicabilityType`). Spring will not intercept this as a 400; it will propagate as a 500.

**Fix:**
Option A — change the DTO field to the enum type so Spring's binding layer rejects bad values automatically:
```java
// UpdatePreconfiguredMasterRequest.java
private GstApplicabilityType gstApplicabilityType;
```
Then in the controller, remove `valueOf`:
```java
if (request.getGstApplicabilityType() != null)
    master.setGstApplicabilityType(request.getGstApplicabilityType());
```

Option B — add a `@Pattern` annotation matching the enum constants (same approach already used for `tdsSection`):
```java
@Pattern(regexp = "TAXABLE|EXEMPT|ZERO_RATED|NON_GST|RCM|NOT_APPLICABLE",
         message = "Invalid GST applicability type")
private String gstApplicabilityType;
```
And wrap `valueOf` in a try/catch as a belt-and-suspenders:
```java
if (request.getGstApplicabilityType() != null) {
    try {
        master.setGstApplicabilityType(
            GstApplicabilityType.valueOf(request.getGstApplicabilityType()));
    } catch (IllegalArgumentException e) {
        return ResponseEntity.badRequest().body("Invalid gstApplicabilityType: " + request.getGstApplicabilityType());
    }
}
```
The same gap exists in `CreatePreconfiguredMasterRequest` — the `gstApplicabilityType` field is also an unvalidated `String` there — but the create endpoint never calls `valueOf`, so it currently silently ignores the value. Once `create` is extended to persist it, the crash risk will apply there too.

---

### CR-02: `DataInitializer` construction-template guard bypassed when named templates already exist

**File:** `Service/superaccountant/src/main/java/com/arktech/superaccountant/login/config/DataInitializer.java:82-83`

**Issue:** `seedConstructionTemplate()` uses `findByTemplateTrue().size() > 0` as its guard. This means: once any named template (standard, simplified, manufacturing) is seeded, `templateCount` will be greater than zero, and `seedConstructionTemplate()` will correctly skip. However, on a fresh database the sequence is:
1. `seedConstructionTemplate()` runs — seeds ~27 rows (no `templateSlug`).
2. `seedStandardTemplate()` checks `findByTemplateTrueAndTemplateSlug("standard").isEmpty()` — this is true (slug is null on construction rows), so it seeds.

The construction template has no `templateSlug` set (`templateSlug` is `null`). On a **second startup** against the same database, `findByTemplateTrue().size()` returns 27 + 60+ = 87, so `seedConstructionTemplate()` skips correctly. But if the DB is wiped between "standard only" and a full restart, the construction template will be seeded alongside standard in the same run — that part is fine.

The real problem is more subtle: the guard is **not slug-specific**. If only the construction template is present (e.g., an operator deleted all named templates), restarting the application will NOT re-seed the named templates (because `findByTemplateTrueAndTemplateSlug(slug).isEmpty()` checks per slug, which is correct), but it also will NOT re-seed the construction template (because `findByTemplateTrue().size() > 0` returns true). This is a data loss scenario: the construction template can never be re-seeded once any other template exists. More critically, if a deployment starts fresh and a previous migration left orphan template rows, `seedConstructionTemplate()` silently skips.

**Fix:** Use a slug-specific guard matching the pattern used for the other templates:
```java
// DataInitializer.java — replace the check at line 82-83
private static final String CONSTRUCTION_SLUG = "construction";

private void seedConstructionTemplate() {
    if (!preconfiguredMasterRepository
            .findByTemplateTrueAndTemplateSlug(CONSTRUCTION_SLUG).isEmpty()) return;

    List<PreconfiguredMaster> templates = List.of(
        template("Cement", LedgerCategory.PURCHASE, "Purchase Accounts", null, null,
                 CONSTRUCTION_SLUG, null, null, null, null),
        // ... rest of rows using the extended template() helper with slug
    );
    preconfiguredMasterRepository.saveAll(templates);
}
```
This makes all four templates use the same idempotency mechanism.

---

### CR-03: `@Transactional` on `backfillFindingSeverities()` is silently ignored due to self-invocation

**File:** `Service/superaccountant/src/main/java/com/arktech/superaccountant/login/config/DataInitializer.java:86-98`

**Issue:** `backfillFindingSeverities()` is annotated `@Transactional` and is called from `run()` on the same object instance (`this.backfillFindingSeverities()` via direct method call). Spring's transaction management works through AOP proxies: a transaction is only started when the call passes through the proxy. Internal calls from within the same bean bypass the proxy entirely, so the `@Transactional` annotation has no effect.

The consequence is that the three `executeUpdate()` calls each execute in their own auto-committed transaction. If the second update (`'WARNING' → 'MEDIUM'`) succeeds and the third (`'ERROR' → 'HIGH'`) throws a `PersistenceException` (e.g., a DB connectivity blip or constraint violation), rows already updated to `LOW` and `MEDIUM` are permanently committed. On the next application restart, those rows have severity `LOW` or `MEDIUM`, and the first two UPDATE queries will find zero matching rows (since `INFO` and `WARNING` no longer exist), so the partial migration is never completed. Rows in `FindingSeverity.ERROR` will remain at `ERROR` indefinitely, which the UI's severity filter — which only exposes `HIGH`, `MEDIUM`, and `LOW` — will never surface.

**Fix:** Move the backfill logic to a separate Spring-managed bean so the proxy wraps the transaction boundary:

```java
// New class: DataInitializerHelper.java (or inline into a @Service)
@Service
public class DataInitializerHelper {

    @PersistenceContext
    EntityManager entityManager;

    @Transactional
    public int backfillFindingSeverities() {
        int updated = entityManager.createQuery(
            "UPDATE ValidationFinding f SET f.severity = 'LOW' WHERE f.severity = 'INFO'")
            .executeUpdate();
        updated += entityManager.createQuery(
            "UPDATE ValidationFinding f SET f.severity = 'MEDIUM' WHERE f.severity = 'WARNING'")
            .executeUpdate();
        updated += entityManager.createQuery(
            "UPDATE ValidationFinding f SET f.severity = 'HIGH' WHERE f.severity = 'ERROR'")
            .executeUpdate();
        return updated;
    }
}
```

Then in `DataInitializer`:
```java
@Autowired
DataInitializerHelper helper;

@Override
public void run(String... args) throws Exception {
    // ...existing seed calls...
    int updated = helper.backfillFindingSeverities();
    if (updated > 0) System.out.println("Backfilled " + updated + " finding severity values.");
}
```

Alternative: annotate `DataInitializer` itself with `@Transactional` at the class level and call `backfillFindingSeverities()` via a self-injected reference, but the separate-bean approach is cleaner and avoids surprising transaction scope on the entire `run()` method (which executes DDL-equivalent seed operations better handled outside a long transaction).

---

## Warnings

### WR-01: `GstinPresenceRule` flags every purchase ledger regardless of GST applicability

**File:** `Service/superaccountant/src/main/java/com/arktech/superaccountant/masters/rules/GstinPresenceRule.java:29`

**Issue:** The rule emits a `HIGH` severity finding for every `PURCHASE` ledger that has no GSTIN — including ledgers like `Stock-in-Hand`, `Raw Material Stock`, `Work-in-Progress`, `Finished Goods`, and `Purchase Returns` that are stock/balance-sheet accounts and do not represent a vendor transaction. These ledgers will never have a GSTIN and are not subject to GSTR-2B reconciliation. The template data confirms this: all stock accounts are seeded with `NOT_APPLICABLE` GST type but no GSTIN, so they will generate HIGH findings on every upload.

**Fix:** Gate the check on `gstApplicabilityType` (or at minimum on the `expectedParentGroup`):
```java
if (master.getCategory() == LedgerCategory.PURCHASE
        && master.getGstin() == null
        && master.getGstApplicabilityType() != GstApplicabilityType.NOT_APPLICABLE
        && master.getGstApplicabilityType() != null) {
    // emit finding
}
```

---

### WR-02: `TdsSectionMappingRule` fires on ledgers for which TDS is structurally inapplicable

**File:** `Service/superaccountant/src/main/java/com/arktech/superaccountant/masters/rules/TdsSectionMappingRule.java:28`

**Issue:** The rule flags every master with `tdsSection == null`. For `GST`, `TDS`, and `OTHER` category ledgers (e.g., `Cash`, `Bank Account`, `Input CGST`, `Output IGST`), TDS is never applicable. The legacy construction template creates masters with `tdsSection == null` for these categories; running a validation on an org onboarded via that template will produce noisy LOW findings for structural ledgers. More importantly, the construction template is seeded without the extended `template()` helper so these fields remain null permanently.

**Fix:** Skip categories where TDS is structurally inapplicable:
```java
for (PreconfiguredMaster master : context.preconfiguredMasters()) {
    if (master.getCategory() == LedgerCategory.GST
            || master.getCategory() == LedgerCategory.TDS
            || master.getCategory() == LedgerCategory.OTHER) {
        continue;
    }
    if (master.getTdsSection() == null) {
        // ... existing finding logic
    }
}
```

---

### WR-03: `create` endpoint silently ignores TDS/GST fields from `CreatePreconfiguredMasterRequest`

**File:** `Service/superaccountant/src/main/java/com/arktech/superaccountant/masters/controllers/PreconfiguredMastersController.java:71-80`

**Issue:** `CreatePreconfiguredMasterRequest` defines `tdsSection`, `gstApplicabilityType`, `hsnSacCode`, and `gstin` fields with validation annotations, but the `create` handler never reads them. Only `ledgerName`, `category`, `expectedParentGroup`, `expectedGstApplicable`, and `expectedTdsApplicable` are mapped to the entity. A caller sending all six fields will find the TDS/GST data silently discarded. The same omission exists in the `bulkImport` handler (lines 149-156).

**Fix:** Map the additional fields in both `create` and `bulkImport`:
```java
// In create and in the bulkImport loop:
m.setTdsSection(r.getTdsSection());
if (r.getGstApplicabilityType() != null) {
    m.setGstApplicabilityType(
        GstApplicabilityType.valueOf(r.getGstApplicabilityType()));
}
m.setHsnSacCode(r.getHsnSacCode());
m.setGstin(r.getGstin());
```
Note: the same `valueOf` crash risk from CR-01 applies here. Validate the string first (use enum type or Pattern).

---

### WR-04: Authorization inconsistency — `ROLE_OWNER` can delete but not create or update masters

**File:** `Service/superaccountant/src/main/java/com/arktech/superaccountant/masters/controllers/PreconfiguredMastersController.java:60,83,114`

**Issue:** The `@PreAuthorize` annotations are inconsistent:
- `create` (POST): `hasRole('ACCOUNTANT') or hasRole('OPERATOR')`
- `update` (PUT): `hasRole('ACCOUNTANT') or hasRole('OPERATOR')`
- `delete` (DELETE): `hasRole('OWNER') or hasRole('ACCOUNTANT')`

`ROLE_OWNER` can delete masters but cannot create or update them. This is likely unintentional. An owner who finds a rogue master can soft-delete it but cannot correct it. The `onboard` endpoint (line 167) does grant `OWNER` access.

**Fix:** Decide on the intended access model. If owners should have full write access:
```java
@PreAuthorize("hasRole('OWNER') or hasRole('ACCOUNTANT') or hasRole('OPERATOR')")
```
If owners should be read/delete only (by design), add a code comment explaining the rationale.

---

### WR-05: Pagination display bug — shows `1–50 of 0` when masters list is empty

**File:** `Client/src/pages/MastersPage.tsx:309`

**Issue:** The pagination footer is rendered inside the `else` branch (line 213: `filteredMasters.length === 0` guards only the empty-state UI, but the table block is at line 213+). However, the pagination counter uses `page * PAGE_SIZE + 1` without checking if `totalElements > 0`. When `totalElements` is 0 (no results matching a server-side category filter), the display reads `1–0 of 0 ledgers` because `filteredMasters` is empty but the table block is never reached. More precisely, the `filteredMasters.length === 0` check at line 181 shows the empty state, so the pagination line is not actually rendered in that case. BUT: when `filteredMasters.length > 0` and `totalElements > 0` — and page is 0 — the display shows `1–50 of N` even if `filteredMasters.length < 50`. The lower bound is always `page * PAGE_SIZE + 1` regardless of actual count.

**Fix:**
```tsx
{Math.min(page * PAGE_SIZE + 1, totalElements)}–{Math.min((page + 1) * PAGE_SIZE, totalElements)} of {totalElements} ledgers
```
The upper bound is already correct (`Math.min((page + 1) * PAGE_SIZE, totalElements)`), but the lower bound should be clamped too:
```tsx
{totalElements === 0 ? '0' : `${page * PAGE_SIZE + 1}–${Math.min((page + 1) * PAGE_SIZE, totalElements)}`} of {totalElements} ledgers
```

---

### WR-06: `/masters` route access control diverges from backend — `ROLE_OWNER` blocked from view

**File:** `Client/src/main.tsx:78` and `Client/src/components/AppShell.tsx:12`

**Issue:** The gap-closure fix correctly renamed `ROLE_DATA_ENTRY_OPERATOR` to `ROLE_OPERATOR` in both files, aligning with the `ERole` enum. However, the broader WR-06 gap is not yet resolved: the frontend route guard and sidebar still exclude `ROLE_OWNER`. The backend grants `ROLE_OWNER` access to the `delete` and `onboard` endpoints. An owner who is entitled to delete masters or initiate onboarding is silently redirected to `/dashboard` at the router level and does not see the Masters link in the sidebar.

**Fix:** Add `ROLE_OWNER` to both guards:
```tsx
// main.tsx:78
if (role !== 'ROLE_ACCOUNTANT' && role !== 'ROLE_OPERATOR' && role !== 'ROLE_OWNER') {
  throw redirect({ to: '/dashboard' })
}

// AppShell.tsx:11-12
const canAccessMasters =
  user?.role === 'ROLE_ACCOUNTANT' ||
  user?.role === 'ROLE_OPERATOR' ||
  user?.role === 'ROLE_OWNER'
```

---

### WR-07: `uploadJobId` not set on findings emitted by Phase 3 rules

**File:** `Service/superaccountant/src/main/java/com/arktech/superaccountant/masters/rules/TdsSectionMappingRule.java:35-43` (and `GstApplicabilityRule.java:34-42`, `HsnSacCodeRule.java:33-41`, `GstinPresenceRule.java:30-38`)

**Issue:** The four Phase 3 rules construct `ValidationFinding` objects but never set `uploadJobId`. The `ValidationFinding` entity defines `upload_job_id` as `nullable = false` at the database level. If the `ValidationOrchestrator.runAndPersist` method sets the `uploadJobId` on all findings before persisting, this is fine — but if any rule finding is persisted before the orchestrator injects the job ID, it will cause a `NOT NULL` constraint violation at runtime. Additionally, the `findByUploadJobId` and `findFiltered` repository queries will never return these findings for the correct job if the ID is left null during construction.

This is a latent bug: its impact depends entirely on how `ValidationOrchestrator` operates. Since `ValidationOrchestrator` is not in scope of this review, the rules should defensively document this dependency or the orchestrator's contract should be enforced via a constructor parameter.

**Fix:** Add the job ID injection point explicitly in rule output, or confirm via a comment that the orchestrator always calls `finding.setUploadJobId(job.getId())` before `saveAll`. If the latter, add a comment to each rule:
```java
// Note: uploadJobId is injected by ValidationOrchestrator.runAndPersist() before persistence.
// Do not set it here; it is not available at rule execution time.
```

---

### WR-08: Three independent auto-committed JPQL updates risk partial backfill if any step fails

**File:** `Service/superaccountant/src/main/java/com/arktech/superaccountant/login/config/DataInitializer.java:88-97`

**Issue:** Even setting aside the `@Transactional` self-invocation problem (CR-03), the three JPQL bulk updates are structurally non-atomic. Because the method is not actually transactional (see CR-03), each `executeUpdate()` call is auto-committed in its own database transaction. If the process is killed between the first and second UPDATE (or if the second or third UPDATE throws a `PersistenceException`), the backfill is left in an intermediate state:

- After restart, rows that were `INFO` are now `LOW` and will not match `WHERE f.severity = 'INFO'` again.
- Rows that were `WARNING` still hold `WARNING` because the second UPDATE never committed.
- `WARNING` is a valid `FindingSeverity` constant, so no DB error occurs — the UI's severity filter silently omits them since it only exposes `HIGH`, `MEDIUM`, and `LOW`.

This is compounded by the fact that the method has no guard: unlike the template seed methods, `backfillFindingSeverities()` runs on every startup, even after all rows are already migrated. On steady-state deployments this is harmless (zero rows match), but it issues three UPDATE queries against the `validation_findings` table on every boot.

**Fix:** Resolving CR-03 (wrapping in a real transaction via a separate bean) also resolves the atomicity risk. Additionally, consider adding an idempotency guard to avoid the boot-time query cost after migration is complete:
```java
@Transactional
public int backfillFindingSeverities() {
    // Short-circuit: if no legacy severity values remain, skip
    Long legacyCount = (Long) entityManager.createQuery(
        "SELECT COUNT(f) FROM ValidationFinding f WHERE f.severity IN ('INFO', 'WARNING', 'ERROR')")
        .getSingleResult();
    if (legacyCount == 0) return 0;

    int updated = entityManager.createQuery(
        "UPDATE ValidationFinding f SET f.severity = 'LOW' WHERE f.severity = 'INFO'")
        .executeUpdate();
    updated += entityManager.createQuery(
        "UPDATE ValidationFinding f SET f.severity = 'MEDIUM' WHERE f.severity = 'WARNING'")
        .executeUpdate();
    updated += entityManager.createQuery(
        "UPDATE ValidationFinding f SET f.severity = 'HIGH' WHERE f.severity = 'ERROR'")
        .executeUpdate();
    return updated;
}
```

---

## Info

### IN-01: `FindingSeverity` enum has two parallel severity vocabularies

**File:** `Service/superaccountant/src/main/java/com/arktech/superaccountant/masters/models/FindingSeverity.java:3-9`

**Issue:** The enum defines both `INFO/WARNING/ERROR` (generic logging vocabulary) and `HIGH/MEDIUM/LOW` (triage vocabulary). Rules only use `HIGH/MEDIUM/LOW`. The `INFO/WARNING/ERROR` constants are unused dead code and will confuse future contributors about which vocabulary to use. The frontend `FindingsTab` severity filter only exposes `HIGH`, `MEDIUM`, and `LOW` as options (MastersPage.tsx:519-522), so `INFO/WARNING/ERROR` findings would be unfiltered-to in the UI.

**Fix:** Remove `INFO`, `WARNING`, and `ERROR` from `FindingSeverity` unless there is a planned use-case. Alternatively, add a comment explaining their intended purpose. Note: the new `backfillFindingSeverities()` method in `DataInitializer` migrates existing `INFO/WARNING/ERROR` rows to `LOW/MEDIUM/HIGH`, which implicitly acknowledges these constants should go away — remove them from the enum after confirming the backfill has run on all environments.

---

### IN-02: `buildFinding` delegates to `buildFindingWithFix` — redundant method

**File:** `Service/superaccountant/src/main/java/com/arktech/superaccountant/masters/rules/MismatchDetectionRule.java:121-124`

**Issue:** `buildFinding` does nothing but call `buildFindingWithFix` with identical parameters. It adds no logic and only increases cognitive surface area.

**Fix:** Remove `buildFinding` and call `buildFindingWithFix` directly at all call sites, or rename `buildFindingWithFix` to `buildFinding` and remove the wrapper.

---

### IN-03: `listValidationRules` stub in `PreconfiguredMastersController` returns 404 with a hint string

**File:** `Service/superaccountant/src/main/java/com/arktech/superaccountant/masters/controllers/PreconfiguredMastersController.java:240-246`

**Issue:** The endpoint `GET /api/v1/preconfigured-masters/validation-rules` exists and is `@PreAuthorize("isAuthenticated()")` but returns HTTP 404 with a plain string body. A client calling this endpoint will receive an authenticated 404, which is misleading — 404 implies the resource does not exist, not that it moved. This is a commented TODO in disguise.

**Fix:** Either remove the endpoint entirely (since the real endpoint is in `UploadController`), or use `301 Moved Permanently` / `308 Permanent Redirect`:
```java
return ResponseEntity.status(308)
    .header("Location", "/api/v1/validation-rules")
    .build();
```

---

### IN-04: `handleGstinBlur` in `LedgerMappingPanel` redundantly calls `.toUpperCase()`

**File:** `Client/src/components/LedgerMappingPanel.tsx:83`

**Issue:** `handleGstinBlur` calls `form.gstin.trim().toUpperCase()` to validate the format, but `form.gstin` is already uppercased on every keystroke via `onChange={(e) => handleChange('gstin', e.target.value.toUpperCase())}`. The redundant `.toUpperCase()` in the blur handler is harmless but misleading — it implies the value might not already be uppercase.

**Fix:** Remove the redundant `.toUpperCase()` in `handleGstinBlur` to clarify intent:
```tsx
function handleGstinBlur() {
  const val = form.gstin.trim() // already uppercase from onChange
  // ...
}
```

---

### IN-05: `backfillFindingSeverities()` should be `private`, not package-private

**File:** `Service/superaccountant/src/main/java/com/arktech/superaccountant/login/config/DataInitializer.java:87`

**Issue:** The method declaration `void backfillFindingSeverities()` has no access modifier, making it package-private. It is only called from `run()` within the same class. Package-private visibility exposes the method to any class in the `com.arktech.superaccountant.login.config` package, which is broader than necessary and may cause confusion for future contributors about whether this is part of a package-level contract.

**Fix:**
```java
private void backfillFindingSeverities() {
```

Note: if the intent was to make it testable without reflection, the recommended approach is to extract it to a separate `@Service` bean (as described in CR-03's fix) and test it via the bean's public interface.

---

## Gap-Closure Verification (2026-05-07)

**Files re-examined:** `DataInitializer.java`, `Client/src/main.tsx`, `Client/src/components/AppShell.tsx`

| Gap | Status | Notes |
|-----|--------|-------|
| `ROLE_DATA_ENTRY_OPERATOR` typo in `/masters` route guard (`main.tsx`) | Closed | `ROLE_OPERATOR` matches `ERole.ROLE_OPERATOR` exactly |
| `ROLE_DATA_ENTRY_OPERATOR` typo in `AppShell.tsx` sidebar | Closed | Consistent with `main.tsx` fix |
| `FindingSeverity` dual-vocabulary (`INFO/WARNING/ERROR` vs `HIGH/MEDIUM/LOW`) — partial closure via backfill | Partial | Backfill method added but has transaction bug (CR-03). IN-01 remains: old constants not yet removed from enum |
| `ROLE_OWNER` excluded from `/masters` frontend access (WR-06) | Still open | Role-name typo was fixed, but OWNER exclusion was not addressed |

**New issues found in gap-closure pass:** CR-03 (1 critical), WR-08 (1 warning, closely related to CR-03), IN-05 (1 info)

---

_Reviewed: 2026-05-07T00:00:00Z (gap-closure pass appended same date)_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
