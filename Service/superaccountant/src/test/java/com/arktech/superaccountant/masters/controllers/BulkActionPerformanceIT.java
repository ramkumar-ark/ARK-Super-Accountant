package com.arktech.superaccountant.masters.controllers;

import com.arktech.superaccountant.login.models.ERole;
import com.arktech.superaccountant.login.models.Role;
import com.arktech.superaccountant.login.models.User;
import com.arktech.superaccountant.login.repository.RoleRepository;
import com.arktech.superaccountant.login.repository.UserRepository;
import com.arktech.superaccountant.login.security.jwt.JwtUtils;
import com.arktech.superaccountant.masters.models.*;
import com.arktech.superaccountant.masters.repository.*;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Guards the cost of the bulk endpoint on a realistic batch (900 missing-in-configuration findings, the heaviest
 * derivable type: each needs its uploaded snapshot and creates a master). Not transactional, so the endpoint's own
 * transaction commits for real. Asserts a statement budget rather than wall-clock time, which is machine-dependent;
 * the elapsed time is printed. Needs Docker.
 */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = "JWT_SECRET=test-jwt-secret-must-be-at-least-32-characters-long")
class BulkActionPerformanceIT {

    private static final int N = 900;

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired RoleRepository roleRepository;
    @Autowired JwtUtils jwtUtils;
    @Autowired OrganizationRepository organizationRepository;
    @Autowired UploadJobRepository uploadJobRepository;
    @Autowired ValidationFindingRepository findingRepository;
    @Autowired UploadLedgerSnapshotRepository snapshotRepository;
    @Autowired PreconfiguredMasterRepository masterRepository;
    @Autowired MasterEditLogRepository editLogRepository;
    @Autowired EntityManagerFactory emf;

    @Test
    void accountantBulkOverrideOf900MissingMasters_staysWithinAStatementBudget() throws Exception {
        UUID orgId = organizationRepository.save(new Organization("Perf Org " + UUID.randomUUID())).getId();
        Role role = roleRepository.findByName(ERole.ROLE_ACCOUNTANT)
                .orElseGet(() -> roleRepository.save(new Role(ERole.ROLE_ACCOUNTANT)));
        String username = "perf_accountant_" + UUID.randomUUID().toString().substring(0, 8);
        User u = new User(username, username + "@test.com", "pass");
        u.setRole(role);
        userRepository.save(u);
        String jwt = jwtUtils.generateJwtTokenForUser(username, orgId);

        UploadJob job = new UploadJob();
        job.setOrganizationId(orgId);
        job.setFileName("masters.json");
        job.setStatus(UploadJobStatus.COMPLETED);
        job.setTotalLedgersParsed(N);
        job = uploadJobRepository.save(job);

        List<UploadLedgerSnapshot> snaps = new ArrayList<>();
        List<ValidationFinding> findings = new ArrayList<>();
        for (int i = 0; i < N; i++) {
            String name = "Ledger " + String.format("%04d", i);
            UploadLedgerSnapshot s = new UploadLedgerSnapshot();
            s.setUploadJobId(job.getId());
            s.setOrganizationId(orgId);
            s.setLedgerName(name);
            s.setParentGroup("Sundry Creditors");
            snaps.add(s);
            ValidationFinding f = new ValidationFinding();
            f.setUploadJobId(job.getId());
            f.setRuleCode("MISMATCH_DETECTION");
            f.setMismatchType(MismatchType.MISSING_IN_CONFIGURATION);
            f.setCategory(LedgerCategory.CREDITOR);
            f.setLedgerName(name);
            f.setSeverity(FindingSeverity.HIGH);
            f.setResolveStatus(ResolveStatus.OPEN);
            f.setMessage("test");
            findings.add(f);
        }
        snapshotRepository.saveAll(snaps);
        findingRepository.saveAll(findings);

        Statistics stats = emf.unwrap(SessionFactory.class).getStatistics();
        stats.clear();
        long t0 = System.nanoTime();
        mockMvc.perform(post("/api/v1/uploads/" + job.getId() + "/findings/bulk-action")
                        .contentType("application/json")
                        .content("{\"action\":\"ACKNOWLEDGE\",\"allMatching\":true}")
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processed").value(N));
        long ms = (System.nanoTime() - t0) / 1_000_000;
        long statements = stats.getPrepareStatementCount();
        System.out.println("BULK-PERF " + N + " findings: " + ms + " ms, " + statements + " prepared statements");

        assertThat(masterRepository.findByOrganizationIdAndActiveTrue(orgId)).hasSize(N);
        assertThat(findingRepository.findMatching(job.getId(), ResolveStatus.APPROVED, null, null, null, null)).hasSize(N);
        assertThat(editLogRepository.count()).isGreaterThanOrEqualTo(N);
        // Work must not scale with a query or two per finding.
        assertThat(statements).isLessThan(100);
    }
}
