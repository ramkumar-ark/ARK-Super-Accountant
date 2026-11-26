package com.arktech.superaccountant.statutory.models;

/**
 * Statute a statutory section belongs to. Sections are keyed by (statute, code),
 * so the same table holds Income-tax Act sections and, later, GST Act sections.
 */
public enum Statute {
    INCOME_TAX,
    CGST
}
