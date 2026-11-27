package com.arktech.superaccountant.statutory.controllers;

import com.arktech.superaccountant.login.models.ERole;
import com.arktech.superaccountant.login.models.Role;
import com.arktech.superaccountant.login.models.User;
import com.arktech.superaccountant.login.repository.RoleRepository;
import com.arktech.superaccountant.login.repository.UserRepository;
import com.arktech.superaccountant.login.security.services.UserDetailsImpl;
import com.arktech.superaccountant.statutory.models.Statute;
import com.arktech.superaccountant.statutory.models.StatutorySection;
import com.arktech.superaccountant.statutory.models.StatutorySectionRule;
import com.arktech.superaccountant.statutory.repository.StatutoryAuditEntryRepository;
import com.arktech.superaccountant.statutory.repository.StatutorySectionRepository;
import com.arktech.superaccountant.statutory.repository.StatutorySectionRuleRepository;
import com.arktech.superaccountant.tds.mapping.TdsLedgerMapping;
import com.arktech.superaccountant.tds.mapping.TdsLedgerMappingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.time.LocalDate;
import java.util.UUID;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The app-wide statutory admin API: reads for every authenticated user, writes only for
 * the app administrator (users.app_admin), append-only dated rule rows with supersession,
 * and an audit trail. Every write endpoint refuses each company role.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = "JWT_SECRET=test-jwt-secret-must-be-at-least-32-characters-long")
class StatutoryAdminIT {

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
    private RoleRepository roleRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StatutorySectionRepository sectionRepository;

    @Autowired
    private StatutorySectionRuleRepository ruleRepository;

    @Autowired
    private StatutoryAuditEntryRepository auditRepository;

    @Autowired
    private TdsLedgerMappingRepository ledgerMappingRepository;

    // --- authorization: every company role is refused on every write endpoint ---

    @ParameterizedTest
    @EnumSource(ERole.class)
    void companyRolesRefusedOnEveryWriteEndpoint(ERole role) throws Exception {
        UserDetailsImpl principal = principal("blocked", role);
        assertNoWrite(principal);
    }

    private void assertNoWrite(UserDetailsImpl principal) throws Exception {
        long sectionsBefore = sectionRepository.count();
        long rulesBefore = ruleRepository.count();
        long auditBefore = auditRepository.count();
        String ruleJson = ruleJson(LocalDate.of(2027, 4, 1));
        String sectionJson = "{\"code\":\"194Z\",\"description\":\"Test section\"}";

        String path = "/api/statutory/sections/" + seededSectionId("194C");
        mockMvc.perform(post(path)
                        .contentType(MediaType.APPLICATION_JSON).content(sectionJson).with(user(principal)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(path + "/deactivate").with(user(principal))).andExpect(status().isForbidden());
        mockMvc.perform(post(path + "/activate").with(user(principal))).andExpect(status().isForbidden());
        mockMvc.perform(delete(path).with(user(principal))).andExpect(status().isForbidden());
        mockMvc.perform(post(path + "/rules")
                        .contentType(MediaType.APPLICATION_JSON).content(ruleJson).with(user(principal)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/statutory/audit").with(user(principal)))
                .andExpect(status().isForbidden());

        assertThat(sectionRepository.count()).isEqualTo(sectionsBefore);
        assertThat(ruleRepository.count()).isEqualTo(rulesBefore);
        assertThat(auditRepository.count()).isEqualTo(auditBefore);
    }

    @Test
    void anonymousRefusedOnReadAndWrite() throws Exception {
        mockMvc.perform(get("/api/statutory/sections"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/statutory/sections/" + seededSectionId("194C") + "/rules")
                        .contentType(MediaType.APPLICATION_JSON).content(ruleJson(LocalDate.of(2027, 4, 1))))
                .andExpect(status().isUnauthorized());
    }

    // --- reads ---

    @Test
    void operatorReadsSectionsWithoutCanEdit() throws Exception {
        mockMvc.perform(get("/api/statutory/sections").with(user(principal("reader", ERole.ROLE_OPERATOR))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canEdit").value(false))
                .andExpect(jsonPath("$.sections[?(@.code == '194C')].rules.length()",
                        org.hamcrest.Matchers.notNullValue()))
                .andExpect(jsonPath("$.sections[?(@.code == 'B2CL_THRESHOLD')].code")
                        .value(org.hamcrest.Matchers.hasItem("B2CL_THRESHOLD")))
                .andExpect(jsonPath("$.sections[?(@.code == '194C')].rules[0].effectiveFrom")
                        .value(org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.notNullValue())));
    }

    @Test
    void adminReadsWithCanEdit() throws Exception {
        mockMvc.perform(get("/api/statutory/sections").with(user(adminPrincipal("admread"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canEdit").value(true));
    }

    // --- admin writes ---

    @Test
    void adminAddsRuleSupersedesOpenPredecessorAndAudits() throws Exception {
        UserDetailsImpl admin = adminPrincipal("rulewriter");
        String sectionId = seededSectionId("194C");

        mockMvc.perform(post("/api/statutory/sections/" + sectionId + "/rules")
                        .with(user(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ruleJson(LocalDate.of(2027, 4, 1))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rule.effectiveFrom").value("2027-04-01"))
                .andExpect(jsonPath("$.rule.createdBy").value(admin.getUsername()))
                .andExpect(jsonPath("$.rule.seeded").value(false))
                .andExpect(jsonPath("$.superseded.effectiveTo").value("2027-03-31"))
                .andExpect(jsonPath("$.superseded.effectiveFrom").value("2026-04-01"));

        // The superseded seed row keeps its values, only the end date changed.
        StatutorySectionRule predecessor = ruleRepository.findBySectionId(UUID.fromString(sectionId)).stream()
                .filter(r -> r.getEffectiveFrom().equals(LocalDate.of(2026, 4, 1)))
                .findFirst().orElseThrow();
        assertThat(predecessor.getEffectiveTo()).isEqualTo(LocalDate.of(2027, 3, 31));
        assertThat(predecessor.isSeeded()).isTrue();

        assertThat(auditRepository.findAll())
                .extracting(e -> e.getAction().name())
                .contains("RULE_ADDED", "RULE_SUPERSEDED");
        assertThat(auditRepository.findAll())
                .extracting(e -> e.getActorUsername())
                .contains(admin.getUsername());
    }

    @Test
    void overlappingRuleRejected400() throws Exception {
        UserDetailsImpl admin = adminPrincipal("overlap");
        String sectionId = createdSection(admin, uniqueCode());
        mockMvc.perform(post("/api/statutory/sections/" + sectionId + "/rules")
                        .with(user(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content(ruleJson(LocalDate.of(2027, 4, 1))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/statutory/sections/" + sectionId + "/rules")
                        .with(user(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content(ruleJson(LocalDate.of(2027, 4, 1))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void validationMessageSurfaced() throws Exception {
        UserDetailsImpl admin = adminPrincipal("validation");
        String sectionId = seededSectionId("194C");
        String body = ruleJson(LocalDate.of(2027, 4, 2)); // not a financial-year start
        mockMvc.perform(post("/api/statutory/sections/" + sectionId + "/rules")
                        .with(user(admin)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("1 April")));
    }

    @Test
    void seededSectionDeleteRefused409() throws Exception {
        mockMvc.perform(delete("/api/statutory/sections/" + seededSectionId("194C"))
                        .with(user(adminPrincipal("delseed"))))
                .andExpect(status().isConflict());
    }

    @Test
    void referencedCreatedSectionDeleteRefused_deactivateInstead() throws Exception {
        UserDetailsImpl admin = adminPrincipal("refdel");
        String sectionId = createdSection(admin, "194Z");
        TdsLedgerMapping mapping = new TdsLedgerMapping();
        mapping.setOrganizationId(UUID.randomUUID());
        mapping.setLedgerName("Ref Ledger");
        mapping.setLedgerKey("ref-" + UUID.randomUUID());
        mapping.setRole(com.arktech.superaccountant.tds.mapping.TdsLedgerRole.TDS_PAYABLE);
        mapping.setSection("194Z");
        mapping.setSavedBy("it");
        mapping.setSavedAt(java.time.Instant.now());
        ledgerMappingRepository.save(mapping);

        mockMvc.perform(delete("/api/statutory/sections/" + sectionId).with(user(admin)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("deactivate instead")));
        mockMvc.perform(post("/api/statutory/sections/" + sectionId + "/deactivate").with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void unreferencedCreatedSectionDeletes() throws Exception {
        UserDetailsImpl admin = adminPrincipal("delunref");
        String sectionId = createdSection(admin, "194Z");
        mockMvc.perform(delete("/api/statutory/sections/" + sectionId).with(user(admin)))
                .andExpect(status().isNoContent());
        assertThat(sectionRepository.findById(UUID.fromString(sectionId))).isEmpty();
        assertThat(ruleRepository.findBySectionId(UUID.fromString(sectionId))).isEmpty();
    }

    @Test
    void createSectionRejectsDuplicateCode() throws Exception {
        UserDetailsImpl admin = adminPrincipal("dupcode");
        String code = uniqueCode();
        mockMvc.perform(post("/api/statutory/sections").with(user(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\",\"description\":\"First\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/statutory/sections").with(user(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\",\"description\":\"Dup\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void cgstSectionCannotBeDeactivated() throws Exception {
        mockMvc.perform(post("/api/statutory/sections/" + seededSectionId("B2CL_THRESHOLD") + "/deactivate")
                        .with(user(adminPrincipal("cgstdel"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownSection404() throws Exception {
        mockMvc.perform(post("/api/statutory/sections/" + UUID.randomUUID() + "/rules")
                        .contentType(MediaType.APPLICATION_JSON).content(ruleJson(LocalDate.of(2027, 4, 1)))
                        .with(user(adminPrincipal("nf404"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void cgstParameterRuleAcceptableByAdmin() throws Exception {
        mockMvc.perform(post("/api/statutory/sections/" + seededSectionId("B2CL_THRESHOLD") + "/rules")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"effectiveDateKind\":\"STATUTORY_DATE\","
                                        + "\"effectiveFrom\":\"2027-04-01\","
                                        + "\"thresholdAnnual\":500000,"
                                        + "\"sourceCitation\":\"CGST Act\"}")
                        .with(user(adminPrincipal("gst"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rule.thresholdAnnual").value(500000));
    }

    // --- helpers ---

    private String uniqueCode() {
        return "19" + (char) ('8' + (int) (Math.random() * 2))
                + (char) ('A' + (int) (Math.random() * 26));
    }

    private String ruleJson(LocalDate from) {
        return "{\"effectiveDateKind\":\"FINANCIAL_YEAR_START\",\"effectiveFrom\":\""
                + from + "\",\"rateCompany\":2,\"rateNonCompany\":1,\"higherRateIfNoPan\":20,"
                + "\"sourceCitation\":\"Income-tax Act 2025 s.393\"}";
    }

    private String seededSectionId(String code) {
        return sectionRepository.findByStatuteAndCode(
                code.startsWith("19") || code.equals("193") ? Statute.INCOME_TAX : Statute.CGST,
                code).orElseThrow().getId().toString();
    }

    private String createdSection(UserDetailsImpl admin) throws Exception {
        return createdSection(admin, uniqueCode());
    }

    private String createdSection(UserDetailsImpl admin, String code) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/statutory/sections")
                        .with(user(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\",\"description\":\"Test section\"}"))
                .andExpect(status().isCreated()).andReturn();
        return com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
                .readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private UserDetailsImpl principal(String name, ERole roleName) {
        return principal(name, roleName, null);
    }

    private UserDetailsImpl principal(String name, ERole roleName, Boolean appAdmin) {
        Role role = roleRepository.findByName(roleName).orElseThrow();
        User u = new User();
        u.setUsername(name + "-" + UUID.randomUUID());
        u.setPassword("x");
        u.setEmail(name + "-" + UUID.randomUUID() + "@t.com");
        u.setRole(role);
        u.setAppAdmin(appAdmin);
        return UserDetailsImpl.build(userRepository.save(u), null);
    }

    private UserDetailsImpl adminPrincipal(String name) {
        return principal(name, ERole.ROLE_OWNER, true);
    }
}
