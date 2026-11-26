package com.arktech.superaccountant.tds.mapping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.arktech.superaccountant.masters.models.UploadLedgerSnapshot;
import com.arktech.superaccountant.masters.repository.UploadLedgerSnapshotRepository;
import com.arktech.superaccountant.tds.mapping.payload.SaveTdsLedgerMappingRequest;
import com.arktech.superaccountant.tds.mapping.payload.TdsLedgerMappingView;
import com.arktech.superaccountant.workflow.seam.MastersJobGate;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class TdsLedgerMappingServiceTest {

    @Mock
    private TdsLedgerMappingRepository repository;

    @Mock
    private MastersJobGate mastersJobGate;

    @Mock
    private UploadLedgerSnapshotRepository snapshotRepository;

    @Mock
    private com.arktech.superaccountant.statutory.repository.StatutorySectionRepository statutorySectionRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private TdsLedgerMappingGate gate;

    private TdsLedgerMappingService service;

    private final UUID org = UUID.randomUUID();

    private final UUID jobId = UUID.randomUUID();

    private static final List<String> SECTION_CODES = List.of("194C", "194I", "194J");

    @BeforeEach
    void setUp() {
        service = new TdsLedgerMappingService(repository, mastersJobGate, snapshotRepository,
                statutorySectionRepository, eventPublisher);
        lenient().when(mastersJobGate.latestCompletedMastersJob(org)).thenReturn(Optional.of(jobId));
        lenient().when(statutorySectionRepository.findByStatuteOrderBySortOrderAscCodeAsc(
                com.arktech.superaccountant.statutory.models.Statute.INCOME_TAX))
                .thenReturn(sections(SECTION_CODES));
        lenient().when(repository.findByOrganizationId(org)).thenReturn(List.of());
        lenient().when(repository.findByOrganizationIdAndLedgerKey(any(), any())).thenReturn(Optional.empty());
        lenient().when(gate.isComplete(org)).thenReturn(false);
        lenient().when(gate.blockerMessage(org)).thenReturn(Optional.of("1 TDS ledgers not mapped: Rent"));
    }

    private static List<com.arktech.superaccountant.statutory.models.StatutorySection> sections(List<String> codes) {
        return codes.stream().map(code -> {
            com.arktech.superaccountant.statutory.models.StatutorySection section =
                    new com.arktech.superaccountant.statutory.models.StatutorySection();
            section.setStatute(com.arktech.superaccountant.statutory.models.Statute.INCOME_TAX);
            section.setCode(code);
            section.setActive(true);
            return section;
        }).toList();
    }

    private static UploadLedgerSnapshot snapshot(String name, String parentGroup, String groupChain,
            Boolean tdsApplicable) {
        UploadLedgerSnapshot s = new UploadLedgerSnapshot();
        s.setLedgerName(name);
        s.setParentGroup(parentGroup);
        s.setGroupChain(groupChain);
        s.setTdsApplicable(tdsApplicable);
        return s;
    }

    /** Narayanan-shaped masters: a saved payable, an unsaved receivable, and an ambiguous payable. */
    private void stubMasters() {
        lenient().when(snapshotRepository.findByUploadJobId(jobId)).thenReturn(List.of(
                snapshot("TDS Payable 194C", "Direct Expenses", "Expenses|Direct Expenses|Duties & Taxes", false),
                snapshot("TDS Receivable 206A", "Current Assets", "Current Assets|Loans & Advances", false),
                snapshot("Rent", "Indirect Expenses", "Indirect Expenses|Duties & Taxes", true)));
    }

    @Test
    void viewJoinsCandidatesWithSavedRowSuggestionAndArea() {
        stubMasters();
        TdsLedgerMapping saved = new TdsLedgerMapping();
        saved.setLedgerName("TDS Payable 194C");
        saved.setLedgerKey("tds payable 194c");
        saved.setRole(TdsLedgerRole.TDS_PAYABLE);
        saved.setSection("194C");
        saved.setSavedBy("accountant1");
        saved.setSavedAt(Instant.parse("2026-11-01T10:15:00Z"));
        when(repository.findByOrganizationId(org)).thenReturn(List.of(saved));

        TdsLedgerMappingView view = service.view(org, gate);

        assertEquals(3, view.ledgers().size());
        assertEquals(List.of("Rent", "TDS Payable 194C", "TDS Receivable 206A"),
                view.ledgers().stream().map(TdsLedgerMappingView.Row::ledgerName).toList());

        TdsLedgerMappingView.Row savedRow = view.ledgers().get(1);
        assertTrue(savedRow.saved());
        assertEquals(TdsLedgerRole.TDS_PAYABLE, savedRow.role());
        assertEquals("194C", savedRow.section());
        assertEquals(TdsLedgerArea.PAYABLE, savedRow.area());
        assertEquals("accountant1", savedRow.savedBy());
        assertEquals(Instant.parse("2026-11-01T10:15:00Z"), savedRow.savedAt());

        TdsLedgerMappingView.Row receivableRow = view.ledgers().get(2);
        assertTrue(receivableRow.saved() == false);
        assertEquals(TdsLedgerRole.TDS_RECEIVABLE, receivableRow.suggestedRole());
        assertEquals(TdsLedgerArea.RECEIVABLE, receivableRow.area());

        TdsLedgerMappingView.Row rentRow = view.ledgers().get(0);
        assertEquals(TdsLedgerRole.NOT_TDS, rentRow.suggestedRole());
        assertEquals(TdsLedgerArea.PAYABLE, rentRow.area());
        assertEquals(TdsLedgerRole.NOT_TDS, rentRow.role());
        assertTrue(rentRow.saved() == false);
    }

    @Test
    void receivableSavedAsNotTdsKeepsReceivableArea() {
        stubMasters();
        TdsLedgerMapping saved = new TdsLedgerMapping();
        saved.setLedgerName("TDS Receivable 206A");
        saved.setLedgerKey("tds receivable 206a");
        saved.setRole(TdsLedgerRole.NOT_TDS);
        when(repository.findByOrganizationId(org)).thenReturn(List.of(saved));

        TdsLedgerMappingView view = service.view(org, gate);

        TdsLedgerMappingView.Row row = view.ledgers().stream()
                .filter(r -> r.ledgerName().equals("TDS Receivable 206A")).findFirst().orElseThrow();
        assertEquals(TdsLedgerRole.NOT_TDS, row.role());
        assertEquals(TdsLedgerArea.RECEIVABLE, row.area());
    }

    @Test
    void viewCarriesCompleteBlockersAndReceivableWarnings() {
        stubMasters();
        when(gate.isComplete(org)).thenReturn(true);
        when(gate.blockerMessage(org)).thenReturn(Optional.empty());

        TdsLedgerMappingView view = service.view(org, gate);

        assertTrue(view.complete());
        assertTrue(view.blockers().isEmpty());
        assertEquals(1, view.receivableWarnings().size());
        assertEquals("1 TDS receivable ledgers not mapped - receivable checks skip them until mapped",
                view.receivableWarnings().get(0));
    }

    @Test
    void saveUpsertsMappingsAndPublishesEvent() {
        stubMasters();
        when(repository.findByOrganizationIdAndLedgerKey(org, "tds payable 194c"))
                .thenReturn(Optional.empty());
        TdsLedgerMapping existing = new TdsLedgerMapping();
        existing.setLedgerName("Rent");
        existing.setLedgerKey("rent");
        existing.setRole(TdsLedgerRole.NOT_TDS);
        when(repository.findByOrganizationIdAndLedgerKey(org, "rent")).thenReturn(Optional.of(existing));

        service.save(org, "owner1", new SaveTdsLedgerMappingRequest(List.of(
                new SaveTdsLedgerMappingRequest.Item("TDS Payable 194C", TdsLedgerRole.TDS_PAYABLE, "194C"),
                new SaveTdsLedgerMappingRequest.Item("Rent", TdsLedgerRole.NOT_TDS, null))));

        ArgumentCaptor<TdsLedgerMapping> captor = ArgumentCaptor.forClass(TdsLedgerMapping.class);
        verify(repository, times(2)).save(captor.capture());
        List<TdsLedgerMapping> savedMappings = captor.getAllValues();
        assertEquals(2, savedMappings.size());

        TdsLedgerMapping payable = savedMappings.get(0);
        assertEquals(org, payable.getOrganizationId());
        assertEquals("TDS Payable 194C", payable.getLedgerName());
        assertEquals("tds payable 194c", payable.getLedgerKey());
        assertEquals(TdsLedgerRole.TDS_PAYABLE, payable.getRole());
        assertEquals("194C", payable.getSection());
        assertEquals("owner1", payable.getSavedBy());
        assertNotNull(payable.getSavedAt());

        TdsLedgerMapping notTds = savedMappings.get(1);
        assertEquals(TdsLedgerRole.NOT_TDS, notTds.getRole());
        assertEquals("Rent", notTds.getLedgerName());

        verify(eventPublisher).publishEvent(new TdsLedgerMappingSavedEvent(org));
    }

    @Test
    void saveRejectsNullItems() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.save(org, "owner1", null));
        assertEquals("No TDS ledger mapping items provided.", ex.getMessage());
    }

    @Test
    void saveRejectsEmptyItems() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.save(org, "owner1", new SaveTdsLedgerMappingRequest(List.of())));
        assertEquals("No TDS ledger mapping items provided.", ex.getMessage());
    }

    @Test
    void saveRejectsBlankLedgerName() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.save(org, "owner1", new SaveTdsLedgerMappingRequest(List.of(
                        new SaveTdsLedgerMappingRequest.Item("  ", TdsLedgerRole.NOT_TDS, null)))));
        assertEquals("Ledger name is required.", ex.getMessage());
    }

    @Test
    void saveRejectsUnknownLedger() {
        stubMasters();
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.save(org, "owner1", new SaveTdsLedgerMappingRequest(List.of(
                        new SaveTdsLedgerMappingRequest.Item("Not There", TdsLedgerRole.NOT_TDS, null)))));
        assertEquals("Unknown TDS ledger: Not There", ex.getMessage());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void saveRejectsNullRole() {
        stubMasters();
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.save(org, "owner1", new SaveTdsLedgerMappingRequest(List.of(
                        new SaveTdsLedgerMappingRequest.Item("Rent", null, null)))));
        assertEquals("TDS ledger role is required for: Rent", ex.getMessage());
    }

    @Test
    void saveRejectsSectionOnReceivable() {
        stubMasters();
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.save(org, "owner1", new SaveTdsLedgerMappingRequest(List.of(
                        new SaveTdsLedgerMappingRequest.Item("TDS Receivable 206A",
                                TdsLedgerRole.TDS_RECEIVABLE, "194C")))));
        assertEquals("Section must be empty when role is not TDS payable.", ex.getMessage());
    }

    @Test
    void saveRejectsSectionOnNotTds() {
        stubMasters();
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.save(org, "owner1", new SaveTdsLedgerMappingRequest(List.of(
                        new SaveTdsLedgerMappingRequest.Item("Rent", TdsLedgerRole.NOT_TDS, "194C")))));
        assertEquals("Section must be empty when role is not TDS payable.", ex.getMessage());
    }

    @Test
    void saveRejectsUnknownSection() {
        stubMasters();
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.save(org, "owner1", new SaveTdsLedgerMappingRequest(List.of(
                        new SaveTdsLedgerMappingRequest.Item("Rent", TdsLedgerRole.TDS_PAYABLE, "194X")))));
        assertEquals("Unknown TDS section: 194X", ex.getMessage());
    }

    @Test
    void mappingsByKeyTdsLedgerKeysAndReceivableLedgerKeysClassifyNotTds() {
        stubMasters();
        TdsLedgerMapping payable = new TdsLedgerMapping();
        payable.setLedgerName("TDS Payable 194C");
        payable.setLedgerKey("tds payable 194c");
        payable.setRole(TdsLedgerRole.TDS_PAYABLE);
        TdsLedgerMapping receivable = new TdsLedgerMapping();
        receivable.setLedgerName("TDS Receivable 206A");
        receivable.setLedgerKey("tds receivable 206a");
        receivable.setRole(TdsLedgerRole.TDS_RECEIVABLE);
        TdsLedgerMapping notTds = new TdsLedgerMapping();
        notTds.setLedgerName("Rent");
        notTds.setLedgerKey("rent");
        notTds.setRole(TdsLedgerRole.NOT_TDS);
        when(repository.findByOrganizationId(org)).thenReturn(List.of(payable, receivable, notTds));

        Map<String, TdsLedgerMapping> byKey = service.mappingsByKey(org);
        assertEquals(3, byKey.size());
        assertTrue(byKey.containsKey("rent"));

        assertEquals(new java.util.TreeSet<>(List.of("tds payable 194c", "tds receivable 206a")),
                new java.util.TreeSet<>(service.tdsLedgerKeys(org)));
        assertEquals(java.util.Set.of("tds receivable 206a"), service.receivableLedgerKeys(org));
    }

    @Test
    void unmappedReceivableLedgersListUnsavedReceivableCandidates() {
        stubMasters();
        TdsLedgerMapping receivableSaved = new TdsLedgerMapping();
        receivableSaved.setLedgerName("TDS Receivable 206A");
        receivableSaved.setLedgerKey("tds receivable 206a");
        receivableSaved.setRole(TdsLedgerRole.TDS_RECEIVABLE);
        when(repository.findByOrganizationId(org)).thenReturn(List.of(receivableSaved));

        assertEquals(List.of(), service.unmappedReceivableLedgers(org));
    }
}
