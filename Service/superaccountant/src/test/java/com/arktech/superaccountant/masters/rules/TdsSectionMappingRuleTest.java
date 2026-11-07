package com.arktech.superaccountant.masters.rules;

import com.arktech.superaccountant.masters.models.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

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

    @Test
    void tdsCategoryNullSection_skipped_noFinding() {
        PreconfiguredMaster m = configured("TDS Payable 194C", LedgerCategory.TDS, null);
        List<ValidationFinding> findings = rule.execute(ctx(List.of(m)), List.of());
        assertTrue(findings.isEmpty());
    }

    @Test
    void purchaseCategoryNullSection_emitsMediumFinding() {
        PreconfiguredMaster m = configured("Cement Purchase", LedgerCategory.PURCHASE, null);
        List<ValidationFinding> findings = rule.execute(ctx(List.of(m)), List.of());
        assertEquals(1, findings.size());
        assertEquals(FindingSeverity.MEDIUM, findings.get(0).getSeverity());
    }

    @Test
    void expenseCategoryNullSection_emitsMediumFinding() {
        PreconfiguredMaster m = configured("Labour Charges", LedgerCategory.EXPENSE, null);
        List<ValidationFinding> findings = rule.execute(ctx(List.of(m)), List.of());
        assertEquals(1, findings.size());
        assertEquals(FindingSeverity.MEDIUM, findings.get(0).getSeverity());
    }

    @Test
    void incomeCategoryNullSection_outOfScope_noFinding() {
        PreconfiguredMaster m = configured("Sales Account", LedgerCategory.INCOME, null);
        assertTrue(rule.execute(ctx(List.of(m)), List.of()).isEmpty());
    }

    private PreconfiguredMaster inGroups(String name, LedgerCategory category, String chain) {
        PreconfiguredMaster m = configured(name, category, null);
        m.setGroupChain(chain);
        return m;
    }

    @Test
    void fixedAssetLedger_emitsFinding_includingCustomSubGroup() {
        assertEquals(1, rule.execute(ctx(List.of(inGroups("Plant", LedgerCategory.OTHER, "fixed assets"))), List.of()).size());
        assertEquals(1, rule.execute(ctx(List.of(inGroups("Crane", LedgerCategory.OTHER, "heavy machinery|fixed assets"))), List.of()).size());
    }

    @Test
    void customSubGroupOfExpenses_emitsFinding() {
        assertEquals(1, rule.execute(ctx(List.of(inGroups("Site Costs", LedgerCategory.OTHER, "site costs|direct expenses"))), List.of()).size());
    }

    @Test
    void creditors_neverInScope_evenIfCategorisedAsExpense() {
        assertTrue(rule.execute(ctx(List.of(
                inGroups("ABC Traders", LedgerCategory.CREDITOR, "sundry creditors"),
                inGroups("Mislabelled", LedgerCategory.EXPENSE, "sundry creditors"))), List.of()).isEmpty());
    }

    @Test
    void debtorsAndLoans_notInScope() {
        assertTrue(rule.execute(ctx(List.of(
                inGroups("Customer", LedgerCategory.OTHER, "sundry debtors"),
                inGroups("Loan", LedgerCategory.OTHER, "unsecured loans"))), List.of()).isEmpty());
    }

    @Test
    void gstCategoryNullSection_skipped_noFinding() {
        PreconfiguredMaster m = configured("Output CGST @18%", LedgerCategory.GST, null);
        List<ValidationFinding> findings = rule.execute(ctx(List.of(m)), List.of());
        assertTrue(findings.isEmpty());
    }

    @Test
    void otherCategoryNullSection_skipped_noFinding() {
        PreconfiguredMaster m = configured("Miscellaneous Expenses", LedgerCategory.OTHER, null);
        List<ValidationFinding> findings = rule.execute(ctx(List.of(m)), List.of());
        assertTrue(findings.isEmpty());
    }

    @Test
    void tdsSectionSet_noFinding() {
        PreconfiguredMaster m = configured("Labour Charges", LedgerCategory.EXPENSE, "194C");
        List<ValidationFinding> findings = rule.execute(ctx(List.of(m)), List.of());
        assertTrue(findings.isEmpty());
    }

    @Test
    void tdsSectionSetToNotSubject_noFinding() {
        PreconfiguredMaster m = configured("Rental Income", LedgerCategory.INCOME, "NOT_SUBJECT");
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
        PreconfiguredMaster m = configured("Labour Charges", LedgerCategory.EXPENSE, null);
        List<ValidationFinding> findings = rule.execute(ctx(List.of(m)), List.of());
        assertEquals("TDS_SECTION_MAPPING", findings.get(0).getRuleCode());
    }

    @Test
    void findingHasResolveStatusOpen() {
        PreconfiguredMaster m = configured("Labour Charges", LedgerCategory.EXPENSE, null);
        List<ValidationFinding> findings = rule.execute(ctx(List.of(m)), List.of());
        assertEquals(ResolveStatus.OPEN, findings.get(0).getResolveStatus());
    }

    @Test
    void findingHasLedgerName() {
        PreconfiguredMaster m = configured("Labour Charges", LedgerCategory.EXPENSE, null);
        List<ValidationFinding> findings = rule.execute(ctx(List.of(m)), List.of());
        assertEquals("Labour Charges", findings.get(0).getLedgerName());
    }

    @Test
    void getRuleCode_returnsTdsSectionMapping() {
        assertEquals("TDS_SECTION_MAPPING", rule.getRuleCode());
    }
}
