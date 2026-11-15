package com.arktech.superaccountant.gst.workflow;

import com.arktech.superaccountant.daybook.models.ParsedVoucher;
import com.arktech.superaccountant.daybook.repository.ParsedVoucherRepository;
import com.arktech.superaccountant.gst.models.GstDaybookFinding;
import com.arktech.superaccountant.gst.models.VoucherSide;
import com.arktech.superaccountant.gst.repository.GstDaybookFindingRepository;
import com.arktech.superaccountant.gst.rules.GstRuleContext;
import com.arktech.superaccountant.masters.models.Organization;
import com.arktech.superaccountant.masters.models.UploadLedgerSnapshot;
import com.arktech.superaccountant.masters.repository.OrganizationRepository;
import com.arktech.superaccountant.masters.services.FindingOverrideMasterService;
import com.arktech.superaccountant.review.FindingLifecycleService;
import com.arktech.superaccountant.workflow.analysis.AnalysisContext;
import com.arktech.superaccountant.workflow.analysis.WorkflowAnalyzer;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Shared GST day-book analysis: runs the period rules over the bound upload, then reconciles the
 * fresh findings against the workflow's current (non-superseded) set and supersedes the previous
 * ones. Runs inside the REQUIRES_NEW transaction {@code WorkflowAnalysisService} opens.
 */
public abstract class AbstractGstAnalyzer implements WorkflowAnalyzer {

    private final ParsedVoucherRepository parsedVoucherRepository;
    private final GstDaybookFindingRepository findingRepository;
    private final FindingOverrideMasterService mastersService;
    private final OrganizationRepository organizationRepository;
    private final GstRuleRunner gstRuleRunner;
    private final FindingLifecycleService lifecycle;

    protected AbstractGstAnalyzer(ParsedVoucherRepository parsedVoucherRepository,
                                  GstDaybookFindingRepository findingRepository,
                                  FindingOverrideMasterService mastersService,
                                  OrganizationRepository organizationRepository,
                                  GstRuleRunner gstRuleRunner,
                                  FindingLifecycleService lifecycle) {
        this.parsedVoucherRepository = parsedVoucherRepository;
        this.findingRepository = findingRepository;
        this.mastersService = mastersService;
        this.organizationRepository = organizationRepository;
        this.gstRuleRunner = gstRuleRunner;
        this.lifecycle = lifecycle;
    }

    /** The voucher sides this analyzer owns; no finding is raised on both. */
    protected abstract Set<VoucherSide> keepSides();

    @Override
    public void analyze(AnalysisContext ctx) {
        List<ParsedVoucher> vouchers = parsedVoucherRepository.findByUploadJobId(ctx.uploadJobId());
        Map<String, UploadLedgerSnapshot> snapshots = ctx.mastersJobId() == null
                ? Map.of()
                : mastersService.snapshotsByName(ctx.mastersJobId());
        String gstin = organizationRepository.findById(ctx.organizationId())
                .map(Organization::getGstin)
                .orElse(null);
        GstRuleContext ruleCtx = new GstRuleContext(ctx.organizationId(), ctx.workflowId(),
                ctx.uploadJobId(), gstin, snapshots);

        List<GstDaybookFinding> fresh = gstRuleRunner.run(ruleCtx, vouchers, ctx.periodStart(),
                ctx.periodEnd(), keepSides());

        List<GstDaybookFinding> previous = findingRepository
                .findByWorkflowIdAndSupersededAtIsNullOrderByVoucherDateAscCreatedAtAsc(ctx.workflowId());

        Instant now = Instant.now();
        lifecycle.reconcile(previous, fresh, now);
        for (GstDaybookFinding f : previous) {
            f.setSupersededAt(now);
        }
        findingRepository.saveAll(previous);
        findingRepository.saveAll(fresh);
    }
}
