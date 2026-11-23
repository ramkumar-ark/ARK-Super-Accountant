package com.arktech.superaccountant.tdsapplicability;

import com.arktech.superaccountant.login.models.ERole;
import com.arktech.superaccountant.login.models.Role;
import com.arktech.superaccountant.login.models.User;
import com.arktech.superaccountant.login.repository.RoleRepository;
import com.arktech.superaccountant.login.repository.UserRepository;
import com.arktech.superaccountant.login.security.jwt.JwtUtils;
import com.arktech.superaccountant.masters.models.LedgerCategory;
import com.arktech.superaccountant.masters.models.Organization;
import com.arktech.superaccountant.masters.models.UploadJob;
import com.arktech.superaccountant.masters.models.UploadJobStatus;
import com.arktech.superaccountant.masters.models.UploadLedgerSnapshot;
import com.arktech.superaccountant.masters.models.UserOrganization;
import com.arktech.superaccountant.masters.repository.OrganizationRepository;
import com.arktech.superaccountant.masters.repository.UploadJobRepository;
import com.arktech.superaccountant.masters.repository.UploadLedgerSnapshotRepository;
import com.arktech.superaccountant.masters.repository.UserOrganizationRepository;
import com.arktech.superaccountant.support.TestDatabase;
import com.arktech.superaccountant.daybook.models.DayBookUploadJob;
import com.arktech.superaccountant.daybook.models.ParsedVoucher;
import com.arktech.superaccountant.daybook.models.ParsedVoucherEntry;
import com.arktech.superaccountant.daybook.repository.DayBookUploadJobRepository;
import com.arktech.superaccountant.daybook.repository.ParsedVoucherRepository;
import com.arktech.superaccountant.tdsapplicability.models.PromptKind;
import com.arktech.superaccountant.tdsapplicability.models.TdsApplicabilityBasis;
import com.arktech.superaccountant.tdsapplicability.repository.TdsApplicabilityPromptRepository;
import com.arktech.superaccountant.workflow.models.ResponsibilityStart;
import com.arktech.superaccountant.workflow.models.Workflow;
import com.arktech.superaccountant.workflow.models.WorkflowStage;
import com.arktech.superaccountant.workflow.models.WorkflowType;
import com.arktech.superaccountant.workflow.payload.request.CloseFinancialYearRequest;
import com.arktech.superaccountant.workflow.repository.ResponsibilityStartRepository;
import com.arktech.superaccountant.workflow.repository.WorkflowRepository;
import com.arktech.superaccountant.workflow.seam.BaselineConfirmedEvent;
import com.arktech.superaccountant.workflow.service.compliance.FinancialYearCloseService;
import com.arktech.superaccountant.workflow.service.period.PeriodCalendar;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Task 8 — late-discovery and next-year TDS applicability prompts.
 * Not @Transactional: the AFTER_COMMIT listeners must fire on real commits.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "JWT_SECRET=test-jwt-secret-must-be-at-least-32-characters-long")
class TdsApplicabilityPromptIT {

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        TestDatabase.register(registry);
    }

    private static final BigDecimal BOOKS_1_2_CR = new BigDecimal("12000000");
    private static final BigDecimal BOOKS_45L = new BigDecimal("4500000");

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private UserOrganizationRepository userOrganizationRepository;

    @Autowired
    private TdsApplicabilityService tdsApplicabilityService;

    @Autowired
    private TdsApplicabilityPromptRepository promptRepository;

    @Autowired
    private UploadJobRepository uploadJobRepository;

    @Autowired
    private UploadLedgerSnapshotRepository ledgerSnapshotRepository;

    @Autowired
    private DayBookUploadJobRepository dayBookJobRepository;

    @Autowired
    private ParsedVoucherRepository voucherRepository;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private FinancialYearCloseService financialYearCloseService;

    @Autowired
    private WorkflowRepository workflowRepository;

    @Autowired
    private ResponsibilityStartRepository responsibilityStartRepository;

    @Test
    void baselineConfirmRaisesPromptWhenBooksExceed() throws Exception {
        Organization org = qOrg("ta_prompt_owner");
        seedBooks(org.getId(), BOOKS_1_2_CR);
        recordNotApplicable(org.getId(), 2026);

        publishBaselineConfirmed(org.getId());

        JsonNode prompts = openPrompts(org.getId(), "ta_prompt_owner");
        assertThat(prompts.size()).isEqualTo(1);
        assertThat(prompts.get(0).get("kind").asText()).isEqualTo("BOOKS_EXCEED_LIMIT");
        assertThat(prompts.get(0).get("financialYear").asInt()).isEqualTo(2026);
        assertThat(new BigDecimal(prompts.get(0).get("estimate").get("amount").asText()))
                .isEqualByComparingTo(BOOKS_1_2_CR);
        assertThat(prompts.get(0).get("message").asText()).contains("₹1,20,00,000");
    }

    @Test
    void noPromptBelowTrigger() throws Exception {
        Organization org = qOrg("ta_below_owner");
        seedBooks(org.getId(), BOOKS_45L);
        recordNotApplicable(org.getId(), 2026);

        publishBaselineConfirmed(org.getId());

        JsonNode prompts = openPrompts(org.getId(), "ta_below_owner");
        assertThat(prompts.size()).isZero();
    }

    @Test
    void noDuplicatePromptAfterDismiss() throws Exception {
        Organization org = qOrg("ta_dup_owner");
        seedBooks(org.getId(), BOOKS_1_2_CR);
        recordNotApplicable(org.getId(), 2026);
        publishBaselineConfirmed(org.getId());

        UUID promptId = promptIdFrom(openPrompts(org.getId(), "ta_dup_owner"));
        mockMvc.perform(post("/api/v1/organizations/" + org.getId()
                                + "/tds-applicability/prompts/" + promptId + "/dismiss")
                        .header("Authorization", "Bearer " + jwt(org.getId(), "ta_dup_owner"))
                        .contentType("application/json")
                        .content("{\"reason\":\"Books include non-sale receipts\"}"))
                .andExpect(status().isOk());

        publishBaselineConfirmed(org.getId());

        assertThat(openPrompts(org.getId(), "ta_dup_owner").size()).isZero();
        assertThat(promptRepository
                .findByOrganizationIdAndKindAndFinancialYear(org.getId(), PromptKind.BOOKS_EXCEED_LIMIT, 2026))
                .hasSize(1);
    }

    @Test
    void confirmMakesYearApplicableAndCreatesBaselineRows() throws Exception {
        Organization org = qOrg("ta_confirm_owner");
        seedBooks(org.getId(), BOOKS_1_2_CR);
        recordNotApplicable(org.getId(), 2026);

        ResponsibilityStart start = new ResponsibilityStart();
        start.setOrganizationId(org.getId());
        start.setType(WorkflowType.TDS_PAYMENT);
        start.setFirstPeriodStart(LocalDate.of(2026, 5, 1));
        start.setSetBy("seed");
        responsibilityStartRepository.save(start);

        Workflow oldRow = new Workflow();
        oldRow.setOrganizationId(org.getId());
        oldRow.setType(WorkflowType.TDS_PAYMENT);
        oldRow.setPeriodStart(LocalDate.of(2026, 4, 1));
        oldRow.setPeriodEnd(LocalDate.of(2026, 4, 30));
        oldRow.setPeriodKey("2026-04");
        oldRow.setFinancialYear(2026);
        oldRow.setStage(WorkflowStage.BASELINE);
        oldRow.setActive(true);
        oldRow.setRevision(0);
        oldRow = workflowRepository.save(oldRow);

        publishBaselineConfirmed(org.getId());
        UUID promptId = promptIdFrom(openPrompts(org.getId(), "ta_confirm_owner"));

        mockMvc.perform(post("/api/v1/organizations/" + org.getId()
                                + "/tds-applicability/prompts/" + promptId + "/confirm")
                        .header("Authorization", "Bearer " + jwt(org.getId(), "ta_confirm_owner"))
                        .contentType("application/json")
                        .content("{\"applicable\":true,\"priorYearTurnover\":\"12000000\","
                                + "\"turnoverNature\":\"BUSINESS\"}"))
                .andExpect(status().isOk());

        assertThat(openPrompts(org.getId(), "ta_confirm_owner").size()).isZero();
        JsonNode years = objectMapper.readTree(mockMvc.perform(
                        get("/api/v1/organizations/" + org.getId() + "/tds-applicability")
                                .header("Authorization", "Bearer " + jwt(org.getId(), "ta_confirm_owner")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray()).get("years");
        JsonNode fy2026 = null;
        for (JsonNode y : years) {
            if (y.get("financialYear").asInt() == 2026) {
                fy2026 = y;
            }
        }
        assertThat(fy2026).isNotNull();
        assertThat(fy2026.get("applicable").asBoolean()).isTrue();
        assertThat(fy2026.get("basis").asText()).isEqualTo("CONFIRMED_FROM_BOOKS");

        List<UUID> activeIds = workflowRepository
                .findAllByOrganizationIdAndTypeAndStageAndActiveTrue(
                        org.getId(), WorkflowType.TDS_PAYMENT, WorkflowStage.BASELINE)
                .stream().map(Workflow::getId).toList();
        assertThat(activeIds).doesNotContain(oldRow.getId());
        assertThat(activeIds).isNotEmpty();
        assertThat(workflowRepository.findById(oldRow.getId()).orElseThrow().isActive()).isFalse();
    }

    @Test
    void dismissRequiresReason() throws Exception {
        Organization org = qOrg("ta_dismiss_owner");
        seedBooks(org.getId(), BOOKS_1_2_CR);
        recordNotApplicable(org.getId(), 2026);
        publishBaselineConfirmed(org.getId());
        UUID promptId = promptIdFrom(openPrompts(org.getId(), "ta_dismiss_owner"));

        mockMvc.perform(post("/api/v1/organizations/" + org.getId()
                                + "/tds-applicability/prompts/" + promptId + "/dismiss")
                        .header("Authorization", "Bearer " + jwt(org.getId(), "ta_dismiss_owner"))
                        .contentType("application/json")
                        .content("{\"reason\":\"\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/organizations/" + org.getId()
                                + "/tds-applicability/prompts/" + promptId + "/dismiss")
                        .header("Authorization", "Bearer " + jwt(org.getId(), "ta_dismiss_owner"))
                        .contentType("application/json")
                        .content("{\"reason\":\"   \"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/organizations/" + org.getId()
                                + "/tds-applicability/prompts/" + promptId + "/dismiss")
                        .header("Authorization", "Bearer " + jwt(org.getId(), "ta_dismiss_owner"))
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/organizations/" + org.getId()
                                + "/tds-applicability/prompts/" + promptId + "/dismiss")
                        .header("Authorization", "Bearer " + jwt(org.getId(), "ta_dismiss_owner"))
                        .contentType("application/json")
                        .content("{\"reason\":\"Not a deductor\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void fyCloseRaisesNextYearDeclaration() throws Exception {
        Organization org = qOrg("ta_close_owner");

        CloseFinancialYearRequest request = new CloseFinancialYearRequest();
        request.setCloseType("ITR_FILED");
        request.setItrAckNo("ACK123456");
        request.setItrAckDate(LocalDate.of(2026, 11, 1));
        financialYearCloseService.close(org.getId(), "ta_close_owner", 2025, request);

        JsonNode prompts = openPrompts(org.getId(), "ta_close_owner");
        boolean found = false;
        for (JsonNode prompt : prompts) {
            assertThat(prompt.get("financialYear").asInt()).isEqualTo(2026);
            if (prompt.get("kind").asText().equals("NEXT_YEAR_DECLARATION")) {
                found = true;
            } else {
                assertThat(prompt.get("kind").asText())
                        .isEqualTo("LEGACY_INDIVIDUAL_NOTE");
            }
        }
        assertThat(found).isTrue();
    }

    @Test
    void legacyIndividualGetsOneTimeNote() throws Exception {
        Organization org = qOrg("ta_legacy_owner");

        JsonNode prompts = openPrompts(org.getId(), "ta_legacy_owner");
        assertThat(prompts.size()).isEqualTo(1);
        assertThat(prompts.get(0).get("kind").asText()).isEqualTo("LEGACY_INDIVIDUAL_NOTE");

        UUID promptId = promptIdFrom(prompts);
        mockMvc.perform(post("/api/v1/organizations/" + org.getId()
                                + "/tds-applicability/prompts/" + promptId + "/confirm")
                        .header("Authorization", "Bearer " + jwt(org.getId(), "ta_legacy_owner"))
                        .contentType("application/json")
                        .content("{\"applicable\":true}"))
                .andExpect(status().isOk());

        int currentFy = PeriodCalendar.financialYearOf(LocalDate.now(), 4);
        var resolved = tdsApplicabilityService.resolve(org.getId(), currentFy);
        assertThat(resolved.applicable()).isTrue();
        assertThat(resolved.basis()).isEqualTo(TdsApplicabilityBasis.DECLARED);

        // Second GET must not create another legacy note.
        mockMvc.perform(get("/api/v1/organizations/" + org.getId() + "/tds-applicability/prompts")
                        .header("Authorization", "Bearer " + jwt(org.getId(), "ta_legacy_owner")))
                .andExpect(status().isOk());
        assertThat(promptRepository
                .findByOrganizationIdAndKindAndFinancialYear(
                        org.getId(), PromptKind.LEGACY_INDIVIDUAL_NOTE, currentFy))
                .hasSize(1);
    }

    @Test
    void companyNeverPrompted() throws Exception {
        Organization org = new Organization("Company Prompt Org " + UUID.randomUUID());
        org.setPan("AAACA1234A");
        org.setFinancialYearStart(4);
        org.setBooksStartDate(LocalDate.of(2025, 4, 1));
        org = organizationRepository.save(org);
        jwt(org.getId(), "ta_company_owner");
        User user = userRepository.findByUsername("ta_company_owner").orElseThrow();
        userOrganizationRepository.save(new UserOrganization(user, org, ERole.ROLE_OWNER));

        seedBooks(org.getId(), BOOKS_1_2_CR);
        publishBaselineConfirmed(org.getId());

        assertThat(openPrompts(org.getId(), "ta_company_owner").size()).isZero();
    }

    // ---- helpers ----

    private Organization qOrg(String username) {
        Organization org = new Organization("Prompt Org " + username + " " + UUID.randomUUID());
        org.setPan("AAAPA1234A");
        org.setFinancialYearStart(4);
        org.setBooksStartDate(LocalDate.of(2025, 4, 1));
        org = organizationRepository.save(org);
        jwt(org.getId(), username);
        User user = userRepository.findByUsername(username).orElseThrow();
        userOrganizationRepository.save(new UserOrganization(user, org, ERole.ROLE_OWNER));
        return org;
    }

    private String jwt(UUID organizationId, String username) {
        Role role = roleRepository.findByName(ERole.ROLE_OWNER)
                .orElseGet(() -> roleRepository.save(new Role(ERole.ROLE_OWNER)));
        if (userRepository.findByUsername(username).isEmpty()) {
            User user = new User(username, username + "@test.com", "pass");
            user.setRole(role);
            userRepository.save(user);
        }
        return jwtUtils.generateJwtTokenForUser(username, organizationId);
    }

    private void recordNotApplicable(UUID orgId, int fy) {
        tdsApplicabilityService.recordYear(orgId, fy, false, TdsApplicabilityBasis.DECLARED,
                null, null, null, "it");
    }

    private void publishBaselineConfirmed(UUID orgId) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.executeWithoutResult(tx ->
                eventPublisher.publishEvent(new BaselineConfirmedEvent(orgId)));
    }

    private JsonNode openPrompts(UUID orgId, String username) throws Exception {
        MvcResult result = mockMvc.perform(
                        get("/api/v1/organizations/" + orgId + "/tds-applicability/prompts")
                                .header("Authorization", "Bearer " + jwt(orgId, username)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private UUID promptIdFrom(JsonNode prompts) {
        return UUID.fromString(prompts.get(0).get("id").asText());
    }

    private void seedBooks(UUID orgId, BigDecimal amount) {
        UploadJob mastersJob = new UploadJob();
        mastersJob.setOrganizationId(orgId);
        mastersJob.setStatus(UploadJobStatus.COMPLETED);
        mastersJob.setUploadedBy("test");
        UUID mastersJobId = uploadJobRepository.save(mastersJob).getId();

        UploadLedgerSnapshot ledger = new UploadLedgerSnapshot();
        ledger.setOrganizationId(orgId);
        ledger.setUploadJobId(mastersJobId);
        ledger.setLedgerName("Sales Account");
        ledger.setCategory(LedgerCategory.INCOME);
        ledger.setGroupChain("income statement|sales accounts");
        ledgerSnapshotRepository.save(ledger);

        DayBookUploadJob job = new DayBookUploadJob();
        job.setOrganizationId(orgId);
        job.setStatus(UploadJobStatus.COMPLETED);
        job.setCoverageFrom(LocalDate.of(2025, 4, 1));
        job.setCoverageTo(LocalDate.of(2026, 3, 31));
        UUID jobId = dayBookJobRepository.save(job).getId();

        ParsedVoucher voucher = new ParsedVoucher();
        voucher.setOrganizationId(orgId);
        voucher.setUploadJobId(jobId);
        voucher.setVoucherTypeName("Sales");
        voucher.setVoucherDate(LocalDate.of(2025, 5, 10));
        voucher.setCancelled(false);
        ParsedVoucherEntry entry = new ParsedVoucherEntry();
        entry.setParsedVoucher(voucher);
        entry.setOrganizationId(orgId);
        entry.setLedgerName("Sales Account");
        entry.setAmount(amount.negate());
        entry.setDebit(false);
        voucher.setEntries(List.of(entry));
        voucherRepository.save(voucher);
    }
}
