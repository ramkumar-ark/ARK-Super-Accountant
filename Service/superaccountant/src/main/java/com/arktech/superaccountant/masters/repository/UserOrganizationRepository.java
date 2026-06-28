package com.arktech.superaccountant.masters.repository;

import com.arktech.superaccountant.masters.models.UserOrganization;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserOrganizationRepository extends JpaRepository<UserOrganization, Long> {
    List<UserOrganization> findByUserId(Long userId);
    Optional<UserOrganization> findByUserIdAndOrganizationId(Long userId, UUID organizationId);
    @Query("SELECT uo FROM UserOrganization uo JOIN FETCH uo.user WHERE uo.organization.id = :organizationId ORDER BY uo.user.username ASC")
    List<UserOrganization> findByOrganizationId(@Param("organizationId") UUID organizationId);
}
