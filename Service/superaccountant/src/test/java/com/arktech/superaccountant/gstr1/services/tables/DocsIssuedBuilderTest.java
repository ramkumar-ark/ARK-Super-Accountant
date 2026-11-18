package com.arktech.superaccountant.gstr1.services.tables;

import com.arktech.superaccountant.gstr1.models.PreparedReturnRow;
import com.arktech.superaccountant.gstr1.services.VoucherView;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DocsIssuedBuilderTest extends TableTestSupport {

    @Test
    void splitsNumberSeriesAtGapsIntoSeparateRanges() {
        VoucherView s1 = voucher().voucherKey("SB/001").voucherTypeName("Sales")
                .voucherNumber("SB/001").build();
        VoucherView s2 = voucher().voucherKey("SB/002").voucherTypeName("Sales")
                .voucherNumber("SB/002").build();
        VoucherView s5 = voucher().voucherKey("SB/005").voucherTypeName("Sales")
                .voucherNumber("SB/005").build();

        List<PreparedReturnRow> rows = new DocsIssuedBuilder().build(workflow(), List.of(s1, s2, s5));

        assertEquals(2, rows.size());
        PreparedReturnRow first = rows.get(0);
        assertEquals("DOCS", first.getTableCode());
        Map<String, Object> p1 = first.payload();
        assertEquals("Sales", p1.get("voucherType"));
        assertEquals("SB/001", p1.get("from"));
        assertEquals("SB/002", p1.get("to"));
        assertEquals(2, p1.get("total"));
        assertEquals(0, p1.get("cancelled"));
        Map<String, Object> p2 = rows.get(1).payload();
        assertEquals("SB/005", p2.get("from"));
        assertEquals("SB/005", p2.get("to"));
        assertEquals(1, p2.get("total"));
    }

    @Test
    void countsCancelledVouchersInTheirRange() {
        VoucherView s1 = voucher().voucherKey("SB/010").voucherTypeName("Sales")
                .voucherNumber("SB/010").build();
        VoucherView s2 = voucher().voucherKey("SB/011").voucherTypeName("Sales")
                .voucherNumber("SB/011").isCancelled(true).build();
        VoucherView receipt = voucher().voucherKey("RCT/001").voucherTypeName("Receipt")
                .voucherNumber("RCT/001").build();

        List<PreparedReturnRow> rows = new DocsIssuedBuilder().build(workflow(), List.of(s1, s2, receipt));

        assertEquals(2, rows.size());
        Map<String, Object> salesRow = rows.stream()
                .filter(r -> "Sales".equals(r.payload().get("voucherType")))
                .findFirst().orElseThrow().payload();
        assertEquals(2, salesRow.get("total"));
        assertEquals(1, salesRow.get("cancelled"));
    }

    @Test
    void skipsVouchersWithoutNumbers() {
        VoucherView noNumber = voucher().voucherKey("SB/900").voucherTypeName("Sales").build();

        List<PreparedReturnRow> rows = new DocsIssuedBuilder().build(workflow(), List.of(noNumber));

        assertEquals(0, rows.size());
    }
}
