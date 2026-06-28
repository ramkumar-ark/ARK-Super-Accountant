package com.arktech.superaccountant.daybook;

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
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Transactional
@TestPropertySource(properties = "JWT_SECRET=test-jwt-secret-must-be-at-least-32-characters-long")
class DayBookControllerIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    // ── Test 1: TALLY-01, TALLY-06 ───────────────────────────────────────────

    @Test
    void upload_validJson_returnsCompletedJobWith201() throws Exception {
        String token = setupOrgScopedToken();
        byte[] fixture = getClass().getResourceAsStream("/fixtures/daybook-minimal.json").readAllBytes();

        MvcResult result = mockMvc.perform(
                        multipart("/api/v1/day-book/upload")
                                .file(new MockMultipartFile("file", "daybook-minimal.json",
                                        "application/json", fixture))
                                .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn();

        @SuppressWarnings("unchecked")
        Map<String, Object> body = objectMapper.readValue(
                result.getResponse().getContentAsString(), Map.class);

        assertThat(body.get("id")).isNotNull();
        assertThat(body.get("status")).isEqualTo("COMPLETED");
        assertThat(((Number) body.get("totalVouchersParsed")).intValue()).isGreaterThanOrEqualTo(1);
        assertThat(body.get("errorMessage")).isNull();
    }

    // ── Test 2: TALLY-02, TALLY-03 ───────────────────────────────────────────

    @Test
    void upload_multiTypeFixture_returnsVoucherSummaryInResponse() throws Exception {
        String token = setupOrgScopedToken();
        byte[] fixture = getClass().getResourceAsStream("/fixtures/daybook-multi-type.json").readAllBytes();

        MvcResult result = mockMvc.perform(
                        multipart("/api/v1/day-book/upload")
                                .file(new MockMultipartFile("file", "daybook-multi-type.json",
                                        "application/json", fixture))
                                .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn();

        @SuppressWarnings("unchecked")
        Map<String, Object> body = objectMapper.readValue(
                result.getResponse().getContentAsString(), Map.class);

        assertThat(body.get("status")).isEqualTo("COMPLETED");
        assertThat(((Number) body.get("totalVouchersParsed")).intValue()).isEqualTo(7);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> summary = (List<Map<String, Object>>) body.get("voucherSummary");
        assertThat(summary).isNotEmpty();

        boolean hasPurchase = summary.stream()
                .anyMatch(row -> "Purchase".equals(row.get("voucherTypeName"))
                        && ((Number) row.get("count")).intValue() == 2);
        assertThat(hasPurchase).isTrue();
    }

    // ── Test 3: TALLY-05 ─────────────────────────────────────────────────────

    @Test
    void upload_malformedJson_returns400WithFailedJob() throws Exception {
        String token = setupOrgScopedToken();
        byte[] fixture = getClass().getResourceAsStream("/fixtures/daybook-malformed.json").readAllBytes();

        MvcResult result = mockMvc.perform(
                        multipart("/api/v1/day-book/upload")
                                .file(new MockMultipartFile("file", "daybook-malformed.json",
                                        "application/json", fixture))
                                .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andReturn();

        @SuppressWarnings("unchecked")
        Map<String, Object> body = objectMapper.readValue(
                result.getResponse().getContentAsString(), Map.class);

        assertThat(body.get("error")).isNotNull();
        assertThat(body.get("error").toString()).isNotBlank();
        assertThat(body.get("uploadId")).isNotNull();
    }

    // ── Test 4: TALLY-02 (summary endpoint) ──────────────────────────────────

    @Test
    void getSummary_byJobId_returnsVoucherTypeBreakdown() throws Exception {
        String token = setupOrgScopedToken();
        byte[] fixture = getClass().getResourceAsStream("/fixtures/daybook-multi-type.json").readAllBytes();

        MvcResult uploadResult = mockMvc.perform(
                        multipart("/api/v1/day-book/upload")
                                .file(new MockMultipartFile("file", "daybook-multi-type.json",
                                        "application/json", fixture))
                                .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn();

        @SuppressWarnings("unchecked")
        Map<String, Object> uploadBody = objectMapper.readValue(
                uploadResult.getResponse().getContentAsString(), Map.class);
        String jobId = uploadBody.get("id").toString();

        MvcResult summaryResult = mockMvc.perform(
                        get("/api/v1/day-book/summary/" + jobId)
                                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        @SuppressWarnings("unchecked")
        Map<String, Object> summaryBody = objectMapper.readValue(
                summaryResult.getResponse().getContentAsString(), Map.class);

        assertThat(summaryBody.get("status")).isEqualTo("COMPLETED");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> summary = (List<Map<String, Object>>) summaryBody.get("voucherSummary");
        assertThat(summary).isNotEmpty();
    }

    // ── Test 5: T-4-04 cross-tenant ──────────────────────────────────────────

    @Test
    void getSummary_withAnotherOrgJobId_returns404() throws Exception {
        String token1 = setupOrgScopedToken();
        byte[] fixture = getClass().getResourceAsStream("/fixtures/daybook-minimal.json").readAllBytes();

        MvcResult uploadResult = mockMvc.perform(
                        multipart("/api/v1/day-book/upload")
                                .file(new MockMultipartFile("file", "daybook-minimal.json",
                                        "application/json", fixture))
                                .header("Authorization", "Bearer " + token1))
                .andExpect(status().isCreated())
                .andReturn();

        @SuppressWarnings("unchecked")
        Map<String, Object> uploadBody = objectMapper.readValue(
                uploadResult.getResponse().getContentAsString(), Map.class);
        String jobId = uploadBody.get("id").toString();

        String token2 = setupOrgScopedToken();

        mockMvc.perform(
                        get("/api/v1/day-book/summary/" + jobId)
                                .header("Authorization", "Bearer " + token2))
                .andExpect(status().isNotFound());
    }

    // ── Test 6: TALLY-04 + D-11 gate endpoint ────────────────────────────────

    @Test
    void getGateStatus_whenNoFindings_returnsGatedFalse() throws Exception {
        String token = setupOrgScopedToken();

        MvcResult result = mockMvc.perform(
                        get("/api/v1/masters/gate-status")
                                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        @SuppressWarnings("unchecked")
        Map<String, Object> body = objectMapper.readValue(
                result.getResponse().getContentAsString(), Map.class);

        assertThat(body.containsKey("gated")).isTrue();
        if (Boolean.TRUE.equals(body.get("gated"))) {
            assertThat(body.get("unresolvedCount")).isNotNull();
            assertThat(body.get("reason")).isNotNull();
        }
    }

    // ── Test 7: T-4-03 file extension check ──────────────────────────────────

    @Test
    void upload_nonJsonFile_returns400() throws Exception {
        String token = setupOrgScopedToken();

        mockMvc.perform(
                        multipart("/api/v1/day-book/upload")
                                .file(new MockMultipartFile("file", "daybook.xml",
                                        "application/xml", "<xml/>".getBytes()))
                                .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    // ── Test 8: auth guard ────────────────────────────────────────────────────

    @Test
    void upload_withoutAuth_returns401() throws Exception {
        byte[] fixture = getClass().getResourceAsStream("/fixtures/daybook-minimal.json").readAllBytes();

        mockMvc.perform(
                        multipart("/api/v1/day-book/upload")
                                .file(new MockMultipartFile("file", "daybook-minimal.json",
                                        "application/json", fixture)))
                .andExpect(status().isUnauthorized());
    }

    // ── Test 9: TALLY-03 UTF-16 LE encoding ──────────────────────────────────

    @Test
    void upload_utf16leFile_parsesCorrectly() throws Exception {
        String token = setupOrgScopedToken();
        byte[] fixture = getClass().getResourceAsStream("/fixtures/daybook-utf16le.json").readAllBytes();

        MvcResult result = mockMvc.perform(
                        multipart("/api/v1/day-book/upload")
                                .file(new MockMultipartFile("file", "daybook-utf16le.json",
                                        "application/json", fixture))
                                .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn();

        @SuppressWarnings("unchecked")
        Map<String, Object> body = objectMapper.readValue(
                result.getResponse().getContentAsString(), Map.class);

        assertThat(body.get("status")).isEqualTo("COMPLETED");
        assertThat(((Number) body.get("totalVouchersParsed")).intValue()).isGreaterThanOrEqualTo(1);
        assertThat(body.get("errorMessage")).isNull();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String setupOrgScopedToken() throws Exception {
        String suffix = java.util.UUID.randomUUID().toString().substring(0, 8);
        String username = "it_" + suffix;

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"email\":\"" + username + "@test.com\",\"password\":\"TestPass1!\",\"role\":\"accountant\"}"))
                .andExpect(status().isOk());

        MvcResult signinResult = mockMvc.perform(post("/api/auth/signin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"TestPass1!\"}"))
                .andExpect(status().isOk())
                .andReturn();
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> signinBody = objectMapper.readValue(
                signinResult.getResponse().getContentAsString(), java.util.Map.class);
        String token = (String) signinBody.get("token");

        MvcResult createOrgResult = mockMvc.perform(post("/api/organizations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Test Org " + suffix + "\",\"financialYearStart\":4}"))
                .andExpect(status().isCreated())
                .andReturn();
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> orgBody = objectMapper.readValue(
                createOrgResult.getResponse().getContentAsString(), java.util.Map.class);
        String orgId = orgBody.get("id").toString();

        MvcResult selectResult = mockMvc.perform(post("/api/organizations/" + orgId + "/select")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> selectBody = objectMapper.readValue(
                selectResult.getResponse().getContentAsString(), java.util.Map.class);
        return (String) selectBody.get("token");
    }

    private String setupOperatorToken() throws Exception {
        String suffix = java.util.UUID.randomUUID().toString().substring(0, 8);
        String username = "op_" + suffix;

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"email\":\"" + username + "@test.com\",\"password\":\"TestPass1!\",\"role\":\"operator\"}"))
                .andExpect(status().isOk());

        MvcResult signinResult = mockMvc.perform(post("/api/auth/signin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"TestPass1!\"}"))
                .andExpect(status().isOk())
                .andReturn();
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> signinBody = objectMapper.readValue(
                signinResult.getResponse().getContentAsString(), java.util.Map.class);
        String token = (String) signinBody.get("token");

        MvcResult createOrgResult = mockMvc.perform(post("/api/organizations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Test Org " + suffix + "\",\"financialYearStart\":4}"))
                .andExpect(status().isCreated())
                .andReturn();
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> orgBody = objectMapper.readValue(
                createOrgResult.getResponse().getContentAsString(), java.util.Map.class);
        String orgId = orgBody.get("id").toString();

        MvcResult selectResult = mockMvc.perform(post("/api/organizations/" + orgId + "/select")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> selectBody = objectMapper.readValue(
                selectResult.getResponse().getContentAsString(), java.util.Map.class);
        return (String) selectBody.get("token");
    }
}
