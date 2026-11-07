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
        m.setExpectedGstApplicable(true);
        m.setActive(true);
        return m;
    }

    private PreconfiguredMaster configured(String name, LedgerCategory category, Boolean gstApplicable, String parentGroup) {
        PreconfiguredMaster m = configured(name, category, null);
        m.setExpectedGstApplicable(gstApplicable);
        m.setExpectedParentGroup(parentGroup);
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
    void gstCategoryDutiesAndTaxesLedger_noFinding() {
        // Output CGST / Input IGST etc. are tax-account ledgers, not goods or services: no HSN/SAC.
        PreconfiguredMaster m = configured("Output CGST @18%", LedgerCategory.GST, true, "Duties & Taxes");
        assertTrue(rule.execute(ctx(List.of(m)), List.of()).isEmpty());
    }

    @Test
    void expenseWithGstApplicableAndNoHsnSac_emitsMediumFinding() {
        PreconfiguredMaster m = configured("Professional Fees", LedgerCategory.EXPENSE, true, "Indirect Expenses");
        List<ValidationFinding> findings = rule.execute(ctx(List.of(m)), List.of());
        assertEquals(1, findings.size());
        assertEquals(FindingSeverity.MEDIUM, findings.get(0).getSeverity());
    }

    @Test
    void expenseWithoutGstApplicability_noFinding() {
        assertTrue(rule.execute(ctx(List.of(
                configured("Salary & Wages", LedgerCategory.EXPENSE, false, "Indirect Expenses"))), List.of()).isEmpty());
    }

    @Test
    void incomeWithoutGstApplicability_noFinding() {
        assertTrue(rule.execute(ctx(List.of(
                configured("Interest Received", LedgerCategory.INCOME, false, "Indirect Incomes"))), List.of()).isEmpty());
    }

    @Test
    void incomeWithUnknownGstApplicability_noFinding() {
        // No signal either way: do not guess.
        assertTrue(rule.execute(ctx(List.of(
                configured("Contract Receipts", LedgerCategory.INCOME, null, "Direct Incomes"))), List.of()).isEmpty());
    }

    @Test
    void sundryDebtorsLedger_noFindingEvenWhenCategorisedAsIncome() {
        // Regression: the seeded "Sundry Debtors" master is filed under INCOME and flagged GST applicable.
        // A debtor is a party account, not an income ledger, so it never needs an HSN/SAC code.
        PreconfiguredMaster m = configured("Sundry Debtors", LedgerCategory.INCOME, true, "Sundry Debtors");
        assertTrue(rule.execute(ctx(List.of(m)), List.of()).isEmpty());
    }

    private ValidationContext ctx(List<PreconfiguredMaster> masters, boolean requireOnExpense) {
        return new ValidationContext(UUID.randomUUID(), "testuser", masters,
                Map.of("REQUIRE_HSN_SAC_ON_EXPENSE", requireOnExpense));
    }

    @Test
    void expenseSetting_missingKeyDefaultsToOn() {
        PreconfiguredMaster m = configured("Professional Fees", LedgerCategory.EXPENSE, true, "Indirect Expenses");
        assertEquals(1, rule.execute(ctx(List.of(m)), List.of()).size());
    }

    @Test
    void expenseSettingOn_flagsExpense() {
        PreconfiguredMaster m = configured("Professional Fees", LedgerCategory.EXPENSE, true, "Indirect Expenses");
        assertEquals(1, rule.execute(ctx(List.of(m), true), List.of()).size());
    }

    @Test
    void expenseSettingOff_neverFlagsExpense() {
        PreconfiguredMaster m = configured("Professional Fees", LedgerCategory.EXPENSE, true, "Indirect Expenses");
        assertTrue(rule.execute(ctx(List.of(m), false), List.of()).isEmpty());
    }

    @Test
    void expenseSettingOff_incomeStillFlagged() {
        PreconfiguredMaster income = configured("Sales Account", LedgerCategory.INCOME, true, "Sales Accounts");
        PreconfiguredMaster expense = configured("Professional Fees", LedgerCategory.EXPENSE, true, "Indirect Expenses");
        List<ValidationFinding> findings = rule.execute(ctx(List.of(income, expense), false), List.of());
        assertEquals(1, findings.size());
        assertEquals("Sales Account", findings.get(0).getLedgerName());
    }

    @Test
    void purchaseCategoryNullHsnSac_noFinding() {
        PreconfiguredMaster m = configured("Cement Purchase", LedgerCategory.PURCHASE, null);
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
