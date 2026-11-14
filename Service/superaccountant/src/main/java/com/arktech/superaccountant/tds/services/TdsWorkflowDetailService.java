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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;

@Service
public class TdsWorkflowDetailService {

    @Autowired
    TdsPaymentDetailRepository paymentDetailRepository;

    @Autowired
    Tds26qDetailRepository twenty6qDetailRepository;

    @Autowired
    WorkflowRepository workflowRepository;

    @Autowired
    OrganizationRepository organizationRepository;

    @Transactional
    public TdsPaymentDetail ensurePaymentDetail(Workflow workflow) {
        if (workflow.getType() != WorkflowType.TDS_PAYMENT) {
            throw new IllegalArgumentException("Not a TDS_PAYMENT workflow");
        }
        return paymentDetailRepository.findByWorkflowId(workflow.getId())
                .orElseGet(() -> {
                    TdsPaymentDetail detail = new TdsPaymentDetail();
                    detail.setWorkflowId(workflow.getId());
                    detail.setMonth(workflow.getPeriodStart());
                    detail.setDueDate(TdsDueDates.paymentDueDate(YearMonth.from(workflow.getPeriodStart())));
                    return paymentDetailRepository.save(detail);
                });
    }

    @Transactional
    public Tds26qDetail ensure26qDetail(Workflow workflow) {
        if (workflow.getType() != WorkflowType.TDS_26Q) {
            throw new IllegalArgumentException("Not a TDS_26Q workflow");
        }
        return twenty6qDetailRepository.findByWorkflowId(workflow.getId())
                .orElseGet(() -> {
                    Tds26qDetail detail = new Tds26qDetail();
                    detail.setWorkflowId(workflow.getId());

                    Organization org = organizationRepository.findById(workflow.getOrganizationId())
                            .orElseThrow(() -> new IllegalStateException("Organization not found: " + workflow.getOrganizationId()));
                    detail.setTan(org.getTan());
                    detail.setDeductorPan(org.getPan());
                    detail.setDeductorName(org.getName());
                    detail.setDeductorAddress(org.getRegisteredAddress());
                    detail.setDeductorType(org.getDeductorType());
                    detail.setResponsiblePersonName(org.getResponsiblePersonName());
                    detail.setResponsiblePersonDesignation(org.getResponsiblePersonDesignation());

                    if (workflow.getSupersedesWorkflowId() != null) {
                        workflowRepository.findById(workflow.getSupersedesWorkflowId())
                                .map(Workflow::getFilingRef)
                                .ifPresent(detail::setOriginalReceiptNumber);
                    }

                    return twenty6qDetailRepository.save(detail);
                });
    }
}
