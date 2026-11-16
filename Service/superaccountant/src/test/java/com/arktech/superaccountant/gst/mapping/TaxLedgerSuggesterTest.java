package com.arktech.superaccountant.gst.mapping;

import org.junit.jupiter.api.Test;

import static com.arktech.superaccountant.gst.mapping.GstHead.CESS;
import static com.arktech.superaccountant.gst.mapping.GstHead.CGST;
import static com.arktech.superaccountant.gst.mapping.GstHead.IGST;
import static com.arktech.superaccountant.gst.mapping.GstHead.SGST;
import static com.arktech.superaccountant.gst.mapping.TaxLedgerRole.INPUT;
import static com.arktech.superaccountant.gst.mapping.TaxLedgerRole.OUTPUT;
import static com.arktech.superaccountant.gst.mapping.TaxLedgerRole.RCM_INPUT;
import static com.arktech.superaccountant.gst.mapping.TaxLedgerRole.RCM_LIABILITY;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TaxLedgerSuggesterTest {

    @Test
    void suggestsHeadAndRole() {
        assertEquals(new TaxLedgerSuggester.Suggestion(OUTPUT, CGST), TaxLedgerSuggester.suggest("Output CGST 9%"));
        assertEquals(new TaxLedgerSuggester.Suggestion(INPUT, IGST), TaxLedgerSuggester.suggest("input igst"));
        assertEquals(new TaxLedgerSuggester.Suggestion(RCM_LIABILITY, SGST), TaxLedgerSuggester.suggest("SGST Payable RCM"));
        assertEquals(new TaxLedgerSuggester.Suggestion(RCM_INPUT, CGST), TaxLedgerSuggester.suggest("Input CGST (Reverse Charge)"));
        assertEquals(new TaxLedgerSuggester.Suggestion(OUTPUT, SGST), TaxLedgerSuggester.suggest("Output UTGST"));
        assertEquals(new TaxLedgerSuggester.Suggestion(null, CESS), TaxLedgerSuggester.suggest("Compensation Cess"));
        assertEquals(new TaxLedgerSuggester.Suggestion(null, null), TaxLedgerSuggester.suggest("Duties & Taxes misc"));
    }

    @Test
    void cessMatchesWholeWordOnly() {
        for (String name : new String[]{"Processing Charges", "Excess Freight", "Access Fee", "Success Fee"}) {
            assertEquals(new TaxLedgerSuggester.Suggestion(null, null), TaxLedgerSuggester.suggest(name));
        }
        assertEquals(CESS, TaxLedgerSuggester.suggest("Output Cess 1%").head());
    }

    @Test
    void suggestsBalanceAndTdsAreas() {
        assertEquals(new TaxLedgerSuggester.Suggestion(TaxLedgerRole.CASH_BALANCE, CGST),
                TaxLedgerSuggester.suggest("CGST Cash Balance A/c"));
        assertEquals(new TaxLedgerSuggester.Suggestion(TaxLedgerRole.CASH_BALANCE, SGST),
                TaxLedgerSuggester.suggest("SGST Electronic Cash Ledger"));
        assertEquals(new TaxLedgerSuggester.Suggestion(TaxLedgerRole.LIABILITY_BALANCE, IGST),
                TaxLedgerSuggester.suggest("IGST Liability Balance A/c"));
        assertEquals(new TaxLedgerSuggester.Suggestion(TaxLedgerRole.CREDIT_BALANCE, CGST),
                TaxLedgerSuggester.suggest("CGST Input Credit Balance"));
        assertEquals(new TaxLedgerSuggester.Suggestion(TaxLedgerRole.CREDIT_BALANCE, SGST),
                TaxLedgerSuggester.suggest("SGST ITC Balance"));
        assertEquals(new TaxLedgerSuggester.Suggestion(TaxLedgerRole.GST_TDS, IGST),
                TaxLedgerSuggester.suggest("IGST TDS Receivable"));
    }

    @Test
    void existingRolesUnaffectedByNewTokens() {
        assertEquals(RCM_LIABILITY, TaxLedgerSuggester.suggest("CGST RCM Liability").role());
        assertEquals(INPUT, TaxLedgerSuggester.suggest("Input CGST").role());
        assertEquals(OUTPUT, TaxLedgerSuggester.suggest("Output SGST").role());
        assertEquals(null, TaxLedgerSuggester.suggest("Credit Card Charges").role());
    }
}
