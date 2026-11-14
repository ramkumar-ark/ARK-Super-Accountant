package com.arktech.superaccountant.workflow.analysis;

import com.arktech.superaccountant.workflow.models.Workflow;
import com.arktech.superaccountant.workflow.models.WorkflowEvent;
import com.arktech.superaccountant.workflow.models.WorkflowEventKind;
import com.arktech.superaccountant.workflow.models.WorkflowStage;
import com.arktech.superaccountant.workflow.repository.WorkflowEventRepository;
import com.arktech.superaccountant.workflow.repository.WorkflowRepository;
import com.arktech.superaccountant.workflow.seam.MastersJobGate;
import com.arktech.superaccountant.workflow.service.WorkflowBadRequestException;
import com.arktech.superaccountant.workflow.service.WorkflowConflictException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Moves a workflow from DATA_UPLOADED to FINDINGS_REVIEW by running every analyzer that applies
 * to its type. Each run row is written in its own REQUIRES_NEW transaction, a throwing analyzer
 * is recorded as FAILED (fail closed: the workflow stays in DATA_UPLOADED), and the workflow is
 * only advanced after re-locking and confirming the upload has not been replaced mid-run.
 */
@Service
public class WorkflowAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowAnalysisService.class);

    private final WorkflowRepository workflowRepository;
    private final WorkflowEventRepository eventRepository;
    private final WorkflowAnalysisRunRepository runRepository;
    private final MastersJobGate mastersJobGate;
    private final PlatformTransactionManager txManager;
    private final List<WorkflowAnalyzer> analyzers;

    public WorkflowAnalysisService(WorkflowRepository workflowRepository,
                                   WorkflowEventRepository eventRepository,
                                   WorkflowAnalysisRunRepository runRepository,
                                   MastersJobGate mastersJobGate,
                                   PlatformTransactionManager txManager,
                                   List<WorkflowAnalyzer> analyzers) {
        this.workflowRepository = workflowRepository;
        this.eventRepository = eventRepository;
        this.runRepository = runRepository;
        this.mastersJobGate = mastersJobGate;
        this.txManager = txManager;
        this.analyzers = analyzers == null ? List.of() : analyzers;
    }

    // REQUIRES_NEW: the AFTER_COMMIT upload-bound listener fires while the binding transaction is
    // completing, so a REQUIRED join would find no active transaction.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Workflow run(UUID organizationId, UUID workflowId, String actor) {
        Workflow workflow = workflowRepository.findByIdForUpdate(workflowId)
                .orElseThrow(() -> new WorkflowBadRequestException("Workflow not found"));
        if (!organizationId.equals(workflow.getOrganizationId())) {
            throw new WorkflowBadRequestException("Workflow not found");
        }
        if (workflow.getStage() != WorkflowStage.DATA_UPLOADED || workflow.getUploadJobId() == null) {
            throw new WorkflowConflictException("Workflow has no bound upload awaiting analysis");
        }
        UUID uploadJobId = workflow.getUploadJobId();
        UUID mastersJobId = mastersJobGate.latestCompletedMastersJob(organizationId).orElse(null);

        List<WorkflowAnalyzer> matching = analyzers.stream()
                .filter(a -> a.types().contains(workflow.getType()))
                .toList();
        boolean allDone = matching.isEmpty();
        for (WorkflowAnalyzer analyzer : matching) {
            WorkflowAnalysisRun run = new WorkflowAnalysisRun();
            run.setWorkflowId(workflowId);
            run.setAnalyzerCode(analyzer.code());
            run.setUploadJobId(uploadJobId);
            run.setMastersJobId(mastersJobId);
            run.setStatus(AnalysisRunStatus.RUNNING);
            run.setStartedAt(Instant.now());
            saveRunInNewTransaction(run);

            AnalysisContext ctx = new AnalysisContext(workflowId, organizationId, workflow.getType(),
                    workflow.getPeriodStart(), workflow.getPeriodEnd(), uploadJobId, mastersJobId, run.getId());
            try {
                analyzeInNewTransaction(analyzer, ctx);
                run.setStatus(AnalysisRunStatus.DONE);
            } catch (RuntimeException e) {
                log.warn("Analyzer {} failed for workflow {}", analyzer.code(), workflowId, e);
                run.setStatus(AnalysisRunStatus.FAILED);
                run.setErrorMessage(e.getMessage());
            }
            run.setFinishedAt(Instant.now());
            saveRunInNewTransaction(run);
            allDone = allDone || run.getStatus() == AnalysisRunStatus.DONE;
        }

        // Re-lock: the upload may have been replaced while the analyzers were running.
        Workflow reloaded = workflowRepository.findByIdForUpdate(workflowId).orElse(workflow);
        if (!Optional.ofNullable(reloaded.getUploadJobId()).equals(Optional.ofNullable(uploadJobId))
                || reloaded.getStage() != WorkflowStage.DATA_UPLOADED) {
            return reloaded;
        }
        if (allDone) {
            reloaded.setStage(WorkflowStage.FINDINGS_REVIEW);
            reloaded.setMastersJobId(mastersJobId);
            workflowRepository.save(reloaded);
            WorkflowEvent event = new WorkflowEvent();
            event.setWorkflowId(workflowId);
            event.setActor(actor);
            event.setKind(WorkflowEventKind.ANALYSIS_DONE);
            event.setPayload("{\"uploadJobId\":\"" + uploadJobId + "\",\"analyzers\":"
                    + matching.stream().map(a -> "\"" + a.code() + "\"").toList() + "}");
            eventRepository.save(event);
        }
        return reloaded;
    }

    private void saveRunInNewTransaction(WorkflowAnalysisRun run) {
        TransactionTemplate tx = new TransactionTemplate(txManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        tx.executeWithoutResult(status -> runRepository.save(run));
    }

    private void analyzeInNewTransaction(WorkflowAnalyzer analyzer, AnalysisContext ctx) {
        TransactionTemplate tx = new TransactionTemplate(txManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        tx.executeWithoutResult(status -> analyzer.analyze(ctx));
    }
}
