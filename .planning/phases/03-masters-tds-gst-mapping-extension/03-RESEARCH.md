# Phase 3: Masters TDS & GST Mapping Extension — Research

**Researched:** 2026-05-03
**Domain:** Spring Boot JPA entity extension, validation rule pipeline, React side-sheet UI, Indian tax compliance metadata
**Confidence:** HIGH

---

## Summary

Phase 3 extends the existing masters pipeline — which already handles upload → parse → validate → findings for ledger category mismatches — with two new orthogonal metadata dimensions: TDS section mapping and GST applicability classification. The pipeline machinery (ValidationRule interface, ValidationOrchestrator, ValidationFinding entity, ValidationRuleConfig seeding, UploadController) is complete and proven. The only structural changes needed are:

1. Extend the data model: `PreconfiguredMaster` and `ParsedLedger` gain 4 new fields (TDS section, GST applicability type, HSN/SAC code, GSTIN).
2. Add 4 new `ValidationRule` implementations and seed their `ValidationRuleConfig` rows.
3. Extend the `PreconfiguredMastersController` onboard endpoint to support named template slugs (not just `useTemplate: true/false`).
4. Seed 3 named templates (Standard, Simplified, Manufacturing) with fully pre-classified TDS section and GST applicability data.
5. Build the frontend: `MastersPage`, `FindingsPage` (tab on Masters), `LedgerMappingPanel`, and extend `OrganizationSetupPage` with a two-step template selector.

The most important implementation decisions are:

- **FindingSeverity alignment:** The existing enum uses `INFO / WARNING / ERROR`; the UI spec requires `LOW / MEDIUM / HIGH`. This is a breaking divergence. The enum must be extended or renamed before Phase 3 rules can use the severity contract the planner expects.
- **ResolveStatus alignment:** The existing enum has `OPEN / ACKNOWLEDGED / RESOLVED`; the UI spec calls for `ACCEPTED / OVERRIDDEN`. This must be reconciled — either extend the enum or map existing values.
- **Template model:** The current `onboard` endpoint only supports one undifferentiated template (`useTemplate: true`). The UI spec and MSTR-01 require three named templates. The `OnboardRequest` must carry a `templateSlug` field, and DataInitializer must seed 3 distinct template sets.
- **Ledger-level mapping endpoint:** There is no PUT endpoint for per-ledger TDS/GST mapping. `PUT /api/v1/preconfigured-masters/{id}` already exists and its `UpdatePreconfiguredMasterRequest` must be extended with the 4 new fields.

**Primary recommendation:** Execute model extension first (plan 1), then new validation rules (plan 2), then template/onboard expansion (plan 3), then frontend (plan 4), and integration tests last (plan 5). Plan 1 is the dependency foundation for all other plans.

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| MSTR-01 | OPERATOR can onboard using a preconfigured master template; sees all standard Indian accounting ledger groups pre-classified | Onboard endpoint exists; needs named template slugs + 3 seeded template sets with TDS/GST fields populated |
| MSTR-02 | Validation pipeline flags ledgers with unexpected/missing category as findings with HIGH/MEDIUM/LOW severity | FindingSeverity enum must be extended to include HIGH/MEDIUM/LOW (currently INFO/WARNING/ERROR); rule implementations needed |
| MSTR-03 | Every ledger has a TDS section assigned or is explicitly marked "not subject to TDS" | New `tdsSection` field on `PreconfiguredMaster` + `ParsedLedger`; `TdsSectionMappingRule` ValidationRule |
| MSTR-04 | Every sales/income ledger has GST applicability type + HSN/SAC code; missing codes flagged | New `gstApplicabilityType` + `hsnSacCode` fields; `GstApplicabilityRule` + `HsnSacCodeRule` |
| MSTR-05 | Every purchase ledger linked to registered dealer has GSTIN; missing GSTIN is HIGH finding blocking GSTR-2B | New `gstin` field on PreconfiguredMaster; `GstinPresenceRule` |
| MSTR-06 | Masters view shows TDS section column for every ledger | Frontend `MastersPage` table column spec is fully defined in 03-UI-SPEC.md |
| MSTR-07 | OPERATOR can resolve findings by accepting or overriding the suggested category | Resolve endpoint already exists; ResolveStatus enum must add ACCEPTED/OVERRIDDEN values |
| MSTR-08 | Bulk and per-ledger mapping endpoints exposed; integration tests assert finding counts for known fixture files | PUT /api/v1/preconfigured-masters/{id} needs new fields; bulk endpoint exists; new fixture + IT tests needed |
</phase_requirements>

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| TDS section / GST applicability storage | Database / Storage | — | Persisted on PreconfiguredMaster entity; Hibernate manages schema |
| Validation rule execution | API / Backend | — | ValidationOrchestrator calls rules server-side; findings persisted |
| Per-ledger mapping save | API / Backend | — | PUT /api/v1/preconfigured-masters/{id} |
| Finding resolution (accept/override) | API / Backend | — | PATCH /api/v1/uploads/{jobId}/mismatches/{findingId}/resolve |
| Template onboarding | API / Backend | — | POST /api/v1/preconfigured-masters/onboard |
| Masters table display | Frontend Server (SSR) | Browser / Client | React SPA; data fetched from API via Axios |
| Findings tab / review | Browser / Client | — | Interactive state (expand/collapse, accept/override) is client-side |
| LedgerMappingPanel side sheet | Browser / Client | — | Right-anchored panel is pure client-side interaction |
| Template selector (Step 2 of org setup) | Browser / Client | — | Modal step after org creation |
| Route guard for /masters | Browser / Client | — | TanStack Router beforeLoad check on user.role |

---

## Existing Code Inventory

All items below are [VERIFIED: direct file read].

### Entities Requiring Extension

**`PreconfiguredMaster`** — `Service/.../masters/models/PreconfiguredMaster.java`

Current fields:
- `id` (UUID), `organizationId` (UUID), `ledgerName` (String), `category` (LedgerCategory), `expectedParentGroup` (String), `expectedGstApplicable` (Boolean), `expectedTdsApplicable` (Boolean), `active` (boolean), `template` (boolean), `createdAt`, `updatedAt`

**4 new columns to add for Phase 3:**
- `tds_section` — TDS section code string (e.g., "194C", "NOT_SUBJECT") or null
- `gst_applicability_type` — enum or string (TAXABLE / EXEMPT / ZERO_RATED / NON_GST / RCM / NOT_APPLICABLE)
- `hsn_sac_code` — HSN or SAC code string (4–8 digits), nullable
- `gstin` — vendor GSTIN string (15 chars), nullable (only on PURCHASE ledgers linked to registered dealers)

**`ParsedLedger`** — `Service/.../masters/classifier/ParsedLedger.java`

This is a DTO/value object (not a JPA entity — uses `@Builder @Data`, no `@Entity`). It carries what was parsed from the upload. Currently has: `name`, `parentGroup`, `guid`, `gstApplicable` (Boolean), `tdsApplicable` (Boolean), `category`.

Phase 3 needs 4 more fields here too (to carry uploaded ledger's TDS/GST metadata into rule execution):
- `tdsSection` — from JSON field (Tally does not export TDS section directly, so this will always be null from the upload; rules compare against what PreconfiguredMaster expects)
- `gstApplicabilityType` — same note; Tally JSON has `taxtype: "GST"` flag only, not fine-grained type
- `hsnSacCode` — Tally JSON may export HSN code under a field name to be determined
- `gstin` — Tally JSON may export GSTIN on ledger objects under a field name to be determined

**Important constraint:** The Tally masters JSON currently only captures `taxtype` (for GST flag) and `istdsapplicable` (for TDS flag). The granular fields (TDS section, GST applicability type, HSN/SAC, GSTIN) are NOT currently parsed from the upload. The validation rules for Phase 3 will therefore check the `PreconfiguredMaster` configuration (what the org has set) against expected values — not against values parsed from Tally. For MSTR-03/04/05, the finding is: "this PreconfiguredMaster entry has no tdsSection set" — not "the uploaded ledger has a different tdsSection than expected."

This means the Phase 3 rules are metadata completeness checks on the PreconfiguredMaster table, not structural comparisons like MismatchDetectionRule. The ValidationContext already carries `preconfiguredMasters`, so the rules have access to the full configured list.

### Enums with Alignment Issues

**`FindingSeverity`** — current: `INFO, WARNING, ERROR`

UI spec, MSTR-02 success criterion, and the Finding Review panel all use `HIGH / MEDIUM / LOW`. The current rule implementations use `WARNING`, `ERROR`, `INFO`. Phase 3 must:
- Either add `HIGH, MEDIUM, LOW` values to the enum alongside existing ones
- Or rename existing values (breaking change if any live data stores old strings)

**Recommendation:** Add `HIGH, MEDIUM, LOW` as new enum values. Keep `INFO, WARNING, ERROR` for backward compatibility (existing `MISMATCH_DETECTION` findings in the DB). New Phase 3 rules exclusively use `HIGH / MEDIUM / LOW`. Update `ValidationFindingRepository.findFiltered` to accept the new severity values. The filter API parameter and the FindingResponse serialization use the same enum, so the existing logic carries over.

**`ResolveStatus`** — current: `OPEN, ACKNOWLEDGED, RESOLVED`

UI spec and MSTR-07 use `ACCEPTED / OVERRIDDEN`. The UI spec's interaction contract calls `PATCH` with `{status: "ACCEPTED"}` and `{status: "OVERRIDDEN"}`.

**Recommendation:** Add `ACCEPTED, OVERRIDDEN` to the enum. Keep `ACKNOWLEDGED, RESOLVED` for backward compatibility. Update `ResolveRequest` validation to accept the new values. The `UploadController.resolveFinding` currently rejects re-setting to `OPEN` — extend that guard to also handle the new values correctly.

### Validation Rule Infrastructure

The `ValidationOrchestrator` auto-discovers all `ValidationRule` Spring beans by collecting `List<ValidationRule> rules` in its constructor — any `@Component` implementing the interface is automatically registered. No manual wiring needed; just annotate the new rule classes with `@Component`.

Each new rule needs:
1. A `@Component` class implementing `ValidationRule`
2. A `ValidationRuleConfig` row seeded in `DataInitializer.seedValidationRules()` (or separate seeder method)
3. An `execution_order` integer (MISMATCH_DETECTION is order 1; new rules should be 2–5)

### Existing Controller & Repository Inventory

**`PreconfiguredMastersController`:**
- `GET /api/v1/preconfigured-masters` — lists paginated, filter by category
- `POST /api/v1/preconfigured-masters` — create single
- `PUT /api/v1/preconfigured-masters/{id}` — update (partial update via null-check)
- `DELETE /api/v1/preconfigured-masters/{id}` — soft delete
- `POST /api/v1/preconfigured-masters/bulk` — bulk import (array of CreatePreconfiguredMasterRequest)
- `POST /api/v1/preconfigured-masters/onboard` — apply template to org

The `onboard` endpoint currently: checks `existsByOrganizationId(orgId)` and rejects if org already has masters; if `useTemplate: true`, copies all `is_template=true` rows (one undifferentiated set) for the org.

**Phase 3 changes needed to `/onboard`:**
- `OnboardRequest` must carry `templateSlug` (String: "standard", "simplified", "manufacturing") instead of or in addition to `useTemplate`
- The copy loop must select templates filtered by slug/name
- DataInitializer must seed 3 distinct template sets with `template_slug` or similar discriminator

The cleanest approach without a schema redesign: add a `templateSlug` column to `preconfigured_masters` (nullable, only set on template rows), and change the seeder to populate it. The `onboard` endpoint then filters `findByTemplateTrueAndTemplateSlug(slug)`.

**`PreconfiguredMasterRepository`:**
- `findByOrganizationIdAndActiveTrue(UUID)` — list
- `findByOrganizationIdAndActiveTrue(UUID, Pageable)` — paged list
- `findByOrganizationIdAndActiveTrueAndCategory(UUID, LedgerCategory, Pageable)` — filtered paged
- `existsByOrganizationId(UUID)` — onboard guard
- `findByTemplateTrue()` — current template fetch (returns all templates, no slug filter)

New methods needed:
- `findByTemplateTrueAndTemplateSlug(String slug)` — for named template onboarding
- `findByOrganizationIdAndActiveTrue(UUID, String ledgerName)` — for per-ledger lookup in mapping update (or use `findById`)

**`UploadController.resolveFinding`:**
The existing resolve endpoint: `PATCH /api/v1/uploads/{jobId}/mismatches/{findingId}/resolve`

This is the endpoint the UI calls for both Accept Fix and Override Value actions. It already handles the full resolve flow (sets `resolveStatus`, `resolveNote`, `resolvedBy`, `resolvedAt`). Phase 3 just needs `ResolveStatus` to include `ACCEPTED` and `OVERRIDDEN`, and the controller to accept those values.

Current guard on re-setting to OPEN:
```java
if (request.getStatus() == ResolveStatus.OPEN) {
    return ResponseEntity.badRequest().body("Cannot set status back to OPEN.");
}
```
This remains valid. No other changes to the controller logic are required.

**`ValidationFindingRepository.findFiltered`:**

Current JPQL query:
```java
"AND (:severity IS NULL OR f.severity = :severity) " +
"AND (:showResolved = true OR f.resolveStatus <> com.arktech.superaccountant.masters.models.ResolveStatus.RESOLVED)"
```

The `showResolved` condition checks for `RESOLVED` status. Phase 3 adds `ACCEPTED` and `OVERRIDDEN` as resolved-equivalent statuses. The query must be updated to exclude `ACCEPTED` and `OVERRIDDEN` when `showResolved = false`:
```java
"AND (:showResolved = true OR (f.resolveStatus = com.arktech.superaccountant.masters.models.ResolveStatus.OPEN OR f.resolveStatus = com.arktech.superaccountant.masters.models.ResolveStatus.ACKNOWLEDGED))"
```

### Existing Request/Response DTOs

**`CreatePreconfiguredMasterRequest`** — fields: `ledgerName`, `category`, `expectedParentGroup`, `expectedGstApplicable`, `expectedTdsApplicable`

Phase 3 adds: `tdsSection` (String), `gstApplicabilityType` (String or new enum), `hsnSacCode` (String), `gstin` (String)

**`UpdatePreconfiguredMasterRequest`** — same fields as Create (currently)

Phase 3 adds the same 4 fields. The controller's null-check partial update pattern already handles optional fields — add null-checks for each new field.

**`PreconfiguredMasterResponse`** — fields: `id`, `ledgerName`, `category`, `expectedParentGroup`, `expectedGstApplicable`, `expectedTdsApplicable`, `active`, `createdAt`, `updatedAt`

Phase 3 adds: `tdsSection`, `gstApplicabilityType`, `hsnSacCode`, `gstin`

**`OnboardRequest`** — current: `useTemplate` (boolean)

Phase 3 change: Add `templateSlug` (String) — when present and non-blank, overrides `useTemplate`; when `templateSlug` is null and `useTemplate` is false, custom setup.

### Frontend State

**`authStore.ts`** — `user.role` is stored as raw string (e.g., `"ROLE_OPERATOR"`). The Masters nav visibility guard in the UI spec uses `user?.role === 'ROLE_ACCOUNTANT' || user?.role === 'ROLE_OPERATOR'`. This matches the existing `role` format in `AuthUser` — no store changes needed.

**`main.tsx`** — routes are manually defined (not file-based). The `masterse` route (`/masters`) must be added here with `beforeLoad` auth + role guard.

**`api.ts`** — Axios instance, base URL `/api`, auth header injected from store. All new API calls use this instance. No changes needed.

**`OrganizationSetupPage.tsx`** — currently: single form + `window.alert` + `navigate('/dashboard')` on success. Phase 3 changes: replace the `window.alert` + redirect with a step transition to the template selector.

---

## Standard Stack

### Core (all already in project — no new dependencies needed)

| Library | Version | Purpose | Status |
|---------|---------|---------|--------|
| Spring Boot | 4.0.2 | Framework, JPA, Security, Web | In use |
| Spring Data JPA / Hibernate | via Boot 4 | Entity extension, `ddl-auto: update` | In use |
| Lombok | via Boot 4 | `@Data`, `@Builder`, `@NoArgsConstructor` | In use |
| Jakarta Validation | via Boot 4 | `@NotBlank`, `@Pattern` on request DTOs | In use |
| Jackson | via Boot 4 | JSON serialization of new enum values | In use |
| React 19 | current | Frontend | In use |
| Tailwind CSS 4 | current | Styling via `@tailwindcss/vite` | In use |
| TanStack Router | current | Route guard for `/masters` | In use |
| Zustand | current | `user.role` for nav visibility guard | In use |
| Lucide React | current | `Database`, `Upload`, `CheckCircle2`, `Filter`, `SearchX`, `Loader2`, `X` icons | In use |
| Axios | current | API calls from new pages/components | In use |

**No new dependencies required for Phase 3.** All libraries needed are already in the project.

### Frontend validation (client-side only — no new library)

HSN/SAC code: regex `/^\d{4,8}$/` — inline validation on blur in `LedgerMappingPanel`
GSTIN: reuse `GSTIN_REGEX` from `OrganizationSetupPage.tsx` — import or duplicate the constant

---

## Architecture Patterns

### System Architecture Diagram

```
POST /api/v1/preconfigured-masters/onboard
  │
  ├── Reads template rows by slug from preconfigured_masters (is_template=true)
  ├── Copies rows for org (organizationId = orgId, is_template=false)
  └── Returns count

POST /api/v1/uploads  (file upload — existing)
  │
  ├── TallyParserService.parseMastersJson() → List<ParsedLedger>
  ├── ValidationOrchestrator.runAndPersist()
  │     ├── MISMATCH_DETECTION rule (existing) → findings
  │     ├── TDS_SECTION_MAPPING rule (new) → HIGH/MEDIUM findings
  │     ├── GST_APPLICABILITY rule (new) → MEDIUM findings
  │     ├── HSN_SAC_CODE rule (new) → MEDIUM findings
  │     └── GSTIN_PRESENCE rule (new) → HIGH findings
  └── Persists ValidationFinding rows, updates UploadJob status

GET /api/v1/preconfigured-masters?page=0&size=50
  └── Returns paginated PreconfiguredMasterResponse (with new tdsSection, gstApplicabilityType, hsnSacCode, gstin fields)

PUT /api/v1/preconfigured-masters/{id}  (update mapping)
  └── Partial update: sets tdsSection, gstApplicabilityType, hsnSacCode, gstin

GET /api/v1/uploads/{jobId}/mismatches?showResolved=false
  └── Returns paginated findings (includes new rule codes)

PATCH /api/v1/uploads/{jobId}/mismatches/{findingId}/resolve
  └── Sets resolveStatus = ACCEPTED or OVERRIDDEN (new values)

Frontend /masters
  ├── MastersPage — fetches GET /api/v1/preconfigured-masters → renders table
  │     ├── "Ledgers" tab (default)
  │     └── "Findings" tab — fetches GET /api/v1/uploads/{latestJobId}/mismatches
  │           ├── Accept Fix → PATCH resolve {status: "ACCEPTED"}
  │           └── Override Value → expand form → PATCH resolve {status: "OVERRIDDEN", note: "..."}
  └── LedgerMappingPanel (side sheet) — PUT /api/v1/preconfigured-masters/{id}

Frontend /organization/setup (modified)
  ├── Step 1: existing org creation form
  └── Step 2: template selector → POST /api/v1/preconfigured-masters/onboard {templateSlug: "standard"}
```

### Recommended Project Structure (new files only)

```
Service/.../masters/
  models/
    GstApplicabilityType.java     # new enum: TAXABLE, EXEMPT, ZERO_RATED, NON_GST, RCM, NOT_APPLICABLE
    TdsSection.java               # new enum: NOT_SUBJECT, 194C, 194J_A, 194J_B, 194H, 194I, 194Q, 194A, 194B, 194D, 194M, OTHER
  rules/
    TdsSectionMappingRule.java    # new ValidationRule
    GstApplicabilityRule.java     # new ValidationRule
    HsnSacCodeRule.java           # new ValidationRule
    GstinPresenceRule.java        # new ValidationRule

Client/src/
  pages/
    MastersPage.tsx               # new — /masters route
  components/
    LedgerMappingPanel.tsx        # new — side sheet for per-ledger mapping edit
```

`FindingsPage` is a tab within `MastersPage`, not a separate file (per UI spec). It can be an inline component or a separate file `FindingsTab.tsx` under `components/` — the planner can decide.

### Pattern 1: Adding Enum Values with Hibernate ddl-auto:update

**What:** `ddl-auto: update` adds new columns and modifies column definitions but does NOT rename or remove enum constraint values in PostgreSQL. PostgreSQL stores enums as varchar strings by default (since `@Enumerated(EnumType.STRING)` is used throughout). Adding new enum Java values is safe — Hibernate will allow the new string values to be persisted; no DDL change is needed for the varchar column.

**When to use:** All new enum values (`HIGH`, `LOW`, `MEDIUM` on `FindingSeverity`; `ACCEPTED`, `OVERRIDDEN` on `ResolveStatus`; the entire new `GstApplicabilityType` and `TdsSection` enums) are safe to add without migration scripts.

**Verification:** `@Enumerated(EnumType.STRING)` is confirmed on `ValidationFinding.severity`, `ValidationFinding.resolveStatus`, `ValidationFinding.category`. The columns are `VARCHAR` in PostgreSQL, not native PG enums.

[VERIFIED: read ValidationFinding.java, ResolveStatus.java, FindingSeverity.java]

### Pattern 2: New ValidationRule Implementation

**What:** Implement `ValidationRule` interface, annotate with `@Component`, return findings from `execute()`.

**When to use:** For all 4 new rules. The orchestrator auto-collects all `ValidationRule` beans — no extra wiring needed.

**Example pattern (from MismatchDetectionRule):**
```java
// [VERIFIED: read MismatchDetectionRule.java]
@Component("TDS_SECTION_MAPPING")
public class TdsSectionMappingRule implements ValidationRule {

    @Override
    public String getRuleCode() { return "TDS_SECTION_MAPPING"; }

    @Override
    public List<ValidationFinding> execute(ValidationContext context, List<ParsedLedger> parsedLedgers) {
        List<ValidationFinding> findings = new ArrayList<>();
        for (PreconfiguredMaster master : context.preconfiguredMasters()) {
            if (master.getTdsSection() == null) {
                ValidationFinding f = new ValidationFinding();
                f.setRuleCode(getRuleCode());
                f.setLedgerName(master.getLedgerName());
                f.setCategory(master.getCategory());
                f.setSeverity(FindingSeverity.MEDIUM); // or HIGH based on category
                f.setResolveStatus(ResolveStatus.OPEN);
                f.setMessage("Ledger '" + master.getLedgerName() + "' has no TDS section assigned.");
                f.setSuggestedFix("Assign a TDS section or mark as 'Not Subject to TDS'.");
                findings.add(f);
            }
        }
        return findings;
    }
}
```

Note: Phase 3 rules iterate over `context.preconfiguredMasters()` (the configured list), NOT `parsedLedgers`. The missing metadata is in the master configuration — it cannot come from the Tally upload. Rule invocation happens when an XML/JSON file is uploaded, which means a fresh upload triggers the completeness check on the org's master list.

[ASSUMED: Rules run against configured masters, not uploaded ledgers, since Tally JSON does not export TDS section, GST applicability type, HSN/SAC, or GSTIN per ledger. Confirm with user if this assumption should change.]

### Pattern 3: Partial Update (null-check pattern)

**What:** The existing `PUT /api/v1/preconfigured-masters/{id}` uses null-checks to only update provided fields.

**When to use:** Extend `UpdatePreconfiguredMasterRequest` with the 4 new fields (all nullable), extend the controller's `.map()` block with 4 more null-checks.

```java
// [VERIFIED: read PreconfiguredMastersController.java line 94-104]
if (request.getTdsSection() != null) master.setTdsSection(request.getTdsSection());
if (request.getGstApplicabilityType() != null) master.setGstApplicabilityType(request.getGstApplicabilityType());
if (request.getHsnSacCode() != null) master.setHsnSacCode(request.getHsnSacCode());
if (request.getGstin() != null) master.setGstin(request.getGstin());
```

### Pattern 4: TanStack Router route with role guard

**What:** Add `/masters` route to `main.tsx` with `beforeLoad` checking both `isAuthenticated` and `user.role`.

**Example (matching existing pattern):**
```typescript
// [VERIFIED: read main.tsx]
const mastersRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/masters',
  beforeLoad: () => {
    const { isAuthenticated, user } = useAuthStore.getState()
    if (!isAuthenticated) throw redirect({ to: '/login' })
    const allowed = user?.role === 'ROLE_ACCOUNTANT' || user?.role === 'ROLE_OPERATOR'
    if (!allowed) throw redirect({ to: '/dashboard' })
  },
  component: MastersPage,
})
```

### Pattern 5: OrganizationSetupPage two-step flow

**What:** Replace `window.alert + navigate` with local state to render Step 2 after successful org creation.

```typescript
// [ASSUMED: target state — based on current code and UI spec]
const [step, setStep] = useState<1 | 2>(1)
const [createdOrgId, setCreatedOrgId] = useState<string | null>(null)

async function handleSubmit(e) {
  // ... existing API call ...
  const response = await api.post('/organizations', { ...form })
  setCreatedOrgId(response.data.id)  // [ASSUMED: API returns org ID in response]
  setStep(2)  // proceed to template selector
}
```

**Open question:** Does `POST /api/organizations` currently return the created org's `id` in the response body? Reading `OrganizationController.java` is needed to confirm. [ASSUMED: it does, based on REST conventions and the fact that the onboard endpoint needs an orgId — but this must be verified by the planner reading OrganizationController.]

### Anti-Patterns to Avoid

- **Running new rules against `parsedLedgers` for fields Tally doesn't export:** The 4 new rules check master configuration completeness, not upload-vs-config comparison. Attempting to read `tdsSection` from a `ParsedLedger` (which gets it from Tally JSON) will always return null since Tally doesn't export this metadata. The rule logic must iterate `context.preconfiguredMasters()`.
- **Storing TDS section/GST type as raw String columns without enum typing:** Using Java enums on the entity ensures invalid values cannot be persisted. Use `@Enumerated(EnumType.STRING)` on the new `TdsSection` and `GstApplicabilityType` fields. Null = "not yet set" is a valid state.
- **Overloading `MismatchType` for the new rule findings:** The new findings don't fit any existing `MismatchType`. It's acceptable for Phase 3 findings to have `mismatchType = null` and use `ruleCode` as the primary discriminator. The UI spec uses `ruleCode` for the "Rule" filter dropdown.
- **Breaking the `existsByOrganizationId` onboard guard:** The guard rejects second onboarding. This is intentional — don't bypass it when adding template slug support. The UI's "Skip for now" action calls `navigate('/dashboard')` without an API call, which is the correct approach when an org already has masters.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| GSTIN format validation | Custom regex method | Reuse `GSTIN_REGEX` constant from `OrganizationSetupPage.tsx` (or extract to shared util) | Already proven, correct format per STATE.md locked decisions |
| HSN/SAC code validation | Complex validator | Inline regex `/^\d{4,8}$/` on blur (per UI spec) | HSN = 4/6/8 digit, SAC = 6 digit; 4-8 digit range covers both |
| TDS section list | Hardcode inline | `TdsSection` enum (Java) + select options in LedgerMappingPanel (TypeScript) | Enum-as-string means the list is declared once; UI options match enum values |
| Rule discovery/wiring | Manual `ruleMap.put()` | Keep existing constructor injection pattern in `ValidationOrchestrator` | Already auto-discovers all `ValidationRule` beans via Spring's `List<ValidationRule>` injection |
| Pagination on findings list | Custom cursor/offset | Use existing `PageRequest` + `Pageable` pattern | Already implemented in `findFiltered`; just use the same pattern |

---

## Common Pitfalls

### Pitfall 1: FindingSeverity mismatch between rules and UI filter
**What goes wrong:** New rules emit `HIGH`, `MEDIUM`, `LOW` findings. Existing `MismatchDetectionRule` emits `INFO`, `WARNING`, `ERROR`. The severity filter dropdown in the UI spec shows `HIGH / MEDIUM / LOW` only. If the Findings tab uses a severity filter, `INFO/WARNING/ERROR` findings from MismatchDetectionRule will never match the filter and appear to disappear.
**Why it happens:** Phase 1/2 used the old severity labels; Phase 3 introduces new ones.
**How to avoid:** The Findings tab filter must include all severity values that exist in the DB, or the UI must map old→new (INFO→LOW, WARNING→MEDIUM, ERROR→HIGH). The cleanest solution: update `MismatchDetectionRule` to use `HIGH/MEDIUM/LOW` at the same time Phase 3 rules are added, and migrate existing `validation_findings` rows to the new severity values. Include a SQL migration for any live findings data.
**Warning signs:** Findings from MISMATCH_DETECTION rule disappear when applying severity filters in the UI.

### Pitfall 2: Template onboard guard blocks re-application
**What goes wrong:** `existsByOrganizationId(orgId)` returns true if the org already has any preconfigured master row (even from a previous partial onboard or manual creation). The onboard endpoint returns 400. A user who "skipped" the template step during org setup and tries to apply a template later from the Masters page will be blocked.
**Why it happens:** The guard was designed for initial onboarding; it doesn't account for "apply template to an org with no masters yet but that has been set up before."
**How to avoid:** The plan must address whether the Masters page needs a "Apply Template" button for orgs that skipped setup. The UI spec says "You can customize individual ledger mappings later" — implying a re-apply path exists. Consider changing the guard to `existsByOrganizationIdAndTemplateFalse(orgId)` or removing the guard entirely for template applies (templates are idempotent seed operations).
**Warning signs:** Template Apply button on Masters page returns 400 for any org that has ever had a master record.

### Pitfall 3: ValidationOrchestrator runs all rules on every upload, including rules checking master completeness
**What goes wrong:** If an org has zero `PreconfiguredMaster` rows (new org, never onboarded), the completeness rules (TDS_SECTION_MAPPING etc.) produce zero findings — not because the masters are complete, but because there are no masters to check. The UI would show "All findings resolved" when it should show "No masters configured yet."
**Why it happens:** Rules iterate `context.preconfiguredMasters()` which is empty for a new org.
**How to avoid:** Rules should emit a single HIGH finding when `context.preconfiguredMasters().isEmpty()` — "No preconfigured masters found. Apply a template to classify your ledgers." Alternatively, the UI gates the Findings tab behind an "upload required first" state check. Discuss with user which behavior is preferred.
**Warning signs:** Empty findings list for an org that has never been configured, looking identical to a fully-resolved org.

### Pitfall 4: `ddl-auto: update` adds new nullable columns but not enum constraints
**What goes wrong:** Adding `tdsSection` as `@Enumerated(EnumType.STRING)` with nullable column — Hibernate adds a `VARCHAR` column, which works. But if the DB already has rows in `preconfigured_masters` (from the existing Construction template seed), those rows will have `NULL` for the new columns. The update operation runs at startup — all existing rows get null values, which is valid. No action needed, but the planner should be aware that all seeded template rows will have null TDS/GST fields until DataInitializer is updated.
**Why it happens:** `ddl-auto: update` only runs `ALTER TABLE ADD COLUMN` — it doesn't backfill existing rows.
**How to avoid:** DataInitializer's `seedConstructionTemplate()` must be updated to set TDS section, GST applicability, HSN/SAC, and GSTIN on each template ledger. The guard `if (templateCount > 0) return;` will prevent re-seeding if templates already exist. The plan must include a SQL migration to backfill the new columns on existing template rows if the DB is already seeded.
**Warning signs:** Template rows exist in DB but have null tdsSection / gstApplicabilityType after migration.

### Pitfall 5: `ValidationFindingRepository.findFiltered` excludes resolved findings incorrectly
**What goes wrong:** The `showResolved=false` condition currently excludes `ResolveStatus.RESOLVED`. After adding `ACCEPTED` and `OVERRIDDEN`, findings resolved via Phase 3 actions will still show as "open" in the findings list because the JPQL query only excludes `RESOLVED`.
**Why it happens:** The JPQL was written before `ACCEPTED`/`OVERRIDDEN` existed.
**How to avoid:** Update the `findFiltered` query to treat `ACCEPTED` and `OVERRIDDEN` as "resolved" in the `showResolved=false` condition.
**Warning signs:** Accepted/overridden findings continue to appear in the open findings list; open count badge on the Findings tab doesn't decrement after resolution.

### Pitfall 6: Side sheet focus trap and keyboard navigation
**What goes wrong:** The `LedgerMappingPanel` side sheet must trap focus when open (per accessibility spec in UI-SPEC). React's default rendering puts the panel's DOM nodes at the end of the tree, breaking tab order if focus trap is not implemented.
**Why it happens:** Focus management is not automatic in React.
**How to avoid:** Use a `useEffect` to set focus to the first focusable element (the panel's close button or first form field) on open. Implement a simple tab-trap by catching `Tab` key events at the panel boundary. On close, return focus to the triggering "Edit" button (hold a ref to it when opening).
**Warning signs:** Tab key leaves the panel while it's open; screen readers can reach background content.

### Pitfall 7: TanStack Router route registration order
**What goes wrong:** The `/masters` route is not added to the `routeTree` in `main.tsx`. Navigating to `/masters` shows a 404 or renders the wrong component.
**Why it happens:** TanStack Router with manual route definitions requires explicit `routeTree.addChildren([..., mastersRoute])`.
**How to avoid:** Add `mastersRoute` to both the route definition section and the `addChildren` call in `main.tsx`.

---

## Code Examples

### New Enum: TdsSection

```java
// [ASSUMED: target; modeled after UI spec dropdown options in 03-UI-SPEC.md]
public enum TdsSection {
    NOT_SUBJECT,   // "Not Subject to TDS"
    Section_194C,  // Contractors & Sub-contractors
    Section_194J_A, // Technical Services
    Section_194J_B, // Professional Services
    Section_194H,  // Commission & Brokerage
    Section_194I,  // Rent
    Section_194Q,  // Purchase of Goods
    Section_194A,  // Interest
    Section_194B,  // Winnings
    Section_194D,  // Insurance Commission
    Section_194M,  // Payments by Individuals/HUF
    OTHER
}
```

Note: Java enum names cannot start with a digit. Use `Section_194C` or rename to `TDS_194C`. The API response serializes the enum name as a string — the frontend must map `"TDS_194C"` to display text "194C — Contractors & Sub-contractors". Alternatively, use a String column instead of an enum, and validate via `@Pattern` in the request DTO. String column is simpler and avoids the digit-leading naming issue.

**Recommendation:** Use `String tdsSection` (not enum) on the entity, validated via `@Pattern` or custom validator in the DTO. This matches the UI spec's `"194C"`, `"194J_A"`, `"NOT_SUBJECT"` string values directly. Similarly for `gstApplicabilityType`.

### New `GstApplicabilityType` enum

```java
// [ASSUMED: target; modeled after UI spec options]
public enum GstApplicabilityType {
    TAXABLE,
    EXEMPT,
    ZERO_RATED,
    NON_GST,
    RCM,
    NOT_APPLICABLE
}
```

This is safe as an enum (no digit-leading names). Use `@Enumerated(EnumType.STRING)` on the entity.

### Extended `PreconfiguredMaster` fields

```java
// [ASSUMED: target; extends existing entity]
@Column(name = "tds_section")          // VARCHAR, nullable
private String tdsSection;

@Enumerated(EnumType.STRING)
@Column(name = "gst_applicability_type")  // VARCHAR, nullable
private GstApplicabilityType gstApplicabilityType;

@Column(name = "hsn_sac_code")         // VARCHAR(8), nullable
private String hsnSacCode;

@Column(name = "gstin")               // VARCHAR(15), nullable
private String gstin;
```

### TdsSectionMappingRule — severity logic

TDS section is missing → severity depends on category:
- PURCHASE / EXPENSE / INCOME → MEDIUM (likely TDS-applicable)
- TDS category → HIGH (TDS ledger without a section is a critical gap)
- GST, OTHER → LOW (GST ledgers typically not subject to TDS)

```java
// [ASSUMED: severity assignment based on MSTR-03 and Phase 3 success criterion 3]
FindingSeverity severity = switch (master.getCategory()) {
    case TDS -> FindingSeverity.HIGH;
    case PURCHASE, EXPENSE, INCOME -> FindingSeverity.MEDIUM;
    default -> FindingSeverity.LOW;
};
```

### GstinPresenceRule — HIGH severity for purchase ledgers

```java
// [ASSUMED: based on MSTR-05 — missing GSTIN on purchase ledger blocks GSTR-2B]
if (master.getCategory() == LedgerCategory.PURCHASE && master.getGstin() == null) {
    f.setSeverity(FindingSeverity.HIGH);
    f.setMessage("Purchase ledger '" + master.getLedgerName() + "' has no GSTIN. Required for GSTR-2B reconciliation.");
}
```

### DataInitializer — seedValidationRules extension

```java
// [ASSUMED: target; extends existing seedValidationRules method]
private void seedValidationRuleIfAbsent(String code, String name, String description, int order) {
    if (validationRuleConfigRepository.findByRuleCode(code).isEmpty()) {
        ValidationRuleConfig rule = new ValidationRuleConfig();
        rule.setRuleCode(code);
        rule.setRuleName(name);
        rule.setDescription(description);
        rule.setActive(true);
        rule.setExecutionOrder(order);
        validationRuleConfigRepository.save(rule);
    }
}
// Call for each new rule in run():
seedValidationRuleIfAbsent("TDS_SECTION_MAPPING", "TDS Section Mapping", "...", 2);
seedValidationRuleIfAbsent("GST_APPLICABILITY", "GST Applicability", "...", 3);
seedValidationRuleIfAbsent("HSN_SAC_CODE", "HSN/SAC Code Presence", "...", 4);
seedValidationRuleIfAbsent("GSTIN_PRESENCE", "GSTIN Presence", "...", 5);
```

Note: `ValidationRuleConfigRepository` needs a new `findByRuleCode(String)` method — or use `existsByRuleCode()`.

### Frontend: Masters route with role guard

```typescript
// [ASSUMED: target; follows existing pattern in main.tsx]
const mastersRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/masters',
  beforeLoad: () => {
    const { isAuthenticated, user } = useAuthStore.getState()
    if (!isAuthenticated) throw redirect({ to: '/login' })
    const role = user?.role ?? ''
    if (role !== 'ROLE_ACCOUNTANT' && role !== 'ROLE_OPERATOR') {
      throw redirect({ to: '/dashboard' })
    }
  },
  component: MastersPage,
})
```

---

## State of the Art

| Old Approach | Current Approach | Notes |
|--------------|------------------|-------|
| Boolean `expectedGstApplicable` / `expectedTdsApplicable` on PreconfiguredMaster | Boolean (Phase 1/2) + TdsSection string + GstApplicabilityType enum (Phase 3) | Phase 3 makes the flags granular — the booleans remain for backward compat with MismatchDetectionRule |
| `FindingSeverity.INFO/WARNING/ERROR` | Add `HIGH/MEDIUM/LOW`; keep old values for existing data | Phase 3 UI uses HIGH/MEDIUM/LOW exclusively; MismatchDetectionRule findings use the old values |
| Single undifferentiated "Construction" template | 3 named templates: Standard, Simplified, Manufacturing | Template slug field needed on preconfigured_masters |
| `onboard` endpoint: `{useTemplate: true/false}` | `{templateSlug: "standard" | "simplified" | "manufacturing"}` | Backward compat: if `templateSlug` absent and `useTemplate: true`, use the legacy template |
| `ResolveStatus.RESOLVED` | Add `ACCEPTED`, `OVERRIDDEN`; keep `RESOLVED`, `ACKNOWLEDGED`, `OPEN` | UI spec uses ACCEPTED/OVERRIDDEN; existing data uses RESOLVED |

**Deprecated/outdated:**
- `window.alert` in `OrganizationSetupPage.tsx`: removed in Phase 3, replaced by step transition. [VERIFIED: read OrganizationSetupPage.tsx line 79]
- No-sidebar `DashboardPage` layout: Phase 3 adds the sidebar (per UI spec and Phase 2 spec deferred the Masters nav item to Phase 3). DashboardPage needs to be refactored to include the sidebar shell.

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Phase 3 validation rules check PreconfiguredMaster completeness (not ParsedLedger vs. master comparison) because Tally JSON does not export TDS section/GST type/HSN-SAC/GSTIN per ledger | Architecture Patterns, Code Examples | HIGH — if Tally DOES export these fields, the rules would need to compare upload vs. configured values. Verify with CA pilot user what Tally JSON exports include. |
| A2 | `POST /api/organizations` returns the created organization's `id` in the response body | Pattern 5 (OrganizationSetupPage two-step flow) | MEDIUM — if the response doesn't include the org ID, the template selector Step 2 cannot know which org to onboard. Planner must read OrganizationController to verify. |
| A3 | `tdsSection` should be stored as a `String` column (not enum) to avoid Java enum naming constraints with digit-prefixed values | Code Examples | LOW — string column works fine with `@Enumerated` absent; DTO validation via `@Pattern` is equivalent. Alternative: use enum with `TDS_194C` naming convention. |
| A4 | DataInitializer `seedConstructionTemplate()` will be extended (not replaced) to add TDS/GST fields to the existing Construction template AND seed the 2 new templates | Standard Stack / Seeding | LOW — could be a new separate seeder class. Either approach is valid; extending existing method is simpler. |
| A5 | DashboardPage needs to be refactored to include the sidebar shell before MastersPage can be linked from it | Architecture Patterns | MEDIUM — if DashboardPage remains headerless (no sidebar), the Masters nav item has nowhere to appear. The UI spec specifies a sidebar shell — this was deferred from Phase 2. The planner must include a DashboardPage refactor task in Phase 3 Plan 4. |
| A6 | The MastersPage fetches the LATEST upload job's findings (not a specific job) via the Findings tab | Architecture Patterns | MEDIUM — the existing findings API is scoped to a specific `uploadJobId`. The Masters page needs either: (a) a query for findings by orgId (not jobId), or (b) a "latest job" endpoint. Neither exists today. The planner must decide and add an endpoint or query. |

---

## Open Questions

1. **Does Tally JSON export TDS section, HSN/SAC, or GSTIN per ledger?**
   - What we know: The parser currently reads `taxtype` (for GST flag) and `istdsapplicable` (for TDS flag). No evidence of HSN/SAC or GSTIN fields in the parser.
   - What's unclear: Tally Prime 7+ may export `hsncode` or `gstinofparty` fields in the masters export. If so, Phase 3 rules could do actual upload-vs-config comparison (much more valuable), not just completeness checks.
   - Recommendation: Check with CA pilot user. If Tally exports these fields, update `TallyParserService.parseMastersJson()` to parse them into `ParsedLedger`. This would change the rule implementations significantly.

2. **Findings tab scoping: by job or by org?**
   - What we know: `ValidationFinding` is linked to `uploadJobId`, not directly to `organizationId`. The Masters page shows per-org context.
   - What's unclear: Should the Findings tab show findings from the latest upload job only? All historical jobs? All open findings across all jobs?
   - Recommendation: Show findings from the most recent completed upload job for the org. Add a `findLatestCompletedJobByOrgId(UUID orgId)` query to `UploadJobRepository` and expose a `GET /api/v1/uploads/latest` endpoint or add an org-scoped findings endpoint.

3. **Template re-application from Masters page?**
   - What we know: The `existsByOrganizationId` guard on onboard blocks second application. The UI spec shows "Apply Template" in the template selector but doesn't specify a Masters page template button.
   - What's unclear: If a user skips the template at org setup and navigates to the Masters page, can they apply a template there? The UI spec says "You can customize individual ledger mappings later" but doesn't show a template button on the Masters page.
   - Recommendation: Out of scope for Phase 3 UI (UI spec confirms no template button on Masters page). The skip-then-apply path requires a separate design decision for Phase 4+.

4. **Severity alignment: migrate existing findings or dual-track?**
   - What we know: Existing `MismatchDetectionRule` uses `INFO/WARNING/ERROR`. Phase 3 adds `HIGH/MEDIUM/LOW`. The UI spec's severity filter shows HIGH/MEDIUM/LOW only.
   - What's unclear: Whether existing MISMATCH_DETECTION findings in the DB should be migrated to new severity values, or if the UI should show both severity schemes.
   - Recommendation: Update `MismatchDetectionRule` to use the new `HIGH/MEDIUM/LOW` values in the same plan that adds them to the enum. Existing DB findings (if any live data exists) should be migrated via SQL.

---

## Environment Availability

Step 2.6: SKIPPED — Phase 3 is purely code and DB changes. No new external tools, CLIs, or services are required beyond what is already running (PostgreSQL on 5432, Java 25, Maven wrapper, Node.js/npm).

---

## Validation Architecture

No `.planning/config.json` found — treat `nyquist_validation` as enabled.

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 5 + Spring Boot Test + Testcontainers (backend); Vitest + Testing Library (frontend) |
| Config file | `Service/superaccountant/src/test/resources/application-test.properties`; `Client/vite.config.ts` |
| Quick run command | `cd Service/superaccountant && ./mvnw test -Dtest=TdsSectionMappingRuleTest,GstApplicabilityRuleTest,HsnSacCodeRuleTest,GstinPresenceRuleTest` |
| Full suite command | `cd Service/superaccountant && ./mvnw test` and `cd Client && npm run test:run` |

### Phase Requirements to Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| MSTR-01 | POST /onboard with templateSlug="standard" copies 60+ pre-classified masters for org | integration | `./mvnw test -Dtest=PreconfiguredMastersControllerIT` | No — Wave 0 |
| MSTR-02 | TdsSectionMappingRule emits MEDIUM finding when tdsSection is null | unit | `./mvnw test -Dtest=TdsSectionMappingRuleTest` | No — Wave 0 |
| MSTR-02 | GstinPresenceRule emits HIGH finding when purchase ledger has null GSTIN | unit | `./mvnw test -Dtest=GstinPresenceRuleTest` | No — Wave 0 |
| MSTR-03 | PreconfiguredMaster with tdsSection set → TDS_SECTION_MAPPING emits no finding | unit | `./mvnw test -Dtest=TdsSectionMappingRuleTest` | No — Wave 0 |
| MSTR-04 | HsnSacCodeRule emits MEDIUM finding for income ledger without hsnSacCode | unit | `./mvnw test -Dtest=HsnSacCodeRuleTest` | No — Wave 0 |
| MSTR-05 | GstinPresenceRule HIGH finding for purchase ledger with null GSTIN | unit | `./mvnw test -Dtest=GstinPresenceRuleTest` | No — Wave 0 |
| MSTR-07 | PATCH resolve with ACCEPTED sets resolveStatus = ACCEPTED | integration | `./mvnw test -Dtest=UploadControllerIT` (extend existing) | Partial — UploadControllerIT exists |
| MSTR-08 | Known fixture masters.json with 3 null-section ledgers → 3 TDS_SECTION_MAPPING findings | integration | `./mvnw test -Dtest=ValidationOrchestratorIT` | No — Wave 0 |

### Sampling Rate

- Per task commit: `./mvnw test -Dtest=TdsSectionMappingRuleTest,GstApplicabilityRuleTest,HsnSacCodeRuleTest,GstinPresenceRuleTest -pl Service/superaccountant`
- Per wave merge: `./mvnw test -pl Service/superaccountant`
- Phase gate: Full suite (backend + frontend) green before `/gsd-verify-work`

### Wave 0 Gaps

- [ ] `src/test/java/.../masters/rules/TdsSectionMappingRuleTest.java` — covers MSTR-02, MSTR-03
- [ ] `src/test/java/.../masters/rules/GstApplicabilityRuleTest.java` — covers MSTR-04
- [ ] `src/test/java/.../masters/rules/HsnSacCodeRuleTest.java` — covers MSTR-04
- [ ] `src/test/java/.../masters/rules/GstinPresenceRuleTest.java` — covers MSTR-05
- [ ] `src/test/java/.../masters/controllers/PreconfiguredMastersControllerIT.java` — covers MSTR-01, MSTR-08 (Testcontainers; test named template onboarding)
- [ ] `src/test/resources/fixtures/masters-with-tds-gaps.json` — fixture file with known gaps for integration tests

---

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | no — no new auth changes | JWT (existing) |
| V3 Session Management | no — no session changes | Stateless JWT (existing) |
| V4 Access Control | yes | `@PreAuthorize("hasRole('ACCOUNTANT') or hasRole('OPERATOR')")` on mutating endpoints; `/masters` route guard on frontend |
| V5 Input Validation | yes | GSTIN regex validation (Jakarta `@Pattern` on DTO + client-side); HSN/SAC 4–8 digit validation; TDS section enum validation |
| V6 Cryptography | no — no new crypto | jjwt 0.12.6 (Phase 1) |

### Known Threat Patterns

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| OPERATOR submitting arbitrary `tdsSection` string value | Tampering | Backend: validate against allowed values (`@Pattern` or enum); reject unknown strings with 400 |
| AUDITOR_CA accessing PUT /preconfigured-masters (write endpoint) | Elevation of Privilege | `@PreAuthorize("hasRole('ACCOUNTANT') or hasRole('OPERATOR')")` — AUDITOR_CA excluded |
| Invalid GSTIN stored as vendor GSTIN | Tampering | `@Pattern(regexp = "[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z]{1}[1-9A-Z]{1}Z[0-9A-Z]{1}")` on UpdatePreconfiguredMasterRequest.gstin |
| OWNER accessing Masters page (no permission per UI spec matrix) | Unauthorized access | Route guard: `user.role !== 'ROLE_ACCOUNTANT' && user.role !== 'ROLE_OPERATOR'` redirects to `/dashboard` |

---

## Sources

### Primary (HIGH confidence)

- [VERIFIED: read PreconfiguredMaster.java] — entity fields, column names, enum types
- [VERIFIED: read ParsedLedger.java] — DTO fields, @Builder pattern
- [VERIFIED: read ValidationFinding.java] — all fields, FindingSeverity, ResolveStatus usage
- [VERIFIED: read ValidationRule.java] — interface contract (ruleCode, execute signature)
- [VERIFIED: read ValidationContext.java] — record fields (organizationId, uploadedBy, preconfiguredMasters, settings)
- [VERIFIED: read ValidationOrchestrator.java] — auto-discovery via List<ValidationRule>, execution order, persistence
- [VERIFIED: read MismatchDetectionRule.java] — implementation pattern, @Component annotation
- [VERIFIED: read FindingSeverity.java] — current values: INFO, WARNING, ERROR
- [VERIFIED: read ResolveStatus.java] — current values: OPEN, ACKNOWLEDGED, RESOLVED
- [VERIFIED: read LedgerCategory.java] — current values: PURCHASE, EXPENSE, INCOME, GST, TDS, OTHER
- [VERIFIED: read MismatchType.java] — existing mismatch types
- [VERIFIED: read ValidationRuleConfig.java] — execution_order, rule_code, is_active
- [VERIFIED: read DataInitializer.java] — seeding pattern, construction template entries (27 rows), seedRoles() upsert, seedValidationRules() count-guard
- [VERIFIED: read UploadController.java] — POST /uploads, PATCH /resolve endpoint, resolveFinding logic
- [VERIFIED: read PreconfiguredMastersController.java] — all endpoints, onboard logic, existsByOrganizationId guard
- [VERIFIED: read CreatePreconfiguredMasterRequest.java] — current DTO fields
- [VERIFIED: read UpdatePreconfiguredMasterRequest.java] — current DTO fields
- [VERIFIED: read PreconfiguredMasterResponse.java] — current response fields
- [VERIFIED: read OnboardRequest.java] — single field: useTemplate boolean
- [VERIFIED: read ResolveRequest.java] — status + note fields
- [VERIFIED: read FindingResponse.java] — all response fields
- [VERIFIED: read PreconfiguredMasterRepository.java] — findByTemplateTrue(), existsByOrganizationId()
- [VERIFIED: read ValidationFindingRepository.java] — findFiltered() JPQL (showResolved only excludes RESOLVED)
- [VERIFIED: read LedgerCategoryClassifier.java] — classification logic, root groups
- [VERIFIED: read TallyParserService.java lines 57-129] — parseMastersJson: parses name, guid, parent, taxtype (for gstApplicable), istdsapplicable (for tdsApplicable) — no HSN/SAC, GSTIN, or TDS section fields parsed
- [VERIFIED: read MismatchDetectionRuleTest.java] — unit test pattern, helpers
- [VERIFIED: read main.tsx] — route definition pattern, beforeLoad guards
- [VERIFIED: read OrganizationSetupPage.tsx] — GSTIN_REGEX constant, window.alert usage, inputClass constant
- [VERIFIED: read authStore.ts] — user.role as string, OrgMembership, AuthUser interfaces
- [VERIFIED: read DashboardPage.tsx] — no sidebar present; topbar only
- [VERIFIED: read index.css] — all design tokens confirmed present
- [VERIFIED: read 03-UI-SPEC.md] — full surface specs for all 5 new/modified components

### Secondary (MEDIUM confidence)

- [CITED: ROADMAP.md Phase 3 plans] — template seeding, 5 plans structure, integration test requirements
- [CITED: 03-UI-SPEC.md Pre-Population Source Audit] — template apply endpoint: POST /api/v1/preconfigured-masters/onboard

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — all libraries already in use; no new dependencies required
- Existing code inventory: HIGH — all files read directly
- Architecture patterns: HIGH — patterns derived from verified source files
- Validation rule structure: HIGH — interface, orchestrator, and existing rule all read
- Enum alignment issues (FindingSeverity, ResolveStatus): HIGH — verified by reading both enums
- Template model extension: MEDIUM — OnboardRequest and seeder read; template slug mechanism is designed but not yet in code
- Frontend new components: HIGH — patterns derived from OrganizationSetupPage, main.tsx, authStore
- Indian compliance domain (TDS sections, GST types): MEDIUM — section codes from UI spec, confirmed against ROADMAP.md; specific thresholds and rates are Phase 5 concern

**Research date:** 2026-05-03
**Valid until:** 2026-06-03 (stable domain; rules change only if Tally JSON schema or Indian tax law changes)
