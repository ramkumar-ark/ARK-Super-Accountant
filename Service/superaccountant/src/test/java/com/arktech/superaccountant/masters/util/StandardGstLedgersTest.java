package com.arktech.superaccountant.masters.util;

import com.arktech.superaccountant.gst.mapping.GstHead;
import com.arktech.superaccountant.gst.mapping.TaxLedgerRole;
import com.arktech.superaccountant.gst.mapping.TaxLedgerSuggester;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StandardGstLedgersTest {

    @Test
    void definesExactlyTheNineteenStandardLedgers() {
        List<StandardGstLedgers.StandardGstLedger> ledgers = StandardGstLedgers.ledgers();
        assertEquals(22, ledgers.size());

        Set<String> names = new HashSet<>();
        for (StandardGstLedgers.StandardGstLedger l : ledgers) {
            assertTrue(names.add(l.ledgerName()), "duplicate name: " + l.ledgerName());
        }
        for (String head : List.of("SGST", "CGST", "IGST", "Cess")) {
            assertTrue(names.contains("Input " + head + " A/c"));
            assertTrue(names.contains("Output " + head + " A/c"));
            assertTrue(names.contains(head + " Payable A/c"));
            assertTrue(names.contains(head + " Cash Balance A/c"));
        }
        for (String head : List.of("SGST", "CGST", "IGST")) {
            assertTrue(names.contains("TDS " + head + " Receivable A/c"));
            assertTrue(names.contains(head + " Credit Balance A/c"));
        }
    }

    @Test
    void groupsFollowDutiesAndTaxesForBalancesAndCurrentAssetsForCash() {
        for (StandardGstLedgers.StandardGstLedger l : StandardGstLedgers.ledgers()) {
            if (l.ledgerName().contains("Cash Balance") || l.ledgerName().contains("Credit Balance")) {
                assertEquals("Current Assets", l.parentGroup());
            } else {
                assertEquals("Duties & Taxes", l.parentGroup());
            }
        }
    }

    @Test
    void everyLedgerSuggestsTheRightTaxHeadAndRole() {
        for (StandardGstLedgers.StandardGstLedger l : StandardGstLedgers.ledgers()) {
            GstHead head = headOf(l.ledgerName());
            TaxLedgerRole role = TaxLedgerSuggester.suggest(l.ledgerName()).role();
            if (l.ledgerName().startsWith("Input ")) {
                assertEquals(TaxLedgerRole.INPUT, role, l.ledgerName());
            } else if (l.ledgerName().startsWith("Output ")) {
                assertEquals(TaxLedgerRole.OUTPUT, role, l.ledgerName());
            } else if (l.ledgerName().contains("Payable")) {
                assertEquals(TaxLedgerRole.LIABILITY_BALANCE, role, l.ledgerName());
            } else if (l.ledgerName().contains("Cash Balance")) {
                assertEquals(TaxLedgerRole.CASH_BALANCE, role, l.ledgerName());
            } else if (l.ledgerName().contains("Credit Balance")) {
                assertEquals(TaxLedgerRole.CREDIT_BALANCE, role, l.ledgerName());
            } else if (l.ledgerName().startsWith("TDS ")) {
                assertEquals(TaxLedgerRole.GST_TDS, role, l.ledgerName());
            }
            assertEquals(head, TaxLedgerSuggester.suggest(l.ledgerName()).head(), l.ledgerName());
        }
    }

    private GstHead headOf(String name) {
        if (name.contains("IGST")) return GstHead.IGST;
        if (name.contains("CGST")) return GstHead.CGST;
        if (name.contains("SGST")) return GstHead.SGST;
        return GstHead.CESS;
    }
}
