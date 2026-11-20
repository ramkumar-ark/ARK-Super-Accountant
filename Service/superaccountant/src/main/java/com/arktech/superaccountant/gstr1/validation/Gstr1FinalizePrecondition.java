package com.arktech.superaccountant.gstr1.validation;

import com.arktech.superaccountant.gstr1.models.Gstr1FillInAttribute;
import com.arktech.superaccountant.gstr1.models.Gstr1FillInScope;
import com.arktech.superaccountant.gstr1.models.PreparedReturn;
import com.arktech.superaccountant.gstr1.models.PreparedReturnRow;
import com.arktech.superaccountant.gstr1.repository.Gstr1FillInRepository;
import com.arktech.superaccountant.gstr1.repository.PreparedReturnRepository;
import com.arktech.superaccountant.gstr1.repository.PreparedReturnRowRepository;
import com.arktech.superaccountant.gstr1.services.tables.AmendmentBuilder;
import com.arktech.superaccountant.workflow.models.Workflow;
import com.arktech.superaccountant.workflow.models.WorkflowType;
import com.arktech.superaccountant.workflow.seam.FinalizePrecondition;
import com.arktech.superaccountant.workflow.snapshot.AmendmentChangeKind;
import com.arktech.superaccountant.workflow.snapshot.AmendmentFollowUp;
import com.arktech.superaccountant.workflow.snapshot.AmendmentFollowUpRepository;
import com.arktech.superaccountant.workflow.snapshot.AmendmentStatus;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Finalize gate for GSTR-1 workflows: the prepared return must exist, its
 * validation must have no ERROR findings, and every open DELETED amendment
 * follow-up affecting this return must carry the accountant's amend-to-zero
 * acknowledgement (AMENDMENT_ZERO_ACK fill-in, R5). WARNING findings never block.
 */
@Component
public class Gstr1FinalizePrecondition implements FinalizePrecondition {

    private static final String RETURN_KIND = "GSTR1";

    private final Gstr1Validator validator;
    private final PreparedReturnRepository preparedReturnRepository;
    private final PreparedReturnRowRepository rowRepository;
    private final AmendmentFollowUpRepository followUpRepository;
    private final Gstr1FillInRepository fillInRepository;
    private final AmendmentBuilder amendmentBuilder;

    public Gstr1FinalizePrecondition(Gstr1Validator validator,
                                     PreparedReturnRepository preparedReturnRepository,
                                     PreparedReturnRowRepository rowRepository,
                                     AmendmentFollowUpRepository followUpRepository,
                                     Gstr1FillInRepository fillInRepository,
                                     AmendmentBuilder amendmentBuilder) {
        this.validator = validator;
        this.preparedReturnRepository = preparedReturnRepository;
        this.rowRepository = rowRepository;
        this.followUpRepository = followUpRepository;
        this.fillInRepository = fillInRepository;
        this.amendmentBuilder = amendmentBuilder;
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
        List<String> unmet = new ArrayList<>(validator.validate(workflow, rows).stream()
                .filter(result -> result.severity() == ValidationResult.Severity.ERROR)
                .map(result -> result.code() + " [" + result.tableCode() + " "
                        + result.rowKey() + "]: " + result.message())
                .toList());
        unmet.addAll(ackGaps(workflow));
        return unmet;
    }

    private List<String> ackGaps(Workflow workflow) {
        List<String> unmet = new ArrayList<>();
        for (AmendmentFollowUp followUp : followUpRepository.findByOrganizationIdAndStatus(
                workflow.getOrganizationId(), AmendmentStatus.OPEN)) {
            if (followUp.getChange() != AmendmentChangeKind.DELETED
                    || followUp.getWorkflowId().equals(workflow.getId())
                    || followUp.getVoucherKey() == null
                    || followUp.getTableCode() == null
                    || !amendmentBuilder.canAmendToZero(followUp)) {
                continue;
            }
            boolean acked = fillInRepository.findByWorkflowIdAndScopeAndKeyAndAttribute(
                    workflow.getId(), Gstr1FillInScope.VOUCHER, followUp.getVoucherKey(),
                    Gstr1FillInAttribute.AMENDMENT_ZERO_ACK).isPresent();
            if (!acked) {
                unmet.add("AMENDMENT_ACK_MISSING [" + followUp.getTableCode() + " "
                        + followUp.getVoucherKey() + "]: deleted document " + followUp.getVoucherKey()
                        + " must be acknowledged for amend-to-zero via the AMENDMENT_ZERO_ACK"
                        + " fill-in before finalizing");
            }
        }
        return unmet;
    }
}
