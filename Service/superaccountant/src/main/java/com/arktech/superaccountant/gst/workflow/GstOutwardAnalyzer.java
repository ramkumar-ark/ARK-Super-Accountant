package com.arktech.superaccountant.gst.workflow;

import com.arktech.superaccountant.daybook.models.ParsedVoucher;
import com.arktech.superaccountant.daybook.repository.ParsedVoucherRepository;
import com.arktech.superaccountant.gst.models.VoucherSide;
import com.arktech.superaccountant.gst.rules.GstRuleSupport;
import com.arktech.superaccountant.gst.mapping.TaxLedgerMappingGate;
import com.arktech.superaccountant.gst.mapping.TaxLedgerMappingService;
import com.arktech.superaccountant.gst.repository.GstDaybookFindingRepository;
import com.arktech.superaccountant.masters.repository.OrganizationRepository;
import com.arktech.superaccountant.masters.services.FindingOverrideMasterService;
import com.arktech.superaccountant.review.FindingLifecycleService;
import com.arktech.superaccountant.workflow.models.WorkflowType;
import org.springframework.stereotype.Component;

import java.util.Set;

/** GSTR-1 owns the sales side of the day book. */
@Component
public class GstOutwardAnalyzer extends AbstractGstAnalyzer {

    public GstOutwardAnalyzer(ParsedVoucherRepository parsedVoucherRepository,
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
        return "GST_OUTWARD";
    }

    @Override
    public Set<WorkflowType> types() {
        return Set.of(WorkflowType.GSTR1, WorkflowType.IFF);
    }

    @Override
    protected Set<VoucherSide> keepSides() {
        return Set.of(VoucherSide.SALES);
    }

    /** IFF reports only B2B invoices and CDNR credit notes: sales vouchers with a registered party. */
    @Override
    protected boolean includeVoucher(ParsedVoucher voucher, WorkflowType type) {
        return type != WorkflowType.IFF || GstRuleSupport.isValidGstin(voucher.getPartyGstin());
    }
}
