package com.arktech.superaccountant.workflow.seam;

import com.arktech.superaccountant.workflow.models.Workflow;
import com.arktech.superaccountant.workflow.models.WorkflowStage;
import com.arktech.superaccountant.workflow.repository.WorkflowRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class BaselineGateTest {

    private WorkflowRepository workflowRepository;
    private BaselineGate baselineGate;

    private final UUID orgId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        workflowRepository = Mockito.mock(WorkflowRepository.class);
        baselineGate = new BaselineGate(workflowRepository);
    }

    @Test
    void unconfirmedBaselineRowIsNotConfirmed() {
        when(workflowRepository
                .existsByOrganizationIdAndStageAndActiveTrueAndBaselineConfirmedAtIsNull(
                        orgId, WorkflowStage.BASELINE))
                .thenReturn(true);
        assertFalse(baselineGate.isConfirmed(orgId));
    }

    @Test
    void noUnconfirmedBaselineRowsIsConfirmed() {
        when(workflowRepository
                .existsByOrganizationIdAndStageAndActiveTrueAndBaselineConfirmedAtIsNull(
                        orgId, WorkflowStage.BASELINE))
                .thenReturn(false);
        assertTrue(baselineGate.isConfirmed(orgId));
    }
}
