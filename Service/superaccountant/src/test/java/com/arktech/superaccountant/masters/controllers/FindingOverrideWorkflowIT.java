package com.arktech.superaccountant.masters.controllers;

import com.arktech.superaccountant.login.models.ERole;
import com.arktech.superaccountant.login.models.Role;
import com.arktech.superaccountant.login.models.User;
import com.arktech.superaccountant.login.repository.RoleRepository;
import com.arktech.superaccountant.login.repository.UserRepository;
import com.arktech.superaccountant.login.security.jwt.JwtUtils;
import com.arktech.superaccountant.masters.models.*;
import com.arktech.superaccountant.masters.repository.*;
import com.arktech.superaccountant.masters.services.MastersGateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end tests (real Postgres via Testcontainers) of the two-stage finding override:
 * operator submits, accountant approves/rejects, the master is written only on approval, and the
 * gate stays locked until approval. Needs Docker.
 *
 * Each test performs at most one failing request: a failing action marks the shared test
 * transaction rollback-only, so a second request in the same test would not be meaningful.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Transactional
@TestPropertySource(properties = "JWT_SECRET=test-jwt-secret-must-be-at-least-32-characters-long")
class FindingOverrideWorkflowIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired RoleRepository roleRepository;
    @Autowired JwtUtils jwtUtils;
    @Autowired OrganizationRepository organizationRepository;
    @Autowired UploadJobRepository uploadJobRepository;
    @Autowired ValidationFindingRepository findingRepository;
    @Autowired PreconfiguredMasterRepository masterRepository;
    @Autowired MasterEditLogRepository editLogRepository;
    @Autowired UploadLedgerSnapshotRepository snapshotRepository;
    @Autowired MastersGateService gateService;

    private UUID orgId;
    private UploadJob job;
    private PreconfiguredMaster master;
    private String operatorJwt, accountantJwt, auditorJwt, ownerJwt;

    @BeforeEach
    void setUp() {
        orgId = organizationRepository.save(new Organization("Override Org " + UUID.randomUUID())).getId();
        operatorJwt = userWithRole("wf_operator", ERole.ROLE_OPERATOR);
        accountantJwt = userWithRole("wf_accountant", ERole.ROLE_ACCOUNTANT);
        auditorJwt = userWithRole("wf_auditor", ERole.ROLE_AUDITOR_CA);
        ownerJwt = userWithRole("wf_owner", ERole.ROLE_OWNER);

        job = new UploadJob();
        job.setOrganizationId(orgId);
        job.setFileName("masters.json");
        job.setStatus(UploadJobStatus.COMPLETED);
        job.setTotalLedgersParsed(5);
        job = uploadJobRepository.save(job);

        master = new PreconfiguredMaster();
        master.setOrganizationId(orgId);
        master.setLedgerName("Acme Traders");
        master.setCategory(LedgerCategory.CREDITOR);
        master.setExpectedParentGroup("Sundry Creditors");
        master = masterRepository.save(master);
    }

    private String userWithRole(String username, ERole eRole) {
        Role role = roleRepository.findByName(eRole).orElseGet(() -> roleRepository.save(new Role(eRole)));
        if (userRepository.findByUsername(username).isEmpty()) {
            User u = new User(username, username + "@test.com", "pass");
            u.setRole(role);
            userRepository.save(u);
        }
        return jwtUtils.generateJwtTokenForUser(username, orgId);
    }

    private ValidationFinding finding(String ruleCode, MismatchType type, String ledger, String expected, String actual) {
        ValidationFinding f = new ValidationFinding();
        f.setUploadJobId(job.getId());
        f.setRuleCode(ruleCode);
        f.setMismatchType(type);
        f.setCategory(LedgerCategory.CREDITOR);
        f.setLedgerName(ledger);
        f.setExpectedValue(expected);
        f.setActualValue(actual);
        f.setSeverity(FindingSeverity.HIGH);
        f.setResolveStatus(ResolveStatus.OPEN);
        f.setMessage("test");
        return findingRepository.saveAndFlush(f);
    }

    private ValidationFinding gstinFinding() {
        return finding("GSTIN_PRESENCE", null, "Acme Traders", null, null);
    }

    private ResultActions act(String jwt, ValidationFinding f, String json) throws Exception {
        return mockMvc.perform(post("/api/v1/uploads/" + job.getId() + "/findings/" + f.getId() + "/action")
                .contentType("application/json").content(json)
                .header("Authorization", "Bearer " + jwt));
    }

    private PreconfiguredMaster reloadMaster() {
        masterRepository.flush();
        return masterRepository.findById(master.getId()).orElseThrow();
    }

    // ── submit ───────────────────────────────────────────────────────────────

    @Test
    void operatorSubmit_isPending_masterUntouched_gateStaysLocked_noAuditRows() throws Exception {
        ValidationFinding f = gstinFinding();

        act(operatorJwt, f, "{\"action\":\"ACKNOWLEDGE\",\"note\":\"vendor sent it\",\"value\":\"29ABCDE1234F1Z5\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resolveStatus").value("ACKNOWLEDGED"))
                .andExpect(jsonPath("$.proposedValue").value("29ABCDE1234F1Z5"))
                .andExpect(jsonPath("$.submittedBy").value("wf_operator"));

        assertThat(reloadMaster().getGstin()).isNull();
        assertThat(editLogRepository.findByFindingId(f.getId())).isEmpty();
        assertThat(gateService.checkGate(orgId).gated()).isTrue();
        assertThat(gateService.checkGate(orgId).unresolvedCount()).isEqualTo(1);
    }

    @Test
    void gate_isGatedWhenOpen_stillGatedWhenPending_openOnlyAfterAccountantApproval() throws Exception {
        ValidationFinding f = gstinFinding();
        assertThat(gateService.checkGate(orgId).unresolvedCount()).isEqualTo(1);

        act(operatorJwt, f, "{\"action\":\"ACKNOWLEDGE\",\"value\":\"29ABCDE1234F1Z5\"}").andExpect(status().isOk());
        assertThat(gateService.checkGate(orgId).gated()).isTrue();

        act(accountantJwt, f, "{\"action\":\"APPROVE\"}").andExpect(status().isOk());
        findingRepository.flush();
        assertThat(gateService.checkGate(orgId).gated()).isFalse();
    }

    // ── approve ──────────────────────────────────────────────────────────────

    @Test
    void accountantApprove_writesMasterAndAuditRow_withSubmitterAndApprover() throws Exception {
        ValidationFinding f = gstinFinding();
        act(operatorJwt, f, "{\"action\":\"ACKNOWLEDGE\",\"value\":\"29ABCDE1234F1Z5\"}").andExpect(status().isOk());

        act(accountantJwt, f, "{\"action\":\"APPROVE\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resolveStatus").value("APPROVED"))
                .andExpect(jsonPath("$.resolvedBy").value("wf_accountant"))
                .andExpect(jsonPath("$.submittedBy").value("wf_operator"));

        assertThat(reloadMaster().getGstin()).isEqualTo("29ABCDE1234F1Z5");
        var logs = editLogRepository.findByFindingId(f.getId());
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getEditedBy()).isEqualTo("wf_accountant");
        assertThat(logs.get(0).getSubmittedBy()).isEqualTo("wf_operator");
        assertThat(logs.get(0).getFieldName()).isEqualTo("gstin");
        assertThat(logs.get(0).getOldValue()).isNull();
        assertThat(logs.get(0).getNewValue()).isEqualTo("29ABCDE1234F1Z5");
        assertThat(logs.get(0).getSource()).isEqualTo("FINDING_OVERRIDE");
        assertThat(logs.get(0).getMasterId()).isEqualTo(master.getId());
    }

    @Test
    void accountantOwnSubmission_isApprovedInOneStep() throws Exception {
        ValidationFinding f = gstinFinding();

        act(accountantJwt, f, "{\"action\":\"ACKNOWLEDGE\",\"value\":\"29ABCDE1234F1Z5\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resolveStatus").value("APPROVED"))
                .andExpect(jsonPath("$.submittedBy").value("wf_accountant"))
                .andExpect(jsonPath("$.reviewedBy").value("wf_accountant"));

        assertThat(reloadMaster().getGstin()).isEqualTo("29ABCDE1234F1Z5");
        assertThat(editLogRepository.findByFindingId(f.getId())).hasSize(1);
        assertThat(gateService.checkGate(orgId).gated()).isFalse();
    }

    @Test
    void nameMismatch_approval_renamesMasterLocatedByExpectedValue() throws Exception {
        ValidationFinding f = finding("MISMATCH_DETECTION", MismatchType.NAME_MISMATCH,
                "acme traders", "Acme Traders", "acme traders");
        act(operatorJwt, f, "{\"action\":\"ACKNOWLEDGE\"}").andExpect(status().isOk());
        assertThat(reloadMaster().getLedgerName()).isEqualTo("Acme Traders");

        act(accountantJwt, f, "{\"action\":\"APPROVE\"}").andExpect(status().isOk());

        assertThat(reloadMaster().getLedgerName()).isEqualTo("acme traders");
    }

    @Test
    void nameMismatch_masterRenamedBeforeApproval_returns409_masterAndFindingUnchanged() throws Exception {
        ValidationFinding f = finding("MISMATCH_DETECTION", MismatchType.NAME_MISMATCH,
                "acme traders", "Acme Traders", "acme traders");
        act(operatorJwt, f, "{\"action\":\"ACKNOWLEDGE\"}").andExpect(status().isOk());
        master.setLedgerName("Acme Traders Pvt Ltd");
        masterRepository.saveAndFlush(master);

        act(accountantJwt, f, "{\"action\":\"APPROVE\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value(containsString("Master changed")));

        assertThat(reloadMaster().getLedgerName()).isEqualTo("Acme Traders Pvt Ltd");
        assertThat(findingRepository.findById(f.getId()).orElseThrow().getResolveStatus())
                .isEqualTo(ResolveStatus.ACKNOWLEDGED);
        assertThat(editLogRepository.findByFindingId(f.getId())).isEmpty();
    }

    @Test
    void missingInConfiguration_approval_createsMasterFromSnapshot() throws Exception {
        UploadLedgerSnapshot s = new UploadLedgerSnapshot();
        s.setUploadJobId(job.getId());
        s.setOrganizationId(orgId);
        s.setLedgerName("Brand New Vendor");
        s.setParentGroup("Sundry Creditors");
        s.setGstApplicable(true);
        s.setGstin("29ABCDE1234F1Z5");
        snapshotRepository.saveAndFlush(s);
        ValidationFinding f = finding("MISMATCH_DETECTION", MismatchType.MISSING_IN_CONFIGURATION,
                "Brand New Vendor", null, "Brand New Vendor");

        act(operatorJwt, f, "{\"action\":\"ACKNOWLEDGE\"}").andExpect(status().isOk());
        assertThat(masterRepository.findByOrganizationIdAndActiveTrue(orgId)).hasSize(1); // only the seeded one

        act(accountantJwt, f, "{\"action\":\"APPROVE\"}").andExpect(status().isOk());

        var created = masterRepository.findByOrganizationIdAndActiveTrue(orgId).stream()
                .filter(m -> m.getLedgerName().equals("Brand New Vendor")).findFirst().orElseThrow();
        assertThat(created.getGstin()).isEqualTo("29ABCDE1234F1Z5");
        assertThat(created.getExpectedParentGroup()).isEqualTo("Sundry Creditors");
        assertThat(editLogRepository.findByMasterId(created.getId())).isNotEmpty();
    }

    @Test
    void missingInConfiguration_snapshotGoneBeforeApproval_errors_noMasterCreated_findingStaysPending() throws Exception {
        UploadLedgerSnapshot s = new UploadLedgerSnapshot();
        s.setUploadJobId(job.getId());
        s.setOrganizationId(orgId);
        s.setLedgerName("Brand New Vendor");
        s = snapshotRepository.saveAndFlush(s);
        ValidationFinding f = finding("MISMATCH_DETECTION", MismatchType.MISSING_IN_CONFIGURATION,
                "Brand New Vendor", null, "Brand New Vendor");
        act(operatorJwt, f, "{\"action\":\"ACKNOWLEDGE\"}").andExpect(status().isOk());
        snapshotRepository.delete(s);
        snapshotRepository.flush();

        act(accountantJwt, f, "{\"action\":\"APPROVE\"}").andExpect(status().isConflict());

        assertThat(masterRepository.findByOrganizationIdAndActiveTrue(orgId)).hasSize(1);
        assertThat(findingRepository.findById(f.getId()).orElseThrow().getResolveStatus())
                .isEqualTo(ResolveStatus.ACKNOWLEDGED);
        assertThat(editLogRepository.findByFindingId(f.getId())).isEmpty();
    }

    @Test
    void inlineLedgerEdit_doesNotCloseFindingPendingAccountantReview_gateStaysLocked() throws Exception {
        UploadLedgerSnapshot s = new UploadLedgerSnapshot();
        s.setUploadJobId(job.getId());
        s.setOrganizationId(orgId);
        s.setLedgerName("Acme Traders");
        s = snapshotRepository.saveAndFlush(s);
        ValidationFinding f = gstinFinding();
        act(operatorJwt, f, "{\"action\":\"ACKNOWLEDGE\",\"value\":\"29ABCDE1234F1Z5\"}").andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/uploads/" + job.getId() + "/ledgers/" + s.getId())
                        .contentType("application/json").content("{\"gstin\":\"29ABCDE1234F1Z5\"}")
                        .header("Authorization", "Bearer " + operatorJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resolvedFindings").isEmpty());

        findingRepository.flush();
        assertThat(findingRepository.findById(f.getId()).orElseThrow().getResolveStatus())
                .isEqualTo(ResolveStatus.ACKNOWLEDGED);
        assertThat(gateService.checkGate(orgId).gated()).isTrue();
    }

    private UploadLedgerSnapshot acmeSnapshot() {
        UploadLedgerSnapshot s = new UploadLedgerSnapshot();
        s.setUploadJobId(job.getId());
        s.setOrganizationId(orgId);
        s.setLedgerName("Acme Traders");
        return snapshotRepository.saveAndFlush(s);
    }

    private ResultActions inlineGstin(String jwt, UploadLedgerSnapshot s, String gstin) throws Exception {
        return mockMvc.perform(patch("/api/v1/uploads/" + job.getId() + "/ledgers/" + s.getId())
                .contentType("application/json").content("{\"gstin\":\"" + gstin + "\"}")
                .header("Authorization", "Bearer " + jwt));
    }

    @Test
    void operatorInlineEdit_afterRejection_submitsForReview_cannotUnlockGate() throws Exception {
        UploadLedgerSnapshot s = acmeSnapshot();
        ValidationFinding f = gstinFinding();
        act(operatorJwt, f, "{\"action\":\"ACKNOWLEDGE\",\"value\":\"29ABCDE1234F1Z5\"}").andExpect(status().isOk());
        act(accountantJwt, f, "{\"action\":\"REJECT\",\"note\":\"wrong GSTIN\"}").andExpect(status().isOk());

        inlineGstin(operatorJwt, s, "29ABCDE1234F1Z5").andExpect(status().isOk());

        findingRepository.flush();
        ValidationFinding after = findingRepository.findById(f.getId()).orElseThrow();
        assertThat(after.getResolveStatus()).isEqualTo(ResolveStatus.ACKNOWLEDGED);
        assertThat(after.getSubmittedBy()).isEqualTo("wf_operator");
        assertThat(after.getProposedValue()).isEqualTo("29ABCDE1234F1Z5");
        assertThat(after.getReviewNote()).isNull();
        assertThat(after.getResolvedBy()).isNull();
        assertThat(reloadMaster().getGstin()).isNull();
        assertThat(gateService.checkGate(orgId).gated()).isTrue();

        act(accountantJwt, f, "{\"action\":\"APPROVE\"}").andExpect(status().isOk());
        findingRepository.flush();
        assertThat(reloadMaster().getGstin()).isEqualTo("29ABCDE1234F1Z5");
        assertThat(gateService.checkGate(orgId).gated()).isFalse();
    }

    @Test
    void operatorInlineEdit_invalidValue_leavesFindingOpen() throws Exception {
        UploadLedgerSnapshot s = acmeSnapshot();
        ValidationFinding f = gstinFinding();

        inlineGstin(operatorJwt, s, "abc").andExpect(status().isOk());

        findingRepository.flush();
        assertThat(findingRepository.findById(f.getId()).orElseThrow().getResolveStatus())
                .isEqualTo(ResolveStatus.OPEN);
        assertThat(gateService.checkGate(orgId).gated()).isTrue();
    }

    @Test
    void accountantInlineEdit_resolvesFindingInOneStep() throws Exception {
        UploadLedgerSnapshot s = acmeSnapshot();
        ValidationFinding f = gstinFinding();

        inlineGstin(accountantJwt, s, "29ABCDE1234F1Z5").andExpect(status().isOk());

        findingRepository.flush();
        ValidationFinding after = findingRepository.findById(f.getId()).orElseThrow();
        assertThat(after.getResolveStatus()).isEqualTo(ResolveStatus.RESOLVED);
        assertThat(after.getResolvedBy()).isEqualTo("wf_accountant");
        assertThat(gateService.checkGate(orgId).gated()).isFalse();
    }

    // ── validation at submission ─────────────────────────────────────────────

    @Test
    void typedFinding_blankValue_returns400_findingStaysOpen_noAuditRows() throws Exception {
        ValidationFinding f = gstinFinding();

        act(operatorJwt, f, "{\"action\":\"ACKNOWLEDGE\",\"value\":\"  \"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("A value is required to resolve this finding."));

        assertThat(findingRepository.findById(f.getId()).orElseThrow().getResolveStatus()).isEqualTo(ResolveStatus.OPEN);
        assertThat(editLogRepository.findByFindingId(f.getId())).isEmpty();
        assertThat(reloadMaster().getGstin()).isNull();
    }

    @Test
    void gstApplicability_unknownConstant_returns400ListingAllowedValues() throws Exception {
        ValidationFinding f = finding("GST_APPLICABILITY", null, "Acme Traders", null, null);

        act(accountantJwt, f, "{\"action\":\"ACKNOWLEDGE\",\"value\":\"BOGUS\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("TAXABLE")));
    }

    // ── reject ───────────────────────────────────────────────────────────────

    @Test
    void reject_requiresNote_returns400() throws Exception {
        ValidationFinding f = gstinFinding();
        act(operatorJwt, f, "{\"action\":\"ACKNOWLEDGE\",\"value\":\"27ABCDE1234F1Z5\"}").andExpect(status().isOk());

        act(accountantJwt, f, "{\"action\":\"REJECT\"}").andExpect(status().isBadRequest());
    }

    @Test
    void reject_returnsToOpen_keepsProposalAndNote_writesNothing_thenResubmitClearsReview() throws Exception {
        ValidationFinding f = gstinFinding();
        act(operatorJwt, f, "{\"action\":\"ACKNOWLEDGE\",\"note\":\"n1\",\"value\":\"27ABCDE1234F1Z5\"}").andExpect(status().isOk());

        act(accountantJwt, f, "{\"action\":\"REJECT\",\"note\":\"not a valid GSTIN\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resolveStatus").value("OPEN"))
                .andExpect(jsonPath("$.proposedValue").value("27ABCDE1234F1Z5"))
                .andExpect(jsonPath("$.resolveNote").value("n1"))
                .andExpect(jsonPath("$.reviewNote").value("not a valid GSTIN"))
                .andExpect(jsonPath("$.reviewedBy").value("wf_accountant"))
                .andExpect(jsonPath("$.resolvedBy").doesNotExist());

        assertThat(reloadMaster().getGstin()).isNull();
        assertThat(editLogRepository.findByFindingId(f.getId())).isEmpty();
        assertThat(gateService.checkGate(orgId).gated()).isTrue();

        act(operatorJwt, f, "{\"action\":\"ACKNOWLEDGE\",\"note\":\"n2\",\"value\":\"29ABCDE1234F1Z5\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resolveStatus").value("ACKNOWLEDGED"))
                .andExpect(jsonPath("$.proposedValue").value("29ABCDE1234F1Z5"))
                .andExpect(jsonPath("$.reviewNote").doesNotExist())
                .andExpect(jsonPath("$.reviewedBy").doesNotExist());
    }

    // ── authorization ────────────────────────────────────────────────────────

    @Test
    void operatorCannotApprove_selfApprovalBypassIsClosed() throws Exception {
        ValidationFinding f = gstinFinding();
        act(operatorJwt, f, "{\"action\":\"ACKNOWLEDGE\",\"value\":\"29ABCDE1234F1Z5\"}").andExpect(status().isOk());

        act(operatorJwt, f, "{\"action\":\"APPROVE\"}").andExpect(status().isForbidden());

        assertThat(reloadMaster().getGstin()).isNull();
    }

    @Test
    void operatorCannotReject() throws Exception {
        ValidationFinding f = gstinFinding();
        act(operatorJwt, f, "{\"action\":\"ACKNOWLEDGE\",\"value\":\"29ABCDE1234F1Z5\"}").andExpect(status().isOk());

        act(operatorJwt, f, "{\"action\":\"REJECT\",\"note\":\"x\"}").andExpect(status().isForbidden());
    }

    @Test
    void auditorCa_cannotApproveRejectOrSubmit() throws Exception {
        ValidationFinding f = gstinFinding();
        act(operatorJwt, f, "{\"action\":\"ACKNOWLEDGE\",\"value\":\"29ABCDE1234F1Z5\"}").andExpect(status().isOk());

        act(auditorJwt, f, "{\"action\":\"APPROVE\"}").andExpect(status().isForbidden());
        act(auditorJwt, f, "{\"action\":\"REJECT\",\"note\":\"x\"}").andExpect(status().isForbidden());
        act(auditorJwt, f, "{\"action\":\"ACKNOWLEDGE\",\"value\":\"29ABCDE1234F1Z5\"}").andExpect(status().isForbidden());
        assertThat(reloadMaster().getGstin()).isNull();
    }

    @Test
    void owner_cannotApproveRejectOrSubmit() throws Exception {
        ValidationFinding f = gstinFinding();

        act(ownerJwt, f, "{\"action\":\"ACKNOWLEDGE\",\"value\":\"29ABCDE1234F1Z5\"}").andExpect(status().isForbidden());
        act(ownerJwt, f, "{\"action\":\"APPROVE\"}").andExpect(status().isForbidden());
        act(ownerJwt, f, "{\"action\":\"REJECT\",\"note\":\"x\"}").andExpect(status().isForbidden());
    }

    @Test
    void approveOfNonPendingFinding_returns400() throws Exception {
        ValidationFinding f = gstinFinding(); // still OPEN

        act(accountantJwt, f, "{\"action\":\"APPROVE\"}").andExpect(status().isBadRequest());
    }

    @Test
    void doubleApprove_secondReturns400() throws Exception {
        ValidationFinding f = gstinFinding();
        act(operatorJwt, f, "{\"action\":\"ACKNOWLEDGE\",\"value\":\"29ABCDE1234F1Z5\"}").andExpect(status().isOk());
        act(accountantJwt, f, "{\"action\":\"APPROVE\"}").andExpect(status().isOk());

        act(accountantJwt, f, "{\"action\":\"APPROVE\"}").andExpect(status().isBadRequest());

        assertThat(editLogRepository.findByFindingId(f.getId())).hasSize(1);
    }

    @Test
    void accountantOfAnotherOrganization_getsNotFound_andMasterIsNeverTouched() throws Exception {
        ValidationFinding f = gstinFinding();
        act(operatorJwt, f, "{\"action\":\"ACKNOWLEDGE\",\"value\":\"29ABCDE1234F1Z5\"}").andExpect(status().isOk());
        UUID otherOrg = organizationRepository.save(new Organization("Other Org " + UUID.randomUUID())).getId();
        String otherJwt = jwtUtils.generateJwtTokenForUser("wf_accountant", otherOrg);

        act(otherJwt, f, "{\"action\":\"APPROVE\"}").andExpect(status().isNotFound());

        assertThat(reloadMaster().getGstin()).isNull();
    }

    @Test
    void removedPatchResolveEndpoint_isGone() throws Exception {
        ValidationFinding f = gstinFinding();

        mockMvc.perform(patch("/api/v1/uploads/" + job.getId() + "/mismatches/" + f.getId() + "/resolve")
                        .contentType("application/json").content("{\"status\":\"APPROVED\"}")
                        .header("Authorization", "Bearer " + operatorJwt))
                .andExpect(status().is(org.hamcrest.Matchers.anyOf(
                        org.hamcrest.Matchers.is(404), org.hamcrest.Matchers.is(405))));

        assertThat(findingRepository.findById(f.getId()).orElseThrow().getResolveStatus()).isEqualTo(ResolveStatus.OPEN);
    }
}
