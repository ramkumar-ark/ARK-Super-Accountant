package com.arktech.superaccountant.masters.repository;

import com.arktech.superaccountant.masters.models.OrganizationInvite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OrganizationInviteRepository extends JpaRepository<OrganizationInvite, UUID> {
    Optional<OrganizationInvite> findByToken(String token);
}
