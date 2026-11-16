package com.arktech.superaccountant.gst.mapping.payload;

import com.arktech.superaccountant.gst.mapping.GstHead;
import com.arktech.superaccountant.gst.mapping.TaxLedgerRole;
import java.util.List;

public record SaveTaxLedgerMappingRequest(List<Item> items) {

    public record Item(String ledgerName, TaxLedgerRole role, GstHead head) {
    }
}
