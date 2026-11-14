package com.arktech.superaccountant.workflow.analysis;

import com.arktech.superaccountant.workflow.models.Workflow;
import com.arktech.superaccountant.workflow.models.WorkflowEvent;
import com.arktech.superaccountant.workflow.models.WorkflowEventKind;
import com.arktech.superaccountant.workflow.models.WorkflowStage;
import com.arktech.superaccountant.workflow.models.WorkflowType;
import com.arktech.superaccountant.workflow.repository.WorkflowEventRepository;
import com.arktech.superaccountant.workflow.repository.WorkflowRepository;
import com.arktech.superaccountant.workflow.seam.MastersJobGate;
import com.arktech.superaccountant.workflow.service.WorkflowConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkflowAnalysisServiceTest {

    WorkflowRepository workflowRepository;
    WorkflowEventRepository eventRepository;
    WorkflowAnalysisRunRepository runRepository;
    MastersJobGate mastersJobGate;
    PlatformTransactionManager txManager;
    WorkflowAnalyzer gstrAnalyzer;
    WorkflowAnalyzer tdsAnalyzer;
    WorkflowAnalysisService service;

    Workflow workflow;

    @BeforeEach
    void setUp() {
        workflowRepository = mock(WorkflowRepository.class);
        eventRepository = mock(WorkflowEventRepository.class);
        runRepository = mock(WorkflowAnalysisRunRepository.class);
        mastersJobGate = mock(MastersJobGate.class);
        txManager = mock(PlatformTransactionManager.class);
        when(txManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());

        gstrAnalyzer = mock(WorkflowAnalyzer.class);
        when(gstrAnalyzer.code()).thenReturn("GST_DAYBOOK");
        when(gstrAnalyzer.types()).thenReturn(Set.of(WorkflowType.GSTR1, WorkflowType.GSTR3B));
        tdsAnalyzer = mock(WorkflowAnalyzer.class);
        when(tdsAnalyzer.code()).thenReturn("TDS_DAYBOOK");
        when(tdsAnalyzer.types()).thenReturn(Set.of(WorkflowType.TDS_PAYMENT));

        service = new WorkflowAnalysisService(workflowRepository, eventRepository, runRepository,
                mastersJobGate, txManager, List.of(gstrAnalyzer, tdsAnalyzer));

        workflow = new Workflow();
        workflow.setId(UUID.randomUUID());
        workflow.setOrganizationId(UUID.randomUUID());
        workflow.setType(WorkflowType.GSTR1);
        workflow.setPeriodStart(LocalDate.of(2025, 4, 1));
        workflow.setPeriodEnd(LocalDate.of(2025, 4, 30));
        workflow.setPeriodKey("2025-04");
        workflow.setStage(WorkflowStage.DATA_UPLOADED);
        workflow.setUploadJobId(UUID.randomUUID());
        when(workflowRepository.findByIdForUpdate(workflow.getId())).thenReturn(Optional.of(workflow));
        when(runRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(eventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void run_noAnalyzers_advancesToFindingsReviewAndRecordsEvent() {
        service = new WorkflowAnalysisService(workflowRepository, eventRepository, runRepository,
                mastersJobGate, txManager, List.of());

        service.run(workflow.getOrganizationId(), workflow.getId(), "acct");

        assertThat(workflow.getStage()).isEqualTo(WorkflowStage.FINDINGS_REVIEW);
        ArgumentCaptor<WorkflowEvent> events = ArgumentCaptor.forClass(WorkflowEvent.class);
        verify(eventRepository).save(events.capture());
        assertThat(events.getValue().getKind()).isEqualTo(WorkflowEventKind.ANALYSIS_DONE);
        assertThat(events.getValue().getPayload()).contains(workflow.getUploadJobId().toString());
    }

    @Test
    void run_onlyAnalyzersForWorkflowTypeRun() {
        service.run(workflow.getOrganizationId(), workflow.getId(), "acct");

        verify(gstrAnalyzer).analyze(any(AnalysisContext.class));
        verify(tdsAnalyzer, never()).analyze(any(AnalysisContext.class));
    }

    @Test
    void run_analyzerThrows_recordsFailedAndStaysDataUploaded() {
        org.mockito.Mockito.doThrow(new IllegalStateException("boom")).when(gstrAnalyzer).analyze(any());

        service.run(workflow.getOrganizationId(), workflow.getId(), "acct");

        assertThat(workflow.getStage()).isEqualTo(WorkflowStage.DATA_UPLOADED);
        ArgumentCaptor<WorkflowAnalysisRun> runs = ArgumentCaptor.forClass(WorkflowAnalysisRun.class);
        verify(runRepository, org.mockito.Mockito.atLeastOnce()).save(runs.capture());
        WorkflowAnalysisRun failed = runs.getAllValues().get(runs.getAllValues().size() - 1);
        assertThat(failed.getStatus()).isEqualTo(AnalysisRunStatus.FAILED);
        assertThat(failed.getErrorMessage()).isEqualTo("boom");
        verify(eventRepository, never()).save(any());
    }

    @Test
    void run_uploadReplacedMidRun_doesNotAdvance() {
        Workflow replaced = new Workflow();
        replaced.setId(workflow.getId());
        replaced.setOrganizationId(workflow.getOrganizationId());
        replaced.setType(WorkflowType.GSTR1);
        replaced.setPeriodStart(workflow.getPeriodStart());
        replaced.setPeriodEnd(workflow.getPeriodEnd());
        replaced.setPeriodKey("2025-04");
        replaced.setStage(WorkflowStage.DATA_UPLOADED);
        replaced.setUploadJobId(UUID.randomUUID()); // different upload: the upload was replaced mid-run
        when(workflowRepository.findByIdForUpdate(workflow.getId()))
                .thenReturn(Optional.of(workflow))
                .thenReturn(Optional.of(replaced));

        service.run(workflow.getOrganizationId(), workflow.getId(), "acct");

        assertThat(replaced.getStage()).isEqualTo(WorkflowStage.DATA_UPLOADED);
        verify(eventRepository, never()).save(any());
    }

    @Test
    void run_wrongStage_conflict() {
        workflow.setStage(WorkflowStage.STARTED);

        assertThatThrownBy(() -> service.run(workflow.getOrganizationId(), workflow.getId(), "acct"))
                .isInstanceOf(WorkflowConflictException.class)
                .hasMessage("Workflow has no bound upload awaiting analysis");
    }

    @Test
    void run_noBoundUpload_conflict() {
        workflow.setUploadJobId(null);

        assertThatThrownBy(() -> service.run(workflow.getOrganizationId(), workflow.getId(), "acct"))
                .isInstanceOf(WorkflowConflictException.class);
    }
}
