package com.arktech.superaccountant.masters.controllers;

import com.arktech.superaccountant.login.models.ERole;
import com.arktech.superaccountant.login.models.Role;
import com.arktech.superaccountant.login.models.User;
import com.arktech.superaccountant.login.repository.RoleRepository;
import com.arktech.superaccountant.login.repository.UserRepository;
import com.arktech.superaccountant.login.security.jwt.JwtUtils;
import com.arktech.superaccountant.masters.models.*;
import com.arktech.superaccountant.masters.repository.*;
import com.arktech.superaccountant.tds.models.TdsPanOverride;
import com.arktech.superaccountant.tds.repository.TdsPanOverrideRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
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
 * PAN_FORMAT end to end: an over-length Tally PAN on a masters upload is flagged HIGH/OPEN and
 * persisted. Needs Docker.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Transactional
@TestPropertySource(properties = "JWT_SECRET=test-jwt-secret-must-be-at-least-32-characters-long")
class PanFormatFindingIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    private static final String MASTERS_JSON = """
            {"tallymessage":[
              {"group":{"name":"Indirect Expenses","parent":""}},
              {"ledger":{"name":"ANANDHA TIMBER DEPOT","guid":"pan-1",
                         "parent":"Indirect Expenses","incometaxnumber":"AAPPK2262D1"}}
            ]}
            """;

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired RoleRepository roleRepository;
    @Autowired JwtUtils jwtUtils;
    @Autowired OrganizationRepository organizationRepository;
    @Autowired ValidationFindingRepository findingRepository;
    @Autowired ValidationRuleConfigRepository ruleConfigRepository;
    @Autowired TdsPanOverrideRepository panOverrideRepository;

    private UUID orgId;
    private String accountantJwt;

    @BeforeEach
    void setUp() {
        orgId = organizationRepository.save(new Organization("Pan Org " + UUID.randomUUID())).getId();
        if (!ruleConfigRepository.existsByRuleCode("PAN_FORMAT")) {
            ValidationRuleConfig rule = new ValidationRuleConfig();
            rule.setRuleCode("PAN_FORMAT");
            rule.setRuleName("PAN Format");
            rule.setActive(true);
            ruleConfigRepository.save(rule);
        }
        accountantJwt = user("pan_accountant", ERole.ROLE_ACCOUNTANT);
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

    private UUID upload(String token, String mastersJson, String filename) throws Exception {
        String body = mockMvc.perform(multipart("/api/v1/uploads")
                        .file(new MockMultipartFile("file", filename, "application/json",
                                mastersJson.getBytes(StandardCharsets.UTF_8)))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"id\"\\s*:\\s*\"([0-9a-f-]{36})\"").matcher(body);
        String last = null;
        while (m.find()) last = m.group(1); // job id is serialised after the nested finding ids
        assertThat(last).isNotNull();
        return UUID.fromString(last);
    }

    @Test
    void uploadRaisesPanFormatFindingForOverLengthPan() throws Exception {
        String token = accountantJwt;
        UUID jobId = upload(token, MASTERS_JSON, "masters-pan.json");

        List<ValidationFinding> findings = findingRepository.findByUploadJobId(jobId).stream()
                .filter(f -> "PAN_FORMAT".equals(f.getRuleCode()))
                .toList();

        assertThat(findings).hasSize(1);
        ValidationFinding f = findings.get(0);
        assertThat(f.getLedgerName()).isEqualTo("ANANDHA TIMBER DEPOT");
        assertThat(f.getActualValue()).isEqualTo("AAPPK2262D1");
        assertThat(f.getSeverity()).isEqualTo(FindingSeverity.HIGH);
        assertThat(f.getResolveStatus()).isEqualTo(ResolveStatus.OPEN);
    }

    @Test
    void accountantCorrectsPanAndStoreSuppressesNextUpload() throws Exception {
        String token = accountantJwt;
        UUID jobId = upload(token, MASTERS_JSON, "masters-pan.json");
        UUID findingId = panFindingId(token, jobId);

        mockMvc.perform(post("/api/v1/uploads/{jobId}/findings/{findingId}/action", jobId, findingId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"action":"ACKNOWLEDGE","note":"","value":" aappk2262d "}
                                """))
                .andExpect(status().isOk());

        ValidationFinding approved = findingRepository.findById(findingId).orElseThrow();
        assertThat(approved.getResolveStatus()).isEqualTo(ResolveStatus.APPROVED);

        TdsPanOverride override = panOverrideRepository
                .findByOrganizationIdAndPartyKey(orgId, "anandha timber depot").orElseThrow();
        assertThat(override.getPan()).isEqualTo("AAPPK2262D");
        assertThat(override.getPartyLedgerName()).isEqualTo("ANANDHA TIMBER DEPOT");
        assertThat(override.getSourceFindingId()).isEqualTo(findingId);
        assertThat(override.getApprovedBy()).isNotBlank();
        assertThat(override.getApprovedAt()).isNotNull();

        UUID secondJob = upload(token, MASTERS_JSON, "masters-pan-2.json");
        assertThat(panFindingIds(token, secondJob)).isEmpty();
    }

    private UUID panFindingId(String token, UUID jobId) throws Exception {
        List<UUID> ids = panFindingIds(token, jobId);
        assertThat(ids).hasSize(1);
        return ids.get(0);
    }

    private List<UUID> panFindingIds(String token, UUID jobId) throws Exception {
        return findingRepository.findByUploadJobId(jobId).stream()
                .filter(f -> "PAN_FORMAT".equals(f.getRuleCode()))
                .map(ValidationFinding::getId)
                .toList();
    }
}
