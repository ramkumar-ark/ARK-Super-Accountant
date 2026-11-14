package com.arktech.superaccountant.tds.rpu;

import com.arktech.superaccountant.tds.models.TdsDeducteeType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RpuFormatSpecTest {

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    private static RpuHeader header() {
        return new RpuHeader(
                "CHEA12345B", "AAACA1234A", "Acme Traders", "12 MG Road, Mumbai", "K",
                "Ravi Kumar", "Director", null, 2026, 1, LocalDate.of(2026, 7, 20));
    }

    @Test
    void fileHeader_fieldCountAndOrderMatchSpec() {
        List<String> f = RpuFormatSpec.fileHeader(
                new RpuFormatSpec.FhData(1, "CHEA12345B", 1, LocalDate.of(2026, 7, 20)));

        assertEquals(18, f.size());
        assertEquals("1", f.get(0));
        assertEquals("FH", f.get(1));
        assertEquals("NS1", f.get(2));
        assertEquals("R", f.get(3));
        assertEquals("20072026", f.get(4));
        assertEquals("D", f.get(6));
        assertEquals("CHEA12345B", f.get(7));
        assertEquals("1", f.get(8));
        assertEquals("SUPER ACCOUNTANT", f.get(9));
    }

    @Test
    void batchHeader_fieldCountMatchesSpec_andCarriesTanPanQuarter() {
        List<String> f = RpuFormatSpec.batchHeader(
                new RpuFormatSpec.BhData(2, 1, 1, header(), bd("1000.00")));

        assertEquals(72, f.size());
        assertEquals("2", f.get(0));
        assertEquals("BH", f.get(1));
        assertEquals("1", f.get(2));
        assertEquals("1", f.get(3));
        assertEquals("26Q", f.get(4));
        assertEquals("CHEA12345B", f.get(12));
        assertEquals("AAACA1234A", f.get(14));
        assertEquals("202728", f.get(15));
        assertEquals("202627", f.get(16));
        assertEquals("Q1", f.get(17));
        assertEquals("Acme Traders", f.get(18));
        assertEquals("N", f.get(50));
        assertEquals("N", f.get(51));
        assertEquals("1000.00", f.get(46));
    }

    @Test
    void challanDetail_fieldCountMatchesSpec() {
        List<String> f = RpuFormatSpec.challanDetail(new RpuFormatSpec.CdData(
                3, 1, "0004329", LocalDate.of(2026, 7, 5), "00042",
                bd("1000.00"), bd("0.00"), bd("0.00"), bd("0.00"), bd("0.00"), bd("1000.00"),
                false, 1, bd("1000.00"), bd("1000.00"), bd("0.00"), bd("0.00"), bd("1000.00")));

        assertEquals(41, f.size());
        assertEquals("3", f.get(0));
        assertEquals("CD", f.get(1));
        assertEquals("1", f.get(3));
        assertEquals("1", f.get(4));
        assertEquals("N", f.get(5));
        assertEquals("00042", f.get(11));
        assertEquals("0004329", f.get(15));
        assertEquals("05072026", f.get(17));
        assertEquals("1000.00", f.get(21));
        assertEquals("1000.00", f.get(26));
        assertEquals("N", f.get(36));
        assertEquals("0.00", f.get(38));
        assertEquals("200", f.get(39));
    }

    @Test
    void deducteeDetail_fieldCountMatchesSpec() {
        List<String> f = RpuFormatSpec.deducteeDetail(new RpuFormatSpec.DdData(
                4, 1, 1, "ABCPE1234F", true, "ABC Contractors", TdsDeducteeType.NON_COMPANY,
                "194C", bd("100000.00"), bd("1000.00"), bd("1000.00"),
                LocalDate.of(2026, 6, 15), LocalDate.of(2026, 6, 15), bd("1.00"), false));

        assertEquals(54, f.size());
        assertEquals("4", f.get(0));
        assertEquals("DD", f.get(1));
        assertEquals("02", f.get(7));
        assertEquals("ABCPE1234F", f.get(9));
        assertEquals("ABC Contractors", f.get(12));
        assertEquals("15062026", f.get(22));
        assertEquals("1.0000", f.get(25));
        assertEquals("94C", f.get(32));
    }

    @Test
    void sectionCode_mapsAllEightInternalCodes() {
        for (String c : List.of("194A", "194C", "194H", "194I", "194J_A", "194J_B", "194M", "194Q")) {
            assertFalse(RpuFormatSpec.sectionCode(c).isBlank(), c);
        }
        assertEquals("94A", RpuFormatSpec.sectionCode("194A"));
        assertEquals("94C", RpuFormatSpec.sectionCode("194C"));
        assertEquals("94H", RpuFormatSpec.sectionCode("194H"));
        assertEquals("4IB", RpuFormatSpec.sectionCode("194I"));
        assertEquals("4JA", RpuFormatSpec.sectionCode("194J_A"));
        assertEquals("4JB", RpuFormatSpec.sectionCode("194J_B"));
        assertEquals("94Q", RpuFormatSpec.sectionCode("194Q"));
        assertThrows(IllegalArgumentException.class, () -> RpuFormatSpec.sectionCode("194Z"));
    }

    @Test
    void pan_invalidOrBlank_isPanNotAvailableLiteral() {
        assertEquals("PANNOTAVBL", RpuFormatSpec.pan(null, false));
        assertEquals("PANNOTAVBL", RpuFormatSpec.pan("", true));
        assertEquals("PANNOTAVBL", RpuFormatSpec.pan("ABCPE1234F", false));
        assertEquals("ABCPE1234F", RpuFormatSpec.pan("ABCPE1234F", true));
    }

    @Test
    void date_and_amount_formatPerSpec() {
        assertEquals("20072026", RpuFormatSpec.date(LocalDate.of(2026, 7, 20)));
        assertEquals("", RpuFormatSpec.date(null));
        assertEquals("1000.00", RpuFormatSpec.amount(bd("1000")));
        assertEquals("2345.50", RpuFormatSpec.amount(bd("2345.5")));
        assertEquals("0.00", RpuFormatSpec.amount(null));
        assertEquals("2.0000", RpuFormatSpec.rate(bd("2")));
        assertEquals("0.0000", RpuFormatSpec.rate(null));
    }

    @Test
    void sanitiseName_stripsDisallowedCharsAndCollapsesSpaces() {
        assertEquals("M/s. A&B Traders", RpuFormatSpec.sanitiseName("M/s.  A&B \"Traders\"\n"));
        assertTrue(RpuFormatSpec.sanitiseName("x".repeat(100)).length() <= 75);
    }

    @Test
    void remark_higherRateWithoutValidPan_isCodeC() {
        assertEquals("C", RpuFormatSpec.remark(true, false));
        assertEquals("", RpuFormatSpec.remark(true, true));
        assertEquals("", RpuFormatSpec.remark(false, false));
    }
}
