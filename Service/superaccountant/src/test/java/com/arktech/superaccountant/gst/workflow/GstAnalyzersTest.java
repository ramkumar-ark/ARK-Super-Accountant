package com.arktech.superaccountant.gst.workflow;

import com.arktech.superaccountant.daybook.models.ParsedVoucher;
import com.arktech.superaccountant.daybook.repository.ParsedVoucherRepository;
import com.arktech.superaccountant.gst.GstTestFixtures;
import com.arktech.superaccountant.gst.models.GstDaybookFinding;
import com.arktech.superaccountant.gst.models.GstFindingType;
import com.arktech.superaccountant.gst.models.VoucherSide;
import com.arktech.superaccountant.gst.mapping.TaxLedgerMappingGate;
import com.arktech.superaccountant.gst.mapping.TaxLedgerMappingService;
import com.arktech.superaccountant.gst.repository.GstDaybookFindingRepository;
import com.arktech.superaccountant.gst.rules.GstDaybookRule;
import com.arktech.superaccountant.gst.rules.GstRuleContext;
import com.arktech.superaccountant.masters.models.FindingSeverity;
import com.arktech.superaccountant.masters.models.Organization;
import com.arktech.superaccountant.masters.models.ResolveStatus;
import com.arktech.superaccountant.masters.repository.OrganizationRepository;
import com.arktech.superaccountant.masters.services.FindingOverrideMasterService;
import com.arktech.superaccountant.review.EvidenceHash;
import com.arktech.superaccountant.review.FindingLifecycleService;
import com.arktech.superaccountant.workflow.analysis.AnalysisContext;
import com.arktech.superaccountant.workflow.models.WorkflowType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GstAnalyzersTest {

    private static final LocalDate PERIOD_START = LocalDate.of(2025, 4, 1);
    private static final LocalDate PERIOD_END = LocalDate.of(2025, 4, 30);

    private static final UUID WORKFLOW_ID = UUID.fromString("00000000-0000-0000-0000-000000000011");
    private static final UUID UPLOAD_JOB_ID = UUID.fromString("00000000-0000-0000-0000-000000000012");
    private static final UUID RUN_ID = UUID.fromString("00000000-0000-0000-0000-000000000013");
    private static final UUID ORG_ID = GstTestFixtures.ORG_ID;
    private static final String ORG_GSTIN = "27AAACA1234A1Z5";

    @Mock
    private ParsedVoucherRepository parsedVoucherRepository;
    @Mock
    private GstDaybookFindingRepository findingRepository;
    @Mock
    private FindingOverrideMasterService mastersService;
    @Mock
    private OrganizationRepository organizationRepository;

    private final List<GstRuleContext> ruleContexts = new ArrayList<>();
    private final List<GstDaybookFinding> emitted = new ArrayList<>();

    private GstDaybookRule stubRule() {
        return (ctx, vouchers) -> {
            ruleContexts.add(ctx);
            return new ArrayList<>(emitted);
        };
    }

    @Test
    void outward_typesAndCode() {
        GstOutwardAnalyzer analyzer = new GstOutwardAnalyzer(parsedVoucherRepository, findingRepository,
                mastersService, organizationRepository, new GstRuleRunner(List.of(stubRule())),
                new FindingLifecycleService(), mock(TaxLedgerMappingGate.class), mock(TaxLedgerMappingService.class));
        assertThat(analyzer.code()).isEqualTo("GST_OUTWARD");
        assertThat(analyzer.types()).containsExactly(WorkflowType.GSTR1);
    }

    @Test
    void inward_typesAndCode() {
        GstInwardAnalyzer analyzer = new GstInwardAnalyzer(parsedVoucherRepository, findingRepository,
                mastersService, organizationRepository, new GstRuleRunner(List.of(stubRule())),
                new FindingLifecycleService(), mock(TaxLedgerMappingGate.class), mock(TaxLedgerMappingService.class));
        assertThat(analyzer.code()).isEqualTo("GST_INWARD");
        assertThat(analyzer.types()).containsExactly(WorkflowType.GSTR3B);
    }

    @Test
    void outward_savesOnlySalesFindings() {
        ParsedVoucher v = voucher();
        emitted.clear();
        emitted.add(finding(v, VoucherSide.SALES, "Rent"));
        emitted.add(finding(v, VoucherSide.PURCHASE, "Stock"));
        emitted.add(finding(v, VoucherSide.OTHER, "Cash"));
        GstOutwardAnalyzer analyzer = new GstOutwardAnalyzer(parsedVoucherRepository, findingRepository,
                mastersService, organizationRepository, new GstRuleRunner(List.of(stubRule())),
                new FindingLifecycleService(), mock(TaxLedgerMappingGate.class), mock(TaxLedgerMappingService.class));

        analyzer.analyze(ctx(WorkflowType.GSTR1));

        List<GstDaybookFinding> saved = savedFresh();
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getVoucherSide()).isEqualTo(VoucherSide.SALES);
        assertThat(ruleContexts).hasSize(1);
        assertThat(ruleContexts.get(0).orgGstin()).isEqualTo(ORG_GSTIN);
        assertThat(ruleContexts.get(0).workflowId()).isEqualTo(WORKFLOW_ID);
        assertThat(ruleContexts.get(0).uploadJobId()).isEqualTo(UPLOAD_JOB_ID);
    }

    @Test
    void inward_savesPurchaseAndOther() {
        ParsedVoucher v = voucher();
        emitted.clear();
        emitted.add(finding(v, VoucherSide.SALES, "Rent"));
        emitted.add(finding(v, VoucherSide.PURCHASE, "Stock"));
        emitted.add(finding(v, VoucherSide.OTHER, "Cash"));
        GstInwardAnalyzer analyzer = new GstInwardAnalyzer(parsedVoucherRepository, findingRepository,
                mastersService, organizationRepository, new GstRuleRunner(List.of(stubRule())),
                new FindingLifecycleService(), mock(TaxLedgerMappingGate.class), mock(TaxLedgerMappingService.class));

        analyzer.analyze(ctx(WorkflowType.GSTR3B));

        List<GstDaybookFinding> saved = savedFresh();
        assertThat(saved).extracting(GstDaybookFinding::getVoucherSide)
                .containsExactly(VoucherSide.PURCHASE, VoucherSide.OTHER);
    }

    @Test
    void analyze_reanalysisCarriesApprovedAndSupersedesOld() {
        ParsedVoucher v = voucher();
        GstDaybookFinding freshFinding = finding(v, VoucherSide.SALES, "Rent");
        emitted.clear();
        emitted.add(freshFinding);

        String hash = EvidenceHash.of(GstFindingType.GST_NOT_CHARGED.name(),
                EvidenceHash.voucherIdentity(v.getVoucherGuid(), v.getVoucherTypeName(),
                        v.getVoucherNumber(), v.getVoucherDate()),
                "expected", "actual", null);
        GstDaybookFinding previous = new GstDaybookFinding();
        previous.setId(UUID.randomUUID());
        previous.setFindingType(GstFindingType.GST_NOT_CHARGED);
        previous.setSeverity(FindingSeverity.HIGH);
        previous.setVoucherSide(VoucherSide.SALES);
        previous.setVoucherGuid(v.getVoucherGuid());
        previous.setVoucherTypeName(v.getVoucherTypeName());
        previous.setVoucherNumber(v.getVoucherNumber());
        previous.setVoucherDate(v.getVoucherDate());
        previous.setLedgerName("Rent");
        previous.setExpectedValue("expected");
        previous.setActualValue("actual");
        previous.setEvidenceHash(hash);
        previous.setResolveStatus(ResolveStatus.APPROVED);
        when(findingRepository.findByWorkflowIdAndSupersededAtIsNullOrderByVoucherDateAscCreatedAtAsc(WORKFLOW_ID))
                .thenReturn(List.of(previous));

        GstOutwardAnalyzer analyzer = new GstOutwardAnalyzer(parsedVoucherRepository, findingRepository,
                mastersService, organizationRepository, new GstRuleRunner(List.of(stubRule())),
                new FindingLifecycleService(), mock(TaxLedgerMappingGate.class), mock(TaxLedgerMappingService.class));

        analyzer.analyze(ctx(WorkflowType.GSTR1));

        List<GstDaybookFinding> saved = savedFresh();
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getResolveStatus()).isEqualTo(ResolveStatus.APPROVED);
        assertThat(saved.get(0).getCarriedFromFindingId()).isEqualTo(previous.getId());
        assertThat(previous.getSupersededAt()).isNotNull();
    }

    @Test
    void analyze_zeroFreshFindings_supersedesAllPrevious() {
        GstDaybookFinding previous = new GstDaybookFinding();
        previous.setId(UUID.randomUUID());
        previous.setFindingType(GstFindingType.GST_NOT_CHARGED);
        previous.setResolveStatus(ResolveStatus.OPEN);
        emitted.clear();
        when(findingRepository.findByWorkflowIdAndSupersededAtIsNullOrderByVoucherDateAscCreatedAtAsc(WORKFLOW_ID))
                .thenReturn(List.of(previous));

        GstOutwardAnalyzer analyzer = new GstOutwardAnalyzer(parsedVoucherRepository, findingRepository,
                mastersService, organizationRepository, new GstRuleRunner(List.of(stubRule())),
                new FindingLifecycleService(), mock(TaxLedgerMappingGate.class), mock(TaxLedgerMappingService.class));

        analyzer.analyze(ctx(WorkflowType.GSTR1));

        assertThat(previous.getResolveStatus()).isEqualTo(ResolveStatus.RESOLVED);
        assertThat(previous.getResolvedBy()).isEqualTo("system");
        assertThat(previous.getSupersededAt()).isNotNull();
    }

    @Test
    void analyze_nullMastersJobAndNoGstin_runsWithEmptyInputs() {
        emitted.clear();
        Organization org = new Organization();
        org.setGstin(null);
        when(organizationRepository.findById(ORG_ID)).thenReturn(Optional.of(org));
        GstInwardAnalyzer analyzer = new GstInwardAnalyzer(parsedVoucherRepository, findingRepository,
                mastersService, organizationRepository, new GstRuleRunner(List.of(stubRule())),
                new FindingLifecycleService(), mock(TaxLedgerMappingGate.class), mock(TaxLedgerMappingService.class));

        analyzer.analyze(ctx(WorkflowType.GSTR3B));

        verify(mastersService, never()).snapshotsByName(null);
        assertThat(ruleContexts).hasSize(1);
        assertThat(ruleContexts.get(0).orgGstin()).isNull();
        assertThat(ruleContexts.get(0).snapshotsByName()).isEmpty();
        assertThat(savedFresh()).isEmpty();
    }

    private AnalysisContext ctx(WorkflowType type) {
        return new AnalysisContext(WORKFLOW_ID, ORG_ID, type, PERIOD_START, PERIOD_END, UPLOAD_JOB_ID,
                null, RUN_ID);
    }

    private ParsedVoucher voucher() {
        ParsedVoucher v = GstTestFixtures.voucher("Purchase", LocalDate.of(2025, 4, 15), "Acme", ORG_GSTIN);
        when(parsedVoucherRepository.findByUploadJobId(UPLOAD_JOB_ID)).thenReturn(List.of(v));
        when(organizationRepository.findById(ORG_ID)).thenReturn(Optional.of(orgWithGstin()));
        return v;
    }

    private Organization orgWithGstin() {
        Organization org = new Organization();
        org.setGstin(ORG_GSTIN);
        return org;
    }

    private GstDaybookFinding finding(ParsedVoucher v, VoucherSide side, String ledger) {
        GstDaybookFinding f = new GstDaybookFinding();
        f.setFindingType(GstFindingType.GST_NOT_CHARGED);
        f.setSeverity(FindingSeverity.HIGH);
        f.setVoucherSide(side);
        f.setVoucherId(v.getId());
        f.setVoucherGuid(v.getVoucherGuid());
        f.setVoucherTypeName(v.getVoucherTypeName());
        f.setVoucherNumber(v.getVoucherNumber());
        f.setVoucherDate(v.getVoucherDate());
        f.setLedgerName(ledger);
        f.setExpectedValue("expected");
        f.setActualValue("actual");
        f.setMessage("msg");
        return f;
    }

    @SuppressWarnings("unchecked")
    private List<GstDaybookFinding> savedFresh() {
        ArgumentCaptor<List<GstDaybookFinding>> captor = ArgumentCaptor.forClass(List.class);
        verify(findingRepository, org.mockito.Mockito.atLeastOnce()).saveAll(captor.capture());
        List<List<GstDaybookFinding>> all = captor.getAllValues();
        return all.get(all.size() - 1);
    }
}
