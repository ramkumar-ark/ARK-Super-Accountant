package com.arktech.superaccountant.tds.rpu;

import com.arktech.superaccountant.tds.models.TdsChallan;
import com.arktech.superaccountant.tds.models.TdsDeducteeEntry;
import com.arktech.superaccountant.tds.models.TdsDeducteeType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Rpu140GeneratorTest {

    private static final UUID CID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private final Rpu140Generator gen = new Rpu140Generator();

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    private static RpuHeader header() {
        return new RpuHeader(
                "CHEA12345B", "AAACA1234A", "Acme Traders", "12 MG Road, Mumbai", "K",
                "Ravi Kumar", "Director", null, 2026, 1, LocalDate.of(2026, 7, 20), false);
    }

    private static TdsChallan challan(UUID id) {
        TdsChallan c = new TdsChallan();
        c.setId(id);
        c.setBsrCode("0004329");
        c.setDepositDate(LocalDate.of(2026, 7, 5));
        c.setChallanSerialNumber("00042");
        c.setTdsAmount(bd("1000.00"));
        c.setTotalAmount(bd("1000.00"));
        return c;
    }

    private static TdsDeducteeEntry entry(UUID challanId, String section) {
        TdsDeducteeEntry e = new TdsDeducteeEntry();
        e.setId(UUID.randomUUID());
        e.setTdsChallanId(challanId);
        e.setDeducteeName("ABC Contractors");
        e.setDeducteePan("ABCPE1234F");
        e.setPanValid(true);
        e.setDeducteeType(TdsDeducteeType.NON_COMPANY);
        e.setSection(section);
        e.setAmountPaid(bd("100000.00"));
        e.setTdsDeducted(bd("1000.00"));
        e.setTdsDeposited(bd("1000.00"));
        e.setDateOfPayment(LocalDate.of(2026, 6, 15));
        e.setDateOfDeduction(LocalDate.of(2026, 6, 15));
        e.setRateApplied(bd("1.00"));
        return e;
    }

    @Test
    void generatesForm140FileWithBatchAndDeducteeLines() {
        String file = gen.generate(header(), List.of(challan(CID)),
                List.of(entry(CID, "194C"), entry(CID, "194J_B")));

        String[] lines = file.split("\r\n");
        assertEquals(5, lines.length);
        assertTrue(lines[0].contains("^FH^NS1^R^20072026^1^D^CHEA12345B^1^SUPER ACCOUNTANT"));
        assertTrue(lines[1].contains("^BH^1^1^140^"), lines[1]);
        assertTrue(lines[1].contains("^202627^"), "tax year: " + lines[1]);
        assertTrue(lines[1].contains("^202728^"), "assessment year: " + lines[1]);
        // CD: deductee count, mode, BSR, minor head
        String[] cd = lines[2].split("\\^", -1);
        assertEquals("CD", cd[1]);
        assertEquals("2", cd[4]);
        assertEquals("N", cd[5]);
        assertEquals("C", cd[12]);
        assertEquals("0004329", cd[14]);
        assertEquals("200", cd[22]);
        // DD: 4-digit section codes, first deductee
        String[] dd1 = lines[3].split("\\^", -1);
        assertEquals("DD", dd1[1]);
        assertEquals("1023", dd1[14]);   // entry PAN 4th char P => individual/HUF payee
        String[] dd2 = lines[4].split("\\^", -1);
        assertEquals("1027", dd2[14]);
    }

    @Test
    void individualHufContractorGets1023CodeFromPan() {
        TdsDeducteeEntry ind = entry(CID, "194C");
        ind.setDeducteePan("ABCPX1234E");
        String file = gen.generate(header(), List.of(challan(CID)), List.of(ind));

        String[] dd = file.split("\r\n")[3].split("\\^", -1);
        assertEquals("1023", dd[14]);
    }

    @Test
    void hufContractorGets1023CodeFromPan() {
        TdsDeducteeEntry huf = entry(CID, "194C");
        huf.setDeducteePan("ABCHX1234E");
        String file = gen.generate(header(), List.of(challan(CID)), List.of(huf));

        assertEquals("1023", file.split("\r\n")[3].split("\\^", -1)[14]);
    }

    @Test
    void rejects194cEntryWithoutDeterminablePayeeType() {
        TdsDeducteeEntry noPan = entry(CID, "194C");
        noPan.setDeducteePan(null);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> gen.generate(header(), List.of(challan(CID)), List.of(noPan)));
        assertTrue(e.getMessage().contains("194C payee type"));

        TdsDeducteeEntry badPan = entry(CID, "194C");
        badPan.setDeducteePan("TOOSHORT");
        assertThrows(IllegalArgumentException.class,
                () -> gen.generate(header(), List.of(challan(CID)), List.of(badPan)));
    }

    @Test
    void nilChallanGetsQuarterEndDate() {
        TdsChallan nil = new TdsChallan();
        nil.setId(CID);
        nil.setNilChallan(true);
        nil.setTotalAmount(bd("0.00"));

        String file = gen.generate(header(), List.of(nil), List.of());

        String[] lines = file.split("\r\n");
        String[] cd = lines[2].split("\\^", -1);
        assertEquals("Y", cd[5]);
        assertEquals("30062026", cd[18]);
        assertEquals("", cd[13]);
        assertEquals("", cd[22]);
    }

    @Test
    void rejectsEntryWithoutChallanLink() {
        TdsDeducteeEntry e = entry(CID, "194C");
        e.setTdsChallanId(UUID.randomUUID());
        assertThrows(IllegalArgumentException.class,
                () -> gen.generate(header(), List.of(challan(CID)), List.of(e)));
    }

    @Test
    void rejects194mPost2026() {
        assertThrows(IllegalArgumentException.class,
                () -> gen.generate(header(), List.of(challan(CID)),
                        List.of(entry(CID, "194M"))));
    }
}
