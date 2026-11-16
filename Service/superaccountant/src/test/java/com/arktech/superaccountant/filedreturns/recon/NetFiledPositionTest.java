package com.arktech.superaccountant.filedreturns.recon;

import com.arktech.superaccountant.filedreturns.FiledFormType;
import com.arktech.superaccountant.filedreturns.FiledReturnLine;
import com.arktech.superaccountant.filedreturns.FiledReturnSnapshot;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class NetFiledPositionTest {

    private static final LocalDate APRIL_START = LocalDate.of(2026, 4, 1);
    private static final LocalDate APRIL_END = LocalDate.of(2026, 4, 30);

    private FiledReturnSnapshot snapshot(String period, LocalDate start, LocalDate end) {
        FiledReturnSnapshot s = new FiledReturnSnapshot();
        s.setId(UUID.randomUUID());
        s.setFormType(FiledFormType.GSTR1);
        s.setFilingPeriod(period);
        s.setCoverStart(start);
        s.setCoverEnd(end);
        s.setContentHash("hash-" + period);
        return s;
    }

    private FiledReturnLine line(String tableCode, String docType, String ctin, String docNumber,
                                 LocalDate docDate, String rate, String taxable,
                                 String igst, String cgst, String sgst, String cess) {
        FiledReturnLine l = new FiledReturnLine();
        l.setTableCode(tableCode);
        l.setDocType(docType);
        l.setCounterpartyGstin(ctin);
        l.setDocNumber(docNumber);
        l.setDocDate(docDate);
        l.setRate(rate == null ? null : new BigDecimal(rate));
        l.setTaxable(new BigDecimal(taxable));
        l.setIgst(new BigDecimal(igst));
        l.setCgst(new BigDecimal(cgst));
        l.setSgst(new BigDecimal(sgst));
        l.setCess(BigDecimal.ZERO);
        return l;
    }

    @Test
    void amendmentInLaterSnapshotReplacesOriginal() {
        FiledReturnSnapshot april = snapshot("2026-04", APRIL_START, APRIL_END);
        FiledReturnSnapshot may = snapshot("2026-05", LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31));
        FiledReturnLine original = line("B2B", "INV", "24AAAAA0000A1Z5", "INV-7",
                LocalDate.of(2026, 4, 10), "18", "10000", "1800", "0", "0", "0");
        FiledReturnLine amended = line("B2BA", "INV", "24AAAAA0000A1Z5", "INV-7",
                LocalDate.of(2026, 5, 12), "18", "11000", "1980", "0", "0", "0");
        amended.setOriginalDocNumber("INV-7");
        amended.setOriginalDocDate(LocalDate.of(2026, 4, 10));

        NetFiledPosition position = NetFiledPosition.build(List.of(
                new NetFiledPosition.SnapshotWithLines(april, List.of(original)),
                new NetFiledPosition.SnapshotWithLines(may, List.of(amended))),
                APRIL_START, APRIL_END);

        assertThat(position.documents()).hasSize(1);
        NetFiledPosition.FiledDocument doc = position.documents().get(0);
        assertThat(doc.tableCode()).isEqualTo("B2B");
        assertThat(doc.docKey()).isEqualTo("INV7");
        assertThat(doc.amounts().igst()).isEqualByComparingTo("1980");
        assertThat(doc.amounts().taxable()).isEqualByComparingTo("11000");
        assertThat(doc.amendedInPeriod()).isEqualTo("2026-05");
    }

    @Test
    void amendmentOfPreRangeInvoiceIsIgnoredWhenDateOutsideCover() {
        FiledReturnSnapshot may = snapshot("2026-05", LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31));
        FiledReturnLine amended = line("B2BA", "INV", "24AAAAA0000A1Z5", "INV-3",
                LocalDate.of(2026, 5, 12), "18", "11000", "1980", "0", "0", "0");
        amended.setOriginalDocNumber("INV-3");
        amended.setOriginalDocDate(LocalDate.of(2026, 3, 15));

        NetFiledPosition position = NetFiledPosition.build(List.of(
                new NetFiledPosition.SnapshotWithLines(may, List.of(amended))),
                APRIL_START, APRIL_END);

        assertThat(position.documents()).isEmpty();
    }

    @Test
    void b2csaReplacesByOriginalMonth() {
        FiledReturnSnapshot april = snapshot("2026-04", APRIL_START, APRIL_END);
        FiledReturnSnapshot may = snapshot("2026-05", LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31));
        FiledReturnLine b2cs = line("B2CS", null, null, null, null,
                "18", "10000", "0", "900", "900", "0");
        b2cs.setSupplyType("INTRA");
        FiledReturnLine b2csa = line("B2CSA", null, null, null, null,
                "18", "11000", "0", "990", "990", "0");
        b2csa.setSupplyType("INTRA");
        b2csa.setOriginalPeriod("2026-04");

        NetFiledPosition position = NetFiledPosition.build(List.of(
                new NetFiledPosition.SnapshotWithLines(april, List.of(b2cs)),
                new NetFiledPosition.SnapshotWithLines(may, List.of(b2csa))),
                APRIL_START, APRIL_END);

        assertThat(position.b2csTotals()).hasSize(1);
        NetFiledPosition.B2csKey key = new NetFiledPosition.B2csKey(new BigDecimal("18"), "INTRA");
        assertThat(position.b2csTotals().get(key).taxable()).isEqualByComparingTo("11000");
        assertThat(position.b2csTotals().get(key).cgst()).isEqualByComparingTo("990");
        assertThat(position.b2csTotals().get(key).sgst()).isEqualByComparingTo("990");
    }

    @Test
    void sezAndDeemedExportStayInB2b() {
        FiledReturnSnapshot april = snapshot("2026-04", APRIL_START, APRIL_END);
        FiledReturnLine sewp = line("B2B", "INV", "24AAAAA0000A1Z5", "INV-1",
                LocalDate.of(2026, 4, 10), "18", "10000", "1800", "0", "0", "0");
        sewp.setInvoiceType("SEWP");
        FiledReturnLine de = line("B2B", "INV", "24AAAAA0000A1Z5", "INV-2",
                LocalDate.of(2026, 4, 11), "18", "5000", "900", "0", "0", "0");
        de.setInvoiceType("DE");

        NetFiledPosition position = NetFiledPosition.build(List.of(
                new NetFiledPosition.SnapshotWithLines(april, new ArrayList<>(List.of(sewp, de)))),
                APRIL_START, APRIL_END);

        assertThat(position.documents()).hasSize(2);
        assertThat(position.documents()).allSatisfy(d -> assertThat(d.tableCode()).isEqualTo("B2B"));
    }
}
