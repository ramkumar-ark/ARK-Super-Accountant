package com.arktech.superaccountant.masters.services;

import java.util.List;
import java.util.UUID;

/** A bulk override that could not be applied to every targeted finding; nothing was written. */
public class BulkFindingActionException extends RuntimeException {

    public record Failure(UUID findingId, String ledgerName, String error) {}

    private final transient List<Failure> failures;

    public BulkFindingActionException(String message, List<Failure> failures) {
        super(message);
        this.failures = failures;
    }

    public List<Failure> getFailures() {
        return failures;
    }
}
