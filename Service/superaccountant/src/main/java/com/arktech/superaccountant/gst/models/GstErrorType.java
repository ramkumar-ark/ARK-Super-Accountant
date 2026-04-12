package com.arktech.superaccountant.gst.models;

public enum GstErrorType {
    GST_NOT_CHARGED("GST not charged on a taxable item"),
    INCORRECT_GST_AMOUNT("GST amount does not match the expected calculation"),
    MISSING_SUPPLIER_GSTIN("Supplier GSTIN is missing for a registered dealer"),
    INCORRECT_GST_TYPE("Wrong GST type used (IGST vs CGST/SGST mismatch for state)"),
    RCM_MIXED_WITH_TAXABLE("Reverse charge voucher contains non-RCM taxable entries"),
    INCORRECT_GST_LEDGER("Wrong GST ledger direction (Input/Output) for voucher type");

    private final String description;

    GstErrorType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
