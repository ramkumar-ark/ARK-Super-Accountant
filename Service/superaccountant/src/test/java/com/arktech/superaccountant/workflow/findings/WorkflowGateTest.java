package com.arktech.superaccountant.workflow.findings;

import com.arktech.superaccountant.masters.models.ResolveStatus;
import com.arktech.superaccountant.masters.repository.ValidationFindingRepository;
import com.arktech.superaccountant.workflow.models.Workflow;
import com.arktech.superaccountant.workflow.models.WorkflowStage;
import com.arktech.superaccountant.workflow.models.WorkflowType;
import com.arktech.superaccountant.workflow.repository.WorkflowRepository;
import com.arktech.superaccountant.workflow.seam.WorkflowGate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WorkflowGateTest {

    WorkflowRepository workflowRepository;
    ValidationFindingRepository findingRepository;
    Workflow workflow;

    @BeforeEach
    void setUp() {
        workflowRepository = mock(WorkflowRepository.class);
        findingRepository = mock(ValidationFindingRepository.class);
        workflow = new Workflow();
        workflow.setId(UUID.randomUUID());
        workflow.setOrganizationId(UUID.randomUUID());
        workflow.setType(WorkflowType.GSTR1);
        workflow.setStage(WorkflowStage.DATA_UPLOADED);
        when(workflowRepository.findById(workflow.getId())).thenReturn(Optional.of(workflow));
        when(findingRepository.countByUploadJobIdAndResolveStatusNotIn(any(), any())).thenAnswer(inv -> 1L);
    }

    @Test
    void missingWorkflow_zero() {
        WorkflowGate gate = new WorkflowGate(workflowRepository, List.of());
        when(workflowRepository.findById(any())).thenReturn(Optional.empty());

        assertThat(gate.blockingFindingCount(UUID.randomUUID())).isZero();
    }

    @Test
    void blockingCount_sumsOnlyApplicableSources() {
        BlockingFindingSource applicable = mock(BlockingFindingSource.class);
        BlockingFindingSource notApplicable = mock(BlockingFindingSource.class);
        when(applicable.appliesTo(workflow)).thenReturn(true);
        when(applicable.blockingCount(workflow)).thenReturn(2L);
        when(notApplicable.appliesTo(workflow)).thenReturn(false);

        WorkflowGate gate = new WorkflowGate(workflowRepository, List.of(applicable, notApplicable));

        assertThat(gate.blockingFindingCount(workflow.getId())).isEqualTo(2);
        verifyNoBlockingCount(notApplicable);
    }

    private void verifyNoBlockingCount(BlockingFindingSource source) {
        org.mockito.Mockito.verify(source, org.mockito.Mockito.never()).blockingCount(any());
    }

    @Test
    void mastersSource_nullMastersJob_zero() {
        MastersBlockingSource source = new MastersBlockingSource(findingRepository);
        workflow.setMastersJobId(null);

        assertThat(source.appliesTo(workflow)).isTrue();
        assertThat(source.blockingCount(workflow)).isZero();
        org.mockito.Mockito.verifyNoInteractions(findingRepository);
    }

    @Test
    void mastersSource_countsAllSeveritiesExceptTerminal() {
        MastersBlockingSource source = new MastersBlockingSource(findingRepository);
        workflow.setMastersJobId(UUID.randomUUID());

        assertThat(source.blockingCount(workflow)).isEqualTo(1);
        org.mockito.Mockito.verify(findingRepository).countByUploadJobIdAndResolveStatusNotIn(
                workflow.getMastersJobId(), java.util.EnumSet.of(ResolveStatus.APPROVED,
                        ResolveStatus.RESOLVED, ResolveStatus.DISCARDED));
    }
}
