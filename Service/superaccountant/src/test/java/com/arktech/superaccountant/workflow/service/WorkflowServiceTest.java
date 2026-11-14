package com.arktech.superaccountant.workflow.service;

import com.arktech.superaccountant.masters.models.Organization;
import com.arktech.superaccountant.masters.repository.OrganizationRepository;
import com.arktech.superaccountant.workflow.models.FilerFrequency;
import com.arktech.superaccountant.workflow.models.FilerProfile;
import com.arktech.superaccountant.workflow.models.FinancialYearClose;
import com.arktech.superaccountant.workflow.models.Workflow;
import com.arktech.superaccountant.workflow.models.WorkflowStage;
import com.arktech.superaccountant.workflow.models.WorkflowType;
import com.arktech.superaccountant.workflow.repository.FilerProfileRepository;
import com.arktech.superaccountant.workflow.repository.FinancialYearCloseRepository;
import com.arktech.superaccountant.workflow.repository.WorkflowEventRepository;
import com.arktech.superaccountant.workflow.repository.WorkflowRepository;
import com.arktech.superaccountant.workflow.seam.MastersJobGate;
import com.arktech.superaccountant.workflow.seam.ResponsibilityStartService;
import com.arktech.superaccountant.workflow.seam.SnapshotService;
import com.arktech.superaccountant.workflow.seam.UploadFreshnessGate;
import com.arktech.superaccountant.workflow.seam.WorkflowGate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

class WorkflowServiceTest {

    private WorkflowRepository workflowRepository;
    private WorkflowEventRepository workflowEventRepository;
    private FilerProfileRepository filerProfileRepository;
    private FinancialYearCloseRepository financialYearCloseRepository;
    private OrganizationRepository organizationRepository;
    private WorkflowGate workflowGate;
    private MastersJobGate mastersJobGate;
    private UploadFreshnessGate uploadFreshnessGate;
    private ResponsibilityStartService responsibilityStartService;
    private WorkflowService service;

    private final UUID orgId = UUID.randomUUID();
    private final String actor = "tester";

    @BeforeEach
    void setUp() {
        workflowRepository = Mockito.mock(WorkflowRepository.class);
        workflowEventRepository = Mockito.mock(WorkflowEventRepository.class);
        filerProfileRepository = Mockito.mock(FilerProfileRepository.class);
        financialYearCloseRepository = Mockito.mock(FinancialYearCloseRepository.class);
        organizationRepository = Mockito.mock(OrganizationRepository.class);
        workflowGate = Mockito.mock(WorkflowGate.class);
        mastersJobGate = Mockito.mock(MastersJobGate.class);
        uploadFreshnessGate = Mockito.mock(UploadFreshnessGate.class);
        responsibilityStartService = Mockito.mock(ResponsibilityStartService.class);

        CoverageService coverageService = new CoverageService();
        try {
            var f = CoverageService.class.getDeclaredField("organizationRepository");
            f.setAccessible(true);
            f.set(coverageService, organizationRepository);
            var f2 = CoverageService.class.getDeclaredField("financialYearCloseRepository");
            f2.setAccessible(true);
            f2.set(coverageService, financialYearCloseRepository);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        // inject via reflection-free approach: use Mockito spies on the real service
        service = new WorkflowService();
        inject("workflowRepository", workflowRepository);
        inject("workflowEventRepository", workflowEventRepository);
        inject("filerProfileRepository", filerProfileRepository);
        inject("financialYearCloseRepository", financialYearCloseRepository);
        inject("organizationRepository", organizationRepository);
        inject("workflowGate", workflowGate);
        inject("mastersJobGate", mastersJobGate);
        inject("uploadFreshnessGate", uploadFreshnessGate);
        inject("responsibilityStartService", responsibilityStartService);
        inject("snapshotService", new SnapshotService());
        inject("coverageService", coverageService);

        Organization org = new Organization("Test Org");
        org.setFinancialYearStart(4);
        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(org));
        lenient().when(financialYearCloseRepository
                .findAllByOrganizationIdOrderByFinancialYearAsc(orgId)).thenReturn(List.of());
        lenient().when(responsibilityStartService.responsibilityStart(orgId))
                .thenReturn(Optional.empty());
        lenient().when(filerProfileRepository
                .findFirstByOrganizationIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                        eq(orgId), any()))
                .thenReturn(Optional.empty());
        lenient().when(workflowEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(workflowRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private void inject(String field, Object value) {
        try {
            var f = WorkflowService.class.getDeclaredField(field);
            f.setAccessible(true);
            f.set(service, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    // ---------- start ----------

    @Test
    void start_createsStartedWorkflow() {
        when(workflowRepository.findByOrganizationIdAndTypeAndPeriodKeyAndActiveTrue(
                orgId, "GSTR1", "2026-04")).thenReturn(Optional.empty());
        when(workflowRepository.existsFinalized(orgId, "GSTR1", "2026-03")).thenReturn(false);

        Workflow w = service.start(orgId, actor, WorkflowType.GSTR1, "2026-04");

        assertEquals(WorkflowStage.STARTED, w.getStage());
        assertTrue(w.isActive());
        assertEquals(LocalDate.of(2026, 4, 1), w.getPeriodStart());
        assertEquals(LocalDate.of(2026, 4, 30), w.getPeriodEnd());
        assertEquals(2026, w.getFinancialYear());
        assertEquals(0, w.getRevision());
    }

    @Test
    void start_rejectsDuplicateActiveWorkflow() {
        Workflow existing = new Workflow();
        existing.setId(UUID.randomUUID());
        existing.setOrganizationId(orgId);
        existing.setType(WorkflowType.GSTR1);
        existing.setPeriodKey("2026-04");
        existing.setStage(WorkflowStage.STARTED);
        existing.setActive(true);
        when(workflowRepository.findByOrganizationIdAndTypeAndPeriodKeyAndActiveTrue(
                orgId, "GSTR1", "2026-04")).thenReturn(Optional.of(existing));

        assertThrows(WorkflowConflictException.class,
                () -> service.start(orgId, actor, WorkflowType.GSTR1, "2026-04"));
    }

    @Test
    void start_supersedesFinalizedWorkflowAsAmendment() {
        Workflow existing = new Workflow();
        UUID prevId = UUID.randomUUID();
        existing.setId(prevId);
        existing.setOrganizationId(orgId);
        existing.setType(WorkflowType.GSTR1);
        existing.setPeriodKey("2026-04");
        existing.setStage(WorkflowStage.FINALIZED);
        existing.setActive(true);
        existing.setRevision(0);
        when(workflowRepository.findByOrganizationIdAndTypeAndPeriodKeyAndActiveTrue(
                orgId, "GSTR1", "2026-04")).thenReturn(Optional.of(existing));

        Workflow w = service.start(orgId, actor, WorkflowType.GSTR1, "2026-04");

        assertEquals(1, w.getRevision());
        assertEquals(prevId, w.getSupersedesWorkflowId());
        assertFalse(existing.isActive());
    }

    @Test
    void start_rejectsBeforeResponsibilityStart() {
        when(responsibilityStartService.responsibilityStart(orgId))
                .thenReturn(Optional.of(LocalDate.of(2026, 5, 1)));
        assertThrows(WorkflowBadRequestException.class,
                () -> service.start(orgId, actor, WorkflowType.GSTR1, "2026-04"));
    }

    @Test
    void start_rejectsWrongGrainForType() {
        assertThrows(WorkflowBadRequestException.class,
                () -> service.start(orgId, actor, WorkflowType.TDS_26Q, "2026-04"));
        assertThrows(WorkflowBadRequestException.class,
                () -> service.start(orgId, actor, WorkflowType.TDS_PAYMENT, "2026-Q1"));
    }

    @Test
    void start_enforcesQrmpGrainFromProfile() {
        FilerProfile profile = new FilerProfile();
        profile.setFrequency(FilerFrequency.QRMP);
        profile.setIffOptIn(false);
        when(filerProfileRepository
                .findFirstByOrganizationIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                        eq(orgId), any()))
                .thenReturn(Optional.of(profile));

        // GSTR3B must be quarterly under QRMP
        assertThrows(WorkflowBadRequestException.class,
                () -> service.start(orgId, actor, WorkflowType.GSTR3B, "2026-04"));

        // GSTR1 stays monthly without IFF opt-in
        when(workflowRepository.findByOrganizationIdAndTypeAndPeriodKeyAndActiveTrue(
                orgId, "GSTR1", "2026-04")).thenReturn(Optional.empty());
        assertDoesNotThrow(() -> service.start(orgId, actor, WorkflowType.GSTR1, "2026-04"));
    }

    // ---------- finalize / dependencies ----------

    private Workflow prepareWorkflow(WorkflowType type, String periodKey) {
        Workflow w = new Workflow();
        w.setId(UUID.randomUUID());
        w.setOrganizationId(orgId);
        w.setType(type);
        w.setPeriodKey(periodKey);
        w.setPeriodStart(LocalDate.of(2026, 4, 1));
        w.setPeriodEnd(LocalDate.of(2026, 4, 30));
        w.setStage(WorkflowStage.PREPARE);
        w.setActive(true);
        return w;
    }

    @Test
    void finalize_blocksOnBlockingFindings() {
        Workflow w = prepareWorkflow(WorkflowType.GSTR1, "2026-04");
        when(workflowRepository.findById(w.getId())).thenReturn(Optional.of(w));
        when(workflowGate.blockingFindingCount(w.getId())).thenReturn(2);

        assertThrows(WorkflowConflictException.class,
                () -> service.finalize(orgId, actor, w.getId()));
    }

    @Test
    void finalize_enforcesHardDependencies() {
        Workflow w = prepareWorkflow(WorkflowType.GSTR3B, "2026-04");
        when(workflowRepository.findById(w.getId())).thenReturn(Optional.of(w));
        when(workflowGate.blockingFindingCount(w.getId())).thenReturn(0);
        when(workflowRepository.existsFinalized(orgId, "GSTR1", "2026-04")).thenReturn(false);
        when(workflowRepository.existsFinalized(orgId, "GSTR3B", "2026-03")).thenReturn(false);

        assertThrows(WorkflowConflictException.class,
                () -> service.finalize(orgId, actor, w.getId()));

        // with both deps satisfied, finalize succeeds
        when(workflowRepository.existsFinalized(orgId, "GSTR1", "2026-04")).thenReturn(true);
        when(workflowRepository.existsFinalized(orgId, "GSTR3B", "2026-03")).thenReturn(true);

        Workflow finalized = service.finalize(orgId, actor, w.getId());
        assertEquals(WorkflowStage.FINALIZED, finalized.getStage());
        assertEquals(LocalDate.of(2026, 4, 30), finalized.getFrozenThrough());
    }

    @Test
    void finalize_26qRequiresPaymentMonths() {
        Workflow w = prepareWorkflow(WorkflowType.TDS_26Q, "2026-Q1");
        when(workflowRepository.findById(w.getId())).thenReturn(Optional.of(w));
        when(workflowGate.blockingFindingCount(w.getId())).thenReturn(0);
        for (String month : List.of("2026-04", "2026-05", "2026-06")) {
            when(workflowRepository.existsFinalized(orgId, "TDS_PAYMENT", month))
                    .thenReturn(month.equals("2026-06") ? false : true);
        }

        assertThrows(WorkflowConflictException.class,
                () -> service.finalize(orgId, actor, w.getId()));
    }

    @Test
    void dependencies_treatClosedFyPeriodsAsFinalized() {
        FinancialYearClose close = new FinancialYearClose();
        close.setFinancialYear(2025);
        when(financialYearCloseRepository
                .findAllByOrganizationIdOrderByFinancialYearAsc(orgId))
                .thenReturn(List.of(close));

        // 2026-03 belongs to FY2025, which is closed → satisfied even without a workflow
        Workflow w = prepareWorkflow(WorkflowType.GSTR1, "2026-04");
        when(workflowRepository.existsFinalized(orgId, "GSTR1", "2026-03")).thenReturn(false);
        var deps = service.evaluateDependencies(w);
        assertTrue(deps.get(deps.size() - 1).satisfied());

        // a period inside the open FY with no finalized workflow is not satisfied
        Workflow w2 = prepareWorkflow(WorkflowType.GSTR1, "2026-07");
        when(workflowRepository.existsFinalized(orgId, "GSTR1", "2026-06")).thenReturn(false);
        var deps2 = service.evaluateDependencies(w2);
        assertFalse(deps2.get(deps2.size() - 1).satisfied());
    }

    // ---------- unfinalize ----------

    @Test
    void unfinalize_requiresReason() {
        assertThrows(WorkflowBadRequestException.class,
                () -> service.unfinalize(orgId, actor, UUID.randomUUID(), "  "));
    }

    @Test
    void unfinalize_onlyFromFinalizedStage() {
        Workflow w = prepareWorkflow(WorkflowType.GSTR1, "2026-04");
        w.setStage(WorkflowStage.PREPARE);
        when(workflowRepository.findById(w.getId())).thenReturn(Optional.of(w));
        when(workflowRepository.findAllByOrganizationIdOrderByCreatedAtDesc(orgId))
                .thenReturn(List.of(w));

        assertThrows(WorkflowConflictException.class,
                () -> service.unfinalize(orgId, actor, w.getId(), "fix something"));
    }

    @Test
    void unfinalize_blocksWhenAnotherFinalizedOfTypeExists() {
        Workflow w = prepareWorkflow(WorkflowType.GSTR1, "2026-04");
        w.setStage(WorkflowStage.FINALIZED);
        when(workflowRepository.findById(w.getId())).thenReturn(Optional.of(w));

        Workflow other = prepareWorkflow(WorkflowType.GSTR1, "2026-05");
        other.setStage(WorkflowStage.FILED);
        when(workflowRepository.findAllByOrganizationIdOrderByCreatedAtDesc(orgId))
                .thenReturn(List.of(w, other));

        assertThrows(WorkflowConflictException.class,
                () -> service.unfinalize(orgId, actor, w.getId(), "fix"));
    }

    @Test
    void unfinalize_succeedsForLatestFinalizedWithoutDependents() {
        Workflow w = prepareWorkflow(WorkflowType.GSTR1, "2026-04");
        w.setStage(WorkflowStage.FINALIZED);
        when(workflowRepository.findById(w.getId())).thenReturn(Optional.of(w));
        when(workflowRepository.findAllByOrganizationIdOrderByCreatedAtDesc(orgId))
                .thenReturn(List.of(w));

        Workflow result = service.unfinalize(orgId, actor, w.getId(), "fix something");
        assertEquals(WorkflowStage.PREPARE, result.getStage());
        assertNull(result.getFinalizedAt());
        assertNull(result.getFrozenThrough());
    }

    @Test
    void unfinalize_blocksWhenFinalizedDependentExists() {
        Workflow gstr1 = prepareWorkflow(WorkflowType.GSTR1, "2026-04");
        gstr1.setStage(WorkflowStage.FINALIZED);
        when(workflowRepository.findById(gstr1.getId())).thenReturn(Optional.of(gstr1));

        Workflow gstr3b = prepareWorkflow(WorkflowType.GSTR3B, "2026-04");
        gstr3b.setStage(WorkflowStage.FINALIZED);
        when(workflowRepository.findById(gstr3b.getId())).thenReturn(Optional.of(gstr3b));
        when(workflowRepository.findAllByOrganizationIdOrderByCreatedAtDesc(orgId))
                .thenReturn(List.of(gstr1, gstr3b));
        when(workflowRepository.existsFinalized(orgId, "GSTR1", "2026-04")).thenReturn(true);

        assertThrows(WorkflowConflictException.class,
                () -> service.unfinalize(orgId, actor, gstr1.getId(), "fix"));
    }

    // ---------- filing ----------

    @Test
    void recordFiling_requiresRefAndDate() {
        assertThrows(WorkflowBadRequestException.class,
                () -> service.recordFiling(orgId, actor, UUID.randomUUID(), null,
                        LocalDate.of(2026, 5, 20)));
        assertThrows(WorkflowBadRequestException.class,
                () -> service.recordFiling(orgId, actor, UUID.randomUUID(), "REF",
                        null));
    }

    @Test
    void recordFiling_onlyFromFinalized() {
        Workflow w = prepareWorkflow(WorkflowType.GSTR1, "2026-04");
        when(workflowRepository.findById(w.getId())).thenReturn(Optional.of(w));

        assertThrows(WorkflowConflictException.class,
                () -> service.recordFiling(orgId, actor, w.getId(), "REF",
                        LocalDate.of(2026, 5, 20)));
    }

    @Test
    void recordFiling_setsFiledFields() {
        Workflow w = prepareWorkflow(WorkflowType.GSTR1, "2026-04");
        w.setStage(WorkflowStage.FINALIZED);
        when(workflowRepository.findById(w.getId())).thenReturn(Optional.of(w));

        Workflow filed = service.recordFiling(orgId, actor, w.getId(), "ARN123",
                LocalDate.of(2026, 5, 20));
        assertEquals(WorkflowStage.FILED, filed.getStage());
        assertEquals("ARN123", filed.getFilingRef());
        assertEquals(actor, filed.getFiledBy());
    }

    // ---------- advance ----------

    @Test
    void advance_requiresFindingsReviewAndBaseline() {
        Workflow w = prepareWorkflow(WorkflowType.GSTR1, "2026-04");
        w.setStage(WorkflowStage.DATA_UPLOADED);
        when(workflowRepository.findById(w.getId())).thenReturn(Optional.of(w));

        assertThrows(WorkflowConflictException.class,
                () -> service.advanceToPrepare(orgId, actor, w.getId()));

        w.setStage(WorkflowStage.FINDINGS_REVIEW);
        assertThrows(WorkflowConflictException.class,
                () -> service.advanceToPrepare(orgId, actor, w.getId()));

        w.setBaselineConfirmedAt(java.time.Instant.now());
        assertDoesNotThrow(() -> service.advanceToPrepare(orgId, actor, w.getId()));
    }

    @Test
    void advance_validatesMastersJobIsLatest() {
        Workflow w = prepareWorkflow(WorkflowType.GSTR1, "2026-04");
        w.setStage(WorkflowStage.FINDINGS_REVIEW);
        w.setBaselineConfirmedAt(java.time.Instant.now());
        w.setMastersJobId(UUID.randomUUID());
        when(workflowRepository.findById(w.getId())).thenReturn(Optional.of(w));
        when(mastersJobGate.latestCompletedMastersJob(orgId))
                .thenReturn(Optional.of(UUID.randomUUID()));

        assertThrows(WorkflowConflictException.class,
                () -> service.advanceToPrepare(orgId, actor, w.getId()));
    }
}
