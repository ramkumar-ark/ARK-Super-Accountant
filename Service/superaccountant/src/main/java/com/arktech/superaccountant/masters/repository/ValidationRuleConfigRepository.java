package com.arktech.superaccountant.masters.repository;

import com.arktech.superaccountant.masters.models.ValidationRuleConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ValidationRuleConfigRepository extends JpaRepository<ValidationRuleConfig, UUID> {
    List<ValidationRuleConfig> findByActiveTrueOrderByExecutionOrderAsc();
}
