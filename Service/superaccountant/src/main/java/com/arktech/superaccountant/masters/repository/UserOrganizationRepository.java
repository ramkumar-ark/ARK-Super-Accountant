package com.arktech.superaccountant.masters.repository;

import com.arktech.superaccountant.masters.models.UserOrganization;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserOrganizationRepository extends JpaRepository<UserOrganization, Long> {
    List<UserOrganization> findByUserId(Long userId);
    Optional<UserOrganization> findByUserIdAndOrganizationId(Long userId, UUID organizationId);
}
