package com.arktech.superaccountant.tds.controllers;

import com.arktech.superaccountant.login.models.ERole;
import com.arktech.superaccountant.login.models.Role;
import com.arktech.superaccountant.login.models.User;
import com.arktech.superaccountant.login.repository.RoleRepository;
import com.arktech.superaccountant.login.repository.UserRepository;
import com.arktech.superaccountant.login.security.services.UserDetailsImpl;
import com.arktech.superaccountant.masters.models.Organization;
import com.arktech.superaccountant.masters.models.UploadJob;
import com.arktech.superaccountant.masters.models.UploadJobStatus;
import com.arktech.superaccountant.masters.models.UploadLedgerSnapshot;
import com.arktech.superaccountant.masters.repository.OrganizationRepository;
import com.arktech.superaccountant.masters.repository.UploadJobRepository;
import com.arktech.superaccountant.masters.repository.UploadLedgerSnapshotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = "JWT_SECRET=test-jwt-secret-must-be-at-least-32-characters-long")
class DebtorTanControllerIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @org.springframework.test.context.DynamicPropertySource
    static void configureProperties(org.springframework.test.context.DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired MockMvc mockMvc;
    @Autowired OrganizationRepository organizationRepository;
    @Autowired RoleRepository roleRepository;
    @Autowired UserRepository userRepository;
    @Autowired UploadJobRepository uploadJobRepository;
    @Autowired UploadLedgerSnapshotRepository snapshotRepository;

    UUID orgId;
    UserDetailsImpl owner;
    UserDetailsImpl accountant;
    UserDetailsImpl operator;
    UserDetailsImpl auditor;

    @BeforeEach
    void seed() {
        Organization org = new Organization();
        org.setName("Debtor TAN Controller IT " + UUID.randomUUID());
        org.setBooksStartDate(LocalDate.of(2025, 4, 1));
        orgId = organizationRepository.save(org).getId();

        owner = principal(ERole.ROLE_OWNER);
        accountant = principal(ERole.ROLE_ACCOUNTANT);
        operator = principal(ERole.ROLE_OPERATOR);
        auditor = principal(ERole.ROLE_AUDITOR_CA);

        UploadJob job = new UploadJob();
        job.setOrganizationId(orgId);
        job.setFileName("masters.xml");
        job.setStatus(UploadJobStatus.COMPLETED);
        job.setUploadedBy("it");
        UUID jobId = uploadJobRepository.save(job).getId();
        snapshot(jobId, "Acme Corp", "guid-1");
        snapshot(jobId, "Beta Traders", "guid-2");
        snapshot(jobId, "Not A Debtor", "guid-3", "Sundry Creditors");
    }

    @Test
    void listsEveryDebtorWithConfiguredOrMissingTan() throws Exception {
        mockMvc.perform(put("/api/v1/tds/debtor-tans")
                        .with(user(accountant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ledgerName\":\"Acme Corp\",\"tan\":\"blra12345c\",\"tallyGuid\":\"guid-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tan").value("BLRA12345C"))
                .andExpect(jsonPath("$.inLatestUpload").value(true));

        mockMvc.perform(get("/api/v1/tds/debtor-tans").with(user(auditor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].ledgerName").value("Acme Corp"))
                .andExpect(jsonPath("$[0].tan").value("BLRA12345C"))
                .andExpect(jsonPath("$[0].inLatestUpload").value(true))
                .andExpect(jsonPath("$[1].ledgerName").value("Beta Traders"))
                .andExpect(jsonPath("$[1].tan").doesNotExist())
                .andExpect(jsonPath("$[1].inLatestUpload").value(true));
    }

    @Test
    void rejectsInvalidTanWithTheSharedMessage() throws Exception {
        mockMvc.perform(put("/api/v1/tds/debtor-tans")
                        .with(user(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ledgerName\":\"Acme Corp\",\"tan\":\"not-a-tan\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error")
                        .value("Enter a valid 10-character TAN (format AAAA99999A, e.g. BLRA12345C)."));
    }

    @Test
    void operatorCannotEditButCanView() throws Exception {
        mockMvc.perform(put("/api/v1/tds/debtor-tans")
                        .with(user(operator))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ledgerName\":\"Acme Corp\",\"tan\":\"BLRA12345C\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/tds/debtor-tans").with(user(operator)))
                .andExpect(status().isOk());
    }

    @Test
    void deleteClearsTanButKeepsTheDebtorInTheLatestUpload() throws Exception {
        mockMvc.perform(put("/api/v1/tds/debtor-tans")
                        .with(user(accountant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ledgerName\":\"Acme Corp\",\"tan\":\"BLRA12345C\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/tds/debtor-tans").param("ledgerName", "Acme Corp")
                        .with(user(accountant)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/tds/debtor-tans").with(user(auditor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].ledgerName").value("Acme Corp"))
                .andExpect(jsonPath("$[0].tan").doesNotExist())
                .andExpect(jsonPath("$[0].inLatestUpload").value(true));
    }

    @Test
    void storedRecordOutsideTheLatestUploadIsListedAsStoredOnly() throws Exception {
        mockMvc.perform(put("/api/v1/tds/debtor-tans")
                        .with(user(accountant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ledgerName\":\"Old Customer\",\"tan\":\"BLRA12345C\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inLatestUpload").value(false));

        mockMvc.perform(get("/api/v1/tds/debtor-tans").with(user(auditor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[2].ledgerName").value("Old Customer"))
                .andExpect(jsonPath("$[2].inLatestUpload").value(false));
    }

    private UserDetailsImpl principal(ERole roleName) {
        Role role = roleRepository.findByName(roleName).orElseThrow();
        User user = new User();
        user.setUsername(roleName.name().substring(5, 8) + "-" + UUID.randomUUID().toString().substring(0, 8));
        user.setPassword("x");
        user.setEmail(UUID.randomUUID() + "@example.com");
        user.setRole(role);
        return UserDetailsImpl.build(userRepository.save(user), orgId);
    }

    private void snapshot(UUID jobId, String name, String guid) {
        snapshot(jobId, name, guid, "Sundry Debtors|Current Assets");
    }

    private void snapshot(UUID jobId, String name, String guid, String groupChain) {
        UploadLedgerSnapshot snapshot = new UploadLedgerSnapshot();
        snapshot.setUploadJobId(jobId);
        snapshot.setOrganizationId(orgId);
        snapshot.setLedgerName(name);
        snapshot.setTallyGuid(guid);
        snapshot.setGroupChain(groupChain);
        snapshotRepository.save(snapshot);
    }
}
