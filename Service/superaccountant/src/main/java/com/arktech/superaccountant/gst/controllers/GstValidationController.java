package com.arktech.superaccountant.gst.controllers;

import java.util.LinkedHashMap;
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

import com.arktech.superaccountant.gst.models.GstValidationError;
import com.arktech.superaccountant.gst.models.GstValidationResult;
import com.arktech.superaccountant.gst.payload.response.GstValidationResponse;
import com.arktech.superaccountant.gst.services.GstValidationService;
import com.arktech.superaccountant.login.payload.response.MessageResponse;
import com.arktech.superaccountant.tally.models.TallyMessage;
import com.arktech.superaccountant.tally.services.TallyParserService;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/gst")
public class GstValidationController {

    @Autowired
    private GstValidationService gstValidationService;

    @Autowired
    private TallyParserService tallyParserService;

    @PreAuthorize("hasRole('OWNER') or hasRole('ACCOUNTANT') or hasRole('DATA_ENTRY_OPERATOR')")
    @PostMapping("/validate")
    public ResponseEntity<?> validateGst(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(new MessageResponse("Error: File is empty"));
        }

        try {
            TallyMessage tallyMessage = tallyParserService.parseJson(file);

            if (tallyMessage.getTallymessage() == null || tallyMessage.getTallymessage().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(new MessageResponse("Error: No vouchers found in the file"));
            }

            List<GstValidationResult> allResults = gstValidationService.validate(tallyMessage);

            List<GstValidationResult> resultsWithErrors = allResults.stream()
                    .filter(GstValidationResult::hasErrors)
                    .collect(Collectors.toList());

            // Count errors by type
            Map<String, Integer> errorTypeCounts = new LinkedHashMap<>();
            for (GstValidationResult result : resultsWithErrors) {
                for (GstValidationError error : result.getErrors()) {
                    String type = error.getErrorType().name();
                    errorTypeCounts.put(type, errorTypeCounts.getOrDefault(type, 0) + 1);
                }
            }

            GstValidationResponse response = new GstValidationResponse(
                    allResults.size(),
                    resultsWithErrors.size(),
                    errorTypeCounts,
                    resultsWithErrors);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(new MessageResponse("Error: Failed to validate GST - " + e.getMessage()));
        }
    }
}
