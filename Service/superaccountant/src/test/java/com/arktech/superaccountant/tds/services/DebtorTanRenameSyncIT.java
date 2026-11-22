package com.arktech.superaccountant.tds.services;

import com.arktech.superaccountant.masters.classifier.ParsedLedger;
import com.arktech.superaccountant.masters.models.Organization;
import com.arktech.superaccountant.masters.repository.OrganizationRepository;
import com.arktech.superaccountant.tds.models.DebtorTan;
import com.arktech.superaccountant.tds.repository.DebtorTanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@TestPropertySource(properties = "JWT_SECRET=test-jwt-secret-must-be-at-least-32-characters-long")
class DebtorTanRenameSyncIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @org.springframework.test.context.DynamicPropertySource
    static void configureProperties(org.springframework.test.context.DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired DebtorTanStore store;
    @Autowired DebtorTanRepository repository;
    @Autowired OrganizationRepository organizationRepository;

    UUID orgId;

    @BeforeEach
    void seed() {
        Organization org = new Organization();
        org.setName("Debtor TAN Rename Sync IT " + UUID.randomUUID());
        org.setBooksStartDate(LocalDate.of(2025, 4, 1));
        orgId = organizationRepository.save(org).getId();
    }

    @Test
    void followsLedgerRenameByGuidAndKeepsTan() {
        store.saveTan(orgId, "Acme Corp", "BLRA12345C", "guid-1", null, "acct", Instant.now());

        store.syncLedgerRenames(orgId, List.of(ledger("Acme Corporation", "guid-1", "Sundry Debtors|Current Assets")));

        List<DebtorTan> records = repository.findByOrganizationIdOrderByLedgerNameAsc(orgId);
        assertThat(records).hasSize(1);
        DebtorTan record = records.get(0);
        assertThat(record.getLedgerName()).isEqualTo("Acme Corporation");
        assertThat(record.getLedgerKey()).isEqualTo("acme corporation");
        assertThat(record.getTallyGuid()).isEqualTo("guid-1");
        assertThat(record.getTan()).isEqualTo("BLRA12345C");
        assertThat(repository.findByOrganizationIdAndLedgerKey(orgId, "acme corp")).isEmpty();
    }

    @Test
    void bindsGuidToLegacyRecordMatchedByLedgerKey() {
        store.saveTan(orgId, "Acme Corp", "BLRA12345C", null, null, "acct", Instant.now());

        store.syncLedgerRenames(orgId, List.of(ledger("Acme Corp", "guid-2", "Sundry Debtors")));

        Optional<DebtorTan> record = repository.findByOrganizationIdAndLedgerKey(orgId, "acme corp");
        assertThat(record).isPresent();
        assertThat(record.get().getTallyGuid()).isEqualTo("guid-2");
        assertThat(record.get().getTan()).isEqualTo("BLRA12345C");
    }

    @Test
    void ignoresLedgersOutsideSundryDebtors() {
        store.saveTan(orgId, "Acme Corp", "BLRA12345C", "guid-1", null, "acct", Instant.now());

        store.syncLedgerRenames(orgId, List.of(ledger("Acme Corporation", "guid-1", "Sundry Creditors")));

        Optional<DebtorTan> record = repository.findByOrganizationIdAndLedgerKey(orgId, "acme corp");
        assertThat(record).isPresent();
        assertThat(record.get().getLedgerName()).isEqualTo("Acme Corp");
    }

    private static ParsedLedger ledger(String name, String guid, String groupChain) {
        return ParsedLedger.builder().name(name).guid(guid).groupChain(groupChain).build();
    }
}
