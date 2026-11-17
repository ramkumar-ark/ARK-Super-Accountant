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

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
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
    void run_oneOfTwoAnalyzersFails_staysDataUploaded() {
        WorkflowAnalyzer gstr2 = mock(WorkflowAnalyzer.class);
        when(gstr2.code()).thenReturn("GST_DAYBOOK_V2");
        when(gstr2.types()).thenReturn(Set.of(WorkflowType.GSTR1));
        org.mockito.Mockito.doThrow(new RuntimeException("boom2")).when(gstr2).analyze(any());
        service = new WorkflowAnalysisService(workflowRepository, eventRepository, runRepository,
                mastersJobGate, txManager, List.of(gstrAnalyzer, gstr2));

        Workflow result = service.run(workflow.getOrganizationId(), workflow.getId(), "acct");

        assertThat(result.getStage()).isEqualTo(WorkflowStage.DATA_UPLOADED);
        verify(eventRepository, never()).save(any());
        ArgumentCaptor<WorkflowAnalysisRun> runs = ArgumentCaptor.forClass(WorkflowAnalysisRun.class);
        verify(runRepository, org.mockito.Mockito.times(4)).save(runs.capture());
        assertThat(runs.getAllValues()).extracting(WorkflowAnalysisRun::getStatus)
                .contains(AnalysisRunStatus.DONE, AnalysisRunStatus.FAILED);
        assertThat(runs.getAllValues().get(runs.getAllValues().size() - 1).getStatus())
                .isEqualTo(AnalysisRunStatus.FAILED);
    }

    @Test
    void run_twoAnalyzersBothDone_advances() {
        WorkflowAnalyzer gstr2 = mock(WorkflowAnalyzer.class);
        when(gstr2.code()).thenReturn("GST_DAYBOOK_V2");
        when(gstr2.types()).thenReturn(Set.of(WorkflowType.GSTR1));
        service = new WorkflowAnalysisService(workflowRepository, eventRepository, runRepository,
                mastersJobGate, txManager, List.of(gstrAnalyzer, gstr2));

        Workflow result = service.run(workflow.getOrganizationId(), workflow.getId(), "acct");

        assertThat(result.getStage()).isEqualTo(WorkflowStage.FINDINGS_REVIEW);
        ArgumentCaptor<WorkflowEvent> events = ArgumentCaptor.forClass(WorkflowEvent.class);
        verify(eventRepository).save(events.capture());
        assertThat(events.getValue().getKind()).isEqualTo(WorkflowEventKind.ANALYSIS_DONE);
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
    void run_otherOrganization_rejectedWithoutMutation() {
        assertThatThrownBy(() -> service.run(UUID.randomUUID(), workflow.getId(), "acct"))
                .isInstanceOf(com.arktech.superaccountant.workflow.service.WorkflowBadRequestException.class);

        assertThat(workflow.getStage()).isEqualTo(WorkflowStage.DATA_UPLOADED);
        verify(gstrAnalyzer, never()).analyze(any(AnalysisContext.class));
        verify(runRepository, never()).save(any());
        verify(eventRepository, never()).save(any());
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

    @Test
    void staleRunningRow_isFailedAndRetryable() {
        Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
        service = new WorkflowAnalysisService(workflowRepository, eventRepository, runRepository,
                mastersJobGate, txManager, List.of(gstrAnalyzer), clock, Duration.ofMinutes(15));
        WorkflowAnalysisRun stale = runningRun(clock.instant().minus(Duration.ofMinutes(16)));
        when(runRepository.findByWorkflowIdOrderByStartedAtAsc(workflow.getId()))
                .thenReturn(List.of(stale));

        Workflow result = service.run(workflow.getOrganizationId(), workflow.getId(), "acct");

        assertThat(stale.getStatus()).isEqualTo(AnalysisRunStatus.FAILED);
        assertThat(stale.getErrorMessage()).contains("timed out");
        assertThat(stale.getFinishedAt()).isEqualTo(clock.instant());
        assertThat(result.getStage()).isEqualTo(WorkflowStage.FINDINGS_REVIEW);
        ArgumentCaptor<WorkflowAnalysisRun> runs = ArgumentCaptor.forClass(WorkflowAnalysisRun.class);
        verify(runRepository, org.mockito.Mockito.times(3)).save(runs.capture());
        assertThat(runs.getAllValues().get(0)).isSameAs(stale);
        WorkflowAnalysisRun freshRun = runs.getAllValues().get(1);
        assertThat(freshRun).isSameAs(runs.getAllValues().get(2));
        assertThat(freshRun.getStatus()).isEqualTo(AnalysisRunStatus.DONE);
        assertThat(freshRun.getStartedAt()).isEqualTo(clock.instant());
    }

    @Test
    void freshRunningRow_blocksConcurrentStart() {
        Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
        service = new WorkflowAnalysisService(workflowRepository, eventRepository, runRepository,
                mastersJobGate, txManager, List.of(gstrAnalyzer), clock, Duration.ofMinutes(15));
        WorkflowAnalysisRun running = runningRun(clock.instant().minus(Duration.ofMinutes(1)));
        when(runRepository.findByWorkflowIdOrderByStartedAtAsc(workflow.getId()))
                .thenReturn(List.of(running));

        assertThatThrownBy(() -> service.run(workflow.getOrganizationId(), workflow.getId(), "acct"))
                .isInstanceOf(WorkflowConflictException.class)
                .hasMessage("Analysis is already running for this workflow");

        assertThat(running.getStatus()).isEqualTo(AnalysisRunStatus.RUNNING);
        assertThat(workflow.getStage()).isEqualTo(WorkflowStage.DATA_UPLOADED);
        verify(gstrAnalyzer, never()).analyze(any(AnalysisContext.class));
        verify(runRepository, never()).save(any());
    }

    private WorkflowAnalysisRun runningRun(Instant startedAt) {
        WorkflowAnalysisRun run = new WorkflowAnalysisRun();
        run.setWorkflowId(workflow.getId());
        run.setAnalyzerCode("GST_DAYBOOK");
        run.setUploadJobId(workflow.getUploadJobId());
        run.setStatus(AnalysisRunStatus.RUNNING);
        run.setStartedAt(startedAt);
        return run;
    }
}
