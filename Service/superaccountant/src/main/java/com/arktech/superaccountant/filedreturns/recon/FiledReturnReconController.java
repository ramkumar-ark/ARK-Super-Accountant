package com.arktech.superaccountant.filedreturns.recon;

import com.arktech.superaccountant.login.security.services.UserDetailsImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/v1/filed-returns")
public class FiledReturnReconController {

    @Autowired
    private FiledReturnReconService reconService;

    @PostMapping("/baseline/{workflowId}/reconcile")
    @PreAuthorize("hasRole('ACCOUNTANT') or hasRole('OPERATOR')")
    public ReconOutcome reconcile(@AuthenticationPrincipal UserDetailsImpl principal,
                                  @PathVariable UUID workflowId) {
        return reconService.reconcile(principal.getOrganizationId(), workflowId,
                principal.getUsername());
    }
}
