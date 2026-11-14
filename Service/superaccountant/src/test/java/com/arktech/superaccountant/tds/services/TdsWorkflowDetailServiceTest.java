package com.arktech.superaccountant.tds.services;

import com.arktech.superaccountant.masters.models.Organization;
import com.arktech.superaccountant.masters.repository.OrganizationRepository;
import com.arktech.superaccountant.tds.models.Tds26qDetail;
import com.arktech.superaccountant.tds.models.TdsPaymentDetail;
import com.arktech.superaccountant.tds.repository.Tds26qDetailRepository;
import com.arktech.superaccountant.tds.repository.TdsPaymentDetailRepository;
import com.arktech.superaccountant.workflow.models.Workflow;
import com.arktech.superaccountant.workflow.models.WorkflowType;
import com.arktech.superaccountant.workflow.repository.WorkflowRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TdsWorkflowDetailServiceTest {

    @Mock
    TdsPaymentDetailRepository paymentDetailRepository;
    @Mock
    Tds26qDetailRepository twenty6qDetailRepository;
    @Mock
    WorkflowRepository workflowRepository;
    @Mock
    OrganizationRepository organizationRepository;

    @InjectMocks
    TdsWorkflowDetailService service;

    private Workflow workflow(WorkflowType type, UUID organizationId) {
        Workflow w = new Workflow();
        w.setId(UUID.randomUUID());
        w.setType(type);
        w.setOrganizationId(organizationId);
        w.setPeriodStart(LocalDate.of(2025, 4, 1));
        w.setPeriodEnd(LocalDate.of(2025, 4, 30));
        return w;
    }

    private Organization organization() {
        Organization org = new Organization("Acme");
        org.setId(UUID.randomUUID());
        org.setTan("ABCD12345E");
        org.setPan("ABCDE1234F");
        org.setRegisteredAddress("12 MG Road");
        org.setDeductorType("K");
        org.setResponsiblePersonName("Ram Kumar");
        org.setResponsiblePersonDesignation("Director");
        return org;
    }

    @Test
    void ensurePaymentDetail_createsFirstOfMonthWithDueDate() {
        Workflow w = workflow(WorkflowType.TDS_PAYMENT, UUID.randomUUID());
        when(paymentDetailRepository.findByWorkflowId(w.getId())).thenReturn(Optional.empty());
        lenient().when(paymentDetailRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TdsPaymentDetail d = service.ensurePaymentDetail(w);

        assertThat(d.getMonth()).isEqualTo(LocalDate.of(2025, 4, 1));
        assertThat(d.getDueDate()).isEqualTo(LocalDate.of(2025, 5, 7));
        verify(paymentDetailRepository).save(d);
    }

    @Test
    void ensurePaymentDetail_idempotent() {
        Workflow w = workflow(WorkflowType.TDS_PAYMENT, UUID.randomUUID());
        TdsPaymentDetail existing = new TdsPaymentDetail();
        existing.setWorkflowId(w.getId());
        existing.setMonth(w.getPeriodStart());
        existing.setDueDate(LocalDate.of(2025, 5, 7));
        when(paymentDetailRepository.findByWorkflowId(w.getId())).thenReturn(Optional.of(existing));

        TdsPaymentDetail d = service.ensurePaymentDetail(w);

        assertThat(d).isSameAs(existing);
        verify(paymentDetailRepository, never()).save(any());
    }

    @Test
    void ensurePaymentDetail_rejectsNonPaymentWorkflow() {
        Workflow w = workflow(WorkflowType.TDS_26Q, UUID.randomUUID());

        assertThatThrownBy(() -> service.ensurePaymentDetail(w))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Not a TDS_PAYMENT workflow");
    }

    @Test
    void ensure26qDetail_copiesDeductorSnapshot() {
        Organization org = organization();
        Workflow w = workflow(WorkflowType.TDS_26Q, org.getId());
        when(twenty6qDetailRepository.findByWorkflowId(w.getId())).thenReturn(Optional.empty());
        when(organizationRepository.findById(org.getId())).thenReturn(Optional.of(org));
        lenient().when(twenty6qDetailRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Tds26qDetail d = service.ensure26qDetail(w);

        assertThat(d.getTan()).isEqualTo("ABCD12345E");
        assertThat(d.getDeductorName()).isEqualTo("Acme");
        assertThat(d.getDeductorAddress()).isEqualTo("12 MG Road");
        assertThat(d.getDeductorType()).isEqualTo("K");
        assertThat(d.getResponsiblePersonName()).isEqualTo("Ram Kumar");
        assertThat(d.getResponsiblePersonDesignation()).isEqualTo("Director");
        verify(twenty6qDetailRepository).save(d);
    }

    @Test
    void ensure26qDetail_idempotent() {
        Organization org = organization();
        Workflow w = workflow(WorkflowType.TDS_26Q, org.getId());
        Tds26qDetail existing = new Tds26qDetail();
        existing.setWorkflowId(w.getId());
        when(twenty6qDetailRepository.findByWorkflowId(w.getId())).thenReturn(Optional.of(existing));

        Tds26qDetail d = service.ensure26qDetail(w);

        assertThat(d).isSameAs(existing);
        verify(twenty6qDetailRepository, never()).save(any());
        verify(organizationRepository, never()).findById(any());
    }

    @Test
    void ensure26qDetail_copiesOriginalReceiptNumberFromSupersededFiling() {
        Organization org = organization();
        Workflow w = workflow(WorkflowType.TDS_26Q, org.getId());
        w.setSupersedesWorkflowId(UUID.randomUUID());

        Workflow original = new Workflow();
        original.setId(w.getSupersedesWorkflowId());
        original.setFilingRef("12345678");

        when(twenty6qDetailRepository.findByWorkflowId(w.getId())).thenReturn(Optional.empty());
        when(organizationRepository.findById(org.getId())).thenReturn(Optional.of(org));
        when(workflowRepository.findById(w.getSupersedesWorkflowId())).thenReturn(Optional.of(original));

        ArgumentCaptor<Tds26qDetail> captor = ArgumentCaptor.forClass(Tds26qDetail.class);

        service.ensure26qDetail(w);

        verify(twenty6qDetailRepository).save(captor.capture());
        assertThat(captor.getValue().getOriginalReceiptNumber()).isEqualTo("12345678");
    }

    @Test
    void ensure26qDetail_rejectsNon26qWorkflow() {
        Workflow w = workflow(WorkflowType.TDS_PAYMENT, UUID.randomUUID());

        assertThatThrownBy(() -> service.ensure26qDetail(w))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Not a TDS_26Q workflow");
    }
}
