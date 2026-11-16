package com.arktech.superaccountant.gst.mapping;

/**
 * What a mapped GST ledger represents. OUTPUT, INPUT, RCM_LIABILITY and RCM_INPUT are voucher-level tax
 * ledgers; CASH_BALANCE, LIABILITY_BALANCE, CREDIT_BALANCE and GST_TDS mirror GST-portal balances / TDS
 * accounting and never carry output or input tax on a voucher. NOT_TAX is explicitly not a tax ledger.
 */
public enum TaxLedgerRole {
    OUTPUT,
    INPUT,
    RCM_LIABILITY,
    RCM_INPUT,
    /** Electronic cash ledger (cash tax head) balance of the GST portal. */
    CASH_BALANCE,
    /** Electronic liability ledger balance of the GST portal. */
    LIABILITY_BALANCE,
    /** Electronic credit ledger (input tax credit) balance of the GST portal. */
    CREDIT_BALANCE,
    /** GST TDS accounted in books. */
    GST_TDS,
    NOT_TAX;

    /** True for roles that represent tax charged or claimed on vouchers. */
    public boolean isVoucherTax() {
        return this == OUTPUT || this == INPUT || this == RCM_LIABILITY || this == RCM_INPUT;
    }
}
