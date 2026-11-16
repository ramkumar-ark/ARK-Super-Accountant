package com.arktech.superaccountant.gst.workflow;

import com.arktech.superaccountant.daybook.repository.ParsedVoucherRepository;
import com.arktech.superaccountant.gst.models.VoucherSide;
import com.arktech.superaccountant.gst.mapping.TaxLedgerMappingGate;
import com.arktech.superaccountant.gst.mapping.TaxLedgerMappingService;
import com.arktech.superaccountant.gst.repository.GstDaybookFindingRepository;
import com.arktech.superaccountant.masters.repository.OrganizationRepository;
import com.arktech.superaccountant.masters.services.FindingOverrideMasterService;
import com.arktech.superaccountant.review.FindingLifecycleService;
import com.arktech.superaccountant.workflow.models.WorkflowType;
import org.springframework.stereotype.Component;

import java.util.Set;

/** GSTR-3B owns the purchase and other sides of the day book. */
@Component
public class GstInwardAnalyzer extends AbstractGstAnalyzer {

    public GstInwardAnalyzer(ParsedVoucherRepository parsedVoucherRepository,
                             GstDaybookFindingRepository findingRepository,
                             FindingOverrideMasterService mastersService,
                             OrganizationRepository organizationRepository,
                             GstRuleRunner gstRuleRunner,
                             FindingLifecycleService lifecycle,
                             TaxLedgerMappingGate mappingGate,
                             TaxLedgerMappingService mappingService) {
        super(parsedVoucherRepository, findingRepository, mastersService, organizationRepository,
                gstRuleRunner, lifecycle, mappingGate, mappingService);
    }

    @Override
    public String code() {
        return "GST_INWARD";
    }

    @Override
    public Set<WorkflowType> types() {
        return Set.of(WorkflowType.GSTR3B);
    }

    @Override
    protected Set<VoucherSide> keepSides() {
        return Set.of(VoucherSide.PURCHASE, VoucherSide.OTHER);
    }
}
