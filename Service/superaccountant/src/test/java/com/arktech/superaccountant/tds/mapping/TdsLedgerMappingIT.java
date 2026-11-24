package com.arktech.superaccountant.tds.mapping;

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
import com.arktech.superaccountant.tdsapplicability.TdsApplicabilityService;
import com.arktech.superaccountant.tdsapplicability.repository.TdsApplicabilityYearRepository;
import com.arktech.superaccountant.workflow.service.period.PeriodCalendar;
import java.time.Instant;
import java.time.LocalDate;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@RecordApplicationEvents
@WithMockUser
@TestPropertySource(properties = "JWT_SECRET=test-jwt-secret-must-be-at-least-32-characters-long")
class TdsLedgerMappingIT {

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
    @Autowired
    OrganizationRepository organizationRepository;
    @Autowired
    UploadJobRepository uploadJobRepository;
    @Autowired
    UploadLedgerSnapshotRepository snapshotRepository;
    @Autowired
    TdsLedgerMappingRepository mappingRepository;
    @Autowired
    TdsApplicabilityYearRepository applicabilityYearRepository;
    @Autowired
    RoleRepository roleRepository;
    @Autowired
    UserRepository userRepository;
    @Autowired
    TdsApplicabilityService tdsApplicabilityService;
    @Autowired
    ApplicationEvents events;

    @BeforeEach
    void cleanSeedData() {
        mappingRepository.deleteAll();
        applicabilityYearRepository.deleteAll();
        snapshotRepository.deleteAll();
        uploadJobRepository.deleteAll();
        userRepository.deleteAll();
        organizationRepository.deleteAll();
    }

    /** Narayanan-shaped masters: a saved-or-not payable, an unsaved receivable, an expense payable. */
    private void seedMasters(UUID orgId) {
        UploadJob mastersJob = new UploadJob();
        mastersJob.setOrganizationId(orgId);
        mastersJob.setFileName("masters.json");
        mastersJob.setStatus(UploadJobStatus.COMPLETED);
        mastersJob.setCreatedAt(Instant.now());
        mastersJob = uploadJobRepository.save(mastersJob);

        snapshot(mastersJob, "TDS Payable 194C", "Direct Expenses", "Expenses|Direct Expenses|Duties & Taxes", false);
        snapshot(mastersJob, "TDS Receivable 206A", "Current Assets", "Current Assets|Loans & Advances", false);
        snapshot(mastersJob, "Rent", "Indirect Expenses", "Indirect Expenses|Duties & Taxes", true);
    }

    private void snapshot(UploadJob mastersJob, String name, String parentGroup, String groupChain,
            boolean tdsApplicable) {
        UploadLedgerSnapshot s = new UploadLedgerSnapshot();
        s.setUploadJobId(mastersJob.getId());
        s.setOrganizationId(mastersJob.getOrganizationId());
        s.setLedgerName(name);
        s.setParentGroup(parentGroup);
        s.setGroupChain(groupChain);
        s.setTdsApplicable(tdsApplicable);
        s.setCategory(LedgerCategory.EXPENSE);
        snapshotRepository.save(s);
    }

    private Organization seedOrganization(boolean deductor) {
        Organization org = new Organization("TDS Mapping IT " + UUID.randomUUID());
        if (deductor) {
            org.setPan("ABCCS1234F");
        }
        org.setGstin("27AAPFU0939F1ZV");
        Organization saved = organizationRepository.save(org);
        if (!deductor) {
            tdsApplicabilityService.recordDeclared(saved.getId(),
                    PeriodCalendar.financialYearOf(LocalDate.now(), 4), false, null, null, "it");
        }
        return saved;
    }

    private UserDetailsImpl principal(String name, ERole roleName, UUID orgId) {
        Role role = roleRepository.findByName(roleName).orElseThrow();
        User u = new User();
        u.setUsername(name + "-" + UUID.randomUUID());
        u.setPassword("x");
        u.setEmail(name + "-" + UUID.randomUUID() + "@t.com");
        u.setRole(role);
        return UserDetailsImpl.build(userRepository.save(u), orgId);
    }

    @Test
    void getShowsNarayananCandidatesUnsaved() throws Exception {
        UUID orgId = seedOrganization(true).getId();
        seedMasters(orgId);
        UserDetailsImpl accountant = principal("acct", ERole.ROLE_ACCOUNTANT, orgId);

        mockMvc.perform(get("/api/v1/tds/ledger-mapping").with(user(accountant)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.complete").value(false))
                .andExpect(jsonPath("$.blockers.length()").value(1))
                .andExpect(jsonPath("$.blockers[0]").value(
                        org.hamcrest.Matchers.is("2 TDS ledgers not mapped: Rent, TDS Payable 194C")))
                .andExpect(jsonPath("$.receivableWarnings[0]").value(
                        "1 TDS receivable ledgers not mapped - receivable checks skip them until mapped"))
                .andExpect(jsonPath("$.ledgers.length()").value(3))
                .andExpect(jsonPath("$.ledgers[?(@.ledgerName == 'TDS Payable 194C')].saved").value(false))
                .andExpect(jsonPath("$.ledgers[?(@.ledgerName == 'TDS Payable 194C')].suggestedRole")
                        .value("TDS_PAYABLE"))
                .andExpect(jsonPath("$.ledgers[?(@.ledgerName == 'TDS Payable 194C')].suggestedSection")
                        .value("194C"))
                .andExpect(jsonPath("$.ledgers[?(@.ledgerName == 'TDS Payable 194C')].area").value("PAYABLE"))
                .andExpect(jsonPath("$.ledgers[?(@.ledgerName == 'TDS Receivable 206A')].saved").value(false))
                .andExpect(jsonPath("$.ledgers[?(@.ledgerName == 'TDS Receivable 206A')].suggestedRole")
                        .value("TDS_RECEIVABLE"))
                .andExpect(jsonPath("$.ledgers[?(@.ledgerName == 'TDS Receivable 206A')].suggestedSection")
                        .value(org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.nullValue())))
                .andExpect(jsonPath("$.ledgers[?(@.ledgerName == 'TDS Receivable 206A')].area").value("RECEIVABLE"))
                .andExpect(jsonPath("$.ledgers[?(@.ledgerName == 'Rent')].suggestedRole").value("TDS_PAYABLE"));
    }

    @Test
    void nonDeductorOrgViewsCompleteWithReceivableRowsListed() throws Exception {
        UUID orgId = seedOrganization(false).getId();
        seedMasters(orgId);
        UserDetailsImpl accountant = principal("acct", ERole.ROLE_ACCOUNTANT, orgId);

        mockMvc.perform(get("/api/v1/tds/ledger-mapping").with(user(accountant)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.complete").value(true))
                .andExpect(jsonPath("$.blockers.length()").value(0))
                .andExpect(jsonPath("$.ledgers.length()").value(3))
                .andExpect(jsonPath("$.ledgers[?(@.ledgerName == 'TDS Receivable 206A')].suggestedRole")
                        .value("TDS_RECEIVABLE"))
                .andExpect(jsonPath("$.ledgers[?(@.ledgerName == 'TDS Payable 194C')].suggestedRole")
                        .value("TDS_PAYABLE"));
    }

    @Test
    void accountantSaveCompletesMapping() throws Exception {
        UUID orgId = seedOrganization(true).getId();
        seedMasters(orgId);
        UserDetailsImpl accountant = principal("acct", ERole.ROLE_ACCOUNTANT, orgId);

        mockMvc.perform(put("/api/v1/tds/ledger-mapping")
                        .with(user(accountant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"items":[
                              {"ledgerName":"TDS Payable 194C","role":"TDS_PAYABLE","section":"194C"},
                              {"ledgerName":"TDS Receivable 206A","role":"TDS_RECEIVABLE"},
                              {"ledgerName":"Rent","role":"NOT_TDS"}
                            ]}
                            """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/tds/ledger-mapping").with(user(accountant)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.complete").value(true))
                .andExpect(jsonPath("$.blockers.length()").value(0))
                .andExpect(jsonPath("$.receivableWarnings.length()").value(0))
                .andExpect(jsonPath("$.ledgers[?(@.ledgerName == 'TDS Payable 194C')].saved").value(true))
                .andExpect(jsonPath("$.ledgers[?(@.ledgerName == 'TDS Payable 194C')].role").value("TDS_PAYABLE"))
                .andExpect(jsonPath("$.ledgers[?(@.ledgerName == 'TDS Payable 194C')].section").value("194C"))
                .andExpect(jsonPath("$.ledgers[?(@.ledgerName == 'TDS Receivable 206A')].saved").value(true))
                .andExpect(jsonPath("$.ledgers[?(@.ledgerName == 'TDS Receivable 206A')].role")
                        .value("TDS_RECEIVABLE"))
                .andExpect(jsonPath("$.ledgers[?(@.ledgerName == 'Rent')].role").value("NOT_TDS"));
    }

    @Test
    void operatorCannotSave() throws Exception {
        UUID orgId = seedOrganization(true).getId();
        seedMasters(orgId);
        UserDetailsImpl operator = principal("op", ERole.ROLE_OPERATOR, orgId);

        mockMvc.perform(put("/api/v1/tds/ledger-mapping")
                        .with(user(operator))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"items":[{"ledgerName":"Rent","role":"NOT_TDS"}]}
                            """))
                .andExpect(status().isForbidden());
        assertThat(mappingRepository.count()).isZero();
    }

    @Test
    void saveRejectsUnknownLedgerAndInvalidSection() throws Exception {
        UUID orgId = seedOrganization(true).getId();
        seedMasters(orgId);
        UserDetailsImpl accountant = principal("acct", ERole.ROLE_ACCOUNTANT, orgId);

        mockMvc.perform(put("/api/v1/tds/ledger-mapping")
                        .with(user(accountant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"items":[{"ledgerName":"Not There","role":"NOT_TDS"}]}
                            """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Unknown TDS ledger: Not There"));

        mockMvc.perform(put("/api/v1/tds/ledger-mapping")
                        .with(user(accountant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"items":[{"ledgerName":"TDS Receivable 206A","role":"TDS_RECEIVABLE","section":"194C"}]}
                            """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Section must be empty when role is not TDS payable."));

        mockMvc.perform(put("/api/v1/tds/ledger-mapping")
                        .with(user(accountant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"items":[{"ledgerName":"Rent","role":"TDS_PAYABLE","section":"194X"}]}
                            """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Unknown TDS section: 194X"));

        assertThat(mappingRepository.count()).isZero();
    }

    @Test
    void savePublishesEvent() throws Exception {
        UUID orgId = seedOrganization(true).getId();
        seedMasters(orgId);
        UserDetailsImpl accountant = principal("acct", ERole.ROLE_ACCOUNTANT, orgId);

        mockMvc.perform(put("/api/v1/tds/ledger-mapping")
                        .with(user(accountant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"items":[{"ledgerName":"Rent","role":"NOT_TDS"}]}
                            """))
                .andExpect(status().isOk());

        assertThat(events.stream(TdsLedgerMappingSavedEvent.class)
                .anyMatch(e -> e.organizationId().equals(orgId))).isTrue();
    }

    @Test
    void noOrgUserGetsBadRequestWithMessage() throws Exception {
        UserDetailsImpl noOrg = principal("noorg", ERole.ROLE_ACCOUNTANT, null);

        mockMvc.perform(get("/api/v1/tds/ledger-mapping").with(user(noOrg)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("User is not linked to an organization."));

        mockMvc.perform(put("/api/v1/tds/ledger-mapping")
                        .with(user(noOrg))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"items":[{"ledgerName":"Rent","role":"NOT_TDS"}]}
                            """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("User is not linked to an organization."));
    }
}
