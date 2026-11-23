package com.arktech.superaccountant.workflow.controllers;

import com.arktech.superaccountant.login.models.ERole;
import com.arktech.superaccountant.login.models.Role;
import com.arktech.superaccountant.login.models.User;
import com.arktech.superaccountant.login.repository.RoleRepository;
import com.arktech.superaccountant.login.repository.UserRepository;
import com.arktech.superaccountant.login.security.jwt.JwtUtils;
import com.arktech.superaccountant.masters.models.Organization;
import com.arktech.superaccountant.masters.models.UserOrganization;
import com.arktech.superaccountant.masters.repository.OrganizationRepository;
import com.arktech.superaccountant.masters.repository.UserOrganizationRepository;
import com.arktech.superaccountant.support.TestDatabase;
import com.arktech.superaccountant.tdsapplicability.TdsApplicabilityService;
import com.arktech.superaccountant.tdsapplicability.models.TdsApplicabilityBasis;
import com.arktech.superaccountant.workflow.models.ResponsibilityStart;
import com.arktech.superaccountant.workflow.models.Workflow;
import com.arktech.superaccountant.workflow.models.WorkflowStage;
import com.arktech.superaccountant.workflow.models.WorkflowType;
import com.arktech.superaccountant.workflow.repository.ResponsibilityStartRepository;
import com.arktech.superaccountant.workflow.repository.WorkflowRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = "JWT_SECRET=test-jwt-secret-must-be-at-least-32-characters-long")
class TdsWorkflowGatingIT {

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        TestDatabase.register(registry);
    }

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private UserOrganizationRepository userOrganizationRepository;

    @Autowired
    private WorkflowRepository workflowRepository;

    @Autowired
    private ResponsibilityStartRepository responsibilityStartRepository;

    @Autowired
    private TdsApplicabilityService tdsApplicabilityService;

    private Organization orgWithPan(String pan, String username, ERole roleName) {
        Organization org = new Organization("Gating Org " + pan);
        org.setPan(pan);
        org.setBooksStartDate(LocalDate.of(2025, 4, 1));
        org = organizationRepository.save(org);
        String jwt = user(username, roleName, org.getId());
        User user = userRepository.findByUsername(username).orElseThrow();
        userOrganizationRepository.save(new UserOrganization(user, org, roleName));
        return org;
    }

    private String user(String username, ERole roleName, UUID orgId) {
        Role role = roleRepository.findByName(roleName)
                .orElseGet(() -> roleRepository.save(new Role(roleName)));
        if (userRepository.findByUsername(username).isEmpty()) {
            User user = new User(username, username + "@test.com", "pass");
            user.setRole(role);
            userRepository.save(user);
        }
        return jwtUtils.generateJwtTokenForUser(username, orgId);
    }

    /** Q org (individual/HUF) that is not a deductor in FY 2026 but was in FY 2025. */
    private Organization qOrgNotDeductorIn2026(String username) {
        Organization org = orgWithPan("AAAPA1234A", username, ERole.ROLE_ACCOUNTANT);
        tdsApplicabilityService.recordYear(org.getId(), 2025, true,
                TdsApplicabilityBasis.DECLARED, null, null, null, "test");
        tdsApplicabilityService.recordYear(org.getId(), 2026, false,
                TdsApplicabilityBasis.DECLARED, null, null, null, "test");
        return org;
    }

    private String startBody(String type, String periodKey) {
        var json = objectMapper.createObjectNode();
        json.put("type", type);
        json.put("periodKey", periodKey);
        return json.toString();
    }

    @Test
    void startTdsPaymentInNonApplicableYearIs400() throws Exception {
        Organization org = qOrgNotDeductorIn2026("gate_nonapplicable");
        String jwt = user("gate_nonapplicable", ERole.ROLE_ACCOUNTANT, org.getId());

        MvcResult result = mockMvc.perform(post("/api/v1/workflows")
                        .header("Authorization", "Bearer " + jwt)
                        .contentType("application/json")
                        .content(startBody("TDS_PAYMENT", "2026-04")))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertThat(result.getResponse().getContentAsString())
                .contains("TDS is not applicable for FY 2026-27");
    }

    @Test
    void startTdsPaymentInApplicableYearSucceeds() throws Exception {
        Organization org = qOrgNotDeductorIn2026("gate_applicable");
        String jwt = user("gate_applicable", ERole.ROLE_ACCOUNTANT, org.getId());

        mockMvc.perform(post("/api/v1/workflows")
                        .header("Authorization", "Bearer " + jwt)
                        .contentType("application/json")
                        .content(startBody("TDS_PAYMENT", "2025-04")))
                .andExpect(status().isOk());
    }

    @Test
    void baselineRowsSkipNonApplicableYear() throws Exception {
        Organization org = qOrgNotDeductorIn2026("gate_baseline");
        String jwt = user("gate_baseline", ERole.ROLE_ACCOUNTANT, org.getId());

        // Setting the responsibility start for a TDS type in a not-applicable FY is rejected.
        mockMvc.perform(put("/api/v1/compliance/responsibility-start")
                        .header("Authorization", "Bearer " + jwt)
                        .contentType("application/json")
                        .content("{\"type\":\"TDS_PAYMENT\",\"firstPeriodStart\":\"2026-05-01\"}"))
                .andExpect(status().isBadRequest());

        // The firm seeds it directly: first period in the not-applicable FY 2026.
        ResponsibilityStart start = new ResponsibilityStart();
        start.setOrganizationId(org.getId());
        start.setType(WorkflowType.TDS_PAYMENT);
        start.setFirstPeriodStart(LocalDate.of(2026, 5, 1));
        start.setSetBy("seed");
        responsibilityStartRepository.save(start);

        // Rebuilding happens only when an answer actually changes: flip FY 2025 off, then back on.
        tdsApplicabilityService.recordYear(org.getId(), 2025, false,
                TdsApplicabilityBasis.DECLARED, null, null, null, "test");
        tdsApplicabilityService.recordYear(org.getId(), 2025, true,
                TdsApplicabilityBasis.DECLARED, null, null, null, "test");

        List<Workflow> activeBaseline = workflowRepository
                .findAllByOrganizationIdAndTypeAndStageAndActiveTrue(
                        org.getId(), WorkflowType.TDS_PAYMENT, WorkflowStage.BASELINE);
        assertThat(activeBaseline).isNotEmpty();
        assertThat(activeBaseline)
                .allSatisfy(w -> assertThat(w.getFinancialYear()).isEqualTo(2025));
        assertThat(activeBaseline)
                .anySatisfy(w -> assertThat(w.getPeriodKey()).isEqualTo("2025-04"));
        assertThat(activeBaseline)
                .noneSatisfy(w -> assertThat(w.getPeriodKey()).isEqualTo("2026-04"));
    }

    @Test
    void listHidesDraftTdsWorkflowOfNonApplicableYear() throws Exception {
        Organization org = qOrgNotDeductorIn2026("gate_list_draft");
        String jwt = user("gate_list_draft", ERole.ROLE_ACCOUNTANT, org.getId());

        workflowRepository.save(workflow(org.getId(), WorkflowType.TDS_PAYMENT,
                "2026-04", LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30),
                2026, WorkflowStage.STARTED));
        Workflow fy2025 = workflowRepository.save(workflow(org.getId(), WorkflowType.TDS_PAYMENT,
                "2025-04", LocalDate.of(2025, 4, 1), LocalDate.of(2025, 4, 30),
                2025, WorkflowStage.STARTED));

        MvcResult result = mockMvc.perform(get("/api/v1/workflows")
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        List<String> ids = new java.util.ArrayList<>();
        root.forEach(w -> ids.add(w.get("id").asText()));
        assertThat(ids).contains(fy2025.getId().toString());
        // The FY 2026 draft is the only other TDS workflow; it must be hidden.
        assertThat(ids).hasSize(1);
    }

    @Test
    void listKeepsFinalizedTdsWorkflow() throws Exception {
        Organization org = qOrgNotDeductorIn2026("gate_list_final");
        String jwt = user("gate_list_final", ERole.ROLE_ACCOUNTANT, org.getId());

        Workflow finalized = workflowRepository.save(workflow(org.getId(), WorkflowType.TDS_PAYMENT,
                "2026-04", LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30),
                2026, WorkflowStage.FINALIZED));

        MvcResult result = mockMvc.perform(get("/api/v1/workflows")
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        List<String> ids = new java.util.ArrayList<>();
        root.forEach(w -> ids.add(w.get("id").asText()));
        assertThat(ids).contains(finalized.getId().toString());
    }

    @Test
    void companyUnaffected() throws Exception {
        Organization org = orgWithPan("AAACA1234A", "gate_company", ERole.ROLE_ACCOUNTANT);
        String jwt = user("gate_company", ERole.ROLE_ACCOUNTANT, org.getId());

        mockMvc.perform(post("/api/v1/workflows")
                        .header("Authorization", "Bearer " + jwt)
                        .contentType("application/json")
                        .content(startBody("TDS_PAYMENT", "2026-04")))
                .andExpect(status().isOk());
    }

    private Workflow workflow(UUID orgId, WorkflowType type, String periodKey,
                              LocalDate start, LocalDate end, int fy, WorkflowStage stage) {
        Workflow w = new Workflow();
        w.setOrganizationId(orgId);
        w.setType(type);
        w.setPeriodStart(start);
        w.setPeriodEnd(end);
        w.setPeriodKey(periodKey);
        w.setFinancialYear(fy);
        w.setStage(stage);
        w.setActive(true);
        w.setRevision(0);
        return w;
    }
}
