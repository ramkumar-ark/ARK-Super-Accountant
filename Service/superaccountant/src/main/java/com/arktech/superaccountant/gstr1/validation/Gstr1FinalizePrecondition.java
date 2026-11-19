package com.arktech.superaccountant.gstr1.validation;

import com.arktech.superaccountant.gstr1.models.PreparedReturn;
import com.arktech.superaccountant.gstr1.models.PreparedReturnRow;
import com.arktech.superaccountant.gstr1.repository.PreparedReturnRepository;
import com.arktech.superaccountant.gstr1.repository.PreparedReturnRowRepository;
import com.arktech.superaccountant.workflow.models.Workflow;
import com.arktech.superaccountant.workflow.models.WorkflowType;
import com.arktech.superaccountant.workflow.seam.FinalizePrecondition;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Finalize gate for GSTR-1 workflows: the prepared return must exist and its
 * validation must have no ERROR findings. WARNING findings never block.
 */
@Component
public class Gstr1FinalizePrecondition implements FinalizePrecondition {

    private static final String RETURN_KIND = "GSTR1";

    private final Gstr1Validator validator;
    private final PreparedReturnRepository preparedReturnRepository;
    private final PreparedReturnRowRepository rowRepository;

    public Gstr1FinalizePrecondition(Gstr1Validator validator,
                                     PreparedReturnRepository preparedReturnRepository,
                                     PreparedReturnRowRepository rowRepository) {
        this.validator = validator;
        this.preparedReturnRepository = preparedReturnRepository;
        this.rowRepository = rowRepository;
    }

    @Override
    public boolean appliesTo(Workflow workflow) {
        return workflow.getType() == WorkflowType.GSTR1;
    }

    @Override
    public List<String> unmet(Workflow workflow) {
        Optional<PreparedReturn> prepared = preparedReturnRepository
                .findByWorkflowIdAndReturnKind(workflow.getId(), RETURN_KIND);
        if (prepared.isEmpty()) {
            return List.of("The GSTR-1 prepared return has not been built yet");
        }
        List<PreparedReturnRow> rows = rowRepository.findByPreparedReturnId(prepared.get().getId());
        return validator.validate(workflow, rows).stream()
                .filter(result -> result.severity() == ValidationResult.Severity.ERROR)
                .map(result -> result.code() + " [" + result.tableCode() + " "
                        + result.rowKey() + "]: " + result.message())
                .toList();
    }
}
