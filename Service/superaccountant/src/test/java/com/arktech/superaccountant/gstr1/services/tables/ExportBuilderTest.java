package com.arktech.superaccountant.gstr1.services.tables;

import com.arktech.superaccountant.gstr1.models.PreparedReturnRow;
import com.arktech.superaccountant.gstr1.services.VoucherView;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExportBuilderTest extends TableTestSupport {

    @Test
    void flagsWithPaymentExportsAndCarriesIgst() {
        VoucherView wp = voucher()
                .placeOfSupply("Other Territory")
                .voucherKey("SB/200")
                .voucherTypeName("Sales")
                .voucherNumber("SB/200")
                .typeOfSupply("Export with payment")
                .consigneeCountry("United States")
                .totalValue(v("500000.00"))
                .taxableValue(v("500000.00"))
                .rate(v("18"))
                .igst(v("90000.00"))
                .build();

        List<PreparedReturnRow> rows = new ExportBuilder().build(workflow(), List.of(wp));

        assertEquals(1, rows.size());
        PreparedReturnRow row = rows.get(0);
        assertEquals("EXP", row.getTableCode());
        assertEquals("exp:SB/200", row.getRowKey());
        Map<String, Object> payload = row.payload();
        assertEquals("WP", payload.get("exportType"));
        assertNum("90000.00", num(payload, "igst"));
        assertNum("0", num(payload, "cgst"));
        assertNum("500000.00", num(payload, "invoiceValue"));
        assertEquals("United States", payload.get("country"));
    }

    @Test
    void flagsWithoutPaymentForSupplyNatureOrForeignConsignee() {
        VoucherView nature = voucher()
                .voucherKey("SB/201")
                .voucherTypeName("Sales")
                .supplyNature("Export without payment")
                .build();
        VoucherView foreign = voucher()
                .voucherKey("SB/202")
                .voucherTypeName("Sales")
                .consigneeCountry("Singapore")
                .build();

        List<PreparedReturnRow> rows = new ExportBuilder().build(workflow(), List.of(nature, foreign));

        assertEquals(2, rows.size());
        assertEquals("WOP", rows.get(0).payload().get("exportType"));
        assertEquals("WOP", rows.get(1).payload().get("exportType"));
    }

    @Test
    void skipsRegisteredCancelledAndDomestic() {
        VoucherView registered = voucher()
                .voucherKey("SB/203")
                .voucherTypeName("Sales")
                .partyGstin("29ABCDE1234F1Z5")
                .build();
        VoucherView cancelled = voucher()
                .voucherKey("SB/204")
                .voucherTypeName("Sales")
                .consigneeCountry("Singapore")
                .isCancelled(true)
                .build();
        VoucherView domestic = voucher()
                .voucherKey("SB/205")
                .voucherTypeName("Sales")
                .placeOfSupply("Karnataka")
                .build();

        List<PreparedReturnRow> rows = new ExportBuilder().build(workflow(),
                List.of(registered, cancelled, domestic));

        assertEquals(0, rows.size());
    }
}
