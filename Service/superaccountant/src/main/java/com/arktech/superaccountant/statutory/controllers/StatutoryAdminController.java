package com.arktech.superaccountant.statutory.controllers;

import com.arktech.superaccountant.login.payload.response.MessageResponse;
import com.arktech.superaccountant.login.security.services.UserDetailsImpl;
import com.arktech.superaccountant.statutory.payload.request.CreateStatutoryRuleRequest;
import com.arktech.superaccountant.statutory.payload.request.CreateStatutorySectionRequest;
import com.arktech.superaccountant.statutory.services.StatutoryAdminService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * App-wide statutory administration (PR6). Reads are open to every authenticated user;
 * every write — and the audit list — requires the app administrator authority
 * (users.app_admin), also enforced at the URL layer in WebSecurityConfig.
 */
@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/statutory")
public class StatutoryAdminController {

    @Autowired
    private StatutoryAdminService service;

    @GetMapping("/sections")
    public Map<String, Object> sections() {
        return service.view(current());
    }

    @PostMapping("/sections")
    @PreAuthorize("hasRole('APP_ADMIN')")
    public ResponseEntity<Map<String, Object>> createSection(
            @RequestBody CreateStatutorySectionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.createSection(request, current()));
    }

    @PostMapping("/sections/{id}/deactivate")
    @PreAuthorize("hasRole('APP_ADMIN')")
    public Map<String, Object> deactivate(@PathVariable UUID id) {
        return service.setActive(id, false, current());
    }

    @PostMapping("/sections/{id}/activate")
    @PreAuthorize("hasRole('APP_ADMIN')")
    public Map<String, Object> activate(@PathVariable UUID id) {
        return service.setActive(id, true, current());
    }

    @DeleteMapping("/sections/{id}")
    @PreAuthorize("hasRole('APP_ADMIN')")
    public ResponseEntity<Void> deleteSection(@PathVariable UUID id) {
        service.deleteSection(id, current());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/sections/{id}/rules")
    @PreAuthorize("hasRole('APP_ADMIN')")
    public ResponseEntity<Map<String, Object>> addRule(@PathVariable UUID id,
            @RequestBody CreateStatutoryRuleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.addRule(id, request, current()));
    }

    @GetMapping("/audit")
    @PreAuthorize("hasRole('APP_ADMIN')")
    public List<Map<String, Object>> audit(@RequestParam(defaultValue = "100") int limit) {
        return service.audit(limit);
    }

    @ExceptionHandler(StatutoryAdminException.class)
    public ResponseEntity<MessageResponse> handle(StatutoryAdminException ex) {
        return ResponseEntity.status(ex.getStatus()).body(new MessageResponse(ex.getMessage()));
    }

    private UserDetailsImpl current() {
        var auth = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication();
        Object principal = auth != null ? auth.getPrincipal() : null;
        return principal instanceof UserDetailsImpl details ? details : null;
    }
}
