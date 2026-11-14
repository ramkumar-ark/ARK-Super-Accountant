package com.arktech.superaccountant.tally.services;

import com.arktech.superaccountant.masters.classifier.ParsedLedger;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TallyParserPanOpeningBalanceTest {

    private final TallyParserService service = new TallyParserService(
            new com.arktech.superaccountant.masters.classifier.LedgerCategoryClassifier());

    private List<ParsedLedger> parse(String json) throws Exception {
        return service.parseMastersJson(new MockMultipartFile(
                "file", "masters.json", "application/json", json.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void parsesPanAndOpeningBalance() throws Exception {
        String json = """
        {"tallymessage":[
          {"ledger":{"name":"ABC Contractors","guid":"g-1","parent":"Sundry Creditors",
            "incometaxnumber":"ABCDE1234F","openingbalance":"-50000.00"}}
        ]}
        """;
        List<ParsedLedger> ledgers = parse(json);
        assertEquals(1, ledgers.size());
        assertEquals("ABCDE1234F", ledgers.get(0).getPan());
        assertEquals(new BigDecimal("-50000.00"), ledgers.get(0).getOpeningBalance());
    }

    @Test
    void missingPanAndOpeningBalanceStayNull() throws Exception {
        String json = """
        {"tallymessage":[
          {"ledger":{"name":"Rent","guid":"g-2","parent":"Indirect Expenses"}}
        ]}
        """;
        List<ParsedLedger> ledgers = parse(json);
        assertNull(ledgers.get(0).getPan());
        assertNull(ledgers.get(0).getOpeningBalance());
    }
}
