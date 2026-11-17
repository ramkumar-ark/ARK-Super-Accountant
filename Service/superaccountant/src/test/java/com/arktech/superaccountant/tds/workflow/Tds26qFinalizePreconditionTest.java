package com.arktech.superaccountant.tds.workflow;

import com.arktech.superaccountant.masters.models.Organization;
import com.arktech.superaccountant.masters.repository.OrganizationRepository;
import com.arktech.superaccountant.tds.models.Tds26qDetail;
import com.arktech.superaccountant.tds.models.TdsChallan;
import com.arktech.superaccountant.tds.models.TdsChallanStatus;
import com.arktech.superaccountant.tds.models.TdsDeducteeEntry;
import com.arktech.superaccountant.tds.repository.Tds26qDetailRepository;
import com.arktech.superaccountant.tds.repository.TdsDeducteeEntryRepository;
import com.arktech.superaccountant.tds.repository.TdsFindingRepository;
import com.arktech.superaccountant.tds.services.TdsQuarterService;
import com.arktech.superaccountant.workflow.models.Workflow;
import com.arktech.superaccountant.workflow.models.WorkflowStage;
import com.arktech.superaccountant.workflow.models.WorkflowType;
import com.arktech.superaccountant.workflow.repository.WorkflowRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class Tds26qFinalizePreconditionTest {

    private static final String TAN = "CHEA12345B";
    private static final String PAN = "CHEAB1234F";

    private final UUID orgId = UUID.randomUUID();
    private final UUID quarterId = UUID.randomUUID();
    private final UUID challanId = UUID.randomUUID();

    private TdsDeducteeEntryRepository entryRepository;
    private Tds26qDetailRepository detailRepository;
    private TdsFindingRepository findingRepository;
    private Tds26qFinalizePrecondition precondition;
    private Workflow quarter;

    @BeforeEach
    void setUp() {
        entryRepository = mock(TdsDeducteeEntryRepository.class);
        detailRepository = mock(Tds26qDetailRepository.class);
        findingRepository = mock(TdsFindingRepository.class);
        TdsQuarterService quarterService = mock(TdsQuarterService.class);
        OrganizationRepository organizationRepository = mock(OrganizationRepository.class);
        WorkflowRepository workflowRepository = mock(WorkflowRepository.class);
        precondition = new Tds26qFinalizePrecondition(entryRepository, quarterService,
                detailRepository, organizationRepository, workflowRepository, findingRepository);

        quarter = new Workflow();
        quarter.setId(quarterId);
        quarter.setOrganizationId(orgId);
        quarter.setType(WorkflowType.TDS_26Q);
        quarter.setPeriodKey("2025-Q1");
        quarter.setPeriodStart(LocalDate.of(2025, 4, 1));
        quarter.setPeriodEnd(LocalDate.of(2025, 6, 30));
        quarter.setFinancialYear(2025);

        Organization org = new Organization("Org");
        org.setFinancialYearStart(4);
        org.setTan(TAN);
        org.setPan(PAN);
        org.setRegisteredAddress("Address");
        org.setResponsiblePersonName("Responsible");
        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(org));
        when(detailRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TdsChallan approved = new TdsChallan();
        approved.setId(challanId);
        approved.setChallanSerialNumber("1234");
        approved.setTdsAmount(new BigDecimal("1000.00"));
        approved.setStatus(TdsChallanStatus.APPROVED);
        when(quarterService.approvedChallans(quarter)).thenReturn(List.of(approved));
        List<Workflow> paymentMonths = new java.util.ArrayList<>();
        for (String month : List.of("2025-04", "2025-05", "2025-06")) {
            Workflow payment = new Workflow();
            payment.setId(UUID.randomUUID());
            payment.setPeriodKey(month);
            payment.setStage(WorkflowStage.FINALIZED);
            paymentMonths.add(payment);
        }
        when(quarterService.paymentWorkflows(quarter)).thenReturn(paymentMonths);

        Tds26qDetail detail = new Tds26qDetail();
        detail.setWorkflowId(quarterId);
        detail.setTan(TAN);
        detail.setDeductorPan(PAN);
        detail.setDeductorName("Deductor");
        detail.setDeductorAddress("Address");
        detail.setResponsiblePersonName("Responsible");
        when(detailRepository.findByWorkflowId(quarterId)).thenReturn(Optional.of(detail));
    }

    private TdsDeducteeEntry entry(String section, String deposited, String tdsAmount,
                                   boolean panValid, boolean higherRate) {
        TdsDeducteeEntry e = new TdsDeducteeEntry();
        e.setId(UUID.randomUUID());
        e.setWorkflowId(quarterId);
        e.setSection(section);
        e.setTdsChallanId(challanId);
        e.setTdsDeposited(new BigDecimal(deposited));
        e.setTdsDeducted(new BigDecimal(tdsAmount));
        e.setPanValid(panValid);
        e.setHigherRateApplied(higherRate);
        return e;
    }

    private void entries(List<TdsDeducteeEntry> list) {
        when(entryRepository.findByWorkflowIdAndSupersededAtIsNull(quarterId)).thenReturn(list);
    }

    @Test
    void allPass_emptyUnmet() {
        entries(List.of(entry("194C", "1000.00", "1000.00", true, false)));

        assertTrue(precondition.unmet(quarter).isEmpty());
    }

    @Test
    void unmappedEntry_failsAllEntriesMapped_detail() {
        TdsDeducteeEntry unmapped1 = entry("194C", "500.00", "500.00", true, false);
        TdsDeducteeEntry unmapped2 = entry("194C", "500.00", "500.00", true, false);
        unmapped1.setTdsChallanId(null);
        unmapped2.setTdsChallanId(null);
        entries(List.of(entry("194C", "1000.00", "1000.00", true, false), unmapped1, unmapped2));

        List<String> unmet = precondition.unmet(quarter);

        assertTrue(unmet.stream().anyMatch(m -> m.contains("2 entries unmapped")));
    }

    @Test
    void reconcileExact_failsOnOnePaisa() {
        entries(List.of(entry("194C", "5000.01", "5000.01", true, false)));

        List<String> unmet = precondition.unmet(quarter);

        assertTrue(unmet.stream().anyMatch(m -> m.contains("Challan 1234: mapped 5000.01")
                && m.contains("vs challan 1000.00")));
    }

    @Test
    void nilChallanWithMappedAmount_fails() {
        TdsChallan nil = new TdsChallan();
        nil.setId(UUID.randomUUID());
        nil.setChallanSerialNumber("9999");
        nil.setChallanSerialNumber("9999");
        nil.setTdsAmount(BigDecimal.ZERO);
        nil.setStatus(TdsChallanStatus.APPROVED);
        TdsQuarterService quarterService = mock(TdsQuarterService.class);
        when(quarterService.approvedChallans(quarter)).thenReturn(List.of(nil));
        precondition = new Tds26qFinalizePrecondition(entryRepository, quarterService,
                detailRepository, mock(OrganizationRepository.class), mock(WorkflowRepository.class),
                findingRepository);
        TdsDeducteeEntry mapped = entry("194C", "1000.00", "1000.00", true, false);
        mapped.setTdsChallanId(nil.getId());
        entries(List.of(mapped));

        List<String> unmet = precondition.unmet(quarter);

        assertTrue(unmet.stream().anyMatch(m -> m.contains("Challan 9999")));
    }

    @Test
    void invalidPanWithHigherRate_passesPansValid() {
        entries(List.of(entry("194C", "1000.00", "1000.00", false, true)));

        List<String> unmet = precondition.unmet(quarter);

        assertTrue(unmet.stream().noneMatch(m -> m.startsWith("PANs valid:")));
    }

    @Test
    void deductorDetailsRefreshedFromOrgWhenNotFinalized() {
        Organization updatedOrg = new Organization("Updated Org");
        updatedOrg.setFinancialYearStart(4);
        updatedOrg.setTan("XYZP99999K");
        updatedOrg.setPan("ABCDE9999F");
        updatedOrg.setRegisteredAddress("New Address");
        updatedOrg.setResponsiblePersonName("New Person");

        OrganizationRepository orgRepo = mock(OrganizationRepository.class);
        when(orgRepo.findById(orgId)).thenReturn(Optional.of(updatedOrg));

        TdsQuarterService quarterService = mock(TdsQuarterService.class);
        when(quarterService.approvedChallans(quarter)).thenReturn(List.of());

        precondition = new Tds26qFinalizePrecondition(entryRepository, quarterService,
                detailRepository, orgRepo, mock(WorkflowRepository.class), findingRepository);

        Tds26qDetail detail = new Tds26qDetail();
        detail.setWorkflowId(quarterId);
        detail.setTan(TAN);
        detail.setDeductorPan(PAN);
        detail.setDeductorName("Old Deductor");
        when(detailRepository.findByWorkflowId(quarterId)).thenReturn(Optional.of(detail));

        quarter.setStage(com.arktech.superaccountant.workflow.models.WorkflowStage.PREPARE);
        precondition.evaluate(quarter);

        verify(detailRepository).save(detail);
        assertEquals("XYZP99999K", detail.getTan());
        assertEquals("ABCDE9999F", detail.getDeductorPan());
        assertEquals("Updated Org", detail.getDeductorName());
        assertEquals("New Address", detail.getDeductorAddress());
        assertEquals("New Person", detail.getResponsiblePersonName());
    }

    @Test
    void deductorDetailsNotRefreshedWhenFinalized() {
        Organization updatedOrg = new Organization("Updated Org");
        updatedOrg.setFinancialYearStart(4);
        updatedOrg.setTan("XYZP99999K");

        OrganizationRepository orgRepo = mock(OrganizationRepository.class);
        when(orgRepo.findById(orgId)).thenReturn(Optional.of(updatedOrg));

        TdsQuarterService quarterService = mock(TdsQuarterService.class);
        when(quarterService.approvedChallans(quarter)).thenReturn(List.of());

        precondition = new Tds26qFinalizePrecondition(entryRepository, quarterService,
                detailRepository, orgRepo, mock(WorkflowRepository.class), findingRepository);

        Tds26qDetail detail = new Tds26qDetail();
        detail.setWorkflowId(quarterId);
        detail.setTan(TAN);
        detail.setDeductorPan(PAN);
        detail.setDeductorName("Old Deductor");
        detail.setDeductorAddress("Old Address");
        detail.setResponsiblePersonName("Old Person");
        when(detailRepository.findByWorkflowId(quarterId)).thenReturn(Optional.of(detail));

        quarter.setStage(com.arktech.superaccountant.workflow.models.WorkflowStage.FINALIZED);
        precondition.evaluate(quarter);

        verify(detailRepository, never()).save(any());
        assertEquals(TAN, detail.getTan());
    }

    @Test
    void revisedWithoutReceipt_failsDeductorComplete() {
        quarter.setRevision(1);
        entries(List.of(entry("194C", "1000.00", "1000.00", true, false)));

        List<String> unmet = precondition.unmet(quarter);

        assertTrue(unmet.stream().anyMatch(m ->
                m.startsWith("Deductor complete:") && m.contains("Original receipt number")));
    }

    @Test
    void noApprovedChallan_failsHasChallan() {
        TdsQuarterService quarterService = mock(TdsQuarterService.class);
        when(quarterService.approvedChallans(quarter)).thenReturn(List.of());
        precondition = new Tds26qFinalizePrecondition(entryRepository, quarterService,
                detailRepository, mock(OrganizationRepository.class), mock(WorkflowRepository.class),
                findingRepository);
        entries(List.of());

        List<String> unmet = precondition.unmet(quarter);

        assertTrue(unmet.stream().anyMatch(m ->
                m.startsWith("Has approved challan:")));
    }

    @Test
    void hasApprovedChallan_passesWithApprovedDetail() {
        // quarter has 1 approved challan set up in setUp()
        entries(List.of(entry("194C", "1000.00", "1000.00", true, false)));

        List<TdsGateCondition> conditions = precondition.evaluate(quarter);
        TdsGateCondition hasChallan = conditions.stream()
                .filter(c -> "HAS_CHALLAN".equals(c.code()))
                .findFirst().orElseThrow();

        assertTrue(hasChallan.passed());
        assertEquals("1 approved challan", hasChallan.detail());
    }

    @Test
    void conditionsInOldOrder() {
        TdsQuarterService emptyQuarter = mock(TdsQuarterService.class);
        when(emptyQuarter.approvedChallans(quarter)).thenReturn(List.of());
        precondition = new Tds26qFinalizePrecondition(entryRepository, emptyQuarter,
                detailRepository, mock(OrganizationRepository.class), mock(WorkflowRepository.class),
                findingRepository);
        entries(List.of());

        List<String> codes = precondition.evaluate(quarter).stream()
                .map(TdsGateCondition::code).toList();

        assertEquals(List.of("ALL_ENTRIES_MAPPED", "CHALLANS_RECONCILE", "PANS_VALID",
                "DEDUCTOR_COMPLETE", "HAS_CHALLAN", "PAYMENT_MONTHS_FINALIZED"), codes);
        assertFalse(precondition.evaluate(quarter).get(4).passed());
    }
}
