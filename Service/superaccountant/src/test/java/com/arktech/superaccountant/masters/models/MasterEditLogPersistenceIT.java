package com.arktech.superaccountant.masters.models;

import com.arktech.superaccountant.masters.repository.MasterEditLogRepository;
import com.arktech.superaccountant.masters.repository.ValidationFindingRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Schema created by ddl-auto=update: new finding columns and the master_edit_log table. Needs Docker. */
@SpringBootTest
@Testcontainers
@Transactional
@TestPropertySource(properties = "JWT_SECRET=test-jwt-secret-must-be-at-least-32-characters-long")
class MasterEditLogPersistenceIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    ValidationFindingRepository findingRepository;
    @Autowired
    MasterEditLogRepository editLogRepository;

    @Test
    void finding_roundTripsReviewFields_andNullsLoadFine() {
        ValidationFinding plain = new ValidationFinding();
        plain.setUploadJobId(UUID.randomUUID());
        plain = findingRepository.saveAndFlush(plain);

        ValidationFinding f = new ValidationFinding();
        f.setUploadJobId(UUID.randomUUID());
        f.setProposedValue("29ABCDE1234F1Z5");
        f.setSubmittedBy("op");
        f.setSubmittedAt(Instant.now().truncatedTo(ChronoUnit.MILLIS));
        f.setReviewedBy("acct");
        f.setReviewedAt(Instant.now().truncatedTo(ChronoUnit.MILLIS));
        f.setReviewNote("not valid");
        f = findingRepository.saveAndFlush(f);

        ValidationFinding loaded = findingRepository.findById(f.getId()).orElseThrow();
        assertThat(loaded.getProposedValue()).isEqualTo("29ABCDE1234F1Z5");
        assertThat(loaded.getSubmittedBy()).isEqualTo("op");
        assertThat(loaded.getReviewedBy()).isEqualTo("acct");
        assertThat(loaded.getReviewNote()).isEqualTo("not valid");
        assertThat(loaded.getSubmittedAt()).isNotNull();

        ValidationFinding loadedPlain = findingRepository.findById(plain.getId()).orElseThrow();
        assertThat(loadedPlain.getProposedValue()).isNull();
        assertThat(loadedPlain.getReviewedBy()).isNull();
    }

    @Test
    void masterEditLog_persistsAndIsFoundByFindingIdAndMasterId() {
        UUID findingId = UUID.randomUUID();
        UUID masterId = UUID.randomUUID();
        MasterEditLog log = new MasterEditLog();
        log.setMasterId(masterId);
        log.setLedgerName("Acme");
        log.setFieldName("gstin");
        log.setOldValue(null);
        log.setNewValue("29ABCDE1234F1Z5");
        log.setEditedBy("acct");
        log.setSubmittedBy("op");
        log.setSource(MasterEditLog.SOURCE_FINDING_OVERRIDE);
        log.setFindingId(findingId);
        editLogRepository.saveAndFlush(log);

        var byFinding = editLogRepository.findByFindingId(findingId);
        assertThat(byFinding).hasSize(1);
        assertThat(byFinding.get(0).getEditedBy()).isEqualTo("acct");
        assertThat(byFinding.get(0).getSubmittedBy()).isEqualTo("op");
        assertThat(byFinding.get(0).getEditedAt()).isNotNull();
        assertThat(editLogRepository.findByMasterId(masterId)).hasSize(1);
    }
}
