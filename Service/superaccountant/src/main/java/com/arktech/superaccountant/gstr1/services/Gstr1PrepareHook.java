package com.arktech.superaccountant.gstr1.services;

import com.arktech.superaccountant.workflow.models.Workflow;
import com.arktech.superaccountant.workflow.models.WorkflowType;
import com.arktech.superaccountant.workflow.seam.PrepareHook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * GSTR-1 implementation of the {@link PrepareHook} seam: builds the prepared return
 * when a workflow enters PREPARE (carrying forward LEDGER-scope fill-ins from the
 * superseded workflow first), freezes it at Finalize and releases the freeze on
 * un-finalize.
 */
@Service
public class Gstr1PrepareHook implements PrepareHook {

    @Autowired
    private Gstr1PrepareService prepareService;

    @Autowired
    private Gstr1FillInService fillInService;

    @Autowired
    private com.arktech.superaccountant.workflow.repository.WorkflowRepository workflowRepository;

    @Override
    public boolean appliesTo(Workflow w) {
        return w != null && w.getType() == WorkflowType.GSTR1;
    }

    @Override
    @Transactional
    public void onEnterPrepare(Workflow w) {
        if (w.getSupersedesWorkflowId() != null) {
            workflowRepository.findById(w.getSupersedesWorkflowId())
                    .filter(previous -> previous.getType() == w.getType())
                    .ifPresent(previous -> fillInService.carryForward(previous, w));
        }
        prepareService.buildOrRebuild(w);
    }

    @Override
    public void onFinalize(Workflow w) {
        prepareService.freeze(w);
    }

    @Override
    public void onUnfinalize(Workflow w) {
        prepareService.release(w);
    }
}
