package com.arktech.superaccountant.masters.repository;

import com.arktech.superaccountant.masters.models.OrganizationSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrganizationSettingRepository extends JpaRepository<OrganizationSetting, UUID> {
    List<OrganizationSetting> findByOrganizationId(UUID organizationId);

    Optional<OrganizationSetting> findByOrganizationIdAndSettingKey(UUID organizationId, String settingKey);
}
