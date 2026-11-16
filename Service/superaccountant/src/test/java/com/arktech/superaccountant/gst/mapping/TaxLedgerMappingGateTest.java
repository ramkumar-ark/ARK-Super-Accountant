package com.arktech.superaccountant.gst.mapping;

import com.arktech.superaccountant.masters.models.LedgerCategory;
import com.arktech.superaccountant.masters.models.Organization;
import com.arktech.superaccountant.masters.models.UploadLedgerSnapshot;
import com.arktech.superaccountant.masters.repository.OrganizationRepository;
import com.arktech.superaccountant.masters.services.FindingOverrideMasterService;
import com.arktech.superaccountant.workflow.seam.MastersJobGate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaxLedgerMappingGateTest {

    @Mock
    private TaxLedgerMappingService mappingService;

    @Mock
    private MastersJobGate mastersJobGate;

    @Mock
    private FindingOverrideMasterService findingOverrideMasterService;

    @Mock
    private OrganizationRepository organizationRepository;

    private TaxLedgerMappingGate gate;

    private final UUID org = UUID.randomUUID();

    private static UploadLedgerSnapshot gstLedger(String name) {
        UploadLedgerSnapshot s = new UploadLedgerSnapshot();
        s.setLedgerName(name);
        s.setCategory(LedgerCategory.GST);
        return s;
    }

    @BeforeEach
    void setUp() {
        gate = new TaxLedgerMappingGate(mappingService, mastersJobGate, findingOverrideMasterService,
                organizationRepository);
    }

    private void stubOrg(boolean hasGstin) {
        Organization organization = new Organization();
        organization.setGstin(hasGstin ? "27AAAAA0000A1Z5" : null);
        lenient().when(organizationRepository.findById(org)).thenReturn(Optional.of(organization));
    }

    @Test
    void gateIncompleteWithoutMastersUpload() {
        stubOrg(true);
        when(mastersJobGate.latestCompletedMastersJob(org)).thenReturn(Optional.empty());

        assertFalse(gate.isComplete(org));
        assertEquals(Optional.of("No masters upload: tax ledgers cannot be mapped"), gate.blockerMessage(org));
    }

    @Test
    void gateCompleteWhenNoGstinAndNoMasters() {
        stubOrg(false);
        when(mastersJobGate.latestCompletedMastersJob(org)).thenReturn(Optional.empty());

        assertTrue(gate.isComplete(org));
        assertEquals(Optional.empty(), gate.blockerMessage(org));
    }

    @Test
    void unmappedListsGstLedgersWithoutSavedRow() {
        stubOrg(true);
        UUID jobId = UUID.randomUUID();
        when(mastersJobGate.latestCompletedMastersJob(org)).thenReturn(Optional.of(jobId));
        when(findingOverrideMasterService.snapshotsByName(jobId)).thenReturn(Map.of(
                "output cgst", gstLedger("Output CGST"),
                "output sgst", gstLedger("Output SGST"),
                "sales", incomeLedger()));
        when(mappingService.mappingsByKey(org)).thenReturn(Map.of("output cgst", savedMapping()));

        assertEquals(List.of("Output SGST"), gate.unmappedLedgers(org));
        assertEquals(Optional.of("1 tax ledgers not mapped: Output SGST"), gate.blockerMessage(org));
    }

    private static final List<TaxLedgerRole> REQUIRED_ROLES = List.of(TaxLedgerRole.OUTPUT, TaxLedgerRole.INPUT,
            TaxLedgerRole.CASH_BALANCE, TaxLedgerRole.LIABILITY_BALANCE, TaxLedgerRole.CREDIT_BALANCE,
            TaxLedgerRole.GST_TDS);

    private static final List<GstHead> REQUIRED_HEADS = List.of(GstHead.IGST, GstHead.CGST, GstHead.SGST);

    /** Stubs masters + mappings with the given (role, head) pairs, each as its own mapped GST ledger. */
    private void stubMapped(List<TaxLedgerRole> roles, List<GstHead> heads) {
        UUID jobId = UUID.randomUUID();
        when(mastersJobGate.latestCompletedMastersJob(org)).thenReturn(Optional.of(jobId));
        Map<String, UploadLedgerSnapshot> snapshots = new java.util.LinkedHashMap<>();
        Map<String, TaxLedgerMapping> mappings = new java.util.LinkedHashMap<>();
        for (TaxLedgerRole role : roles) {
            for (GstHead head : heads) {
                String name = role + " " + head;
                String key = name.toLowerCase();
                snapshots.put(key, gstLedger(name));
                TaxLedgerMapping m = new TaxLedgerMapping();
                m.setLedgerName(name);
                m.setLedgerKey(key);
                m.setRole(role);
                m.setHead(head);
                mappings.put(key, m);
            }
        }
        when(findingOverrideMasterService.snapshotsByName(jobId)).thenReturn(snapshots);
        when(mappingService.mappingsByKey(org)).thenReturn(mappings);
    }

    @Test
    void gstinCompanyWithNoGstLedgersIsBlocked() {
        stubOrg(true);
        UUID jobId = UUID.randomUUID();
        when(mastersJobGate.latestCompletedMastersJob(org)).thenReturn(Optional.of(jobId));
        when(findingOverrideMasterService.snapshotsByName(jobId)).thenReturn(Map.of("sales", incomeLedger()));
        when(mappingService.mappingsByKey(org)).thenReturn(Map.of());

        assertFalse(gate.isComplete(org));
        assertEquals(Optional.of("No GST tax ledgers found - check your masters"), gate.blockerMessage(org));
    }

    @Test
    void noGstinCompanyWithNoGstLedgersStaysComplete() {
        stubOrg(false);
        UUID jobId = UUID.randomUUID();
        when(mastersJobGate.latestCompletedMastersJob(org)).thenReturn(Optional.of(jobId));
        when(findingOverrideMasterService.snapshotsByName(jobId)).thenReturn(Map.of("sales", incomeLedger()));
        when(mappingService.mappingsByKey(org)).thenReturn(Map.of());

        assertTrue(gate.isComplete(org));
    }

    @Test
    void gstinCompanyBlockedUntilEveryAreaHasEveryHead() {
        stubOrg(true);
        stubMapped(List.of(TaxLedgerRole.OUTPUT, TaxLedgerRole.INPUT), REQUIRED_HEADS);

        assertFalse(gate.isComplete(org));
        assertEquals(Optional.of("Missing GST ledger mappings: "
                + "Cash balance IGST, Cash balance CGST, Cash balance SGST, "
                + "Liability balance IGST, Liability balance CGST, Liability balance SGST, "
                + "Credit balance IGST, Credit balance CGST, Credit balance SGST, "
                + "GST TDS IGST, GST TDS CGST, GST TDS SGST"), gate.blockerMessage(org));
    }

    @Test
    void missingSingleHeadIsNamedExactly() {
        stubOrg(true);
        stubMapped(REQUIRED_ROLES, List.of(GstHead.IGST, GstHead.CGST));

        assertEquals(Optional.of("Missing GST ledger mappings: Output SGST, Input SGST, Cash balance SGST, "
                + "Liability balance SGST, Credit balance SGST, GST TDS SGST"), gate.blockerMessage(org));
    }

    @Test
    void completeWhenEveryAreaHasIgstCgstSgst() {
        stubOrg(true);
        stubMapped(REQUIRED_ROLES, REQUIRED_HEADS);

        assertTrue(gate.isComplete(org));
        assertEquals(Optional.empty(), gate.blockerMessage(org));
    }

    @Test
    void rcmAndCessMappingsDoNotSatisfyRequirement() {
        stubOrg(true);
        stubMapped(List.of(TaxLedgerRole.RCM_INPUT, TaxLedgerRole.RCM_LIABILITY, TaxLedgerRole.NOT_TAX),
                List.of(GstHead.IGST, GstHead.CGST, GstHead.SGST, GstHead.CESS));

        assertTrue(gate.blockerMessage(org).orElseThrow().startsWith("Missing GST ledger mappings: Output IGST"));
    }

    private static TaxLedgerMapping savedMapping() {
        TaxLedgerMapping mapping = new TaxLedgerMapping();
        mapping.setLedgerName("Output CGST");
        mapping.setLedgerKey("output cgst");
        mapping.setRole(TaxLedgerRole.OUTPUT);
        mapping.setHead(GstHead.CGST);
        return mapping;
    }

    private static UploadLedgerSnapshot incomeLedger() {
        UploadLedgerSnapshot s = new UploadLedgerSnapshot();
        s.setLedgerName("Sales");
        s.setCategory(LedgerCategory.INCOME);
        return s;
    }
}
