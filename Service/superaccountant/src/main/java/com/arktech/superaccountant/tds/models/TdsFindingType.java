package com.arktech.superaccountant.tds.models;

public enum TdsFindingType {
    NOT_DEDUCTED,
    SHORT_DEDUCTED,
    EXCESS_DEDUCTED,
    WRONG_SECTION,
    ADVANCE_PAYMENT,
    THRESHOLD_CROSSED,
    PAN_MISSING,
    PAN_INVALID,
    HIGHER_RATE_NOT_APPLIED,
    DEBTOR_TDS_RECEIVABLE_MISSING_TAN,
    /** A voucher maps to a TDS section with no active statutory configuration, so it was not analysed. */
    SECTION_NOT_CONFIGURED
}
