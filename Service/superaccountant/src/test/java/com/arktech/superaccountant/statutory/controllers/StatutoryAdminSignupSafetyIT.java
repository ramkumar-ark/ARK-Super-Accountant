package com.arktech.superaccountant.statutory.controllers;

import com.arktech.superaccountant.statutory.models.Statute;
import com.arktech.superaccountant.statutory.repository.StatutorySectionRepository;
import com.arktech.superaccountant.login.repository.UserRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The app administrator flag has no self-service path: even a client sending an extra
 * "appAdmin": true field on signup must not get the authority, and its token stays
 * refused on every statutory write.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = "JWT_SECRET=test-jwt-secret-must-be-at-least-32-characters-long")
class StatutoryAdminSignupSafetyIT {

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
    private UserRepository userRepository;

    @Autowired
    private StatutorySectionRepository sectionRepository;

    @Test
    void signupCannotSelfGrantAppAdmin() throws Exception {
        String username = "intr-" + java.util.UUID.randomUUID().toString().substring(0, 8);
        String password = "Valid-Passw0rd";
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"email\":\"" + username + "@t.com\","
                                + "\"password\":\"" + password + "\",\"role\":\"operator\","
                                + "\"appAdmin\":true}"))
                .andExpect(status().isOk());

        MvcResult signin = mockMvc.perform(post("/api/auth/signin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andReturn();
        String token = com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
                .readTree(signin.getResponse().getContentAsString()).get("token").asText();

        assertThat(userRepository.findByUsername(username).orElseThrow().isAppAdmin()).isFalse();

        String path = "/api/statutory/sections/" + sectionId194C();
        mockMvc.perform(post(path + "/rules")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"effectiveDateKind\":\"FINANCIAL_YEAR_START\","
                                + "\"effectiveFrom\":\"2027-04-01\",\"rateCompany\":2,"
                                + "\"rateNonCompany\":1,\"higherRateIfNoPan\":20,"
                                + "\"sourceCitation\":\"x\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(path + "/deactivate").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete(path).header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/statutory/sections")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"199T\",\"description\":\"Self-service\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/statutory/audit").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    private String sectionId194C() {
        return sectionRepository.findByStatuteAndCode(Statute.INCOME_TAX, "194C")
                .orElseThrow().getId().toString();
    }
}
