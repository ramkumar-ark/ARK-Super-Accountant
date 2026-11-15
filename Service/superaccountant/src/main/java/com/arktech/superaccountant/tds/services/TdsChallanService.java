package com.arktech.superaccountant.tds.services;

import com.arktech.superaccountant.tds.models.TdsChallan;
import com.arktech.superaccountant.tds.models.TdsChallanStatus;
import com.arktech.superaccountant.tds.models.TdsDeducteeEntry;
import com.arktech.superaccountant.tds.payload.request.ChallanRequest;
import com.arktech.superaccountant.tds.repository.TdsChallanRepository;
import com.arktech.superaccountant.tds.repository.TdsDeducteeEntryRepository;
import com.arktech.superaccountant.workflow.models.Workflow;
import com.arktech.superaccountant.workflow.models.WorkflowStage;
import com.arktech.superaccountant.workflow.models.WorkflowType;
import com.arktech.superaccountant.workflow.repository.WorkflowRepository;
import com.arktech.superaccountant.workflow.service.WorkflowBadRequestException;
import com.arktech.superaccountant.workflow.service.WorkflowConflictException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Operator-proposed, accountant-approved challans for a TDS_PAYMENT month. Mutations are allowed
 * only while the workflow is in PREPARE; editing an approved challan drops it back to PROPOSED.
 */
@Service
public class TdsChallanService {

    private static final Pattern BSR_PATTERN = Pattern.compile("[0-9]{7}");
    private static final Pattern SERIAL_PATTERN = Pattern.compile("[0-9]{1,5}");

    private final TdsChallanRepository challanRepository;
    private final TdsDeducteeEntryRepository entryRepository;
    private final WorkflowRepository workflowRepository;

    public TdsChallanService(TdsChallanRepository challanRepository,
                             TdsDeducteeEntryRepository entryRepository,
                             WorkflowRepository workflowRepository) {
        this.challanRepository = challanRepository;
        this.entryRepository = entryRepository;
        this.workflowRepository = workflowRepository;
    }

    public List<TdsChallan> list(UUID organizationId, UUID workflowId) {
        loadForOrg(organizationId, workflowId);
        return challanRepository.findByPaymentWorkflowIdOrderByDepositDateAsc(workflowId);
    }

    @Transactional
    public TdsChallan propose(UUID organizationId, UUID workflowId, ChallanRequest req,
                              String actor) {
        Workflow w = requireMutable(organizationId, workflowId);
        TdsChallan challan = new TdsChallan();
        challan.setPaymentWorkflowId(w.getId());
        applyRequest(challan, req);
        challan.setStatus(TdsChallanStatus.PROPOSED);
        challan.setProposedBy(actor);
        challan.setProposedAt(Instant.now());
        return challanRepository.save(challan);
    }

    @Transactional
    public TdsChallan update(UUID organizationId, UUID workflowId, UUID challanId,
                             ChallanRequest req, String actor) {
        requireMutable(organizationId, workflowId);
        TdsChallan challan = load(organizationId, workflowId, challanId);
        applyRequest(challan, req);
        challan.setStatus(TdsChallanStatus.PROPOSED);
        challan.setProposedBy(actor);
        challan.setProposedAt(Instant.now());
        challan.setApprovedBy(null);
        challan.setApprovedAt(null);
        return challanRepository.save(challan);
    }

    @Transactional
    public TdsChallan approve(UUID organizationId, UUID workflowId, UUID challanId, String actor) {
        requireMutable(organizationId, workflowId);
        TdsChallan challan = load(organizationId, workflowId, challanId);
        challan.setStatus(TdsChallanStatus.APPROVED);
        challan.setApprovedBy(actor);
        challan.setApprovedAt(Instant.now());
        return challanRepository.save(challan);
    }

    @Transactional
    public void delete(UUID organizationId, UUID workflowId, UUID challanId) {
        requireMutable(organizationId, workflowId);
        TdsChallan challan = load(organizationId, workflowId, challanId);
        List<TdsDeducteeEntry> mapped = entryRepository
                .findByTdsChallanIdAndSupersededAtIsNull(challanId);
        mapped.forEach(e -> e.setTdsChallanId(null));
        entryRepository.saveAll(mapped);
        challanRepository.delete(challan);
    }

    private Workflow loadForOrg(UUID organizationId, UUID workflowId) {
        Workflow w = workflowRepository.findById(workflowId)
                .orElseThrow(() -> new TdsNotFoundException("Workflow not found"));
        if (!w.getOrganizationId().equals(organizationId)) {
            throw new TdsNotFoundException("Workflow not found");
        }
        if (w.getType() != WorkflowType.TDS_PAYMENT) {
            throw new WorkflowBadRequestException("Not a TDS_PAYMENT workflow");
        }
        return w;
    }

    private Workflow requireMutable(UUID organizationId, UUID workflowId) {
        Workflow w = loadForOrg(organizationId, workflowId);
        if (w.getStage() != WorkflowStage.PREPARE) {
            throw new WorkflowConflictException(
                    "Challans can only be changed while the payment workflow is in Prepare");
        }
        return w;
    }

    private TdsChallan load(UUID organizationId, UUID workflowId, UUID challanId) {
        TdsChallan challan = challanRepository.findById(challanId)
                .orElseThrow(() -> new TdsNotFoundException("Challan not found"));
        if (!challan.getPaymentWorkflowId().equals(workflowId)) {
            throw new TdsNotFoundException("Challan not found");
        }
        return challan;
    }

    private void applyRequest(TdsChallan challan, ChallanRequest req) {
        validate(req);
        challan.setBsrCode(req.bsrCode());
        challan.setDepositDate(req.depositDate());
        challan.setChallanSerialNumber(req.challanSerialNumber());
        challan.setCin(req.cin());
        BigDecimal tds = amount(req.tdsAmount());
        BigDecimal surcharge = amount(req.surcharge());
        BigDecimal cess = amount(req.educationCess());
        BigDecimal interest = amount(req.interest());
        BigDecimal penalty = amount(req.penalty());
        challan.setTdsAmount(tds);
        challan.setSurcharge(surcharge);
        challan.setEducationCess(cess);
        challan.setInterest(interest);
        challan.setPenalty(penalty);
        challan.setTotalAmount(tds.add(surcharge).add(cess).add(interest).add(penalty));
        challan.setNilChallan(req.nilChallan());
    }

    private void validate(ChallanRequest req) {
        if (req.bsrCode() == null || !BSR_PATTERN.matcher(req.bsrCode()).matches()) {
            throw new WorkflowBadRequestException("BSR code must be exactly 7 digits");
        }
        if (req.challanSerialNumber() == null
                || !SERIAL_PATTERN.matcher(req.challanSerialNumber()).matches()) {
            throw new WorkflowBadRequestException("Challan serial number must be 1 to 5 digits");
        }
        if (req.depositDate() == null) {
            throw new WorkflowBadRequestException("Deposit date is required");
        }
        if (amount(req.tdsAmount()).signum() < 0 || amount(req.surcharge()).signum() < 0
                || amount(req.educationCess()).signum() < 0 || amount(req.interest()).signum() < 0
                || amount(req.penalty()).signum() < 0) {
            throw new WorkflowBadRequestException("Amounts must be zero or positive");
        }
        if (req.nilChallan() && amount(req.tdsAmount()).signum() != 0) {
            throw new WorkflowBadRequestException("A nil challan requires a TDS amount of zero");
        }
    }

    private static BigDecimal amount(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
