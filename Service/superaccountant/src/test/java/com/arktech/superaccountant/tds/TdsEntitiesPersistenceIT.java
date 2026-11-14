package com.arktech.superaccountant.tds;

import com.arktech.superaccountant.masters.models.FindingSeverity;
import com.arktech.superaccountant.masters.models.Organization;
import com.arktech.superaccountant.masters.models.ResolveStatus;
import com.arktech.superaccountant.masters.repository.OrganizationRepository;
import com.arktech.superaccountant.tds.models.TdsChallan;
import com.arktech.superaccountant.tds.models.TdsFinding;
import com.arktech.superaccountant.tds.models.TdsFindingType;
import com.arktech.superaccountant.tds.models.TdsPaymentDetail;
import com.arktech.superaccountant.tds.repository.TdsChallanRepository;
import com.arktech.superaccountant.tds.repository.TdsFindingRepository;
import com.arktech.superaccountant.tds.repository.TdsPaymentDetailRepository;
import com.arktech.superaccountant.workflow.models.Workflow;
import com.arktech.superaccountant.workflow.models.WorkflowStage;
import com.arktech.superaccountant.workflow.models.WorkflowType;
import com.arktech.superaccountant.workflow.repository.WorkflowRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
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
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
@Transactional
@TestPropertySource(properties = "JWT_SECRET=test-jwt-secret-must-be-at-least-32-characters-long")
class TdsEntitiesPersistenceIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private WorkflowRepository workflowRepository;
    @Autowired private TdsPaymentDetailRepository paymentDetailRepository;
    @Autowired private TdsChallanRepository challanRepository;
    @Autowired private TdsFindingRepository findingRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @PersistenceContext private EntityManager entityManager;

    private Organization org;

    private Workflow paymentWorkflow(LocalDate periodStart) {
        Workflow w = new Workflow();
        w.setOrganizationId(org.getId());
        w.setType(WorkflowType.TDS_PAYMENT);
        w.setPeriodStart(periodStart);
        w.setPeriodEnd(periodStart.plusMonths(1).minusDays(1));
        w.setPeriodKey(periodStart.toString().substring(0, 7));
        w.setFinancialYear(2025);
        w.setStage(WorkflowStage.STARTED);
        return workflowRepository.saveAndFlush(w);
    }

    private void initOrg() {
        org = new Organization("TDS IT Org");
        org.setBooksStartDate(LocalDate.of(2025, 4, 1));
        org = organizationRepository.saveAndFlush(org);
    }

    @Test
    void paymentDetail_uniquePerWorkflow() {
        initOrg();
        Workflow w = paymentWorkflow(LocalDate.of(2025, 4, 1));
        TdsPaymentDetail d = new TdsPaymentDetail();
        d.setWorkflowId(w.getId());
        d.setMonth(LocalDate.of(2025, 4, 1));
        d.setDueDate(LocalDate.of(2025, 5, 7));
        paymentDetailRepository.saveAndFlush(d);

        TdsPaymentDetail dup = new TdsPaymentDetail();
        dup.setWorkflowId(w.getId());
        dup.setMonth(LocalDate.of(2025, 4, 1));
        dup.setDueDate(LocalDate.of(2025, 5, 7));
        paymentDetailRepository.save(dup);

        assertThatThrownBy(() -> paymentDetailRepository.flush())
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void challansReadAcrossThreePaymentWorkflows() {
        initOrg();
        Workflow w1 = paymentWorkflow(LocalDate.of(2025, 4, 1));
        Workflow w2 = paymentWorkflow(LocalDate.of(2025, 5, 1));

        TdsChallan c1 = challan(w1.getId(), LocalDate.of(2025, 5, 3));
        TdsChallan c2 = challan(w1.getId(), LocalDate.of(2025, 5, 5));
        TdsChallan c3 = challan(w2.getId(), LocalDate.of(2025, 6, 5));
        challanRepository.saveAllAndFlush(List.of(c1, c2, c3));

        List<TdsChallan> inW1 = challanRepository.findByPaymentWorkflowIdOrderByDepositDateAsc(w1.getId());
        assertThat(inW1).extracting(TdsChallan::getId).containsExactly(c1.getId(), c2.getId());

        List<TdsChallan> both = challanRepository.findByPaymentWorkflowIdIn(List.of(w1.getId(), w2.getId()));
        assertThat(both).hasSize(3);
        assertThat(challanRepository.findByPaymentWorkflowIdIn(List.of(UUID.randomUUID()))).isEmpty();
    }

    private TdsChallan challan(UUID workflowId, LocalDate depositDate) {
        TdsChallan c = new TdsChallan();
        c.setPaymentWorkflowId(workflowId);
        c.setDepositDate(depositDate);
        c.setBsrCode("1234567");
        c.setChallanSerialNumber("12345");
        c.setTdsAmount(new BigDecimal("1500.00"));
        return c;
    }

    @Test
    void finding_defaultsOpen_andEnumsAsVarchar() {
        initOrg();
        Workflow w = paymentWorkflow(LocalDate.of(2025, 4, 1));

        TdsFinding f = new TdsFinding();
        f.setOrganizationId(org.getId());
        f.setWorkflowId(w.getId());
        f.setUploadJobId(UUID.randomUUID());
        f.setFindingType(TdsFindingType.SHORT_DEDUCTED);
        f.setSeverity(FindingSeverity.MEDIUM);
        f = findingRepository.saveAndFlush(f);

        String type = jdbcTemplate.queryForObject(
                "SELECT finding_type FROM tds_findings WHERE id = ?",
                String.class, f.getId());
        String status = jdbcTemplate.queryForObject(
                "SELECT resolve_status FROM tds_findings WHERE id = ?",
                String.class, f.getId());

        assertThat(type).isEqualTo("SHORT_DEDUCTED");
        assertThat(status).isEqualTo("OPEN");
        assertThat(f.getResolveStatus()).isEqualTo(ResolveStatus.OPEN);
    }

    @Test
    void finding_countBlockingAllSeverities() {
        initOrg();
        Workflow w = paymentWorkflow(LocalDate.of(2025, 4, 1));
        UUID jobId = UUID.randomUUID();

        TdsFinding lowOpen = finding(w, jobId, TdsFindingType.NOT_DEDUCTED, FindingSeverity.LOW, ResolveStatus.OPEN);
        TdsFinding highAck = finding(w, jobId, TdsFindingType.PAN_MISSING, FindingSeverity.HIGH, ResolveStatus.ACKNOWLEDGED);
        TdsFinding medApproved = finding(w, jobId, TdsFindingType.WRONG_SECTION, FindingSeverity.MEDIUM, ResolveStatus.APPROVED);
        findingRepository.saveAllAndFlush(List.of(lowOpen, highAck, medApproved));

        long blocking = findingRepository.countByWorkflowIdAndResolveStatusNotIn(
                w.getId(), List.of(ResolveStatus.APPROVED, ResolveStatus.RESOLVED, ResolveStatus.DISCARDED));

        assertThat(blocking).isEqualTo(2);
    }

    private TdsFinding finding(Workflow w, UUID jobId, TdsFindingType type, FindingSeverity severity, ResolveStatus status) {
        TdsFinding f = new TdsFinding();
        f.setOrganizationId(org.getId());
        f.setWorkflowId(w.getId());
        f.setUploadJobId(jobId);
        f.setFindingType(type);
        f.setSeverity(severity);
        f.setResolveStatus(status);
        return f;
    }
}
