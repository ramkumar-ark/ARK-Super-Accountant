package com.arktech.superaccountant.masters.classifier;

import com.arktech.superaccountant.masters.models.LedgerCategory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LedgerCategoryClassifierTest {

    private LedgerCategoryClassifier classifier;

    @BeforeEach
    void setUp() {
        classifier = new LedgerCategoryClassifier();
    }

    @Test
    void directPurchaseParent_classifiedAsPurchase() {
        Map<String, String> hierarchy = new HashMap<>();
        hierarchy.put("purchase accounts", "");
        LedgerCategory result = classifier.classify("Purchase Accounts", null, null, hierarchy);
        assertEquals(LedgerCategory.PURCHASE, result);
    }

    @Test
    void subgroupOfPurchaseAccounts_resolvedToPurchase() {
        Map<String, String> hierarchy = new HashMap<>();
        hierarchy.put("my custom sub-group", "purchase accounts");
        hierarchy.put("purchase accounts", "");
        LedgerCategory result = classifier.classify("My Custom Sub-group", null, null, hierarchy);
        assertEquals(LedgerCategory.PURCHASE, result);
    }

    @Test
    void directExpenseParent_directExpenses_classifiedAsExpense() {
        Map<String, String> hierarchy = new HashMap<>();
        hierarchy.put("direct expenses", "");
        LedgerCategory result = classifier.classify("Direct Expenses", null, null, hierarchy);
        assertEquals(LedgerCategory.EXPENSE, result);
    }

    @Test
    void indirectExpenseParent_classifiedAsExpense() {
        Map<String, String> hierarchy = new HashMap<>();
        hierarchy.put("indirect expenses", "");
        LedgerCategory result = classifier.classify("Indirect Expenses", null, null, hierarchy);
        assertEquals(LedgerCategory.EXPENSE, result);
    }

    @Test
    void dutiesAndTaxes_withGst_classifiedAsGst() {
        Map<String, String> hierarchy = new HashMap<>();
        hierarchy.put("duties & taxes", "current liabilities");
        hierarchy.put("current liabilities", "");
        LedgerCategory result = classifier.classify("Duties & Taxes", true, null, hierarchy);
        assertEquals(LedgerCategory.GST, result);
    }

    @Test
    void dutiesAndTaxes_withTds_classifiedAsTds() {
        Map<String, String> hierarchy = new HashMap<>();
        hierarchy.put("duties & taxes", "current liabilities");
        hierarchy.put("current liabilities", "");
        LedgerCategory result = classifier.classify("Duties & Taxes", null, true, hierarchy);
        assertEquals(LedgerCategory.TDS, result);
    }

    @Test
    void unknownParentGroup_classifiedAsOther() {
        Map<String, String> hierarchy = new HashMap<>();
        LedgerCategory result = classifier.classify("Some Unknown Group", null, null, hierarchy);
        assertEquals(LedgerCategory.OTHER, result);
    }

    @Test
    void nullParentGroup_classifiedAsOther() {
        Map<String, String> hierarchy = new HashMap<>();
        LedgerCategory result = classifier.classify(null, null, null, hierarchy);
        assertEquals(LedgerCategory.OTHER, result);
    }

    @Test
    void circularGroupReference_classifiedAsOther_noInfiniteLoop() {
        Map<String, String> hierarchy = new HashMap<>();
        hierarchy.put("groupa", "groupb");
        hierarchy.put("groupb", "groupa");
        LedgerCategory result = classifier.classify("GroupA", null, null, hierarchy);
        assertEquals(LedgerCategory.OTHER, result);
    }

    @Test
    void deepHierarchy_resolvedToPurchase() {
        Map<String, String> hierarchy = new HashMap<>();
        hierarchy.put("sub-sub-group", "sub-group");
        hierarchy.put("sub-group", "purchase accounts");
        hierarchy.put("purchase accounts", "");
        LedgerCategory result = classifier.classify("Sub-sub-group", null, null, hierarchy);
        assertEquals(LedgerCategory.PURCHASE, result);
    }

    @Test
    void salesAccounts_classifiedAsIncome() {
        Map<String, String> hierarchy = new HashMap<>();
        hierarchy.put("sales accounts", "");
        LedgerCategory result = classifier.classify("Sales Accounts", null, null, hierarchy);
        assertEquals(LedgerCategory.INCOME, result);
    }

    @Test
    void dutiesAndTaxes_neitherGstNorTds_classifiedAsOther() {
        Map<String, String> hierarchy = new HashMap<>();
        hierarchy.put("duties & taxes", "");
        LedgerCategory result = classifier.classify("Duties & Taxes", null, null, hierarchy);
        assertEquals(LedgerCategory.OTHER, result);
    }

    @Test
    void caseInsensitiveGroupName_mixedCaseInput_classifiedCorrectly() {
        Map<String, String> hierarchy = new HashMap<>();
        hierarchy.put("purchase accounts", "");
        LedgerCategory result = classifier.classify("PURCHASE ACCOUNTS", null, null, hierarchy);
        assertEquals(LedgerCategory.PURCHASE, result);
    }

    @Test
    void sundryCreditors_underCurrentLiabilities_classifiedAsCreditor() {
        Map<String, String> hierarchy = new HashMap<>();
        hierarchy.put("sundry creditors", "current liabilities");
        hierarchy.put("current liabilities", "");
        LedgerCategory result = classifier.classify("Sundry Creditors", null, null, hierarchy);
        assertEquals(LedgerCategory.CREDITOR, result);
    }

    @Test
    void creditorSubgroup_underSundryCreditors_underCurrentLiabilities_classifiedAsCreditor() {
        Map<String, String> hierarchy = new HashMap<>();
        hierarchy.put("trade payables", "sundry creditors");
        hierarchy.put("sundry creditors", "current liabilities");
        hierarchy.put("current liabilities", "");
        LedgerCategory result = classifier.classify("Trade Payables", null, null, hierarchy);
        assertEquals(LedgerCategory.CREDITOR, result);
    }

    @Test
    void labourContractors_underSundryCreditors_classifiedAsCreditor() {
        Map<String, String> hierarchy = new HashMap<>();
        hierarchy.put("labour contractors", "sundry creditors");
        hierarchy.put("sundry creditors", "current liabilities");
        hierarchy.put("current liabilities", "");
        LedgerCategory result = classifier.classify("Labour Contractors", null, null, hierarchy);
        assertEquals(LedgerCategory.CREDITOR, result);
    }

    @Test
    void sundryCreditors_notInHierarchy_classifiedViaKeyword() {
        Map<String, String> hierarchy = new HashMap<>();
        LedgerCategory result = classifier.classify("Sundry Creditors", null, null, hierarchy);
        assertEquals(LedgerCategory.CREDITOR, result);
    }

    @Test
    void creditorSubgroup_parentNotInHierarchy_classifiedViaKeyword() {
        Map<String, String> hierarchy = new HashMap<>();
        LedgerCategory result = classifier.classify("Creditors - Material Purchase", null, null, hierarchy);
        assertEquals(LedgerCategory.CREDITOR, result);
    }
}
