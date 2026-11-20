package com.arktech.superaccountant.gstr1.models;

public enum Gstr1FillInAttribute {
    HSN,
    UQC,
    QTY,
    POS,
    EXPORT_TYPE,
    PORT_CODE,
    SHIPPING_BILL_NO,
    SHIPPING_BILL_DATE,
    SEZ_DEEMED,
    SUPPLY_NATURE,
    /**
     * Accountant acknowledgement that a deleted document may be amended to zero
     * (R5). VOUCHER scope, key = voucher key; value is unused (null is fine).
     */
    AMENDMENT_ZERO_ACK
}
