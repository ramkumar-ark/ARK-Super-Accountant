package com.arktech.superaccountant.tds.workflow;

import com.arktech.superaccountant.daybook.repository.ParsedVoucherRepository;
import com.arktech.superaccountant.masters.models.LedgerCategory;
import com.arktech.superaccountant.masters.models.Organization;
import com.arktech.superaccountant.masters.models.UploadLedgerSnapshot;
import com.arktech.superaccountant.masters.repository.OrganizationRepository;
import com.arktech.superaccountant.masters.repository.UploadLedgerSnapshotRepository;
import com.arktech.superaccountant.tds.engine.TdsEngine;
import com.arktech.superaccountant.tds.engine.TdsEngineFixtures;
import com.arktech.superaccountant.tds.models.TdsPanOverride;
import com.arktech.superaccountant.statutory.models.Statute;
import com.arktech.superaccountant.statutory.models.StatutoryRuleContext;
import com.arktech.superaccountant.statutory.models.StatutorySection;
import com.arktech.superaccountant.statutory.models.StatutorySectionRule;
import com.arktech.superaccountant.tds.repository.TdsPanOverrideRepository;
import com.arktech.superaccountant.workflow.analysis.AnalysisContext;
import com.arktech.superaccountant.workflow.models.WorkflowType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TdsAnalysisInputFactoryTest {

    private static final UUID MASTERS_JOB = UUID.fromString(
            "00000000-0000-0000-0000-00000000000a");

    private ParsedVoucherRepository parsedVoucherRepository;
    private UploadLedgerSnapshotRepository snapshotRepository;
    private OrganizationRepository organizationRepository;
    private TdsPanOverrideRepository panOverrideRepository;
    private com.arktech.superaccountant.tds.repository.DebtorTanRepository debtorTanRepository;
    private com.arktech.superaccountant.tds.mapping.TdsLedgerMappingGate mappingGate;
    private com.arktech.superaccountant.tds.mapping.TdsLedgerMappingService mappingService;
    private com.arktech.superaccountant.statutory.repository.StatutorySectionRepository statutorySectionRepository;
    private com.arktech.superaccountant.statutory.repository.StatutorySectionRuleRepository statutorySectionRuleRepository;
    private TdsAnalysisInputFactory factory;

    private final AnalysisContext ctx = new AnalysisContext(
            UUID.randomUUID(), TdsEngineFixtures.ORG, WorkflowType.TDS_PAYMENT,
            LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30),
            UUID.randomUUID(), MASTERS_JOB, UUID.randomUUID());

    @BeforeEach
    void setUp() {
        parsedVoucherRepository = mock(ParsedVoucherRepository.class);
        snapshotRepository = mock(UploadLedgerSnapshotRepository.class);
        organizationRepository = mock(OrganizationRepository.class);
        panOverrideRepository = mock(TdsPanOverrideRepository.class);
        debtorTanRepository = mock(com.arktech.superaccountant.tds.repository.DebtorTanRepository.class);
        mappingGate = mock(com.arktech.superaccountant.tds.mapping.TdsLedgerMappingGate.class);
        mappingService = mock(com.arktech.superaccountant.tds.mapping.TdsLedgerMappingService.class);
        statutorySectionRepository = mock(
                com.arktech.superaccountant.statutory.repository.StatutorySectionRepository.class);
        statutorySectionRuleRepository = mock(
                com.arktech.superaccountant.statutory.repository.StatutorySectionRuleRepository.class);
        factory = new TdsAnalysisInputFactory();
        inject("parsedVoucherRepository", parsedVoucherRepository);
        inject("snapshotRepository", snapshotRepository);
        inject("organizationRepository", organizationRepository);
        inject("panOverrideRepository", panOverrideRepository);
        inject("debtorTanRepository", debtorTanRepository);
        inject("mappingGate", mappingGate);
        inject("mappingService", mappingService);
        inject("statutorySectionRepository", statutorySectionRepository);
        inject("statutorySectionRuleRepository", statutorySectionRuleRepository);
        inject("statutorySectionService", mock(
                com.arktech.superaccountant.statutory.services.StatutorySectionService.class));

        when(mappingService.mappingsByKey(TdsEngineFixtures.ORG)).thenReturn(java.util.Map.of());

        Organization org = new Organization();
        org.setName("Org");
        org.setFinancialYearStart(4);
        when(organizationRepository.findById(TdsEngineFixtures.ORG))
                .thenReturn(Optional.of(org));
        when(parsedVoucherRepository.findByUploadJobId(any())).thenReturn(List.of());
        when(snapshotRepository.findByUploadJobId(MASTERS_JOB)).thenReturn(List.of());
        when(panOverrideRepository.findByOrganizationIdOrderByPartyLedgerNameAsc(
                TdsEngineFixtures.ORG)).thenReturn(List.of());
        when(debtorTanRepository.findByOrganizationIdOrderByLedgerNameAsc(
                TdsEngineFixtures.ORG)).thenReturn(List.of());
    }

    private final List<StatutorySection> sections = new java.util.ArrayList<>();
    private final List<StatutorySectionRule> rules = new java.util.ArrayList<>();

    /** Seeds one section with an old-regime DEFAULT row plus (when newActAnnual given) a s.393 row. */
    private StatutorySection seedSection(String code,
            String oldRateCompany, String oldRateNonCompany, String oldPerTxn, String oldAnnual,
            String newRateCompany, String newRateNonCompany, String newPerTxn, String newAnnual) {
        StatutorySection section = new StatutorySection();
        section.setId(UUID.randomUUID());
        section.setStatute(Statute.INCOME_TAX);
        section.setCode(code);
        section.setActive(true);
        sections.add(section);
        rules.add(rule(section, oldRateCompany, oldRateNonCompany, oldPerTxn, oldAnnual,
                LocalDate.of(2000, 4, 1), LocalDate.of(2026, 3, 31)));
        if (newAnnual != null) {
            rules.add(rule(section, newRateCompany, newRateNonCompany, newPerTxn, newAnnual,
                    LocalDate.of(2026, 4, 1), null));
        }
        return section;
    }

    private StatutorySectionRule rule(StatutorySection section, String rateCompany, String rateNonCompany,
            String perTxn, String annual, LocalDate from, LocalDate to) {
        StatutorySectionRule r = new StatutorySectionRule();
        r.setSectionId(section.getId());
        r.setRuleContext(StatutoryRuleContext.DEFAULT);
        r.setRateCompany(new java.math.BigDecimal(rateCompany));
        r.setRateNonCompany(new java.math.BigDecimal(rateNonCompany));
        r.setThresholdPerTransaction(perTxn != null ? new java.math.BigDecimal(perTxn) : null);
        r.setThresholdAnnual(new java.math.BigDecimal(annual));
        r.setEffectiveFrom(from);
        r.setEffectiveTo(to);
        return r;
    }

    private void stubStatutory() {
        when(statutorySectionRepository.findByStatuteOrderBySortOrderAscCodeAsc(Statute.INCOME_TAX))
                .thenReturn(sections);
        when(statutorySectionRuleRepository.findAll()).thenReturn(rules);
    }

    private void inject(String field, Object value) {
        try {
            var f = TdsAnalysisInputFactory.class.getDeclaredField(field);
            f.setAccessible(true);
            f.set(factory, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private UploadLedgerSnapshot partySnapshot(String pan) {
        UploadLedgerSnapshot s = new UploadLedgerSnapshot();
        s.setLedgerName("ABC Contractors");
        s.setCategory(LedgerCategory.CREDITOR);
        s.setPan(pan);
        return s;
    }

    private TdsPanOverride override(String pan) {
        TdsPanOverride o = new TdsPanOverride();
        o.setOrganizationId(TdsEngineFixtures.ORG);
        o.setPartyLedgerName("ABC Contractors");
        o.setPartyKey(TdsEngine.key("ABC Contractors"));
        o.setPan(pan);
        return o;
    }

    @Test
    void mappings_flowedFromMappingService() {
        com.arktech.superaccountant.tds.mapping.TdsLedgerMapping row =
                new com.arktech.superaccountant.tds.mapping.TdsLedgerMapping();
        row.setLedgerKey(TdsEngine.key("TDS Payable 194C"));
        when(mappingService.mappingsByKey(TdsEngineFixtures.ORG)).thenReturn(java.util.Map.of(
                TdsEngine.key("TDS Payable 194C"), row));

        var input = factory.build(ctx);

        assertSame(row, input.mappingsByKey().get(TdsEngine.key("TDS Payable 194C")));
    }

    @Test
    void overlay_appliesOverrideWhenMastersPanInvalid() {
        UploadLedgerSnapshot masters = partySnapshot("BAD");
        when(snapshotRepository.findByUploadJobId(MASTERS_JOB)).thenReturn(List.of(masters));
        when(panOverrideRepository.findByOrganizationIdOrderByPartyLedgerNameAsc(
                TdsEngineFixtures.ORG)).thenReturn(List.of(override("ABCPE1234F")));

        var input = factory.build(ctx);

        Map<String, UploadLedgerSnapshot> snaps = input.snapshotsByName();
        assertEquals("ABCPE1234F", snaps.get(TdsEngine.key("ABC Contractors")).getPan());
        assertEquals("BAD", masters.getPan());
    }

    @Test
    void overlay_validMastersPanWins() {
        UploadLedgerSnapshot masters = partySnapshot("ABCPE1234F");
        when(snapshotRepository.findByUploadJobId(MASTERS_JOB)).thenReturn(List.of(masters));
        when(panOverrideRepository.findByOrganizationIdOrderByPartyLedgerNameAsc(
                TdsEngineFixtures.ORG)).thenReturn(List.of(override("ZZZPE9999Z")));

        var input = factory.build(ctx);

        assertEquals("ABCPE1234F", input.snapshotsByName()
                .get(TdsEngine.key("ABC Contractors")).getPan());
        assertSame(masters, input.snapshotsByName().get(TdsEngine.key("ABC Contractors")));
    }

    @Test
    void overlay_missingSnapshot_addsSynthetic() {
        when(panOverrideRepository.findByOrganizationIdOrderByPartyLedgerNameAsc(
                TdsEngineFixtures.ORG)).thenReturn(List.of(override("ABCPE1234F")));

        var input = factory.build(ctx);

        UploadLedgerSnapshot synthetic = input.snapshotsByName()
                .get(TdsEngine.key("ABC Contractors"));
        assertTrue(synthetic != null);
        assertEquals("ABCPE1234F", synthetic.getPan());
        assertEquals("ABC Contractors", synthetic.getLedgerName());
    }

    private Organization organization(String interestPayerType) {
        Organization org = new Organization();
        org.setName("Org");
        org.setFinancialYearStart(4);
        org.setInterestPayerType(interestPayerType);
        when(organizationRepository.findById(TdsEngineFixtures.ORG)).thenReturn(Optional.of(org));
        return org;
    }

    @Test
    void configResolution_194aBankPayerUsesFiftyThousandThreshold() {
        organization("BANK");
        seedSection("194A", "10.00", "10.00", null, "10000.00", "10.00", "10.00", null, "10000.00");
        stubStatutory();

        var input = factory.build(new AnalysisContext(
                UUID.randomUUID(), TdsEngineFixtures.ORG, WorkflowType.TDS_PAYMENT,
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31),
                UUID.randomUUID(), MASTERS_JOB, UUID.randomUUID()));

        assertEquals("50000.00",
                input.configsBySection().get("194A").getThresholdAnnual().toPlainString());
        assertEquals("10000.00", rules.get(0).getThresholdAnnual().toPlainString());
    }

    @Test
    void configResolution_194aBankPayerUsesFortyThousandBeforeFinanceAct2025() {
        organization("BANK");
        seedSection("194A", "10.00", "10.00", null, "10000.00", "10.00", "10.00", null, "10000.00");
        stubStatutory();

        var input = factory.build(new AnalysisContext(
                UUID.randomUUID(), TdsEngineFixtures.ORG, WorkflowType.TDS_PAYMENT,
                LocalDate.of(2024, 6, 15), LocalDate.of(2024, 6, 30),
                UUID.randomUUID(), MASTERS_JOB, UUID.randomUUID()));

        assertEquals("40000.00",
                input.configsBySection().get("194A").getThresholdAnnual().toPlainString());
        assertEquals("10000.00", rules.get(0).getThresholdAnnual().toPlainString());
    }

    @Test
    void configResolution_194aOtherPayerUsesFiveThousandBeforeFinanceAct2025() {
        organization("OTHER");
        seedSection("194A", "10.00", "10.00", null, "10000.00", "10.00", "10.00", null, "10000.00");
        stubStatutory();

        var input = factory.build(new AnalysisContext(
                UUID.randomUUID(), TdsEngineFixtures.ORG, WorkflowType.TDS_PAYMENT,
                LocalDate.of(2024, 6, 15), LocalDate.of(2024, 6, 30),
                UUID.randomUUID(), MASTERS_JOB, UUID.randomUUID()));

        assertEquals("5000.00",
                input.configsBySection().get("194A").getThresholdAnnual().toPlainString());
        assertEquals("10000.00", rules.get(0).getThresholdAnnual().toPlainString());
    }

    @Test
    void configResolution_194aOtherPayerKeepsTenThousandThreshold() {
        organization("OTHER");
        seedSection("194A", "10.00", "10.00", null, "10000.00", "10.00", "10.00", null, "10000.00");
        stubStatutory();

        var input = factory.build(new AnalysisContext(
                UUID.randomUUID(), TdsEngineFixtures.ORG, WorkflowType.TDS_PAYMENT,
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31),
                UUID.randomUUID(), MASTERS_JOB, UUID.randomUUID()));

        assertEquals("10000.00",
                input.configsBySection().get("194A").getThresholdAnnual().toPlainString());
    }

    @Test
    void configResolution_194aBankPayerUsesFiftyThousandInNewActPeriod() {
        organization("POST_OFFICE");
        seedSection("194A", "10.00", "10.00", null, "10000.00", "10.00", "10.00", null, "10000.00");
        stubStatutory();

        var input = factory.build(ctx);

        assertEquals("50000.00",
                input.configsBySection().get("194A").getThresholdAnnual().toPlainString());
        assertEquals("10000.00", rules.get(1).getThresholdAnnual().toPlainString());
    }

    @Test
    void overlay_emptyOverride_missingSnapshot_hasNullSyntheticPan() {
        when(panOverrideRepository.findByOrganizationIdOrderByPartyLedgerNameAsc(
                TdsEngineFixtures.ORG)).thenReturn(List.of(override("")));

        var input = factory.build(ctx);

        UploadLedgerSnapshot synthetic = input.snapshotsByName()
                .get(TdsEngine.key("ABC Contractors"));
        assertTrue(synthetic != null);
        assertNull(synthetic.getPan());
    }

    @Test
    void overlay_emptyOverride_invalidMastersPan_copiesNullPan() {
        UploadLedgerSnapshot masters = partySnapshot("BAD");
        when(snapshotRepository.findByUploadJobId(MASTERS_JOB)).thenReturn(List.of(masters));
        when(panOverrideRepository.findByOrganizationIdOrderByPartyLedgerNameAsc(
                TdsEngineFixtures.ORG)).thenReturn(List.of(override("")));

        var input = factory.build(ctx);

        assertNull(input.snapshotsByName()
                .get(TdsEngine.key("ABC Contractors")).getPan());
        assertEquals("BAD", masters.getPan());
    }

    @Test
    void configResolution_newActPeriodResolvesNewValues() {
        seedSection("194J_A", "2.00", "2.00", "30000.00", "30000.00", "2.00", "2.00", null, "50000.00");
        stubStatutory();

        var input = factory.build(ctx);

        var resolved = input.configsBySection().get("194J_A");
        assertEquals("50000.00", resolved.getThresholdAnnual().toPlainString());
        assertEquals("30000.00", rules.get(0).getThresholdAnnual().toPlainString());
    }

    @Test
    void configResolution_legacy194IResolvesToBuildingHalf() {
        seedSection("194I_B", "10.00", "10.00", null, "240000.00", null, null, null, null);
        stubStatutory();

        var input = factory.build(ctx);

        assertSame(input.configsBySection().get("194I_B"), input.configsBySection().get("194I"));
    }

    @Test
    void configResolution_oldActPeriodKeepsStoredValues() {
        seedSection("194J_A", "2.00", "2.00", "30000.00", "30000.00", "2.00", "2.00", null, "50000.00");
        stubStatutory();

        var input = factory.build(new AnalysisContext(
                UUID.randomUUID(), TdsEngineFixtures.ORG, WorkflowType.TDS_PAYMENT,
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31),
                UUID.randomUUID(), MASTERS_JOB, UUID.randomUUID()));

        var resolved = input.configsBySection().get("194J_A");
        assertEquals("30000.00", resolved.getThresholdAnnual().toPlainString());
    }
}
