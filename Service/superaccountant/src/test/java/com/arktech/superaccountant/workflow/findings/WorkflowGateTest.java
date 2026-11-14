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

    private com.arktech.superaccountant.masters.models.ValidationFinding finding(String ruleCode,
            com.arktech.superaccountant.masters.models.FindingSeverity severity,
            com.arktech.superaccountant.masters.models.ResolveStatus status) {
        com.arktech.superaccountant.masters.models.ValidationFinding f =
                new com.arktech.superaccountant.masters.models.ValidationFinding();
        f.setRuleCode(ruleCode);
        f.setSeverity(severity);
        f.setResolveStatus(status);
        f.setUploadJobId(workflow.getMastersJobId());
        return f;
    }

    @Test
    void scopedSource_nullMastersJob_zeroEmptyNoRepoCalls() {
        ScopedMastersFindingSource source = new ScopedMastersFindingSource();
        source.findingRepository = findingRepository;
        source.scopeService = org.mockito.Mockito.mock(com.arktech.superaccountant.masters.scope.MastersScopeService.class);
        workflow.setMastersJobId(null);

        assertThat(source.appliesTo(workflow)).isTrue();
        assertThat(source.blockingCount(workflow)).isZero();
        assertThat(source.list(workflow)).isEmpty();
        org.mockito.Mockito.verifyNoInteractions(findingRepository, source.scopeService);
    }

    @Test
    void scopedSource_countsAllSeveritiesInScopeOnly() {
        com.arktech.superaccountant.masters.scope.MastersScopeService scopeService =
                org.mockito.Mockito.mock(com.arktech.superaccountant.masters.scope.MastersScopeService.class);
        ScopedMastersFindingSource source = new ScopedMastersFindingSource();
        source.findingRepository = findingRepository;
        source.scopeService = scopeService;
        workflow.setMastersJobId(UUID.randomUUID());
        var ctx = new com.arktech.superaccountant.masters.scope.MastersScopeService.ScopeContext(
                java.util.Map.of(), java.util.Map.of());
        org.mockito.Mockito.when(scopeService.contextFor(workflow.getMastersJobId())).thenReturn(ctx);
        org.mockito.Mockito.when(scopeService.inScope(
                        org.mockito.ArgumentMatchers.any(com.arktech.superaccountant.masters.models.ValidationFinding.class),
                        org.mockito.ArgumentMatchers.eq(WorkflowType.GSTR1),
                        org.mockito.ArgumentMatchers.eq(ctx)))
                .thenAnswer(inv -> {
                    com.arktech.superaccountant.masters.models.ValidationFinding f = inv.getArgument(0);
                    return "HSN_SAC_CODE".equals(f.getRuleCode());
                });
        org.mockito.Mockito.when(findingRepository.findByUploadJobId(workflow.getMastersJobId()))
                .thenReturn(java.util.List.of(
                        finding("HSN_SAC_CODE", com.arktech.superaccountant.masters.models.FindingSeverity.INFO,
                                ResolveStatus.OPEN),
                        finding("HSN_SAC_CODE", com.arktech.superaccountant.masters.models.FindingSeverity.ERROR,
                                ResolveStatus.ACKNOWLEDGED),
                        finding("TDS_SECTION_MAPPING", com.arktech.superaccountant.masters.models.FindingSeverity.ERROR,
                                ResolveStatus.OPEN),
                        finding("HSN_SAC_CODE", com.arktech.superaccountant.masters.models.FindingSeverity.ERROR,
                                ResolveStatus.APPROVED),
                        finding("HSN_SAC_CODE", com.arktech.superaccountant.masters.models.FindingSeverity.ERROR,
                                ResolveStatus.RESOLVED),
                        finding("HSN_SAC_CODE", com.arktech.superaccountant.masters.models.FindingSeverity.ERROR,
                                ResolveStatus.DISCARDED)));

        assertThat(source.blockingCount(workflow)).isEqualTo(2);
    }
}
