package com.arktech.superaccountant.tdsapplicability.repository;

import com.arktech.superaccountant.tdsapplicability.models.PromptKind;
import com.arktech.superaccountant.tdsapplicability.models.PromptStatus;
import com.arktech.superaccountant.tdsapplicability.models.TdsApplicabilityPrompt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TdsApplicabilityPromptRepository extends JpaRepository<TdsApplicabilityPrompt, UUID> {

    List<TdsApplicabilityPrompt> findByOrganizationIdAndStatusOrderByCreatedAtAsc(
            UUID organizationId, PromptStatus status);

    List<TdsApplicabilityPrompt> findByOrganizationIdAndKindAndFinancialYear(
            UUID organizationId, PromptKind kind, int financialYear);

    boolean existsByOrganizationIdAndKindAndFinancialYearAndStatusIn(
            UUID organizationId, PromptKind kind, int financialYear, List<PromptStatus> statuses);

    boolean existsByOrganizationIdAndKind(UUID organizationId, PromptKind kind);
}
