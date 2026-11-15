package com.arktech.superaccountant.tds.repository;

import com.arktech.superaccountant.tds.models.TdsPanOverride;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TdsPanOverrideRepository extends JpaRepository<TdsPanOverride, UUID> {
    Optional<TdsPanOverride> findByOrganizationIdAndPartyKey(UUID organizationId, String partyKey);

    List<TdsPanOverride> findByOrganizationIdOrderByPartyLedgerNameAsc(UUID organizationId);
}
