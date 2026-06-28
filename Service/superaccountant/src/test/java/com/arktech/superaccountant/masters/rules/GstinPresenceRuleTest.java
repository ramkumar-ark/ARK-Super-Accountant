package com.arktech.superaccountant.masters.rules;

import com.arktech.superaccountant.masters.models.*;
import com.arktech.superaccountant.masters.models.GstApplicabilityType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class GstinPresenceRuleTest {

    private GstinPresenceRule rule;

    @BeforeEach
    void setUp() {
        rule = new GstinPresenceRule();
    }

    private PreconfiguredMaster configured(String name, LedgerCategory category, String gstin) {
        PreconfiguredMaster m = new PreconfiguredMaster();
        m.setOrganizationId(UUID.randomUUID());
        m.setLedgerName(name);
        m.setCategory(category);
        m.setGstin(gstin);
        m.setActive(true);
        m.setGstApplicabilityType(GstApplicabilityType.TAXABLE);
        return m;
    }

    private ValidationContext ctx(List<PreconfiguredMaster> masters) {
        return new ValidationContext(UUID.randomUUID(), "testuser", masters, Map.of());
    }

    @Test
    void creditorCategoryNullGstin_emitsHighFinding() {
        PreconfiguredMaster m = configured("ABC Traders", LedgerCategory.CREDITOR, null);
        List<ValidationFinding> findings = rule.execute(ctx(List.of(m)), List.of());
        assertEquals(1, findings.size());
        assertEquals(FindingSeverity.HIGH, findings.get(0).getSeverity());
    }

    @Test
    void purchaseCategoryNullGstin_noFinding() {
        PreconfiguredMaster m = configured("Cement Purchase", LedgerCategory.PURCHASE, null);
        List<ValidationFinding> findings = rule.execute(ctx(List.of(m)), List.of());
        assertTrue(findings.isEmpty());
    }

    @Test
    void expenseCategoryNullGstin_noFinding() {
        PreconfiguredMaster m = configured("Labour Charges", LedgerCategory.EXPENSE, null);
        List<ValidationFinding> findings = rule.execute(ctx(List.of(m)), List.of());
        assertTrue(findings.isEmpty());
    }

    @Test
    void incomeCategoryNullGstin_noFinding() {
        PreconfiguredMaster m = configured("Sales Account", LedgerCategory.INCOME, null);
        List<ValidationFinding> findings = rule.execute(ctx(List.of(m)), List.of());
        assertTrue(findings.isEmpty());
    }

    @Test
    void gstCategoryNullGstin_noFinding() {
        PreconfiguredMaster m = configured("Output CGST @18%", LedgerCategory.GST, null);
        List<ValidationFinding> findings = rule.execute(ctx(List.of(m)), List.of());
        assertTrue(findings.isEmpty());
    }

    @Test
    void tdsCategoryNullGstin_noFinding() {
        PreconfiguredMaster m = configured("TDS Payable 194C", LedgerCategory.TDS, null);
        List<ValidationFinding> findings = rule.execute(ctx(List.of(m)), List.of());
        assertTrue(findings.isEmpty());
    }

    @Test
    void otherCategoryNullGstin_noFinding() {
        PreconfiguredMaster m = configured("Miscellaneous", LedgerCategory.OTHER, null);
        List<ValidationFinding> findings = rule.execute(ctx(List.of(m)), List.of());
        assertTrue(findings.isEmpty());
    }

    @Test
    void creditorWithGstinSet_noFinding() {
        PreconfiguredMaster m = configured("ABC Traders", LedgerCategory.CREDITOR, "29ABCDE1234F1Z5");
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
        PreconfiguredMaster m = configured("ABC Traders", LedgerCategory.CREDITOR, null);
        List<ValidationFinding> findings = rule.execute(ctx(List.of(m)), List.of());
        assertEquals("GSTIN_PRESENCE", findings.get(0).getRuleCode());
    }

    @Test
    void findingHasResolveStatusOpen() {
        PreconfiguredMaster m = configured("ABC Traders", LedgerCategory.CREDITOR, null);
        List<ValidationFinding> findings = rule.execute(ctx(List.of(m)), List.of());
        assertEquals(ResolveStatus.OPEN, findings.get(0).getResolveStatus());
    }

    @Test
    void findingHasLedgerName() {
        PreconfiguredMaster m = configured("ABC Traders", LedgerCategory.CREDITOR, null);
        List<ValidationFinding> findings = rule.execute(ctx(List.of(m)), List.of());
        assertEquals("ABC Traders", findings.get(0).getLedgerName());
    }

    @Test
    void getRuleCode_returnsGstinPresence() {
        assertEquals("GSTIN_PRESENCE", rule.getRuleCode());
    }
}
