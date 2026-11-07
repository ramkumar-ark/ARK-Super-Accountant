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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Bulk override of TDS_SECTION_MAPPING (one chosen section or NOT_SUBJECT) and GSTIN_PRESENCE (NOT_APPLICABLE only)
 * findings,. Needs Docker.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Transactional
@TestPropertySource(properties = "JWT_SECRET=test-jwt-secret-must-be-at-least-32-characters-long")
class BulkTypedOverrideIT {

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
              {"group": {"name": "Direct Expenses", "parent": ""}},
              {"ledger": {"name": "Rent A", "guid": "e1", "parent": "Indirect Expenses"}},
              {"ledger": {"name": "Rent B", "guid": "e2", "parent": "Indirect Expenses"}},
              {"ledger": {"name": "Freight X", "guid": "e3", "parent": "Direct Expenses"}},
              {"ledger": {"name": "Vendor One", "guid": "v1", "parent": "South Vendors", "gstregistrationtype": "Regular"}},
              {"ledger": {"name": "Vendor Two", "guid": "v2", "parent": "Sundry Creditors", "gstregistrationtype": "Regular"}}
            ]}
            """;

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired RoleRepository roleRepository;
    @Autowired JwtUtils jwtUtils;
    @Autowired OrganizationRepository organizationRepository;
    @Autowired ValidationFindingRepository findingRepository;
    @Autowired ValidationRuleConfigRepository ruleConfigRepository;
    @Autowired PreconfiguredMasterRepository masterRepository;

    private UUID orgId;
    private String operatorJwt, accountantJwt;
    private UUID job;

    @BeforeEach
    void setUp() throws Exception {
        orgId = organizationRepository.save(new Organization("Bulk Org " + UUID.randomUUID())).getId();
        for (String code : List.of("GSTIN_PRESENCE", "TDS_SECTION_MAPPING")) {
            if (!ruleConfigRepository.existsByRuleCode(code)) {
                ValidationRuleConfig rule = new ValidationRuleConfig();
                rule.setRuleCode(code);
                rule.setRuleName(code);
                rule.setActive(true);
                ruleConfigRepository.save(rule);
            }
        }
        operatorJwt = user("bt_operator", ERole.ROLE_OPERATOR);
        accountantJwt = user("bt_accountant", ERole.ROLE_ACCOUNTANT);
        job = upload();
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

    private org.springframework.test.web.servlet.ResultActions bulk(String jwt, String json) throws Exception {
        return mockMvc.perform(post("/api/v1/uploads/" + job + "/findings/bulk-action")
                .contentType("application/json").content(json).header("Authorization", "Bearer " + jwt));
    }

    private ValidationFinding finding(String rule, String ledger) {
        return findingRepository.findByUploadJobId(job).stream()
                .filter(f -> rule.equals(f.getRuleCode()) && ledger.equals(f.getLedgerName())).findFirst().orElseThrow();
    }

    private ResolveStatus statusOf(String rule, String ledger) {
        return findingRepository.findById(finding(rule, ledger).getId()).orElseThrow().getResolveStatus();
    }

    private PreconfiguredMaster master(String ledger) {
        return masterRepository.findByOrganizationIdAndActiveTrue(orgId).stream()
                .filter(m -> m.getLedgerName().equals(ledger)).findFirst().orElseThrow();
    }

    @Test
    void operatorBulkTds_byIds_submitsChosenSectionForReview_masterUntouched() throws Exception {
        UUID a = finding("TDS_SECTION_MAPPING", "Rent A").getId();
        UUID b = finding("TDS_SECTION_MAPPING", "Freight X").getId();

        bulk(operatorJwt, "{\"findingIds\":[\"" + a + "\",\"" + b + "\"],\"value\":\"194C\",\"note\":\"batch\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.processed").value(2))
                .andExpect(jsonPath("$.status").value("ACKNOWLEDGED"));

        ValidationFinding fa = findingRepository.findById(a).orElseThrow();
        assertThat(fa.getResolveStatus()).isEqualTo(ResolveStatus.ACKNOWLEDGED);
        assertThat(fa.getProposedValue()).isEqualTo("194C");
        assertThat(master("Rent A").getTdsSection()).isNull();
        assertThat(statusOf("TDS_SECTION_MAPPING", "Rent B")).isEqualTo(ResolveStatus.OPEN);
    }

    private String ids(String rule, String... ledgers) {
        return java.util.Arrays.stream(ledgers).map(l -> "\"" + finding(rule, l).getId() + "\"")
                .collect(java.util.stream.Collectors.joining(",", "[", "]"));
    }

    @Test
    void accountantBulkTds_oneValuePerTickedSubset_repeatedPerValue() throws Exception {
        bulk(accountantJwt, "{\"findingIds\":" + ids("TDS_SECTION_MAPPING", "Rent A", "Rent B") + ",\"value\":\"194C\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.processed").value(2))
                .andExpect(jsonPath("$.status").value("APPROVED"));
        assertThat(master("Rent A").getTdsSection()).isEqualTo("194C");
        assertThat(master("Rent B").getTdsSection()).isEqualTo("194C");
        assertThat(statusOf("TDS_SECTION_MAPPING", "Freight X")).isEqualTo(ResolveStatus.OPEN);

        bulk(accountantJwt, "{\"findingIds\":" + ids("TDS_SECTION_MAPPING", "Freight X") + ",\"value\":\"NOT_SUBJECT\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.processed").value(1));
        assertThat(master("Freight X").getTdsSection()).isEqualTo("NOT_SUBJECT");
    }

    @Test
    void bulkTds_selectAllMatching_targetsOnlyTdsFindings() throws Exception {
        // the value alone picks the rule: GSTIN findings are never swept in by a TDS section
        bulk(accountantJwt, "{\"allMatching\":true,\"value\":\"194J_A\",\"dryRun\":true}")
                .andExpect(jsonPath("$.matched").value(3));
        bulk(accountantJwt, "{\"allMatching\":true,\"value\":\"194J_A\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.processed").value(3));
        assertThat(statusOf("GSTIN_PRESENCE", "Vendor One")).isEqualTo(ResolveStatus.OPEN);
    }

    @Test
    void operatorBulkTdsThenAccountantBulkApprove_appliesProposedValues() throws Exception {
        bulk(operatorJwt, "{\"findingIds\":" + ids("TDS_SECTION_MAPPING", "Rent A", "Rent B") + ",\"value\":\"194I\"}")
                .andExpect(status().isOk());
        assertThat(statusOf("TDS_SECTION_MAPPING", "Rent A")).isEqualTo(ResolveStatus.ACKNOWLEDGED);

        bulk(operatorJwt, "{\"action\":\"APPROVE\",\"ruleCode\":\"TDS_SECTION_MAPPING\"}").andExpect(status().isForbidden());
        bulk(accountantJwt, "{\"action\":\"APPROVE\",\"ruleCode\":\"TDS_SECTION_MAPPING\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.processed").value(2));

        assertThat(master("Rent B").getTdsSection()).isEqualTo("194I");
        assertThat(statusOf("TDS_SECTION_MAPPING", "Rent B")).isEqualTo(ResolveStatus.APPROVED);
        assertThat(statusOf("TDS_SECTION_MAPPING", "Freight X")).isEqualTo(ResolveStatus.OPEN);
    }

    @Test
    void bulkGstin_notApplicableOnly_markedForEverySelectedLedger() throws Exception {
        bulk(accountantJwt, "{\"allMatching\":true,\"value\":\"NOT_APPLICABLE\",\"dryRun\":true}")
                .andExpect(jsonPath("$.matched").value(2));
        bulk(accountantJwt, "{\"allMatching\":true,\"value\":\"NOT_APPLICABLE\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.processed").value(2));

        assertThat(master("Vendor One").getGstApplicabilityType()).isEqualTo(GstApplicabilityType.NOT_APPLICABLE);
        assertThat(master("Vendor Two").getGstApplicabilityType()).isEqualTo(GstApplicabilityType.NOT_APPLICABLE);
        assertThat(statusOf("GSTIN_PRESENCE", "Vendor One")).isEqualTo(ResolveStatus.APPROVED);
        // the TDS findings were not touched by the GSTIN-only value
        assertThat(statusOf("TDS_SECTION_MAPPING", "Rent A")).isEqualTo(ResolveStatus.OPEN);
    }

    @Test
    void bulkValues_thatBulkMustNotAccept_areRejected() throws Exception {
        UUID vendor = finding("GSTIN_PRESENCE", "Vendor One").getId();
        // a GSTIN is unique per party: no bulk GSTIN entry
        bulk(accountantJwt, "{\"ruleCode\":\"GSTIN_PRESENCE\",\"value\":\"29ABCDE1234F1Z5\"}").andExpect(status().isBadRequest());
        bulk(accountantJwt, "{\"allMatching\":true,\"value\":\"194Z\"}").andExpect(status().isBadRequest());
        bulk(accountantJwt, "{\"ruleCode\":\"GSTIN_PRESENCE\",\"value\":\"194C\"}").andExpect(status().isBadRequest());
        bulk(accountantJwt, "{\"action\":\"APPROVE\",\"ruleCode\":\"TDS_SECTION_MAPPING\",\"value\":\"194C\"}").andExpect(status().isBadRequest());
        // a TDS section aimed at a GSTIN finding by id fails all-or-nothing
        UUID rent = finding("TDS_SECTION_MAPPING", "Rent A").getId();
        // (the rollback itself is not observable here: the test shares the controller's transaction)
        bulk(accountantJwt, "{\"findingIds\":[\"" + vendor + "\",\"" + rent + "\"],\"value\":\"194C\"}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.failures[0].ledgerName").value("Vendor One"));
    }

    @Test
    void auditorAndOwnerCannotBulkOverride() throws Exception {
        String owner = user("bt_owner", ERole.ROLE_OWNER);
        bulk(owner, "{\"allMatching\":true,\"value\":\"194C\"}").andExpect(status().isForbidden());
    }
}
