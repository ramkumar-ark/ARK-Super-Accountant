package com.arktech.superaccountant.masters.repository;

import com.arktech.superaccountant.masters.models.LedgerCategory;
import com.arktech.superaccountant.masters.models.PreconfiguredMaster;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PreconfiguredMasterRepository extends JpaRepository<PreconfiguredMaster, UUID> {
    List<PreconfiguredMaster> findByOrganizationIdAndActiveTrue(UUID organizationId);
    Page<PreconfiguredMaster> findByOrganizationIdAndActiveTrue(UUID organizationId, Pageable pageable);
    Page<PreconfiguredMaster> findByOrganizationIdAndActiveTrueAndCategory(UUID organizationId, LedgerCategory category, Pageable pageable);
    boolean existsByOrganizationId(UUID organizationId);
    List<PreconfiguredMaster> findByTemplateTrue();
}
