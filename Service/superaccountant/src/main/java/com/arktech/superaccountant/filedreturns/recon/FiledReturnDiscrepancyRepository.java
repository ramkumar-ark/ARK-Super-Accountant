package com.arktech.superaccountant.filedreturns.recon;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Set;

public interface FiledReturnDiscrepancyRepository extends JpaRepository<FiledReturnDiscrepancy, UUID> {

    List<FiledReturnDiscrepancy> findByWorkflowIdAndSupersededAtIsNull(UUID workflowId);

    List<FiledReturnDiscrepancy> findByOrganizationIdAndSupersededAtIsNull(UUID organizationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from FiledReturnDiscrepancy f where f.id = :id")
    Optional<FiledReturnDiscrepancy> findByIdAndWorkflowIdForUpdate(@Param("id") UUID id);

    long countByWorkflowIdInAndSupersededAtIsNullAndMaterialTrueAndResolveStatusNotIn(
            Collection<UUID> workflowIds, Set<com.arktech.superaccountant.masters.models.ResolveStatus> statuses);
}
