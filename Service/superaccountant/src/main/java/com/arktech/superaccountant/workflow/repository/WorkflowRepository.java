package com.arktech.superaccountant.workflow.repository;

import com.arktech.superaccountant.workflow.models.Workflow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkflowRepository extends JpaRepository<Workflow, UUID> {

    Optional<Workflow> findByOrganizationIdAndTypeAndPeriodKeyAndActiveTrue(
            UUID organizationId, String type, String periodKey);

    List<Workflow> findAllByOrganizationIdOrderByCreatedAtDesc(UUID organizationId);

    List<Workflow> findAllByOrganizationIdAndPeriodKey(UUID organizationId, String periodKey);

    Optional<Workflow> findFirstByOrganizationIdAndTypeAndStageOrderByFinalizedAtDesc(
            UUID organizationId, String type, String stage);

    @Query("""
            select count(w) > 0 from Workflow w
            where w.organizationId = :orgId and w.type = :type
              and w.periodKey = :periodKey and w.active = true
              and w.stage in ('BASELINE', 'FINALIZED', 'FILED')
            """)
    boolean existsFinalized(@Param("orgId") UUID orgId,
                            @Param("type") String type,
                            @Param("periodKey") String periodKey);

    List<Workflow> findAllByOrganizationIdAndActiveTrue(UUID organizationId);
}
