package com.arktech.superaccountant.tally.controllers;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.arktech.superaccountant.login.payload.response.MessageResponse;
import com.arktech.superaccountant.tally.models.TallyMessage;
import com.arktech.superaccountant.tally.models.Voucher;
import com.arktech.superaccountant.tally.payload.response.TallyImportResponse;
import com.arktech.superaccountant.tally.services.TallyParserService;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/tally")
public class TallyImportController {

    @Autowired
    private TallyParserService tallyParserService;

    @PreAuthorize("hasRole('OWNER') or hasRole('ACCOUNTANT') or hasRole('DATA_ENTRY_OPERATOR')")
    @PostMapping("/import")
    public ResponseEntity<?> importTallyData(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(new MessageResponse("Error: File is empty"));
        }

        try {
            TallyMessage tallyMessage = tallyParserService.parseJson(file);

            if (tallyMessage.getTallymessage() == null || tallyMessage.getTallymessage().isEmpty()) {
                return ResponseEntity.badRequest().body(new MessageResponse("Error: No vouchers found in the file"));
            }

            Map<String, List<Voucher>> vouchersByType = tallyParserService.groupByVoucherType(tallyMessage);

            Map<String, Integer> voucherTypeCounts = vouchersByType.entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().size()));

            int totalVouchers = tallyMessage.getTallymessage().size();

            TallyImportResponse response = new TallyImportResponse(totalVouchers, voucherTypeCounts, vouchersByType);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(new MessageResponse("Error: Failed to parse Tally JSON - " + e.getMessage()));
        }
    }
}
