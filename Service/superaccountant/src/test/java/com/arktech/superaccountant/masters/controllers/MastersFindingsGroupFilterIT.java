package com.arktech.superaccountant.masters.controllers;

import com.arktech.superaccountant.login.models.ERole;
import com.arktech.superaccountant.login.models.Role;
import com.arktech.superaccountant.login.models.User;
import com.arktech.superaccountant.login.repository.RoleRepository;
import com.arktech.superaccountant.login.repository.UserRepository;
import com.arktech.superaccountant.login.security.jwt.JwtUtils;
import com.arktech.superaccountant.masters.models.FindingSeverity;
import com.arktech.superaccountant.masters.models.LedgerCategory;
import com.arktech.superaccountant.masters.models.MismatchType;
import com.arktech.superaccountant.masters.models.Organization;
import com.arktech.superaccountant.masters.models.PreconfiguredMaster;
import com.arktech.superaccountant.masters.models.ResolveStatus;
import com.arktech.superaccountant.masters.models.UploadJob;
import com.arktech.superaccountant.masters.models.UploadJobStatus;
import com.arktech.superaccountant.masters.models.UploadLedgerSnapshot;
import com.arktech.superaccountant.masters.models.ValidationFinding;
import com.arktech.superaccountant.masters.repository.OrganizationRepository;
import com.arktech.superaccountant.masters.repository.PreconfiguredMasterRepository;
import com.arktech.superaccountant.masters.repository.UploadJobRepository;
import com.arktech.superaccountant.masters.repository.UploadLedgerSnapshotRepository;
import com.arktech.superaccountant.masters.repository.ValidationFindingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The masters findings page's Tally-group filter: options are the primary groups of the latest upload,
 * and the filter matches findings by the ledger's full group chain, falling back to the configured
 * parent group for masters missing from the upload.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Transactional
@TestPropertySource(properties = "JWT_SECRET=test-jwt-secret-must-be-at-least-32-characters-long")
class MastersFindingsGroupFilterIT {

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
    @Autowired UploadLedgerSnapshotRepository snapshotRepository;
    @Autowired ValidationFindingRepository findingRepository;
    @Autowired PreconfiguredMasterRepository masterRepository;

    private String jwt;
    private UUID jobId;

    @BeforeEach
    void seed() {
        UUID orgId = organizationRepository.save(new Organization("Group Filter " + UUID.randomUUID())).getId();
        Role role = roleRepository.findByName(ERole.ROLE_ACCOUNTANT)
                .orElseGet(() -> roleRepository.save(new Role(ERole.ROLE_ACCOUNTANT)));
        User user = new User("group_accountant", "group_accountant@test.com", "pass");
        user.setRole(role);
        userRepository.save(user);
        jwt = jwtUtils.generateJwtTokenForUser("group_accountant", orgId);

        UploadJob job = new UploadJob();
        job.setOrganizationId(orgId);
        job.setFileName("masters.json");
        job.setStatus(UploadJobStatus.COMPLETED_WITH_MISMATCHES);
        jobId = uploadJobRepository.save(job).getId();

        snapshot(orgId, "Loan Given", "Loans & Advances (Asset)", "loans & advances (asset)");
        snapshot(orgId, "Vehicle Loan", "Vehicle Loans", "vehicle loans|loans & advances (asset)");
        snapshot(orgId, "Fixed Deposit", "Deposits (Asset)", "deposits (asset)");
        snapshot(orgId, "Secured Loan", "Secured Loans", "secured loans");
        snapshot(orgId, "No Group Ledger", null, null);

        finding("Loan Given");
        finding("Vehicle Loan");
        finding("Fixed Deposit");
        finding("Secured Loan");
        finding("No Group Ledger");
        // Missing from the upload: its configured parent group decides its group.
        finding("Unlisted Loan");
        master(orgId, "Unlisted Loan", "Loans & Advances (Asset)", "loans & advances (asset)");
        finding("Configured Deposit");
        master(orgId, "Configured Deposit", "Deposits (Asset)", null);
        // Missing from the upload and unconfigured: no Tally group at all.
        finding("Nowhere Ledger");
    }

    private void snapshot(UUID orgId, String ledger, String parentGroup, String chain) {
        UploadLedgerSnapshot s = new UploadLedgerSnapshot();
        s.setUploadJobId(jobId);
        s.setOrganizationId(orgId);
        s.setLedgerName(ledger);
        s.setParentGroup(parentGroup);
        s.setGroupChain(chain);
        snapshotRepository.save(s);
    }

    private void finding(String ledger) {
        ValidationFinding f = new ValidationFinding();
        f.setUploadJobId(jobId);
        f.setRuleCode("MISMATCH_DETECTION");
        f.setMismatchType(MismatchType.MISSING_IN_CONFIGURATION);
        f.setCategory(LedgerCategory.OTHER);
        f.setLedgerName(ledger);
        f.setSeverity(FindingSeverity.HIGH);
        f.setResolveStatus(ResolveStatus.OPEN);
        f.setMessage("test");
        findingRepository.save(f);
    }

    private void master(UUID orgId, String ledger, String expectedParentGroup, String chain) {
        PreconfiguredMaster m = new PreconfiguredMaster();
        m.setOrganizationId(orgId);
        m.setLedgerName(ledger);
        m.setCategory(LedgerCategory.OTHER);
        m.setExpectedParentGroup(expectedParentGroup);
        m.setGroupChain(chain);
        masterRepository.save(m);
    }

    @Test
    void latestGroupsListsOnlyPrimaryGroups_withReadableLabels() throws Exception {
        mockMvc.perform(get("/api/v1/uploads/latest/groups").header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].value",
                        containsInAnyOrder("loans & advances (asset)", "deposits (asset)", "secured loans")))
                .andExpect(jsonPath("$[*].label",
                        containsInAnyOrder("Loans & Advances (Asset)", "Deposits (Asset)", "Secured Loans")));
    }

    @Test
    void groupFilterMatchesSubgroupsAndConfiguredFallback() throws Exception {
        mockMvc.perform(get("/api/v1/uploads/latest/mismatches")
                        .param("group", "loans & advances (asset)")
                        .param("size", "100")
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[*].ledgerName",
                        containsInAnyOrder("Loan Given", "Vehicle Loan", "Unlisted Loan")));
    }

    @Test
    void findingsWithoutATallyGroupOnlyMatchAllGroups() throws Exception {
        mockMvc.perform(get("/api/v1/uploads/latest/mismatches")
                        .param("group", "deposits (asset)")
                        .param("size", "100")
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[*].ledgerName",
                        containsInAnyOrder("Fixed Deposit", "Configured Deposit")));

        mockMvc.perform(get("/api/v1/uploads/latest/mismatches")
                        .param("size", "100")
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(8));
    }

    @Test
    void bulkCountHonoursTheGroupFilter() throws Exception {
        mockMvc.perform(post("/api/v1/uploads/" + jobId + "/findings/bulk-action")
                        .contentType("application/json")
                        .content("{\"action\":\"ACKNOWLEDGE\",\"allMatching\":true,"
                                + "\"group\":\"loans & advances (asset)\",\"dryRun\":true}")
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matched").value(3));
    }
}
