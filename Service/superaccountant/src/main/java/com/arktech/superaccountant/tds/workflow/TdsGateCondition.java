package com.arktech.superaccountant.tds.workflow;

/** One old-plan 26Q gate condition, evaluated in the original order. */
public record TdsGateCondition(String code, String label, boolean passed, String detail) {
}
