package com.arktech.superaccountant.tdsapplicability.controllers;

import com.arktech.superaccountant.login.models.ERole;
import com.arktech.superaccountant.login.models.Role;
import com.arktech.superaccountant.login.models.User;
import com.arktech.superaccountant.login.repository.RoleRepository;
import com.arktech.superaccountant.login.repository.UserRepository;
import com.arktech.superaccountant.login.security.jwt.JwtUtils;
import com.arktech.superaccountant.daybook.models.DayBookUploadJob;
import com.arktech.superaccountant.daybook.models.ParsedVoucher;
import com.arktech.superaccountant.daybook.models.ParsedVoucherEntry;
import com.arktech.superaccountant.daybook.repository.DayBookUploadJobRepository;
import com.arktech.superaccountant.daybook.repository.ParsedVoucherRepository;
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
import com.arktech.superaccountant.tdsapplicability.TdsApplicabilityService;
import com.arktech.superaccountant.tdsapplicability.models.TdsApplicabilityBasis;
import com.arktech.superaccountant.workflow.models.ResponsibilityStart;
import com.arktech.superaccountant.workflow.models.Workflow;
import com.arktech.superaccountant.workflow.models.WorkflowStage;
import com.arktech.superaccountant.workflow.models.WorkflowType;
import com.arktech.superaccountant.workflow.repository.ResponsibilityStartRepository;
import com.arktech.superaccountant.workflow.repository.WorkflowRepository;
import com.arktech.superaccountant.workflow.service.period.PeriodCalendar;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = "JWT_SECRET=test-jwt-secret-must-be-at-least-32-characters-long")
class TdsApplicabilityControllerIT {

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        TestDatabase.register(registry);
    }

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

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
    private WorkflowRepository workflowRepository;

    @Autowired
    private ResponsibilityStartRepository responsibilityStartRepository;

    @Autowired
    private TdsApplicabilityService tdsApplicabilityService;

    @Autowired
    private UploadJobRepository uploadJobRepository;

    @Autowired
    private UploadLedgerSnapshotRepository snapshotRepository;

    @Autowired
    private DayBookUploadJobRepository dayBookUploadJobRepository;

    @Autowired
    private ParsedVoucherRepository parsedVoucherRepository;

    @Test
    void panOnlyAdvisoriesForNonDeductorQOrg() throws Exception {
        Organization org = orgWithPan("AAAPA1234A", "ta_panonly_owner", ERole.ROLE_OWNER);
        String jwt = user("ta_panonly_owner", ERole.ROLE_OWNER, org.getId());
        tdsApplicabilityService.recordYear(org.getId(), 2025, false,
                TdsApplicabilityBasis.DECLARED, null, null, null, "it");

        UploadJob mastersJob = new UploadJob();
        mastersJob.setOrganizationId(org.getId());
        mastersJob.setFileName("masters.xml");
        mastersJob.setStatus(UploadJobStatus.COMPLETED);
        mastersJob.setUploadedBy("it");
        UUID mastersJobId = uploadJobRepository.save(mastersJob).getId();
        UploadLedgerSnapshot snapshot = new UploadLedgerSnapshot();
        snapshot.setUploadJobId(mastersJobId);
        snapshot.setOrganizationId(org.getId());
        snapshot.setLedgerName("Contractor X");
        snapshot.setTdsSection("194C");
        snapshotRepository.save(snapshot);

        DayBookUploadJob dayBookJob = new DayBookUploadJob();
        dayBookJob.setOrganizationId(org.getId());
        dayBookJob.setFileName("daybook.xml");
        dayBookJob.setStatus(UploadJobStatus.COMPLETED);
        dayBookJob.setUploadedBy("it");
        UUID dayBookJobId = dayBookUploadJobRepository.save(dayBookJob).getId();

        ParsedVoucher voucher = new ParsedVoucher();
        voucher.setUploadJobId(dayBookJobId);
        voucher.setOrganizationId(org.getId());
        voucher.setVoucherTypeName("Payment");
        voucher.setVoucherNumber("P-1");
        voucher.setVoucherDate(LocalDate.of(2026, 3, 20));
        voucher.setCancelled(false);
        ParsedVoucherEntry entry = new ParsedVoucherEntry();
        entry.setParsedVoucher(voucher);
        entry.setOrganizationId(org.getId());
        entry.setLedgerName("Contractor X");
        entry.setAmount(new java.math.BigDecimal("6000000.00"));
        voucher.setEntries(List.of(entry));
        parsedVoucherRepository.save(voucher);

        mockMvc.perform(get("/api/v1/organizations/" + org.getId()
                                + "/tds-applicability/2025/pan-only-advisories")
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].section").value("194M"))
                .andExpect(jsonPath("$[0].partyLedgerName").value("Contractor X"))
                .andExpect(jsonPath("$[0].financialYear").value(2025))
                .andExpect(jsonPath("$[0].month").doesNotExist())
                .andExpect(jsonPath("$[0].amount").value(6000000.00))
                .andExpect(jsonPath("$[0].form").value("Form 26QD"));
    }

    @Test
    void getReturnsYearsFromCoverageStart() throws Exception {
        Organization org = new Organization("TDS Applicability Org");
        org.setPan("AAAPA1234A");
        org.setBooksStartDate(LocalDate.of(2025, 4, 1));
        org = organizationRepository.save(org);
        String jwt = user("ta_accountant", ERole.ROLE_ACCOUNTANT, org.getId());
        User user = userRepository.findByUsername("ta_accountant").orElseThrow();
        userOrganizationRepository.save(new UserOrganization(user, org, ERole.ROLE_ACCOUNTANT));

        MvcResult result = mockMvc.perform(get("/api/v1/organizations/" + org.getId() + "/tds-applicability")
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        assertThat(root.get("entityCode").asText()).isEqualTo("Q");
        assertThat(root.get("entityCodeSource").asText()).isEqualTo("PAN");

        int currentFy = PeriodCalendar.financialYearOf(LocalDate.now(), 4);
        assertThat(currentFy).isGreaterThanOrEqualTo(2026);

        JsonNode years = root.get("years");
        List<Integer> financialYears = new ArrayList<>();
        years.forEach(y -> financialYears.add(y.get("financialYear").asInt()));
        assertThat(financialYears).contains(2025, currentFy);

        JsonNode fy2025 = years.get(0);
        assertThat(fy2025.get("financialYear").asInt()).isEqualTo(2025);
        assertThat(fy2025.get("applicable").asBoolean()).isTrue();
        assertThat(fy2025.get("basis").asText()).isEqualTo("LEGACY_DEFAULT");
        assertThat(fy2025.get("needsAnswer").asBoolean()).isFalse();
    }

    private String user(String username, ERole roleName, UUID organizationId) {
        Role role = roleRepository.findByName(roleName)
                .orElseGet(() -> roleRepository.save(new Role(roleName)));
        if (userRepository.findByUsername(username).isEmpty()) {
            User user = new User(username, username + "@test.com", "pass");
            user.setRole(role);
            userRepository.save(user);
        }
        return jwtUtils.generateJwtTokenForUser(username, organizationId);
    }

    private Organization orgWithPan(String pan, String username, ERole roleName) {
        Organization org = new Organization("TDS Org " + pan);
        org.setPan(pan);
        org.setBooksStartDate(LocalDate.of(2025, 4, 1));
        org = organizationRepository.save(org);
        String jwt = user(username, roleName, org.getId());
        User user = userRepository.findByUsername(username).orElseThrow();
        userOrganizationRepository.save(new UserOrganization(user, org, roleName));
        return org;
    }

    private String body(boolean applicable, String turnover, String nature) throws Exception {
        var json = objectMapper.createObjectNode();
        json.put("applicable", applicable);
        if (turnover != null) json.put("priorYearTurnover", turnover);
        if (nature != null) json.put("turnoverNature", nature);
        return json.toString();
    }

    @Test
    void putRecordsDeclaredAnswer() throws Exception {
        Organization org = orgWithPan("AAAPA1234A", "ta_put_owner", ERole.ROLE_OWNER);
        String jwt = user("ta_put_owner", ERole.ROLE_OWNER, org.getId());

        mockMvc.perform(put("/api/v1/organizations/" + org.getId() + "/tds-applicability/2025")
                        .header("Authorization", "Bearer " + jwt)
                        .contentType("application/json")
                        .content(body(true, "120000000", "BUSINESS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.basis").value("DECLARED"))
                .andExpect(jsonPath("$.applicable").value(true));
    }

    @Test
    void cannotMarkYearNotApplicableWithFinalizedTdsWorkflow() throws Exception {
        Organization org = orgWithPan("AAAPA1234A", "ta_finalized_owner", ERole.ROLE_OWNER);
        String jwt = user("ta_finalized_owner", ERole.ROLE_OWNER, org.getId());

        Workflow payment = new Workflow();
        payment.setOrganizationId(org.getId());
        payment.setType(WorkflowType.TDS_PAYMENT);
        payment.setPeriodStart(LocalDate.of(2025, 4, 1));
        payment.setPeriodEnd(LocalDate.of(2025, 4, 30));
        payment.setPeriodKey("2025-04");
        payment.setFinancialYear(2025);
        payment.setStage(WorkflowStage.FINALIZED);
        payment.setActive(true);
        payment.setRevision(0);
        workflowRepository.save(payment);

        MvcResult result = mockMvc.perform(
                        put("/api/v1/organizations/" + org.getId() + "/tds-applicability/2025")
                                .header("Authorization", "Bearer " + jwt)
                                .contentType("application/json")
                                .content(body(false, null, null)))
                .andExpect(status().isConflict())
                .andReturn();

        assertThat(result.getResponse().getContentAsString())
                .contains("FY 2025-26 has a finalized TDS workflow; unfinalize it first");
    }

    @Test
    void putWithoutApplicableFieldIsBadRequest() throws Exception {
        Organization org = orgWithPan("AAAPA1234A", "ta_missing_field_owner", ERole.ROLE_OWNER);
        String jwt = user("ta_missing_field_owner", ERole.ROLE_OWNER, org.getId());

        mockMvc.perform(put("/api/v1/organizations/" + org.getId() + "/tds-applicability/2025")
                        .header("Authorization", "Bearer " + jwt)
                        .contentType("application/json")
                        .content("{\"note\":\"no applicable field\"}"))
                .andExpect(status().isBadRequest());

        assertThat(tdsApplicabilityService.resolve(org.getId(), 2025).applicable()).isTrue();
    }

    @Test
    void operatorCannotChangeApplicability() throws Exception {
        Organization org = orgWithPan("AAAPA1234A", "ta_operator", ERole.ROLE_OPERATOR);
        String jwt = user("ta_operator", ERole.ROLE_OPERATOR, org.getId());

        mockMvc.perform(put("/api/v1/organizations/" + org.getId() + "/tds-applicability/2025")
                        .header("Authorization", "Bearer " + jwt)
                        .contentType("application/json")
                        .content(body(true, "120000000", "BUSINESS")))
                .andExpect(status().isForbidden());
    }

    @Test
    void companyYearCannotBeChanged() throws Exception {
        Organization org = orgWithPan("AAACA1234A", "ta_company_owner", ERole.ROLE_OWNER);
        String jwt = user("ta_company_owner", ERole.ROLE_OWNER, org.getId());

        MvcResult result = mockMvc.perform(
                        put("/api/v1/organizations/" + org.getId() + "/tds-applicability/2025")
                                .header("Authorization", "Bearer " + jwt)
                                .contentType("application/json")
                                .content(body(false, null, null)))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertThat(result.getResponse().getContentAsString())
                .contains("Entity type K always deducts TDS");
    }

    @Test
    void changingYearRebuildsTdsBaselineRows() throws Exception {
        Organization org = orgWithPan("AAAPA1234A", "ta_rebuild_owner", ERole.ROLE_OWNER);
        String jwt = user("ta_rebuild_owner", ERole.ROLE_OWNER, org.getId());

        ResponsibilityStart start = new ResponsibilityStart();
        start.setOrganizationId(org.getId());
        start.setType(WorkflowType.TDS_PAYMENT);
        start.setFirstPeriodStart(LocalDate.of(2025, 5, 1));
        start.setSetBy("seed");
        responsibilityStartRepository.save(start);

        Workflow oldRow = new Workflow();
        oldRow.setOrganizationId(org.getId());
        oldRow.setType(WorkflowType.TDS_PAYMENT);
        oldRow.setPeriodStart(LocalDate.of(2025, 4, 1));
        oldRow.setPeriodEnd(LocalDate.of(2025, 4, 30));
        oldRow.setPeriodKey("2025-04");
        oldRow.setFinancialYear(2025);
        oldRow.setStage(WorkflowStage.BASELINE);
        oldRow.setActive(true);
        oldRow.setRevision(0);
        oldRow = workflowRepository.save(oldRow);

        mockMvc.perform(put("/api/v1/organizations/" + org.getId() + "/tds-applicability/2025")
                        .header("Authorization", "Bearer " + jwt)
                        .contentType("application/json")
                        .content(body(true, "120000000", "BUSINESS")))
                .andExpect(status().isOk());

        List<Workflow> activeBaseline = workflowRepository
                .findAllByOrganizationIdAndTypeAndStageAndActiveTrue(
                        org.getId(), WorkflowType.TDS_PAYMENT, WorkflowStage.BASELINE);
        List<UUID> activeIds = activeBaseline.stream().map(Workflow::getId).toList();
        assertThat(activeIds).doesNotContain(oldRow.getId());
        assertThat(activeIds).isNotEmpty();
        Workflow inactive = workflowRepository.findById(oldRow.getId()).orElseThrow();
        assertThat(inactive.isActive()).isFalse();
    }
}
