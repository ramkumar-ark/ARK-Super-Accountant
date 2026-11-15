package com.arktech.superaccountant.tds.workflow;

import com.arktech.superaccountant.tds.models.TdsChallan;
import com.arktech.superaccountant.tds.models.TdsChallanStatus;
import com.arktech.superaccountant.tds.models.TdsPaymentDetail;
import com.arktech.superaccountant.tds.repository.TdsChallanRepository;
import com.arktech.superaccountant.tds.repository.TdsPaymentDetailRepository;
import com.arktech.superaccountant.workflow.models.Workflow;
import com.arktech.superaccountant.workflow.models.WorkflowType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TdsPaymentFinalizePreconditionTest {

    private final UUID workflowId = UUID.randomUUID();
    private TdsChallanRepository challanRepository;
    private TdsPaymentDetailRepository paymentDetailRepository;
    private TdsPaymentFinalizePrecondition precondition;
    private Workflow workflow;

    @BeforeEach
    void setUp() {
        challanRepository = mock(TdsChallanRepository.class);
        paymentDetailRepository = mock(TdsPaymentDetailRepository.class);
        precondition = new TdsPaymentFinalizePrecondition(challanRepository,
                paymentDetailRepository);
        workflow = new Workflow();
        workflow.setId(workflowId);
        workflow.setOrganizationId(UUID.randomUUID());
        workflow.setType(WorkflowType.TDS_PAYMENT);
        workflow.setPeriodStart(LocalDate.of(2025, 4, 1));
        workflow.setPeriodEnd(LocalDate.of(2025, 4, 30));
        when(paymentDetailRepository.findByWorkflowId(workflowId)).thenReturn(Optional.empty());
        when(challanRepository.findByPaymentWorkflowIdOrderByDepositDateAsc(workflowId))
                .thenReturn(List.of());
    }

    private void detail(String deducted) {
        TdsPaymentDetail d = new TdsPaymentDetail();
        d.setWorkflowId(workflowId);
        d.setTdsDeducted(new BigDecimal(deducted));
        when(paymentDetailRepository.findByWorkflowId(workflowId)).thenReturn(Optional.of(d));
    }

    private TdsChallan challan(TdsChallanStatus status, String tds) {
        TdsChallan c = new TdsChallan();
        c.setStatus(status);
        c.setTdsAmount(new BigDecimal(tds));
        c.setNilChallan(new BigDecimal(tds).signum() == 0);
        return c;
    }

    @Test
    void notAnalysed() {
        List<String> unmet = precondition.unmet(workflow);

        assertFalse(precondition.unmet(workflow).isEmpty());
        assertTrue(unmet.contains(
                "TDS deducted for the month has not been computed; run analysis"));
    }

    @Test
    void noApprovedChallan() {
        detail("1000.00");

        List<String> unmet = precondition.unmet(workflow);

        assertTrue(unmet.contains(
                "At least one approved challan (or an approved nil challan) is required"));
    }

    @Test
    void proposedPending() {
        detail("1000.00");
        when(challanRepository.findByPaymentWorkflowIdOrderByDepositDateAsc(workflowId))
                .thenReturn(List.of(challan(TdsChallanStatus.PROPOSED, "1000")));

        List<String> unmet = precondition.unmet(workflow);

        assertTrue(unmet.contains("1 challan(s) awaiting accountant approval"));
    }

    @Test
    void shortPayment_message() {
        detail("5000.00");
        when(challanRepository.findByPaymentWorkflowIdOrderByDepositDateAsc(workflowId))
                .thenReturn(List.of(challan(TdsChallanStatus.APPROVED, "4000")));

        List<String> unmet = precondition.unmet(workflow);

        assertTrue(unmet.contains(
                "Approved challans cover 4000.00 of 5000.00 TDS deducted (short by 1000.00)"));
    }

    @Test
    void excessAllowed() {
        detail("1000.00");
        when(challanRepository.findByPaymentWorkflowIdOrderByDepositDateAsc(workflowId))
                .thenReturn(List.of(challan(TdsChallanStatus.APPROVED, "1500")));

        assertTrue(precondition.unmet(workflow).isEmpty());
    }

    @Test
    void nilChallanSatisfiesWhenNothingDeducted() {
        detail("0.00");
        when(challanRepository.findByPaymentWorkflowIdOrderByDepositDateAsc(workflowId))
                .thenReturn(List.of(challan(TdsChallanStatus.APPROVED, "0")));

        assertTrue(precondition.unmet(workflow).isEmpty());
    }

    @Test
    void nilChallanDoesNotSatisfyWhenDeducted() {
        detail("100.00");
        when(challanRepository.findByPaymentWorkflowIdOrderByDepositDateAsc(workflowId))
                .thenReturn(List.of(challan(TdsChallanStatus.APPROVED, "0")));

        List<String> unmet = precondition.unmet(workflow);

        assertTrue(unmet.stream().anyMatch(m -> m.startsWith("Approved challans cover")));
        assertTrue(precondition.appliesTo(workflow));
    }
}
