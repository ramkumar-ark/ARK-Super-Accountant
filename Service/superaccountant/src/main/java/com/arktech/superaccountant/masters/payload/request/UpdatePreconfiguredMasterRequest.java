package com.arktech.superaccountant.masters.payload.request;

import com.arktech.superaccountant.masters.models.LedgerCategory;
import lombok.Data;

@Data
public class UpdatePreconfiguredMasterRequest {
    private String ledgerName;
    private LedgerCategory category;
    private String expectedParentGroup;
    private Boolean expectedGstApplicable;
    private Boolean expectedTdsApplicable;
}
