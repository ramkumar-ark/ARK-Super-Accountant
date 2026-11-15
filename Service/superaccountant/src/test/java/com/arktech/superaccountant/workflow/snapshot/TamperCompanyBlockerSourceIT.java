package com.arktech.superaccountant.workflow.snapshot;

import com.arktech.superaccountant.masters.models.ResolveStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CompanyBlockerSource implemented by the tamper tables. Calls the bean
 * directly; rows are seeded so the read filters are what is under test.
 */
@SpringBootTest
@Testcontainers
@Transactional
@TestPropertySource(properties = "JWT_SECRET=test-jwt-secret-must-be-at-least-32-characters-long")
class TamperCompanyBlockerSourceIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private TamperCompanyBlockerSource blockers;
    @Autowired
    private TamperFindingRepository tamperFindingRepository;
    @Autowired
    private com.arktech.superaccountant.workflow.repository.WorkflowRepository workflowRepository;
    @Autowired
    private AmendmentFollowUpRepository amendmentFollowUpRepository;

    private final UUID orgId = UUID.randomUUID();

    @Test
    void fyClose_listsOpenTamperFindingDatedInFy() {
        finding(LocalDate.of(2025, 5, 10), ResolveStatus.OPEN, "GSTIN changed for Acme Traders");

        List<String> result = blockers.financialYearCloseBlockers(orgId, 2025);

        assertThat(result).containsExactly("Open tamper finding: GSTIN changed for Acme Traders");
    }

    @Test
    void fyClose_ignoresFindingDatedInOtherFy() {
        finding(LocalDate.of(2024, 5, 10), ResolveStatus.OPEN, "Old-year finding");

        assertThat(blockers.financialYearCloseBlockers(orgId, 2025)).isEmpty();
        assertThat(blockers.baselineConfirmBlockers(orgId))
                .containsExactly("Open tamper finding: Old-year finding");
    }

    @Test
    void fyClose_listsOpenAmendmentFollowUp_notDoneOnes() {
        com.arktech.superaccountant.workflow.models.Workflow w = new com.arktech.superaccountant.workflow.models.Workflow();
        w.setOrganizationId(orgId);
        w.setType(com.arktech.superaccountant.workflow.models.WorkflowType.GSTR1);
        w.setPeriodStart(LocalDate.of(2025, 5, 1));
        w.setPeriodEnd(LocalDate.of(2025, 5, 31));
        w.setPeriodKey("2025-05");
        w.setFinancialYear(2025);
        w.setStage(com.arktech.superaccountant.workflow.models.WorkflowStage.FINALIZED);
        w.setActive(true);
        w.setCreatedBy("acc");
        w = workflowRepository.save(w);

        AmendmentFollowUp open = followUp(w.getId(), AmendmentStatus.OPEN);
        followUp(w.getId(), AmendmentStatus.DONE);

        List<String> result = blockers.financialYearCloseBlockers(orgId, 2025);

        assertThat(result).containsExactly(
                "Open amendment follow-up: " + open.getSummary());
    }

    @Test
    void baselineConfirm_listsAnyOpenTamperFinding_notApprovedOnes() {
        finding(LocalDate.of(2025, 5, 10), ResolveStatus.OPEN, "Voucher tamper");
        finding(LocalDate.of(2025, 6, 10), ResolveStatus.ACKNOWLEDGED, "Masters tamper");
        finding(LocalDate.of(2025, 7, 10), ResolveStatus.APPROVED, "Approved tamper");
        finding(LocalDate.of(2025, 8, 10), ResolveStatus.RESOLVED, "Resolved tamper");
        finding(LocalDate.of(2025, 9, 10), ResolveStatus.DISCARDED, "Discarded tamper");

        List<String> result = blockers.baselineConfirmBlockers(orgId);

        assertThat(result).containsExactlyInAnyOrder(
                "Open tamper finding: Voucher tamper",
                "Open tamper finding: Masters tamper");
        assertThat(blockers.financialYearCloseBlockers(orgId, 2025)).hasSize(2);
    }

    private TamperFinding finding(LocalDate changeDate, ResolveStatus status, String message) {
        TamperFinding f = new TamperFinding();
        f.setOrganizationId(orgId);
        f.setFindingKey("MODIFIED|" + UUID.randomUUID() + "|voucher");
        f.setEvidenceHash("h-" + UUID.randomUUID());
        f.setChangeKind(TamperChangeKind.MODIFIED);
        f.setChangeDate(changeDate);
        f.setAffectedTypes("GSTR1");
        f.setAffectedWorkflowIds("");
        f.setMessage(message);
        f.setResolveStatus(status);
        f.setCreatedAt(java.time.Instant.now());
        return tamperFindingRepository.save(f);
    }

    private AmendmentFollowUp followUp(UUID workflowId, AmendmentStatus status) {
        AmendmentFollowUp f = new AmendmentFollowUp();
        f.setOrganizationId(orgId);
        f.setWorkflowId(workflowId);
        f.setVoucherKey("G:g-1");
        f.setSummary("Sales SB/1 was modified");
        f.setStatus(status);
        f.setCreatedAt(java.time.Instant.now());
        return amendmentFollowUpRepository.save(f);
    }
}
