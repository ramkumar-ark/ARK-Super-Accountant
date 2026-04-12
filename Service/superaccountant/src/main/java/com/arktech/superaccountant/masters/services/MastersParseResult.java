package com.arktech.superaccountant.masters.services;

import com.arktech.superaccountant.masters.classifier.ParsedLedger;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class MastersParseResult {
    private List<ParsedLedger> ledgers;
    private int totalLedgersParsed;
}
