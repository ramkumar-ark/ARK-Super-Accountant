package com.arktech.superaccountant.repository;

import com.arktech.superaccountant.masters.models.Organization;
import com.arktech.superaccountant.masters.repository.OrganizationRepository;
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

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
@Transactional
@TestPropertySource(properties = "JWT_SECRET=testcontainers-test-jwt-secret-min-32-chars")
class OrganizationRepositoryIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private OrganizationRepository organizationRepository;

    @Test
    void saveAndFindById() {
        Organization org = new Organization("Acme Construction Pvt Ltd");
        Organization saved = organizationRepository.save(org);

        assertNotNull(saved.getId());

        Optional<Organization> found = organizationRepository.findById(saved.getId());
        assertTrue(found.isPresent());
        assertEquals("Acme Construction Pvt Ltd", found.get().getName());
        assertNotNull(found.get().getCreatedAt());
    }

    @Test
    void deleteOrganization_removedFromDatabase() {
        Organization org = new Organization("Temporary Org");
        Organization saved = organizationRepository.save(org);
        UUID savedId = saved.getId();

        organizationRepository.deleteById(savedId);
        organizationRepository.flush();

        assertFalse(organizationRepository.findById(savedId).isPresent());
    }
}
