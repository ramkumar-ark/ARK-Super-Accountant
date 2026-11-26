package com.arktech.superaccountant.statutory.repository;

import com.arktech.superaccountant.statutory.models.StatutorySectionRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface StatutorySectionRuleRepository extends JpaRepository<StatutorySectionRule, UUID> {

    List<StatutorySectionRule> findBySectionId(UUID sectionId);
}
