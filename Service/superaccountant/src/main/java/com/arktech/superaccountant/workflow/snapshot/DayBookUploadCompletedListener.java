package com.arktech.superaccountant.workflow.snapshot;

import com.arktech.superaccountant.daybook.events.DayBookUploadCompletedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Runs the tamper comparison after the intake transaction commits. fallbackExecution
 * matters because the legacy intake route runs without a transaction. A failed
 * comparison must never surface on the upload request, so every runtime failure is
 * caught and logged.
 */
@Component
public class DayBookUploadCompletedListener {

    private static final Logger log = LoggerFactory.getLogger(DayBookUploadCompletedListener.class);

    @Autowired
    private TamperComparisonService comparisonService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onUploadCompleted(DayBookUploadCompletedEvent event) {
        try {
            comparisonService.compare(event.uploadJobId());
        } catch (RuntimeException e) {
            log.error("Tamper comparison after upload completion failed for job {}", event.uploadJobId(), e);
        }
    }
}
