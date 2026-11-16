package com.arktech.superaccountant.filedreturns.recon;

import com.arktech.superaccountant.filedreturns.FiledFormType;
import com.arktech.superaccountant.filedreturns.FiledReturnLine;
import com.arktech.superaccountant.filedreturns.FiledReturnSnapshot;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class Gstr1ReconcilerTest {

    private static final UUID ORG_ID = UUID.randomUUID();
    private static final UUID WORKFLOW_ID = UUID.randomUUID();
    private static final String GSTIN_A = "24AAAAA0000A1Z5";
    private static final String GSTIN_B = "27AAAAA0000A1Z5";
    private static final LocalDate APRIL_START = LocalDate.of(2026, 4, 1);
    private static final LocalDate APRIL_END = LocalDate.of(2026, 4, 30);

    private FiledReturnSnapshot snapshot() {
        FiledReturnSnapshot s = new FiledReturnSnapshot();
        s.setId(UUID.randomUUID());
        s.setFormType(FiledFormType.GSTR1);
        s.setFilingPeriod("2026-04");
        s.setCoverStart(APRIL_START);
        s.setCoverEnd(APRIL_END);
        s.setContentHash("hash-1");
        return s;
    }

    private FiledReturnLine filedLine(String tableCode, String docType, String ctin, String docNumber,
                                      LocalDate docDate, String rate, String taxable,
                                      String igst, String cgst, String sgst) {
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

    private NetFiledPosition.FiledDocument filedDoc(String tableCode, DocKind kind, String ctin, String docNumber,
                                                    LocalDate docDate, HeadAmounts amounts,
                                                    Map<BigDecimal, BigDecimal> taxableByRate) {
        String key = DocKeys.normalise(docNumber);
        return new NetFiledPosition.FiledDocument(tableCode, kind, ctin, docNumber, key, DocKeys.loose(docNumber),
                docDate, amounts, taxableByRate, UUID.randomUUID(), null);
    }

    private BooksDocument booksDoc(String voucherNumber, String docKey, String looseKey, LocalDate date,
                                   String partyGstin, DocKind kind, HeadAmounts amounts,
                                   Map<BigDecimal, BigDecimal> taxableByRate) {
        return new BooksDocument(UUID.randomUUID(), voucherNumber, docKey, looseKey, date, partyGstin,
                kind, false, amounts, taxableByRate);
    }

    private List<FiledReturnDiscrepancy> reconcile(NetFiledPosition filed, BooksGst books) {
        return Gstr1Reconciler.reconcile(ORG_ID, WORKFLOW_ID, filed, books, APRIL_START, APRIL_END);
    }

    @Test
    void exactMatchNoDifferenceGivesNothing() {
        NetFiledPosition filed = new NetFiledPosition(
                List.of(filedDoc("B2B", DocKind.INV, GSTIN_A, "INV-1", LocalDate.of(2026, 4, 10),
                        new HeadAmounts(new BigDecimal("10000"), new BigDecimal("1800"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO),
                        Map.of(new BigDecimal("18"), new BigDecimal("10000")))),
                Map.of(), Map.of());
        BooksGst books = new BooksGst(List.of(booksDoc("INV-1", "INV1", "INV1", LocalDate.of(2026, 4, 10), GSTIN_A,
                DocKind.INV, new HeadAmounts(new BigDecimal("10000"), new BigDecimal("1800"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO),
                Map.of(new BigDecimal("18"), new BigDecimal("10000")))), Map.of());

        assertThat(reconcile(filed, books)).isEmpty();
    }

    @Test
    void headDifferenceIsMaterial() {
        NetFiledPosition filed = new NetFiledPosition(
                List.of(filedDoc("B2B", DocKind.INV, GSTIN_A, "INV-1", LocalDate.of(2026, 4, 10),
                        new HeadAmounts(new BigDecimal("10000"), new BigDecimal("1800"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO),
                        Map.of(new BigDecimal("18"), new BigDecimal("10000")))),
                Map.of(), Map.of());
        BooksGst books = new BooksGst(List.of(booksDoc("INV-1", "INV1", "INV1", LocalDate.of(2026, 4, 10), GSTIN_A,
                DocKind.INV, new HeadAmounts(new BigDecimal("10000"), new BigDecimal("1700"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO),
                Map.of(new BigDecimal("18"), new BigDecimal("10000")))), Map.of());

        List<FiledReturnDiscrepancy> result = reconcile(filed, books);
        assertThat(result).hasSize(1);
        FiledReturnDiscrepancy d = result.get(0);
        assertThat(d.getKind()).isEqualTo(DiscrepancyKind.VALUE_DIFFERENCE);
        assertThat(d.isMaterial()).isTrue();
        assertThat(d.getGrain()).isEqualTo(DiscrepancyGrain.DOCUMENT);
    }

    @Test
    void looseKeyMatchesSeparatorAndLeadingZeroVariants() {
        NetFiledPosition filed = new NetFiledPosition(
                List.of(filedDoc("B2B", DocKind.INV, GSTIN_A, "INV-1", LocalDate.of(2026, 4, 10),
                        new HeadAmounts(new BigDecimal("10000"), new BigDecimal("1800"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO),
                        Map.of(new BigDecimal("18"), new BigDecimal("10000")))),
                Map.of(), Map.of());
        BooksGst books = new BooksGst(List.of(booksDoc("INV/001", "INV001", "INV1", LocalDate.of(2026, 4, 10), GSTIN_A,
                DocKind.INV, new HeadAmounts(new BigDecimal("10000"), new BigDecimal("1800"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO),
                Map.of(new BigDecimal("18"), new BigDecimal("10000")))), Map.of());

        assertThat(reconcile(filed, books)).isEmpty();

        BooksGst booksDiff = new BooksGst(List.of(booksDoc("INV/001", "INV001", "INV1", LocalDate.of(2026, 4, 10), GSTIN_A,
                DocKind.INV, new HeadAmounts(new BigDecimal("10000"), new BigDecimal("1500"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO),
                Map.of(new BigDecimal("18"), new BigDecimal("10000")))), Map.of());
        List<FiledReturnDiscrepancy> result = reconcile(filed, booksDiff);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getFuzzyMatch()).isTrue();
    }

    @Test
    void gstinDifferenceDetected() {
        NetFiledPosition filed = new NetFiledPosition(
                List.of(filedDoc("B2B", DocKind.INV, GSTIN_A, "INV-1", LocalDate.of(2026, 4, 10),
                        new HeadAmounts(new BigDecimal("10000"), new BigDecimal("1800"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO),
                        Map.of(new BigDecimal("18"), new BigDecimal("10000")))),
                Map.of(), Map.of());
        BooksGst books = new BooksGst(List.of(booksDoc("INV-1", "INV1", "INV1", LocalDate.of(2026, 4, 10), GSTIN_B,
                DocKind.INV, new HeadAmounts(new BigDecimal("10000"), new BigDecimal("1800"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO),
                Map.of(new BigDecimal("18"), new BigDecimal("10000")))), Map.of());

        List<FiledReturnDiscrepancy> result = reconcile(filed, books);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getKind()).isEqualTo(DiscrepancyKind.GSTIN_DIFFERENCE);
        assertThat(result.get(0).isMaterial()).isTrue();
    }

    @Test
    void portalOnlyAndBooksOnlyAreMaterial() {
        NetFiledPosition filed = new NetFiledPosition(
                List.of(filedDoc("B2B", DocKind.INV, GSTIN_A, "INV-P", LocalDate.of(2026, 4, 10),
                        new HeadAmounts(new BigDecimal("10000"), new BigDecimal("1800"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO),
                        Map.of(new BigDecimal("18"), new BigDecimal("10000")))),
                Map.of(), Map.of());
        BooksGst books = new BooksGst(List.of(booksDoc("INV-B", "INVB", "INVB", LocalDate.of(2026, 4, 10), GSTIN_A,
                DocKind.INV, new HeadAmounts(new BigDecimal("10000"), new BigDecimal("1800"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO),
                Map.of(new BigDecimal("18"), new BigDecimal("10000")))), Map.of());

        List<FiledReturnDiscrepancy> result = reconcile(filed, books);
        assertThat(result).hasSize(2);
        assertThat(result).extracting(FiledReturnDiscrepancy::getKind)
                .containsExactlyInAnyOrder(DiscrepancyKind.PORTAL_ONLY, DiscrepancyKind.BOOKS_ONLY);
        assertThat(result).allSatisfy(d -> assertThat(d.isMaterial()).isTrue());
    }

    @Test
    void dateShiftAndRateSplitAreInfo() {
        NetFiledPosition filed = new NetFiledPosition(
                List.of(filedDoc("B2B", DocKind.INV, GSTIN_A, "INV-1", LocalDate.of(2026, 4, 10),
                        new HeadAmounts(new BigDecimal("10000"), new BigDecimal("1800"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO),
                        Map.of(new BigDecimal("18"), new BigDecimal("10000")))),
                Map.of(), Map.of());
        BooksGst books = new BooksGst(List.of(booksDoc("INV-1", "INV1", "INV1", LocalDate.of(2026, 4, 12), GSTIN_A,
                DocKind.INV, new HeadAmounts(new BigDecimal("10000"), new BigDecimal("1800"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO),
                Map.of(new BigDecimal("12"), new BigDecimal("10000")))), Map.of());

        List<FiledReturnDiscrepancy> result = reconcile(filed, books);
        assertThat(result).hasSize(2);
        assertThat(result).extracting(FiledReturnDiscrepancy::getKind)
                .containsExactlyInAnyOrder(DiscrepancyKind.DATE_SHIFT, DiscrepancyKind.RATE_SPLIT);
        assertThat(result).allSatisfy(d -> {
            assertThat(d.isMaterial()).isFalse();
            assertThat(d.getSeverity().name()).isEqualTo("INFO");
        });
    }

    @Test
    void unregisteredSmallSalesGoToB2csTotals() {
        BooksGst books = new BooksGst(List.of(
                booksDoc("INV-S1", "INVS1", "INVS1", LocalDate.of(2026, 4, 10), null,
                        DocKind.INV, new HeadAmounts(new BigDecimal("5000"), BigDecimal.ZERO, new BigDecimal("450"), new BigDecimal("450"), BigDecimal.ZERO),
                        Map.of(new BigDecimal("18"), new BigDecimal("5000"))),
                booksDoc("INV-S2", "INVS2", "INVS2", LocalDate.of(2026, 4, 11), null,
                        DocKind.INV, new HeadAmounts(new BigDecimal("3000"), BigDecimal.ZERO, new BigDecimal("270"), new BigDecimal("270"), BigDecimal.ZERO),
                        Map.of(new BigDecimal("18"), new BigDecimal("3000")))), Map.of());

        FiledReturnSnapshot s = snapshot();
        FiledReturnLine b2cs = filedLine("B2CS", null, null, null, null, "18", "8000", "0", "720", "720");
        b2cs.setSupplyType("INTRA");
        NetFiledPosition filedMatching = NetFiledPosition.build(
                List.of(new NetFiledPosition.SnapshotWithLines(s, List.of(b2cs))), APRIL_START, APRIL_END);
        assertThat(reconcile(filedMatching, books)).isEmpty();

        FiledReturnLine b2csShort = filedLine("B2CS", null, null, null, null, "18", "7000", "0", "630", "630");
        b2csShort.setSupplyType("INTRA");
        NetFiledPosition filedShort = NetFiledPosition.build(
                List.of(new NetFiledPosition.SnapshotWithLines(snapshot(), List.of(b2csShort))), APRIL_START, APRIL_END);
        List<FiledReturnDiscrepancy> result = reconcile(filedShort, books);
        assertThat(result).hasSize(1);
        FiledReturnDiscrepancy d = result.get(0);
        assertThat(d.getKind()).isEqualTo(DiscrepancyKind.VALUE_DIFFERENCE);
        assertThat(d.getGrain()).isEqualTo(DiscrepancyGrain.SUMMARY);
        assertThat(d.isMaterial()).isTrue();
        assertThat(d.getFindingKey()).isEqualTo("GSTR1|B2CS|18-INTRA|");
    }

    @Test
    void largeInterStateUnregisteredMatchesB2cl() {
        BooksGst books = new BooksGst(List.of(booksDoc("INV-L", "INVL", "INVL", LocalDate.of(2026, 4, 10), null,
                DocKind.INV, new HeadAmounts(new BigDecimal("150000"), new BigDecimal("27000"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO),
                Map.of(new BigDecimal("18"), new BigDecimal("150000")))), Map.of());

        NetFiledPosition filed = new NetFiledPosition(
                List.of(filedDoc("B2CL", DocKind.INV, null, "INV-L", LocalDate.of(2026, 4, 10),
                        new HeadAmounts(new BigDecimal("150000"), new BigDecimal("27000"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO),
                        Map.of(new BigDecimal("18"), new BigDecimal("150000")))),
                Map.of(), Map.of());

        assertThat(reconcile(filed, books)).isEmpty();
    }
}
