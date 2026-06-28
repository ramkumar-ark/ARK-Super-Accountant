package com.arktech.superaccountant.masters.payload.request;

import com.arktech.superaccountant.masters.models.LedgerCategory;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class UpdatePreconfiguredMasterRequest {
    private String ledgerName;
    private LedgerCategory category;
    private String expectedParentGroup;
    private Boolean expectedGstApplicable;
    private Boolean expectedTdsApplicable;
    @Pattern(regexp = "NOT_SUBJECT|194C|194J_A|194J_B|194H|194I|194Q|194A|194B|194D|194M|OTHER",
             message = "Invalid TDS section code")
    private String tdsSection;
    @Pattern(regexp = "TAXABLE|EXEMPT|ZERO_RATED|NON_GST|RCM|NOT_APPLICABLE",
             message = "Invalid GST applicability type")
    private String gstApplicabilityType;
    @Pattern(regexp = "\\d{4,8}", message = "HSN/SAC code must be 4-8 digits")
    private String hsnSacCode;
    @Pattern(regexp = "[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z]{1}[1-9A-Z]{1}Z[0-9A-Z]{1}",
             message = "GSTIN must be 15 characters in format: 22AAAAA0000A1Z5")
    private String gstin;
}
