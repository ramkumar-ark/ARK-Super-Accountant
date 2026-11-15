package com.arktech.superaccountant.workflow.snapshot;

import com.arktech.superaccountant.login.models.ERole;
import com.arktech.superaccountant.login.models.Role;
import com.arktech.superaccountant.login.models.User;
import com.arktech.superaccountant.login.repository.RoleRepository;
import com.arktech.superaccountant.login.repository.UserRepository;
import com.arktech.superaccountant.masters.models.LedgerCategory;
import com.arktech.superaccountant.masters.models.Organization;
import com.arktech.superaccountant.masters.models.UploadJob;
import com.arktech.superaccountant.masters.models.UploadJobStatus;
import com.arktech.superaccountant.masters.models.UploadLedgerSnapshot;
import com.arktech.superaccountant.masters.models.UserOrganization;
import com.arktech.superaccountant.masters.repository.OrganizationRepository;
import com.arktech.superaccountant.masters.repository.UploadJobRepository;
import com.arktech.superaccountant.masters.repository.UploadLedgerSnapshotRepository;
import com.arktech.superaccountant.masters.repository.UserOrganizationRepository;
import com.arktech.superaccountant.daybook.models.DayBookUploadJob;
import com.arktech.superaccountant.daybook.repository.DayBookUploadJobRepository;
import com.arktech.superaccountant.workflow.models.Workflow;
import com.arktech.superaccountant.workflow.models.WorkflowStage;
import com.arktech.superaccountant.workflow.models.WorkflowType;
import com.arktech.superaccountant.workflow.repository.WorkflowRepository;
import com.arktech.superaccountant.workflow.seam.SnapshotService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@Transactional
@TestPropertySource(properties = "JWT_SECRET=test-jwt-secret-must-be-at-least-32-characters-long")
class MasterAttributeFreezeIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private SnapshotService snapshotService;
    @Autowired
    private OrganizationRepository organizationRepository;
    @Autowired
    private UserOrganizationRepository userOrganizationRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private UploadJobRepository uploadJobRepository;
    @Autowired
    private UploadLedgerSnapshotRepository snapshotRepository;
    @Autowired
    private DayBookUploadJobRepository dayBookUploadJobRepository;
    @Autowired
    private WorkflowRepository workflowRepository;
    @Autowired
    private FrozenMasterAttributeRepository frozenMasterAttributeRepository;

    private Organization newOrg(String name) {
        Role role = roleRepository.findByName(ERole.ROLE_ACCOUNTANT)
                .orElseGet(() -> roleRepository.save(new Role(ERole.ROLE_ACCOUNTANT)));
        User user = new User(name.toLowerCase() + "-acc", "acc-" + UUID.randomUUID() + "@example.com",
                passwordEncoder.encode("TestPass1!"));
        user.setRole(role);
        user = userRepository.save(user);
        Organization org = new Organization(name);
        org.setFinancialYearStart(4);
        org.setBooksStartDate(LocalDate.of(2025, 4, 1));
        org = organizationRepository.save(org);
        UserOrganization membership = new UserOrganization();
        membership.setUser(user);
        membership.setOrganization(org);
        membership.setRole(ERole.ROLE_ACCOUNTANT);
        userOrganizationRepository.save(membership);
        return org;
    }

    private UploadJob completedMastersJob(Organization org, String fileName) {
        UploadJob job = new UploadJob();
        job.setOrganizationId(org.getId());
        job.setFileName(fileName);
        job.setStatus(UploadJobStatus.COMPLETED);
        job.setTotalLedgersParsed(2);
        job.setUploadedBy("acc");
        return uploadJobRepository.saveAndFlush(job);
    }

    private UploadLedgerSnapshot ledger(UUID orgId, UUID jobId, String name, LedgerCategory category,
                                        String gstin, String pan, String tdsSection) {
        UploadLedgerSnapshot s = new UploadLedgerSnapshot();
        s.setOrganizationId(orgId);
        s.setUploadJobId(jobId);
        s.setLedgerName(name);
        s.setCategory(category);
        s.setGstin(gstin);
        s.setPan(pan);
        s.setTdsSection(tdsSection);
        s.setTdsApplicable(tdsSection != null);
        return snapshotRepository.saveAndFlush(s);
    }

    private DayBookUploadJob completedDayBookJob(UUID orgId) {
        DayBookUploadJob job = new DayBookUploadJob();
        job.setOrganizationId(orgId);
        job.setFileName("daybook.xml");
        job.setUploadedBy("acc");
        job.setStatus(UploadJobStatus.COMPLETED);
        return dayBookUploadJobRepository.saveAndFlush(job);
    }

    private Workflow workflow(UUID orgId, WorkflowType type, LocalDate end, WorkflowStage stage,
                              UUID uploadJobId, UUID mastersJobId) {
        Workflow w = new Workflow();
        w.setOrganizationId(orgId);
        w.setType(type);
        w.setPeriodStart(end.minusDays(29));
        w.setPeriodEnd(end);
        w.setPeriodKey(end.toString().substring(0, 7));
        w.setFinancialYear(end.getYear());
        w.setStage(stage);
        w.setActive(true);
        w.setUploadJobId(uploadJobId);
        w.setMastersJobId(mastersJobId);
        w.setCreatedBy("acc");
        return workflowRepository.saveAndFlush(w);
    }

    @Test
    void finalize_freezesInScopeAttributesPerType() {
        Organization org = newOrg("maf-org1");
        UploadJob mastersJob = completedMastersJob(org, "masters-1.json");
        ledger(org.getId(), mastersJob.getId(), "Acme Traders", LedgerCategory.INCOME,
                "29ABCDE1234F1Z5", null, null);
        ledger(org.getId(), mastersJob.getId(), "Contractor A", LedgerCategory.CREDITOR,
                null, "ABCDE1234F", "194C");
        DayBookUploadJob dayBookJob = completedDayBookJob(org.getId());
        Workflow w = workflow(org.getId(), WorkflowType.GSTR1, LocalDate.of(2025, 4, 30),
                WorkflowStage.FINALIZED, dayBookJob.getId(), mastersJob.getId());

        snapshotService.freeze(w.getId(), org.getId(), "acc");

        List<FrozenMasterAttribute> rows = frozenMasterAttributeRepository
                .findByOrganizationIdAndReleasedAtIsNull(org.getId());
        List<FrozenMasterAttribute> acme = rows.stream()
                .filter(r -> r.getLedgerName().equals("Acme Traders")).toList();
        assertThat(acme).hasSize(6);
        assertThat(acme).allSatisfy(r -> {
            assertThat(r.getWorkflowType()).isEqualTo(WorkflowType.GSTR1);
            assertThat(r.getPeriodEnd()).isEqualTo(LocalDate.of(2025, 4, 30));
            assertThat(r.getFrozenByWorkflowId()).isEqualTo(w.getId());
            assertThat(r.getReleasedAt()).isNull();
            assertThat(r.getLedgerKey()).isEqualTo("acme traders");
            assertThat(r.getValidFrom()).isNull();
            assertThat(r.getFrozenAt()).isNotNull();
        });
        assertThat(acme).extracting(FrozenMasterAttribute::getAttribute)
                .containsExactlyInAnyOrder("category", "gstin", "gstRegistrationType",
                        "gstApplicable", "gstApplicabilityType", "hsnSacCode");
        assertThat(acme.stream().filter(r -> r.getAttribute().equals("gstin")).findFirst().orElseThrow()
                .getValue()).isEqualTo("29ABCDE1234F1Z5");
        List<FrozenMasterAttribute> contractor = rows.stream()
                .filter(r -> r.getLedgerName().equals("Contractor A")).toList();
        assertThat(contractor).isEmpty();
    }

    @Test
    void finalize_skipsLedgersOutOfTypeScope() {
        Organization org = newOrg("maf-org2");
        UploadJob mastersJob = completedMastersJob(org, "masters-2.json");
        ledger(org.getId(), mastersJob.getId(), "Contractor A", LedgerCategory.CREDITOR,
                null, "ABCDE1234F", "194C");
        DayBookUploadJob dayBookJob = completedDayBookJob(org.getId());
        Workflow w = workflow(org.getId(), WorkflowType.GSTR1, LocalDate.of(2025, 4, 30),
                WorkflowStage.FINALIZED, dayBookJob.getId(), mastersJob.getId());

        snapshotService.freeze(w.getId(), org.getId(), "acc");

        assertThat(frozenMasterAttributeRepository
                .findByOrganizationIdAndReleasedAtIsNull(org.getId())).isEmpty();
    }

    @Test
    void secondFinalize_extendsPeriodEnd_neverRewritesValue() {
        Organization org = newOrg("maf-org3");
        UploadJob mastersJob1 = completedMastersJob(org, "masters-a.json");
        ledger(org.getId(), mastersJob1.getId(), "Acme Traders", LedgerCategory.INCOME,
                "29ABCDE1234F1Z5", null, null);
        DayBookUploadJob dayBookJob = completedDayBookJob(org.getId());
        Workflow april = workflow(org.getId(), WorkflowType.GSTR1, LocalDate.of(2025, 4, 30),
                WorkflowStage.FINALIZED, dayBookJob.getId(), mastersJob1.getId());
        snapshotService.freeze(april.getId(), org.getId(), "acc");

        UploadJob mastersJob2 = completedMastersJob(org, "masters-b.json");
        ledger(org.getId(), mastersJob2.getId(), "Acme Traders", LedgerCategory.INCOME,
                "29ABCDE1234F1Z6", null, null);
        Workflow may = workflow(org.getId(), WorkflowType.GSTR1, LocalDate.of(2025, 5, 31),
                WorkflowStage.FINALIZED, dayBookJob.getId(), mastersJob2.getId());
        snapshotService.freeze(may.getId(), org.getId(), "acc");

        List<FrozenMasterAttribute> rows = frozenMasterAttributeRepository
                .findByOrganizationIdAndWorkflowTypeAndReleasedAtIsNull(org.getId(), WorkflowType.GSTR1)
                .stream().filter(r -> r.getAttribute().equals("gstin")).toList();
        assertThat(rows).hasSize(1);
        FrozenMasterAttribute row = rows.get(0);
        assertThat(row.getPeriodEnd()).isEqualTo(LocalDate.of(2025, 5, 31));
        assertThat(row.getValue()).isEqualTo("29ABCDE1234F1Z5");
        assertThat(row.getFrozenByWorkflowId()).isEqualTo(may.getId());
    }

    @Test
    void freeze_withoutDayBookUpload_stillFreezesMasters() {
        Organization org = newOrg("maf-org4");
        UploadJob mastersJob = completedMastersJob(org, "masters-4.json");
        ledger(org.getId(), mastersJob.getId(), "Acme Traders", LedgerCategory.INCOME,
                "29ABCDE1234F1Z5", null, null);
        Workflow w = workflow(org.getId(), WorkflowType.GSTR1, LocalDate.of(2025, 4, 30),
                WorkflowStage.FINALIZED, null, mastersJob.getId());

        snapshotService.freeze(w.getId(), org.getId(), "acc");

        assertThat(frozenMasterAttributeRepository
                .findByOrganizationIdAndReleasedAtIsNull(org.getId())).hasSize(6);
    }

    @Test
    void release_afterUnfinalize_dropsRowsBeyondTypeFrozenThrough() {
        Organization org = newOrg("maf-org5");
        UploadJob mastersJob = completedMastersJob(org, "masters-5.json");
        ledger(org.getId(), mastersJob.getId(), "Acme Traders", LedgerCategory.INCOME,
                "29ABCDE1234F1Z5", null, null);
        DayBookUploadJob dayBookJob = completedDayBookJob(org.getId());
        Workflow april = workflow(org.getId(), WorkflowType.GSTR1, LocalDate.of(2025, 4, 30),
                WorkflowStage.FINALIZED, dayBookJob.getId(), mastersJob.getId());
        snapshotService.freeze(april.getId(), org.getId(), "acc");
        Workflow may = workflow(org.getId(), WorkflowType.GSTR1, LocalDate.of(2025, 5, 31),
                WorkflowStage.FINALIZED, dayBookJob.getId(), mastersJob.getId());
        snapshotService.freeze(may.getId(), org.getId(), "acc");

        // Un-finalize May: the workflow is already back in PREPARE when release runs.
        may.setStage(WorkflowStage.PREPARE);
        workflowRepository.saveAndFlush(may);
        snapshotService.release(may.getId(), org.getId(), "acc");

        List<FrozenMasterAttribute> gstinRows = frozenMasterAttributeRepository
                .findByOrganizationIdAndWorkflowTypeAndReleasedAtIsNull(org.getId(), WorkflowType.GSTR1)
                .stream().filter(r -> r.getAttribute().equals("gstin")).toList();
        assertThat(gstinRows).hasSize(1);
        assertThat(gstinRows.get(0).getPeriodEnd()).isEqualTo(LocalDate.of(2025, 4, 30));

        // Un-finalize April too: nothing of the type is frozen any more, so rows release.
        april.setStage(WorkflowStage.PREPARE);
        workflowRepository.saveAndFlush(april);
        snapshotService.release(april.getId(), org.getId(), "acc");

        List<FrozenMasterAttribute> released = frozenMasterAttributeRepository
                .findByOrganizationIdAndWorkflowTypeAndReleasedAtIsNull(org.getId(), WorkflowType.GSTR1);
        assertThat(released).isEmpty();
        List<FrozenMasterAttribute> all = frozenMasterAttributeRepository
                .findByOrganizationIdAndReleasedAtIsNull(org.getId());
        assertThat(all).isEmpty();
        List<FrozenMasterAttribute> everyRow = frozenMasterAttributeRepository.findAll();
        assertThat(everyRow).allSatisfy(r -> assertThat(r.getReleasedAt()).isNotNull());
    }
}
