package com.arktech.superaccountant.tds.mapping;

import java.util.Set;
import org.junit.jupiter.api.Test;

import static com.arktech.superaccountant.tds.mapping.TdsLedgerRole.TDS_PAYABLE;
import static com.arktech.superaccountant.tds.mapping.TdsLedgerRole.TDS_RECEIVABLE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TdsLedgerSuggesterTest {

    private static final Set<String> KNOWN_SECTION_CODES =
            Set.of("194C", "194H", "194I_A", "194I_B", "194J_A", "194J_B", "194J_C");

    @Test
    void candidateDetection() {
        assertTrue(TdsLedgerSuggester.isCandidate("TDS Payable - 194C A/c", null, "tds|duties & taxes", null));
        assertTrue(TdsLedgerSuggester.isCandidate("TDS (FY:24-25) A/c", null, "loans & advances (asset)", null));
        assertFalse(TdsLedgerSuggester.isCandidate("Sales Ledger", null, "sales accounts", null));
        assertTrue(TdsLedgerSuggester.isCandidate("Party Ledger", "Misc Expenses", "misc expenses (indirect expenses)", true));
        assertFalse(TdsLedgerSuggester.isCandidate("Sales Ledger", "Duties & Taxes", "sales accounts", false));
    }

    @Test
    void payableSuggestsRoleAndSection() {
        assertEquals(new TdsLedgerSuggester.Suggestion(TDS_PAYABLE, "194C"),
                TdsLedgerSuggester.suggest("TDS Payable - 194C A/c", null, "tds|duties & taxes", null, KNOWN_SECTION_CODES));
        assertEquals(new TdsLedgerSuggester.Suggestion(TDS_PAYABLE, "194J_B"),
                TdsLedgerSuggester.suggest("TDS Payable - 194J-B A/c", null, "tds|duties & taxes", null, KNOWN_SECTION_CODES));
        assertEquals(new TdsLedgerSuggester.Suggestion(TDS_PAYABLE, null),
                TdsLedgerSuggester.suggest("TDS Payable - 194I A/c", null, "tds|duties & taxes", null, KNOWN_SECTION_CODES));
        assertEquals(new TdsLedgerSuggester.Suggestion(TDS_PAYABLE, null),
                TdsLedgerSuggester.suggest("TDS Payable - 192B A/c", null, "tds|duties & taxes", null, KNOWN_SECTION_CODES));
        assertEquals(new TdsLedgerSuggester.Suggestion(TDS_PAYABLE, null),
                TdsLedgerSuggester.suggest("TDS Payable - 194T A/c", null, "tds|duties & taxes", null, KNOWN_SECTION_CODES));
    }

    @Test
    void receivableAndAmbiguousRoles() {
        assertEquals(new TdsLedgerSuggester.Suggestion(TDS_RECEIVABLE, null),
                TdsLedgerSuggester.suggest("TDS (FY:24-25) A/c", "Loans & Advances (Asset)", "loans & advances (asset)", null, KNOWN_SECTION_CODES));
        assertEquals(new TdsLedgerSuggester.Suggestion(TDS_RECEIVABLE, null),
                TdsLedgerSuggester.suggest("TDS Receivable", null, null, null, KNOWN_SECTION_CODES));
        assertEquals(new TdsLedgerSuggester.Suggestion(null, null),
                TdsLedgerSuggester.suggest("TDS Payable and Receivable", null, "tds|duties & taxes", null, KNOWN_SECTION_CODES));
        assertEquals(new TdsLedgerSuggester.Suggestion(null, null),
                TdsLedgerSuggester.suggest("TDS A/c", "Misc Expenses", "misc expenses", false, KNOWN_SECTION_CODES));
    }
}
