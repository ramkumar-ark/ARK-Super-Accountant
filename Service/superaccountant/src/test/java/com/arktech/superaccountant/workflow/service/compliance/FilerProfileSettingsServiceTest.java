package com.arktech.superaccountant.workflow.service.compliance;

import com.arktech.superaccountant.masters.models.Organization;
import com.arktech.superaccountant.masters.repository.OrganizationRepository;
import com.arktech.superaccountant.workflow.payload.request.FilerProfileRequest;
import com.arktech.superaccountant.workflow.payload.response.FilerProfileResponse;
import com.arktech.superaccountant.workflow.service.WorkflowBadRequestException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@Testcontainers
@TestPropertySource(properties = "JWT_SECRET=test-jwt-secret-must-be-at-least-32-characters-long")
class FilerProfileSettingsServiceTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private FilerProfileSettingsService filerProfileSettingsService;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID createOrg() {
        Organization org = new Organization();
        org.setName("Test Org");
        org.setFinancialYearStart(4);
        return organizationRepository.save(org).getId();
    }

    private FilerProfileRequest request(UUID orgId, LocalDate effectiveFrom, Integer hsnDigits) {
        FilerProfileRequest request = new FilerProfileRequest();
        request.setFrequency("MONTHLY");
        request.setEffectiveFrom(effectiveFrom);
        request.setHsnDigits(hsnDigits);
        return request;
    }

    @Test
    void put_hsnDigitsSix_roundTrips() {
        UUID orgId = createOrg();
        FilerProfileResponse saved = filerProfileSettingsService.put(
                orgId, "tester", request(orgId, LocalDate.of(2025, 4, 1), 6));

        assertEquals(6, saved.hsnDigits());
        assertEquals(6, filerProfileSettingsService.list(orgId).get(0).hsnDigits());
    }

    @Test
    void put_hsnDigitsSix_explicitDefaultFour_roundTrips() {
        UUID orgId = createOrg();
        FilerProfileResponse saved = filerProfileSettingsService.put(
                orgId, "tester", request(orgId, LocalDate.of(2025, 7, 1), 4));

        assertEquals(4, saved.hsnDigits());
    }

    @Test
    void put_hsnDigitsFive_isRejected() {
        UUID orgId = createOrg();
        assertThrows(WorkflowBadRequestException.class, () -> filerProfileSettingsService.put(
                orgId, "tester", request(orgId, LocalDate.of(2025, 4, 1), 5)));
    }

    @Test
    void oldRow_withoutHsnDigits_defaultsToFour() {
        UUID orgId = createOrg();
        jdbcTemplate.update(
                "insert into filer_profiles (id, organization_id, frequency, iff_opt_in, "
                        + "einvoice_enabled, effective_from, created_by, created_at) "
                        + "values (?, ?, 'MONTHLY', false, false, ?, 'seed', ?)",
                UUID.randomUUID(), orgId, LocalDate.of(2024, 4, 1), LocalDateTime.now());

        assertEquals(4, filerProfileSettingsService.list(orgId).get(0).hsnDigits());
    }
}
