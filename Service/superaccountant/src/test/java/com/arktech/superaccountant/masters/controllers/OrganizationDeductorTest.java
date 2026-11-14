package com.arktech.superaccountant.masters.controllers;

import com.arktech.superaccountant.login.security.services.UserDetailsImpl;
import com.arktech.superaccountant.masters.models.Organization;
import com.arktech.superaccountant.masters.payload.request.UpdateDeductorDetailsRequest;
import com.arktech.superaccountant.masters.repository.OrganizationRepository;
import com.arktech.superaccountant.masters.repository.UserOrganizationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrganizationDeductorTest {

    @Test
    void tanPatternAcceptsValidTan() {
        assertTrue("CHEA12345B".matches(Organization.TAN_PATTERN));
    }

    @Test
    void tanPatternRejectsInvalidTan() {
        assertFalse("CHEA1234B".matches(Organization.TAN_PATTERN));  // 4 digits
        assertFalse("chea12345b".matches(Organization.TAN_PATTERN)); // lowercase
        assertFalse("CHE12345AB".matches(Organization.TAN_PATTERN)); // wrong shape
    }

    @Test
    void organizationCarriesDeductorFields() {
        Organization org = new Organization("Acme");
        org.setTan("CHEA12345B");
        org.setDeductorType("K");
        org.setResponsiblePersonName("Ram Kumar");
        org.setResponsiblePersonDesignation("Director");
        assertEquals("CHEA12345B", org.getTan());
        assertEquals("K", org.getDeductorType());
    }
}
