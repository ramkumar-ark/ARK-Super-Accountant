package com.arktech.superaccountant.tds.mapping;

import com.arktech.superaccountant.masters.models.LedgerCategory;
import com.arktech.superaccountant.masters.models.UploadLedgerSnapshot;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static com.arktech.superaccountant.tds.mapping.TdsLedgerRole.NOT_TDS;
import static com.arktech.superaccountant.tds.mapping.TdsLedgerRole.TDS_PAYABLE;
import static com.arktech.superaccountant.tds.mapping.TdsLedgerRole.TDS_RECEIVABLE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TdsLedgerSuggesterTest {

    private static final Set<String> KNOWN_SECTION_CODES =
            Set.of("194C", "194H", "194I_A", "194I_B", "194J_A", "194J_B", "194J_C");

    @Test
    void candidateDetectionWithoutTaxType() {
        assertTrue(TdsLedgerSuggester.isCandidate(snap("TDS Payable - 194C A/c", "tds|duties & taxes", null, null)));
        assertTrue(TdsLedgerSuggester.isCandidate(snap("TDS (FY:24-25) A/c", "loans & advances (asset)", null, null)));
        assertFalse(TdsLedgerSuggester.isCandidate(snap("Sales Ledger", "sales accounts", null, null)));
        assertFalse(TdsLedgerSuggester.isCandidate(snap("Party Ledger", "misc expenses", null, null)));
    }

    @Test
    void taxTypeTdsLedgerUnderCustomGroupIsCandidate() {
        assertTrue(TdsLedgerSuggester.isCandidate(snap("Withholding", "my custom group", "TDS", null)));
        assertFalse(TdsLedgerSuggester.isCandidate(snap("Output CGST", "duties & taxes", "GST", null)));
    }

    @Test
    void taxTypeOthersDoesNotVetoNameOrGroupRecognition() {
        UploadLedgerSnapshot payable = snap("TDS Payable - 194C A/c", "tds", "Others", null);
        assertTrue(TdsLedgerSuggester.isCandidate(payable));
        assertEquals(new TdsLedgerSuggester.Suggestion(TDS_PAYABLE, "194C"), suggest(payable));
        UploadLedgerSnapshot receivable = snap("TDS (FY:24-25) A/c", "loans & advances (asset)", "Others", null);
        assertTrue(TdsLedgerSuggester.isCandidate(receivable));
        assertEquals(TDS_RECEIVABLE, suggest(receivable).role());
        assertFalse(TdsLedgerSuggester.isCandidate(snap("Sales", "sales accounts", "Others", null)));
    }

    @Test
    void payableSuggestsRoleAndSection() {
        assertEquals(new TdsLedgerSuggester.Suggestion(TDS_PAYABLE, "194C"),
                suggest(snap("TDS Payable - 194C A/c", "tds|duties & taxes", null, null)));
        assertEquals(new TdsLedgerSuggester.Suggestion(TDS_PAYABLE, "194J_B"),
                suggest(snap("TDS Payable - 194J-B A/c", "tds|duties & taxes", null, null)));
        assertEquals(new TdsLedgerSuggester.Suggestion(TDS_PAYABLE, null),
                suggest(snap("TDS Payable - 194I A/c", "tds|duties & taxes", null, null)));
        assertEquals(new TdsLedgerSuggester.Suggestion(TDS_PAYABLE, null),
                suggest(snap("TDS Payable - 192B A/c", "tds|duties & taxes", null, null)));
    }

    @Test
    void receivableAndNeitherRoles() {
        assertEquals(new TdsLedgerSuggester.Suggestion(TDS_RECEIVABLE, null),
                suggest(snap("TDS (FY:24-25) A/c", "loans & advances (asset)", null, null)));
        assertEquals(new TdsLedgerSuggester.Suggestion(TDS_RECEIVABLE, null),
                suggest(snap("TDS Receivable", null, null, null)));
        assertEquals(new TdsLedgerSuggester.Suggestion(null, null),
                suggest(snap("TDS A/c", "misc expenses", null, null)));
    }

    @Test
    void taxTypeTdsWithoutNameHintUsesGroupSide() {
        assertEquals(TDS_PAYABLE, suggest(snap("Withholding", "duties & taxes", "TDS", null)).role());
        assertEquals(TDS_RECEIVABLE, suggest(snap("Withholding", "loans & advances", "TDS", null)).role());
    }

    @Test
    void sectionComesFromExplicitCodesInNatureOrName() {
        assertEquals(new TdsLedgerSuggester.Suggestion(TDS_PAYABLE, "194C"),
                suggest(snap("TDS Payable 194C", "duties & taxes", "TDS", "Any")));
        assertEquals(new TdsLedgerSuggester.Suggestion(TDS_PAYABLE, null),
                suggest(snap("TDS Payable", "duties & taxes", "TDS", "Any")));
        assertEquals(new TdsLedgerSuggester.Suggestion(TDS_PAYABLE, "194H"),
                suggest(snap("TDS Duty", "duties & taxes", "TDS", "194H")));
        assertEquals(new TdsLedgerSuggester.Suggestion(TDS_PAYABLE, null),
                suggest(snap("TDS Duty", "duties & taxes", "TDS", "Payment to Contractors - Individual/HUF")));
        assertEquals(new TdsLedgerSuggester.Suggestion(TDS_PAYABLE, "194C"),
                suggest(snap("TDS Payable 194C", "duties & taxes", "TDS", null)));
    }

    @Test
    void tdsApplicableFlagDoesNotMakeAPayable() {
        UploadLedgerSnapshot s = snap("Rent", "indirect expenses", null, null);
        s.setTdsApplicable(true);
        assertFalse(TdsLedgerSuggester.isCandidate(s));
        assertEquals(new TdsLedgerSuggester.Suggestion(null, null), suggest(s));
    }

    @Test
    void parenthesisedSectionCodeIsRecognised() {
        assertEquals(new TdsLedgerSuggester.Suggestion(TDS_PAYABLE, "194C"),
                suggest(snap("TDS Payable (194C)", "duties & taxes", null, null)));
    }

    @Test
    void dutiesAndTaxesLedgersWithoutTdsTokenAreNotTds() {
        assertEquals(NOT_TDS, suggest(snap("PF Payable", "duties & taxes", null, null)).role());
        assertEquals(NOT_TDS, suggest(snap("Professional Tax", "duties & taxes", null, null)).role());
    }

    @Test
    void gstAndExpenseSnapshotsAreNeverCandidates() {
        assertFalse(TdsLedgerSuggester.isCandidate(snapshot("Output CGST", "duties & taxes", LedgerCategory.GST, true, false)));
        assertFalse(TdsLedgerSuggester.isCandidate(snapshot("Input IGST", "duties & taxes", LedgerCategory.OTHER, null, true)));
        assertFalse(TdsLedgerSuggester.isCandidate(snapshot("TDS Contract Expense", "indirect expenses", LedgerCategory.EXPENSE, true, null)));
        assertFalse(TdsLedgerSuggester.isCandidate(snapshot("TDS Purchase", "purchase accounts", LedgerCategory.PURCHASE, true, null)));
        assertTrue(TdsLedgerSuggester.isCandidate(snapshot("TDS Payable - 194C A/c", "tds|duties & taxes", LedgerCategory.TDS, null, null)));
    }

    @Test
    void unflaggedGstLedgersInDutiesAndTaxesAreNotCandidates() {
        // Captain's real ledger names: no GST taxtype/category/flag on the snapshot.
        for (String name : new String[] {"Cess", "CGST - RCM Liabililty A/c", "SGST - RCM Liability A/c",
                "TDS - CGST Cash Balance A/c", "TDS - SGST Cash Balance A/c", "Output CGST", "Input IGST"}) {
            assertFalse(TdsLedgerSuggester.isCandidate(snap(name, "duties & taxes", null, null)), name);
            assertFalse(TdsLedgerSuggester.isCandidate(snap(name, "duties & taxes", "Others", null)), name);
            assertFalse(TdsLedgerSuggester.isCandidate(snap(name, "tds|duties & taxes", null, null)), name);
        }
        assertFalse(TdsLedgerSuggester.isCandidate(snap("IGST Payable", "gst|duties & taxes", null, null)));
        assertFalse(TdsLedgerSuggester.isCandidate(snap("Output Levy", "gst|duties & taxes", "Others", null)));
    }

    @Test
    void genuineAndAmbiguousDutyLedgersStayCandidates() {
        assertTrue(TdsLedgerSuggester.isCandidate(snap("PF Payable", "duties & taxes", null, null)));
        assertTrue(TdsLedgerSuggester.isCandidate(snap("Professional Tax", "duties & taxes", "Others", null)));
        assertTrue(TdsLedgerSuggester.isCandidate(snap("TDS Payable - 194C A/c", "tds|duties & taxes", "TDS", null)));
        assertTrue(TdsLedgerSuggester.isCandidate(snap("Education Cess on TDS", "duties & taxes", null, null)));
        assertTrue(TdsLedgerSuggester.isCandidate(snap("Withholding", "duties & taxes", "TDS", null)));
        assertEquals(NOT_TDS, suggest(snap("PF Payable", "duties & taxes", null, null)).role());
    }

    private UploadLedgerSnapshot snap(String name, String chain, String taxType, String nature) {
        UploadLedgerSnapshot s = new UploadLedgerSnapshot();
        s.setLedgerName(name);
        s.setGroupChain(chain);
        s.setTaxType(taxType);
        s.setTdsNatureOfPayment(nature);
        return s;
    }

    private TdsLedgerSuggester.Suggestion suggest(UploadLedgerSnapshot s) {
        return TdsLedgerSuggester.suggest(s, KNOWN_SECTION_CODES);
    }

    private static UploadLedgerSnapshot snapshot(String name, String chain, LedgerCategory category,
                                                 Boolean tdsApplicable, Boolean gstApplicable) {
        UploadLedgerSnapshot s = new UploadLedgerSnapshot();
        s.setLedgerName(name);
        s.setGroupChain(chain);
        s.setCategory(category);
        s.setTdsApplicable(tdsApplicable);
        s.setGstApplicable(gstApplicable);
        return s;
    }
}
