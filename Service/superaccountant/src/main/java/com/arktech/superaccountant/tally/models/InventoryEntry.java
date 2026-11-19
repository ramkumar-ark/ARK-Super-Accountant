package com.arktech.superaccountant.tally.models;

import com.fasterxml.jackson.annotation.JsonAlias;
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
    @JsonAlias({"STOCKITEMNAME", "Stockitemname"})
    private String stockitemname;

    @JsonProperty("hsn")
    @JsonAlias({"HSN", "Hsn"})
    private String hsn;

    @JsonProperty("uqc")
    @JsonAlias({"UQC", "Uqc"})
    private String uqc;

    @JsonProperty("unit")
    @JsonAlias({"UNIT", "Unit"})
    private String unit;

    @JsonProperty("actualqty")
    @JsonAlias({"ACTUALQTY", "Actualqty"})
    private String actualqty;

    @JsonProperty("billedqty")
    @JsonAlias({"BILLEDQTY", "Billedqty"})
    private String billedqty;

    @JsonProperty("rate")
    @JsonAlias({"RATE", "Rate"})
    private String rate;

    @JsonProperty("amount")
    @JsonAlias({"AMOUNT", "Amount"})
    private String amount;

    private boolean isdeemedpositive;
}
