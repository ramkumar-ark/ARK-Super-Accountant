package com.arktech.superaccountant.tds.mapping;

import com.arktech.superaccountant.masters.models.ResolveStatus;
import com.arktech.superaccountant.review.FindingApplier;
import com.arktech.superaccountant.review.ReviewableFinding;
import com.arktech.superaccountant.workflow.findings.WorkflowFindingSource;
import com.arktech.superaccountant.workflow.findings.WorkflowFindingView;
import com.arktech.superaccountant.workflow.models.Workflow;
import com.arktech.superaccountant.workflow.models.WorkflowStage;
import com.arktech.superaccountant.workflow.models.WorkflowType;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Synthetic workflow findings for unmapped TDS payable ledgers. They block the
 * Prepare/Finalize of every active TDS workflow from the moment mapping starts
 * until the company's TDS-ledger mapping is complete. They are not persisted
 * and cannot be individually reviewed.
 */
@Component
public class TdsMappingFindingSource implements WorkflowFindingSource {

    static final String SOURCE_CODE = "TDS_MAPPING";
    static final String FINDING_TYPE = "TDS_LEDGER_UNMAPPED";

    private static final Set<WorkflowType> TDS_TYPES =
            EnumSet.of(WorkflowType.TDS_PAYMENT, WorkflowType.TDS_26Q);
    // STARTED is deliberately excluded: WorkflowService.discard refuses while any source
    // lists findings, and at STARTED the workflow has no data yet, so a mapping finding
    // must not pin a freshly started workflow (every PAN-less org is a default deductor).
    private static final Set<WorkflowStage> BLOCKED_STAGES = EnumSet.of(WorkflowStage.DATA_UPLOADED,
            WorkflowStage.FINDINGS_REVIEW, WorkflowStage.PREPARE);

    private final TdsLedgerMappingGate gate;

    // Lazy: the gate reaches WorkflowService (via TDS applicability) which reaches WorkflowGate,
    // which injects every BlockingFindingSource including this one; a lazy proxy breaks that cycle.
    public TdsMappingFindingSource(@Lazy TdsLedgerMappingGate gate) {
        this.gate = gate;
    }

    @Override
    public String sourceCode() {
        return SOURCE_CODE;
    }

    @Override
    public boolean appliesTo(Workflow w) {
        return TDS_TYPES.contains(w.getType()) && BLOCKED_STAGES.contains(w.getStage());
    }

    @Override
    public long blockingCount(Workflow w) {
        if (!appliesTo(w)) {
            return 0;
        }
        if (gate.blockerMessage(w.getOrganizationId())
                .map(m -> m.startsWith("No masters upload")).orElse(false)) {
            return 1;
        }
        return gate.unmappedPayableLedgers(w.getOrganizationId()).size();
    }

    @Override
    public List<WorkflowFindingView> list(Workflow w) {
        // Callers (findings controller, WorkflowService.discard) do not pre-filter
        // on appliesTo, so this source must emit only for TDS workflows past upload.
        if (!appliesTo(w)) {
            return List.of();
        }
        List<WorkflowFindingView> out = new ArrayList<>();
        for (String ledgerName : gate.unmappedPayableLedgers(w.getOrganizationId())) {
            out.add(view(ledgerName, w.getOrganizationId()));
        }
        if (out.isEmpty() && gate.blockerMessage(w.getOrganizationId())
                .map(m -> m.startsWith("No masters upload")).orElse(false)) {
            out.add(view(null, w.getOrganizationId()));
        }
        return out;
    }

    private WorkflowFindingView view(String ledgerName, UUID organizationId) {
        String ledgerKey = ledgerName == null ? null : TdsLedgerMappingService.ledgerKey(ledgerName);
        UUID id = syntheticId(organizationId, ledgerKey);
        String message = ledgerName == null
                ? "No masters upload: TDS ledgers cannot be mapped."
                : "TDS ledger '" + ledgerName + "' is not mapped. Map it under Settings → Compliance → TDS ledgers.";
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
        throw new UnsupportedOperationException("TDS-mapping findings are not reviewable");
    }

    @Override
    public void save(ReviewableFinding f) {
        throw new UnsupportedOperationException("TDS-mapping findings are not persisted");
    }

    @Override
    public WorkflowFindingView view(ReviewableFinding f) {
        throw new UnsupportedOperationException("TDS-mapping findings are not persisted");
    }
}
