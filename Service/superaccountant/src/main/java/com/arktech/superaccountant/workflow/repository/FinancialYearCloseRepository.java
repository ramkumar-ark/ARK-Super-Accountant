package com.arktech.superaccountant.workflow.repository;

import com.arktech.superaccountant.workflow.models.FinancialYearClose;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FinancialYearCloseRepository extends JpaRepository<FinancialYearClose, UUID> {
    Optional<FinancialYearClose> findByOrganizationIdAndFinancialYear(UUID organizationId, int financialYear);

    List<FinancialYearClose> findAllByOrganizationIdOrderByFinancialYearAsc(UUID organizationId);
}
