package com.arktech.superaccountant.workflow.seam;

import com.arktech.superaccountant.masters.models.Organization;
import com.arktech.superaccountant.masters.models.UploadJob;
import com.arktech.superaccountant.masters.models.UploadJobStatus;
import com.arktech.superaccountant.masters.repository.OrganizationRepository;
import com.arktech.superaccountant.masters.repository.UploadJobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The real masters job gate: the newest COMPLETED or COMPLETED_WITH_MISMATCHES upload job of
 * the organization. FAILED and DISCARDED jobs never satisfy the freshness check. Needs Docker.
 */
@SpringBootTest
@Testcontainers
@TestPropertySource(properties = "JWT_SECRET=test-jwt-secret-must-be-at-least-32-characters-long")
class MastersJobGateIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    MastersJobGate gate;
    @Autowired
    UploadJobRepository uploadJobRepository;
    @Autowired
    OrganizationRepository organizationRepository;

    UUID orgId;

    @BeforeEach
    void seed() {
        orgId = organizationRepository.save(new Organization("Gate IT " + UUID.randomUUID())).getId();
    }

    private UploadJob job(UploadJobStatus status, Instant createdAt) {
        UploadJob job = new UploadJob();
        job.setOrganizationId(orgId);
        job.setFileName("masters-" + UUID.randomUUID() + ".json");
        job.setStatus(status);
        job.setCreatedAt(createdAt);
        return uploadJobRepository.save(job);
    }

    @Test
    void latest_returnsNewestCompletedOrWithMismatches_ignoresFailedAndDiscarded() {
        job(UploadJobStatus.COMPLETED, Instant.now().minus(3, java.time.temporal.ChronoUnit.HOURS));
        job(UploadJobStatus.FAILED, Instant.now().minus(2, java.time.temporal.ChronoUnit.HOURS));
        job(UploadJobStatus.DISCARDED, Instant.now().minus(1, java.time.temporal.ChronoUnit.HOURS));
        UploadJob newest = job(UploadJobStatus.COMPLETED_WITH_MISMATCHES, Instant.now());

        assertThat(gate.latestCompletedMastersJob(orgId)).contains(newest.getId());
    }

    @Test
    void latest_noJobs_empty() {
        assertThat(gate.latestCompletedMastersJob(orgId)).isEmpty();
    }
}
