package com.arktech.superaccountant.workflow.findings;

import com.arktech.superaccountant.login.models.ERole;
import com.arktech.superaccountant.login.models.Role;
import com.arktech.superaccountant.login.models.User;
import com.arktech.superaccountant.login.repository.RoleRepository;
import com.arktech.superaccountant.login.repository.UserRepository;
import com.arktech.superaccountant.login.security.services.UserDetailsImpl;
import com.arktech.superaccountant.masters.models.Organization;
import com.arktech.superaccountant.masters.models.ResolveStatus;
import com.arktech.superaccountant.masters.repository.OrganizationRepository;
import com.arktech.superaccountant.review.FindingApplier;
import com.arktech.superaccountant.review.ReviewableFinding;
import com.arktech.superaccountant.workflow.models.Workflow;
import com.arktech.superaccountant.workflow.models.WorkflowStage;
import com.arktech.superaccountant.workflow.models.WorkflowType;
import com.arktech.superaccountant.workflow.repository.WorkflowRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end tests (real Postgres via Testcontainers) of the workflow findings API over a
 * test-only fake {@link WorkflowFindingSource}; wave 2 registers no GST or TDS sources.
 * Needs Docker.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = "JWT_SECRET=test-jwt-secret-must-be-at-least-32-characters-long")
class WorkflowFindingsControllerIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    static class FakeFinding implements ReviewableFinding {
        final UUID id;
        final boolean blocking;
        final LocalDate voucherDate;
        volatile ResolveStatus resolveStatus = ResolveStatus.OPEN;
        volatile String proposedValue;
        volatile String submittedBy;
        volatile String reviewedBy;
        volatile String reviewNote;

        FakeFinding(UUID id, boolean blocking, LocalDate voucherDate) {
            this.id = id;
            this.blocking = blocking;
            this.voucherDate = voucherDate;
        }

        /** A working copy: review actions mutate the copy; only save() commits it back. */
        FakeFinding copy() {
            FakeFinding c = new FakeFinding(id, blocking, voucherDate);
            c.resolveStatus = resolveStatus;
            c.proposedValue = proposedValue;
            c.submittedBy = submittedBy;
            c.reviewedBy = reviewedBy;
            c.reviewNote = reviewNote;
            return c;
        }

        void commit(FakeFinding c) {
            resolveStatus = c.resolveStatus;
            proposedValue = c.proposedValue;
            submittedBy = c.submittedBy;
            reviewedBy = c.reviewedBy;
            reviewNote = c.reviewNote;
        }

        @Override
        public UUID getId() {
            return id;
        }

        @Override
        public ResolveStatus getResolveStatus() {
            return resolveStatus;
        }

        @Override
        public void setResolveStatus(ResolveStatus s) {
            resolveStatus = s;
        }

        @Override
        public String getResolveNote() {
            return null;
        }

        @Override
        public void setResolveNote(String s) {
        }

        @Override
        public String getProposedValue() {
            return proposedValue;
        }

        @Override
        public void setProposedValue(String s) {
            proposedValue = s;
        }

        @Override
        public String getSubmittedBy() {
            return submittedBy;
        }

        @Override
        public void setSubmittedBy(String s) {
            submittedBy = s;
        }

        @Override
        public java.time.Instant getSubmittedAt() {
            return null;
        }

        @Override
        public void setSubmittedAt(java.time.Instant t) {
        }

        @Override
        public String getReviewedBy() {
            return reviewedBy;
        }

        @Override
        public void setReviewedBy(String s) {
            reviewedBy = s;
        }

        @Override
        public java.time.Instant getReviewedAt() {
            return null;
        }

        @Override
        public void setReviewedAt(java.time.Instant t) {
        }

        @Override
        public String getReviewNote() {
            return reviewNote;
        }

        @Override
        public void setReviewNote(String s) {
            reviewNote = s;
        }

        @Override
        public String getResolvedBy() {
            return null;
        }

        @Override
        public void setResolvedBy(String s) {
        }

        @Override
        public java.time.Instant getResolvedAt() {
            return null;
        }

        @Override
        public void setResolvedAt(java.time.Instant t) {
        }
    }

    @TestConfiguration
    static class FakeSourceConfig {
        static final Map<UUID, FakeFinding> FINDINGS = new ConcurrentHashMap<>();
        static volatile boolean failApply;
        static volatile Integer completionStatus;

        @Bean
        @Primary
        WorkflowFindingSource fakeWorkflowFindingSource() {
            return new WorkflowFindingSource() {
                @Override
                public String sourceCode() {
                    return "FAKE";
                }

                @Override
                public boolean appliesTo(Workflow w) {
                    return true;
                }

                @Override
                public long blockingCount(Workflow w) {
                    return FINDINGS.values().stream()
                            .filter(f -> f.getResolveStatus() != ResolveStatus.APPROVED
                                    && f.getResolveStatus() != ResolveStatus.RESOLVED
                                    && f.getResolveStatus() != ResolveStatus.DISCARDED)
                            .count();
                }

                @Override
                public List<WorkflowFindingView> list(Workflow w) {
                    return FINDINGS.values().stream()
                            .sorted(Comparator.comparing(f -> f.id))
                            .map(this::view)
                            .toList();
                }

                @Override
                public Optional<ReviewableFinding> lockForUpdate(Workflow w, UUID findingId) {
                    return Optional.ofNullable(FINDINGS.get(findingId)).map(FakeFinding::copy);
                }

                @Override
                public FindingApplier<ReviewableFinding> applier(Workflow w) {
                    return new FindingApplier<>() {
                        @Override
                        public void validate(ReviewableFinding finding, String proposedValue, String note) {
                        }

                        @Override
                        public void apply(ReviewableFinding finding, String approver) {
                            if (failApply) {
                                org.springframework.transaction.support.TransactionSynchronizationManager
                                        .registerSynchronization(
                                                new org.springframework.transaction.support.TransactionSynchronization() {
                                                    @Override
                                                    public void afterCompletion(int status) {
                                                        completionStatus = status;
                                                    }
                                                });
                                throw new com.arktech.superaccountant.masters.services.FindingOverrideException(
                                        org.springframework.http.HttpStatus.CONFLICT, "stale");
                            }
                        }
                    };
                }

                @Override
                public void save(ReviewableFinding f) {
                    FINDINGS.get(((FakeFinding) f).id).commit((FakeFinding) f);
                }

                @Override
                public WorkflowFindingView view(ReviewableFinding f) {
                    FakeFinding ff = (FakeFinding) f;
                    return new WorkflowFindingView(ff.id, "FAKE", "FAKE_TYPE", "ERROR", ff.blocking,
                            "message", null, null, "V1", ff.voucherDate, "Ledger", ff.getResolveStatus(),
                            null, ff.getProposedValue(), ff.getSubmittedBy(), null, ff.getReviewedBy(),
                            null, ff.getReviewNote(), null, null, null);
                }
            };
        }
    }

    @Autowired
    MockMvc mockMvc;
    @Autowired
    OrganizationRepository organizationRepository;
    @Autowired
    RoleRepository roleRepository;
    @Autowired
    UserRepository userRepository;
    @Autowired
    WorkflowRepository workflowRepository;

    UUID orgId;
    Workflow workflow;
    UserDetailsImpl operator;
    UserDetailsImpl accountant;
    UserDetailsImpl auditor;

    @BeforeEach
    void seed() {
        FakeSourceConfig.FINDINGS.clear();
        FakeSourceConfig.failApply = false;
        FakeSourceConfig.completionStatus = null;
        Organization org = new Organization();
        org.setName("Findings IT " + UUID.randomUUID());
        org.setBooksStartDate(LocalDate.of(2025, 4, 1));
        orgId = organizationRepository.save(org).getId();

        workflow = new Workflow();
        workflow.setOrganizationId(orgId);
        workflow.setType(WorkflowType.GSTR1);
        workflow.setPeriodStart(LocalDate.of(2025, 4, 1));
        workflow.setPeriodEnd(LocalDate.of(2025, 4, 30));
        workflow.setPeriodKey("2025-04");
        workflow.setFinancialYear(2025);
        workflow.setStage(WorkflowStage.FINDINGS_REVIEW);
        workflow.setCreatedBy("it");
        workflowRepository.save(workflow);

        operator = principal("op", ERole.ROLE_OPERATOR);
        accountant = principal("acct", ERole.ROLE_ACCOUNTANT);
        auditor = principal("aud", ERole.ROLE_AUDITOR_CA);
    }

    private UserDetailsImpl principal(String name, ERole roleName) {
        Role role = roleRepository.findByName(roleName).orElseThrow();
        User u = new User();
        u.setUsername(name + "-" + UUID.randomUUID());
        u.setPassword("x");
        u.setEmail(name + "-" + UUID.randomUUID() + "@t.com");
        u.setRole(role);
        return UserDetailsImpl.build(userRepository.save(u), orgId);
    }

    private FakeFinding seedFinding(boolean blocking) {
        FakeFinding f = new FakeFinding(UUID.randomUUID(), blocking, LocalDate.of(2025, 4, 5));
        FakeSourceConfig.FINDINGS.put(f.id, f);
        return f;
    }

    @Test
    void listFindings_returnsSourceFindingsAndBlockingCount() throws Exception {
        FakeFinding blocking = seedFinding(true);
        seedFinding(false); // non-blocking, also OPEN -> still counts as blocking; add a terminal one
        FakeFinding resolved = seedFinding(false);
        resolved.resolveStatus = ResolveStatus.RESOLVED;

        mockMvc.perform(get("/api/v1/workflows/" + workflow.getId() + "/findings").with(user(accountant)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blockingCount").value(2))
                .andExpect(jsonPath("$.findings.length()").value(3))
                .andExpect(jsonPath("$.findings[0].source").value("FAKE"));

        assertThat(blocking.resolveStatus).isEqualTo(ResolveStatus.OPEN);
    }

    @Test
    void action_operatorAcknowledge_thenAccountantApprove() throws Exception {
        FakeFinding f = seedFinding(true);

        mockMvc.perform(post("/api/v1/workflows/" + workflow.getId() + "/findings/" + f.id + "/action")
                        .with(user(operator))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"ACKNOWLEDGE\",\"value\":\"V\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resolveStatus").value("ACKNOWLEDGED"));
        assertThat(f.resolveStatus).isEqualTo(ResolveStatus.ACKNOWLEDGED);

        mockMvc.perform(post("/api/v1/workflows/" + workflow.getId() + "/findings/" + f.id + "/action")
                        .with(user(accountant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"APPROVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resolveStatus").value("APPROVED"));
        assertThat(f.resolveStatus).isEqualTo(ResolveStatus.APPROVED);
    }

    @Test
    void action_auditorCa_403() throws Exception {
        FakeFinding f = seedFinding(true);

        mockMvc.perform(post("/api/v1/workflows/" + workflow.getId() + "/findings/" + f.id + "/action")
                        .with(user(auditor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"ACKNOWLEDGE\",\"value\":\"V\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Only OPERATOR or ACCOUNTANT can resolve findings."));
        assertThat(f.resolveStatus).isEqualTo(ResolveStatus.OPEN);
    }

    @Test
    void action_unknownFinding_404() throws Exception {
        mockMvc.perform(post("/api/v1/workflows/" + workflow.getId() + "/findings/" + UUID.randomUUID() + "/action")
                        .with(user(accountant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"APPROVE\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Finding not found"));
    }

    @Test
    void bulkApprove_oneFails_409AndNothingChanged() throws Exception {
        FakeFinding acknowledged = seedFinding(true);
        acknowledged.resolveStatus = ResolveStatus.ACKNOWLEDGED;
        FakeFinding open = seedFinding(true); // APPROVE on OPEN fails

        mockMvc.perform(post("/api/v1/workflows/" + workflow.getId() + "/findings/bulk-action")
                        .with(user(accountant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"findingIds\":[\"" + acknowledged.id + "\",\"" + open.id
                                + "\"],\"action\":\"APPROVE\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.failures.length()").value(1));

        assertThat(acknowledged.resolveStatus).isEqualTo(ResolveStatus.ACKNOWLEDGED); // all-or-nothing
        assertThat(open.resolveStatus).isEqualTo(ResolveStatus.OPEN);
    }

    @Test
    void action_applierFailure_returns409AndRollsBackTransaction() throws Exception {
        FakeFinding f = seedFinding(true);
        FakeSourceConfig.failApply = true;

        mockMvc.perform(post("/api/v1/workflows/" + workflow.getId() + "/findings/" + f.id + "/action")
                        .with(user(accountant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"ACKNOWLEDGE\",\"value\":\"V\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("stale"));

        assertThat(FakeSourceConfig.completionStatus)
                .isEqualTo(org.springframework.transaction.support.TransactionSynchronization.STATUS_ROLLED_BACK);
        assertThat(f.resolveStatus).isEqualTo(ResolveStatus.OPEN);
    }
}
