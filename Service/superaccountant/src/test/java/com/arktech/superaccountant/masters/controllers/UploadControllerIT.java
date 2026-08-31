package com.arktech.superaccountant.masters.controllers;

import com.arktech.superaccountant.login.models.ERole;
import com.arktech.superaccountant.login.models.Role;
import com.arktech.superaccountant.login.models.User;
import com.arktech.superaccountant.login.repository.RoleRepository;
import com.arktech.superaccountant.login.repository.UserRepository;
import com.arktech.superaccountant.login.security.jwt.JwtUtils;
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

import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for UploadController role-based access control.
 *
 * Asserts the permission matrix: ACCOUNTANT and OPERATOR may upload and resolve
 * findings; OWNER and AUDITOR_CA are read-only; reads are open to any
 * authenticated user.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Transactional
@TestPropertySource(properties = "JWT_SECRET=test-jwt-secret-must-be-at-least-32-characters-long")
class UploadControllerIT {

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
    private RoleRepository roleRepository;

    @Autowired
    private JwtUtils jwtUtils;

    private String ownerJwt;
    private String accountantJwt;
    private String operatorJwt;
    private String auditorJwt;

    @BeforeEach
    void setUp() {
        Role ownerRole = roleRepository.findByName(ERole.ROLE_OWNER)
                .orElseGet(() -> roleRepository.save(new Role(ERole.ROLE_OWNER)));
        Role accountantRole = roleRepository.findByName(ERole.ROLE_ACCOUNTANT)
                .orElseGet(() -> roleRepository.save(new Role(ERole.ROLE_ACCOUNTANT)));
        Role operatorRole = roleRepository.findByName(ERole.ROLE_OPERATOR)
                .orElseGet(() -> roleRepository.save(new Role(ERole.ROLE_OPERATOR)));
        Role auditorRole = roleRepository.findByName(ERole.ROLE_AUDITOR_CA)
                .orElseGet(() -> roleRepository.save(new Role(ERole.ROLE_AUDITOR_CA)));

        User ownerUser = new User("upload_owner", "upload_owner@test.com", "pass");
        ownerUser.setRole(ownerRole);
        userRepository.save(ownerUser);

        User accountantUser = new User("upload_accountant", "upload_accountant@test.com", "pass");
        accountantUser.setRole(accountantRole);
        userRepository.save(accountantUser);

        User operatorUser = new User("upload_operator", "upload_operator@test.com", "pass");
        operatorUser.setRole(operatorRole);
        userRepository.save(operatorUser);

        User auditorUser = new User("upload_auditor", "upload_auditor@test.com", "pass");
        auditorUser.setRole(auditorRole);
        userRepository.save(auditorUser);

        ownerJwt = jwtUtils.generateJwtTokenForUser("upload_owner", null);
        accountantJwt = jwtUtils.generateJwtTokenForUser("upload_accountant", null);
        operatorJwt = jwtUtils.generateJwtTokenForUser("upload_operator", null);
        auditorJwt = jwtUtils.generateJwtTokenForUser("upload_auditor", null);
    }

    // ── POST /api/v1/uploads ─────────────────────────────────────────────────

    // Scenario 1: OWNER → 403 (OWNER is read-only; cannot upload)
    @Test
    void postUploads_asOwner_returns403() throws Exception {
        MockMultipartFile fakeFile = new MockMultipartFile("file", "test.json",
                "application/json", "{}".getBytes());
        mockMvc.perform(multipart("/api/v1/uploads")
                        .file(fakeFile)
                        .header("Authorization", "Bearer " + ownerJwt))
                .andExpect(status().isForbidden());
    }

    // Scenario 2: ACCOUNTANT → NOT 403 (200 or 400 due to file/org validation)
    @Test
    void postUploads_asAccountant_notForbidden() throws Exception {
        MockMultipartFile fakeFile = new MockMultipartFile("file", "test.json",
                "application/json", "{}".getBytes());
        mockMvc.perform(multipart("/api/v1/uploads")
                        .file(fakeFile)
                        .header("Authorization", "Bearer " + accountantJwt))
                .andExpect(status().is(not(403)));
    }

    // Scenario 3: OPERATOR → NOT 403
    @Test
    void postUploads_asOperator_notForbidden() throws Exception {
        MockMultipartFile fakeFile = new MockMultipartFile("file", "test.json",
                "application/json", "{}".getBytes());
        mockMvc.perform(multipart("/api/v1/uploads")
                        .file(fakeFile)
                        .header("Authorization", "Bearer " + operatorJwt))
                .andExpect(status().is(not(403)));
    }

    // Scenario 4: AUDITOR_CA → 403 (read-only role)
    @Test
    void postUploads_asAuditorCa_returns403() throws Exception {
        MockMultipartFile fakeFile = new MockMultipartFile("file", "test.json",
                "application/json", "{}".getBytes());
        mockMvc.perform(multipart("/api/v1/uploads")
                        .file(fakeFile)
                        .header("Authorization", "Bearer " + auditorJwt))
                .andExpect(status().isForbidden());
    }

    // ── GET /api/v1/uploads ──────────────────────────────────────────────────

    // Scenario 5: OWNER → NOT 403 (read is open to all authenticated users)
    @Test
    void getUploads_asOwner_notForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/uploads")
                        .header("Authorization", "Bearer " + ownerJwt))
                .andExpect(status().is(not(403)));
    }

    // Scenario 6: AUDITOR_CA → NOT 403 (read is open to all authenticated users)
    @Test
    void getUploads_asAuditorCa_notForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/uploads")
                        .header("Authorization", "Bearer " + auditorJwt))
                .andExpect(status().is(not(403)));
    }

    // ── PATCH /api/v1/uploads/{jobId}/mismatches/{findingId}/resolve ─────────

    private static final String ZERO_UUID = "00000000-0000-0000-0000-000000000000";

    // Scenario 7: OWNER → 403 (OWNER cannot resolve mismatches)
    @Test
    void patchResolve_asOwner_returns403() throws Exception {
        mockMvc.perform(patch("/api/v1/uploads/" + ZERO_UUID + "/mismatches/" + ZERO_UUID + "/resolve")
                        .contentType("application/json")
                        .content("{}")
                        .header("Authorization", "Bearer " + ownerJwt))
                .andExpect(status().isForbidden());
    }

    // Scenario 8: ACCOUNTANT → NOT 403 (will be 404 because job doesn't exist)
    @Test
    void patchResolve_asAccountant_notForbidden() throws Exception {
        mockMvc.perform(patch("/api/v1/uploads/" + ZERO_UUID + "/mismatches/" + ZERO_UUID + "/resolve")
                        .contentType("application/json")
                        .content("{}")
                        .header("Authorization", "Bearer " + accountantJwt))
                .andExpect(status().is(not(403)));
    }

    // Scenario 9: OPERATOR → NOT 403
    @Test
    void patchResolve_asOperator_notForbidden() throws Exception {
        mockMvc.perform(patch("/api/v1/uploads/" + ZERO_UUID + "/mismatches/" + ZERO_UUID + "/resolve")
                        .contentType("application/json")
                        .content("{}")
                        .header("Authorization", "Bearer " + operatorJwt))
                .andExpect(status().is(not(403)));
    }

    // Scenario 10: AUDITOR_CA → 403 (read-only role)
    @Test
    void patchResolve_asAuditorCa_returns403() throws Exception {
        mockMvc.perform(patch("/api/v1/uploads/" + ZERO_UUID + "/mismatches/" + ZERO_UUID + "/resolve")
                        .contentType("application/json")
                        .content("{}")
                        .header("Authorization", "Bearer " + auditorJwt))
                .andExpect(status().isForbidden());
    }

    // Scenario 11: No JWT → 401
    @Test
    void postUploads_withNoJwt_returns401() throws Exception {
        MockMultipartFile fakeFile = new MockMultipartFile("file", "test.json",
                "application/json", "{}".getBytes());
        mockMvc.perform(multipart("/api/v1/uploads")
                        .file(fakeFile))
                .andExpect(status().isUnauthorized());
    }
}
