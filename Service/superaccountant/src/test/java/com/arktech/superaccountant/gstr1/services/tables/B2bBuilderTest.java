package com.arktech.superaccountant.gstr1.services.tables;

import com.arktech.superaccountant.gstr1.models.PreparedReturnRow;
import com.arktech.superaccountant.gstr1.services.VoucherView;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class B2bBuilderTest extends TableTestSupport {

    @Test
    void buildsOneRowPerRegisteredInvoiceWithNormalizedPos() {
        VoucherView sales = voucher()
                .voucherKey("SB/001")
                .voucherTypeName("Sales")
                .voucherNumber("SB/001")
                .partyGstin("29ABCDE1234F1Z5")
                .totalValue(v("118000.00"))
                .taxableValue(v("100000.00"))
                .rate(v("18"))
                .igst(v("0.00"))
                .cgst(v("9000.00"))
                .sgst(v("9000.00"))
                .cess(v("0.00"))
                .build();

        List<PreparedReturnRow> rows = new B2bBuilder().build(workflow(), List.of(sales));

        assertEquals(1, rows.size());
        PreparedReturnRow row = rows.get(0);
        assertEquals("B2B", row.getTableCode());
        assertEquals("b2b:SB/001", row.getRowKey());
        assertEquals("SB/001", row.getSourceVoucherKey());
        assertEquals(false, row.isAmendment());
        assertNull(row.getOriginalPeriod());
        assertNull(row.getOriginalDocKey());
        Map<String, Object> payload = row.payload();
        assertEquals("29ABCDE1234F1Z5", payload.get("gstin"));
        assertEquals("SB/001", payload.get("invoiceNo"));
        assertEquals("2025-04-15", payload.get("invoiceDate"));
        assertEquals("29", payload.get("pos"));
        assertNum("18", num(payload, "rate"));
        assertNum("100000.00", num(payload, "taxableValue"));
        assertNum("0", num(payload, "igst"));
        assertNum("9000.00", num(payload, "cgst"));
        assertNum("9000.00", num(payload, "sgst"));
        assertNum("0", num(payload, "cess"));
        assertNum("118000.00", num(payload, "invoiceValue"));
    }

    @Test
    void skipsCancelledAndNoteVouchers() {
        VoucherView cancelled = voucher()
                .voucherKey("SB/002")
                .voucherTypeName("Sales")
                .partyGstin("29ABCDE1234F1Z5")
                .isCancelled(true)
                .build();
        VoucherView note = voucher()
                .voucherKey("CN/001")
                .voucherTypeName("Credit Note")
                .partyGstin("29ABCDE1234F1Z5")
                .build();

        List<PreparedReturnRow> rows = new B2bBuilder().build(workflow(), List.of(cancelled, note));

        assertTrue(rows.isEmpty());
    }
}
