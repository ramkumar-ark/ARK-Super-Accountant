package com.arktech.superaccountant.tds.repository;

import com.arktech.superaccountant.tds.models.DebtorTan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DebtorTanRepository extends JpaRepository<DebtorTan, UUID> {
    Optional<DebtorTan> findByOrganizationIdAndLedgerKey(UUID organizationId, String ledgerKey);

    List<DebtorTan> findByOrganizationIdAndTallyGuid(UUID organizationId, String tallyGuid);

    List<DebtorTan> findByOrganizationIdOrderByLedgerNameAsc(UUID organizationId);
}
