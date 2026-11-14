package com.arktech.superaccountant.masters.scope;

import com.arktech.superaccountant.masters.models.LedgerCategory;
import com.arktech.superaccountant.masters.models.UploadLedgerSnapshot;
import com.arktech.superaccountant.masters.models.ValidationFinding;
import com.arktech.superaccountant.masters.models.FindingSeverity;
import com.arktech.superaccountant.masters.models.ResolveStatus;
import com.arktech.superaccountant.workflow.models.WorkflowType;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MastersScopeTest {

    private static ValidationFinding finding(String ruleCode, LedgerCategory category, String ledgerName) {
        ValidationFinding f = new ValidationFinding();
        f.setRuleCode(ruleCode);
        f.setCategory(category);
        f.setLedgerName(ledgerName);
        f.setSeverity(FindingSeverity.ERROR);
        f.setResolveStatus(ResolveStatus.OPEN);
        return f;
    }

    private static UploadLedgerSnapshot snap(String name, LedgerCategory category, String tdsSection, String groupChain) {
        UploadLedgerSnapshot s = new UploadLedgerSnapshot();
        s.setLedgerName(name);
        s.setCategory(category);
        s.setTdsSection(tdsSection);
        s.setGroupChain(groupChain);
        return s;
    }

    private static Set<WorkflowType> types(ValidationFinding f, UploadLedgerSnapshot l, UploadLedgerSnapshot p) {
        Map<String, UploadLedgerSnapshot> latest = l == null ? Map.of()
                : Map.of(com.arktech.superaccountant.masters.util.LedgerNames.normalize(l.getLedgerName()), l);
        Map<String, UploadLedgerSnapshot> previous = p == null ? Map.of()
                : Map.of(com.arktech.superaccountant.masters.util.LedgerNames.normalize(p.getLedgerName()), p);
        return MastersScope.typesFor(f, latest, previous);
    }

    @Test
    void typesFor_income_gstr1And3b() {
        assertEquals(Set.of(WorkflowType.GSTR1, WorkflowType.GSTR3B),
                types(finding("MISMATCH_DETECTION", LedgerCategory.INCOME, "Sales A/c"), null, null));
    }

    @Test
    void typesFor_purchase_gstr3bOnly() {
        assertEquals(Set.of(WorkflowType.GSTR3B),
                types(finding("MISMATCH_DETECTION", LedgerCategory.PURCHASE, "Purchases"), null, null));
    }

    @Test
    void typesFor_expenseTdsMapped_gstr3bAndTds() {
        assertEquals(Set.of(WorkflowType.GSTR3B, WorkflowType.TDS_PAYMENT, WorkflowType.TDS_26Q),
                types(finding("MISMATCH_DETECTION", LedgerCategory.EXPENSE, "Contractor Charges"),
                        snap("Contractor Charges", LedgerCategory.EXPENSE, "194C", null), null));
    }

    @Test
    void typesFor_expenseNotTdsMapped_gstr3bOnly() {
        assertEquals(Set.of(WorkflowType.GSTR3B),
                types(finding("MISMATCH_DETECTION", LedgerCategory.EXPENSE, "Rent"), null, null));
    }

    @Test
    void typesFor_gstOutputLedger_gstr1And3b_inputLedger_gstr3bOnly() {
        assertEquals(Set.of(WorkflowType.GSTR1, WorkflowType.GSTR3B),
                types(finding("MISMATCH_DETECTION", LedgerCategory.GST, "Output VAT"), null, null));
        assertEquals(Set.of(WorkflowType.GSTR3B),
                types(finding("MISMATCH_DETECTION", LedgerCategory.GST, "Input Tax Credit"), null, null));
        assertEquals(Set.of(WorkflowType.GSTR1, WorkflowType.GSTR3B),
                types(finding("MISMATCH_DETECTION", LedgerCategory.GST, "Ambiguous"), null, null));
    }

    @Test
    void typesFor_tdsCategory_tdsTypesOnly() {
        assertEquals(Set.of(WorkflowType.TDS_PAYMENT, WorkflowType.TDS_26Q),
                types(finding("MISMATCH_DETECTION", LedgerCategory.TDS, "TDS Payable"), null, null));
    }

    @Test
    void typesFor_other_empty() {
        assertTrue(types(finding("MISMATCH_DETECTION", LedgerCategory.OTHER, "Misc"), null, null).isEmpty());
    }

    @Test
    void typesFor_categoryChanged_unionOfOldAndNew() {
        // Review Focus 1: finding INCOME, previous EXPENSE TDS-mapped → all four types
        ValidationFinding f = finding("MISMATCH_DETECTION", LedgerCategory.INCOME, "Sales A/c");
        UploadLedgerSnapshot p = snap("Sales A/c", LedgerCategory.EXPENSE, "194C", null);
        assertEquals(Set.of(WorkflowType.GSTR1, WorkflowType.GSTR3B, WorkflowType.TDS_PAYMENT, WorkflowType.TDS_26Q),
                types(f, snap("Sales A/c", LedgerCategory.INCOME, null, null), p));
    }

    @Test
    void typesFor_gstinPresence_sundryDebtors_gstr1And3b() {
        ValidationFinding f = finding("GSTIN_PRESENCE", LedgerCategory.INCOME, "Customer X");
        assertEquals(Set.of(WorkflowType.GSTR1, WorkflowType.GSTR3B),
                types(f, snap("Customer X", LedgerCategory.INCOME, null, "Sundry Debtors|Group"), null));
    }

    @Test
    void typesFor_gstinPresence_sundryCreditors_keepsBaseAndAddsGstr3b() {
        // TDS-mapped creditor → {GSTR3B, TDS_PAYMENT, TDS_26Q}
        ValidationFinding f = finding("GSTIN_PRESENCE", LedgerCategory.CREDITOR, "Supplier X");
        UploadLedgerSnapshot l = snap("Supplier X", LedgerCategory.CREDITOR, "194C", "Sundry Creditors");
        assertEquals(Set.of(WorkflowType.GSTR3B, WorkflowType.TDS_PAYMENT, WorkflowType.TDS_26Q),
                types(f, l, null));
    }

    @Test
    void typesFor_tdsSectionMapping_tdsOnly() {
        assertEquals(Set.of(WorkflowType.TDS_PAYMENT, WorkflowType.TDS_26Q),
                types(finding("TDS_SECTION_MAPPING", LedgerCategory.EXPENSE, "Contractor"), null, null));
    }

    @Test
    void typesFor_hsnSac_onTdsMappedExpense_gstr3bOnly() {
        // TDS types stripped by the GST intersection
        ValidationFinding f = finding("HSN_SAC_CODE", LedgerCategory.EXPENSE, "Contractor Charges");
        UploadLedgerSnapshot l = snap("Contractor Charges", LedgerCategory.EXPENSE, "194C", null);
        assertEquals(Set.of(WorkflowType.GSTR3B), types(f, l, null));
    }

    @Test
    void typesFor_missingInUpload_noLatestSnapshot_usesFindingAndPrevious() {
        ValidationFinding f = finding("MISMATCH_DETECTION", LedgerCategory.INCOME, "Sales A/c");
        UploadLedgerSnapshot p = snap("Sales A/c", LedgerCategory.INCOME, null, null);
        Set<WorkflowType> types = types(f, null, p);
        assertEquals(Set.of(WorkflowType.GSTR1, WorkflowType.GSTR3B), types);
    }

    @Test
    void typesFor_tdsApplicableTrue_expense_gstr3bAndTds() {
        UploadLedgerSnapshot l = snap("Professional Fees", LedgerCategory.EXPENSE, null, null);
        l.setTdsApplicable(true);
        assertEquals(Set.of(WorkflowType.GSTR3B, WorkflowType.TDS_PAYMENT, WorkflowType.TDS_26Q),
                types(finding("MISMATCH_DETECTION", LedgerCategory.EXPENSE, "Professional Fees"), l, null));
    }
}
