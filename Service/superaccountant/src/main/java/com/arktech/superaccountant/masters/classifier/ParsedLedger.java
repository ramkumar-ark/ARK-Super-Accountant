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
    private String tdsSection;
    private String gstApplicabilityType;
    private String hsnSacCode;
    private String gstin;
    private String gstRegistrationType;
    private String pan;
    private java.math.BigDecimal openingBalance;
    /** Parent-group chain, lower-case, nearest group first, joined by '|'. */
    private String groupChain;
}
