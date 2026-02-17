package com.arktech.superaccountant.tally.models;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class LedgerEntry {
    private String ledgername;
    private String amount;
    private String vatexpamount;
    private String gstclass;
    private String gstovrdnnature;
    private String gstovrdntaxability;
    private String gstovrdntypeofsupply;
    private String roundtype;
    private String appropriatefor;
    private boolean isdeemedpositive;
    private boolean ispartyledger;
    private boolean gstoverridden;
    private boolean ledgerfromitem;
    private List<BankAllocation> bankallocations;
    private List<BillAllocation> billallocations;
    private List<GstRateDetail> ratedetails;
}
