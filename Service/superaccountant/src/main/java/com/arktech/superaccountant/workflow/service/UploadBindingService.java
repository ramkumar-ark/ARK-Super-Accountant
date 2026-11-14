package com.arktech.superaccountant.workflow.service;

import com.arktech.superaccountant.daybook.models.DayBookUploadJob;
import com.arktech.superaccountant.daybook.repository.DayBookUploadJobRepository;
import com.arktech.superaccountant.daybook.services.DayBookIntakeResult;
import com.arktech.superaccountant.daybook.services.DayBookIntakeService;
import com.arktech.superaccountant.daybook.services.DayBookIntakeException;
import com.arktech.superaccountant.masters.models.UploadJobStatus;
import com.arktech.superaccountant.workflow.events.WorkflowUploadBoundEvent;
import com.arktech.superaccountant.workflow.models.Workflow;
import com.arktech.superaccountant.workflow.models.WorkflowEvent;
import com.arktech.superaccountant.workflow.models.WorkflowEventKind;
import com.arktech.superaccountant.workflow.models.WorkflowStage;
import com.arktech.superaccountant.workflow.repository.WorkflowEventRepository;
import com.arktech.superaccountant.workflow.repository.WorkflowRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Binds (or replaces) a day-book upload on a workflow, moving it to DATA_UPLOADED
 * (worker B). Publishes {@link WorkflowUploadBoundEvent} after binding, which the
 * analysis runner (worker R) consumes. A's WorkflowService stays untouched.
 */
@Service
public class UploadBindingService {

    @Autowired
    private WorkflowRepository workflowRepository;

    @Autowired
    private WorkflowEventRepository workflowEventRepository;

    @Autowired
    private CoverageService coverageService;

    @Autowired
    private DayBookUploadJobRepository dayBookUploadJobRepository;

    @Autowired
    private DayBookIntakeService dayBookIntakeService;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    /**
     * Uploads a new day-book file and binds it to the workflow. The declared
     * coverage range is checked against the workflow's required range BEFORE
     * intake, so a rejected request writes no job row.
     */
    @Transactional(noRollbackFor = DayBookIntakeException.class)
    public Workflow uploadAndBind(UUID orgId, UUID workflowId, MultipartFile file,
                                  LocalDate coverageFrom, LocalDate coverageTo, String actor) {
        Workflow w = loadWorkflow(orgId, workflowId);
        assertChangeable(w);
        LocalDate requiredFrom = coverageService.coverageStart(orgId);
        LocalDate requiredTo = w.getPeriodEnd();
        if (!covers(coverageFrom, coverageTo, requiredFrom, requiredTo)) {
            throw new UploadCoverageException(
                    "upload must cover " + requiredFrom + " to " + requiredTo);
        }
        DayBookIntakeResult result = dayBookIntakeService.intake(
                file, coverageFrom, coverageTo, orgId, actor, workflowId);
        return bind(w, result.job().getId(), actor);
    }

    /**
     * Binds an existing COMPLETED day-book upload to the workflow. Only the
     * latest COMPLETED upload of the organization may be used.
     */
    @Transactional
    public Workflow useUpload(UUID orgId, UUID workflowId, UUID uploadJobId, String actor) {
        Workflow w = loadWorkflow(orgId, workflowId);
        assertChangeable(w);
        DayBookUploadJob job = dayBookUploadJobRepository.findByIdAndOrganizationId(uploadJobId, orgId)
                .orElseThrow(UploadNotFoundException::new);
        if (job.getStatus() != UploadJobStatus.COMPLETED) {
            throw new WorkflowConflictException("Only the latest completed upload can be used");
        }
        DayBookUploadJob latest = dayBookUploadJobRepository
                .findTopByOrganizationIdAndStatusOrderByCreatedAtDesc(orgId, UploadJobStatus.COMPLETED)
                .orElse(null);
        if (latest == null || !latest.getId().equals(uploadJobId)) {
            throw new WorkflowConflictException("Only the latest completed upload can be used");
        }
        LocalDate requiredFrom = coverageService.coverageStart(orgId);
        LocalDate requiredTo = w.getPeriodEnd();
        if (!covers(job.getCoverageFrom(), job.getCoverageTo(), requiredFrom, requiredTo)) {
            throw new UploadCoverageException(
                    "upload must cover " + requiredFrom + " to " + requiredTo);
        }
        return bind(w, uploadJobId, actor);
    }

    private Workflow loadWorkflow(UUID orgId, UUID workflowId) {
        Workflow w = workflowRepository.findById(workflowId)
                .orElseThrow(() -> new WorkflowBadRequestException("Workflow not found"));
        if (!w.getOrganizationId().equals(orgId)) {
            throw new WorkflowBadRequestException("Workflow not found");
        }
        return w;
    }

    private void assertChangeable(Workflow w) {
        switch (w.getStage()) {
            case STARTED, DATA_UPLOADED, FINDINGS_REVIEW, PREPARE -> { /* allowed */ }
            default -> throw new WorkflowConflictException(
                    "Upload cannot change once the workflow is finalized");
        }
    }

    private boolean covers(LocalDate from, LocalDate to, LocalDate requiredFrom, LocalDate requiredTo) {
        return from != null && to != null
                && !from.isAfter(requiredFrom)
                && !to.isBefore(requiredTo);
    }

    private Workflow bind(Workflow w, UUID uploadJobId, String actor) {
        UUID previous = w.getUploadJobId();
        w.setUploadJobId(uploadJobId);
        w.setStage(WorkflowStage.DATA_UPLOADED);
        w.setMastersJobId(null);
        w = workflowRepository.save(w);

        WorkflowEvent event = new WorkflowEvent();
        event.setWorkflowId(w.getId());
        event.setActor(actor);
        event.setKind(WorkflowEventKind.UPLOAD_BOUND);
        event.setPayload("{\"uploadJobId\":\"" + uploadJobId + "\","
                + (previous != null ? "\"previousUploadJobId\":\"" + previous + "\"" : "\"previousUploadJobId\":null")
                + "}");
        workflowEventRepository.save(event);

        eventPublisher.publishEvent(new WorkflowUploadBoundEvent(
                w.getId(), w.getOrganizationId(), uploadJobId, actor));
        return w;
    }
}
