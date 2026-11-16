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
}
