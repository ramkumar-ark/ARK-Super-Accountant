package com.arktech.superaccountant.tds.mapping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.arktech.superaccountant.masters.models.UploadLedgerSnapshot;
import com.arktech.superaccountant.masters.repository.UploadLedgerSnapshotRepository;
import com.arktech.superaccountant.tdsapplicability.TdsApplicabilityService;
import com.arktech.superaccountant.workflow.seam.MastersJobGate;
import com.arktech.superaccountant.workflow.service.CoverageService;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TdsLedgerMappingGateTest {

    @Mock
    private TdsLedgerMappingService mappingService;

    @Mock
    private MastersJobGate mastersJobGate;

    @Mock
    private UploadLedgerSnapshotRepository snapshotRepository;

    @Mock
    private TdsApplicabilityService tdsApplicabilityService;

    @Mock
    private CoverageService coverageService;

    private TdsLedgerMappingGate gate;

    private final UUID org = UUID.randomUUID();

    private final UUID jobId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        gate = new TdsLedgerMappingGate(mappingService, mastersJobGate, snapshotRepository,
                tdsApplicabilityService, coverageService);
        lenient().when(coverageService.coverageStart(org)).thenReturn(LocalDate.of(2026, 4, 1));
        lenient().when(tdsApplicabilityService.anyApplicableFrom(org, LocalDate.of(2026, 4, 1)))
                .thenReturn(true);
        lenient().when(mastersJobGate.latestCompletedMastersJob(org)).thenReturn(Optional.of(jobId));
    }

    private static UploadLedgerSnapshot payableLedger(String name) {
        UploadLedgerSnapshot s = new UploadLedgerSnapshot();
        s.setLedgerName(name);
        s.setParentGroup("Direct Expenses");
        s.setGroupChain("Expenses|Direct Expenses|Duties & Taxes");
        s.setTdsApplicable(false);
        return s;
    }

    private static UploadLedgerSnapshot receivableLedger(String name) {
        UploadLedgerSnapshot s = new UploadLedgerSnapshot();
        s.setLedgerName(name);
        s.setParentGroup("Current Assets");
        s.setGroupChain("Current Assets|Loans & Advances");
        s.setTdsApplicable(false);
        return s;
    }

    private static TdsLedgerMapping saved(String name, TdsLedgerRole role) {
        TdsLedgerMapping m = new TdsLedgerMapping();
        m.setLedgerName(name);
        m.setLedgerKey(name.trim().toLowerCase());
        m.setRole(role);
        return m;
    }

    @Test
    void nonDeductorOrgIsCompleteEvenWithUnmappedPayableCandidates() {
        lenient().when(tdsApplicabilityService.anyApplicableFrom(org, LocalDate.of(2026, 4, 1)))
                .thenReturn(false);
        lenient().when(snapshotRepository.findByUploadJobId(jobId))
                .thenReturn(List.of(payableLedger("TDS Payable 194C")));
        lenient().when(mappingService.mappingsByKey(org)).thenReturn(Map.of());

        assertTrue(gate.isComplete(org));
        assertEquals(Optional.empty(), gate.blockerMessage(org));
    }

    @Test
    void deductorWithoutMastersUploadIsBlocked() {
        when(mastersJobGate.latestCompletedMastersJob(org)).thenReturn(Optional.empty());

        assertFalse(gate.isComplete(org));
        assertEquals(Optional.of("No masters upload: TDS ledgers cannot be mapped"), gate.blockerMessage(org));
    }

    @Test
    void unmappedPayablesBlockWithFirstFiveNames() {
        when(snapshotRepository.findByUploadJobId(jobId)).thenReturn(List.of(
                payableLedger("TDS Payable 194C"),
                payableLedger("TDS Payable 194I"),
                payableLedger("TDS Payable 194J"),
                payableLedger("TDS Payable 194H"),
                payableLedger("TDS Payable 194A"),
                payableLedger("TDS Payable 194B")));
        when(mappingService.mappingsByKey(org)).thenReturn(Map.of());

        assertFalse(gate.isComplete(org));
        assertEquals(Optional.of("6 TDS ledgers not mapped: TDS Payable 194A, TDS Payable 194B, "
                + "TDS Payable 194C, TDS Payable 194H, TDS Payable 194I"), gate.blockerMessage(org));
    }

    @Test
    void allPayablesMappedIsComplete() {
        when(snapshotRepository.findByUploadJobId(jobId)).thenReturn(List.of(
                payableLedger("TDS Payable 194C"),
                payableLedger("TDS Payable 194I")));
        when(mappingService.mappingsByKey(org)).thenReturn(Map.of(
                "tds payable 194c", saved("TDS Payable 194C", TdsLedgerRole.TDS_PAYABLE),
                "tds payable 194i", saved("TDS Payable 194I", TdsLedgerRole.TDS_PAYABLE)));

        assertTrue(gate.isComplete(org));
        assertEquals(Optional.empty(), gate.blockerMessage(org));
    }

    @Test
    void savedNotTdsCandidateStopsBlocking() {
        when(snapshotRepository.findByUploadJobId(jobId)).thenReturn(List.of(payableLedger("Rent")));
        when(mappingService.mappingsByKey(org))
                .thenReturn(Map.of("rent", saved("Rent", TdsLedgerRole.NOT_TDS)));

        assertTrue(gate.isComplete(org));
        assertEquals(Optional.empty(), gate.blockerMessage(org));
    }

    @Test
    void unmappedReceivableOnlyIsComplete() {
        when(snapshotRepository.findByUploadJobId(jobId))
                .thenReturn(List.of(receivableLedger("TDS Receivable 206A")));
        when(mappingService.mappingsByKey(org)).thenReturn(Map.of());

        assertTrue(gate.isComplete(org));
        assertEquals(Optional.empty(), gate.blockerMessage(org));
    }

    @Test
    void deductorWithZeroCandidatesIsComplete() {
        when(snapshotRepository.findByUploadJobId(jobId)).thenReturn(List.of());
        when(mappingService.mappingsByKey(org)).thenReturn(Map.of());

        assertTrue(gate.isComplete(org));
        assertEquals(Optional.empty(), gate.blockerMessage(org));
    }

    @Test
    void requireCompleteThrowsWithBlockerMessage() {
        when(snapshotRepository.findByUploadJobId(jobId))
                .thenReturn(List.of(payableLedger("TDS Payable 194C")));
        when(mappingService.mappingsByKey(org)).thenReturn(Map.of());

        TdsLedgerMappingIncompleteException ex = assertThrows(TdsLedgerMappingIncompleteException.class,
                () -> gate.requireComplete(org));
        assertEquals("1 TDS ledgers not mapped: TDS Payable 194C", ex.getMessage());
    }
}
