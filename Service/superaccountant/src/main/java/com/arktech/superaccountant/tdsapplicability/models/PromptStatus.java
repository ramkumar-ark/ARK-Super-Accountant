package com.arktech.superaccountant.tdsapplicability.models;

/** Lifecycle of a TDS applicability prompt: open, answered (confirmed) or dismissed. */
public enum PromptStatus {
    OPEN,
    CONFIRMED,
    DISMISSED
}
