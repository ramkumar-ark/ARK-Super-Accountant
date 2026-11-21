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
import com.arktech.superaccountant.tds.repository.TdsPanOverrideRepository;
import com.arktech.superaccountant.tds.repository.TdsSectionConfigRepository;
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
    private TdsSectionConfigRepository configRepository;
    private OrganizationRepository organizationRepository;
    private TdsPanOverrideRepository panOverrideRepository;
    private TdsAnalysisInputFactory factory;

    private final AnalysisContext ctx = new AnalysisContext(
            UUID.randomUUID(), TdsEngineFixtures.ORG, WorkflowType.TDS_PAYMENT,
            LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30),
            UUID.randomUUID(), MASTERS_JOB, UUID.randomUUID());

    @BeforeEach
    void setUp() {
        parsedVoucherRepository = mock(ParsedVoucherRepository.class);
        snapshotRepository = mock(UploadLedgerSnapshotRepository.class);
        configRepository = mock(TdsSectionConfigRepository.class);
        organizationRepository = mock(OrganizationRepository.class);
        panOverrideRepository = mock(TdsPanOverrideRepository.class);
        factory = new TdsAnalysisInputFactory();
        inject("parsedVoucherRepository", parsedVoucherRepository);
        inject("snapshotRepository", snapshotRepository);
        inject("configRepository", configRepository);
        inject("organizationRepository", organizationRepository);
        inject("panOverrideRepository", panOverrideRepository);

        Organization org = new Organization();
        org.setName("Org");
        org.setFinancialYearStart(4);
        when(organizationRepository.findById(TdsEngineFixtures.ORG))
                .thenReturn(Optional.of(org));
        when(parsedVoucherRepository.findByUploadJobId(any())).thenReturn(List.of());
        when(configRepository.findAll()).thenReturn(
                List.of(TdsEngineFixtures.config("194C", "2.00", "1.00", "30000.00", "100000.00")));
        when(snapshotRepository.findByUploadJobId(MASTERS_JOB)).thenReturn(List.of());
        when(panOverrideRepository.findByOrganizationIdOrderByPartyLedgerNameAsc(
                TdsEngineFixtures.ORG)).thenReturn(List.of());
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

    @Test
    void configResolution_newActPeriodResolvesNewValues() {
        var c = TdsEngineFixtures.config("194J_A", "2.00", "2.00", "30000.00", "30000.00");
        c.setNewActRateCompany(new java.math.BigDecimal("2.00"));
        c.setNewActRateNonCompany(new java.math.BigDecimal("2.00"));
        c.setNewActThresholdAnnual(new java.math.BigDecimal("50000.00"));
        when(configRepository.findAll()).thenReturn(List.of(c));

        var input = factory.build(ctx);

        var resolved = input.configsBySection().get("194J_A");
        assertEquals("50000.00", resolved.getThresholdAnnual().toPlainString());
        assertEquals("30000.00", c.getThresholdAnnual().toPlainString());
    }

    @Test
    void configResolution_legacy194IResolvesToBuildingHalf() {
        var c = TdsEngineFixtures.config("194I_B", "10.00", "10.00", null, "240000.00");
        when(configRepository.findAll()).thenReturn(List.of(c));

        var input = factory.build(ctx);

        assertSame(input.configsBySection().get("194I_B"), input.configsBySection().get("194I"));
    }

    @Test
    void configResolution_oldActPeriodKeepsStoredValues() {
        var c = TdsEngineFixtures.config("194J_A", "2.00", "2.00", "30000.00", "30000.00");
        c.setNewActRateCompany(new java.math.BigDecimal("2.00"));
        c.setNewActRateNonCompany(new java.math.BigDecimal("2.00"));
        c.setNewActThresholdAnnual(new java.math.BigDecimal("50000.00"));
        when(configRepository.findAll()).thenReturn(List.of(c));

        var input = factory.build(new AnalysisContext(
                UUID.randomUUID(), TdsEngineFixtures.ORG, WorkflowType.TDS_PAYMENT,
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31),
                UUID.randomUUID(), MASTERS_JOB, UUID.randomUUID()));

        var resolved = input.configsBySection().get("194J_A");
        assertEquals("30000.00", resolved.getThresholdAnnual().toPlainString());
    }
}
