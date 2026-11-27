package com.arktech.superaccountant.gstr1.services.tables;

import com.arktech.superaccountant.statutory.services.GstStatutorySeed;
import com.arktech.superaccountant.gstr1.models.PreparedReturnRow;
import com.arktech.superaccountant.gstr1.services.VoucherView;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CdnBuilderTest extends TableTestSupport {

    @Test
    void creditNoteWithGstinGoesToCdnrWithNegativeSignedValues() {
        VoucherView note = voucher()
                .voucherKey("CN/001")
                .voucherTypeName("Credit Note")
                .voucherNumber("CN/001")
                .partyGstin("29ABCDE1234F1Z5")
                .totalValue(v("-11800.00"))
                .taxableValue(v("10000.00"))
                .rate(v("18"))
                .igst(v("0.00"))
                .cgst(v("900.00"))
                .sgst(v("900.00"))
                .cess(v("0.00"))
                .build();

        List<PreparedReturnRow> rows = new CdnBuilder(GstStatutorySeed::asParameters).build(workflow(), List.of(note));

        assertEquals(1, rows.size());
        PreparedReturnRow row = rows.get(0);
        assertEquals("CDNR", row.getTableCode());
        assertEquals("cdn:CN/001", row.getRowKey());
        Map<String, Object> payload = row.payload();
        assertEquals("29ABCDE1234F1Z5", payload.get("gstin"));
        assertNum("-10000.00", num(payload, "taxableValue"));
        assertNum("-900.00", num(payload, "cgst"));
        assertNum("-900.00", num(payload, "sgst"));
        assertNum("-11800.00", num(payload, "invoiceValue"));
        assertEquals("Credit Note", payload.get("noteType"));
    }

    @Test
    void unregisteredNoteGoesToCdnurWithSmallType() {
        VoucherView note = voucher()
                .placeOfSupply("Tamil Nadu")
                .voucherKey("CN/002")
                .voucherTypeName("Debit Note")
                .voucherNumber("CN/002")
                .totalValue(v("5900.00"))
                .taxableValue(v("5000.00"))
                .rate(v("18"))
                .igst(v("900.00"))
                .build();

        List<PreparedReturnRow> rows = new CdnBuilder(GstStatutorySeed::asParameters).build(workflow(), List.of(note));

        assertEquals(1, rows.size());
        PreparedReturnRow row = rows.get(0);
        assertEquals("CDNUR", row.getTableCode());
        Map<String, Object> payload = row.payload();
        assertEquals("B2CS", payload.get("type"));
        assertNum("-5000.00", num(payload, "taxableValue"));
        assertNum("-900.00", num(payload, "igst"));
    }

    @Test
    void unregisteredLargeInterStateNoteGetsB2clType() {
        VoucherView note = voucher()
                .placeOfSupply("Tamil Nadu")
                .voucherKey("CN/003")
                .voucherTypeName("Debit Note")
                .totalValue(v("300000.00"))
                .taxableValue(v("300000.00"))
                .igst(v("54000.00"))
                .build();

        List<PreparedReturnRow> rows = new CdnBuilder(GstStatutorySeed::asParameters).build(workflow(), List.of(note));

        assertEquals(1, rows.size());
        assertEquals("B2CL", rows.get(0).payload().get("type"));
    }

    @Test
    void skipsCancelledAndNonNoteVouchers() {
        VoucherView cancelled = voucher()
                .voucherKey("CN/004")
                .voucherTypeName("Credit Note")
                .isCancelled(true)
                .build();
        VoucherView invoice = voucher()
                .voucherKey("SB/006")
                .voucherTypeName("Sales")
                .partyGstin("29ABCDE1234F1Z5")
                .build();

        List<PreparedReturnRow> rows = new CdnBuilder(GstStatutorySeed::asParameters).build(workflow(), List.of(cancelled, invoice));

        assertEquals(0, rows.size());
    }
}
