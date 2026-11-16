package com.arktech.superaccountant.gst.mapping;

import com.arktech.superaccountant.masters.models.ResolveStatus;
import com.arktech.superaccountant.workflow.findings.WorkflowFindingView;
import com.arktech.superaccountant.workflow.models.Workflow;
import com.arktech.superaccountant.workflow.models.WorkflowStage;
import com.arktech.superaccountant.workflow.models.WorkflowType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TaxMappingFindingSourceTest {

    private final TaxLedgerMappingGate gate = mock(TaxLedgerMappingGate.class);
    private final TaxMappingFindingSource source = new TaxMappingFindingSource(gate);

    private Workflow workflow(WorkflowType type, WorkflowStage stage) {
        Workflow w = new Workflow();
        w.setId(UUID.randomUUID());
        w.setOrganizationId(UUID.randomUUID());
        w.setType(type);
        w.setStage(stage);
        return w;
    }

    @Test
    void sourceCodeAndFindingType() {
        assertEquals("TAX_MAPPING", source.sourceCode());
        assertEquals("TAX_LEDGER_UNMAPPED", TaxMappingFindingSource.FINDING_TYPE);
    }

    @Test
    void appliesToActiveGstStagesOnly() {
        assertTrue(source.appliesTo(workflow(WorkflowType.GSTR1, WorkflowStage.DATA_UPLOADED)));
        assertTrue(source.appliesTo(workflow(WorkflowType.GSTR3B, WorkflowStage.STARTED)));
        assertTrue(source.appliesTo(workflow(WorkflowType.GSTR1, WorkflowStage.FINDINGS_REVIEW)));
        assertTrue(source.appliesTo(workflow(WorkflowType.GSTR3B, WorkflowStage.PREPARE)));
        assertFalse(source.appliesTo(workflow(WorkflowType.GSTR1, WorkflowStage.BASELINE)));
        assertFalse(source.appliesTo(workflow(WorkflowType.GSTR3B, WorkflowStage.FINALIZED)));
        assertFalse(source.appliesTo(workflow(WorkflowType.TDS_PAYMENT, WorkflowStage.DATA_UPLOADED)));
    }

    @Test
    void blockingCountIsUnmappedLedgerCount() {
        Workflow w = workflow(WorkflowType.GSTR1, WorkflowStage.DATA_UPLOADED);
        when(gate.unmappedLedgers(w.getOrganizationId()))
                .thenReturn(List.of("Output CGST", "Output SGST", "Output Cess"));
        assertEquals(3, source.blockingCount(w));
        assertEquals(3, source.list(w).size());
    }

    @Test
    void blockingCountIsOneWhenNoMastersUpload() {
        Workflow w = workflow(WorkflowType.GSTR3B, WorkflowStage.DATA_UPLOADED);
        when(gate.blockerMessage(w.getOrganizationId()))
                .thenReturn(Optional.of("No masters upload: tax ledgers cannot be mapped"));
        when(gate.unmappedLedgers(w.getOrganizationId())).thenReturn(List.of());
        assertEquals(1, source.blockingCount(w));
        List<WorkflowFindingView> findings = source.list(w);
        assertEquals(1, findings.size());
        assertTrue(findings.get(0).message().contains("No masters upload"));
    }

    @Test
    void listProducesSyntheticBlockingFindings() {
        Workflow w = workflow(WorkflowType.GSTR1, WorkflowStage.PREPARE);
        when(gate.unmappedLedgers(w.getOrganizationId())).thenReturn(List.of("Output CGST"));
        WorkflowFindingView f = source.list(w).get(0);
        assertEquals(UUID.nameUUIDFromBytes((w.getOrganizationId() + "|output cgst").getBytes()), f.id());
        assertEquals("TAX_MAPPING", f.source());
        assertEquals("TAX_LEDGER_UNMAPPED", f.findingType());
        assertEquals("HIGH", f.severity());
        assertTrue(f.blocking());
        assertEquals("Output CGST", f.ledgerName());
        assertEquals(ResolveStatus.OPEN, f.resolveStatus());
        assertTrue(f.message().contains("Tax ledger 'Output CGST' is not mapped"));
    }

    @Test
    void lockForUpdateIsAlwaysEmpty() {
        Workflow w = workflow(WorkflowType.GSTR1, WorkflowStage.DATA_UPLOADED);
        assertEquals(Optional.empty(), source.lockForUpdate(w, UUID.randomUUID()));
    }
}
