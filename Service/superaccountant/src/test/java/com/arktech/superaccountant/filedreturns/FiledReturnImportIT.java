package com.arktech.superaccountant.filedreturns;

import com.arktech.superaccountant.masters.models.Organization;
import com.arktech.superaccountant.masters.repository.OrganizationRepository;
import com.arktech.superaccountant.workflow.models.Workflow;
import com.arktech.superaccountant.workflow.models.WorkflowStage;
import com.arktech.superaccountant.workflow.models.WorkflowType;
import com.arktech.superaccountant.workflow.repository.WorkflowRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = "JWT_SECRET=test-jwt-secret-must-be-at-least-32-characters-long")
class FiledReturnImportIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private WorkflowRepository workflowRepository;

    @Autowired
    private FiledReturnSnapshotRepository snapshotRepository;

    @Autowired
    private FiledReturnImportService importService;

    private static int counter = 0;

    private static String unique() {
        return String.valueOf(++counter);
    }

    private String signupAndLogin(String username, String email, String role) throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"username":"%s","email":"%s","password":"pass1234","role":"%s"}
                    """.formatted(username, email, role)))
                .andExpect(status().isOk());
        return login(username);
    }

    private String login(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/signin")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"username":"%s","password":"pass1234"}
                    """.formatted(username)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("token").asText();
    }

    private UUID createOrganization(String jwt) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/organizations")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + jwt)
                .content("""
                    {"name":"Filed Returns Org","financialYearStart":4}
                    """))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(
                objectMapper.readTree(result.getResponse().getContentAsString())
                        .get("id").asText());
    }

    private String selectOrg(String jwt, UUID orgId) throws Exception {
        MvcResult selected = mockMvc.perform(post("/api/organizations/{id}/select", orgId)
                .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(selected.getResponse().getContentAsString())
                .get("token").asText();
    }

    private String memberJwt(String ownerJwt, UUID orgId, String role, String username, String email) throws Exception {
        MvcResult inviteResult = mockMvc.perform(post("/api/organizations/{id}/invites", orgId)
                .param("role", role)
                .header("Authorization", "Bearer " + ownerJwt))
                .andExpect(status().isOk())
                .andReturn();
        String token = objectMapper.readTree(inviteResult.getResponse().getContentAsString()).get("token").asText();
        signupAndLogin(username, email, role);
        mockMvc.perform(post("/api/organizations/invites/{token}/accept", token)
                .header("Authorization", "Bearer " + login(username)))
                .andExpect(status().isOk());
        return selectOrg(login(username), orgId);
    }

    private record Setup(UUID orgId, String ownerJwt, String accountantJwt, String operatorJwt,
                         UUID gstr3bRow, UUID gstr1Row) {
    }

    private Workflow baselineRow(UUID orgId, WorkflowType type, LocalDate start, LocalDate end, String periodKey) {
        Workflow w = new Workflow();
        w.setOrganizationId(orgId);
        w.setType(type);
        w.setPeriodStart(start);
        w.setPeriodEnd(end);
        w.setPeriodKey(periodKey);
        w.setFinancialYear(2026);
        w.setStage(WorkflowStage.BASELINE);
        w.setActive(true);
        w.setCreatedBy("setup");
        return workflowRepository.saveAndFlush(w);
    }

    private Setup monthlySetup() throws Exception {
        String ownerJwt = signupAndLogin("frowner" + unique(), "fr" + unique() + "@test.com", "owner");
        UUID orgId = createOrganization(ownerJwt);
        var org = organizationRepository.findById(orgId).orElseThrow();
        org.setGstin("29ABCDE1234F1Z5");
        organizationRepository.save(org);
        ownerJwt = selectOrg(ownerJwt, orgId);
        String accountantJwt = memberJwt(ownerJwt, orgId, "accountant", "fracc" + unique(), "fracc" + unique() + "@test.com");
        String operatorJwt = memberJwt(ownerJwt, orgId, "operator", "froper" + unique(), "froper" + unique() + "@test.com");
        Workflow gstr3bRow = baselineRow(orgId, WorkflowType.GSTR3B,
                LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30), "2026-04");
        Workflow gstr1Row = baselineRow(orgId, WorkflowType.GSTR1,
                LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30), "2026-04");
        return new Setup(orgId, ownerJwt, accountantJwt, operatorJwt, gstr3bRow.getId(), gstr1Row.getId());
    }

    private Setup quarterlySetup() throws Exception {
        String ownerJwt = signupAndLogin("frqowner" + unique(), "frq" + unique() + "@test.com", "owner");
        UUID orgId = createOrganization(ownerJwt);
        var org = organizationRepository.findById(orgId).orElseThrow();
        org.setGstin("29ABCDE1234F1Z5");
        organizationRepository.save(org);
        ownerJwt = selectOrg(ownerJwt, orgId);
        String accountantJwt = memberJwt(ownerJwt, orgId, "accountant", "frqacc" + unique(), "frqacc" + unique() + "@test.com");
        Workflow gstr3bRow = baselineRow(orgId, WorkflowType.GSTR3B,
                LocalDate.of(2026, 4, 1), LocalDate.of(2026, 6, 30), "2026-Q2");
        Workflow gstr1Row = baselineRow(orgId, WorkflowType.GSTR1,
                LocalDate.of(2026, 4, 1), LocalDate.of(2026, 6, 30), "2026-Q2");
        return new Setup(orgId, ownerJwt, accountantJwt, null, gstr3bRow.getId(), gstr1Row.getId());
    }

    private String gstr3bBody() {
        return """
            {"filingPeriod":"2026-04","rows":[
              {"rowCode":"3.1a","taxable":100000,"igst":18000,"cgst":0,"sgst":0,"cess":0},
              {"rowCode":"4A5","taxable":0,"igst":0,"cgst":4500,"sgst":4500,"cess":0}
            ]}
            """;
    }

    @Test
    void manual3bEntrySavesSnapshotWithRows() throws Exception {
        Setup s = monthlySetup();
        mockMvc.perform(post("/api/v1/filed-returns/gstr3b")
                .header("Authorization", "Bearer " + s.accountantJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(gstr3bBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sourceKind").value("MANUAL_ENTRY"))
                .andExpect(jsonPath("$.lines.length()").value(2))
                .andExpect(jsonPath("$.baselineWorkflowId").value(s.gstr3bRow().toString()))
                .andExpect(jsonPath("$.coverStart").value("2026-04-01"))
                .andExpect(jsonPath("$.coverEnd").value("2026-04-30"));

        assertEquals(1, snapshotRepository.findAllByOrganizationId(s.orgId()).size());
    }

    @Test
    void rejectsForeignGstin() throws Exception {
        Setup s = monthlySetup();
        SnapshotDraft draft = new SnapshotDraft(FiledFormType.GSTR1, "2026-04", "27ZZZZZ9999Z1Z9",
                FiledSourceKind.MANUAL_ENTRY, null, null, List.of());
        assertThrows(FiledReturnValidationException.class, () -> importService.save(s.orgId(), "x", draft));
        assertEquals(0, snapshotRepository.findAllByOrganizationId(s.orgId()).size());
    }

    @Test
    void rejectsPeriodWithoutBaselineRow() throws Exception {
        Setup s = monthlySetup();
        mockMvc.perform(post("/api/v1/filed-returns/gstr3b")
                .header("Authorization", "Bearer " + s.accountantJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"filingPeriod":"2025-01","rows":[
                      {"rowCode":"3.1a","taxable":100000,"igst":18000,"cgst":0,"sgst":0,"cess":0}
                    ]}
                    """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("No baseline period covers 2025-01"));
    }

    @Test
    void reimportIsIdempotentAndNewContentSupersedes() throws Exception {
        Setup s = monthlySetup();

        MvcResult first = mockMvc.perform(post("/api/v1/filed-returns/gstr3b")
                .header("Authorization", "Bearer " + s.accountantJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(gstr3bBody()))
                .andExpect(status().isCreated())
                .andReturn();
        UUID firstId = UUID.fromString(objectMapper.readTree(first.getResponse().getContentAsString()).get("id").asText());

        MvcResult second = mockMvc.perform(post("/api/v1/filed-returns/gstr3b")
                .header("Authorization", "Bearer " + s.accountantJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(gstr3bBody()))
                .andExpect(status().isCreated())
                .andReturn();
        UUID secondId = UUID.fromString(objectMapper.readTree(second.getResponse().getContentAsString()).get("id").asText());
        assertEquals(firstId, secondId);

        List<FiledReturnSnapshot> all = snapshotRepository.findAllByOrganizationId(s.orgId());
        assertEquals(1, all.size());
        assertNull(all.get(0).getSupersededAt());

        mockMvc.perform(post("/api/v1/filed-returns/gstr3b")
                .header("Authorization", "Bearer " + s.accountantJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"filingPeriod":"2026-04","rows":[
                      {"rowCode":"3.1a","taxable":100000,"igst":18001,"cgst":0,"sgst":0,"cess":0},
                      {"rowCode":"4A5","taxable":0,"igst":0,"cgst":4500,"sgst":4500,"cess":0}
                    ]}
                    """))
                .andExpect(status().isCreated())
                .andReturn();
        List<FiledReturnSnapshot> now = snapshotRepository.findAllByOrganizationId(s.orgId());
        assertEquals(2, now.size());
        assertEquals(1, now.stream().filter(x -> x.getSupersededAt() == null).count());
        assertNotNull(now.stream().filter(x -> x.getSupersededAt() != null).findFirst().orElseThrow().getSupersededAt());
        UUID currentId = now.stream().filter(x -> x.getSupersededAt() == null).findFirst().orElseThrow().getId();
        UUID supersededId = now.stream().filter(x -> x.getSupersededAt() != null).findFirst().orElseThrow().getId();
        assertEquals(firstId, supersededId);

        mockMvc.perform(get("/api/v1/filed-returns/snapshots")
                .param("workflowId", s.gstr3bRow().toString())
                .header("Authorization", "Bearer " + s.accountantJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.superseded == false)].id").value(currentId.toString()))
                .andExpect(jsonPath("$[?(@.superseded == true)].id").value(supersededId.toString()));
    }

    @Test
    void quarterly3bCoversQuarterAndIffCoversItsMonth() throws Exception {
        Setup s = quarterlySetup();

        mockMvc.perform(post("/api/v1/filed-returns/gstr3b")
                .header("Authorization", "Bearer " + s.accountantJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"filingPeriod":"2026-06","rows":[
                      {"rowCode":"3.1a","taxable":100000,"igst":18000,"cgst":0,"sgst":0,"cess":0}
                    ]}
                    """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.baselineWorkflowId").value(s.gstr3bRow().toString()))
                .andExpect(jsonPath("$.coverStart").value("2026-04-01"))
                .andExpect(jsonPath("$.coverEnd").value("2026-06-30"));

        SnapshotDraft iff = new SnapshotDraft(FiledFormType.IFF, "2026-04", "29ABCDE1234F1Z5",
                FiledSourceKind.MANUAL_ENTRY, null, null, List.of());
        FiledReturnSnapshot iffSnapshot = importService.save(s.orgId(), "tester", iff);
        assertEquals(LocalDate.of(2026, 4, 1), iffSnapshot.getCoverStart());
        assertEquals(LocalDate.of(2026, 4, 30), iffSnapshot.getCoverEnd());
        assertEquals(s.gstr1Row(), iffSnapshot.getBaselineWorkflowId());
    }

    @Test
    void rejectsUnknownRowCode() throws Exception {
        Setup s = monthlySetup();
        mockMvc.perform(post("/api/v1/filed-returns/gstr3b")
                .header("Authorization", "Bearer " + s.accountantJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"filingPeriod":"2026-04","rows":[
                      {"rowCode":"3.9z","taxable":100000,"igst":18000,"cgst":0,"sgst":0,"cess":0}
                    ]}
                    """))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void operatorMaySaveAndOwnerMayNot() throws Exception {
        Setup s = monthlySetup();
        mockMvc.perform(post("/api/v1/filed-returns/gstr3b")
                .header("Authorization", "Bearer " + s.operatorJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(gstr3bBody()))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/filed-returns/gstr3b")
                .header("Authorization", "Bearer " + s.ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(gstr3bBody()))
                .andExpect(status().isForbidden());
    }
}
