package com.arktech.superaccountant.gstr1.services;

import com.arktech.superaccountant.daybook.models.ParsedVoucher;
import com.arktech.superaccountant.daybook.repository.ParsedVoucherRepository;
import com.arktech.superaccountant.gstr1.models.Gstr1FillIn;
import com.arktech.superaccountant.gstr1.models.Gstr1FillInAttribute;
import com.arktech.superaccountant.gstr1.models.Gstr1FillInScope;
import com.arktech.superaccountant.gstr1.repository.Gstr1FillInRepository;
import com.arktech.superaccountant.workflow.models.Workflow;
import com.arktech.superaccountant.workflow.models.WorkflowStage;
import com.arktech.superaccountant.workflow.models.WorkflowType;
import com.arktech.superaccountant.workflow.repository.WorkflowRepository;
import com.arktech.superaccountant.workflow.service.WorkflowConflictException;
import com.arktech.superaccountant.workflow.snapshot.VoucherKeys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Testcontainers
@TestPropertySource(properties = "JWT_SECRET=test-jwt-secret-must-be-at-least-32-characters-long")
class Gstr1FillInServiceTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private Gstr1FillInService fillInService;

    @Autowired
    private Gstr1FillInRepository fillInRepository;

    @Autowired
    private WorkflowRepository workflowRepository;

    @Autowired
    private ParsedVoucherRepository parsedVoucherRepository;

    private static final String ACCOUNTANT = "ROLE_ACCOUNTANT";

    private Workflow workflow(WorkflowStage stage, WorkflowType type) {
        Workflow w = new Workflow();
        w.setOrganizationId(UUID.randomUUID());
        w.setType(type);
        w.setPeriodStart(LocalDate.of(2026, 4, 1));
        w.setPeriodEnd(LocalDate.of(2026, 4, 30));
        w.setPeriodKey("042026");
        w.setFinancialYear(2026);
        w.setStage(stage);
        w.setActive(true);
        w.setCreatedBy("tester");
        return workflowRepository.save(w);
    }

    @Test
    void voucherFillInBeatsLedgerScope() {
        Workflow wf = workflow(WorkflowStage.PREPARE, WorkflowType.GSTR1);
        wf.setUploadJobId(UUID.randomUUID());
        wf = workflowRepository.save(wf);

        ParsedVoucher pv = new ParsedVoucher();
        pv.setOrganizationId(wf.getOrganizationId());
        pv.setUploadJobId(wf.getUploadJobId());
        pv.setVoucherGuid("guid-1");
        pv.setVoucherTypeName("Sales");
        pv.setVoucherNumber("1");
        pv.setVoucherDate(LocalDate.of(2026, 4, 5));
        pv.setPlaceOfSupply("27");
        pv.setSupplyNature("B2B");
        pv.setTypeOfSupply("Inter-State");
        pv = parsedVoucherRepository.save(pv);
        String voucherKey = VoucherKeys.of(pv);

        fillInService.upsert(wf, Gstr1FillInScope.LEDGER, "Sales - Local",
                Gstr1FillInAttribute.POS, "29", ACCOUNTANT, "acc");
        fillInService.upsert(wf, Gstr1FillInScope.VOUCHER, voucherKey,
                Gstr1FillInAttribute.POS, "33", ACCOUNTANT, "acc");

        assertEquals(Optional.of("33"),
                fillInService.resolve(wf, voucherKey, "Sales - Local", Gstr1FillInAttribute.POS));

        fillInRepository.delete(fillInRepository
                .findByWorkflowIdAndScopeAndKeyAndAttribute(wf.getId(),
                        Gstr1FillInScope.VOUCHER, voucherKey, Gstr1FillInAttribute.POS).orElseThrow());

        assertEquals(Optional.of("29"),
                fillInService.resolve(wf, voucherKey, "Sales - Local", Gstr1FillInAttribute.POS));

        Gstr1FillIn ledger = fillInRepository
                .findByWorkflowIdAndScopeAndKeyAndAttribute(wf.getId(),
                        Gstr1FillInScope.LEDGER, "Sales - Local", Gstr1FillInAttribute.POS).orElseThrow();
        fillInRepository.delete(ledger);

        assertEquals(Optional.empty(),
                fillInService.resolve(wf, voucherKey, "Sales - Local", Gstr1FillInAttribute.POS));
        assertEquals(Optional.empty(),
                fillInService.resolve(wf, voucherKey, null, Gstr1FillInAttribute.PORT_CODE));
    }

    @Test
    void carryForwardCopiesLedgerScopeOnlyIntoSameTypePrepareWorkflow() {
        Workflow seed = workflow(WorkflowStage.PREPARE, WorkflowType.GSTR1);
        Workflow otherType = workflow(WorkflowStage.PREPARE, WorkflowType.GSTR3B);
        Workflow notPrepare = workflow(WorkflowStage.FINDINGS_REVIEW, WorkflowType.GSTR1);

        fillInService.upsert(seed, Gstr1FillInScope.LEDGER, "Sales - Local",
                Gstr1FillInAttribute.POS, "29", ACCOUNTANT, "acc");
        fillInService.upsert(seed, Gstr1FillInScope.LEDGER, "Export Ledger",
                Gstr1FillInAttribute.EXPORT_TYPE, "EXPWP", ACCOUNTANT, "acc");
        String voucherKey = "G:some-guid";
        fillInService.upsert(seed, Gstr1FillInScope.VOUCHER, voucherKey,
                Gstr1FillInAttribute.POS, "33", ACCOUNTANT, "acc");

        Workflow finalized = workflowRepository.findById(seed.getId()).orElseThrow();
        finalized.setStage(WorkflowStage.FINALIZED);
        final Workflow from = workflowRepository.save(finalized);

        Workflow to = workflow(WorkflowStage.PREPARE, WorkflowType.GSTR1);
        // pre-existing value in target must not be overwritten
        fillInService.upsert(to, Gstr1FillInScope.LEDGER, "Sales - Local",
                Gstr1FillInAttribute.POS, "24", ACCOUNTANT, "acc");

        assertThrows(WorkflowConflictException.class,
                () -> fillInService.carryForward(from, otherType));
        assertThrows(WorkflowConflictException.class,
                () -> fillInService.carryForward(from, notPrepare));

        fillInService.carryForward(from, to);

        assertEquals(Optional.of("24"), fillInService.resolve(to, null, "Sales - Local",
                Gstr1FillInAttribute.POS));
        assertEquals(Optional.of("EXPWP"), fillInService.resolve(to, null, "Export Ledger",
                Gstr1FillInAttribute.EXPORT_TYPE));
        // VOUCHER-scope fill-ins are NOT carried forward
        assertEquals(Optional.empty(), fillInService.resolve(to, voucherKey, null,
                Gstr1FillInAttribute.POS));
        List<Gstr1FillIn> toFillIns = fillInRepository.findByWorkflowId(to.getId());
        assertEquals(2, toFillIns.size());
        assertTrue(toFillIns.stream().allMatch(f -> f.getScope() == Gstr1FillInScope.LEDGER));
    }

    @Test
    void editsRefusedAfterFinalizedAndByNonAccountant() {
        Workflow finalized = workflow(WorkflowStage.FINALIZED, WorkflowType.GSTR1);
        Workflow filed = workflow(WorkflowStage.FILED, WorkflowType.GSTR1);

        assertThrows(WorkflowConflictException.class,
                () -> fillInService.upsert(finalized, Gstr1FillInScope.LEDGER, "Ledger",
                        Gstr1FillInAttribute.POS, "29", ACCOUNTANT, "acc"));
        assertThrows(WorkflowConflictException.class,
                () -> fillInService.upsert(filed, Gstr1FillInScope.LEDGER, "Ledger",
                        Gstr1FillInAttribute.HSN, "1234", ACCOUNTANT, "acc"));

        Workflow prepare = workflow(WorkflowStage.PREPARE, WorkflowType.GSTR1);
        assertThrows(WorkflowConflictException.class,
                () -> fillInService.upsert(prepare, Gstr1FillInScope.LEDGER, "Ledger",
                        Gstr1FillInAttribute.POS, "29", "ROLE_OPERATOR", "op"));
    }
}
