package com.arktech.superaccountant.filedreturns;

import com.arktech.superaccountant.masters.models.Organization;
import com.arktech.superaccountant.masters.repository.OrganizationRepository;
import com.arktech.superaccountant.workflow.models.Workflow;
import com.arktech.superaccountant.workflow.models.WorkflowStage;
import com.arktech.superaccountant.workflow.models.WorkflowType;
import com.arktech.superaccountant.workflow.repository.WorkflowRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FiledReturnImportServiceTest {

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private WorkflowRepository workflowRepository;

    @Mock
    private FiledReturnSnapshotRepository snapshotRepository;

    @Mock
    private FiledReturnLineRepository lineRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private FiledReturnImportService service;

    private static final UUID ORG = UUID.randomUUID();
    private static final UUID BASELINE_ID = UUID.randomUUID();

    private Workflow monthlyGstr1Row() {
        Workflow w = new Workflow();
        w.setId(BASELINE_ID);
        w.setOrganizationId(ORG);
        w.setType(WorkflowType.GSTR1);
        w.setPeriodStart(LocalDate.of(2026, 4, 1));
        w.setPeriodEnd(LocalDate.of(2026, 4, 30));
        w.setPeriodKey("2026-04");
        w.setStage(WorkflowStage.BASELINE);
        w.setActive(true);
        return w;
    }

    @BeforeEach
    void setUp() {
        Organization org = new Organization();
        org.setId(ORG);
        org.setGstin("29ABCDE1234F1Z5");
        lenient().when(organizationRepository.findById(ORG)).thenReturn(Optional.of(org));
        lenient().when(workflowRepository.findAllByOrganizationIdAndStageAndActiveTrue(ORG, WorkflowStage.BASELINE))
                .thenReturn(List.of(monthlyGstr1Row()));
        lenient().when(snapshotRepository.findByOrganizationIdAndFormTypeAndFilingPeriodAndSupersededAtIsNull(any(), any(), any()))
                .thenReturn(Optional.empty());
        lenient().when(snapshotRepository.save(any(FiledReturnSnapshot.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    private FiledReturnLine line(String tableCode, String gstin, String docNumber, BigDecimal rate) {
        FiledReturnLine l = new FiledReturnLine();
        l.setTableCode(tableCode);
        l.setCounterpartyGstin(gstin);
        l.setDocNumber(docNumber);
        l.setRate(rate);
        l.setTaxable(BigDecimal.ZERO);
        l.setIgst(BigDecimal.ZERO);
        l.setCgst(BigDecimal.ZERO);
        l.setSgst(BigDecimal.ZERO);
        l.setCess(BigDecimal.ZERO);
        return l;
    }

    private SnapshotDraft draft(FiledFormType formType, String filingPeriod, List<FiledReturnLine> lines) {
        return new SnapshotDraft(formType, filingPeriod, "29ABCDE1234F1Z5", FiledSourceKind.MANUAL_ENTRY,
                null, null, lines);
    }

    @Test
    void computesSameHashRegardlessOfLineOrder() {
        FiledReturnLine a = line("B2B", "27AAECS6620H1ZQ", "INV-1", new BigDecimal("18"));
        FiledReturnLine b = line("B2B", "27AAECS6620H1ZQ", "INV-2", new BigDecimal("5"));

        FiledReturnSnapshot first = service.save(ORG, "tester", draft(FiledFormType.GSTR1, "2026-04", List.of(a, b)));
        FiledReturnSnapshot second = service.save(ORG, "tester", draft(FiledFormType.GSTR1, "2026-04", List.of(b, a)));

        assertEquals(first.getContentHash(), second.getContentHash());
    }

    @Test
    void iffCoversOnlyItsMonth() {
        Workflow quarterly = new Workflow();
        quarterly.setId(UUID.randomUUID());
        quarterly.setOrganizationId(ORG);
        quarterly.setType(WorkflowType.GSTR1);
        quarterly.setPeriodStart(LocalDate.of(2026, 4, 1));
        quarterly.setPeriodEnd(LocalDate.of(2026, 6, 30));
        quarterly.setPeriodKey("2026-Q2");
        quarterly.setStage(WorkflowStage.BASELINE);
        quarterly.setActive(true);
        when(workflowRepository.findAllByOrganizationIdAndStageAndActiveTrue(ORG, WorkflowStage.BASELINE))
                .thenReturn(List.of(quarterly));

        FiledReturnSnapshot snapshot = service.save(ORG, "tester",
                draft(FiledFormType.IFF, "2026-04", List.of(line("B2B", "27AAECS6620H1ZQ", "INV-1", BigDecimal.ONE))));

        assertEquals(LocalDate.of(2026, 4, 1), snapshot.getCoverStart());
        assertEquals(LocalDate.of(2026, 4, 30), snapshot.getCoverEnd());
        assertEquals(quarterly.getId(), snapshot.getBaselineWorkflowId());
    }
}
