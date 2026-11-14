package com.arktech.superaccountant.workflow.repository;

import com.arktech.superaccountant.workflow.models.WorkflowEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface WorkflowEventRepository extends JpaRepository<WorkflowEvent, UUID> {
    List<WorkflowEvent> findAllByWorkflowIdOrderByOccurredAtAsc(UUID workflowId);
}
