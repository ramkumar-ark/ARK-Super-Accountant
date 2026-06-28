package com.arktech.superaccountant.masters.rules;

import com.arktech.superaccountant.masters.models.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class GstApplicabilityRuleTest {

    private GstApplicabilityRule rule;

    @BeforeEach
    void setUp() {
        rule = new GstApplicabilityRule();
    }

    private PreconfiguredMaster configured(String name, LedgerCategory category, GstApplicabilityType gstType) {
        PreconfiguredMaster m = new PreconfiguredMaster();
        m.setOrganizationId(UUID.randomUUID());
        m.setLedgerName(name);
        m.setCategory(category);
        m.setGstApplicabilityType(gstType);
        m.setActive(true);
        return m;
    }

    private ValidationContext ctx(List<PreconfiguredMaster> masters) {
        return new ValidationContext(UUID.randomUUID(), "testuser", masters, Map.of());
    }

    @Test
    void incomeCategoryNullGstType_emitsMediumFinding() {
        PreconfiguredMaster m = configured("Sales Account", LedgerCategory.INCOME, null);
        List<ValidationFinding> findings = rule.execute(ctx(List.of(m)), List.of());
        assertEquals(1, findings.size());
        assertEquals(FindingSeverity.MEDIUM, findings.get(0).getSeverity());
    }

    @Test
    void gstCategoryNullGstType_emitsMediumFinding() {
        PreconfiguredMaster m = configured("Output CGST @18%", LedgerCategory.GST, null);
        List<ValidationFinding> findings = rule.execute(ctx(List.of(m)), List.of());
        assertEquals(1, findings.size());
        assertEquals(FindingSeverity.MEDIUM, findings.get(0).getSeverity());
    }

    @Test
    void purchaseCategoryNullGstType_emitsLowFinding() {
        PreconfiguredMaster m = configured("Cement Purchase", LedgerCategory.PURCHASE, null);
        List<ValidationFinding> findings = rule.execute(ctx(List.of(m)), List.of());
        assertEquals(1, findings.size());
        assertEquals(FindingSeverity.LOW, findings.get(0).getSeverity());
    }

    @Test
    void expenseCategoryNullGstType_emitsLowFinding() {
        PreconfiguredMaster m = configured("Labour Charges", LedgerCategory.EXPENSE, null);
        List<ValidationFinding> findings = rule.execute(ctx(List.of(m)), List.of());
        assertEquals(1, findings.size());
        assertEquals(FindingSeverity.LOW, findings.get(0).getSeverity());
    }

    @Test
    void tdsCategoryNullGstType_emitsLowFinding() {
        PreconfiguredMaster m = configured("TDS Payable 194C", LedgerCategory.TDS, null);
        List<ValidationFinding> findings = rule.execute(ctx(List.of(m)), List.of());
        assertEquals(1, findings.size());
        assertEquals(FindingSeverity.LOW, findings.get(0).getSeverity());
    }

    @Test
    void otherCategoryNullGstType_emitsLowFinding() {
        PreconfiguredMaster m = configured("Miscellaneous", LedgerCategory.OTHER, null);
        List<ValidationFinding> findings = rule.execute(ctx(List.of(m)), List.of());
        assertEquals(1, findings.size());
        assertEquals(FindingSeverity.LOW, findings.get(0).getSeverity());
    }

    @Test
    void gstTypeSet_noFinding() {
        PreconfiguredMaster m = configured("Sales Account", LedgerCategory.INCOME, GstApplicabilityType.TAXABLE);
        List<ValidationFinding> findings = rule.execute(ctx(List.of(m)), List.of());
        assertTrue(findings.isEmpty());
    }

    @Test
    void gstTypeSetExempt_noFinding() {
        PreconfiguredMaster m = configured("Exempt Sales", LedgerCategory.INCOME, GstApplicabilityType.EXEMPT);
        List<ValidationFinding> findings = rule.execute(ctx(List.of(m)), List.of());
        assertTrue(findings.isEmpty());
    }

    @Test
    void emptyMastersList_noFindings() {
        List<ValidationFinding> findings = rule.execute(ctx(List.of()), List.of());
        assertTrue(findings.isEmpty());
    }

    @Test
    void findingHasCorrectRuleCode() {
        PreconfiguredMaster m = configured("Sales Account", LedgerCategory.INCOME, null);
        List<ValidationFinding> findings = rule.execute(ctx(List.of(m)), List.of());
        assertEquals("GST_APPLICABILITY", findings.get(0).getRuleCode());
    }

    @Test
    void findingHasResolveStatusOpen() {
        PreconfiguredMaster m = configured("Sales Account", LedgerCategory.INCOME, null);
        List<ValidationFinding> findings = rule.execute(ctx(List.of(m)), List.of());
        assertEquals(ResolveStatus.OPEN, findings.get(0).getResolveStatus());
    }

    @Test
    void getRuleCode_returnsGstApplicability() {
        assertEquals("GST_APPLICABILITY", rule.getRuleCode());
    }
}
