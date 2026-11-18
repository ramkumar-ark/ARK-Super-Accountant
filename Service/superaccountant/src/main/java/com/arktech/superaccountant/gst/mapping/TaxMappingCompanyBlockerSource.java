package com.arktech.superaccountant.gst.mapping;

import com.arktech.superaccountant.workflow.models.Workflow;
import com.arktech.superaccountant.workflow.models.WorkflowStage;
import com.arktech.superaccountant.workflow.models.WorkflowType;
import com.arktech.superaccountant.workflow.seam.CompanyBlockerSource;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Blocks baseline confirmation while the company's GST tax ledgers are not fully
 * mapped. Financial-year close is never blocked by mapping state.
 */
@Component
public class TaxMappingCompanyBlockerSource implements CompanyBlockerSource {

    private static final Set<WorkflowType> GST_TYPES =
            EnumSet.of(WorkflowType.GSTR1, WorkflowType.GSTR3B, WorkflowType.IFF);

    private final TaxLedgerMappingGate gate;
    private final com.arktech.superaccountant.workflow.repository.WorkflowRepository workflowRepository;

    public TaxMappingCompanyBlockerSource(TaxLedgerMappingGate gate,
            com.arktech.superaccountant.workflow.repository.WorkflowRepository workflowRepository) {
        this.gate = gate;
        this.workflowRepository = workflowRepository;
    }

    @Override
    public List<String> baselineConfirmBlockers(UUID organizationId) {
        boolean hasGstBaseline = !workflowRepository
                .findAllByOrganizationIdAndTypeAndStageAndActiveTrue(organizationId, WorkflowType.GSTR1,
                        WorkflowStage.BASELINE)
                .isEmpty()
                || !workflowRepository
                        .findAllByOrganizationIdAndTypeAndStageAndActiveTrue(organizationId, WorkflowType.GSTR3B,
                                WorkflowStage.BASELINE)
                        .isEmpty();
        if (!hasGstBaseline) {
            return List.of();
        }
        return gate.blockerMessage(organizationId)
                .map(List::of)
                .orElse(List.of());
    }

    @Override
    public List<String> financialYearCloseBlockers(UUID organizationId, int financialYear) {
        return List.of();
    }
}
