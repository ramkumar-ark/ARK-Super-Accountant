package com.arktech.superaccountant.filedreturns.recon;

import com.arktech.superaccountant.filedreturns.FiledFormType;
import com.arktech.superaccountant.filedreturns.FiledReturnLine;
import com.arktech.superaccountant.filedreturns.FiledReturnSnapshot;
import com.arktech.superaccountant.masters.models.FindingSeverity;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class Gstr3bComparatorTest {

    private static final UUID ORG_ID = UUID.randomUUID();
    private static final UUID WORKFLOW_ID = UUID.randomUUID();

    private static BooksGst books3b(String rowCode, HeadAmounts amounts) {
        return new BooksGst(List.of(), Map.of(rowCode, amounts));
    }

    private FiledReturnLine line(String tableCode, BigDecimal taxable, BigDecimal igst,
                                 BigDecimal cgst, BigDecimal sgst, BigDecimal cess) {
        FiledReturnLine l = new FiledReturnLine();
        l.setSnapshotId(UUID.randomUUID());
        l.setLineNo(1);
        l.setTableCode(tableCode);
        l.setTaxable(taxable);
        l.setIgst(igst);
        l.setCgst(cgst);
        l.setSgst(sgst);
        l.setCess(cess);
        return l;
    }

    private FiledReturnSnapshot snapshot() {
        FiledReturnSnapshot snap = new FiledReturnSnapshot();
        snap.setId(UUID.randomUUID());
        snap.setOrganizationId(ORG_ID);
        snap.setFormType(FiledFormType.GSTR3B);
        snap.setFilingPeriod("2025-04");
        return snap;
    }

    @Test
    void equalWithinToleranceGivesNothing() {
        BooksGst books = books3b("3.1a", new HeadAmounts(
                new BigDecimal("100000"), new BigDecimal("18000.00"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
        List<FiledReturnDiscrepancy> out = Gstr3bComparator.compare(ORG_ID, WORKFLOW_ID, snapshot(),
                List.of(line("3.1a", new BigDecimal("100000"), new BigDecimal("18000.50"),
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO)),
                books);
        assertThat(out).isEmpty();
    }

    @Test
    void headDifferenceIsMaterialWarning() {
        FiledReturnSnapshot snap = snapshot();
        BooksGst books = books3b("3.1a", new HeadAmounts(
                new BigDecimal("100000"), new BigDecimal("17500"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
        List<FiledReturnDiscrepancy> out = Gstr3bComparator.compare(ORG_ID, WORKFLOW_ID, snap,
                List.of(line("3.1a", new BigDecimal("100000"), new BigDecimal("18000"),
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO)),
                books);
        assertThat(out).hasSize(1);
        FiledReturnDiscrepancy d = out.get(0);
        assertThat(d.getKind()).isEqualTo(DiscrepancyKind.VALUE_DIFFERENCE);
        assertThat(d.isMaterial()).isTrue();
        assertThat(d.getSeverity()).isEqualTo(FindingSeverity.WARNING);
        assertThat(d.getMessage()).contains("diff 500.00");
        assertThat(d.getFormType()).isEqualTo(FiledFormType.GSTR3B);
        assertThat(d.getTableCode()).isEqualTo("3.1a");
        assertThat(d.getSnapshotId()).isEqualTo(snap.getId());
        assertThat(d.getWorkflowId()).isEqualTo(WORKFLOW_ID);
        assertThat(d.getOrganizationId()).isEqualTo(ORG_ID);
    }

    @Test
    void taxableOnlyDifferenceIsInfo() {
        BooksGst books = books3b("3.1a", new HeadAmounts(
                new BigDecimal("100000"), new BigDecimal("18000"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
        List<FiledReturnDiscrepancy> out = Gstr3bComparator.compare(ORG_ID, WORKFLOW_ID, snapshot(),
                List.of(line("3.1a", new BigDecimal("100500"), new BigDecimal("18000"),
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO)),
                books);
        assertThat(out).hasSize(1);
        assertThat(out.get(0).getSeverity()).isEqualTo(FindingSeverity.INFO);
        assertThat(out.get(0).isMaterial()).isFalse();
    }

    @Test
    void fourASumsSubRows() {
        BooksGst books = books3b("4A", new HeadAmounts(
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("500"), BigDecimal.ZERO, BigDecimal.ZERO));
        List<FiledReturnDiscrepancy> out = Gstr3bComparator.compare(ORG_ID, WORKFLOW_ID, snapshot(),
                List.of(line("4A1", BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("100"), BigDecimal.ZERO, BigDecimal.ZERO),
                        line("4A5", BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("400"), BigDecimal.ZERO, BigDecimal.ZERO)),
                books);
        assertThat(out).isEmpty();
    }

    @Test
    void uncomparedRowsIgnored() {
        BooksGst books = books3b("4B1", HeadAmounts.ZERO);
        List<FiledReturnDiscrepancy> out = Gstr3bComparator.compare(ORG_ID, WORKFLOW_ID, snapshot(),
                List.of(line("4B1", new BigDecimal("100"), new BigDecimal("18"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO)),
                books);
        assertThat(out).isEmpty();
    }

    @Test
    void findingKeyIsStable() {
        BooksGst books = books3b("3.1a", HeadAmounts.ZERO);
        List<FiledReturnDiscrepancy> out = Gstr3bComparator.compare(ORG_ID, WORKFLOW_ID, snapshot(),
                List.of(line("3.1a", new BigDecimal("100000"), new BigDecimal("18000"),
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO)),
                books);
        assertThat(out.get(0).getFindingKey()).isEqualTo("GSTR3B|3.1a|3.1a|");
    }
}
