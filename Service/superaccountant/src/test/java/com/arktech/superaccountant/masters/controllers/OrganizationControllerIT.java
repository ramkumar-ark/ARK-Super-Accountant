package com.arktech.superaccountant.masters.controllers;

import com.arktech.superaccountant.tdsapplicability.models.TdsApplicabilityYear;
import com.arktech.superaccountant.tdsapplicability.models.TdsApplicabilityBasis;
import com.arktech.superaccountant.tdsapplicability.repository.TdsApplicabilityYearRepository;
import com.arktech.superaccountant.workflow.service.period.PeriodCalendar;
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
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Transactional
@TestPropertySource(properties = "JWT_SECRET=test-jwt-secret-must-be-at-least-32-characters-long")
class OrganizationControllerIT {

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

    @Autowired
    private TdsApplicabilityYearRepository tdsApplicabilityYearRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String signupAndLogin(String username, String email, String role) throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"username":"%s","email":"%s","password":"pass1234","role":"%s"}
                    """.formatted(username, email, role)))
                .andExpect(status().isOk());

        MvcResult result = mockMvc.perform(post("/api/auth/signin")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"username":"%s","password":"pass1234"}
                    """.formatted(username)))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper
                .readTree(result.getResponse().getContentAsString())
                .get("token").asText();
    }

    private UUID createOrganization(String jwt) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/organizations")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + jwt)
                .content("""
                    {"name":"Test Org","pan":"AAACA1234A","financialYearStart":4}
                    """))
                .andExpect(status().isCreated())
                .andReturn();

        return UUID.fromString(
                objectMapper.readTree(result.getResponse().getContentAsString())
                        .get("id").asText());
    }

    @Test
    void getMembers_asOwner_returns200WithOwnerInList() throws Exception {
        String jwt = signupAndLogin("owner1", "owner1@test.com", "owner");
        UUID orgId = createOrganization(jwt);

        mockMvc.perform(get("/api/organizations/{id}/members", orgId)
                .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$[0].username").value("owner1"))
                .andExpect(jsonPath("$[0].email").value("owner1@test.com"))
                .andExpect(jsonPath("$[0].role").value("ROLE_OWNER"));
    }

    @Test
    void getOrganization_afterDeductorPatch_returnsStoredDeductorFields() throws Exception {
        String jwt = signupAndLogin("dedowner", "dedowner@test.com", "owner");
        UUID orgId = createOrganization(jwt);

        mockMvc.perform(patch("/api/organizations/{id}/deductor", orgId)
                .header("Authorization", "Bearer " + jwt)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"tan":"AAAA12345A","deductorType":"K","responsiblePersonName":"Ravi Kumar","responsiblePersonDesignation":"Proprietor"}
                    """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/organizations/{id}", orgId)
                .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tan").value("AAAA12345A"))
                .andExpect(jsonPath("$.deductorType").value("K"))
                .andExpect(jsonPath("$.responsiblePersonName").value("Ravi Kumar"))
                .andExpect(jsonPath("$.responsiblePersonDesignation").value("Proprietor"));
    }

    @Test
    void getMembers_asOperator_returns403() throws Exception {
        String ownerJwt = signupAndLogin("owner2", "owner2@test.com", "owner");
        UUID orgId = createOrganization(ownerJwt);
        String operatorJwt = signupAndLogin("operator1", "op1@test.com", "operator");

        mockMvc.perform(get("/api/organizations/{id}/members", orgId)
                .header("Authorization", "Bearer " + operatorJwt))
                .andExpect(status().isForbidden());
    }

    @Test
    void companyRegistrationStoresDerivedTypeAndTan() throws Exception {
        String jwt = signupAndLogin("tdsowner1", "tdsowner1@test.com", "owner");

        MvcResult result = mockMvc.perform(post("/api/organizations")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + jwt)
                .content("""
                    {"name":"Derived Co","pan":"AAACA1234A","tan":"blra12345b"}
                    """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.deductorType").value("K"))
                .andExpect(jsonPath("$.tan").value("BLRA12345B"))
                .andExpect(jsonPath("$.tdsApplicable").value(true))
                .andReturn();

        UUID orgId = UUID.fromString(
                objectMapper.readTree(result.getResponse().getContentAsString())
                        .get("id").asText());

        mockMvc.perform(get("/api/organizations/{id}", orgId)
                .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tan").value("BLRA12345B"))
                .andExpect(jsonPath("$.deductorType").value("K"));

        assertTrue(tdsApplicabilityYearRepository
                .findByOrganizationIdOrderByFinancialYearAsc(orgId).isEmpty());
    }

    @Test
    void individualNotExceededRecordsDeclaredRow() throws Exception {
        String jwt = signupAndLogin("tdsowner2", "tdsowner2@test.com", "owner");
        int currentFy = PeriodCalendar.financialYearOf(LocalDate.now(), 4);

        MvcResult result = mockMvc.perform(post("/api/organizations")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + jwt)
                .content("""
                    {"name":"Solo","pan":"AAAPA1234A","tan":"BLRA12345B","tdsTurnoverExceeded":false}
                    """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.deductorType").value("Q"))
                .andExpect(jsonPath("$.tan").value(""))
                .andExpect(jsonPath("$.tdsApplicable").value(false))
                .andReturn();

        UUID orgId = UUID.fromString(
                objectMapper.readTree(result.getResponse().getContentAsString())
                        .get("id").asText());

        mockMvc.perform(get("/api/organizations/{id}", orgId)
                .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tan").value(""));

        List<TdsApplicabilityYear> rows = tdsApplicabilityYearRepository
                .findByOrganizationIdOrderByFinancialYearAsc(orgId);
        assertEquals(1, rows.size());
        TdsApplicabilityYear row = rows.get(0);
        assertFalse(row.isApplicable());
        assertEquals(TdsApplicabilityBasis.DECLARED, row.getBasis());
        assertEquals(currentFy, row.getFinancialYear());
    }

    @Test
    void individualWithoutAnswerIs400() throws Exception {
        String jwt = signupAndLogin("tdsowner3", "tdsowner3@test.com", "owner");

        mockMvc.perform(post("/api/organizations")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + jwt)
                .content("""
                    {"name":"Solo No Answer","pan":"AAAPA1234A"}
                    """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(
                        "Tell us whether last year's turnover exceeded the TDS limit"));
    }

    @Test
    void invalidTanIs400() throws Exception {
        String jwt = signupAndLogin("tdsowner4", "tdsowner4@test.com", "owner");

        mockMvc.perform(post("/api/organizations")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + jwt)
                .content("""
                    {"name":"Bad Tan Co","pan":"AAACA1234A","tan":"ABC123"}
                    """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(
                        "TAN must be 10 characters in the format: ABCD12345E"));
    }

    @Test
    void registrationWithoutPanRequiresEntityTypeChoice() throws Exception {
        String jwt = signupAndLogin("tdsowner5", "tdsowner5@test.com", "owner");

        mockMvc.perform(post("/api/organizations")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + jwt)
                .content("""
                    {"name":"No Pan Co"}
                    """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Choose the entity type"));
    }
}
