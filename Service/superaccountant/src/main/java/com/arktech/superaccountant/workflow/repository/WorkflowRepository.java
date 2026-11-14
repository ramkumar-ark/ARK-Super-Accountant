package com.arktech.superaccountant.workflow.repository;

import com.arktech.superaccountant.workflow.models.Workflow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import com.arktech.superaccountant.workflow.models.WorkflowStage;
import com.arktech.superaccountant.workflow.models.WorkflowType;

import java.util.UUID;

public interface WorkflowRepository extends JpaRepository<Workflow, UUID> {

    Optional<Workflow> findByOrganizationIdAndTypeAndPeriodKeyAndActiveTrue(
            UUID organizationId, WorkflowType type, String periodKey);

    List<Workflow> findAllByOrganizationIdOrderByCreatedAtDesc(UUID organizationId);

    List<Workflow> findAllByOrganizationIdAndPeriodKey(UUID organizationId, String periodKey);

    Optional<Workflow> findFirstByOrganizationIdAndTypeAndStageOrderByFinalizedAtDesc(
            UUID organizationId, WorkflowType type, WorkflowStage stage);

    @Query("""
            select count(w) > 0 from Workflow w
            where w.organizationId = :orgId and w.type = :type
              and w.periodKey = :periodKey and w.active = true
              and w.stage in :stages
            """)
    boolean existsFinalizedInStages(@Param("orgId") UUID orgId,
                                    @Param("type") WorkflowType type,
                                    @Param("periodKey") String periodKey,
                                    @Param("stages") java.util.Collection<WorkflowStage> stages);

    default boolean existsFinalized(UUID orgId, WorkflowType type, String periodKey) {
        return existsFinalizedInStages(orgId, type, periodKey,
                java.util.List.of(WorkflowStage.BASELINE, WorkflowStage.FINALIZED, WorkflowStage.FILED));
    }

    List<Workflow> findAllByOrganizationIdAndActiveTrue(UUID organizationId);
}
