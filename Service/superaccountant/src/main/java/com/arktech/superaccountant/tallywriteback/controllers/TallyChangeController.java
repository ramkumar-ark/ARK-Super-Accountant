package com.arktech.superaccountant.tallywriteback.controllers;

import com.arktech.superaccountant.login.security.services.UserDetailsImpl;
import com.arktech.superaccountant.tallywriteback.models.TallyChangeStatus;
import com.arktech.superaccountant.tallywriteback.payload.TallyChangeListResponse;
import com.arktech.superaccountant.tallywriteback.payload.TallyExportRequest;
import com.arktech.superaccountant.tallywriteback.services.TallyExportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/v1")
public class TallyChangeController {

    @Autowired
    private TallyExportService exportService;

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/tally-changes")
    public TallyChangeListResponse list(@AuthenticationPrincipal UserDetailsImpl principal,
                                        @RequestParam(required = false) TallyChangeStatus status) {
        return exportService.list(principal.getOrganizationId(), status);
    }

    @PreAuthorize("hasRole('ACCOUNTANT') or hasRole('OWNER')")
    @PostMapping("/tally-changes/export")
    public ResponseEntity<?> export(@AuthenticationPrincipal UserDetailsImpl principal,
                                    @RequestBody(required = false) TallyExportRequest request) {
        TallyExportService.ExportResult result = exportService.export(
                principal.getOrganizationId(),
                principal.getUsername(),
                request == null ? null : request.itemIds(),
                request == null ? null : request.confirmStale());

        if (result.isError()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("code", result.errorCode()));
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/json; charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + result.filename() + "\"")
                .header("X-Tally-Skipped", Integer.toString(result.skipped()))
                .body(result.content());
    }
}
