package com.arktech.superaccountant.tally;

import com.arktech.superaccountant.masters.classifier.LedgerCategoryClassifier;
import com.arktech.superaccountant.masters.classifier.ParsedLedger;
import com.arktech.superaccountant.masters.models.LedgerCategory;
import com.arktech.superaccountant.tally.services.TallyParserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TallyMastersParserTest {

    private TallyParserService service;

    @BeforeEach
    void setUp() {
        service = new TallyParserService(new LedgerCategoryClassifier());
    }

    private MockMultipartFile json(String content) {
        return new MockMultipartFile("file", "masters.json", "application/json",
                content.getBytes(StandardCharsets.UTF_8));
    }

    private MockMultipartFile utf16LE(String content) {
        byte[] bom = {(byte) 0xFF, (byte) 0xFE};
        byte[] body = content.getBytes(StandardCharsets.UTF_16LE);
        byte[] combined = new byte[bom.length + body.length];
        System.arraycopy(bom, 0, combined, 0, bom.length);
        System.arraycopy(body, 0, combined, bom.length, body.length);
        return new MockMultipartFile("file", "masters.json", "application/json", combined);
    }

    private MockMultipartFile utf32BE() {
        // UTF-32 BE BOM: 00 00 FE FF
        byte[] data = {0x00, 0x00, (byte) 0xFE, (byte) 0xFF, 0x7B, 0x7D};
        return new MockMultipartFile("file", "masters.json", "application/json", data);
    }

    @Test
    void validMastersJson_parsedCorrectly() throws Exception {
        String json = """
                {
                  "tallymessage": [
                    { "group": { "name": "Purchase Accounts", "parent": "" } },
                    { "ledger": { "name": "Cement", "guid": "guid-001", "parent": "Purchase Accounts" } }
                  ]
                }
                """;
        List<ParsedLedger> ledgers = service.parseMastersJson(json(json));
        assertEquals(1, ledgers.size());
        assertEquals("Cement", ledgers.get(0).getName());
        assertEquals(LedgerCategory.PURCHASE, ledgers.get(0).getCategory());
    }

    @Test
    void jsonWithBothLedgerAndGroupEntries_onlyLedgersParsed() throws Exception {
        String json = """
                {
                  "tallymessage": [
                    { "group": { "name": "Purchase Accounts", "parent": "" } },
                    { "ledger": { "name": "Cement", "guid": "guid-001", "parent": "Purchase Accounts" } },
                    { "ledger": { "name": "Labour Charges", "guid": "guid-002", "parent": "Direct Expenses" } },
                    { "group": { "name": "Direct Expenses", "parent": "" } }
                  ]
                }
                """;
        List<ParsedLedger> ledgers = service.parseMastersJson(json(json));
        assertEquals(2, ledgers.size());
    }

    @Test
    void ledgerMissingGuid_throwsIllegalArgument() {
        String json = """
                {
                  "tallymessage": [
                    { "ledger": { "name": "Cement" } }
                  ]
                }
                """;
        assertThrows(IllegalArgumentException.class, () -> service.parseMastersJson(json(json)));
    }

    @Test
    void missingTallymessageEnvelope_throwsIllegalArgument() {
        String json = """
                { "other": [] }
                """;
        assertThrows(IllegalArgumentException.class, () -> service.parseMastersJson(json(json)));
    }

    @Test
    void malformedJson_throwsIllegalArgument() {
        String content = "not valid json {{{";
        assertThrows(IllegalArgumentException.class, () -> service.parseMastersJson(json(content)));
    }

    @Test
    void utf16LeBomFile_parsedCorrectly() throws Exception {
        String content = "{\"tallymessage\":[{\"group\":{\"name\":\"Purchase Accounts\",\"parent\":\"\"}},{\"ledger\":{\"name\":\"Cement\",\"guid\":\"guid-001\",\"parent\":\"Purchase Accounts\"}}]}";
        List<ParsedLedger> ledgers = service.parseMastersJson(utf16LE(content));
        assertEquals(1, ledgers.size());
        assertEquals("Cement", ledgers.get(0).getName());
    }

    @Test
    void utf32BomFile_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> service.parseMastersJson(utf32BE()));
    }

    @Test
    void ledgerWithUnknownParentGroup_classifiedAsOther() throws Exception {
        String json = """
                {
                  "tallymessage": [
                    { "ledger": { "name": "Mystery Ledger", "guid": "guid-001", "parent": "Completely Unknown Group" } }
                  ]
                }
                """;
        List<ParsedLedger> ledgers = service.parseMastersJson(json(json));
        assertEquals(1, ledgers.size());
        assertEquals(LedgerCategory.OTHER, ledgers.get(0).getCategory());
    }

    @Test
    void ledgerWithDutiesAndTaxesGst_classifiedAsGst() throws Exception {
        String json = """
                {
                  "tallymessage": [
                    { "group": { "name": "Duties & Taxes", "parent": "" } },
                    { "ledger": { "name": "Input CGST @6%", "guid": "guid-001", "parent": "Duties & Taxes", "taxtype": "GST" } }
                  ]
                }
                """;
        List<ParsedLedger> ledgers = service.parseMastersJson(json(json));
        assertEquals(1, ledgers.size());
        assertEquals(LedgerCategory.GST, ledgers.get(0).getCategory());
    }

    @Test
    void ledgerWithDutiesAndTaxesTds_classifiedAsTds() throws Exception {
        String json = """
                {
                  "tallymessage": [
                    { "group": { "name": "Duties & Taxes", "parent": "" } },
                    { "ledger": { "name": "TDS Payable - 194C", "guid": "guid-001", "parent": "Duties & Taxes", "istdsapplicable": "Yes" } }
                  ]
                }
                """;
        List<ParsedLedger> ledgers = service.parseMastersJson(json(json));
        assertEquals(1, ledgers.size());
        assertEquals(LedgerCategory.TDS, ledgers.get(0).getCategory());
    }

    @Test
    void circularGroupReference_ledgerClassifiedAsOther() throws Exception {
        // GroupA -> GroupB -> GroupA (cycle), ledger under GroupA
        String json = """
                {
                  "tallymessage": [
                    { "group": { "name": "GroupA", "parent": "GroupB" } },
                    { "group": { "name": "GroupB", "parent": "GroupA" } },
                    { "ledger": { "name": "Test Ledger", "guid": "guid-001", "parent": "GroupA" } }
                  ]
                }
                """;
        // Should not hang or throw; ledger should be OTHER
        List<ParsedLedger> ledgers = service.parseMastersJson(json(json));
        assertEquals(1, ledgers.size());
        assertEquals(LedgerCategory.OTHER, ledgers.get(0).getCategory());
    }

    @Test
    void subgroupOfPurchaseAccounts_resolvedToPurchase() throws Exception {
        String json = """
                {
                  "tallymessage": [
                    { "group": { "name": "Purchase Accounts", "parent": "" } },
                    { "group": { "name": "Raw Materials", "parent": "Purchase Accounts" } },
                    { "ledger": { "name": "Cement", "guid": "guid-001", "parent": "Raw Materials" } }
                  ]
                }
                """;
        List<ParsedLedger> ledgers = service.parseMastersJson(json(json));
        assertEquals(1, ledgers.size());
        assertEquals(LedgerCategory.PURCHASE, ledgers.get(0).getCategory());
    }

    @Test
    void emptyTallymessageArray_returnsEmptyList() throws Exception {
        String json = "{\"tallymessage\":[]}";
        List<ParsedLedger> ledgers = service.parseMastersJson(json(json));
        assertTrue(ledgers.isEmpty());
    }
}
