package com.arktech.superaccountant.tds.mapping.payload;

import com.arktech.superaccountant.tds.mapping.TdsLedgerRole;
import java.util.List;

public record SaveTdsLedgerMappingRequest(List<Item> items) {

    public record Item(String ledgerName, TdsLedgerRole role, String section) {
    }
}
