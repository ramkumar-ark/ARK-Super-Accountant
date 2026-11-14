package com.arktech.superaccountant.workflow.service;

import com.arktech.superaccountant.masters.repository.OrganizationRepository;
import com.arktech.superaccountant.workflow.repository.FinancialYearCloseRepository;
import com.arktech.superaccountant.workflow.service.period.PeriodCalendar;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Service
public class CoverageService {

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private FinancialYearCloseRepository financialYearCloseRepository;

    /**
     * Start of the earliest financial year that has no close row, but never
     * before the organization's books start date. Assumes contiguous closes
     * from the books-start FY onward.
     */
    public LocalDate coverageStart(UUID organizationId) {
        var org = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new IllegalArgumentException("Organization not found"));
        int fyStartMonth = org.getFinancialYearStart();
        LocalDate base = org.getBooksStartDate();
        int fy = base != null
                ? PeriodCalendar.financialYearOf(base, fyStartMonth)
                : PeriodCalendar.financialYearOf(LocalDate.now(), fyStartMonth);

        Set<Integer> closed = new HashSet<>();
        financialYearCloseRepository.findAllByOrganizationIdOrderByFinancialYearAsc(organizationId)
                .forEach(c -> closed.add(c.getFinancialYear()));
        while (closed.contains(fy)) {
            fy++;
        }
        return PeriodCalendar.fyStart(fy, fyStartMonth);
    }
}
