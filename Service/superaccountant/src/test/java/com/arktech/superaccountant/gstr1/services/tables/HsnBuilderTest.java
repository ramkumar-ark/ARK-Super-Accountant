package com.arktech.superaccountant.gstr1.services.tables;

import com.arktech.superaccountant.gstr1.models.PreparedReturnRow;
import com.arktech.superaccountant.gstr1.services.VoucherView;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HsnBuilderTest extends TableTestSupport {

    @Test
    void rollsUpPerHsnRateUqcAndSplitsB2bFromB2c() {
        VoucherView b2bOne = voucher()
                .voucherKey("SB/500")
                .voucherTypeName("Sales")
                .partyGstin("29ABCDE1234F1Z5")
                .hsn("4407")
                .rate(v("18"))
                .taxableValue(v("10000.00"))
                .igst(v("0.00"))
                .cgst(v("900.00"))
                .sgst(v("900.00"))
                .cess(v("0.00"))
                .build();
        VoucherView b2bTwo = voucher()
                .voucherKey("SB/501")
                .voucherTypeName("Sales")
                .partyGstin("29ABCDE1234F1Z5")
                .hsn("4407")
                .rate(v("18"))
                .taxableValue(v("5000.00"))
                .cgst(v("450.00"))
                .sgst(v("450.00"))
                .build();
        VoucherView b2c = voucher()
                .placeOfSupply("Karnataka")
                .voucherKey("SB/502")
                .voucherTypeName("Sales")
                .hsn("4407")
                .rate(v("18"))
                .taxableValue(v("2000.00"))
                .cgst(v("180.00"))
                .sgst(v("180.00"))
                .build();
        VoucherView otherHsn = voucher()
                .placeOfSupply("Karnataka")
                .voucherKey("SB/503")
                .voucherTypeName("Sales")
                .hsn("2523")
                .rate(v("5"))
                .taxableValue(v("1000.00"))
                .cgst(v("25.00"))
                .sgst(v("25.00"))
                .build();

        List<PreparedReturnRow> rows = new HsnBuilder().build(workflow(),
                List.of(b2bOne, b2bTwo, b2c, otherHsn));

        assertEquals(3, rows.size());
        PreparedReturnRow b2bRow = rows.stream()
                .filter(r -> r.getRowKey().startsWith("hsn:4407:NA:18:b2b"))
                .findFirst().orElseThrow();
        Map<String, Object> payload = b2bRow.payload();
        assertEquals("B2B", payload.get("supplyType"));
        assertEquals("4407", payload.get("hsn"));
        assertEquals("NA", payload.get("uqc"));
        assertNum("0", num(payload, "quantity"));
        assertNum("15000.00", num(payload, "taxableValue"));
        assertNum("1350.00", num(payload, "cgst"));
        PreparedReturnRow b2cRow = rows.stream()
                .filter(r -> r.getRowKey().startsWith("hsn:4407:NA:18:b2c"))
                .findFirst().orElseThrow();
        assertNum("2000.00", num(b2cRow.payload(), "taxableValue"));
        assertEquals("B2C", b2cRow.payload().get("supplyType"));
    }

    @Test
    void skipsVouchersWithoutHsn() {
        VoucherView noHsn = voucher()
                .voucherKey("SB/504")
                .voucherTypeName("Sales")
                .build();

        List<PreparedReturnRow> rows = new HsnBuilder().build(workflow(), List.of(noHsn));

        assertEquals(0, rows.size());
    }
}
