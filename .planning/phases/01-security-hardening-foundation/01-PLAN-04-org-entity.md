---
plan: 4
phase: 1
wave: 1
depends_on: []
files_modified:
  - Service/superaccountant/src/main/java/com/arktech/superaccountant/masters/models/Organization.java
  - Service/superaccountant/src/main/java/com/arktech/superaccountant/masters/payload/request/CreateOrganizationRequest.java
  - Service/superaccountant/src/main/java/com/arktech/superaccountant/masters/controllers/OrganizationController.java
  - Service/superaccountant/src/main/java/com/arktech/superaccountant/masters/payload/response/OrganizationResponse.java
  - Client/src/pages/OrganizationSetupPage.tsx
  - Client/src/main.tsx
autonomous: true
requirements:
  - ORG-01
must_haves:
  truths:
    - "OWNER can create an organization with GSTIN, PAN, registered address, and financial year start via POST /api/organizations"
    - "GSTIN format validation rejects invalid strings with a 400 error and a field-level error message"
    - "PAN format validation rejects invalid strings with a 400 error and a field-level error message"
    - "GET /api/organizations/{id} returns the organization's GSTIN, PAN, address, and financial year start"
    - "The OrganizationSetupPage form exists in the frontend and calls POST /api/organizations"
  artifacts:
    - path: "Service/superaccountant/src/main/java/com/arktech/superaccountant/masters/models/Organization.java"
      provides: "Organization entity with GSTIN/PAN/address/fyStart fields"
      contains: "gstin"
    - path: "Service/superaccountant/src/main/java/com/arktech/superaccountant/masters/payload/request/CreateOrganizationRequest.java"
      provides: "Request DTO with @Pattern validation for GSTIN and PAN"
      contains: "@Pattern"
    - path: "Client/src/pages/OrganizationSetupPage.tsx"
      provides: "Organization setup form UI"
      contains: "OrganizationSetupPage"
  key_links:
    - from: "OrganizationSetupPage"
      to: "POST /api/organizations"
      via: "api.post('/organizations', form)"
      pattern: "api.post.*organizations"
    - from: "CreateOrganizationRequest"
      to: "Organization entity"
      via: "controller mapping"
      pattern: "setGstin"
---

# Plan 4: Organization Entity Extension + Setup UI

## Goal
Extend the `Organization` entity with `gstin`, `pan`, `registeredAddress`, and `financialYearStart` fields; add `@Pattern` regex validation to the request DTO; expose a `GET /api/organizations/{id}` endpoint; build the `OrganizationSetupPage` React form per the UI spec.

## Context
`Organization` currently has only `id`, `name`, `createdAt`. D-16 requires adding `gstin` (nullable, 15 chars), `pan` (nullable, 10 chars), `registeredAddress` (String), and `financialYearStart` (int, default 4). D-17 requires GSTIN and PAN format validation at the API layer. Hibernate `ddl-auto: update` will add these columns automatically on next startup — no migration script needed. The `OrganizationSetupPage` frontend form is defined in `01-UI-SPEC.md`. (D-16, D-17)

<threat_model>
## Threat Model (ASVS L1)

### Threats Addressed

- **[HIGH] T-04-01 — Input Validation: Malformed GSTIN/PAN stored in database** — Invalid GSTIN/PAN would corrupt downstream GST and TDS computation. Mitigation: `@Pattern` on `CreateOrganizationRequest.gstin` enforces `[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z]{1}[1-9A-Z]{1}Z[0-9A-Z]{1}` (15 chars); `@Pattern` on `pan` enforces `[A-Z]{5}[0-9]{4}[A-Z]{1}` (10 chars). Jakarta Validation returns 400 with `MethodArgumentNotValidException` before entity is touched.

- **[MED] T-04-02 — Information Disclosure: Organization detail endpoint accessible to any user** — `GET /api/organizations/{id}` should not expose one org's GSTIN/PAN to users of a different org. Mitigation: controller checks `principal.getOrganizationId().equals(id)` and returns 403 if not matching. Plan 5 will add full UserOrganization membership checks; this plan uses the existing `organizationId` on `UserDetailsImpl` as a temporary guard.

- **[LOW] T-04-03 — Tampering: Financial year start outside valid range** — `financialYearStart` must be 1–12. Mitigation: add `@Min(1) @Max(12)` Jakarta constraints to the DTO field.

### Residual Risks

- GSTIN checksum validation (beyond format regex) is not implemented in this plan. Format regex catches obvious mistakes; checksum validation is a Phase 3 MSTR-08 concern.
- Fields are nullable in Phase 1 (GSTIN/PAN optional at org creation). OWNER can update them later. This is intentional per D-16 ("nullable initially").
</threat_model>

## Tasks

<task id="1">
<title>Extend Organization entity and CreateOrganizationRequest DTO; update OrganizationController with GET endpoint</title>
<read_first>
- `Service/superaccountant/src/main/java/com/arktech/superaccountant/masters/models/Organization.java` — current entity with only id/name/createdAt; fields to add: gstin, pan, registeredAddress, financialYearStart
- `Service/superaccountant/src/main/java/com/arktech/superaccountant/masters/payload/request/CreateOrganizationRequest.java` — current DTO with only @NotBlank name; @Pattern constraints to add
- `Service/superaccountant/src/main/java/com/arktech/superaccountant/masters/controllers/OrganizationController.java` — current controller; GET endpoint to add; POST mapping to update with new fields
</read_first>
<action>
**Organization.java — add fields per D-16:**

```java
@Column(name = "gstin", length = 15)
private String gstin;

@Column(name = "pan", length = 10)
private String pan;

@Column(name = "registered_address", columnDefinition = "TEXT")
private String registeredAddress;

@Column(name = "financial_year_start", nullable = false)
private int financialYearStart = 4;
```

All fields are nullable at DB level (no `nullable = false`) except `financialYearStart`. Lombok `@Data` on the class already generates getters/setters — no additional annotations needed for the new fields.

**CreateOrganizationRequest.java — add validation annotations per D-17:**

```java
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

@Pattern(
    regexp = "[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z]{1}[1-9A-Z]{1}Z[0-9A-Z]{1}",
    message = "GSTIN must be 15 characters in the format: 22AAAAA0000A1Z5"
)
private String gstin;

@Pattern(
    regexp = "[A-Z]{5}[0-9]{4}[A-Z]{1}",
    message = "PAN must be 10 characters in the format: ABCDE1234F"
)
private String pan;

private String registeredAddress;

@Min(value = 1, message = "Financial year start must be a month number 1–12")
@Max(value = 12, message = "Financial year start must be a month number 1–12")
private int financialYearStart = 4;
```

Keep the existing `@NotBlank @Size(max = 255) private String name;` field. The new fields (`gstin`, `pan`, `registeredAddress`) are nullable in the request — no `@NotNull` or `@NotBlank` on them. `@Pattern` is only enforced when the value is non-null; null values pass validation (nullable GSTIN allowed at org creation per D-16).

**OrganizationController.java — update POST and add GET:**

Update `createOrganization` to map the new fields from request to entity:
```java
Organization org = new Organization(request.getName());
org.setGstin(request.getGstin());
org.setPan(request.getPan());
org.setRegisteredAddress(request.getRegisteredAddress());
org.setFinancialYearStart(request.getFinancialYearStart() > 0 ? request.getFinancialYearStart() : 4);
```

Update the response body to include the new fields:
```java
return ResponseEntity.status(201).body(Map.of(
    "id", org.getId(),
    "name", org.getName(),
    "gstin", org.getGstin() != null ? org.getGstin() : "",
    "pan", org.getPan() != null ? org.getPan() : "",
    "registeredAddress", org.getRegisteredAddress() != null ? org.getRegisteredAddress() : "",
    "financialYearStart", org.getFinancialYearStart(),
    "createdAt", org.getCreatedAt()
));
```

Add a `GET /api/organizations/{id}` endpoint with org-scoping check per T-04-02:
```java
@PreAuthorize("isAuthenticated()")
@GetMapping("/{id}")
public ResponseEntity<?> getOrganization(
        @PathVariable UUID id,
        @AuthenticationPrincipal UserDetailsImpl principal) {

    // Temporary org-scoping guard (Plan 5 replaces with UserOrganization membership check)
    if (principal.getOrganizationId() == null || !principal.getOrganizationId().equals(id)) {
        return ResponseEntity.status(403).body(new MessageResponse("Access denied to this organization"));
    }

    return organizationRepository.findById(id)
            .map(org -> ResponseEntity.ok(Map.of(
                "id", org.getId(),
                "name", org.getName(),
                "gstin", org.getGstin() != null ? org.getGstin() : "",
                "pan", org.getPan() != null ? org.getPan() : "",
                "registeredAddress", org.getRegisteredAddress() != null ? org.getRegisteredAddress() : "",
                "financialYearStart", org.getFinancialYearStart(),
                "createdAt", org.getCreatedAt()
            )))
            .orElse(ResponseEntity.notFound().build());
}
```

Add `import com.arktech.superaccountant.login.payload.response.MessageResponse;` and `import java.util.UUID;` if not already present.

Add `import org.springframework.security.access.prepost.PreAuthorize;` if not present (may already be added by Plan 2).
</action>
<acceptance_criteria>
- `Organization.java` contains `private String gstin`
- `Organization.java` contains `private String pan`
- `Organization.java` contains `private String registeredAddress`
- `Organization.java` contains `private int financialYearStart`
- `CreateOrganizationRequest.java` contains `@Pattern` annotation with regexp `[0-9]{2}[A-Z]{5}`
- `CreateOrganizationRequest.java` contains `@Pattern` annotation with regexp `[A-Z]{5}[0-9]{4}`
- `CreateOrganizationRequest.java` contains `@Min(value = 1`
- `OrganizationController.java` contains `@GetMapping("/{id}")`
- Running `cd Service/superaccountant && ./mvnw compile -q` (with JWT_SECRET set) exits 0
</acceptance_criteria>
</task>

<task id="2">
<title>Build OrganizationSetupPage frontend form</title>
<read_first>
- `Client/src/pages/SignupPage.tsx` — reference pattern: form layout, input styling classes, error handling, api.post() call, navigate()
- `Client/src/pages/LoginPage.tsx` — reference for max-w-sm layout and card styling
- `Client/src/main.tsx` — routing setup; add `/organization/setup` route
- `Client/src/store/authStore.ts` — auth store shape: token, user (id, username, email, role)
- `.planning/phases/01-security-hardening-foundation/01-UI-SPEC.md` — OrganizationSetupForm spec: fields, validation, copywriting, color values, accessibility requirements
</read_first>
<action>
**Create `Client/src/pages/OrganizationSetupPage.tsx`:**

Follow the `OrganizationSetupForm` spec from `01-UI-SPEC.md` exactly. Key implementation details:

Layout: full-page centered card using app shell layout — topbar (64px) area + main content with 24px padding. Card: `max-w-lg` (512px), `bg-[var(--color-surface)]`, `rounded-[var(--radius-xl)]`, `shadow-[var(--shadow-md)]`, `p-8`.

Page title: `<h1>` "Create Organization" — 24px / weight 600 (`text-2xl font-semibold`).

Form fields (all use the same input class as SignupPage — `w-full h-11 px-3 rounded-[var(--radius-md)] border border-[var(--color-border)] ...`):
1. **Organization Name** — `type="text"`, required, `id="org-name"`
2. **GSTIN** — `type="text"`, `maxLength={15}`, `id="gstin"`, monospace font (`font-mono`), `aria-describedby="gstin-hint"`. Format hint below: `id="gstin-hint"` — "15-character GST Identification Number". Validate on blur against `/^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z]{1}[1-9A-Z]{1}Z[0-9A-Z]{1}$/`. On blur fail: show error `"GSTIN must be 15 characters in the format: 22AAAAA0000A1Z5"` below field in `text-[var(--color-danger)] text-xs` with `role="alert"`. On blur pass: show Lucide `CheckCircle` (14px, `text-[var(--color-success)]`) inline at input right edge.
3. **PAN** — `type="text"`, `maxLength={10}`, `id="pan"`, monospace font, `aria-describedby="pan-hint"`. Format hint: "10-character Permanent Account Number". Validate on blur against `/^[A-Z]{5}[0-9]{4}[A-Z]{1}$/`. Same error/success pattern as GSTIN. Error: `"PAN must be 10 characters in the format: ABCDE1234F"`.
4. **Registered Address** — `<textarea rows={3} id="registered-address">`.
5. **Financial Year Start** — `<select id="fy-start">` with options: `<option value="4">April (Indian default)</option>`, `<option value="1">January</option>`, `<option value="7">July</option>`, `<option value="10">October</option>`. Default selected value: `"4"`.

State: `const [form, setForm] = useState({ name: '', gstin: '', pan: '', registeredAddress: '', financialYearStart: 4 })`. Separate state for field-level errors: `const [fieldErrors, setFieldErrors] = useState({ gstin: '', pan: '' })`.

GSTIN/PAN blur validation: `onBlur` handler sets `fieldErrors.gstin` / `fieldErrors.pan` to empty string if valid, or the error message if invalid.

Submit button: label "Create Organization" per UI spec. Disabled when `form.name.length === 0`. Loading state: spinner replaces text, pointer-events none. Use the same disabled/loading pattern as `SignupPage.tsx`.

On submit: `await api.post('/organizations', { ...form })`. On success: show toast "Organization created successfully" (use `window.alert` as a placeholder if no toast library is installed — check `package.json` for sonner or similar). Then `navigate({ to: '/dashboard' })`.

On API error: show error banner above submit button — "Organization could not be created. Try again, or contact support if the problem continues." (exact copy from UI spec).

Export: `export function OrganizationSetupPage()`.

**Update `Client/src/main.tsx`:**

Add a `/organization/setup` route:
```tsx
const orgSetupRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/organization/setup',
  beforeLoad: () => {
    if (!useAuthStore.getState().isAuthenticated) {
      throw redirect({ to: '/login' })
    }
  },
  component: OrganizationSetupPage,
})
```

Add `orgSetupRoute` to `routeTree.addChildren([...])`.
Add import: `import { OrganizationSetupPage } from '@/pages/OrganizationSetupPage'`.
</action>
<acceptance_criteria>
- `Client/src/pages/OrganizationSetupPage.tsx` exists and contains `export function OrganizationSetupPage`
- `OrganizationSetupPage.tsx` contains `"gstin"` (field id or state key)
- `OrganizationSetupPage.tsx` contains `"pan"` (field id or state key)
- `OrganizationSetupPage.tsx` contains `font-mono` (monospace for GSTIN/PAN fields)
- `OrganizationSetupPage.tsx` contains `onBlur` (blur-time validation)
- `OrganizationSetupPage.tsx` contains `"Create Organization"` (primary CTA copy per UI spec)
- `OrganizationSetupPage.tsx` contains `aria-describedby` (accessibility)
- `Client/src/main.tsx` contains `OrganizationSetupPage`
- `Client/src/main.tsx` contains `'/organization/setup'`
- Running `cd Client && npm run build` exits 0 (TypeScript compilation clean)
</acceptance_criteria>
</task>

## Verification

```bash
# Backend
cd "Service/superaccountant"
export JWT_SECRET="a-32-char-or-longer-dummy-secret-value"

./mvnw compile -q

# Organization entity has new fields
grep "gstin" src/main/java/com/arktech/superaccountant/masters/models/Organization.java && echo "PASS" || echo "FAIL"
grep "@Pattern" src/main/java/com/arktech/superaccountant/masters/payload/request/CreateOrganizationRequest.java && echo "PASS" || echo "FAIL"
grep "@GetMapping" src/main/java/com/arktech/superaccountant/masters/controllers/OrganizationController.java && echo "PASS" || echo "FAIL"

# Frontend
cd "../../Client"
npm run build
grep "OrganizationSetupPage" src/main.tsx && echo "PASS" || echo "FAIL"
grep "font-mono" src/pages/OrganizationSetupPage.tsx && echo "PASS" || echo "FAIL"
```

## must_haves
- `Organization` entity has `gstin`, `pan`, `registeredAddress`, `financialYearStart` columns (Hibernate auto-migrates)
- `POST /api/organizations` returns 400 when GSTIN or PAN format is invalid
- `GET /api/organizations/{id}` returns the full org object including new fields
- `OrganizationSetupPage` form exists at `/organization/setup` route with blur-time GSTIN/PAN validation
- Frontend builds cleanly (TypeScript no errors)

<output>
After completion, create `.planning/phases/01-security-hardening-foundation/01-04-SUMMARY.md`
</output>
