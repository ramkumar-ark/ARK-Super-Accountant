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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TdsChallanServiceTest {

    private final UUID orgId = UUID.randomUUID();
    private final UUID workflowId = UUID.randomUUID();
    private final UUID challanId = UUID.randomUUID();

    private TdsChallanRepository challanRepository;
    private TdsDeducteeEntryRepository entryRepository;
    private WorkflowRepository workflowRepository;
    private TdsChallanService service;
    private Workflow workflow;

    @BeforeEach
    void setUp() {
        challanRepository = mock(TdsChallanRepository.class);
        entryRepository = mock(TdsDeducteeEntryRepository.class);
        workflowRepository = mock(WorkflowRepository.class);
        service = new TdsChallanService(challanRepository, entryRepository, workflowRepository);
        workflow = new Workflow();
        workflow.setId(workflowId);
        workflow.setOrganizationId(orgId);
        workflow.setType(WorkflowType.TDS_PAYMENT);
        workflow.setStage(WorkflowStage.PREPARE);
        when(workflowRepository.findById(workflowId)).thenReturn(Optional.of(workflow));
        when(challanRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private ChallanRequest request(String bsr, String serial, String tds, boolean nil) {
        return new ChallanRequest(bsr, LocalDate.of(2025, 5, 7), serial, "cin",
                tds == null ? null : new BigDecimal(tds), new BigDecimal("10"),
                new BigDecimal("40"), new BigDecimal("5"), null, nil);
    }

    @Test
    void propose_rejectsBadBsrAndSerial() {
        assertThrows(WorkflowBadRequestException.class,
                () -> service.propose(orgId, workflowId, request("12345", "1234", "0", false), "op"));
        assertThrows(WorkflowBadRequestException.class,
                () -> service.propose(orgId, workflowId, request("1234567", "123456", "0", false),
                        "op"));
        assertThrows(WorkflowBadRequestException.class,
                () -> service.propose(orgId, workflowId,
                        new ChallanRequest("1234567", null, "1234", "cin", null, null, null,
                                null, null, false), "op"));
    }

    @Test
    void propose_computesTotalServerSide() {
        TdsChallan saved = service.propose(orgId, workflowId,
                request("1234567", "1234", "1000", false), "op");

        ArgumentCaptor<TdsChallan> captor = ArgumentCaptor.forClass(TdsChallan.class);
        verify(challanRepository).save(captor.capture());
        assertEquals(0, new BigDecimal("1055.00").compareTo(captor.getValue().getTotalAmount()));
        assertEquals(TdsChallanStatus.PROPOSED, saved.getStatus());
        assertEquals("op", saved.getProposedBy());
        assertNull(saved.getApprovedBy());
    }

    @Test
    void propose_outsidePrepare_conflicts() {
        workflow.setStage(WorkflowStage.FINDINGS_REVIEW);

        assertThrows(WorkflowConflictException.class,
                () -> service.propose(orgId, workflowId, request("1234567", "1234", "0", false),
                        "op"));
    }

    @Test
    void propose_wrongWorkflowType_conflicts() {
        workflow.setType(WorkflowType.TDS_26Q);

        assertThrows(WorkflowBadRequestException.class,
                () -> service.propose(orgId, workflowId, request("1234567", "1234", "0", false),
                        "op"));
    }

    @Test
    void update_approvedChallan_revertsToProposed() {
        TdsChallan existing = new TdsChallan();
        existing.setId(challanId);
        existing.setPaymentWorkflowId(workflowId);
        existing.setStatus(TdsChallanStatus.APPROVED);
        existing.setApprovedBy("acct");
        existing.setApprovedAt(java.time.Instant.now());
        when(challanRepository.findById(challanId)).thenReturn(Optional.of(existing));

        TdsChallan updated = service.update(orgId, workflowId, challanId,
                request("1234567", "1234", "0", false), "op");

        assertEquals(TdsChallanStatus.PROPOSED, updated.getStatus());
        assertNull(updated.getApprovedBy());
        assertNull(updated.getApprovedAt());
        assertEquals("op", updated.getProposedBy());
    }

    @Test
    void delete_unmapsEntries() {
        TdsChallan existing = new TdsChallan();
        existing.setId(challanId);
        existing.setPaymentWorkflowId(workflowId);
        when(challanRepository.findById(challanId)).thenReturn(Optional.of(existing));
        TdsDeducteeEntry e = new TdsDeducteeEntry();
        e.setId(UUID.randomUUID());
        e.setTdsChallanId(challanId);
        when(entryRepository.findByTdsChallanIdAndSupersededAtIsNull(challanId))
                .thenReturn(List.of(e));

        service.delete(orgId, workflowId, challanId);

        assertNull(e.getTdsChallanId());
        verify(entryRepository).saveAll(List.of(e));
        verify(challanRepository).delete(existing);
    }

    @Test
    void nilChallan_requiresZeroTds() {
        assertThrows(WorkflowBadRequestException.class,
                () -> service.propose(orgId, workflowId, request("1234567", "1234", "100", true),
                        "op"));
    }
}
