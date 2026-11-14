package com.arktech.superaccountant.workflow.analysis;

import com.arktech.superaccountant.workflow.models.WorkflowType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Tells the UI whether any analyzer is registered for a workflow type.
 * No production analyzer exists yet, so every type reports false.
 */
@Component
public class AnalyzerCatalog {

    @Autowired(required = false)
    private List<WorkflowAnalyzer> analyzers;

    public AnalyzerCatalog() {
    }

    AnalyzerCatalog(List<WorkflowAnalyzer> analyzers) {
        this.analyzers = analyzers;
    }

    public boolean hasAnalyzer(WorkflowType type) {
        List<WorkflowAnalyzer> registered = analyzers == null ? List.of() : analyzers;
        return registered.stream().anyMatch(a -> a.types().contains(type));
    }
}
