package com.arktech.superaccountant.tds.controllers;

import com.arktech.superaccountant.login.security.services.UserDetailsImpl;
import com.arktech.superaccountant.masters.repository.OrganizationRepository;
import com.arktech.superaccountant.tds.models.TdsSectionConfig;
import com.arktech.superaccountant.tds.rpu.Rpu140Generator;
import com.arktech.superaccountant.tds.rpu.Rpu26qGenerator;
import com.arktech.superaccountant.tds.rpu.RpuHeader;
import com.arktech.superaccountant.tds.models.Tds26qDetail;
import com.arktech.superaccountant.tds.models.TdsDeducteeEntry;
import com.arktech.superaccountant.tds.payload.request.MapEntriesRequest;
import com.arktech.superaccountant.tds.repository.Tds26qDetailRepository;
import com.arktech.superaccountant.tds.repository.TdsDeducteeEntryRepository;
import com.arktech.superaccountant.tds.services.TdsQuarterService;
import com.arktech.superaccountant.tds.workflow.Tds26qFinalizePrecondition;
import com.arktech.superaccountant.tds.workflow.TdsGateCondition;
import com.arktech.superaccountant.workflow.models.Workflow;
import com.arktech.superaccountant.workflow.models.WorkflowStage;
import com.arktech.superaccountant.workflow.models.WorkflowType;
import com.arktech.superaccountant.workflow.repository.WorkflowRepository;
import com.arktech.superaccountant.tds.models.TdsChallan;
import com.arktech.superaccountant.tds.models.TdsChallanStatus;
import com.arktech.superaccountant.tds.models.TdsPaymentDetail;
import com.arktech.superaccountant.tds.payload.request.ChallanRequest;
import com.arktech.superaccountant.tds.payload.response.ChallanListResponse;
import com.arktech.superaccountant.tds.payload.response.QuarterChallansResponse;
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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.Map;
import java.util.UUID;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/v1/workflows/{id}/tds")
public class TdsWorkflowController {

    private final TdsChallanService challanService;
    private final TdsPaymentDetailRepository paymentDetailRepository;
    private final TdsDeducteeEntryRepository entryRepository;
    private final TdsQuarterService quarterService;
    private final Tds26qFinalizePrecondition finalize26q;
    private final Tds26qDetailRepository detail26qRepository;
    private final OrganizationRepository organizationRepository;
    private final WorkflowRepository workflowRepository;
    private final Rpu26qGenerator rpu26qGenerator;
    private final Rpu140Generator rpu140Generator;

    public TdsWorkflowController(TdsChallanService challanService,
                                 TdsPaymentDetailRepository paymentDetailRepository,
                                 TdsDeducteeEntryRepository entryRepository,
                                 TdsQuarterService quarterService,
                                 Tds26qFinalizePrecondition finalize26q,
                                 Tds26qDetailRepository detail26qRepository,
                                 OrganizationRepository organizationRepository,
                                 WorkflowRepository workflowRepository,
                                 Rpu26qGenerator rpu26qGenerator,
                                 Rpu140Generator rpu140Generator) {
        this.challanService = challanService;
        this.paymentDetailRepository = paymentDetailRepository;
        this.entryRepository = entryRepository;
        this.quarterService = quarterService;
        this.finalize26q = finalize26q;
        this.detail26qRepository = detail26qRepository;
        this.organizationRepository = organizationRepository;
        this.workflowRepository = workflowRepository;
        this.rpu26qGenerator = rpu26qGenerator;
        this.rpu140Generator = rpu140Generator;
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

    // ---------- 26Q quarter endpoints ----------

    @GetMapping("/entries")
    public List<TdsDeducteeEntry> entries(@PathVariable UUID id,
                                          @AuthenticationPrincipal UserDetailsImpl principal) {
        Workflow w = loadQuarter(principal.getOrganizationId(), id);
        return entryRepository.findByWorkflowIdAndSupersededAtIsNull(w.getId());
    }

    @PostMapping("/entries/map")
    @PreAuthorize("hasRole('ACCOUNTANT') or hasRole('OPERATOR')")
    public List<TdsDeducteeEntry> mapEntries(@PathVariable UUID id,
                                             @RequestBody MapEntriesRequest req,
                                             @AuthenticationPrincipal UserDetailsImpl principal) {
        Workflow q = loadQuarter(principal.getOrganizationId(), id);
        if (q.getStage() != WorkflowStage.PREPARE) {
            throw new WorkflowConflictException(
                    "Entries can only be mapped while the quarter is in Prepare");
        }
        Set<UUID> approvedIds = quarterService.approvedChallans(q).stream()
                .map(TdsChallan::getId).collect(Collectors.toSet());
        if (req.challanId() != null && !approvedIds.contains(req.challanId())) {
            throw new WorkflowConflictException("Challan is not an approved challan of this quarter");
        }
        List<TdsDeducteeEntry> entries = entryRepository
                .findByWorkflowIdAndSupersededAtIsNull(q.getId());
        Set<UUID> entryIds = new HashSet<>(req.entryIds() == null ? List.of() : req.entryIds());
        Map<UUID, TdsDeducteeEntry> byId = entries.stream()
                .collect(Collectors.toMap(TdsDeducteeEntry::getId, java.util.function.Function.identity()));
        if (!entryIds.isEmpty() && !byId.keySet().containsAll(entryIds)) {
            throw new WorkflowConflictException("Entry is not a current entry of this quarter");
        }
        List<TdsDeducteeEntry> changed = new ArrayList<>();
        for (UUID entryId : entryIds) {
            TdsDeducteeEntry e = byId.get(entryId);
            e.setTdsChallanId(req.challanId());
            changed.add(e);
        }
        return entryRepository.saveAll(changed);
    }

    @GetMapping("/gate")
    public List<TdsGateCondition> gate(@PathVariable UUID id,
                                       @AuthenticationPrincipal UserDetailsImpl principal) {
        Workflow q = loadQuarter(principal.getOrganizationId(), id);
        return finalize26q.evaluate(q);
    }

    @GetMapping("/quarter-challans")
    public List<QuarterChallansResponse> quarterChallans(
            @PathVariable UUID id, @AuthenticationPrincipal UserDetailsImpl principal) {
        Workflow q = loadQuarter(principal.getOrganizationId(), id);
        return quarterService.approvedChallansByMonth(q).entrySet().stream()
                .sorted(java.util.Map.Entry.comparingByKey(
                        java.util.Comparator.comparing(Workflow::getPeriodKey)))
                .map(e -> new QuarterChallansResponse(e.getKey().getId(),
                        e.getKey().getPeriodKey(), e.getKey().getStage(), e.getValue()))
                .toList();
    }

    @GetMapping("/export")
    @PreAuthorize("hasRole('ACCOUNTANT')")
    public ResponseEntity<byte[]> export(@PathVariable UUID id,
                                         @AuthenticationPrincipal UserDetailsImpl principal)
            throws java.io.IOException {
        Workflow q = loadQuarter(principal.getOrganizationId(), id);
        WorkflowStage stage = q.getStage();
        if (stage != WorkflowStage.PREPARE && stage != WorkflowStage.FINALIZED
                && stage != WorkflowStage.FILED) {
            throw new WorkflowConflictException(
                    "Export is available from Prepare onward");
        }
        if (stage == WorkflowStage.PREPARE) {
            List<String> unmet = finalize26q.unmet(q);
            if (!unmet.isEmpty()) {
                throw new WorkflowConflictException(
                        "Export is available once the gate conditions pass: "
                                + String.join("; ", unmet));
            }
        }
        Tds26qDetail detail = detail26qRepository.findByWorkflowId(q.getId()).orElseThrow();
        com.arktech.superaccountant.masters.models.Organization organization =
                organizationRepository.findById(q.getOrganizationId()).orElse(null);
        int fyStartMonth = organization != null ? organization.getFinancialYearStart() : 4;
        com.arktech.superaccountant.masters.models.InterestPayerType interestPayerType =
                com.arktech.superaccountant.masters.models.InterestPayerType.from(
                        organization != null ? organization.getInterestPayerType() : null);
        int quarter = (q.getPeriodStart().getMonthValue() - fyStartMonth + 12) % 12 / 3 + 1;
        RpuHeader header = new RpuHeader(detail.getTan(), detail.getDeductorPan(),
                detail.getDeductorName(), detail.getDeductorAddress(), detail.getDeductorType(),
                detail.getResponsiblePersonName(), detail.getResponsiblePersonDesignation(),
                detail.getOriginalReceiptNumber(), q.getFinancialYear(), quarter,
                LocalDate.now(), q.getRevision() > 0);
        List<TdsDeducteeEntry> entries =
                entryRepository.findByWorkflowIdAndSupersededAtIsNull(q.getId());
        boolean newAct = TdsSectionConfig.newActApplies(q.getPeriodStart());
        String body;
        String filename;
        if (newAct) {
            body = rpu140Generator.generate(header, quarterService.approvedChallans(q), entries,
                    interestPayerType);
            filename = "140_" + detail.getTan() + "_FY" + q.getFinancialYear()
                    + "_Q" + quarter + ".txt";
        } else {
            body = rpu26qGenerator.generate(header, quarterService.approvedChallans(q), entries);
            filename = "26Q_" + detail.getTan() + "_FY" + q.getFinancialYear()
                    + "_Q" + quarter + ".txt";
        }
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                .contentType(org.springframework.http.MediaType.TEXT_PLAIN)
                .body(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private Workflow loadQuarter(UUID organizationId, UUID id) {
        Workflow w = workflowRepository.findById(id)
                .orElseThrow(() -> new TdsNotFoundException("Workflow not found"));
        if (!w.getOrganizationId().equals(organizationId)) {
            throw new TdsNotFoundException("Workflow not found");
        }
        if (w.getType() != WorkflowType.TDS_26Q) {
            throw new WorkflowBadRequestException("Not a TDS_26Q workflow");
        }
        return w;
    }
}