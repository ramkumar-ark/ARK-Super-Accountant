package com.arktech.superaccountant.workflow.seam;

import com.arktech.superaccountant.workflow.findings.BlockingFindingSource;
import com.arktech.superaccountant.workflow.repository.WorkflowRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Sums the blocking findings of every {@link BlockingFindingSource} that applies to the workflow.
 * A missing workflow blocks nothing (0), and the C2 severity rule is each source's own business.
 */
@Service
public class WorkflowGate {

    private final WorkflowRepository workflowRepository;
    private final List<BlockingFindingSource> sources;

    public WorkflowGate(WorkflowRepository workflowRepository, List<BlockingFindingSource> sources) {
        this.workflowRepository = workflowRepository;
        this.sources = sources == null ? List.of() : sources;
    }

    public int blockingFindingCount(UUID workflowId) {
        return workflowRepository.findById(workflowId)
                .map(w -> (int) sources.stream().filter(s -> s.appliesTo(w))
                        .mapToLong(s -> s.blockingCount(w)).sum())
                .orElse(0);
    }
}
