package com.arktech.superaccountant.tally.models;

import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Voucher {

    // Metadata
    private VoucherMetadata metadata;

    // Identity
    private String guid;
    private String vouchernumber;
    private String voucherkey;
    private String alterid;
    private String masterid;

    // Dates (format: YYYYMMDD)
    private String date;
    private String effectivedate;
    private String referencedate;
    private String vchstatusdate;

    // Party info
    private String partyledgername;
    private String partyname;
    private String narration;
    private String reference;
    private String enteredby;
    private String partymailingname;
    private String partypincode;
    private String basicbuyername;

    // GST fields
    private String gstregistrationtype;
    private String partygstin;
    private String cmpgstin;
    private String placeofsupply;
    private String statename;
    private String countryofresidence;
    private GstRegistration gstregistration;
    private String cmpgstregistrationtype;
    private String cmpgststate;
    private String vchgstclass;
    private String consigneegstin;
    private String consigneemailingname;
    private String consigneepincode;
    private String consigneestatename;
    private String consigneecountryname;

    // Voucher type
    private String vouchertypename;
    private String vouchernumberseries;
    private String vchentrymode;

    // Address (mixed metadata + string arrays from Tally)
    private List<Object> address;
    private List<Object> basicbuyeraddress;

    // Key booleans for analysis
    private boolean isinvoice;
    private boolean isreversechargeapplicable;
    private boolean istdsoverridden;
    private boolean istcsoverridden;
    private boolean iscancelled;
    private boolean isdeleted;
    private boolean isgstoverridden;
    private boolean hascashflow;
    private boolean ispostdated;
    private boolean isvoid;
    private boolean iseligibleforitc;
    private boolean isewaybillapplicable;

    // Ledger entries — Tally uses different key names depending on voucher type
    @JsonProperty("allledgerentries")
    private List<LedgerEntry> allLedgerEntries;

    @JsonProperty("ledgerentries")
    private List<LedgerEntry> ledgerEntries;

    // GST section
    private List<GstEntry> gst;

    /**
     * Returns all ledger entries regardless of which JSON key was used.
     * Payment vouchers use "allledgerentries", Purchase vouchers use "ledgerentries".
     */
    public List<LedgerEntry> getAllLedgerEntriesCombined() {
        if (allLedgerEntries != null && !allLedgerEntries.isEmpty()) {
            return allLedgerEntries;
        }
        if (ledgerEntries != null && !ledgerEntries.isEmpty()) {
            return ledgerEntries;
        }
        return Collections.emptyList();
    }
}
