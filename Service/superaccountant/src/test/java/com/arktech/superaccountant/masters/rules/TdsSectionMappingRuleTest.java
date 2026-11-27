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
        var sectionService = org.mockito.Mockito.mock(
                com.arktech.superaccountant.statutory.services.StatutorySectionService.class);
        org.mockito.Mockito.lenient().when(sectionService.newActRefsByCode()).thenReturn(java.util.Map.of(
                "194C", "393(1) [Table: Sl. No. 6(i)]",
                "194J_A", "393(1) [Table: Sl. No. 6(iii).D(a)]"));
        org.springframework.test.util.ReflectionTestUtils.setField(rule, "statutorySectionService", sectionService);
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
        return ctx(masters, true);
    }

    private ValidationContext ctx(List<PreconfiguredMaster> masters, boolean tdsDeductor) {
        return new ValidationContext(UUID.randomUUID(), "testuser", masters, Map.of(), tdsDeductor);
    }

    @Test
    void noFindingsWhenNotDeductor() {
        PreconfiguredMaster expense = configured("Labour Charges", LedgerCategory.EXPENSE, null);
        PreconfiguredMaster purchase = configured("Cement Purchase", LedgerCategory.PURCHASE, null);
        assertTrue(rule.execute(ctx(List.of(expense, purchase), false), List.of()).isEmpty());
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

    @Test
    void suggestedFixShowsOldAndNewActNamesWithoutBeingPeriodSpecific() {
        PreconfiguredMaster m = configured("Labour Charges", LedgerCategory.EXPENSE, null);
        String fix = rule.execute(ctx(List.of(m)), List.of()).get(0).getSuggestedFix();
        assertTrue(fix.contains("194C / 393(1) [Table: Sl. No. 6(i)] \u2013 Payments to contractors and sub-contractors"), fix);
        assertTrue(fix.contains("194J_A / 393(1) [Table: Sl. No. 6(iii).D(a)] \u2013 Fees for technical services, royalty for films, call centre"), fix);
    }
}
