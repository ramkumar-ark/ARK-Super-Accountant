package com.arktech.superaccountant.workflow.analysis;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface WorkflowAnalysisRunRepository extends JpaRepository<WorkflowAnalysisRun, UUID> {
    List<WorkflowAnalysisRun> findByWorkflowIdOrderByStartedAtAsc(UUID workflowId);
}
