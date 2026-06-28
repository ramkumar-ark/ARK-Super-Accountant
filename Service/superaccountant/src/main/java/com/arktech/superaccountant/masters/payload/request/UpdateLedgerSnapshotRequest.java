package com.arktech.superaccountant.masters.payload.request;

import com.arktech.superaccountant.masters.models.GstApplicabilityType;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class UpdateLedgerSnapshotRequest {
    private String gstin;
    private String tdsSection;
    private String hsnSacCode;
    private Boolean gstApplicable;
    private Boolean tdsApplicable;
    private String parentGroup;
    private GstApplicabilityType gstApplicabilityType;
}
