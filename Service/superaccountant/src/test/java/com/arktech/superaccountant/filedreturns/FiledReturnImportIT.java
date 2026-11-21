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
import org.springframework.mock.web.MockMultipartFile;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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
                .andExpect(jsonPath("$.message").value(
                        "No baseline period covers 2025-01. Create a baseline period for "
                                + "January 2025 in Settings > Baseline periods, then import this return again."));
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
    void gstr1JsonUploadCreatesSnapshot() throws Exception {
        Setup s = monthlySetup();
        byte[] json = getClass().getResourceAsStream("/filedreturns/gstr1-synthetic.json").readAllBytes();
        MockMultipartFile file = new MockMultipartFile("file", "gstr1.json", "application/json", json);

        mockMvc.perform(multipart("/api/v1/filed-returns/gstr1/json").file(file)
                .param("form", "GSTR1")
                .header("Authorization", "Bearer " + s.accountantJwt()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sourceKind").value("PORTAL_JSON"))
                .andExpect(jsonPath("$.parserVersion").value("gstr1-json-1"))
                .andExpect(jsonPath("$.lines.length()").value(15))
                .andExpect(jsonPath("$.fileId").exists());
    }

    @Test
    void gstr1JsonUploadWithoutMultipartReturnsBadRequestWithCleanMessage() throws Exception {
        Setup s = monthlySetup();

        mockMvc.perform(post("/api/v1/filed-returns/gstr1/json")
                .param("form", "GSTR1")
                .header("Authorization", "Bearer " + s.accountantJwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Current request is not a multipart request"))
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Test
    void pdfParseThenConfirmSavesPortalPdfSnapshot() throws Exception {
        Setup s = monthlySetup();
        byte[] pdf = gstr3bPdf(
                "GSTR-3B",
                "GSTIN: 29ABCDE1234F1Z5",
                "Year: 2026-27   Period: April",
                "3.1(a) Outward taxable supplies (other than zero rated)",
                "1,00,000.00  18,000.00  9,000.00  9,000.00  0.00",
                "4A(5) All other ITC",
                "5,000.00  2,500.00  2,500.00  0.00");
        MockMultipartFile file = new MockMultipartFile("file", "gstr3b.pdf", "application/pdf", pdf);

        MvcResult parse = mockMvc.perform(multipart("/api/v1/filed-returns/gstr3b/pdf").file(file)
                .header("Authorization", "Bearer " + s.accountantJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gstin").value("29ABCDE1234F1Z5"))
                .andExpect(jsonPath("$.filingPeriod").value("2026-04"))
                .andExpect(jsonPath("$.rows.length()").value(2))
                .andExpect(jsonPath("$.rows[0].rowCode").value("3.1a"))
                .andExpect(jsonPath("$.rows[0].taxable").value(100000.00))
                .andExpect(jsonPath("$.rows[1].rowCode").value("4A5"))
                .andExpect(jsonPath("$.parserVersion").value("gstr3b-pdf-1"))
                .andExpect(jsonPath("$.fileId").exists())
                .andReturn();

        String parsedBody = parse.getResponse().getContentAsString();
        ObjectMapper mapper = new ObjectMapper();
        var root = mapper.readTree(parsedBody);
        String fileId = root.get("fileId").asText();
        String filingPeriod = root.get("filingPeriod").asText();
        String confirmBody = """
            {"filingPeriod":"%s","fileId":"%s","parserVersion":"gstr3b-pdf-1","rows":%s}
            """.formatted(filingPeriod, fileId, root.get("rows").toString());

        mockMvc.perform(post("/api/v1/filed-returns/gstr3b")
                .header("Authorization", "Bearer " + s.accountantJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(confirmBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sourceKind").value("PORTAL_PDF"))
                .andExpect(jsonPath("$.fileId").value(fileId));
    }

    @Test
    void garbagePdfGives422AndManualEntryStillSaves() throws Exception {
        Setup s = monthlySetup();
        byte[] pdf = gstr3bPdf("this is not a gstr3b document", "just some prose");
        MockMultipartFile file = new MockMultipartFile("file", "junk.pdf", "application/pdf", pdf);

        MvcResult parse = mockMvc.perform(multipart("/api/v1/filed-returns/gstr3b/pdf").file(file)
                .header("Authorization", "Bearer " + s.accountantJwt()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.fileId").exists())
                .andReturn();

        mockMvc.perform(post("/api/v1/filed-returns/gstr3b")
                .header("Authorization", "Bearer " + s.accountantJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(gstr3bBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sourceKind").value("MANUAL_ENTRY"));

        assertEquals(1, snapshotRepository.findAllByOrganizationId(s.orgId()).size());
    }

    private byte[] gstr3bPdf(String... lines) throws Exception {
        try (var doc = new org.apache.pdfbox.pdmodel.PDDocument()) {
            var page = new org.apache.pdfbox.pdmodel.PDPage();
            doc.addPage(page);
            try (var cs = new org.apache.pdfbox.pdmodel.PDPageContentStream(doc, page)) {
                cs.setFont(new org.apache.pdfbox.pdmodel.font.PDType1Font(
                        org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA), 10);
                float y = 750;
                for (String line : lines) {
                    cs.beginText();
                    cs.newLineAtOffset(50, y);
                    cs.showText(line);
                    cs.endText();
                    y -= 15;
                }
            }
            var out = new java.io.ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
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

    @Test
    void crossOrgSnapshotAndFileAreInvisibleAndMalformedInputGives422() throws Exception {
        Setup a = monthlySetup();
        Setup b = monthlySetup();
        MvcResult saved = mockMvc.perform(post("/api/v1/filed-returns/gstr3b")
                .header("Authorization", "Bearer " + a.accountantJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(gstr3bBody()))
                .andExpect(status().isCreated()).andReturn();
        String snapId = objectMapper.readTree(saved.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(get("/api/v1/filed-returns/snapshots/{id}", snapId)
                .header("Authorization", "Bearer " + a.accountantJwt()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/filed-returns/snapshots/{id}", snapId)
                .header("Authorization", "Bearer " + b.accountantJwt()))
                .andExpect(status().isNotFound());

        MockMultipartFile junk = new MockMultipartFile("file", "j.pdf", "application/pdf", gstr3bPdf("x"));
        MvcResult up = mockMvc.perform(multipart("/api/v1/filed-returns/gstr3b/pdf").file(junk)
                .header("Authorization", "Bearer " + a.accountantJwt()))
                .andExpect(status().isUnprocessableEntity()).andReturn();
        String fileId = objectMapper.readTree(up.getResponse().getContentAsString()).get("fileId").asText();
        mockMvc.perform(post("/api/v1/filed-returns/gstr3b")
                .header("Authorization", "Bearer " + b.accountantJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"filingPeriod\":\"2026-04\",\"fileId\":\"" + fileId + "\",\"rows\":[]}"))
                .andExpect(status().isUnprocessableEntity());

        for (String body : List.of(
                "{\"filingPeriod\":\"2024-13\",\"rows\":[]}",
                "{\"rows\":[]}",
                "{\"filingPeriod\":\"2026-04\"}",
                "{\"filingPeriod\":\"2026-04\",\"rows\":[null]}")) {
            mockMvc.perform(post("/api/v1/filed-returns/gstr3b")
                    .header("Authorization", "Bearer " + a.accountantJwt())
                    .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isUnprocessableEntity());
        }
    }
}
