package com.arktech.superaccountant.masters.classifier;

import com.arktech.superaccountant.masters.models.LedgerCategory;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ParsedLedger {
    private String name;
    private String parentGroup;
    private String guid;
    private Boolean gstApplicable;
    private Boolean tdsApplicable;
    private LedgerCategory category;
}
