package com.arktech.superaccountant.workflow.snapshot;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AmendmentFollowUpRepository extends JpaRepository<AmendmentFollowUp, UUID> {

    List<AmendmentFollowUp> findByWorkflowIdOrderByCreatedAt(UUID workflowId);
}
