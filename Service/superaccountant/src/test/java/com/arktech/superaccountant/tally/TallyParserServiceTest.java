package com.arktech.superaccountant.tally;

import java.io.File;
import java.io.FileInputStream;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import com.arktech.superaccountant.tally.models.TallyMessage;
import com.arktech.superaccountant.tally.models.Voucher;
import com.arktech.superaccountant.tally.services.TallyParserService;

class TallyParserServiceTest {

    @Test
    void testParseDayBookJson() throws Exception {
        TallyParserService service = new TallyParserService();

        File file = new File("C:/Program Files/TallyPrime/DayBook.json");
        FileInputStream fis = new FileInputStream(file);
        MockMultipartFile multipartFile = new MockMultipartFile("file", file.getName(), "application/json", fis);

        TallyMessage result = service.parseJson(multipartFile);

        System.out.println("Total vouchers: " + result.getTallymessage().size());

        Map<String, List<Voucher>> grouped = service.groupByVoucherType(result);
        System.out.println("Voucher types found: " + grouped.keySet());
        grouped.forEach((type, vouchers) -> System.out.println("  " + type + ": " + vouchers.size()));

        // Print first voucher details as a sanity check
        Voucher first = result.getTallymessage().get(0);
        System.out.println("\n--- First Voucher ---");
        System.out.println("Type: " + first.getVouchertypename());
        System.out.println("Number: " + first.getVouchernumber());
        System.out.println("Date: " + first.getDate());
        System.out.println("Party: " + first.getPartyledgername());
        System.out.println("GSTIN: " + first.getPartygstin());
        System.out.println("Narration: " + first.getNarration());
        System.out.println("Ledger entries: " + first.getAllLedgerEntriesCombined().size());
        first.getAllLedgerEntriesCombined().forEach(le ->
            System.out.println("  Ledger: " + le.getLedgername() + " | Amount: " + le.getAmount())
        );
    }
}
