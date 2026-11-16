package com.arktech.superaccountant.filedreturns.recon;

import com.arktech.superaccountant.filedreturns.recon.DiscrepancyGrain;
import com.arktech.superaccountant.filedreturns.recon.DiscrepancyKind;
import com.arktech.superaccountant.filedreturns.recon.FiledReturnDiscrepancy;
import com.arktech.superaccountant.masters.models.FindingSeverity;
import com.arktech.superaccountant.masters.models.ResolveStatus;
import com.arktech.superaccountant.workflow.findings.WorkflowFindingView;
import com.arktech.superaccountant.workflow.models.Workflow;
import com.arktech.superaccountant.workflow.models.WorkflowStage;
import com.arktech.superaccountant.workflow.models.WorkflowType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FiledReturnDiscrepancySourceTest {

    private final FiledReturnDiscrepancySource source = new FiledReturnDiscrepancySource(null);

    private Workflow row(WorkflowType type, WorkflowStage stage, boolean active) {
        Workflow w = new Workflow();
        w.setId(UUID.randomUUID());
        w.setOrganizationId(UUID.randomUUID());
        w.setType(type);
        w.setStage(stage);
        w.setActive(active);
        return w;
    }

    @Test
    void appliesOnlyToActiveGstBaselineRows() {
        assertTrue(source.appliesTo(row(WorkflowType.GSTR1, WorkflowStage.BASELINE, true)));
        assertTrue(source.appliesTo(row(WorkflowType.GSTR3B, WorkflowStage.BASELINE, true)));
        assertFalse(source.appliesTo(row(WorkflowType.GSTR1, WorkflowStage.DATA_UPLOADED, true)));
        assertFalse(source.appliesTo(row(WorkflowType.GSTR1, WorkflowStage.BASELINE, false)));
        assertFalse(source.appliesTo(row(WorkflowType.TDS_PAYMENT, WorkflowStage.BASELINE, true)));
    }

    @Test
    void neverBlocks() {
        assertEquals(0, source.blockingCount(row(WorkflowType.GSTR1, WorkflowStage.BASELINE, true)));
        assertEquals(0, source.blockingCount(row(WorkflowType.GSTR3B, WorkflowStage.BASELINE, true)));
    }

    @Test
    void sourceListFiltersNonApplyingRows() {
        assertTrue(source.list(row(WorkflowType.TDS_PAYMENT, WorkflowStage.BASELINE, true)).isEmpty());
        assertTrue(source.list(row(WorkflowType.GSTR1, WorkflowStage.FINDINGS_REVIEW, true)).isEmpty());
    }

    @Test
    void viewCarriesFiledAndBooksAmounts() {
        FiledReturnDiscrepancy f = new FiledReturnDiscrepancy();
        f.setId(UUID.randomUUID());
        f.setWorkflowId(UUID.randomUUID());
        f.setFormType(com.arktech.superaccountant.filedreturns.FiledFormType.GSTR1);
        f.setTableCode("B2B");
        f.setKind(DiscrepancyKind.VALUE_DIFFERENCE);
        f.setGrain(DiscrepancyGrain.DOCUMENT);
        f.setMaterial(true);
        f.setSeverity(FindingSeverity.WARNING);
        f.setResolveStatus(ResolveStatus.OPEN);
        f.setMessage("B2B INV-7: filed IGST 1700.00 vs books 1800.00 (diff 100.00)");
        f.setDocNumber("INV-7");
        f.setDocDate(LocalDate.of(2026, 4, 7));
        f.setCounterpartyGstin("27AAAAA0000A1Z5");
        f.setVoucherNumber("INV-7");
        f.setFiledTaxable(new BigDecimal("10000"));
        f.setFiledIgst(new BigDecimal("1700"));
        f.setBooksTaxable(new BigDecimal("10000"));
        f.setBooksIgst(new BigDecimal("1800"));

        WorkflowFindingView v = source.view(f);

        assertEquals("FILED_RETURN", v.source());
        assertEquals("VALUE_DIFFERENCE", v.findingType());
        assertEquals("WARNING", v.severity());
        assertFalse(v.blocking());
        assertEquals("INV-7", v.voucherNumber());
        assertEquals("27AAAAA0000A1Z5", v.ledgerName());
        assertEquals("T 10000.00 | I 1700.00 | C 0.00 | S 0.00 | Cess 0.00", v.expectedValue());
        assertEquals("T 10000.00 | I 1800.00 | C 0.00 | S 0.00 | Cess 0.00", v.actualValue());
        assertEquals(ResolveStatus.OPEN, v.resolveStatus());
        assertTrue(v.message().startsWith("[GSTR-1 B2B] "), v.message());
    }

    @Test
    void viewFallsBackToFiledVoucherNumberWhenBooksAbsent() {
        FiledReturnDiscrepancy f = new FiledReturnDiscrepancy();
        f.setId(UUID.randomUUID());
        f.setFormType(com.arktech.superaccountant.filedreturns.FiledFormType.GSTR1);
        f.setTableCode("B2B");
        f.setKind(DiscrepancyKind.PORTAL_ONLY);
        f.setMaterial(true);
        f.setResolveStatus(ResolveStatus.OPEN);
        f.setDocNumber("INV-99");
        f.setDocDate(LocalDate.of(2026, 4, 9));

        WorkflowFindingView v = source.view(f);
        assertEquals("INV-99", v.voucherNumber());
        assertEquals(LocalDate.of(2026, 4, 9), v.voucherDate());
        assertEquals("T 0.00 | I 0.00 | C 0.00 | S 0.00 | Cess 0.00", v.expectedValue());
    }

    @Test
    void viewUsesGstr3bPrefix() {
        FiledReturnDiscrepancy f = new FiledReturnDiscrepancy();
        f.setFormType(com.arktech.superaccountant.filedreturns.FiledFormType.GSTR3B);
        f.setTableCode("3.1a");
        f.setKind(DiscrepancyKind.VALUE_DIFFERENCE);
        f.setGrain(DiscrepancyGrain.SUMMARY);
        f.setResolveStatus(ResolveStatus.OPEN);
        WorkflowFindingView v = source.view(f);
        assertTrue(v.message().startsWith("[GSTR-3B 3.1a] "), v.message());
    }

    @Test
    void findingsAreNotBlockingEvenWhenMaterial() {
        List<FiledReturnDiscrepancy> findings = List.of(material(true), material(false));
        findings.forEach(f -> assertFalse(source.view(f).blocking()));
    }

    private FiledReturnDiscrepancy material(boolean isMaterial) {
        FiledReturnDiscrepancy f = new FiledReturnDiscrepancy();
        f.setFormType(com.arktech.superaccountant.filedreturns.FiledFormType.GSTR1);
        f.setTableCode("B2B");
        f.setKind(isMaterial ? DiscrepancyKind.VALUE_DIFFERENCE : DiscrepancyKind.DATE_SHIFT);
        f.setMaterial(isMaterial);
        f.setResolveStatus(ResolveStatus.OPEN);
        return f;
    }
}
