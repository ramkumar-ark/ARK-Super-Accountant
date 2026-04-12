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
        hierarchy.put("Purchase Accounts", "");
        LedgerCategory result = classifier.classify("Purchase Accounts", null, null, hierarchy);
        assertEquals(LedgerCategory.PURCHASE, result);
    }

    @Test
    void subgroupOfPurchaseAccounts_resolvedToPurchase() {
        Map<String, String> hierarchy = new HashMap<>();
        hierarchy.put("My Custom Sub-group", "Purchase Accounts");
        hierarchy.put("Purchase Accounts", "");
        LedgerCategory result = classifier.classify("My Custom Sub-group", null, null, hierarchy);
        assertEquals(LedgerCategory.PURCHASE, result);
    }

    @Test
    void directExpenseParent_directExpenses_classifiedAsExpense() {
        Map<String, String> hierarchy = new HashMap<>();
        hierarchy.put("Direct Expenses", "");
        LedgerCategory result = classifier.classify("Direct Expenses", null, null, hierarchy);
        assertEquals(LedgerCategory.EXPENSE, result);
    }

    @Test
    void indirectExpenseParent_classifiedAsExpense() {
        Map<String, String> hierarchy = new HashMap<>();
        hierarchy.put("Indirect Expenses", "");
        LedgerCategory result = classifier.classify("Indirect Expenses", null, null, hierarchy);
        assertEquals(LedgerCategory.EXPENSE, result);
    }

    @Test
    void dutiesAndTaxes_withGst_classifiedAsGst() {
        Map<String, String> hierarchy = new HashMap<>();
        hierarchy.put("Duties & Taxes", "");
        LedgerCategory result = classifier.classify("Duties & Taxes", true, null, hierarchy);
        assertEquals(LedgerCategory.GST, result);
    }

    @Test
    void dutiesAndTaxes_withTds_classifiedAsTds() {
        Map<String, String> hierarchy = new HashMap<>();
        hierarchy.put("Duties & Taxes", "");
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
        hierarchy.put("GroupA", "GroupB");
        hierarchy.put("GroupB", "GroupA"); // cycle
        LedgerCategory result = classifier.classify("GroupA", null, null, hierarchy);
        assertEquals(LedgerCategory.OTHER, result);
    }

    @Test
    void deepHierarchy_resolvedToPurchase() {
        Map<String, String> hierarchy = new HashMap<>();
        hierarchy.put("Sub-sub-group", "Sub-group");
        hierarchy.put("Sub-group", "Purchase Accounts");
        hierarchy.put("Purchase Accounts", "");
        LedgerCategory result = classifier.classify("Sub-sub-group", null, null, hierarchy);
        assertEquals(LedgerCategory.PURCHASE, result);
    }

    @Test
    void salesAccounts_classifiedAsIncome() {
        Map<String, String> hierarchy = new HashMap<>();
        hierarchy.put("Sales Accounts", "");
        LedgerCategory result = classifier.classify("Sales Accounts", null, null, hierarchy);
        assertEquals(LedgerCategory.INCOME, result);
    }

    @Test
    void dutiesAndTaxes_neitherGstNorTds_classifiedAsOther() {
        Map<String, String> hierarchy = new HashMap<>();
        hierarchy.put("Duties & Taxes", "");
        LedgerCategory result = classifier.classify("Duties & Taxes", null, null, hierarchy);
        assertEquals(LedgerCategory.OTHER, result);
    }

    @Test
    void caseInsensitiveGroupName_purchaseAccounts_lowercase_classifiedAsPurchase() {
        Map<String, String> hierarchy = new HashMap<>();
        // The group exists in hierarchy but hierarchy walk returns the node name as-is;
        // classification checks lowercase comparison
        hierarchy.put("purchase accounts", "");
        LedgerCategory result = classifier.classify("purchase accounts", null, null, hierarchy);
        assertEquals(LedgerCategory.PURCHASE, result);
    }
}
