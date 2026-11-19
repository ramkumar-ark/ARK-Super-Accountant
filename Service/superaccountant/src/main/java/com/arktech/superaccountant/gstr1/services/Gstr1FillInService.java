package com.arktech.superaccountant.gstr1.services;

import com.arktech.superaccountant.gstr1.models.Gstr1FillIn;
import com.arktech.superaccountant.gstr1.models.Gstr1FillInAttribute;
import com.arktech.superaccountant.gstr1.models.Gstr1FillInScope;
import com.arktech.superaccountant.gstr1.repository.Gstr1FillInRepository;
import com.arktech.superaccountant.workflow.models.Workflow;
import com.arktech.superaccountant.workflow.models.WorkflowStage;
import com.arktech.superaccountant.workflow.service.WorkflowConflictException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class Gstr1FillInService {

    private static final String ACCOUNTANT_ROLE = "ROLE_ACCOUNTANT";
    private static final String CARRY_FORWARD_ACTOR = "CARRY-FORWARD";

    @Autowired
    private Gstr1FillInRepository fillInRepository;

    @Transactional
    public Gstr1FillIn upsert(Workflow workflow, Gstr1FillInScope scope, String key,
                              Gstr1FillInAttribute attribute, String value,
                              String role, String actor) {
        requireEditable(workflow, role);
        Gstr1FillIn fillIn = fillInRepository
                .findByWorkflowIdAndScopeAndKeyAndAttribute(workflow.getId(), scope, key, attribute)
                .orElseGet(() -> {
                    Gstr1FillIn f = new Gstr1FillIn();
                    f.setWorkflowId(workflow.getId());
                    f.setScope(scope);
                    f.setKey(key);
                    f.setAttribute(attribute);
                    return f;
                });
        fillIn.setValue(value);
        fillIn.setEnteredBy(actor);
        fillIn.setEnteredAt(Instant.now());
        return fillInRepository.save(fillIn);
    }

    @Transactional
    public void delete(Workflow workflow, Gstr1FillInScope scope, String key,
                       Gstr1FillInAttribute attribute, String role) {
        requireEditable(workflow, role);
        fillInRepository.findByWorkflowIdAndScopeAndKeyAndAttribute(
                        workflow.getId(), scope, key, attribute)
                .ifPresent(fillInRepository::delete);
    }

    public Optional<String> resolve(Workflow workflow, String voucherKey, String ledgerName,
                                    Gstr1FillInAttribute attribute) {
        if (voucherKey != null) {
            Optional<String> voucher = fillInRepository
                    .findByWorkflowIdAndScopeAndKeyAndAttribute(workflow.getId(),
                            Gstr1FillInScope.VOUCHER, voucherKey, attribute)
                    .map(Gstr1FillIn::getValue)
                    .filter(v -> v != null && !v.isBlank());
            if (voucher.isPresent()) {
                return voucher;
            }
        }
        if (ledgerName != null) {
            Optional<String> ledger = fillInRepository
                    .findByWorkflowIdAndScopeAndKeyAndAttribute(workflow.getId(),
                            Gstr1FillInScope.LEDGER, ledgerName, attribute)
                    .map(Gstr1FillIn::getValue)
                    .filter(v -> v != null && !v.isBlank());
            if (ledger.isPresent()) {
                return ledger;
            }
        }
        return Optional.empty();
    }

    /**
     * Copies LEDGER-scope fill-ins from a previous workflow of the same type into a workflow
     * that has just entered PREPARE, as defaults. Existing fill-ins in the target are kept.
     * VOUCHER-scope fill-ins are never carried forward.
     */
    @Transactional
    public void carryForward(Workflow from, Workflow to) {
        if (to.getType() != from.getType()) {
            throw new WorkflowConflictException(
                    "Carry-forward requires workflows of the same type");
        }
        if (to.getStage() != WorkflowStage.PREPARE) {
            throw new WorkflowConflictException(
                    "Carry-forward target must be in PREPARE stage");
        }
        List<Gstr1FillIn> defaults = fillInRepository
                .findByWorkflowIdAndScope(from.getId(), Gstr1FillInScope.LEDGER);
        for (Gstr1FillIn source : defaults) {
            boolean exists = fillInRepository
                    .findByWorkflowIdAndScopeAndKeyAndAttribute(to.getId(), Gstr1FillInScope.LEDGER,
                            source.getKey(), source.getAttribute())
                    .isPresent();
            if (exists) {
                continue;
            }
            Gstr1FillIn copy = new Gstr1FillIn();
            copy.setWorkflowId(to.getId());
            copy.setScope(Gstr1FillInScope.LEDGER);
            copy.setKey(source.getKey());
            copy.setAttribute(source.getAttribute());
            copy.setValue(source.getValue());
            copy.setEnteredBy(CARRY_FORWARD_ACTOR);
            copy.setEnteredAt(Instant.now());
            fillInRepository.save(copy);
        }
    }

    private void requireEditable(Workflow workflow, String role) {
        if (workflow.getStage() != WorkflowStage.PREPARE) {
            throw new WorkflowConflictException(
                    "Fill-ins can only be edited while the workflow is in PREPARE stage; "
                            + "workflow is in " + workflow.getStage());
        }
        if (!ACCOUNTANT_ROLE.equals(role)) {
            throw new WorkflowConflictException("Only an Accountant can edit fill-ins");
        }
    }
}
