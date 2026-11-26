package com.arktech.superaccountant.statutory.controllers;

import com.arktech.superaccountant.statutory.payload.response.StatutorySectionOptionResponse;
import com.arktech.superaccountant.statutory.services.StatutorySectionService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Read-only statutory section options for the TDS pickers. Statutory configurations are
 * global app configuration that applies to every registered company (no per-company
 * variance), so any authenticated user may read them and there is deliberately no
 * write path — edits happen in the statutory tables, not through the API.
 */
@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/tds/sections")
public class StatutorySectionController {

    private final StatutorySectionService statutorySectionService;

    public StatutorySectionController(StatutorySectionService statutorySectionService) {
        this.statutorySectionService = statutorySectionService;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<StatutorySectionOptionResponse>> list() {
        return ResponseEntity.ok(statutorySectionService.tdsSectionOptions());
    }
}
