package com.arktech.superaccountant.workflow.analysis;

import com.arktech.superaccountant.login.models.ERole;
import com.arktech.superaccountant.login.models.Role;
import com.arktech.superaccountant.login.models.User;
import com.arktech.superaccountant.login.repository.RoleRepository;
import com.arktech.superaccountant.login.repository.UserRepository;
import com.arktech.superaccountant.login.security.services.UserDetailsImpl;
import com.arktech.superaccountant.masters.models.Organization;
import com.arktech.superaccountant.masters.repository.OrganizationRepository;
import com.arktech.superaccountant.workflow.events.WorkflowUploadBoundEvent;
import com.arktech.superaccountant.workflow.models.Workflow;
import com.arktech.superaccountant.workflow.models.WorkflowEvent;
import com.arktech.superaccountant.workflow.models.WorkflowEventKind;
import com.arktech.superaccountant.workflow.models.WorkflowStage;
import com.arktech.superaccountant.workflow.models.WorkflowType;
import com.arktech.superaccountant.workflow.repository.WorkflowEventRepository;
import com.arktech.superaccountant.workflow.repository.WorkflowRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end tests (real Postgres via Testcontainers) of the DATA_UPLOADED → FINDINGS_REVIEW
 * transition. NOT @Transactional: the AFTER_COMMIT listener and REQUIRES_NEW run rows only
 * fire when the publishing transaction really commits. Each test uses its own organization,
 * so no cleanup is needed between tests. Needs Docker.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = "JWT_SECRET=test-jwt-secret-must-be-at-least-32-characters-long")
class WorkflowAnalysisIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    /** Fails on the first analyze call, succeeds afterwards: proves the endpoint is the retry path. */
    @TestConfiguration
    static class FailOnceConfig {
        static final AtomicInteger CALLS = new AtomicInteger();

        @Bean
        WorkflowAnalyzer failOnceAnalyzer() {
            return new WorkflowAnalyzer() {
                @Override
                public String code() {
                    return "FAIL_ONCE";
                }

                @Override
                public java.util.Set<WorkflowType> types() {
                    return java.util.Set.of(WorkflowType.GSTR1);
                }

                @Override
                public void analyze(AnalysisContext ctx) {
                    if (CALLS.incrementAndGet() == 1) {
                        throw new IllegalStateException("boom");
                    }
                }
            };
        }
    }

    @Autowired
    WorkflowRepository workflowRepository;
    @Autowired
    WorkflowEventRepository eventRepository;
    @Autowired
    WorkflowAnalysisRunRepository runRepository;
    @Autowired
    OrganizationRepository organizationRepository;
    @Autowired
    RoleRepository roleRepository;
    @Autowired
    UserRepository userRepository;
    @Autowired
    PlatformTransactionManager txManager;
    @Autowired
    ApplicationEventPublisher eventPublisher;
    @Autowired
    MockMvc mockMvc;

    UUID orgId;
    UserDetailsImpl principal;

    @BeforeEach
    void seed() {
        Organization org = new Organization();
        org.setName("Analysis IT " + UUID.randomUUID());
        org.setBooksStartDate(LocalDate.of(2025, 4, 1));
        orgId = organizationRepository.save(org).getId();

        Role role = roleRepository.findByName(ERole.ROLE_ACCOUNTANT).orElseThrow();
        User user = new User();
        user.setUsername("acct-" + UUID.randomUUID());
        user.setPassword("x");
        user.setEmail("acct-" + UUID.randomUUID() + "@t.com");
        user.setRole(role);
        principal = UserDetailsImpl.build(userRepository.save(user), orgId);
    }

    private Workflow seedWorkflow(WorkflowType type) {
        Workflow w = new Workflow();
        w.setOrganizationId(orgId);
        w.setType(type);
        w.setPeriodStart(LocalDate.of(2025, 4, 1));
        w.setPeriodEnd(LocalDate.of(2025, 4, 30));
        w.setPeriodKey("2025-04");
        w.setFinancialYear(2025);
        w.setStage(WorkflowStage.DATA_UPLOADED);
        w.setUploadJobId(UUID.randomUUID());
        w.setCreatedBy("it");
        return workflowRepository.save(w);
    }

    @Test
    void boundEvent_afterCommit_movesWorkflowToFindingsReview() {
        Workflow w = seedWorkflow(WorkflowType.GSTR3B); // no analyzer applies to GSTR3B in this IT

        new TransactionTemplate(txManager).executeWithoutResult(status ->
                eventPublisher.publishEvent(new WorkflowUploadBoundEvent(
                        w.getId(), orgId, w.getUploadJobId(), "it")));

        Workflow reloaded = workflowRepository.findById(w.getId()).orElseThrow();
        assertThat(reloaded.getStage()).isEqualTo(WorkflowStage.FINDINGS_REVIEW);
        List<WorkflowEvent> events = eventRepository.findAllByWorkflowIdOrderByOccurredAtAsc(w.getId());
        assertThat(events).extracting(WorkflowEvent::getKind).contains(WorkflowEventKind.ANALYSIS_DONE);
    }

    @Test
    void analyzeEndpoint_retriesAfterFailure() throws Exception {
        Workflow w = seedWorkflow(WorkflowType.GSTR1); // failOnceAnalyzer applies to GSTR1
        var withUser = SecurityMockMvcRequestPostProcessors.user(principal);

        mockMvc.perform(post("/api/v1/workflows/" + w.getId() + "/analyze").with(withUser))
                .andExpect(status().isOk());
        assertThat(workflowRepository.findById(w.getId()).orElseThrow().getStage())
                .isEqualTo(WorkflowStage.DATA_UPLOADED);

        mockMvc.perform(post("/api/v1/workflows/" + w.getId() + "/analyze").with(withUser))
                .andExpect(status().isOk());
        assertThat(workflowRepository.findById(w.getId()).orElseThrow().getStage())
                .isEqualTo(WorkflowStage.FINDINGS_REVIEW);

        List<WorkflowAnalysisRun> runs = runRepository.findAll().stream()
                .filter(r -> r.getWorkflowId().equals(w.getId())).toList();
        assertThat(runs).hasSize(2);
        assertThat(runs.get(0).getStatus()).isEqualTo(AnalysisRunStatus.FAILED);
        assertThat(runs.get(0).getErrorMessage()).isEqualTo("boom");
        assertThat(runs.get(1).getStatus()).isEqualTo(AnalysisRunStatus.DONE);
        assertThat(eventRepository.findAllByWorkflowIdOrderByOccurredAtAsc(w.getId()))
                .extracting(WorkflowEvent::getKind).contains(WorkflowEventKind.ANALYSIS_DONE);
    }
}
