package com.arktech.superaccountant.tdsapplicability.repository;

import com.arktech.superaccountant.tdsapplicability.models.TdsApplicabilityYear;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TdsApplicabilityYearRepository extends JpaRepository<TdsApplicabilityYear, UUID> {

    List<TdsApplicabilityYear> findByOrganizationIdOrderByFinancialYearAsc(UUID organizationId);

    Optional<TdsApplicabilityYear> findByOrganizationIdAndFinancialYear(UUID organizationId, int financialYear);
}
