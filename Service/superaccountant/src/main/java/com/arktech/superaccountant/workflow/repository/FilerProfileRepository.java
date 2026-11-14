package com.arktech.superaccountant.workflow.repository;

import com.arktech.superaccountant.workflow.models.FilerProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface FilerProfileRepository extends JpaRepository<FilerProfile, UUID> {

    Optional<FilerProfile> findFirstByOrganizationIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
            UUID organizationId, LocalDate effectiveFrom);

    Optional<FilerProfile> findFirstByOrganizationIdOrderByEffectiveFromDesc(UUID organizationId);
}
