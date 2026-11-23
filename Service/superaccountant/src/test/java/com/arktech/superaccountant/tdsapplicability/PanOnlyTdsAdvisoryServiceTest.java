package com.arktech.superaccountant.tdsapplicability;

import com.arktech.superaccountant.daybook.models.DayBookUploadJob;
import com.arktech.superaccountant.daybook.models.ParsedVoucher;
import com.arktech.superaccountant.daybook.models.ParsedVoucherEntry;
import com.arktech.superaccountant.daybook.repository.DayBookUploadJobRepository;
import com.arktech.superaccountant.daybook.repository.ParsedVoucherRepository;
import com.arktech.superaccountant.masters.models.Organization;
import com.arktech.superaccountant.masters.models.UploadJob;
import com.arktech.superaccountant.masters.models.UploadJobStatus;
import com.arktech.superaccountant.masters.models.UploadLedgerSnapshot;
import com.arktech.superaccountant.masters.repository.OrganizationRepository;
import com.arktech.superaccountant.masters.repository.UploadJobRepository;
import com.arktech.superaccountant.masters.repository.UploadLedgerSnapshotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PanOnlyTdsAdvisoryServiceTest {

    private final UUID orgId = UUID.randomUUID();

    private OrganizationRepository organizationRepository;
    private TdsApplicabilityService applicabilityService;
    private UploadJobRepository uploadJobRepository;
    private UploadLedgerSnapshotRepository snapshotRepository;
    private DayBookUploadJobRepository dayBookJobRepository;
    private ParsedVoucherRepository voucherRepository;
    private PanOnlyTdsAdvisoryService service;

    @BeforeEach
    void setUp() {
        organizationRepository = mock(OrganizationRepository.class);
        applicabilityService = mock(TdsApplicabilityService.class);
        uploadJobRepository = mock(UploadJobRepository.class);
        snapshotRepository = mock(UploadLedgerSnapshotRepository.class);
        dayBookJobRepository = mock(DayBookUploadJobRepository.class);
        voucherRepository = mock(ParsedVoucherRepository.class);
        service = new PanOnlyTdsAdvisoryService(organizationRepository, applicabilityService,
                uploadJobRepository, snapshotRepository, dayBookJobRepository, voucherRepository);
    }

    private Organization org(String pan) {
        Organization org = new Organization("Advisory Org");
        org.setPan(pan);
        return org;
    }

    private void seedData(List<UploadLedgerSnapshot> snapshots, List<ParsedVoucher> vouchers) {
        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(org("AAAPA1234A")));
        when(applicabilityService.isApplicable(orgId, 2025)).thenReturn(false);
        when(applicabilityService.isApplicable(orgId, 2026)).thenReturn(false);

        UploadJob mastersJob = new UploadJob();
        when(uploadJobRepository.findTopByOrganizationIdAndStatusInOrderByCreatedAtDesc(orgId,
                PanOnlyTdsAdvisoryService.COMPLETED_STATUSES)).thenReturn(Optional.of(mastersJob));
        when(snapshotRepository.findByUploadJobId(mastersJob.getId())).thenReturn(snapshots);

        DayBookUploadJob dayBookJob = new DayBookUploadJob();
        when(dayBookJobRepository.findAllByOrganizationIdAndStatusInOrderByCreatedAtDesc(orgId,
                PanOnlyTdsAdvisoryService.COMPLETED_STATUSES)).thenReturn(List.of(dayBookJob));
        when(voucherRepository.findByUploadJobId(dayBookJob.getId())).thenReturn(vouchers);
    }

    private UploadLedgerSnapshot snapshot(String ledgerName, String tdsSection) {
        UploadLedgerSnapshot snapshot = new UploadLedgerSnapshot();
        snapshot.setLedgerName(ledgerName);
        snapshot.setTdsSection(tdsSection);
        return snapshot;
    }

    private ParsedVoucher voucher(String ledgerName, String amount, int year, int month, int day) {
        ParsedVoucher voucher = new ParsedVoucher();
        voucher.setOrganizationId(orgId);
        voucher.setVoucherTypeName("Payment");
        voucher.setVoucherNumber("V-" + ledgerName);
        voucher.setVoucherDate(LocalDate.of(year, month, day));
        voucher.setCancelled(false);
        ParsedVoucherEntry entry = new ParsedVoucherEntry();
        entry.setParsedVoucher(voucher);
        entry.setOrganizationId(orgId);
        entry.setLedgerName(ledgerName);
        entry.setAmount(new BigDecimal(amount));
        voucher.setEntries(List.of(entry));
        return voucher;
    }

    @Test
    void contractorPaymentsAbove50LakhRaise194M() {
        seedData(
                List.of(snapshot("Contractor X", "194C")),
                List.of(
                        voucher("Contractor X", "3000000.00", 2026, 1, 10),
                        voucher("Contractor X", "2500000.00", 2026, 3, 20)));

        List<PanOnlyAdvisory> advisories = service.advisories(orgId, 2025);

        assertEquals(1, advisories.size());
        PanOnlyAdvisory advisory = advisories.get(0);
        assertEquals("194M", advisory.section());
        assertEquals("Contractor X", advisory.partyLedgerName());
        assertEquals(2025, advisory.financialYear());
        assertEquals(null, advisory.month());
        assertEquals(new BigDecimal("5500000.00"), advisory.amount());
        assertEquals("Form 26QD", advisory.form());
    }

    @Test
    void below50LakhNoAdvisory() {
        seedData(
                List.of(snapshot("Contractor X", "194C")),
                List.of(voucher("Contractor X", "4999999.00", 2026, 1, 10)));

        assertTrue(service.advisories(orgId, 2025).isEmpty());
    }

    @Test
    void rentAbove50kInAMonthRaises194IB() {
        seedData(
                List.of(snapshot("Landlord", "194I_B")),
                List.of(
                        voucher("Landlord", "30000.00", 2026, 7, 5),
                        voucher("Landlord", "30000.00", 2026, 7, 20)));

        List<PanOnlyAdvisory> advisories = service.advisories(orgId, 2026);

        assertEquals(1, advisories.size());
        PanOnlyAdvisory advisory = advisories.get(0);
        assertEquals("194-IB", advisory.section());
        assertEquals("Landlord", advisory.partyLedgerName());
        assertEquals(2026, advisory.financialYear());
        assertEquals("2026-07", advisory.month());
        assertEquals(new BigDecimal("60000.00"), advisory.amount());
        assertEquals("Form 141", advisory.form());
    }

    @Test
    void formIs141OnOrAfter1Apr2026() {
        seedData(
                List.of(snapshot("Contractor X", "194C")),
                List.of(voucher("Contractor X", "6000000.00", 2026, 4, 5)));

        List<PanOnlyAdvisory> advisories = service.advisories(orgId, 2026);

        assertEquals(1, advisories.size());
        assertEquals("Form 141", advisories.get(0).form());
        assertEquals("194M", advisories.get(0).section());
    }

    @Test
    void noAdvisoriesWhenYearApplicable() {
        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(org("AAAPA1234A")));
        when(applicabilityService.isApplicable(orgId, 2025)).thenReturn(true);

        assertTrue(service.advisories(orgId, 2025).isEmpty());
    }

    @Test
    void noAdvisoriesForCompany() {
        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(org("AAACA1234A")));

        assertTrue(service.advisories(orgId, 2025).isEmpty());
    }
}
