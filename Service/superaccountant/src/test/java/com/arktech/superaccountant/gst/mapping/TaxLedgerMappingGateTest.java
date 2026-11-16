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
