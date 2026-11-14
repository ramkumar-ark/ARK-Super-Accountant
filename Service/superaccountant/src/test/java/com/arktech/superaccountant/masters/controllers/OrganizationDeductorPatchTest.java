package com.arktech.superaccountant.masters.controllers;

import com.arktech.superaccountant.login.security.services.UserDetailsImpl;
import com.arktech.superaccountant.masters.models.Organization;
import com.arktech.superaccountant.masters.models.UserOrganization;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrganizationDeductorPatchTest {

    @Mock
    OrganizationRepository organizationRepository;
    @Mock
    UserOrganizationRepository userOrganizationRepository;

    @InjectMocks
    OrganizationController controller;

    private final UUID orgId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(userOrganizationRepository.findByUserIdAndOrganizationId(1L, orgId))
                .thenReturn(Optional.of(new com.arktech.superaccountant.masters.models.UserOrganization()));
    }

    private UserDetailsImpl user(String name, String role) {
        return new UserDetailsImpl(1L, name, name + "@t.com", "x", orgId,
                List.of(new SimpleGrantedAuthority(role)));
    }

    private ResponseEntity<?> patch(UserDetailsImpl u, UpdateDeductorDetailsRequest req) {
        return controller.updateDeductorDetails(orgId, req, u);
    }

    @Test
    void patchDeductor_normalisesTan() {
        Organization org = new Organization("Acme");
        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(org));

        UpdateDeductorDetailsRequest req = new UpdateDeductorDetailsRequest();
        req.setTan(" abcd12345e ");

        ResponseEntity<?> res = patch(user("acct", "ROLE_ACCOUNTANT"), req);

        assertThat(res.getStatusCode().value()).isEqualTo(200);
        ArgumentCaptor<Organization> captor = ArgumentCaptor.forClass(Organization.class);
        verify(organizationRepository).save(captor.capture());
        assertThat(captor.getValue().getTan()).isEqualTo("ABCD12345E");
    }

    @Test
    void patchDeductor_malformedTan_400() {
        Organization org = new Organization("Acme");
        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(org));

        UpdateDeductorDetailsRequest req = new UpdateDeductorDetailsRequest();
        req.setTan("ABC123");

        ResponseEntity<?> res = patch(user("acct", "ROLE_ACCOUNTANT"), req);

        assertThat(res.getStatusCode().value()).isEqualTo(400);
        assertThat(((Map<?, ?>) res.getBody()).get("error")).isNotNull();
        verify(organizationRepository, never()).save(any());
    }
}
