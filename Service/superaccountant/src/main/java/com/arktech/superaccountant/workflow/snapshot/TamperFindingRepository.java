package com.arktech.superaccountant.workflow.snapshot;

import com.arktech.superaccountant.masters.models.ResolveStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TamperFindingRepository extends JpaRepository<TamperFinding, UUID> {
    List<TamperFinding> findByOrganizationId(UUID organizationId);

    List<TamperFinding> findByOrganizationIdAndResolveStatusNotIn(UUID organizationId,
                                                                  Collection<ResolveStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<TamperFinding> findLockedById(UUID id);
}
