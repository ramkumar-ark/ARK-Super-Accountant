package com.arktech.superaccountant.tds.repository;

import com.arktech.superaccountant.tds.models.TdsSectionConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TdsSectionConfigRepository extends JpaRepository<TdsSectionConfig, UUID> {
    Optional<TdsSectionConfig> findBySectionCode(String code);

    boolean existsBySectionCode(String code);
}
