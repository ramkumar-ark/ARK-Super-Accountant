package com.arktech.superaccountant.masters.controllers;

import com.arktech.superaccountant.login.models.ERole;
import com.arktech.superaccountant.login.models.Role;
import com.arktech.superaccountant.login.models.User;
import com.arktech.superaccountant.login.repository.RoleRepository;
import com.arktech.superaccountant.login.repository.UserRepository;
import com.arktech.superaccountant.login.security.jwt.JwtUtils;
import com.arktech.superaccountant.masters.models.*;
import com.arktech.superaccountant.masters.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GSTIN_PRESENCE end to end: scope follows the parent-group chain and registration type, and marking a ledger
 * "GST not applicable" through the review flow stops it being flagged on a re-upload. Needs Docker.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Transactional
@TestPropertySource(properties = "JWT_SECRET=test-jwt-secret-must-be-at-least-32-characters-long")
class GstinNotApplicableIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    private static final String MASTERS = """
            {"tallymessage": [
              {"group": {"name": "Sundry Creditors", "parent": "Current Liabilities"}},
              {"group": {"name": "South Vendors", "parent": "Sundry Creditors"}},
              {"group": {"name": "Indirect Expenses", "parent": ""}},
              {"ledger": {"name": "Sub Group Vendor", "guid": "g1", "parent": "South Vendors", "gstregistrationtype": "Regular"}},
              {"ledger": {"name": "Walk In", "guid": "g2", "parent": "Sundry Creditors", "gstregistrationtype": "Unregistered/Consumer"}},
              {"ledger": {"name": "Blank Reg", "guid": "g3", "parent": "Sundry Creditors"}},
              {"ledger": {"name": "Office Rent", "guid": "g4", "parent": "Indirect Expenses", "gstregistrationtype": "Regular"}}
            ]}
            """;

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired RoleRepository roleRepository;
    @Autowired JwtUtils jwtUtils;
    @Autowired OrganizationRepository organizationRepository;
    @Autowired UploadJobRepository uploadJobRepository;
    @Autowired ValidationFindingRepository findingRepository;
    @Autowired ValidationRuleConfigRepository ruleConfigRepository;
    @Autowired PreconfiguredMasterRepository masterRepository;

    private UUID orgId;
    private String operatorJwt, accountantJwt;

    @BeforeEach
    void setUp() {
        orgId = organizationRepository.save(new Organization("Gstin Org " + UUID.randomUUID())).getId();
        if (!ruleConfigRepository.existsByRuleCode("GSTIN_PRESENCE")) {
            ValidationRuleConfig rule = new ValidationRuleConfig();
            rule.setRuleCode("GSTIN_PRESENCE");
            rule.setRuleName("GSTIN presence");
            rule.setActive(true);
            ruleConfigRepository.save(rule);
        }
        operatorJwt = user("gn_operator", ERole.ROLE_OPERATOR);
        accountantJwt = user("gn_accountant", ERole.ROLE_ACCOUNTANT);
    }

    private String user(String username, ERole eRole) {
        Role role = roleRepository.findByName(eRole).orElseGet(() -> roleRepository.save(new Role(eRole)));
        if (userRepository.findByUsername(username).isEmpty()) {
            User u = new User(username, username + "@test.com", "pass");
            u.setRole(role);
            userRepository.save(u);
        }
        return jwtUtils.generateJwtTokenForUser(username, orgId);
    }

    private UUID upload() throws Exception {
        String body = mockMvc.perform(multipart("/api/v1/uploads")
                        .file(new MockMultipartFile("file", "masters.json", "application/json",
                                MASTERS.getBytes(StandardCharsets.UTF_8)))
                        .header("Authorization", "Bearer " + accountantJwt))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"id\"\\s*:\\s*\"([0-9a-f-]{36})\"").matcher(body);
        String last = null;
        while (m.find()) last = m.group(1); // job id is serialised after the nested finding ids
        assertThat(last).isNotNull();
        return UUID.fromString(last);
    }

    private List<String> gstinFindingLedgers(UUID jobId) {
        return findingRepository.findByUploadJobId(jobId).stream()
                .filter(f -> "GSTIN_PRESENCE".equals(f.getRuleCode()))
                .map(ValidationFinding::getLedgerName).toList();
    }

    private void action(String jwt, UUID jobId, ValidationFinding f, String json) throws Exception {
        mockMvc.perform(post("/api/v1/uploads/" + jobId + "/findings/" + f.getId() + "/action")
                        .contentType("application/json").content(json).header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk());
    }

    @Test
    void onlyRegisteredLedgersUnderPartyGroupChain_areFlagged() throws Exception {
        UUID job = upload();

        assertThat(gstinFindingLedgers(job)).containsExactly("Sub Group Vendor");
    }

    @Test
    void markGstNotApplicable_viaReview_resolvesAndSurvivesReupload() throws Exception {
        UUID job = upload();
        ValidationFinding f = findingRepository.findByUploadJobId(job).stream()
                .filter(x -> "GSTIN_PRESENCE".equals(x.getRuleCode())).findFirst().orElseThrow();

        // Operator proposes: goes to review, master untouched, nothing unlocked.
        action(operatorJwt, job, f, "{\"action\":\"ACKNOWLEDGE\",\"value\":\"NOT_APPLICABLE\",\"note\":\"unregistered in practice\"}");
        assertThat(findingRepository.findById(f.getId()).orElseThrow().getResolveStatus()).isEqualTo(ResolveStatus.ACKNOWLEDGED);
        PreconfiguredMaster before = masterRepository.findByOrganizationIdAndActiveTrue(orgId).stream()
                .filter(m -> m.getLedgerName().equals("Sub Group Vendor")).findFirst().orElseThrow();
        assertThat(before.getGstApplicabilityType()).isNotEqualTo(GstApplicabilityType.NOT_APPLICABLE);

        // Accountant approves: master marked, finding closed.
        action(accountantJwt, job, f, "{\"action\":\"APPROVE\"}");
        masterRepository.flush();
        PreconfiguredMaster after = masterRepository.findById(before.getId()).orElseThrow();
        assertThat(after.getGstApplicabilityType()).isEqualTo(GstApplicabilityType.NOT_APPLICABLE);
        assertThat(findingRepository.findById(f.getId()).orElseThrow().getResolveStatus()).isEqualTo(ResolveStatus.APPROVED);

        // Re-uploading the same data does not flag it again.
        UUID second = upload();
        assertThat(gstinFindingLedgers(second)).isEmpty();
    }

    @Test
    void accountantMarksGstNotApplicable_inOneStep() throws Exception {
        UUID job = upload();
        ValidationFinding f = findingRepository.findByUploadJobId(job).stream()
                .filter(x -> "GSTIN_PRESENCE".equals(x.getRuleCode())).findFirst().orElseThrow();

        action(accountantJwt, job, f, "{\"action\":\"ACKNOWLEDGE\",\"value\":\"NOT_APPLICABLE\"}");

        assertThat(findingRepository.findById(f.getId()).orElseThrow().getResolveStatus()).isEqualTo(ResolveStatus.APPROVED);
        assertThat(gstinFindingLedgers(upload())).isEmpty();
    }
}
