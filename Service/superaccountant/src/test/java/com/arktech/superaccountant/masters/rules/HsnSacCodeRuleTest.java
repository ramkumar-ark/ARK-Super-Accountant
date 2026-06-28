package com.arktech.superaccountant.masters.rules;

import com.arktech.superaccountant.masters.models.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class HsnSacCodeRuleTest {

    private HsnSacCodeRule rule;

    @BeforeEach
    void setUp() {
        rule = new HsnSacCodeRule();
    }

    private PreconfiguredMaster configured(String name, LedgerCategory category, String hsnSacCode) {
        PreconfiguredMaster m = new PreconfiguredMaster();
        m.setOrganizationId(UUID.randomUUID());
        m.setLedgerName(name);
        m.setCategory(category);
        m.setHsnSacCode(hsnSacCode);
        m.setActive(true);
        return m;
    }

    private ValidationContext ctx(List<PreconfiguredMaster> masters) {
        return new ValidationContext(UUID.randomUUID(), "testuser", masters, Map.of());
    }

    @Test
    void incomeCategoryNullHsnSac_emitsMediumFinding() {
        PreconfiguredMaster m = configured("Sales Account", LedgerCategory.INCOME, null);
        List<ValidationFinding> findings = rule.execute(ctx(List.of(m)), List.of());
        assertEquals(1, findings.size());
        assertEquals(FindingSeverity.MEDIUM, findings.get(0).getSeverity());
    }

    @Test
    void gstCategoryNullHsnSac_emitsMediumFinding() {
        PreconfiguredMaster m = configured("Output CGST @18%", LedgerCategory.GST, null);
        List<ValidationFinding> findings = rule.execute(ctx(List.of(m)), List.of());
        assertEquals(1, findings.size());
        assertEquals(FindingSeverity.MEDIUM, findings.get(0).getSeverity());
    }

    @Test
    void purchaseCategoryNullHsnSac_noFinding() {
        PreconfiguredMaster m = configured("Cement Purchase", LedgerCategory.PURCHASE, null);
        List<ValidationFinding> findings = rule.execute(ctx(List.of(m)), List.of());
        assertTrue(findings.isEmpty());
    }

    @Test
    void expenseCategoryNullHsnSac_noFinding() {
        PreconfiguredMaster m = configured("Labour Charges", LedgerCategory.EXPENSE, null);
        List<ValidationFinding> findings = rule.execute(ctx(List.of(m)), List.of());
        assertTrue(findings.isEmpty());
    }

    @Test
    void tdsCategoryNullHsnSac_noFinding() {
        PreconfiguredMaster m = configured("TDS Payable 194C", LedgerCategory.TDS, null);
        List<ValidationFinding> findings = rule.execute(ctx(List.of(m)), List.of());
        assertTrue(findings.isEmpty());
    }

    @Test
    void otherCategoryNullHsnSac_noFinding() {
        PreconfiguredMaster m = configured("Miscellaneous", LedgerCategory.OTHER, null);
        List<ValidationFinding> findings = rule.execute(ctx(List.of(m)), List.of());
        assertTrue(findings.isEmpty());
    }

    @Test
    void incomeWithHsnSacSet_noFinding() {
        PreconfiguredMaster m = configured("Sales Account", LedgerCategory.INCOME, "998311");
        List<ValidationFinding> findings = rule.execute(ctx(List.of(m)), List.of());
        assertTrue(findings.isEmpty());
    }

    @Test
    void gstWithHsnSacSet_noFinding() {
        PreconfiguredMaster m = configured("Output CGST @18%", LedgerCategory.GST, "9954");
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
        assertEquals("HSN_SAC_CODE", findings.get(0).getRuleCode());
    }

    @Test
    void findingHasResolveStatusOpen() {
        PreconfiguredMaster m = configured("Sales Account", LedgerCategory.INCOME, null);
        List<ValidationFinding> findings = rule.execute(ctx(List.of(m)), List.of());
        assertEquals(ResolveStatus.OPEN, findings.get(0).getResolveStatus());
    }

    @Test
    void findingHasLedgerName() {
        PreconfiguredMaster m = configured("Sales Account", LedgerCategory.INCOME, null);
        List<ValidationFinding> findings = rule.execute(ctx(List.of(m)), List.of());
        assertEquals("Sales Account", findings.get(0).getLedgerName());
    }

    @Test
    void getRuleCode_returnsHsnSacCode() {
        assertEquals("HSN_SAC_CODE", rule.getRuleCode());
    }
}
