package com.arktech.superaccountant.gstr1.services.tables;

import com.arktech.superaccountant.gstr1.models.PreparedReturnRow;
import com.arktech.superaccountant.gstr1.services.VoucherView;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AdvanceBuilderTest extends TableTestSupport {

    @Test
    void advanceReceiptBecomesAtRow() {
        VoucherView receipt = voucher()
                .placeOfSupply("Tamil Nadu")
                .voucherKey("RCT/001")
                .voucherTypeName("Advance Receipt")
                .voucherNumber("RCT/001")
                .totalValue(v("11800.00"))
                .taxableValue(v("10000.00"))
                .rate(v("18"))
                .igst(v("1800.00"))
                .build();

        List<PreparedReturnRow> rows = new AdvanceBuilder().build(workflow(), List.of(receipt));

        assertEquals(1, rows.size());
        PreparedReturnRow row = rows.get(0);
        assertEquals("AT", row.getTableCode());
        assertEquals("at:RCT/001", row.getRowKey());
        Map<String, Object> payload = row.payload();
        assertEquals("33", payload.get("pos"));
        assertNum("18", num(payload, "rate"));
        assertNum("10000.00", num(payload, "advanceAmount"));
        assertNum("1800.00", num(payload, "igst"));
        assertNull(row.getSourceVoucherKey() == null ? null : payload.get("adjVoucherNo"));
    }

    @Test
    void advanceAdjustmentBecomesTxpdRow() {
        VoucherView adj = voucher()
                .placeOfSupply("Karnataka")
                .voucherKey("ADJ/001")
                .voucherTypeName("Advance Adjustment")
                .voucherNumber("ADJ/001")
                .totalValue(v("5900.00"))
                .taxableValue(v("5000.00"))
                .rate(v("18"))
                .cgst(v("450.00"))
                .sgst(v("450.00"))
                .build();

        List<PreparedReturnRow> rows = new AdvanceBuilder().build(workflow(), List.of(adj));

        assertEquals(1, rows.size());
        PreparedReturnRow row = rows.get(0);
        assertEquals("TXPD", row.getTableCode());
        assertEquals("txpd:ADJ/001", row.getRowKey());
        assertNum("5000.00", num(row.payload(), "advanceAmount"));
    }

    @Test
    void skipsCancelledAndNonAdvanceVouchers() {
        VoucherView cancelled = voucher()
                .voucherKey("RCT/002")
                .voucherTypeName("Advance Receipt")
                .isCancelled(true)
                .build();
        VoucherView sales = voucher()
                .voucherKey("SB/400")
                .voucherTypeName("Sales")
                .build();

        List<PreparedReturnRow> rows = new AdvanceBuilder().build(workflow(), List.of(cancelled, sales));

        assertEquals(0, rows.size());
    }
}
