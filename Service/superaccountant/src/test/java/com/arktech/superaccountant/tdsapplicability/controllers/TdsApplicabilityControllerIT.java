package com.arktech.superaccountant.tdsapplicability.controllers;

import com.arktech.superaccountant.login.models.ERole;
import com.arktech.superaccountant.login.models.Role;
import com.arktech.superaccountant.login.models.User;
import com.arktech.superaccountant.login.repository.RoleRepository;
import com.arktech.superaccountant.login.repository.UserRepository;
import com.arktech.superaccountant.login.security.jwt.JwtUtils;
import com.arktech.superaccountant.masters.models.Organization;
import com.arktech.superaccountant.masters.models.UserOrganization;
import com.arktech.superaccountant.masters.repository.OrganizationRepository;
import com.arktech.superaccountant.masters.repository.UserOrganizationRepository;
import com.arktech.superaccountant.support.TestDatabase;
import com.arktech.superaccountant.workflow.service.period.PeriodCalendar;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = "JWT_SECRET=test-jwt-secret-must-be-at-least-32-characters-long")
class TdsApplicabilityControllerIT {

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        TestDatabase.register(registry);
    }

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private UserOrganizationRepository userOrganizationRepository;

    @Test
    void getReturnsYearsFromCoverageStart() throws Exception {
        Organization org = new Organization("TDS Applicability Org");
        org.setPan("AAAPA1234A");
        org.setBooksStartDate(LocalDate.of(2025, 4, 1));
        org = organizationRepository.save(org);
        String jwt = user("ta_accountant", ERole.ROLE_ACCOUNTANT, org.getId());
        User user = userRepository.findByUsername("ta_accountant").orElseThrow();
        userOrganizationRepository.save(new UserOrganization(user, org, ERole.ROLE_ACCOUNTANT));

        MvcResult result = mockMvc.perform(get("/api/v1/organizations/" + org.getId() + "/tds-applicability")
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        assertThat(root.get("entityCode").asText()).isEqualTo("Q");
        assertThat(root.get("entityCodeSource").asText()).isEqualTo("PAN");

        int currentFy = PeriodCalendar.financialYearOf(LocalDate.now(), 4);
        assertThat(currentFy).isGreaterThanOrEqualTo(2026);

        JsonNode years = root.get("years");
        List<Integer> financialYears = new ArrayList<>();
        years.forEach(y -> financialYears.add(y.get("financialYear").asInt()));
        assertThat(financialYears).contains(2025, currentFy);

        JsonNode fy2025 = years.get(0);
        assertThat(fy2025.get("financialYear").asInt()).isEqualTo(2025);
        assertThat(fy2025.get("applicable").asBoolean()).isTrue();
        assertThat(fy2025.get("basis").asText()).isEqualTo("LEGACY_DEFAULT");
        assertThat(fy2025.get("needsAnswer").asBoolean()).isFalse();
    }

    private String user(String username, ERole roleName, UUID organizationId) {
        Role role = roleRepository.findByName(roleName)
                .orElseGet(() -> roleRepository.save(new Role(roleName)));
        if (userRepository.findByUsername(username).isEmpty()) {
            User user = new User(username, username + "@test.com", "pass");
            user.setRole(role);
            userRepository.save(user);
        }
        return jwtUtils.generateJwtTokenForUser(username, organizationId);
    }
}
