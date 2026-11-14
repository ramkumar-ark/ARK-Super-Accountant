package com.arktech.superaccountant.review;

/**
 * Applier for findings that only need a note: validation requires a non-blank note and applying
 * writes nothing outside the finding row itself.
 */
public class NoteOnlyApplier<F extends ReviewableFinding> implements FindingApplier<F> {
    @Override
    public void validate(F finding, String proposedValue, String note) {
        if (note == null || note.isBlank()) {
            throw new FindingReviewException(400, "A note is required.");
        }
    }

    @Override
    public void apply(F finding, String approver) {
        // nothing to write outside the finding
    }
}
