package com.arktech.superaccountant.gstr1.services.tables;

import com.arktech.superaccountant.gstr1.models.PreparedReturnRow;
import com.arktech.superaccountant.gstr1.services.VoucherView;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class B2clBuilderTest extends TableTestSupport {

    @Test
    void buildsRowForLargeInterStateUnregisteredInvoiceWithOnlyIgst() {
        VoucherView sales = voucher()
                .supplierStateCode("29")
                .placeOfSupply("Tamil Nadu")
                .voucherKey("SB/100")
                .voucherTypeName("Sales")
                .voucherNumber("SB/100")
                .totalValue(v("300000.00"))
                .taxableValue(v("300000.00"))
                .rate(v("18"))
                .igst(v("54000.00"))
                .cgst(v("0.00"))
                .sgst(v("0.00"))
                .cess(v("0.00"))
                .build();

        List<PreparedReturnRow> rows = new B2clBuilder().build(workflow(), List.of(sales));

        assertEquals(1, rows.size());
        PreparedReturnRow row = rows.get(0);
        assertEquals("B2CL", row.getTableCode());
        assertEquals("b2cl:SB/100", row.getRowKey());
        Map<String, Object> payload = row.payload();
        assertEquals("33", payload.get("pos"));
        assertNum("18", num(payload, "rate"));
        assertNum("300000.00", num(payload, "taxableValue"));
        assertNum("54000.00", num(payload, "igst"));
        assertNum("0", num(payload, "cgst"));
        assertNum("0", num(payload, "sgst"));
        assertNum("300000.00", num(payload, "invoiceValue"));
        assertEquals("SB/100", payload.get("invoiceNo"));
    }

    @Test
    void skipsSmallIntraStateAndNilRateInvoices() {
        VoucherView intraState = voucher()
                .placeOfSupply("Karnataka")
                .voucherKey("SB/101")
                .voucherTypeName("Sales")
                .totalValue(v("300000.00"))
                .build();
        VoucherView small = voucher()
                .placeOfSupply("Tamil Nadu")
                .voucherKey("SB/102")
                .voucherTypeName("Sales")
                .totalValue(v("250000.00"))
                .build();

        List<PreparedReturnRow> rows = new B2clBuilder().build(workflow(), List.of(intraState, small));

        assertEquals(0, rows.size());
    }
}
