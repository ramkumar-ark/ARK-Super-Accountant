package com.arktech.superaccountant.gst.mapping;

import com.arktech.superaccountant.masters.models.ResolveStatus;
import com.arktech.superaccountant.review.FindingApplier;
import com.arktech.superaccountant.review.ReviewableFinding;
import com.arktech.superaccountant.workflow.findings.WorkflowFindingSource;
import com.arktech.superaccountant.workflow.findings.WorkflowFindingView;
import com.arktech.superaccountant.workflow.models.Workflow;
import com.arktech.superaccountant.workflow.models.WorkflowStage;
import com.arktech.superaccountant.workflow.models.WorkflowType;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Synthetic workflow findings for unmapped GST tax ledgers. They block the
 * Prepare/Finalize of every active GST workflow from the moment mapping starts
 * until the company's tax-ledger mapping is complete. They are not persisted
 * and cannot be individually reviewed.
 */
@Component
public class TaxMappingFindingSource implements WorkflowFindingSource {

    static final String SOURCE_CODE = "TAX_MAPPING";
    static final String FINDING_TYPE = "TAX_LEDGER_UNMAPPED";

    private static final Set<WorkflowType> GST_TYPES =
            EnumSet.of(WorkflowType.GSTR1, WorkflowType.GSTR3B, WorkflowType.IFF);
    private static final Set<WorkflowStage> BLOCKED_STAGES = EnumSet.of(WorkflowStage.STARTED,
            WorkflowStage.DATA_UPLOADED, WorkflowStage.FINDINGS_REVIEW, WorkflowStage.PREPARE);

    private final TaxLedgerMappingGate gate;

    public TaxMappingFindingSource(TaxLedgerMappingGate gate) {
        this.gate = gate;
    }

    @Override
    public String sourceCode() {
        return SOURCE_CODE;
    }

    @Override
    public boolean appliesTo(Workflow w) {
        return GST_TYPES.contains(w.getType()) && BLOCKED_STAGES.contains(w.getStage());
    }

    @Override
    public long blockingCount(Workflow w) {
        if (gate.blockerMessage(w.getOrganizationId())
                .map(m -> m.startsWith("No masters upload")).orElse(false)) {
            return 1;
        }
        return gate.unmappedLedgers(w.getOrganizationId()).size();
    }

    @Override
    public List<WorkflowFindingView> list(Workflow w) {
        List<WorkflowFindingView> out = new ArrayList<>();
        for (String ledgerName : gate.unmappedLedgers(w.getOrganizationId())) {
            out.add(view(ledgerName, w.getOrganizationId()));
        }
        if (out.isEmpty() && gate.blockerMessage(w.getOrganizationId())
                .map(m -> m.startsWith("No masters upload")).orElse(false)) {
            out.add(view(null, w.getOrganizationId()));
        }
        return out;
    }

    private WorkflowFindingView view(String ledgerName, UUID organizationId) {
        String ledgerKey = ledgerName == null ? null : TaxLedgerMappingService.ledgerKey(ledgerName);
        UUID id = syntheticId(organizationId, ledgerKey);
        String message = ledgerName == null
                ? "No masters upload: tax ledgers cannot be mapped."
                : "Tax ledger '" + ledgerName + "' is not mapped. Map it under Settings → Compliance → Tax ledgers.";
        return new WorkflowFindingView(id, SOURCE_CODE, FINDING_TYPE, "HIGH", true,
                message, null, null, null, null, ledgerName,
                ResolveStatus.OPEN, null, null, null, null,
                null, null, null, null, null, null);
    }

    static UUID syntheticId(UUID organizationId, String ledgerKey) {
        return UUID.nameUUIDFromBytes((organizationId + "|" + ledgerKey).getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public Optional<ReviewableFinding> lockForUpdate(Workflow w, UUID findingId) {
        return Optional.empty();
    }

    @Override
    public FindingApplier<ReviewableFinding> applier(Workflow w) {
        throw new UnsupportedOperationException("Tax-mapping findings are not reviewable");
    }

    @Override
    public void save(ReviewableFinding f) {
        throw new UnsupportedOperationException("Tax-mapping findings are not persisted");
    }

    @Override
    public WorkflowFindingView view(ReviewableFinding f) {
        throw new UnsupportedOperationException("Tax-mapping findings are not persisted");
    }
}
