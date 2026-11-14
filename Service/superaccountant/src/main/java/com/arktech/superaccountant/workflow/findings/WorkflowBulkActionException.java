package com.arktech.superaccountant.workflow.findings;

import java.util.List;
import java.util.UUID;

/**
 * All-or-nothing bulk failure: the transaction rolls back and the handler maps it to a 409 with
 * the per-finding failures.
 */
public class WorkflowBulkActionException extends RuntimeException {
    public record Failure(UUID findingId, String error) {
    }

    private final List<Failure> failures;

    public WorkflowBulkActionException(String message, List<Failure> failures) {
        super(message);
        this.failures = failures;
    }

    public List<Failure> getFailures() {
        return failures;
    }
}
