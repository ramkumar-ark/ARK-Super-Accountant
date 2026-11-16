package com.arktech.superaccountant.gst.mapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.arktech.superaccountant.login.models.ERole;
import com.arktech.superaccountant.login.models.Role;
import com.arktech.superaccountant.login.models.User;
import com.arktech.superaccountant.login.repository.RoleRepository;
import com.arktech.superaccountant.login.repository.UserRepository;
import com.arktech.superaccountant.login.security.services.UserDetailsImpl;
import com.arktech.superaccountant.masters.models.LedgerCategory;
import com.arktech.superaccountant.masters.models.Organization;
import com.arktech.superaccountant.masters.models.UploadJob;
import com.arktech.superaccountant.masters.models.UploadJobStatus;
import com.arktech.superaccountant.masters.models.UploadLedgerSnapshot;
import com.arktech.superaccountant.masters.repository.OrganizationRepository;
import com.arktech.superaccountant.masters.repository.UploadJobRepository;
import com.arktech.superaccountant.masters.repository.UploadLedgerSnapshotRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.context.event.ApplicationEvents;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@RecordApplicationEvents
@TestPropertySource(properties = "JWT_SECRET=test-jwt-secret-must-be-at-least-32-characters-long")
class TaxLedgerMappingIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    MockMvc mockMvc;
    ObjectMapper objectMapper = new ObjectMapper();
    @Autowired
    OrganizationRepository organizationRepository;
    @Autowired
    UploadJobRepository uploadJobRepository;
    @Autowired
    UploadLedgerSnapshotRepository snapshotRepository;
    @Autowired
    TaxLedgerMappingRepository mappingRepository;
    @Autowired
    RoleRepository roleRepository;
    @Autowired
    UserRepository userRepository;
    @Autowired
    ApplicationEvents events;

    UUID orgId;
    UserDetailsImpl accountant;
    UserDetailsImpl operator;

    @BeforeEach
    void seed() {
        mappingRepository.deleteAll();

        Organization org = new Organization("Tax Mapping IT " + UUID.randomUUID());
        org.setGstin("27AAPFU0939F1ZV");
        orgId = organizationRepository.save(org).getId();

        UploadJob mastersJob = new UploadJob();
        mastersJob.setOrganizationId(orgId);
        mastersJob.setFileName("masters.json");
        mastersJob.setStatus(UploadJobStatus.COMPLETED);
        mastersJob.setCreatedAt(Instant.now());
        mastersJob = uploadJobRepository.save(mastersJob);

        for (String name : TaxMappingTestData.standardLedgerNames()) {
            gstSnapshot(mastersJob, name);
        }
        gstSnapshot(mastersJob, "Input CGST (RCM)");
        UploadLedgerSnapshot rent = new UploadLedgerSnapshot();
        rent.setUploadJobId(mastersJob.getId());
        rent.setOrganizationId(orgId);
        rent.setLedgerName("Rent");
        rent.setCategory(LedgerCategory.EXPENSE);
        snapshotRepository.save(rent);

        accountant = principal("acct", ERole.ROLE_ACCOUNTANT);
        operator = principal("op", ERole.ROLE_OPERATOR);
    }

    private void gstSnapshot(UploadJob mastersJob, String name) {
        UploadLedgerSnapshot s = new UploadLedgerSnapshot();
        s.setUploadJobId(mastersJob.getId());
        s.setOrganizationId(orgId);
        s.setLedgerName(name);
        s.setCategory(LedgerCategory.GST);
        snapshotRepository.save(s);
    }

    private UserDetailsImpl principal(String name, ERole roleName) {
        Role role = roleRepository.findByName(roleName).orElseThrow();
        User u = new User();
        u.setUsername(name + "-" + UUID.randomUUID());
        u.setPassword("x");
        u.setEmail(name + "-" + UUID.randomUUID() + "@t.com");
        u.setRole(role);
        return UserDetailsImpl.build(userRepository.save(u), orgId);
    }

    @Test
    void getShowsSuggestionsUnsaved() throws Exception {
        mockMvc.perform(get("/api/v1/gst/tax-ledger-mapping").with(user(accountant)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.complete").value(false))
                .andExpect(jsonPath("$.blockers[0]").value(
                        org.hamcrest.Matchers.startsWith("19 tax ledgers not mapped: CGST Cash Balance A/c")))
                .andExpect(jsonPath("$.ledgers.length()").value(19))
                .andExpect(jsonPath("$.ledgers[?(@.ledgerName == 'CGST Cash Balance A/c')].suggestedRole").value("CASH_BALANCE"))
                .andExpect(jsonPath("$.ledgers[?(@.ledgerName == 'IGST Liability Balance A/c')].suggestedRole").value("LIABILITY_BALANCE"))
                .andExpect(jsonPath("$.ledgers[?(@.ledgerName == 'SGST Credit Balance A/c')].suggestedRole").value("CREDIT_BALANCE"))
                .andExpect(jsonPath("$.ledgers[?(@.ledgerName == 'CGST TDS')].suggestedRole").value("GST_TDS"))
                .andExpect(jsonPath("$.ledgers[?(@.ledgerName == 'Output CGST')].saved").value(false))
                .andExpect(jsonPath("$.ledgers[?(@.ledgerName == 'Output CGST')].suggestedRole").value("OUTPUT"))
                .andExpect(jsonPath("$.ledgers[?(@.ledgerName == 'Output CGST')].suggestedHead").value("CGST"))
                .andExpect(jsonPath("$.ledgers[?(@.ledgerName == 'Input CGST (RCM)')].suggestedRole").value("RCM_INPUT"))
                .andExpect(jsonPath("$.ledgers[?(@.ledgerName == 'Input CGST (RCM)')].suggestedHead").value("CGST"));
    }

    @Test
    void accountantSaveCompletesMapping() throws Exception {
        saveAllGstLedgers(accountant);

        mockMvc.perform(get("/api/v1/gst/tax-ledger-mapping").with(user(accountant)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.complete").value(true))
                .andExpect(jsonPath("$.blockers.length()").value(0))
                .andExpect(jsonPath("$.ledgers[?(@.ledgerName == 'Output CGST')].saved").value(true))
                .andExpect(jsonPath("$.ledgers[?(@.ledgerName == 'CGST Cash Balance A/c')].role").value("CASH_BALANCE"))
                .andExpect(jsonPath("$.ledgers[?(@.ledgerName == 'Output CGST')].role").value("OUTPUT"))
                .andExpect(jsonPath("$.ledgers[?(@.ledgerName == 'Output CGST')].head").value("CGST"))
                .andExpect(jsonPath("$.ledgers[?(@.ledgerName == 'Input CGST (RCM)')].role").value("RCM_LIABILITY"));
    }

    @Test
    void operatorCannotSave() throws Exception {
        mockMvc.perform(put("/api/v1/gst/tax-ledger-mapping")
                        .with(user(operator))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"items":[{"ledgerName":"Output CGST","role":"OUTPUT","head":"CGST"}]}
                            """))
                .andExpect(status().isForbidden());
        assertThat(mappingRepository.count()).isZero();
    }

    @Test
    void saveRejectsUnknownLedgerAndMissingHead() throws Exception {
        mockMvc.perform(put("/api/v1/gst/tax-ledger-mapping")
                        .with(user(accountant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"items":[{"ledgerName":"Rent","role":"OUTPUT","head":"CGST"}]}
                            """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Unknown GST ledger: Rent"));

        mockMvc.perform(put("/api/v1/gst/tax-ledger-mapping")
                        .with(user(accountant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"items":[{"ledgerName":"Output CGST","role":"OUTPUT"}]}
                            """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Head is required for: Output CGST"));

        assertThat(mappingRepository.count()).isZero();
    }

    @Test
    void savePublishesEvent() throws Exception {
        saveAllGstLedgers(accountant);

        assertThat(events.stream(TaxLedgerMappingSavedEvent.class)
                .anyMatch(e -> e.organizationId().equals(orgId))).isTrue();
    }

    private void saveAllGstLedgers(UserDetailsImpl principal) throws Exception {
        mockMvc.perform(put("/api/v1/gst/tax-ledger-mapping")
                        .with(user(principal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(TaxMappingTestData.standardMappingItemsJson()))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/gst/tax-ledger-mapping")
                        .with(user(principal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"items":[{"ledgerName":"Input CGST (RCM)","role":"RCM_LIABILITY","head":"CGST"}]}
                            """))
                .andExpect(status().isOk());
    }
}
