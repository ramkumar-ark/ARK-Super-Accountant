package com.arktech.superaccountant.tally.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One INVENTORYENTRIES.LIST line from a Tally voucher.
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class InventoryEntry {

    @JsonProperty("stockitemname")
    private String stockitemname;

    @JsonProperty("hsn")
    private String hsn;

    @JsonProperty("uqc")
    private String uqc;

    @JsonProperty("unit")
    private String unit;

    @JsonProperty("actualqty")
    private String actualqty;

    @JsonProperty("billedqty")
    private String billedqty;

    @JsonProperty("rate")
    private String rate;

    @JsonProperty("amount")
    private String amount;

    private boolean isdeemedpositive;
}
