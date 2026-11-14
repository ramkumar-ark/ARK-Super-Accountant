package com.arktech.superaccountant.workflow.service;

import com.arktech.superaccountant.daybook.models.DayBookUploadJob;
import com.arktech.superaccountant.daybook.repository.DayBookUploadJobRepository;
import com.arktech.superaccountant.daybook.services.DayBookIntakeResult;
import com.arktech.superaccountant.daybook.services.DayBookIntakeService;
import com.arktech.superaccountant.masters.models.UploadJobStatus;
import com.arktech.superaccountant.workflow.events.WorkflowUploadBoundEvent;
import com.arktech.superaccountant.workflow.models.Workflow;
import com.arktech.superaccountant.workflow.models.WorkflowEventKind;
import com.arktech.superaccountant.workflow.models.WorkflowStage;
import com.arktech.superaccountant.workflow.repository.WorkflowEventRepository;
import com.arktech.superaccountant.workflow.repository.WorkflowRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UploadBindingServiceTest {

    @Mock WorkflowRepository workflowRepository;
    @Mock WorkflowEventRepository workflowEventRepository;
    @Mock CoverageService coverageService;
    @Mock DayBookUploadJobRepository dayBookUploadJobRepository;
    @Mock DayBookIntakeService dayBookIntakeService;
    @Mock ApplicationEventPublisher publisher;

    @InjectMocks
    UploadBindingService service;

    private final UUID orgId = UUID.randomUUID();
    private final UUID wfId = UUID.randomUUID();
    private final UUID jobId = UUID.randomUUID();

    private Workflow workflow(WorkflowStage stage) {
        Workflow w = new Workflow();
        w.setId(wfId);
        w.setOrganizationId(orgId);
        w.setStage(stage);
        w.setPeriodEnd(LocalDate.of(2025, 4, 30));
        return w;
    }

    private DayBookUploadJob job() {
        DayBookUploadJob j = new DayBookUploadJob();
        j.setId(jobId);
        j.setOrganizationId(orgId);
        j.setStatus(UploadJobStatus.COMPLETED);
        j.setCoverageFrom(LocalDate.of(2025, 4, 1));
        j.setCoverageTo(LocalDate.of(2025, 4, 30));
        return j;
    }

    private void stubWorkflow(WorkflowStage stage) {
        when(workflowRepository.findById(wfId)).thenReturn(Optional.of(workflow(stage)));
    }

    private void stubBind() {
        when(workflowRepository.save(any(Workflow.class))).thenAnswer(i -> i.getArgument(0));
        when(workflowEventRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void bind_fromStarted_setsDataUploadedRecordsEventAndPublishes() {
        stubWorkflow(WorkflowStage.STARTED);
        stubBind();
        when(coverageService.coverageStart(orgId)).thenReturn(LocalDate.of(2025, 4, 1));
        when(dayBookIntakeService.intake(any(), any(), any(), eq(orgId), eq("op1"), eq(wfId)))
                .thenReturn(new DayBookIntakeResult(job(), List.of()));

        UploadBindResult result = service.uploadAndBind(orgId, wfId,
                new MockMultipartFile("file", "daybook.json", "application/json", "{}".getBytes()),
                LocalDate.of(2025, 4, 1), LocalDate.of(2025, 4, 30), "op1");

        assertThat(result.workflow().getStage()).isEqualTo(WorkflowStage.DATA_UPLOADED);
        assertThat(result.workflow().getUploadJobId()).isEqualTo(jobId);
        assertThat(result.workflow().getMastersJobId()).isNull();
        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(publisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue())
                .isEqualTo(new WorkflowUploadBoundEvent(wfId, orgId, jobId, "op1"));
    }

    @Test
    void bind_fromPrepare_returnsToDataUploadedAndClearsMastersJob() {
        Workflow w = workflow(WorkflowStage.PREPARE);
        w.setMastersJobId(UUID.randomUUID());
        w.setUploadJobId(UUID.randomUUID());
        when(workflowRepository.findById(wfId)).thenReturn(Optional.of(w));
        stubBind();
        when(coverageService.coverageStart(orgId)).thenReturn(LocalDate.of(2025, 4, 1));
        when(dayBookIntakeService.intake(any(), any(), any(), eq(orgId), eq("op1"), eq(wfId)))
                .thenReturn(new DayBookIntakeResult(job(), List.of()));

        UploadBindResult result = service.uploadAndBind(orgId, wfId,
                new MockMultipartFile("file", "daybook.json", "application/json", "{}".getBytes()),
                LocalDate.of(2025, 4, 1), LocalDate.of(2025, 4, 30), "op1");

        assertThat(result.workflow().getStage()).isEqualTo(WorkflowStage.DATA_UPLOADED);
        assertThat(result.workflow().getUploadJobId()).isEqualTo(jobId);
        assertThat(result.workflow().getMastersJobId()).isNull();
    }

    @Test
    void bind_whenFinalized_throwsConflict() {
        when(workflowRepository.findById(wfId))
                .thenReturn(Optional.of(workflow(WorkflowStage.FINALIZED)));

        assertThatThrownBy(() -> service.uploadAndBind(orgId, wfId,
                new MockMultipartFile("file", "daybook.json", "application/json", "{}".getBytes()),
                LocalDate.of(2025, 4, 1), LocalDate.of(2025, 4, 30), "op1"))
                .isInstanceOf(WorkflowConflictException.class);
        verify(dayBookIntakeService, never()).intake(any(), any(), any(), any(), any(), any());
    }

    @Test
    void useUpload_notLatestCompleted_throwsConflict() {
        stubWorkflow(WorkflowStage.STARTED);
        when(dayBookUploadJobRepository.findByIdAndOrganizationId(jobId, orgId))
                .thenReturn(Optional.of(job()));
        UUID otherJobId = UUID.randomUUID();
        DayBookUploadJob latest = job();
        latest.setId(otherJobId);
        when(dayBookUploadJobRepository.findTopByOrganizationIdAndStatusOrderByCreatedAtDesc(
                orgId, UploadJobStatus.COMPLETED)).thenReturn(Optional.of(latest));

        assertThatThrownBy(() -> service.useUpload(orgId, wfId, jobId, "op1"))
                .isInstanceOf(WorkflowConflictException.class);
    }

    @Test
    void useUpload_notCoveringRange_throws422WithExactMessage() {
        stubWorkflow(WorkflowStage.STARTED);
        when(coverageService.coverageStart(orgId)).thenReturn(LocalDate.of(2025, 4, 1));
        DayBookUploadJob shortJob = job();
        shortJob.setCoverageFrom(LocalDate.of(2025, 4, 5));
        when(dayBookUploadJobRepository.findByIdAndOrganizationId(jobId, orgId))
                .thenReturn(Optional.of(shortJob));
        when(dayBookUploadJobRepository.findTopByOrganizationIdAndStatusOrderByCreatedAtDesc(
                orgId, UploadJobStatus.COMPLETED)).thenReturn(Optional.of(shortJob));

        assertThatThrownBy(() -> service.useUpload(orgId, wfId, jobId, "op1"))
                .isInstanceOf(UploadCoverageException.class)
                .hasMessage("upload must cover 2025-04-01 to 2025-04-30");
    }

    @Test
    void useUpload_legacyJobWithoutCoverage_returns422() {
        stubWorkflow(WorkflowStage.STARTED);
        when(coverageService.coverageStart(orgId)).thenReturn(LocalDate.of(2025, 4, 1));
        DayBookUploadJob legacy = job();
        legacy.setCoverageFrom(null);
        legacy.setCoverageTo(null);
        when(dayBookUploadJobRepository.findByIdAndOrganizationId(jobId, orgId))
                .thenReturn(Optional.of(legacy));
        when(dayBookUploadJobRepository.findTopByOrganizationIdAndStatusOrderByCreatedAtDesc(
                orgId, UploadJobStatus.COMPLETED)).thenReturn(Optional.of(legacy));

        assertThatThrownBy(() -> service.useUpload(orgId, wfId, jobId, "op1"))
                .isInstanceOf(UploadCoverageException.class);
    }

    @Test
    void useUpload_otherOrgJob_returns404() {
        stubWorkflow(WorkflowStage.STARTED);
        when(dayBookUploadJobRepository.findByIdAndOrganizationId(jobId, orgId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.useUpload(orgId, wfId, jobId, "op1"))
                .isInstanceOf(UploadNotFoundException.class);
    }

    @Test
    void uploadAndBind_declaredRangeTooShort_throws422BeforeIntake() {
        stubWorkflow(WorkflowStage.STARTED);
        when(coverageService.coverageStart(orgId)).thenReturn(LocalDate.of(2025, 4, 1));

        assertThatThrownBy(() -> service.uploadAndBind(orgId, wfId,
                new MockMultipartFile("file", "daybook.json", "application/json", "{}".getBytes()),
                LocalDate.of(2025, 4, 5), LocalDate.of(2025, 4, 30), "op1"))
                .isInstanceOf(UploadCoverageException.class);
        verify(dayBookIntakeService, never()).intake(any(), any(), any(), any(), any(), any());
    }
}
