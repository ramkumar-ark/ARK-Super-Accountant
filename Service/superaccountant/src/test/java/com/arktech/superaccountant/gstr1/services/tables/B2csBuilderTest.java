package com.arktech.superaccountant.gstr1.services.tables;

import com.arktech.superaccountant.gstr1.models.PreparedReturnRow;
import com.arktech.superaccountant.gstr1.services.VoucherView;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class B2csBuilderTest extends TableTestSupport {

    @Test
    void aggregatesByRateAndPosSummingTaxableAndTaxes() {
        VoucherView karnataka18 = voucher()
                .placeOfSupply("Karnataka")
                .voucherKey("SB/001")
                .voucherTypeName("Sales")
                .voucherNumber("SB/001")
                .totalValue(v("11800.00"))
                .taxableValue(v("10000.00"))
                .rate(v("18"))
                .igst(v("0.00"))
                .cgst(v("900.00"))
                .sgst(v("900.00"))
                .cess(v("10.00"))
                .build();
        VoucherView karnataka18Again = voucher()
                .placeOfSupply("29-Karnataka")
                .voucherKey("SB/002")
                .voucherTypeName("Sales")
                .voucherNumber("SB/002")
                .totalValue(v("5900.00"))
                .taxableValue(v("5000.00"))
                .rate(v("18"))
                .igst(v("0.00"))
                .cgst(v("450.00"))
                .sgst(v("450.00"))
                .cess(v("5.00"))
                .build();
        VoucherView tn5 = voucher()
                .placeOfSupply("Tamil Nadu")
                .voucherKey("SB/003")
                .voucherTypeName("Sales")
                .voucherNumber("SB/003")
                .totalValue(v("10500.00"))
                .taxableValue(v("10000.00"))
                .rate(v("5"))
                .igst(v("500.00"))
                .cgst(v("0.00"))
                .sgst(v("0.00"))
                .cess(v("0.00"))
                .build();

        List<PreparedReturnRow> rows = new B2csBuilder().build(workflow(),
                List.of(karnataka18, karnataka18Again, tn5));

        assertEquals(2, rows.size());
        Map<String, Object> ka = rows.stream()
                .filter(r -> "29".equals(r.payload().get("pos")))
                .findFirst().orElseThrow().payload();
        assertNum("15000.00", num(ka, "taxableValue"));
        assertNum("0", num(ka, "igst"));
        assertNum("1350.00", num(ka, "cgst"));
        assertNum("1350.00", num(ka, "sgst"));
        assertNum("15.00", num(ka, "cess"));
        assertNum("18", num(ka, "rate"));
        Map<String, Object> tn = rows.stream()
                .filter(r -> "33".equals(r.payload().get("pos")))
                .findFirst().orElseThrow().payload();
        assertNum("10000.00", num(tn, "taxableValue"));
        assertNum("500.00", num(tn, "igst"));
    }

    @Test
    void skipsRegisteredCancelledNoteExportAndNilRate() {
        VoucherView registered = voucher()
                .voucherKey("SB/010")
                .voucherTypeName("Sales")
                .partyGstin("29ABCDE1234F1Z5")
                .build();
        VoucherView cancelled = voucher()
                .voucherKey("SB/011")
                .voucherTypeName("Sales")
                .isCancelled(true)
                .build();
        VoucherView note = voucher()
                .voucherKey("CN/010")
                .voucherTypeName("Credit Note")
                .build();
        VoucherView export = voucher()
                .voucherKey("SB/012")
                .voucherTypeName("Sales")
                .consigneeCountry("United States")
                .build();
        VoucherView nil = voucher()
                .voucherKey("SB/013")
                .voucherTypeName("Sales")
                .rate(v("0"))
                .build();

        List<PreparedReturnRow> rows = new B2csBuilder().build(workflow(),
                List.of(registered, cancelled, note, export, nil));

        assertEquals(0, rows.size());
    }
}
