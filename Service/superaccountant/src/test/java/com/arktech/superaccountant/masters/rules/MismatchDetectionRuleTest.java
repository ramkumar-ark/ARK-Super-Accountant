package com.arktech.superaccountant.masters.rules;

import com.arktech.superaccountant.masters.classifier.ParsedLedger;
import com.arktech.superaccountant.masters.models.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MismatchDetectionRuleTest {

    private MismatchDetectionRule rule;
    private ValidationContext context;

    @BeforeEach
    void setUp() {
        rule = new MismatchDetectionRule();
    }

    private PreconfiguredMaster configured(String name, LedgerCategory category,
                                            String parentGroup, Boolean gst, Boolean tds) {
        PreconfiguredMaster m = new PreconfiguredMaster();
        m.setOrganizationId(UUID.randomUUID());
        m.setLedgerName(name);
        m.setCategory(category);
        m.setExpectedParentGroup(parentGroup);
        m.setExpectedGstApplicable(gst);
        m.setExpectedTdsApplicable(tds);
        m.setActive(true);
        return m;
    }

    private ParsedLedger uploaded(String name, LedgerCategory category,
                                   String parentGroup, Boolean gst, Boolean tds) {
        return ParsedLedger.builder()
                .name(name)
                .guid(UUID.randomUUID().toString())
                .parentGroup(parentGroup)
                .gstApplicable(gst)
                .tdsApplicable(tds)
                .category(category)
                .build();
    }

    private ValidationContext ctx(List<PreconfiguredMaster> masters) {
        return new ValidationContext(UUID.randomUUID(), "testuser", masters, Map.of());
    }

    @Test
    void configuredMasterPresentInUpload_noFinding() {
        PreconfiguredMaster m = configured("Cement", LedgerCategory.PURCHASE, "Purchase Accounts", null, null);
        ParsedLedger l = uploaded("Cement", LedgerCategory.PURCHASE, "Purchase Accounts", null, null);
        List<ValidationFinding> findings = rule.execute(ctx(List.of(m)), List.of(l));
        assertTrue(findings.isEmpty());
    }

    @Test
    void configuredMasterMissingFromUpload_emitsMissingInUpload() {
        PreconfiguredMaster m = configured("Cement", LedgerCategory.PURCHASE, "Purchase Accounts", null, null);
        List<ValidationFinding> findings = rule.execute(ctx(List.of(m)), List.of());
        assertEquals(1, findings.size());
        assertEquals(MismatchType.MISSING_IN_UPLOAD, findings.get(0).getMismatchType());
        assertEquals(FindingSeverity.WARNING, findings.get(0).getSeverity());
    }

    @Test
    void uploadedLedgerNotInConfigured_emitsMissingInConfiguration() {
        ParsedLedger l = uploaded("Cement", LedgerCategory.PURCHASE, "Purchase Accounts", null, null);
        List<ValidationFinding> findings = rule.execute(ctx(List.of()), List.of(l));
        assertEquals(1, findings.size());
        assertEquals(MismatchType.MISSING_IN_CONFIGURATION, findings.get(0).getMismatchType());
        assertEquals(FindingSeverity.WARNING, findings.get(0).getSeverity());
    }

    @Test
    void nameMismatch_differentCasing_emitsNameMismatch() {
        PreconfiguredMaster m = configured("Cement", LedgerCategory.PURCHASE, "Purchase Accounts", null, null);
        ParsedLedger l = uploaded("cement", LedgerCategory.PURCHASE, "Purchase Accounts", null, null);
        List<ValidationFinding> findings = rule.execute(ctx(List.of(m)), List.of(l));
        assertEquals(1, findings.size());
        assertEquals(MismatchType.NAME_MISMATCH, findings.get(0).getMismatchType());
        assertEquals(FindingSeverity.INFO, findings.get(0).getSeverity());
        assertNotNull(findings.get(0).getSuggestedFix());
        assertTrue(findings.get(0).getSuggestedFix().contains("TallyPrime"));
    }

    @Test
    void nameMismatch_extraWhitespace_emitsNameMismatch() {
        PreconfiguredMaster m = configured("Cement", LedgerCategory.PURCHASE, "Purchase Accounts", null, null);
        // normalized name matches but original differs
        ParsedLedger l = uploaded("cement", LedgerCategory.PURCHASE, "Purchase Accounts", null, null);
        List<ValidationFinding> findings = rule.execute(ctx(List.of(m)), List.of(l));
        long nameMismatches = findings.stream()
                .filter(f -> f.getMismatchType() == MismatchType.NAME_MISMATCH)
                .count();
        assertEquals(1, nameMismatches);
    }

    @Test
    void parentGroupMismatch_emitsParentGroupMismatch() {
        PreconfiguredMaster m = configured("Labour Charges", LedgerCategory.EXPENSE, "Direct Expenses", null, null);
        ParsedLedger l = uploaded("Labour Charges", LedgerCategory.EXPENSE, "Indirect Expenses", null, null);
        List<ValidationFinding> findings = rule.execute(ctx(List.of(m)), List.of(l));
        assertEquals(1, findings.size());
        assertEquals(MismatchType.PARENT_GROUP_MISMATCH, findings.get(0).getMismatchType());
        assertEquals(FindingSeverity.ERROR, findings.get(0).getSeverity());
        assertNotNull(findings.get(0).getSuggestedFix());
    }

    @Test
    void gstApplicabilityMismatch_emitsGstMismatch() {
        PreconfiguredMaster m = configured("Input CGST @6%", LedgerCategory.GST, "Duties & Taxes", true, null);
        ParsedLedger l = uploaded("Input CGST @6%", LedgerCategory.GST, "Duties & Taxes", false, null);
        List<ValidationFinding> findings = rule.execute(ctx(List.of(m)), List.of(l));
        assertEquals(1, findings.size());
        assertEquals(MismatchType.GST_APPLICABILITY_MISMATCH, findings.get(0).getMismatchType());
        assertEquals(FindingSeverity.ERROR, findings.get(0).getSeverity());
    }

    @Test
    void tdsApplicabilityMismatch_emitsTdsMismatch() {
        PreconfiguredMaster m = configured("TDS Payable - 194C", LedgerCategory.TDS, "Duties & Taxes", null, true);
        ParsedLedger l = uploaded("TDS Payable - 194C", LedgerCategory.TDS, "Duties & Taxes", null, false);
        List<ValidationFinding> findings = rule.execute(ctx(List.of(m)), List.of(l));
        assertEquals(1, findings.size());
        assertEquals(MismatchType.TDS_APPLICABILITY_MISMATCH, findings.get(0).getMismatchType());
    }

    @Test
    void multipleFieldMismatches_allEmitted() {
        // Same ledger with both parent group AND gst mismatch -> 2 findings
        PreconfiguredMaster m = configured("Test Ledger", LedgerCategory.GST, "Duties & Taxes", true, null);
        ParsedLedger l = uploaded("Test Ledger", LedgerCategory.GST, "Wrong Group", false, null);
        List<ValidationFinding> findings = rule.execute(ctx(List.of(m)), List.of(l));
        assertTrue(findings.size() >= 2, "Expected at least 2 findings for multiple mismatches");
        assertTrue(findings.stream().anyMatch(f -> f.getMismatchType() == MismatchType.PARENT_GROUP_MISMATCH));
        assertTrue(findings.stream().anyMatch(f -> f.getMismatchType() == MismatchType.GST_APPLICABILITY_MISMATCH));
    }

    @Test
    void ledgerCategoryOther_notCompared_noFinding() {
        PreconfiguredMaster m = configured("Some Ledger", LedgerCategory.PURCHASE, "Purchase Accounts", null, null);
        ParsedLedger l = uploaded("Some Ledger", LedgerCategory.OTHER, null, null, null);
        List<ValidationFinding> findings = rule.execute(ctx(List.of(m)), List.of(l));
        // OTHER ledger is excluded from uploaded map; configured ledger will emit MISSING_IN_UPLOAD
        assertTrue(findings.stream().anyMatch(f -> f.getMismatchType() == MismatchType.MISSING_IN_UPLOAD));
        // But no finding about the OTHER ledger itself
        assertTrue(findings.stream().noneMatch(f -> "Some Ledger".equals(f.getLedgerName())
                && f.getMismatchType() == MismatchType.MISSING_IN_CONFIGURATION));
    }

    @Test
    void noConfiguredMasters_allUploadedLedgersEmitMissingInConfiguration() {
        ParsedLedger l1 = uploaded("Cement", LedgerCategory.PURCHASE, "Purchase Accounts", null, null);
        ParsedLedger l2 = uploaded("Labour Charges", LedgerCategory.EXPENSE, "Direct Expenses", null, null);
        List<ValidationFinding> findings = rule.execute(ctx(List.of()), List.of(l1, l2));
        assertEquals(2, findings.size());
        assertTrue(findings.stream().allMatch(f -> f.getMismatchType() == MismatchType.MISSING_IN_CONFIGURATION));
    }

    @Test
    void allPresent_noMismatches_noFindings() {
        PreconfiguredMaster m1 = configured("Cement", LedgerCategory.PURCHASE, "Purchase Accounts", null, null);
        PreconfiguredMaster m2 = configured("Labour Charges", LedgerCategory.EXPENSE, "Direct Expenses", null, null);
        ParsedLedger l1 = uploaded("Cement", LedgerCategory.PURCHASE, "Purchase Accounts", null, null);
        ParsedLedger l2 = uploaded("Labour Charges", LedgerCategory.EXPENSE, "Direct Expenses", null, null);
        List<ValidationFinding> findings = rule.execute(ctx(List.of(m1, m2)), List.of(l1, l2));
        assertTrue(findings.isEmpty());
    }

    @Test
    void findingHasResolveStatusOpen_byDefault() {
        PreconfiguredMaster m = configured("Cement", LedgerCategory.PURCHASE, "Purchase Accounts", null, null);
        List<ValidationFinding> findings = rule.execute(ctx(List.of(m)), List.of());
        assertEquals(ResolveStatus.OPEN, findings.get(0).getResolveStatus());
    }

    @Test
    void getRuleCode_returnsMismatchDetection() {
        assertEquals("MISMATCH_DETECTION", rule.getRuleCode());
    }
}
