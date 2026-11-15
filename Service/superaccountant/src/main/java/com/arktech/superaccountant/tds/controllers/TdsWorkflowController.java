package com.arktech.superaccountant.tds.controllers;

import com.arktech.superaccountant.login.security.services.UserDetailsImpl;
import com.arktech.superaccountant.tds.models.TdsChallan;
import com.arktech.superaccountant.tds.models.TdsChallanStatus;
import com.arktech.superaccountant.tds.models.TdsPaymentDetail;
import com.arktech.superaccountant.tds.payload.request.ChallanRequest;
import com.arktech.superaccountant.tds.payload.response.ChallanListResponse;
import com.arktech.superaccountant.tds.repository.TdsPaymentDetailRepository;
import com.arktech.superaccountant.tds.services.TdsChallanService;
import com.arktech.superaccountant.tds.services.TdsNotFoundException;
import com.arktech.superaccountant.workflow.service.WorkflowBadRequestException;
import com.arktech.superaccountant.workflow.service.WorkflowConflictException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/v1/workflows/{id}/tds")
public class TdsWorkflowController {

    private final TdsChallanService challanService;
    private final TdsPaymentDetailRepository paymentDetailRepository;

    public TdsWorkflowController(TdsChallanService challanService,
                                 TdsPaymentDetailRepository paymentDetailRepository) {
        this.challanService = challanService;
        this.paymentDetailRepository = paymentDetailRepository;
    }

    @GetMapping("/challans")
    public ChallanListResponse list(@PathVariable UUID id,
                                    @AuthenticationPrincipal UserDetailsImpl principal) {
        List<TdsChallan> challans = challanService.list(principal.getOrganizationId(), id);
        TdsPaymentDetail detail = paymentDetailRepository.findByWorkflowId(id).orElse(null);
        BigDecimal deducted = detail == null ? null : detail.getTdsDeducted();
        BigDecimal approvedTotal = challans.stream()
                .filter(c -> c.getStatus() == TdsChallanStatus.APPROVED)
                .map(TdsChallan::getTdsAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal excess = deducted == null ? null
                : approvedTotal.subtract(deducted).max(BigDecimal.ZERO);
        return new ChallanListResponse(challans, deducted, approvedTotal, excess);
    }

    @PostMapping("/challans")
    @PreAuthorize("hasRole('ACCOUNTANT') or hasRole('OPERATOR')")
    public ResponseEntity<TdsChallan> propose(@PathVariable UUID id,
                                              @RequestBody ChallanRequest req,
                                              @AuthenticationPrincipal UserDetailsImpl principal) {
        return ResponseEntity.status(201).body(challanService.propose(
                principal.getOrganizationId(), id, req, principal.getUsername()));
    }

    @PutMapping("/challans/{challanId}")
    @PreAuthorize("hasRole('ACCOUNTANT') or hasRole('OPERATOR')")
    public TdsChallan update(@PathVariable UUID id, @PathVariable UUID challanId,
                             @RequestBody ChallanRequest req,
                             @AuthenticationPrincipal UserDetailsImpl principal) {
        return challanService.update(principal.getOrganizationId(), id, challanId, req,
                principal.getUsername());
    }

    @DeleteMapping("/challans/{challanId}")
    @PreAuthorize("hasRole('ACCOUNTANT') or hasRole('OPERATOR')")
    public ResponseEntity<Void> delete(@PathVariable UUID id, @PathVariable UUID challanId,
                                       @AuthenticationPrincipal UserDetailsImpl principal) {
        challanService.delete(principal.getOrganizationId(), id, challanId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/challans/{challanId}/approve")
    @PreAuthorize("hasRole('ACCOUNTANT')")
    public TdsChallan approve(@PathVariable UUID id, @PathVariable UUID challanId,
                              @AuthenticationPrincipal UserDetailsImpl principal) {
        return challanService.approve(principal.getOrganizationId(), id, challanId,
                principal.getUsername());
    }

    @ExceptionHandler({WorkflowBadRequestException.class, WorkflowConflictException.class,
            TdsNotFoundException.class, IllegalArgumentException.class})
    public ResponseEntity<Map<String, String>> handle(RuntimeException e) {
        int status;
        if (e instanceof WorkflowBadRequestException || e instanceof IllegalArgumentException) {
            status = 400;
        } else if (e instanceof WorkflowConflictException) {
            status = 409;
        } else {
            status = 404;
        }
        return ResponseEntity.status(status).body(Map.of("error", e.getMessage()));
    }
}
