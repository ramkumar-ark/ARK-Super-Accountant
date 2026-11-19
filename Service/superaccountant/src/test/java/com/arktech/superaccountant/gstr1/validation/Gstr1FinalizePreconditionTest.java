package com.arktech.superaccountant.gstr1.validation;

import com.arktech.superaccountant.gstr1.models.PreparedReturn;
import com.arktech.superaccountant.gstr1.repository.PreparedReturnRepository;
import com.arktech.superaccountant.gstr1.repository.PreparedReturnRowRepository;
import com.arktech.superaccountant.workflow.models.Workflow;
import com.arktech.superaccountant.workflow.models.WorkflowType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class Gstr1FinalizePreconditionTest extends com.arktech.superaccountant.gstr1.services.tables.TableTestSupport {

    private Gstr1Validator validator;
    private PreparedReturnRepository preparedReturnRepository;
    private PreparedReturnRowRepository rowRepository;
    private Gstr1FinalizePrecondition precondition;

    @BeforeEach
    void setUp() {
        validator = mock(Gstr1Validator.class);
        preparedReturnRepository = mock(PreparedReturnRepository.class);
        rowRepository = mock(PreparedReturnRowRepository.class);
        when(preparedReturnRepository.findByWorkflowIdAndReturnKind(any(UUID.class), any()))
                .thenReturn(Optional.empty());
        precondition = new Gstr1FinalizePrecondition(validator,
                preparedReturnRepository, rowRepository);
    }

    @Test
    void appliesOnlyToGstr1() {
        assertThat(precondition.appliesTo(workflow())).isTrue();
        Workflow tds = workflow();
        tds.setType(WorkflowType.TDS_26Q);
        assertThat(precondition.appliesTo(tds)).isFalse();
    }

    @Test
    void missingPreparedReturnBlocksFinalize() {
        List<String> unmet = precondition.unmet(workflow());
        assertThat(unmet).hasSize(1);
        assertThat(unmet.get(0)).containsIgnoringCase("prepared");
    }

    @Test
    void errorsBlockFinalize_warningsDoNot() {
        PreparedReturn prepared = new PreparedReturn();
        prepared.setId(UUID.randomUUID());
        when(preparedReturnRepository.findByWorkflowIdAndReturnKind(any(UUID.class), any()))
                .thenReturn(Optional.of(prepared));
        when(rowRepository.findByPreparedReturnId(any(UUID.class))).thenReturn(List.of());
        when(validator.validate(any(), anyList())).thenReturn(List.of(
                new ValidationResult("TAX_MISMATCH", ValidationResult.Severity.ERROR,
                        "B2B", "b2b:G:g1", "tax does not match"),
                new ValidationResult("IFF_CAP", ValidationResult.Severity.WARNING,
                        "B2B", "b2b:G:g2", "above IFF cap")));

        List<String> unmet = precondition.unmet(workflow());
        assertThat(unmet).hasSize(1);
        assertThat(unmet.get(0)).contains("TAX_MISMATCH");
        assertThat(unmet.get(0)).doesNotContain("IFF_CAP");
    }

    @Test
    void cleanValidationDoesNotBlock() {
        PreparedReturn prepared = new PreparedReturn();
        prepared.setId(UUID.randomUUID());
        when(preparedReturnRepository.findByWorkflowIdAndReturnKind(any(UUID.class), any()))
                .thenReturn(Optional.of(prepared));
        when(rowRepository.findByPreparedReturnId(any(UUID.class))).thenReturn(List.of());
        when(validator.validate(any(), anyList())).thenReturn(List.of(
                new ValidationResult("IFF_CAP", ValidationResult.Severity.WARNING,
                        "B2B", "b2b:G:g2", "above IFF cap")));
        assertThat(precondition.unmet(workflow())).isEmpty();
    }
}
