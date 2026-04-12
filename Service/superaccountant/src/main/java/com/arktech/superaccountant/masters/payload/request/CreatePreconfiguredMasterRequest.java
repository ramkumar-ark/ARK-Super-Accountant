package com.arktech.superaccountant.masters.payload.request;

import com.arktech.superaccountant.masters.models.LedgerCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreatePreconfiguredMasterRequest {
    @NotBlank
    private String ledgerName;
    @NotNull
    private LedgerCategory category;
    private String expectedParentGroup;
    private Boolean expectedGstApplicable;
    private Boolean expectedTdsApplicable;
}
