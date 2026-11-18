package com.arktech.superaccountant.gstr1.services;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import com.arktech.superaccountant.daybook.models.ParsedVoucher;
import com.arktech.superaccountant.gstr1.models.Gstr1FillInAttribute;

/**
 * A read-only view over the captured voucher attributes used for GSTR-1
 * reporting classification. Synthetic instances can be built without a
 * database for pure unit testing.
 */
public final class VoucherView {

    private final String voucherKey;
    private final String voucherTypeName;
    private final String partyGstin;
    private final String partyGstRegistrationType;
    private final String placeOfSupply;
    private final String consigneeState;
    private final String consigneeCountry;
    private final String supplyNature;
    private final String typeOfSupply;
    private final String supplierStateCode;
    private final BigDecimal totalValue;
    private final boolean isCancelled;

    private VoucherView(Builder b) {
        this.voucherKey = b.voucherKey;
        this.voucherTypeName = b.voucherTypeName;
        this.partyGstin = b.partyGstin;
        this.partyGstRegistrationType = b.partyGstRegistrationType;
        this.placeOfSupply = b.placeOfSupply;
        this.consigneeState = b.consigneeState;
        this.consigneeCountry = b.consigneeCountry;
        this.supplyNature = b.supplyNature;
        this.typeOfSupply = b.typeOfSupply;
        this.supplierStateCode = b.supplierStateCode;
        this.totalValue = b.totalValue;
        this.isCancelled = b.isCancelled;
    }

    public static VoucherView from(ParsedVoucher voucher, String voucherKey,
                                   String supplierStateCode, BigDecimal totalValue) {
        return new Builder()
                .voucherKey(voucherKey)
                .voucherTypeName(voucher.getVoucherTypeName())
                .partyGstin(voucher.getPartyGstin())
                .partyGstRegistrationType(voucher.getPartyGstRegistrationType())
                .placeOfSupply(voucher.getPlaceOfSupply())
                .consigneeState(voucher.getConsigneeState())
                .consigneeCountry(voucher.getConsigneeCountry())
                .supplyNature(voucher.getSupplyNature())
                .typeOfSupply(voucher.getTypeOfSupply())
                .supplierStateCode(supplierStateCode)
                .totalValue(totalValue)
                .isCancelled(voucher.isCancelled())
                .build();
    }

    public String getVoucherKey() {
        return voucherKey;
    }

    public String getVoucherTypeName() {
        return voucherTypeName;
    }

    public String getPartyGstin() {
        return partyGstin;
    }

    public String getPartyGstRegistrationType() {
        return partyGstRegistrationType;
    }

    public String getPlaceOfSupply() {
        return placeOfSupply;
    }

    public String getConsigneeState() {
        return consigneeState;
    }

    public String getConsigneeCountry() {
        return consigneeCountry;
    }

    public String getSupplyNature() {
        return supplyNature;
    }

    public String getTypeOfSupply() {
        return typeOfSupply;
    }

    public String getSupplierStateCode() {
        return supplierStateCode;
    }

    public BigDecimal getTotalValue() {
        return totalValue;
    }

    public boolean isCancelled() {
        return isCancelled;
    }

    public static final class Builder {
        private String voucherKey;
        private String voucherTypeName;
        private String partyGstin;
        private String partyGstRegistrationType;
        private String placeOfSupply;
        private String consigneeState;
        private String consigneeCountry;
        private String supplyNature;
        private String typeOfSupply;
        private String supplierStateCode;
        private BigDecimal totalValue;
        private boolean isCancelled;

        public Builder voucherKey(String v) {
            this.voucherKey = v;
            return this;
        }

        public Builder voucherTypeName(String v) {
            this.voucherTypeName = v;
            return this;
        }

        public Builder partyGstin(String v) {
            this.partyGstin = v;
            return this;
        }

        public Builder partyGstRegistrationType(String v) {
            this.partyGstRegistrationType = v;
            return this;
        }

        public Builder placeOfSupply(String v) {
            this.placeOfSupply = v;
            return this;
        }

        public Builder consigneeState(String v) {
            this.consigneeState = v;
            return this;
        }

        public Builder consigneeCountry(String v) {
            this.consigneeCountry = v;
            return this;
        }

        public Builder supplyNature(String v) {
            this.supplyNature = v;
            return this;
        }

        public Builder typeOfSupply(String v) {
            this.typeOfSupply = v;
            return this;
        }

        public Builder supplierStateCode(String v) {
            this.supplierStateCode = v;
            return this;
        }

        public Builder totalValue(BigDecimal v) {
            this.totalValue = v;
            return this;
        }

        public Builder isCancelled(boolean v) {
            this.isCancelled = v;
            return this;
        }

        public VoucherView build() {
            return new VoucherView(this);
        }
    }
}
