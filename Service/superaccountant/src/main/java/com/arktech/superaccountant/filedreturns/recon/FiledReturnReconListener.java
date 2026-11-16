package com.arktech.superaccountant.filedreturns.recon;

import com.arktech.superaccountant.filedreturns.FiledReturnImportedEvent;
import com.arktech.superaccountant.filedreturns.FiledReturnLine;
import com.arktech.superaccountant.filedreturns.FiledReturnLineRepository;
import com.arktech.superaccountant.filedreturns.FiledReturnSnapshot;
import com.arktech.superaccountant.filedreturns.FiledReturnSnapshotRepository;
import com.arktech.superaccountant.gst.mapping.TaxLedgerMappingSavedEvent;
import com.arktech.superaccountant.workflow.seam.BaselineConfirmedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Set;
import java.util.UUID;

/**
 * Re-runs baseline reconciliation after a filed-return import, a tax-ledger mapping save or a
 * baseline confirm. Failures are caught and logged: a recon problem must never fail an import
 * or a confirm.
 */
@Component
public class FiledReturnReconListener {

    static final String ACTOR = "system:filed-returns";

    private static final Logger log = LoggerFactory.getLogger(FiledReturnReconListener.class);

    private static final Set<String> AMENDMENT_TABLES =
            Set.of("B2BA", "CDNRA", "B2CLA", "EXPA", "CDNURA");

    @Autowired
    private FiledReturnReconService reconService;

    @Autowired
    private FiledReturnSnapshotRepository snapshotRepository;

    @Autowired
    private FiledReturnLineRepository lineRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onImported(FiledReturnImportedEvent event) {
        try {
            FiledReturnSnapshot snapshot = snapshotRepository.findById(event.snapshotId()).orElse(null);
            if (snapshot == null || snapshot.getBaselineWorkflowId() == null) {
                return;
            }
            boolean amendments = lineRepository
                    .findBySnapshotIdOrderByLineNo(event.snapshotId()).stream()
                    .map(FiledReturnLine::getTableCode)
                    .anyMatch(t -> t != null && AMENDMENT_TABLES.contains(t));
            if (amendments) {
                reconService.reconcileAll(event.organizationId(), ACTOR);
            } else {
                reconService.reconcile(event.organizationId(), snapshot.getBaselineWorkflowId(), ACTOR);
            }
        } catch (RuntimeException e) {
            log.warn("Filed-return reconciliation after import failed: {}", e.getMessage());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMappingSaved(TaxLedgerMappingSavedEvent event) {
        reconcileAllQuietly(event.organizationId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBaselineConfirmed(BaselineConfirmedEvent event) {
        reconcileAllQuietly(event.organizationId());
    }

    private void reconcileAllQuietly(UUID organizationId) {
        try {
            reconService.reconcileAll(organizationId, ACTOR);
        } catch (RuntimeException e) {
            log.warn("Filed-return reconciliation re-run failed: {}", e.getMessage());
        }
    }
}
