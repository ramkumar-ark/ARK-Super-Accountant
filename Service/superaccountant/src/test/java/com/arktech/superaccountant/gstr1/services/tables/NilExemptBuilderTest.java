package com.arktech.superaccountant.gstr1.services.tables;

import com.arktech.superaccountant.gstr1.models.PreparedReturnRow;
import com.arktech.superaccountant.gstr1.services.VoucherView;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NilExemptBuilderTest extends TableTestSupport {

    @Test
    void aggregatesNilRateTaxableByPos() {
        VoucherView ka = voucher()
                .placeOfSupply("Karnataka")
                .voucherKey("SB/300")
                .voucherTypeName("Sales")
                .rate(v("0"))
                .taxableValue(v("10000.00"))
                .build();
        VoucherView kaAgain = voucher()
                .placeOfSupply("29-Karnataka")
                .voucherKey("SB/301")
                .voucherTypeName("Sales")
                .rate(v("0"))
                .taxableValue(v("20000.00"))
                .build();
        VoucherView tn = voucher()
                .placeOfSupply("Tamil Nadu")
                .voucherKey("SB/302")
                .voucherTypeName("Sales")
                .rate(v("0"))
                .taxableValue(v("5000.00"))
                .build();

        List<PreparedReturnRow> rows = new NilExemptBuilder().build(workflow(), List.of(ka, kaAgain, tn));

        assertEquals(2, rows.size());
        Map<String, Object> kaRow = rows.stream()
                .filter(r -> "29".equals(r.payload().get("pos")))
                .findFirst().orElseThrow().payload();
        assertNum("30000.00", num(kaRow, "taxableValue"));
        Map<String, Object> tnRow = rows.stream()
                .filter(r -> "33".equals(r.payload().get("pos")))
                .findFirst().orElseThrow().payload();
        assertNum("5000.00", num(tnRow, "taxableValue"));
    }

    @Test
    void skipsRegisteredNonNilAndCancelled() {
        VoucherView registered = voucher()
                .voucherKey("SB/303")
                .voucherTypeName("Sales")
                .partyGstin("29ABCDE1234F1Z5")
                .rate(v("0"))
                .build();
        VoucherView taxed = voucher()
                .voucherKey("SB/304")
                .voucherTypeName("Sales")
                .rate(v("18"))
                .build();
        VoucherView cancelled = voucher()
                .voucherKey("SB/305")
                .voucherTypeName("Sales")
                .rate(v("0"))
                .isCancelled(true)
                .build();

        List<PreparedReturnRow> rows = new NilExemptBuilder().build(workflow(),
                List.of(registered, taxed, cancelled));

        assertEquals(0, rows.size());
    }
}
