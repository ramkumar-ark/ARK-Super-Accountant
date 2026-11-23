package com.arktech.superaccountant.tdsapplicability;

import com.arktech.superaccountant.masters.models.Organization;
import com.arktech.superaccountant.masters.repository.OrganizationRepository;
import com.arktech.superaccountant.tdsapplicability.models.TdsApplicability;
import com.arktech.superaccountant.tdsapplicability.models.TdsApplicabilityBasis;
import com.arktech.superaccountant.tdsapplicability.models.TdsApplicabilityYear;
import com.arktech.superaccountant.tdsapplicability.repository.TdsApplicabilityYearRepository;
import com.arktech.superaccountant.workflow.repository.WorkflowRepository;
import com.arktech.superaccountant.workflow.service.CoverageService;
import com.arktech.superaccountant.workflow.service.compliance.ResponsibilityStartSettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TdsApplicabilityServiceTest {

    private final UUID orgId = UUID.randomUUID();

    private OrganizationRepository organizationRepository;
    private TdsApplicabilityYearRepository repository;
    private TdsApplicabilityService service;

    @BeforeEach
    void setUp() {
        organizationRepository = mock(OrganizationRepository.class);
        repository = mock(TdsApplicabilityYearRepository.class);
        CoverageService coverageService = mock(CoverageService.class);
        service = new TdsApplicabilityService(organizationRepository, repository, coverageService,
                mock(WorkflowRepository.class), mock(ResponsibilityStartSettingsService.class));
        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(new Organization("Applicability Org")));
        when(repository.findByOrganizationIdOrderByFinancialYearAsc(orgId)).thenReturn(List.of());
    }

    private TdsApplicabilityYear row(int financialYear, boolean applicable, TdsApplicabilityBasis basis) {
        TdsApplicabilityYear row = new TdsApplicabilityYear();
        row.setOrganizationId(orgId);
        row.setFinancialYear(financialYear);
        row.setApplicable(applicable);
        row.setBasis(basis);
        return row;
    }

    @Test
    void companyIsAlwaysApplicable() {
        Organization org = new Organization("Applicability Org");
        org.setPan("AAACA1234A");
        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(org));

        TdsApplicability result = service.resolve(orgId, 2026);

        assertTrue(result.applicable());
        assertEquals(TdsApplicabilityBasis.ENTITY_TYPE, result.basis());
        assertFalse(result.needsAnswer());
    }

    @Test
    void orgWithNoRowsIsLegacyApplicable() {
        Organization org = new Organization("Applicability Org");
        org.setPan("AAAPA1234A");
        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(org));

        TdsApplicability result = service.resolve(orgId, 2026);

        assertTrue(result.applicable());
        assertEquals(TdsApplicabilityBasis.LEGACY_DEFAULT, result.basis());
        assertFalse(result.needsAnswer());
    }

    @Test
    void recordedRowWins() {
        Organization org = new Organization("Applicability Org");
        org.setDeductorType("Q");
        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(org));
        when(repository.findByOrganizationIdAndFinancialYear(orgId, 2026))
                .thenReturn(Optional.of(row(2026, false, TdsApplicabilityBasis.DECLARED)));

        TdsApplicability result = service.resolve(orgId, 2026);

        assertFalse(result.applicable());
        assertEquals(TdsApplicabilityBasis.DECLARED, result.basis());
        assertFalse(result.needsAnswer());
    }

    @Test
    void qOrgWithLaterRowCarriesBackwardAndNeedsAnswer() {
        Organization org = new Organization("Applicability Org");
        org.setDeductorType("Q");
        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(org));
        when(repository.findByOrganizationIdAndFinancialYear(orgId, 2025)).thenReturn(Optional.empty());
        when(repository.findByOrganizationIdOrderByFinancialYearAsc(orgId))
                .thenReturn(List.of(row(2026, false, TdsApplicabilityBasis.DECLARED)));

        TdsApplicability result = service.resolve(orgId, 2025);

        assertFalse(result.applicable());
        assertEquals(TdsApplicabilityBasis.CARRIED_FORWARD, result.basis());
        assertTrue(result.needsAnswer());
    }

    @Test
    void earlierRowPreferredOnTie() {
        Organization org = new Organization("Applicability Org");
        org.setDeductorType("Q");
        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(org));
        when(repository.findByOrganizationIdAndFinancialYear(orgId, 2025)).thenReturn(Optional.empty());
        when(repository.findByOrganizationIdOrderByFinancialYearAsc(orgId))
                .thenReturn(List.of(row(2024, true, TdsApplicabilityBasis.DECLARED),
                        row(2026, false, TdsApplicabilityBasis.DECLARED)));

        TdsApplicability result = service.resolve(orgId, 2025);

        assertTrue(result.applicable());
        assertEquals(TdsApplicabilityBasis.CARRIED_FORWARD, result.basis());
        assertTrue(result.needsAnswer());
    }
}
