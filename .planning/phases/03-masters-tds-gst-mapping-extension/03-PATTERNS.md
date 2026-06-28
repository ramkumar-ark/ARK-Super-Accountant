# Phase 3: Masters TDS & GST Mapping Extension - Pattern Map

**Mapped:** 2026-05-03
**Files analyzed:** 21 new/modified files
**Analogs found:** 21 / 21

---

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `masters/models/GstApplicabilityType.java` | model/enum | — | `masters/models/LedgerCategory.java` | exact |
| `masters/models/FindingSeverity.java` (extend) | model/enum | — | `masters/models/LedgerCategory.java` | exact |
| `masters/models/ResolveStatus.java` (extend) | model/enum | — | `masters/models/LedgerCategory.java` | exact |
| `masters/models/PreconfiguredMaster.java` (extend) | model/entity | CRUD | `masters/models/ValidationFinding.java` | exact |
| `masters/classifier/ParsedLedger.java` (extend) | model/DTO | transform | `masters/classifier/ParsedLedger.java` | self |
| `masters/rules/TdsSectionMappingRule.java` | service/rule | event-driven | `masters/rules/MismatchDetectionRule.java` | exact |
| `masters/rules/GstApplicabilityRule.java` | service/rule | event-driven | `masters/rules/MismatchDetectionRule.java` | exact |
| `masters/rules/HsnSacCodeRule.java` | service/rule | event-driven | `masters/rules/MismatchDetectionRule.java` | exact |
| `masters/rules/GstinPresenceRule.java` | service/rule | event-driven | `masters/rules/MismatchDetectionRule.java` | exact |
| `masters/payload/request/CreatePreconfiguredMasterRequest.java` (extend) | DTO/request | request-response | `masters/payload/request/CreatePreconfiguredMasterRequest.java` | self |
| `masters/payload/request/UpdatePreconfiguredMasterRequest.java` (extend) | DTO/request | request-response | `masters/payload/request/UpdatePreconfiguredMasterRequest.java` | self |
| `masters/payload/response/PreconfiguredMasterResponse.java` (extend) | DTO/response | request-response | `masters/payload/response/FindingResponse.java` | exact |
| `masters/payload/request/OnboardRequest.java` (extend) | DTO/request | request-response | `masters/payload/request/ResolveRequest.java` | role-match |
| `masters/repository/PreconfiguredMasterRepository.java` (extend) | repository | CRUD | `masters/repository/ValidationFindingRepository.java` | exact |
| `masters/repository/ValidationFindingRepository.java` (extend JPQL) | repository | CRUD | `masters/repository/ValidationFindingRepository.java` | self |
| `masters/repository/ValidationRuleConfigRepository.java` (extend) | repository | CRUD | `masters/repository/PreconfiguredMasterRepository.java` | exact |
| `login/config/DataInitializer.java` (extend) | config/seed | batch | `login/config/DataInitializer.java` | self |
| `masters/controllers/PreconfiguredMastersController.java` (extend) | controller | request-response | `masters/controllers/PreconfiguredMastersController.java` | self |
| `Client/src/pages/MastersPage.tsx` | page/component | request-response | `Client/src/pages/DashboardPage.tsx` | role-match |
| `Client/src/components/LedgerMappingPanel.tsx` | component | request-response | `Client/src/pages/OrganizationSetupPage.tsx` | role-match |
| `Client/src/pages/OrganizationSetupPage.tsx` (extend) | page/component | request-response | `Client/src/pages/OrganizationSetupPage.tsx` | self |
| `Client/src/main.tsx` (extend) | config/router | request-response | `Client/src/main.tsx` | self |

---

## Pattern Assignments

### `masters/models/GstApplicabilityType.java` (model/enum)

**Analog:** `Service/.../masters/models/LedgerCategory.java`

**Full enum pattern** (lines 1–10):
```java
package com.arktech.superaccountant.masters.models;

public enum LedgerCategory {
    PURCHASE,
    EXPENSE,
    INCOME,
    GST,
    TDS,
    OTHER
}
```

**Copy as:**
```java
package com.arktech.superaccountant.masters.models;

public enum GstApplicabilityType {
    TAXABLE,
    EXEMPT,
    ZERO_RATED,
    NON_GST,
    RCM,
    NOT_APPLICABLE
}
```

No imports needed. No Lombok. Bare enum — same as every other enum in this package.

---

### `masters/models/FindingSeverity.java` (extend existing enum)

**Analog:** `masters/models/FindingSeverity.java` (self)

**Current file** (lines 1–7):
```java
package com.arktech.superaccountant.masters.models;

public enum FindingSeverity {
    INFO,
    WARNING,
    ERROR
}
```

**Phase 3 extension — add 3 values after existing ones:**
```java
public enum FindingSeverity {
    INFO,
    WARNING,
    ERROR,
    HIGH,
    MEDIUM,
    LOW
}
```

Keep `INFO`, `WARNING`, `ERROR` for backward compatibility with existing `MismatchDetectionRule` DB rows. New Phase 3 rules use `HIGH`, `MEDIUM`, `LOW` exclusively.

---

### `masters/models/ResolveStatus.java` (extend existing enum)

**Analog:** `masters/models/ResolveStatus.java` (self)

**Current file** (lines 1–7):
```java
package com.arktech.superaccountant.masters.models;

public enum ResolveStatus {
    OPEN,
    ACKNOWLEDGED,
    RESOLVED
}
```

**Phase 3 extension — add 2 values after existing ones:**
```java
public enum ResolveStatus {
    OPEN,
    ACKNOWLEDGED,
    RESOLVED,
    ACCEPTED,
    OVERRIDDEN
}
```

Keep `ACKNOWLEDGED`, `RESOLVED` for backward compatibility. New UI actions use `ACCEPTED` and `OVERRIDDEN` exclusively.

---

### `masters/models/PreconfiguredMaster.java` (extend entity — 4 new fields + templateSlug)

**Analog:** `masters/models/PreconfiguredMaster.java` (self) + `masters/models/ValidationFinding.java`

**Existing field pattern** (`PreconfiguredMaster.java` lines 29–52):
```java
@NotNull
@Enumerated(EnumType.STRING)
@Column(nullable = false)
private LedgerCategory category;

@Column(name = "expected_parent_group")
private String expectedParentGroup;

@Column(name = "expected_gst_applicable")
private Boolean expectedGstApplicable;

@Column(name = "is_template", nullable = false)
private boolean template = false;
```

**4 new fields to append (after `template`):**
```java
@Column(name = "template_slug")
private String templateSlug;               // nullable; only set on template rows

@Column(name = "tds_section")
private String tdsSection;                 // e.g. "194C", "NOT_SUBJECT", null = unset

@Enumerated(EnumType.STRING)
@Column(name = "gst_applicability_type")
private GstApplicabilityType gstApplicabilityType;  // nullable = unset

@Column(name = "hsn_sac_code")
private String hsnSacCode;                 // 4-8 digits, nullable

@Column(name = "gstin")
private String gstin;                      // 15 chars, nullable (PURCHASE ledgers only)
```

`@Enumerated(EnumType.STRING)` pattern confirmed from `ValidationFinding.java` lines 25–26, 29–31, 41–42. Hibernate `ddl-auto: update` adds these as nullable `VARCHAR` columns automatically.

---

### `masters/classifier/ParsedLedger.java` (extend DTO — 4 new fields)

**Analog:** `masters/classifier/ParsedLedger.java` (self)

**Existing full file** (lines 1–16):
```java
package com.arktech.superaccountant.masters.classifier;

import com.arktech.superaccountant.masters.models.LedgerCategory;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ParsedLedger {
    private String name;
    private String parentGroup;
    private String guid;
    private Boolean gstApplicable;
    private Boolean tdsApplicable;
    private LedgerCategory category;
}
```

**4 new fields to append:**
```java
private String tdsSection;             // always null from Tally upload; placeholder for future
private String gstApplicabilityType;   // always null from Tally upload
private String hsnSacCode;             // may come from Tally Prime 7+ hsncode field
private String gstin;                  // may come from Tally Prime 7+ gstinofparty field
```

Use `String` (not enum) on `ParsedLedger` — it is a plain data carrier, not an entity. `@Builder` already handles all fields automatically via Lombok.

---

### `masters/rules/TdsSectionMappingRule.java` (new ValidationRule)

**Analog:** `masters/rules/MismatchDetectionRule.java` (exact match — same interface, same package, same orchestrator wiring)

**Imports pattern** (`MismatchDetectionRule.java` lines 1–9):
```java
package com.arktech.superaccountant.masters.rules;

import com.arktech.superaccountant.masters.classifier.ParsedLedger;
import com.arktech.superaccountant.masters.models.*;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;
```

**Component annotation pattern** (`MismatchDetectionRule.java` line 14):
```java
@Component("MISMATCH_DETECTION")
public class MismatchDetectionRule implements ValidationRule {
```

Use `@Component("TDS_SECTION_MAPPING")` — the bean name must match the `ruleCode` string looked up in `ValidationOrchestrator.ruleMap` (line 37–40 of `ValidationOrchestrator.java`).

**Core rule pattern** (`MismatchDetectionRule.java` lines 23–42, adapted for completeness-check style):
```java
@Override
public String getRuleCode() { return "TDS_SECTION_MAPPING"; }

@Override
public List<ValidationFinding> execute(ValidationContext context, List<ParsedLedger> parsedLedgers) {
    List<ValidationFinding> findings = new ArrayList<>();
    for (PreconfiguredMaster master : context.preconfiguredMasters()) {
        if (master.getTdsSection() == null) {
            FindingSeverity severity = switch (master.getCategory()) {
                case TDS -> FindingSeverity.HIGH;
                case PURCHASE, EXPENSE, INCOME -> FindingSeverity.MEDIUM;
                default -> FindingSeverity.LOW;
            };
            ValidationFinding f = new ValidationFinding();
            f.setRuleCode(getRuleCode());
            f.setLedgerName(master.getLedgerName());
            f.setCategory(master.getCategory());
            f.setSeverity(severity);
            f.setResolveStatus(ResolveStatus.OPEN);
            f.setMessage("Ledger '" + master.getLedgerName() + "' has no TDS section assigned.");
            f.setSuggestedFix("Assign a TDS section (e.g. 194C) or mark as NOT_SUBJECT.");
            findings.add(f);
        }
    }
    return findings;
}
```

Key difference from `MismatchDetectionRule`: iterate `context.preconfiguredMasters()` (configured list), NOT `parsedLedgers`. Tally JSON does not export TDS section per ledger — this is a master configuration completeness check.

**Finding builder pattern** (`MismatchDetectionRule.java` lines 127–144):
```java
ValidationFinding f = new ValidationFinding();
f.setRuleCode(getRuleCode());
f.setLedgerName(ledgerName);
f.setCategory(category);
f.setMismatchType(type);       // leave null for Phase 3 rules — no MismatchType applies
f.setSeverity(severity);
f.setExpectedValue(expected);
f.setActualValue(actual);
f.setSuggestedFix(suggestedFix);
f.setResolveStatus(ResolveStatus.OPEN);
f.setMessage("...");
```

---

### `masters/rules/GstApplicabilityRule.java` (new ValidationRule)

**Analog:** `masters/rules/MismatchDetectionRule.java` (exact)

Same imports, same `@Component("GST_APPLICABILITY")`, same `ValidationRule` interface. Core logic:

```java
// Checks: master.getGstApplicabilityType() == null
// Scope: INCOME, GST category ledgers (TAXABLE/EXEMPT required)
// Severity: MEDIUM for INCOME and GST categories; LOW for others
for (PreconfiguredMaster master : context.preconfiguredMasters()) {
    if (master.getGstApplicabilityType() == null) {
        FindingSeverity severity = (master.getCategory() == LedgerCategory.INCOME
                || master.getCategory() == LedgerCategory.GST)
                ? FindingSeverity.MEDIUM : FindingSeverity.LOW;
        // ... build ValidationFinding (same pattern as TdsSectionMappingRule above)
    }
}
```

---

### `masters/rules/HsnSacCodeRule.java` (new ValidationRule)

**Analog:** `masters/rules/MismatchDetectionRule.java` (exact)

`@Component("HSN_SAC_CODE")`. Core logic:

```java
// Checks: master.getHsnSacCode() == null for INCOME/GST ledgers that are TAXABLE
// Severity: MEDIUM
for (PreconfiguredMaster master : context.preconfiguredMasters()) {
    boolean requiresHsn = master.getCategory() == LedgerCategory.INCOME
            || master.getCategory() == LedgerCategory.GST;
    if (requiresHsn && master.getHsnSacCode() == null) {
        // ... build finding with FindingSeverity.MEDIUM
    }
}
```

---

### `masters/rules/GstinPresenceRule.java` (new ValidationRule)

**Analog:** `masters/rules/MismatchDetectionRule.java` (exact)

`@Component("GSTIN_PRESENCE")`. Core logic:

```java
// Checks: PURCHASE ledgers must have GSTIN for GSTR-2B
// Severity: HIGH
for (PreconfiguredMaster master : context.preconfiguredMasters()) {
    if (master.getCategory() == LedgerCategory.PURCHASE && master.getGstin() == null) {
        ValidationFinding f = new ValidationFinding();
        // ...
        f.setSeverity(FindingSeverity.HIGH);
        f.setMessage("Purchase ledger '" + master.getLedgerName()
                + "' has no GSTIN. Required for GSTR-2B reconciliation.");
        f.setSuggestedFix("Add the vendor's GSTIN to this ledger entry.");
        findings.add(f);
    }
}
```

---

### `masters/payload/request/CreatePreconfiguredMasterRequest.java` (extend)

**Analog:** `CreatePreconfiguredMasterRequest.java` (self)

**Current full file** (lines 1–17):
```java
@Data
public class CreatePreconfiguredMasterRequest {
    @NotBlank
    private String ledgerName;
    @NotNull
    private LedgerCategory category;
    private String expectedParentGroup;
    private Boolean expectedGstApplicable;
    private Boolean expectedTdsApplicable;
}
```

**4 new nullable fields to append:**
```java
private String tdsSection;              // validated via @Pattern or service-layer check
private String gstApplicabilityType;    // String (maps to GstApplicabilityType enum values)
private String hsnSacCode;              // 4-8 digit string; validate @Pattern(regexp="\\d{4,8}")
@Pattern(regexp = "[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z]{1}[1-9A-Z]{1}Z[0-9A-Z]{1}",
         message = "GSTIN must be 15 characters in format: 22AAAAA0000A1Z5")
private String gstin;
```

No `@NotBlank`/`@NotNull` on new fields — all are optional during creation.

---

### `masters/payload/request/UpdatePreconfiguredMasterRequest.java` (extend)

**Analog:** `UpdatePreconfiguredMasterRequest.java` (self)

**Current full file** (lines 1–13):
```java
@Data
public class UpdatePreconfiguredMasterRequest {
    private String ledgerName;
    private LedgerCategory category;
    private String expectedParentGroup;
    private Boolean expectedGstApplicable;
    private Boolean expectedTdsApplicable;
}
```

Add the same 4 fields as `CreatePreconfiguredMasterRequest` above. All nullable — the null-check partial update pattern in `PreconfiguredMastersController.update()` (lines 96–104) handles which fields to apply.

---

### `masters/payload/response/PreconfiguredMasterResponse.java` (extend)

**Analog:** `masters/payload/response/FindingResponse.java` (exact — `@Data @Builder`, same response package)

**Current full file** (`PreconfiguredMasterResponse.java` lines 1–22):
```java
@Data
@Builder
public class PreconfiguredMasterResponse {
    private UUID id;
    private String ledgerName;
    private LedgerCategory category;
    private String expectedParentGroup;
    private Boolean expectedGstApplicable;
    private Boolean expectedTdsApplicable;
    private boolean active;
    private Instant createdAt;
    private Instant updatedAt;
}
```

**4 new fields to append:**
```java
private String tdsSection;
private String gstApplicabilityType;   // String serialization of GstApplicabilityType
private String hsnSacCode;
private String gstin;
```

**`toResponse()` method in controller** must add each new field via `.tdsSection(m.getTdsSection())` etc. in the builder chain — see `PreconfiguredMastersController.java` lines 214–225 for the builder call to extend.

---

### `masters/payload/request/OnboardRequest.java` (extend)

**Analog:** `masters/payload/request/ResolveRequest.java` (role-match — same `@Data`, same DTO package)

**Current full file** (lines 1–8):
```java
@Data
public class OnboardRequest {
    private boolean useTemplate;
}
```

**Add `templateSlug` field:**
```java
@Data
public class OnboardRequest {
    private boolean useTemplate;       // keep for backward compat
    private String templateSlug;       // "standard" | "simplified" | "manufacturing"; takes priority over useTemplate
}
```

No validation annotation needed — the controller validates the slug value against known slugs and returns 400 for unknown values.

---

### `masters/repository/PreconfiguredMasterRepository.java` (extend)

**Analog:** `masters/repository/ValidationFindingRepository.java` (exact — same Spring Data JPA derived query pattern)

**Current full file** (`PreconfiguredMasterRepository.java` lines 1–20):
```java
@Repository
public interface PreconfiguredMasterRepository extends JpaRepository<PreconfiguredMaster, UUID> {
    List<PreconfiguredMaster> findByOrganizationIdAndActiveTrue(UUID organizationId);
    Page<PreconfiguredMaster> findByOrganizationIdAndActiveTrue(UUID organizationId, Pageable pageable);
    Page<PreconfiguredMaster> findByOrganizationIdAndActiveTrueAndCategory(UUID organizationId, LedgerCategory category, Pageable pageable);
    boolean existsByOrganizationId(UUID organizationId);
    List<PreconfiguredMaster> findByTemplateTrue();
}
```

**2 new derived query methods to add:**
```java
List<PreconfiguredMaster> findByTemplateTrueAndTemplateSlug(String templateSlug);
// Used by onboard endpoint to copy named template rows for org
```

The `existsByOrganizationId` guard remains unchanged — it still blocks second onboarding.

---

### `masters/repository/ValidationRuleConfigRepository.java` (extend)

**Analog:** `masters/repository/PreconfiguredMasterRepository.java` (exact — same Spring Data interface pattern)

**Current full file** (`ValidationRuleConfigRepository.java` lines 1–13):
```java
@Repository
public interface ValidationRuleConfigRepository extends JpaRepository<ValidationRuleConfig, UUID> {
    List<ValidationRuleConfig> findByActiveTrueOrderByExecutionOrderAsc();
}
```

**1 new method to add:**
```java
boolean existsByRuleCode(String ruleCode);
// Used by DataInitializer.seedValidationRuleIfAbsent() idempotent guard
```

---

### `masters/repository/ValidationFindingRepository.java` (extend JPQL)

**Analog:** `masters/repository/ValidationFindingRepository.java` (self)

**Current `findFiltered` JPQL** (lines 21–30):
```java
@Query("SELECT f FROM ValidationFinding f WHERE f.uploadJobId = :jobId " +
       "AND (:category IS NULL OR f.category = :category) " +
       "AND (:severity IS NULL OR f.severity = :severity) " +
       "AND (:showResolved = true OR f.resolveStatus <> com.arktech.superaccountant.masters.models.ResolveStatus.RESOLVED)")
Page<ValidationFinding> findFiltered(
        @Param("jobId") UUID jobId,
        @Param("category") LedgerCategory category,
        @Param("severity") FindingSeverity severity,
        @Param("showResolved") boolean showResolved,
        Pageable pageable);
```

**Phase 3 change — replace the `showResolved` condition line only:**
```java
"AND (:showResolved = true OR (f.resolveStatus = com.arktech.superaccountant.masters.models.ResolveStatus.OPEN " +
"OR f.resolveStatus = com.arktech.superaccountant.masters.models.ResolveStatus.ACKNOWLEDGED))"
```

This treats `RESOLVED`, `ACCEPTED`, and `OVERRIDDEN` as "resolved-equivalent" when `showResolved=false`. Do not use `NOT IN` with enum literals in JPQL — the positive whitelist of OPEN/ACKNOWLEDGED is safer and more explicit.

---

### `login/config/DataInitializer.java` (extend — new seeder methods)

**Analog:** `login/config/DataInitializer.java` (self)

**Existing seeder guard pattern** (lines 47–57 for validation rules, lines 60–64 for template count guard):
```java
private void seedValidationRules() {
    if (validationRuleConfigRepository.count() == 0) {
        ValidationRuleConfig rule = new ValidationRuleConfig();
        rule.setRuleCode("MISMATCH_DETECTION");
        rule.setRuleName("Mismatch Detection");
        rule.setDescription("...");
        rule.setActive(true);
        rule.setExecutionOrder(1);
        validationRuleConfigRepository.save(rule);
    }
}
```

**Problem:** Current `count() == 0` guard prevents adding new rules once MISMATCH_DETECTION exists. Replace with idempotent per-rule guard:

```java
private void seedValidationRuleIfAbsent(String code, String name, String description, int order) {
    if (!validationRuleConfigRepository.existsByRuleCode(code)) {
        ValidationRuleConfig rule = new ValidationRuleConfig();
        rule.setRuleCode(code);
        rule.setRuleName(name);
        rule.setDescription(description);
        rule.setActive(true);
        rule.setExecutionOrder(order);
        validationRuleConfigRepository.save(rule);
    }
}
```

**Call from `run()` after replacing `seedValidationRules()` call:**
```java
seedValidationRuleIfAbsent("MISMATCH_DETECTION", "Mismatch Detection",
        "Compares uploaded ledgers against pre-configured masters.", 1);
seedValidationRuleIfAbsent("TDS_SECTION_MAPPING", "TDS Section Mapping",
        "Flags ledgers with no TDS section assigned.", 2);
seedValidationRuleIfAbsent("GST_APPLICABILITY", "GST Applicability",
        "Flags income/GST ledgers with no GST applicability type.", 3);
seedValidationRuleIfAbsent("HSN_SAC_CODE", "HSN/SAC Code Presence",
        "Flags taxable income/GST ledgers with no HSN or SAC code.", 4);
seedValidationRuleIfAbsent("GSTIN_PRESENCE", "GSTIN Presence",
        "Flags purchase ledgers with no GSTIN for GSTR-2B reconciliation.", 5);
```

**Existing template helper** (lines 111–123):
```java
private PreconfiguredMaster template(String name, LedgerCategory category,
                                      String parentGroup, Boolean gstApplicable, Boolean tdsApplicable) {
    PreconfiguredMaster m = new PreconfiguredMaster();
    m.setOrganizationId(null);
    m.setLedgerName(name);
    m.setCategory(category);
    m.setExpectedParentGroup(parentGroup);
    m.setExpectedGstApplicable(gstApplicable);
    m.setExpectedTdsApplicable(tdsApplicable);
    m.setTemplate(true);
    m.setActive(true);
    return m;
}
```

**Extend helper signature for Phase 3 fields:**
```java
private PreconfiguredMaster template(String name, LedgerCategory category,
                                      String parentGroup, Boolean gstApplicable, Boolean tdsApplicable,
                                      String templateSlug, String tdsSection,
                                      GstApplicabilityType gstType, String hsnSacCode, String gstin) {
    PreconfiguredMaster m = new PreconfiguredMaster();
    // ... (existing fields) ...
    m.setTemplateSlug(templateSlug);
    m.setTdsSection(tdsSection);
    m.setGstApplicabilityType(gstType);
    m.setHsnSacCode(hsnSacCode);
    m.setGstin(gstin);
    m.setTemplate(true);
    m.setActive(true);
    return m;
}
```

**Template count guard** (line 63–64) — must be changed to per-slug check because 3 slugs are needed:
```java
// Replace: if (templateCount > 0) return;
// With per-slug guard:
if (preconfiguredMasterRepository.findByTemplateTrueAndTemplateSlug("standard").isEmpty()) {
    // seed standard template
}
if (preconfiguredMasterRepository.findByTemplateTrueAndTemplateSlug("simplified").isEmpty()) {
    // seed simplified template
}
if (preconfiguredMasterRepository.findByTemplateTrueAndTemplateSlug("manufacturing").isEmpty()) {
    // seed manufacturing template
}
```

---

### `masters/controllers/PreconfiguredMastersController.java` (extend — onboard + update + toResponse)

**Analog:** `PreconfiguredMastersController.java` (self)

**Existing partial update pattern** (lines 96–104):
```java
if (request.getLedgerName() != null) master.setLedgerName(request.getLedgerName());
if (request.getCategory() != null) master.setCategory(request.getCategory());
if (request.getExpectedParentGroup() != null) master.setExpectedParentGroup(request.getExpectedParentGroup());
if (request.getExpectedGstApplicable() != null) master.setExpectedGstApplicable(request.getExpectedGstApplicable());
if (request.getExpectedTdsApplicable() != null) master.setExpectedTdsApplicable(request.getExpectedTdsApplicable());
master.setUpdatedAt(Instant.now());
```

**Add 4 more null-checks immediately after the existing 5:**
```java
if (request.getTdsSection() != null) master.setTdsSection(request.getTdsSection());
if (request.getGstApplicabilityType() != null)
    master.setGstApplicabilityType(GstApplicabilityType.valueOf(request.getGstApplicabilityType()));
if (request.getHsnSacCode() != null) master.setHsnSacCode(request.getHsnSacCode());
if (request.getGstin() != null) master.setGstin(request.getGstin());
```

**Existing onboard handler** (lines 162–200):
```java
if (request.isUseTemplate()) {
    List<PreconfiguredMaster> templates = masterRepository.findByTemplateTrue();
    List<PreconfiguredMaster> copies = new ArrayList<>();
    for (PreconfiguredMaster t : templates) {
        PreconfiguredMaster copy = new PreconfiguredMaster();
        copy.setOrganizationId(orgId);
        copy.setLedgerName(t.getLedgerName());
        copy.setCategory(t.getCategory());
        copy.setExpectedParentGroup(t.getExpectedParentGroup());
        copy.setExpectedGstApplicable(t.getExpectedGstApplicable());
        copy.setExpectedTdsApplicable(t.getExpectedTdsApplicable());
        copy.setTemplate(false);
        copies.add(copy);
    }
    masterRepository.saveAll(copies);
```

**Phase 3 change — add slug-based branch before `useTemplate` branch:**
```java
if (request.getTemplateSlug() != null && !request.getTemplateSlug().isBlank()) {
    String slug = request.getTemplateSlug();
    List<String> validSlugs = List.of("standard", "simplified", "manufacturing");
    if (!validSlugs.contains(slug)) {
        return ResponseEntity.badRequest().body("Unknown templateSlug: " + slug);
    }
    List<PreconfiguredMaster> templates = masterRepository.findByTemplateTrueAndTemplateSlug(slug);
    List<PreconfiguredMaster> copies = new ArrayList<>();
    for (PreconfiguredMaster t : templates) {
        PreconfiguredMaster copy = new PreconfiguredMaster();
        copy.setOrganizationId(orgId);
        copy.setLedgerName(t.getLedgerName());
        copy.setCategory(t.getCategory());
        copy.setExpectedParentGroup(t.getExpectedParentGroup());
        copy.setExpectedGstApplicable(t.getExpectedGstApplicable());
        copy.setExpectedTdsApplicable(t.getExpectedTdsApplicable());
        copy.setTdsSection(t.getTdsSection());
        copy.setGstApplicabilityType(t.getGstApplicabilityType());
        copy.setHsnSacCode(t.getHsnSacCode());
        copy.setGstin(t.getGstin());
        copy.setTemplate(false);
        copies.add(copy);
    }
    masterRepository.saveAll(copies);
    return ResponseEntity.ok(Map.of(
            "message", slug + " template applied. " + copies.size() + " masters configured.",
            "count", copies.size()
    ));
}
// existing useTemplate branch follows unchanged...
```

**Existing `toResponse()` builder** (lines 214–225):
```java
return PreconfiguredMasterResponse.builder()
        .id(m.getId())
        .ledgerName(m.getLedgerName())
        .category(m.getCategory())
        .expectedParentGroup(m.getExpectedParentGroup())
        .expectedGstApplicable(m.getExpectedGstApplicable())
        .expectedTdsApplicable(m.getExpectedTdsApplicable())
        .active(m.isActive())
        .createdAt(m.getCreatedAt())
        .updatedAt(m.getUpdatedAt())
        .build();
```

**Add 4 new fields to builder before `.build()`:**
```java
.tdsSection(m.getTdsSection())
.gstApplicabilityType(m.getGstApplicabilityType() != null
        ? m.getGstApplicabilityType().name() : null)
.hsnSacCode(m.getHsnSacCode())
.gstin(m.getGstin())
```

---

### `masters/controllers/UploadController.java` (extend — resolveFinding guard only)

**Analog:** `masters/controllers/UploadController.java` (self)

**Existing OPEN guard** (lines 212–214):
```java
if (request.getStatus() == ResolveStatus.OPEN) {
    return ResponseEntity.badRequest().body((Object) "Cannot set status back to OPEN.");
}
```

No change required to this guard. The new `ACCEPTED` and `OVERRIDDEN` values on `ResolveStatus` are valid non-OPEN values and will pass through automatically. No other controller changes needed for Phase 3 finding resolution.

---

### `Client/src/pages/MastersPage.tsx` (new page)

**Analog:** `Client/src/pages/DashboardPage.tsx` (role-match — same auth-guarded page shell pattern)

**Imports pattern** (`DashboardPage.tsx` lines 1–4):
```typescript
import { useAuthStore } from '@/store/authStore'
import { useNavigate } from '@tanstack/react-router'
import { OrganizationSelector } from '@/components/OrganizationSelector'
import { RoleBadge } from '@/components/RoleBadge'
```

**Page shell pattern** (`DashboardPage.tsx` lines 6–46):
```typescript
export function DashboardPage() {
  const { user, logout } = useAuthStore()
  const navigate = useNavigate()

  return (
    <div className="min-h-screen bg-[var(--color-bg)]">
      {/* Topbar */}
      <header className="h-16 bg-[var(--color-surface)] border-b border-[var(--color-border)] flex items-center justify-between px-6 shadow-[var(--shadow-sm)]">
        ...
      </header>
      <main className="p-6">
        ...
      </main>
    </div>
  )
}
```

**API call pattern** (`OrganizationSetupPage.tsx` lines 73–92):
```typescript
async function handleSubmit(e: React.FormEvent) {
  e.preventDefault()
  setLoading(true)
  try {
    await api.post('/organizations', { ...form })
    // success path
  } catch (err: unknown) {
    const msg =
      err instanceof Error && 'response' in err
        ? (err as { response?: { data?: { message?: string } } }).response?.data?.message
        : undefined
    setError(msg ?? 'Default error message')
  } finally {
    setLoading(false)
  }
}
```

**MastersPage structure** — two-tab page (Ledgers tab + Findings tab). Use React `useState` for active tab:
```typescript
import { useState } from 'react'
import { api } from '@/lib/api'
import { useAuthStore } from '@/store/authStore'

export function MastersPage() {
  const [activeTab, setActiveTab] = useState<'ledgers' | 'findings'>('ledgers')
  const [masters, setMasters] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  // ...

  return (
    <div className="min-h-screen bg-[var(--color-bg)]">
      {/* reuse same topbar structure from DashboardPage */}
      <main className="p-6">
        {/* Tab bar */}
        {/* Tab content: ledger table -or- findings list */}
      </main>
    </div>
  )
}
```

**Tailwind class constants** — copy `inputClass` pattern from `OrganizationSetupPage.tsx` line 94:
```typescript
const inputClass =
  'w-full h-11 px-3 rounded-[var(--radius-md)] border border-[var(--color-border)] bg-[var(--color-surface-raised)] text-[var(--color-text-primary)] text-sm placeholder:text-[var(--color-text-muted)] focus:outline-none focus:border-[var(--color-primary)] focus:ring-2 focus:ring-[var(--color-primary-subtle)] transition-colors'
```

---

### `Client/src/components/LedgerMappingPanel.tsx` (new side-sheet component)

**Analog:** `Client/src/pages/OrganizationSetupPage.tsx` (role-match — same form field patterns, same GSTIN regex, same blur validation pattern)

**GSTIN regex reuse** (`OrganizationSetupPage.tsx` line 6):
```typescript
const GSTIN_REGEX = /^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z]{1}[1-9A-Z]{1}Z[0-9A-Z]{1}$/
```

Either import this constant from `OrganizationSetupPage.tsx` (if extracted to shared util) or duplicate it in `LedgerMappingPanel.tsx` directly. Since no shared util exists yet, duplicate is acceptable for Phase 3.

**Blur validation pattern** (`OrganizationSetupPage.tsx` lines 35–51):
```typescript
function handleGstinBlur() {
  const val = form.gstin.trim()
  if (val === '') {
    setFieldErrors((prev) => ({ ...prev, gstin: '' }))
    setFieldValid((prev) => ({ ...prev, gstin: false }))
    return
  }
  if (GSTIN_REGEX.test(val)) {
    setFieldErrors((prev) => ({ ...prev, gstin: '' }))
    setFieldValid((prev) => ({ ...prev, gstin: true }))
  } else {
    setFieldErrors((prev) => ({
      ...prev,
      gstin: 'GSTIN must be 15 characters in the format: 22AAAAA0000A1Z5',
    }))
    setFieldValid((prev) => ({ ...prev, gstin: false }))
  }
}
```

**HSN/SAC code validation** — inline, no existing analog:
```typescript
const HSN_SAC_REGEX = /^\d{4,8}$/
function handleHsnBlur() {
  const val = form.hsnSacCode.trim()
  if (val && !HSN_SAC_REGEX.test(val)) {
    setFieldErrors((prev) => ({ ...prev, hsnSacCode: 'HSN/SAC code must be 4–8 digits' }))
  } else {
    setFieldErrors((prev) => ({ ...prev, hsnSacCode: '' }))
  }
}
```

**PUT API call pattern** (adapted from `OrganizationSetupPage.tsx` handleSubmit):
```typescript
async function handleSave() {
  setLoading(true)
  try {
    await api.put(`/v1/preconfigured-masters/${master.id}`, {
      tdsSection: form.tdsSection || null,
      gstApplicabilityType: form.gstApplicabilityType || null,
      hsnSacCode: form.hsnSacCode || null,
      gstin: form.gstin || null,
    })
    onSaved()   // callback to refresh masters list
    onClose()
  } catch (err: unknown) {
    const msg =
      err instanceof Error && 'response' in err
        ? (err as { response?: { data?: { message?: string } } }).response?.data?.message
        : undefined
    setError(msg ?? 'Failed to save. Try again.')
  } finally {
    setLoading(false)
  }
}
```

**Panel container pattern** — right-anchored side sheet via fixed positioning:
```typescript
<div
  className="fixed inset-y-0 right-0 w-96 bg-[var(--color-surface)] border-l border-[var(--color-border)] shadow-[var(--shadow-lg)] flex flex-col z-50"
  role="dialog"
  aria-modal="true"
>
  {/* header with close button, body with form fields, footer with Save/Cancel */}
</div>
```

**Focus trap** — on mount, set focus to the panel close button:
```typescript
useEffect(() => {
  closeButtonRef.current?.focus()
}, [])
```

---

### `Client/src/pages/OrganizationSetupPage.tsx` (extend — two-step flow)

**Analog:** `OrganizationSetupPage.tsx` (self)

**Current handleSubmit** (lines 73–92):
```typescript
async function handleSubmit(e: React.FormEvent) {
  e.preventDefault()
  setError('')
  setLoading(true)
  try {
    await api.post('/organizations', { ...form })
    window.alert('Organization created successfully')   // REMOVE in Phase 3
    navigate({ to: '/dashboard' })                      // REPLACE with step transition
  } catch (err: unknown) {
    ...
  } finally {
    setLoading(false)
  }
}
```

**Phase 3 replacement pattern:**
```typescript
const [step, setStep] = useState<1 | 2>(1)
const [createdOrgId, setCreatedOrgId] = useState<string | null>(null)

async function handleSubmit(e: React.FormEvent) {
  e.preventDefault()
  setError('')
  setLoading(true)
  try {
    const response = await api.post('/organizations', { ...form })
    // OrganizationController.createOrganization() returns {id, name, gstin, ...}
    // Confirmed: line 68-76 of OrganizationController.java — response includes "id"
    setCreatedOrgId(response.data.id)
    setStep(2)
  } catch (err: unknown) {
    ...
  } finally {
    setLoading(false)
  }
}
```

**Step 2 — template selector render:**
```typescript
if (step === 2 && createdOrgId) {
  return (
    <div className="min-h-screen flex items-center justify-center bg-[var(--color-bg)] px-4 py-8">
      {/* Template selector cards: Standard, Simplified, Manufacturing + Skip */}
      {/* On template select: POST /api/v1/preconfigured-masters/onboard {templateSlug: slug} */}
      {/* On success or Skip: navigate({ to: '/dashboard' }) */}
    </div>
  )
}
```

**Confirmed:** `OrganizationController.java` line 68 returns `Map.of("id", savedOrg.getId(), ...)` — `response.data.id` is valid.

---

### `Client/src/main.tsx` (extend — new /masters route)

**Analog:** `Client/src/main.tsx` (self)

**Existing route with auth guard** (lines 48–57):
```typescript
const dashboardRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/dashboard',
  beforeLoad: () => {
    if (!useAuthStore.getState().isAuthenticated) {
      throw redirect({ to: '/login' })
    }
  },
  component: DashboardPage,
})
```

**New /masters route (auth + role guard):**
```typescript
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

**routeTree.addChildren** (lines 70–76):
```typescript
const routeTree = rootRoute.addChildren([
  indexRoute,
  loginRoute,
  signupRoute,
  dashboardRoute,
  orgSetupRoute,
  mastersRoute,    // ADD HERE
])
```

**Import to add at top of main.tsx:**
```typescript
import { MastersPage } from '@/pages/MastersPage'
```

---

## Tests Pattern Assignments

### New rule unit tests (`TdsSectionMappingRuleTest.java`, `GstApplicabilityRuleTest.java`, `HsnSacCodeRuleTest.java`, `GstinPresenceRuleTest.java`)

**Analog:** `Service/.../masters/rules/MismatchDetectionRuleTest.java` (exact — same package, same test framework, same helper factories)

**Test class structure** (lines 14–51):
```java
class TdsSectionMappingRuleTest {

    private TdsSectionMappingRule rule;

    @BeforeEach
    void setUp() {
        rule = new TdsSectionMappingRule();
    }

    private PreconfiguredMaster configured(String name, LedgerCategory category, String tdsSection) {
        PreconfiguredMaster m = new PreconfiguredMaster();
        m.setOrganizationId(UUID.randomUUID());
        m.setLedgerName(name);
        m.setCategory(category);
        m.setTdsSection(tdsSection);
        m.setActive(true);
        return m;
    }

    private ValidationContext ctx(List<PreconfiguredMaster> masters) {
        return new ValidationContext(UUID.randomUUID(), "testuser", masters, Map.of());
    }
}
```

**Assertion pattern** (`MismatchDetectionRuleTest.java` lines 54–67):
```java
@Test
void configuredMasterPresentInUpload_noFinding() {
    PreconfiguredMaster m = configured("Cement", LedgerCategory.PURCHASE, null, null);
    ParsedLedger l = uploaded("Cement", LedgerCategory.PURCHASE, "Purchase Accounts", null, null);
    List<ValidationFinding> findings = rule.execute(ctx(List.of(m)), List.of(l));
    assertTrue(findings.isEmpty());
}
```

Phase 3 rules don't need `uploaded()` helper since they iterate configured masters only. Tests pass an empty `List.of()` for `parsedLedgers`.

---

## Shared Patterns

### Auth/Role Authorization on Controller Endpoints
**Source:** `masters/controllers/PreconfiguredMastersController.java` lines 58, 81, 107, 129, 160
**Apply to:** All mutating endpoints (POST, PUT, DELETE, /onboard)
```java
@PreAuthorize("hasRole('ACCOUNTANT') or hasRole('OPERATOR')")
// or
@PreAuthorize("hasRole('OWNER') or hasRole('ACCOUNTANT')")
```

### Org-Scoped Resource Guard
**Source:** `masters/controllers/PreconfiguredMastersController.java` lines 43–47, 88–90
**Apply to:** All controller methods that touch org data
```java
UUID orgId = requireOrgId(principal);
if (orgId == null) {
    return ResponseEntity.badRequest().body("User is not linked to an organization.");
}
// then filter: .filter(m -> m.getOrganizationId().equals(orgId))
```

### Enum + String Column on JPA Entity
**Source:** `masters/models/ValidationFinding.java` lines 25–42
**Apply to:** `PreconfiguredMaster` new fields (`gstApplicabilityType` as enum; `tdsSection`, `hsnSacCode`, `gstin` as String)
```java
@Enumerated(EnumType.STRING)
@Column(name = "column_name")
private SomeEnum field;
// or for String columns:
@Column(name = "column_name")
private String stringField;
```

### @Data @Builder Response DTO
**Source:** `masters/payload/response/FindingResponse.java` lines 1–28, `PreconfiguredMasterResponse.java` lines 1–22
**Apply to:** `PreconfiguredMasterResponse` (extend with new fields using same `@Data @Builder` annotations)

### Partial-Update Null-Check in Controller
**Source:** `masters/controllers/PreconfiguredMastersController.java` lines 96–104
**Apply to:** `PreconfiguredMastersController.update()` — extend the null-check block with 4 more fields
```java
if (request.getFieldName() != null) master.setFieldName(request.getFieldName());
```

### Error Handling in React Pages
**Source:** `Client/src/pages/OrganizationSetupPage.tsx` lines 81–88
**Apply to:** `MastersPage.tsx`, `LedgerMappingPanel.tsx`
```typescript
const msg =
  err instanceof Error && 'response' in err
    ? (err as { response?: { data?: { message?: string } } }).response?.data?.message
    : undefined
setError(msg ?? 'Default fallback message')
```

### Axios Instance (with auth token injection)
**Source:** `Client/src/lib/api.ts` lines 1–25
**Apply to:** All new API calls in `MastersPage.tsx` and `LedgerMappingPanel.tsx`
```typescript
import { api } from '@/lib/api'
// api already injects Authorization header from store and handles 401 logout
await api.get('/v1/preconfigured-masters')
await api.put(`/v1/preconfigured-masters/${id}`, payload)
await api.post('/v1/preconfigured-masters/onboard', { templateSlug })
```

### Tailwind Design Token Classes
**Source:** `Client/src/pages/OrganizationSetupPage.tsx` line 94, `Client/src/pages/DashboardPage.tsx` lines 18–20
**Apply to:** All new frontend files
```typescript
// Surface / background
'bg-[var(--color-bg)]'
'bg-[var(--color-surface)]'
'bg-[var(--color-surface-raised)]'
// Border
'border-[var(--color-border)]'
// Text
'text-[var(--color-text-primary)]'
'text-[var(--color-text-secondary)]'
'text-[var(--color-text-muted)]'
// Primary action
'bg-[var(--color-primary)]'
'hover:bg-[var(--color-primary-hover)]'
'focus:ring-[var(--color-primary-subtle)]'
// Danger
'text-[var(--color-danger)]'
'bg-[var(--color-danger-bg)]'
// Radii / shadows
'rounded-[var(--radius-md)]'
'rounded-[var(--radius-xl)]'
'shadow-[var(--shadow-sm)]'
'shadow-[var(--shadow-md)]'
```

---

## No Analog Found

All Phase 3 files have close analogs. No files fall into this category.

---

## Key Notes for Planner

1. **`existsByOrganizationId` guard impact on onboard:** The guard at `PreconfiguredMastersController.java` line 171 fires before the slug check. All orgs with any existing master rows (including template-seeded ones from a prior run) will be blocked from re-onboarding. The planner should decide in Plan 3 whether to change the guard to `existsByOrganizationIdAndTemplateFalse(orgId)` or keep it as-is (accepting that onboard is truly once-only).

2. **`seedValidationRules()` count guard is broken for adding new rules:** The current `count() == 0` guard in `DataInitializer.java` line 48 must be replaced with the `existsByRuleCode()` per-rule guard shown above before any of the 4 new rules can be seeded.

3. **`OrganizationController.createOrganization()` confirmed to return `id`:** Line 68–76 of `OrganizationController.java` uses `Map.of("id", savedOrg.getId(), ...)` — `response.data.id` is safe to use in `OrganizationSetupPage` Step 2 transition.

4. **`MismatchDetectionRule` severity values (`INFO/WARNING/ERROR`) conflict with UI filter:** Phase 3 rules emit `HIGH/MEDIUM/LOW`. The Findings tab filter in `MastersPage` should include all 6 severity values, or `MismatchDetectionRule` must be updated to use `HIGH/MEDIUM/LOW` (recommended in RESEARCH.md Pitfall 1).

5. **`LedgerMappingPanel.tsx` GSTIN regex:** Duplicate `GSTIN_REGEX` from `OrganizationSetupPage.tsx` line 6 into `LedgerMappingPanel.tsx` directly. No shared utility file exists yet.

---

## Metadata

**Analog search scope:** `Service/superaccountant/src/main/java/com/arktech/superaccountant/masters/`, `Service/superaccountant/src/test/`, `Client/src/`
**Files read:** 30
**Pattern extraction date:** 2026-05-03
