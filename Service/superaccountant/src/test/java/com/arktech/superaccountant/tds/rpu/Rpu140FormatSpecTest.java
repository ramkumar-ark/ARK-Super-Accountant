package com.arktech.superaccountant.tds.rpu;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Rpu140FormatSpecTest {

    private static final RpuHeader HEADER = new RpuHeader(
            "MUMA12345A", "AAACA1234A", "Test Deductor Pvt Ltd", "12 MG Road Bangalore Karnataka",
            "K", "Ramesh Kumar", "Director", null,
            2026, 2, LocalDate.of(2026, 10, 5), true);

    @Test
    void fileHeader_hasEighteenFieldsWithNs1AndForm140FileType() {
        List<String> f = Rpu140FormatSpec.fileHeader(new RpuFormatSpec.FhData(
                1, "MUMA12345A", 1, LocalDate.of(2026, 10, 5)));
        assertEquals(18, f.size());
        assertEquals("1", f.get(0));
        assertEquals("FH", f.get(1));
        assertEquals("NS1", f.get(2));
        assertEquals("R", f.get(3));
        assertEquals("05102026", f.get(4));
        assertEquals("1", f.get(5));
        assertEquals("D", f.get(6));
        assertEquals("MUMA12345A", f.get(7));
        assertEquals("1", f.get(8));
        assertEquals("SUPER ACCOUNTANT", f.get(9));
        assertEquals("", f.get(10));
        assertEquals("", f.get(17));
    }

    @Test
    void batchHeader_hasSeventyTwoFieldsWithLiteral140AndTaxYear() {
        List<String> f = Rpu140FormatSpec.batchHeader(new RpuFormatSpec.BhData(
                2, 1, 1, HEADER, new BigDecimal("1000.00")));
        assertEquals(72, f.size());
        assertEquals("2", f.get(0));
        assertEquals("BH", f.get(1));
        assertEquals("1", f.get(2));
        assertEquals("1", f.get(3));
        assertEquals("140", f.get(4));
        assertEquals("MUMA12345A", f.get(12));
        assertEquals("AAACA1234A", f.get(14));
        assertEquals("202728", f.get(15));   // assessment year for FY 2026-27
        assertEquals("202627", f.get(16));   // tax year replaces AY semantics
        assertEquals("Q2", f.get(17));
        assertEquals("Test Deductor Pvt Ltd", f.get(18));
        assertEquals("K", f.get(31));
        assertEquals("1000.00", f.get(46));
        assertEquals("Y", f.get(51));
        assertEquals("91", f.get(28));       // dedutor country code
    }

    @Test
    void challanDetail_hasThirtyFieldsWithFourWaySplit() {
        List<String> f = Rpu140FormatSpec.challanDetail(new Rpu140FormatSpec.CdData(
                3, 1, "1234567", LocalDate.of(2026, 9, 25), "54321",
                new BigDecimal("700.00"), new BigDecimal("50.00"), new BigDecimal("30.00"),
                new BigDecimal("40.00"), new BigDecimal("820.00"), false, 2,
                new BigDecimal("700.00"), new BigDecimal("700.00")));
        assertEquals(30, f.size());
        assertEquals("3", f.get(0));
        assertEquals("CD", f.get(1));
        assertEquals("1", f.get(2));
        assertEquals("2", f.get(4));
        assertEquals("N", f.get(5));
        assertEquals("700.00", f.get(7));    // income tax
        assertEquals("50.00", f.get(8));     // interest
        assertEquals("30.00", f.get(9));     // fee
        assertEquals("40.00", f.get(10));    // others
        assertEquals("820.00", f.get(11));   // total
        assertEquals("C", f.get(12));        // mode challan
        assertEquals("1234567", f.get(14));  // BSR
        assertEquals("54321", f.get(16));
        assertEquals("25092026", f.get(18));
        assertEquals("700.00", f.get(20));   // sum DD25
        assertEquals("700.00", f.get(21));   // sum DD24
        assertEquals("200", f.get(22));      // minor head
        assertEquals("50.00", f.get(23));    // interest allocation
        assertEquals("40.00", f.get(24));    // other allocation
        assertEquals("", f.get(29));
    }

    @Test
    void nilChallan_leavesChallanSpecificFieldsBlank() {
        List<String> f = Rpu140FormatSpec.challanDetail(new Rpu140FormatSpec.CdData(
                3, 1, null, LocalDate.of(2026, 9, 30), null,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                true, 0, BigDecimal.ZERO, BigDecimal.ZERO));
        assertEquals("Y", f.get(5));
        assertEquals("", f.get(12));
        assertEquals("", f.get(14));
        assertEquals("", f.get(16));
        assertEquals("30092026", f.get(18));
    }

    @Test
    void deducteeDetail_hasFortyFiveFieldsInForm140Order() {
        List<String> f = Rpu140FormatSpec.deducteeDetail(new RpuFormatSpec.DdData(
                4, 1, 1, "ABCDE1234F", true, "Vendor One", null, "194C",
                new BigDecimal("50000.00"), new BigDecimal("1000.00"), new BigDecimal("1000.00"),
                LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 10),
                new BigDecimal("2.00"), false));
        assertEquals(45, f.size());
        assertEquals("4", f.get(0));
        assertEquals("DD", f.get(1));
        assertEquals("O", f.get(5));
        assertEquals("ABCDE1234F", f.get(7));
        assertEquals("Vendor One", f.get(8));
        assertEquals("1024", f.get(14));     // 4-digit section code
        assertEquals("Y", f.get(17));        // deposited
        assertEquals("10082026", f.get(18));
        assertEquals("50000.00", f.get(19));
        assertEquals("1000.00", f.get(23));
        assertEquals("1000.00", f.get(24));
        assertEquals("10082026", f.get(26)); // date of deduction
        assertEquals("2.0000", f.get(27));
        assertEquals("", f.get(31));
        assertEquals("", f.get(32));
        assertEquals("", f.get(44));
    }

    @Test
    void deducteeDetail_noPanUsesHigherRateRemarkC() {
        List<String> f = Rpu140FormatSpec.deducteeDetail(new RpuFormatSpec.DdData(
                4, 1, 1, null, false, "Vendor", null, "194A",
                new BigDecimal("50000.00"), new BigDecimal("10000.00"), BigDecimal.ZERO,
                LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 10),
                new BigDecimal("20.00"), true));
        assertEquals("PANNOTAVBL", f.get(7));
        assertEquals("C", f.get(31));        // s.397(2) higher rate reason
        assertEquals("N", f.get(17));
    }

    @Test
    void form140SectionCode_mapsInternalsToFourDigitCodes() {
        assertEquals("1022", Rpu140FormatSpec.sectionCode("194A"));
        assertEquals("1006", Rpu140FormatSpec.sectionCode("194H"));
        assertEquals("1009", Rpu140FormatSpec.sectionCode("194I"));
        assertEquals("1026", Rpu140FormatSpec.sectionCode("194J_A"));
        assertEquals("1027", Rpu140FormatSpec.sectionCode("194J_B"));
        assertEquals("1031", Rpu140FormatSpec.sectionCode("194Q"));

        // 194C requires a deductee PAN: ind/HUF (4th char P/H) -> 1023, others -> 1024
        assertEquals("1023", Rpu140FormatSpec.sectionCode("194C", "ABCP12345F"));
        assertEquals("1023", Rpu140FormatSpec.sectionCode("194C", "ABCH12345F"));
        assertEquals("1024", Rpu140FormatSpec.sectionCode("194C", "ABCC12345F"));
        assertEquals("1024", Rpu140FormatSpec.sectionCode("194C", "ABCF12345F"));
        assertEquals("1026", Rpu140FormatSpec.sectionCode("194J_A", null));

        IllegalArgumentException indeterminate = assertThrows(IllegalArgumentException.class,
                () -> Rpu140FormatSpec.sectionCode("194C", null));
        assertTrue(indeterminate.getMessage().contains("194C payee type"));
        assertThrows(IllegalArgumentException.class,
                () -> Rpu140FormatSpec.sectionCode("194C", "BADPAN12"));
    }

    @Test
    void form140SectionCode_rejects194mWithForm141Guidance() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> Rpu140FormatSpec.sectionCode("194M"));
        assertTrue(e.getMessage().contains("Form 141"));
        assertThrows(IllegalArgumentException.class, () -> Rpu140FormatSpec.sectionCode("9999"));
    }
}
