package com.arktech.superaccountant.login.controllers;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.hamcrest.Matchers.containsStringIgnoringCase;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Transactional
@TestPropertySource(properties = "JWT_SECRET=test-jwt-secret-must-be-at-least-32-characters-long")
class AuthControllerIT {

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

    // ── Valid roles → 200 ────────────────────────────────────────────────────

    @Test
    void signup_withOperatorRole_returns200() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"u1\",\"email\":\"u1@x.com\",\"password\":\"pass123\",\"role\":\"operator\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void signup_withAuditorCaRole_returns200() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"u2\",\"email\":\"u2@x.com\",\"password\":\"pass123\",\"role\":\"auditor_ca\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void signup_withOwnerRole_returns200() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"u3\",\"email\":\"u3@x.com\",\"password\":\"pass123\",\"role\":\"owner\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void signup_withAccountantRole_returns200() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"u4\",\"email\":\"u4@x.com\",\"password\":\"pass123\",\"role\":\"accountant\"}"))
                .andExpect(status().isOk());
    }

    // ── Invalid roles → 400 ─────────────────────────────────────────────────

    @Test
    void signup_withCashierRole_returns400WithInvalidRoleMessage() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"u5\",\"email\":\"u5@x.com\",\"password\":\"pass123\",\"role\":\"cashier\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsStringIgnoringCase("Invalid role")));
    }

    @Test
    void signup_withDataEntryOperatorRole_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"u6\",\"email\":\"u6@x.com\",\"password\":\"pass123\",\"role\":\"data_entry_operator\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void signup_withEmptyRole_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"u7\",\"email\":\"u7@x.com\",\"password\":\"pass123\",\"role\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    // ── GAP-1: Invite endpoint is public (no auth required) ─────────────────

    @Test
    void getInvite_withNonexistentToken_returns400NotUnauthorized() throws Exception {
        // No Authorization header — must return 400 (token not found), NOT 401 (unauthorized)
        mockMvc.perform(get("/api/auth/invite/nonexistent-token-value"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsStringIgnoringCase("invalid")));
    }
}
