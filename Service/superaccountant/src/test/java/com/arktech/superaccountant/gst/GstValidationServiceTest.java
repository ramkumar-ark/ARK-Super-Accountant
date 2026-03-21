package com.arktech.superaccountant.gst;

import java.io.File;
import java.io.FileInputStream;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import com.arktech.superaccountant.gst.models.GstValidationError;
import com.arktech.superaccountant.gst.models.GstValidationResult;
import com.arktech.superaccountant.gst.services.GstValidationService;
import com.arktech.superaccountant.tally.models.TallyMessage;
import com.arktech.superaccountant.tally.services.TallyParserService;

class GstValidationServiceTest {

    @Test
    void testValidateDayBookJson() throws Exception {
        TallyParserService parserService = new TallyParserService();
        GstValidationService validationService = new GstValidationService();

        File file = new File("C:/Program Files/TallyPrime/DayBook.json");
        FileInputStream fis = new FileInputStream(file);
        MockMultipartFile multipartFile = new MockMultipartFile("file", file.getName(), "application/json", fis);

        TallyMessage tallyMessage = parserService.parseJson(multipartFile);
        System.out.println("Total vouchers parsed: " + tallyMessage.getTallymessage().size());

        List<GstValidationResult> results = validationService.validate(tallyMessage);

        List<GstValidationResult> withErrors = results.stream()
                .filter(GstValidationResult::hasErrors)
                .collect(Collectors.toList());

        System.out.println("Vouchers with errors: " + withErrors.size() + " / " + results.size());

        // Count by error type
        Map<String, Long> errorTypeCounts = withErrors.stream()
                .flatMap(r -> r.getErrors().stream())
                .collect(Collectors.groupingBy(e -> e.getErrorType().name(), Collectors.counting()));

        System.out.println("\n--- Error Type Summary ---");
        errorTypeCounts.forEach((type, count) ->
                System.out.println("  " + type + ": " + count));

        // Print details for first 10 vouchers with errors
        System.out.println("\n--- Voucher Error Details (first 10) ---");
        withErrors.stream().limit(10).forEach(result -> {
            System.out.println("\nVoucher: " + result.getVoucherNumber()
                    + " | Type: " + result.getVoucherType()
                    + " | Date: " + result.getDate()
                    + " | Party: " + result.getPartyName());
            for (GstValidationError error : result.getErrors()) {
                System.out.println("  [" + error.getErrorType() + "] " + error.getMessage());
                if (error.getExpectedValue() != null) {
                    System.out.println("    Expected: " + error.getExpectedValue());
                    System.out.println("    Actual:   " + error.getActualValue());
                }
            }
        });
    }
}
