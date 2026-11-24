package com.arktech.superaccountant.tds.mapping;

import com.arktech.superaccountant.workflow.models.Workflow;
import com.arktech.superaccountant.workflow.models.WorkflowStage;
import com.arktech.superaccountant.workflow.models.WorkflowType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TdsMappingFindingSourceTest {

    private final UUID org = UUID.randomUUID();
    private final TdsLedgerMappingGate gate = mock(TdsLedgerMappingGate.class);
    private final TdsMappingFindingSource source = new TdsMappingFindingSource(gate);

    private Workflow workflow() {
        Workflow w = new Workflow();
        w.setType(WorkflowType.TDS_PAYMENT);
        w.setStage(WorkflowStage.DATA_UPLOADED);
        w.setOrganizationId(org);
        return w;
    }

    @Test
    void nonDeductorOrganizationHasNoFindings() {
        when(gate.blockerMessage(org)).thenReturn(Optional.empty());
        when(gate.unmappedPayableLedgers(org)).thenReturn(List.of("TDS Payable 194C"));

        assertEquals(0, source.blockingCount(workflow()));
        assertTrue(source.list(workflow()).isEmpty());
    }

    @Test
    void deductorWithUnmappedPayableHasOneFindingPerLedger() {
        when(gate.blockerMessage(org)).thenReturn(Optional.of("1 TDS ledgers not mapped: TDS Payable 194C"));
        when(gate.unmappedPayableLedgers(org)).thenReturn(List.of("TDS Payable 194C"));

        assertEquals(1, source.blockingCount(workflow()));
        assertEquals("TDS Payable 194C", source.list(workflow()).get(0).ledgerName());
    }
}
