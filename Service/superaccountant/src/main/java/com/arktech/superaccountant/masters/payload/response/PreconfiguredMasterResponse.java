package com.arktech.superaccountant.masters.payload.response;

import com.arktech.superaccountant.masters.models.LedgerCategory;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class PreconfiguredMasterResponse {
    private UUID id;
    private String ledgerName;
    private LedgerCategory category;
    private String expectedParentGroup;
    private Boolean expectedGstApplicable;
    private Boolean expectedTdsApplicable;
    private boolean active;
    private Instant createdAt;
    private Instant updatedAt;
}
