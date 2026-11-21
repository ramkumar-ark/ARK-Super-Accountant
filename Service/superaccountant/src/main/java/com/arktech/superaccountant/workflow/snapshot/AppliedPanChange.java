package com.arktech.superaccountant.workflow.snapshot;

/**
 * A PAN change item that a masters upload applied. The frozen row is only
 * treated as expected when its value still equals {@code baselineValue} and the
 * upload's snapshot value equals {@code newValue}.
 */
public record AppliedPanChange(String ledgerKey, String baselineValue, String newValue) {
}
