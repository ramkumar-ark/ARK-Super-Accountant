package com.arktech.superaccountant.gst.models;

import com.arktech.superaccountant.masters.models.FindingSeverity;
import com.arktech.superaccountant.masters.models.ResolveStatus;
import com.arktech.superaccountant.gst.repository.GstDaybookFindingRepository;
import jakarta.persistence.EntityManager;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Persistence contract for GstDaybookFinding: minimal save defaults to OPEN, enums persist as
 * varchar, and the C2 HIGH-only gate count query. Needs Docker (runs in CI).
 */
@SpringBootTest
@Testcontainers
@Transactional
@TestPropertySource(properties = "JWT_SECRET=test-jwt-secret-must-be-at-least-32-characters-long")
class GstDaybookFindingPersistenceIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    GstDaybookFindingRepository repository;

    @Autowired
    EntityManager entityManager;

    @Test
    void savesMinimalFindingWithOpenDefault() {
        GstDaybookFinding f = minimalFinding();
        f.setFindingType(GstFindingType.GST_NOT_CHARGED);
        f.setSeverity(FindingSeverity.HIGH);
        f.setMessage("GST not charged on taxable purchase");

        GstDaybookFinding saved = repository.saveAndFlush(f);
        entityManager.clear();
        GstDaybookFinding loaded = repository.findById(saved.getId()).orElseThrow();

        assertThat(loaded.getResolveStatus()).isEqualTo(ResolveStatus.OPEN);
        assertThat(loaded.getWorkflowId()).isEqualTo(f.getWorkflowId());
        assertThat(loaded.getUploadJobId()).isEqualTo(f.getUploadJobId());
        assertThat(loaded.getOrganizationId()).isEqualTo(f.getOrganizationId());
        assertThat(loaded.getFindingType()).isEqualTo(GstFindingType.GST_NOT_CHARGED);
        assertThat(loaded.getSeverity()).isEqualTo(FindingSeverity.HIGH);
        assertThat(loaded.getMessage()).isEqualTo("GST not charged on taxable purchase");
    }

    @Test
    void findingTypeAndVoucherSidePersistAsVarchar() {
        GstDaybookFinding f = minimalFinding();
        f.setFindingType(GstFindingType.GST_AMOUNT_MISMATCH);
        f.setSeverity(FindingSeverity.MEDIUM);
        f.setVoucherSide(VoucherSide.SALES);
        f.setMessage("mismatch");

        repository.saveAndFlush(f);
        entityManager.clear();

        Object[] row = (Object[]) entityManager.createNativeQuery(
                        "select finding_type, voucher_side from gst_daybook_findings where finding_type = 'GST_AMOUNT_MISMATCH'")
                .getSingleResult();

        assertThat(row[0]).isEqualTo("GST_AMOUNT_MISMATCH");
        assertThat(row[1]).isEqualTo("SALES");
    }

    @Test
    void countsHighUnresolvedPerWorkflow() {
        UUID workflowW = UUID.randomUUID();
        UUID otherWorkflow = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();

        repository.save(finding(workflowW, orgId, jobId, FindingSeverity.HIGH, ResolveStatus.OPEN));
        repository.save(finding(workflowW, orgId, jobId, FindingSeverity.HIGH, ResolveStatus.APPROVED));
        repository.save(finding(workflowW, orgId, jobId, FindingSeverity.MEDIUM, ResolveStatus.OPEN));
        repository.save(finding(otherWorkflow, orgId, jobId, FindingSeverity.HIGH, ResolveStatus.OPEN));
        repository.flush();

        long count = repository.countByWorkflowIdAndSeverityAndResolveStatusNotIn(
                workflowW, FindingSeverity.HIGH, List.of(ResolveStatus.APPROVED, ResolveStatus.RESOLVED, ResolveStatus.DISCARDED));

        assertThat(count).isEqualTo(1L);
    }

    private GstDaybookFinding minimalFinding() {
        GstDaybookFinding f = new GstDaybookFinding();
        f.setOrganizationId(UUID.randomUUID());
        f.setWorkflowId(UUID.randomUUID());
        f.setUploadJobId(UUID.randomUUID());
        return f;
    }

    private GstDaybookFinding finding(UUID workflowId, UUID orgId, UUID jobId, FindingSeverity severity, ResolveStatus status) {
        GstDaybookFinding f = new GstDaybookFinding();
        f.setOrganizationId(orgId);
        f.setWorkflowId(workflowId);
        f.setUploadJobId(jobId);
        f.setFindingType(GstFindingType.GST_NOT_CHARGED);
        f.setSeverity(severity);
        f.setResolveStatus(status);
        f.setVoucherDate(LocalDate.of(2026, 4, 1));
        f.setTaxableAmount(BigDecimal.ZERO);
        f.setMessage("test");
        return f;
    }
}
