package com.arktech.superaccountant.login.config;

import com.arktech.superaccountant.login.models.ERole;
import com.arktech.superaccountant.login.repository.RoleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
@TestPropertySource(properties = "JWT_SECRET=test-jwt-secret-must-be-at-least-32-characters-long")
class DataInitializerIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void afterBoot_allFourTargetRolesExist() {
        assertTrue(roleRepository.findByName(ERole.ROLE_OWNER).isPresent(),
                "ROLE_OWNER must be seeded on boot");
        assertTrue(roleRepository.findByName(ERole.ROLE_ACCOUNTANT).isPresent(),
                "ROLE_ACCOUNTANT must be seeded on boot");
        assertTrue(roleRepository.findByName(ERole.ROLE_OPERATOR).isPresent(),
                "ROLE_OPERATOR must be seeded on boot");
        assertTrue(roleRepository.findByName(ERole.ROLE_AUDITOR_CA).isPresent(),
                "ROLE_AUDITOR_CA must be seeded on boot");
    }

    @Test
    void afterBoot_exactlyFourRolesExist() {
        assertEquals(4, roleRepository.count(),
                "roles table must contain exactly 4 rows after migration");
    }

    @Test
    void seedRoles_isIdempotent_noDuplicatesOnSecondRun() throws Exception {
        long countBefore = roleRepository.count();

        // Run DataInitializer a second time via the application context bean
        DataInitializer dataInitializer = applicationContext.getBean(DataInitializer.class);
        dataInitializer.run();

        assertEquals(countBefore, roleRepository.count(),
                "Running DataInitializer a second time must not insert duplicate roles");
    }
}
