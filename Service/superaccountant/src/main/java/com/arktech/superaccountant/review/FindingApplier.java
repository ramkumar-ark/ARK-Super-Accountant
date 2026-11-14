package com.arktech.superaccountant.review;

/**
 * Per-module validate-and-apply hook. {@link #validate} runs at submission time, {@link #apply}
 * at approval time inside the caller's transaction, so any exception rolls back both the module
 * write and the finding status change.
 */
public interface FindingApplier<F extends ReviewableFinding> {
    void validate(F finding, String proposedValue, String note);

    void apply(F finding, String approver);
}
