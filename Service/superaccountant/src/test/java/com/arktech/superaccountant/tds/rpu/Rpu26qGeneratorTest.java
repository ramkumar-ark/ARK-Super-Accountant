package com.arktech.superaccountant.tds.rpu;

import com.arktech.superaccountant.tds.models.TdsChallan;
import com.arktech.superaccountant.tds.models.TdsDeducteeEntry;
import com.arktech.superaccountant.tds.models.TdsDeducteeType;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Rpu26qGeneratorTest {

    private static final UUID FIXED_CID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private final Rpu26qGenerator gen = new Rpu26qGenerator();

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    private static RpuHeader header() {
        return new RpuHeader(
                "CHEA12345B", "AAACA1234A", "Acme Traders", "12 MG Road, Mumbai", "K",
                "Ravi Kumar", "Director", null, 2026, 1, LocalDate.of(2026, 7, 20));
    }

    private static TdsChallan challan(UUID id) {
        return challan(id, "00042", LocalDate.of(2026, 7, 5));
    }

    private static TdsChallan challan(UUID id, String serial, LocalDate depositDate) {
        TdsChallan c = new TdsChallan();
        c.setId(id);
        c.setBsrCode("0004329");
        c.setDepositDate(depositDate);
        c.setChallanSerialNumber(serial);
        c.setTdsAmount(bd("1000.00"));
        c.setTotalAmount(bd("1000.00"));
        return c;
    }

    private static TdsDeducteeEntry entry(UUID challanId) {
        return entry(challanId, "ABC Contractors", LocalDate.of(2026, 6, 15));
    }

    private static TdsDeducteeEntry entry(UUID challanId, String name, LocalDate paymentDate) {
        TdsDeducteeEntry e = new TdsDeducteeEntry();
        e.setId(UUID.randomUUID());
        e.setTdsChallanId(challanId);
        e.setDeducteeName(name);
        e.setDeducteePan("ABCPE1234F");
        e.setPanValid(true);
        e.setDeducteeType(TdsDeducteeType.NON_COMPANY);
        e.setSection("194C");
        e.setAmountPaid(bd("100000.00"));
        e.setTdsDeducted(bd("1000.00"));
        e.setTdsDeposited(bd("1000.00"));
        e.setDateOfPayment(paymentDate);
        e.setDateOfDeduction(paymentDate);
        e.setRateApplied(bd("1.00"));
        return e;
    }

    private static String readResource(String path) throws Exception {
        try (InputStream in = Rpu26qGeneratorTest.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Missing test resource " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void generate_singleChallanSingleDeductee_structure() {
        UUID cid = UUID.randomUUID();
        String out = gen.generate(header(), List.of(challan(cid)), List.of(entry(cid)));

        String[] lines = out.split("\r\n", -1);
        assertEquals(5, lines.length);
        assertTrue(lines[0].startsWith("1^FH^"));
        assertTrue(lines[1].startsWith("2^BH^"));
        assertTrue(lines[2].startsWith("3^CD^"));
        assertTrue(lines[3].startsWith("4^DD^"));
        assertTrue(lines[3].contains("ABCPE1234F"));
        assertTrue(lines[3].contains(RpuFormatSpec.sectionCode("194C")));
    }

    @Test
    void generate_ordersChallansByDepositDateAndEntriesByPaymentDate() {
        UUID later = UUID.randomUUID();
        UUID earlier = UUID.randomUUID();
        TdsChallan challanLater = challan(later, "00043", LocalDate.of(2026, 7, 6));
        TdsChallan challanEarlier = challan(earlier, "00042", LocalDate.of(2026, 7, 5));
        TdsDeducteeEntry second = entry(later, "Zeta Traders", LocalDate.of(2026, 6, 20));
        TdsDeducteeEntry first = entry(later, "Alpha Traders", LocalDate.of(2026, 6, 10));

        String out = gen.generate(header(), List.of(challanLater, challanEarlier), List.of(second, first));

        String[] lines = out.split("\r\n", -1);
        assertEquals("00042", lines[2].split("\\^", -1)[11]);
        List<String> dd = List.of(lines).stream().filter(l -> l.contains("^DD^")).toList();
        assertEquals(2, dd.size());
        assertEquals("1", dd.get(0).split("\\^", -1)[4]);
        assertTrue(dd.get(0).contains("Alpha Traders"));
        assertEquals("2", dd.get(1).split("\\^", -1)[4]);
        assertTrue(dd.get(1).contains("Zeta Traders"));
    }

    @Test
    void generate_missingPan_emitsPanNotAvailableAndHigherRateRemark() {
        UUID cid = UUID.randomUUID();
        TdsDeducteeEntry e = entry(cid);
        e.setDeducteePan(null);
        e.setPanValid(false);
        e.setHigherRateApplied(true);
        e.setRateApplied(bd("20.00"));

        String out = gen.generate(header(), List.of(challan(cid)), List.of(e));

        String dd = out.lines().filter(l -> l.contains("^DD^")).findFirst().orElseThrow();
        String[] f = dd.split("\\^", -1);
        assertEquals("PANNOTAVBL", f[9]);
        assertEquals("C", f[29]);
        assertEquals("20.0000", f[25]);
    }

    @Test
    void generate_nilChallan_writesCdWithZeroDeductees() {
        UUID cid = UUID.randomUUID();
        TdsChallan nil = challan(cid);
        nil.setNilChallan(true);
        nil.setTdsAmount(null);
        nil.setTotalAmount(null);

        String out = gen.generate(header(), List.of(nil), List.of());

        String[] lines = out.split("\r\n", -1);
        assertEquals(4, lines.length);
        assertFalse(out.contains("^DD^"));
        String[] cd = lines[2].split("\\^", -1);
        assertEquals("Y", cd[5]);
        assertEquals("0", cd[4]);
    }

    @Test
    void generate_sanitisesNamesToAllowedCharset() {
        UUID cid = UUID.randomUUID();
        TdsDeducteeEntry e = entry(cid);
        e.setDeducteeName("M/s.  A&B \"Traders\"");

        String out = gen.generate(header(), List.of(challan(cid)), List.of(e));

        String dd = out.lines().filter(l -> l.contains("^DD^")).findFirst().orElseThrow();
        assertEquals(RpuFormatSpec.sanitiseName("M/s.  A&B \"Traders\""), dd.split("\\^", -1)[12]);
    }

    @Test
    void generate_entryWithUnknownChallan_throws() {
        UUID cid = UUID.randomUUID();
        TdsDeducteeEntry orphan = entry(UUID.randomUUID());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> gen.generate(header(), List.of(challan(cid)), List.of(orphan)));
        assertTrue(ex.getMessage().contains("unknown challan"));
    }

    @Test
    void generate_revisedReturn_setsOriginalReceiptInBatchHeader() {
        UUID cid = UUID.randomUUID();
        RpuHeader revised = new RpuHeader(
                "CHEA12345B", "AAACA1234A", "Acme Traders", "12 MG Road, Mumbai", "K",
                "Ravi Kumar", "Director", "123456789012345", 2026, 1, LocalDate.of(2026, 7, 20));

        String out = gen.generate(revised, List.of(challan(cid)), List.of(entry(cid)));

        String[] bh = out.split("\r\n", -1)[1].split("\\^", -1);
        assertEquals("123456789012345", bh[8]);
        assertEquals("Y", bh[51]);
    }

    @Test
    void generate_matchesGoldenFile() throws Exception {
        String expected = readResource("/tds/rpu/golden-26q-regular.txt");
        String actual = gen.generate(header(), List.of(challan(FIXED_CID)), List.of(entry(FIXED_CID)));
        assertEquals(expected, actual);
    }
}
