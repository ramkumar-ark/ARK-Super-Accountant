package com.arktech.superaccountant.repository;

import com.arktech.superaccountant.login.models.ERole;
import com.arktech.superaccountant.login.models.Role;
import com.arktech.superaccountant.login.models.User;
import com.arktech.superaccountant.login.repository.RoleRepository;
import com.arktech.superaccountant.login.repository.UserRepository;
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

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
@Transactional
@TestPropertySource(properties = "JWT_SECRET=testcontainers-test-jwt-secret-min-32-chars")
class UserRepositoryIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Test
    void saveAndFindByUsername() {
        Role role = new Role(ERole.ROLE_OWNER);
        roleRepository.save(role);

        User user = new User("testuser", "test@example.com", "hashedpassword");
        user.setRole(role);
        userRepository.save(user);

        Optional<User> found = userRepository.findByUsername("testuser");
        assertTrue(found.isPresent());
        assertEquals("test@example.com", found.get().getEmail());
        assertEquals(ERole.ROLE_OWNER, found.get().getRole().getName());
    }

    @Test
    void existsByUsername_trueForExistingUser() {
        Role role = new Role(ERole.ROLE_OPERATOR);
        roleRepository.save(role);

        User user = new User("existinguser", "existing@example.com", "hashedpassword");
        user.setRole(role);
        userRepository.save(user);

        assertTrue(userRepository.existsByUsername("existinguser"));
        assertFalse(userRepository.existsByUsername("nonexistent"));
    }

    @Test
    void existsByEmail_trueForExistingEmail() {
        Role role = new Role(ERole.ROLE_OPERATOR);
        roleRepository.save(role);

        User user = new User("emailuser", "unique@example.com", "hashedpassword");
        user.setRole(role);
        userRepository.save(user);

        assertTrue(userRepository.existsByEmail("unique@example.com"));
        assertFalse(userRepository.existsByEmail("nothere@example.com"));
    }
}
