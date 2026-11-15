package com.arktech.superaccountant.workflow.snapshot;

import com.arktech.superaccountant.masters.events.MastersUploadCompletedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Runs the masters tamper comparison after the masters intake transaction
 * commits. fallbackExecution matters because the intake route runs without a
 * transaction when the controller is not @Transactional. A failed comparison
 * must never surface on the upload request, so every runtime failure is caught
 * and logged.
 */
@Component
public class MastersUploadCompletedListener {

    private static final Logger log = LoggerFactory.getLogger(MastersUploadCompletedListener.class);

    @Autowired
    private MastersTamperComparisonService comparisonService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onUploadCompleted(MastersUploadCompletedEvent event) {
        try {
            comparisonService.compare(event.uploadJobId());
        } catch (RuntimeException e) {
            log.error("Masters tamper comparison after upload completion failed for job {}",
                    event.uploadJobId(), e);
        }
    }
}
