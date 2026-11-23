package com.arktech.superaccountant.tdsapplicability;

import com.arktech.superaccountant.workflow.seam.BaselineConfirmedEvent;
import com.arktech.superaccountant.workflow.seam.FinancialYearClosedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Raises TDS applicability prompts after the triggering transaction commits, so the
 * prompt never surfaces on the triggering request. A failed prompt run is logged and
 * dropped: the next baseline confirmation or financial-year close re-checks the rules.
 */
@Component
public class TdsApplicabilityEventListener {

    private static final Logger log = LoggerFactory.getLogger(TdsApplicabilityEventListener.class);

    @Autowired
    private TdsApplicabilityPromptService promptService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBaselineConfirmed(BaselineConfirmedEvent event) {
        try {
            promptService.raiseBooksExceedPrompts(event.organizationId());
        } catch (RuntimeException e) {
            log.error("TDS applicability prompts after baseline confirmation failed for organization {}",
                    event.organizationId(), e);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onFinancialYearClosed(FinancialYearClosedEvent event) {
        try {
            promptService.onFinancialYearClosed(event.organizationId(), event.financialYear());
        } catch (RuntimeException e) {
            log.error("TDS applicability prompts after FY {} close failed for organization {}",
                    event.financialYear(), event.organizationId(), e);
        }
    }
}
