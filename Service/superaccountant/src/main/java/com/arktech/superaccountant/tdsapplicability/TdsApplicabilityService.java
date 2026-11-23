package com.arktech.superaccountant.tdsapplicability;

import com.arktech.superaccountant.masters.models.Organization;
import com.arktech.superaccountant.masters.repository.OrganizationRepository;
import com.arktech.superaccountant.tdsapplicability.models.TdsApplicability;
import com.arktech.superaccountant.tdsapplicability.models.TdsApplicabilityBasis;
import com.arktech.superaccountant.tdsapplicability.models.TdsApplicabilityYear;
import com.arktech.superaccountant.tdsapplicability.repository.TdsApplicabilityYearRepository;
import com.arktech.superaccountant.workflow.service.CoverageService;
import com.arktech.superaccountant.workflow.service.period.PeriodCalendar;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Resolves whether an organization must deduct TDS for a financial year
 * (ADR 0008). The resolver order, pinned by tests:
 *
 * <ol>
 *   <li>the effective deductor code is known and is not Q (Individual/HUF) →
 *       applicable, basis {@code ENTITY_TYPE};</li>
 *   <li>a recorded row for the year → the row's values;</li>
 *   <li>the organization has no rows at all → applicable, {@code LEGACY_DEFAULT}
 *       (existing organizations keep today's behaviour);</li>
 *   <li>otherwise the nearest recorded year by financial-year distance (earlier
 *       preferred on a tie) carries its answer forward, with {@code needsAnswer}
 *       set until the user answers for this year.</li>
 * </ol>
 */
@Service
public class TdsApplicabilityService {

    private static final String INDIVIDUAL_HUF_CODE = "Q";

    private final OrganizationRepository organizationRepository;
    private final TdsApplicabilityYearRepository repository;
    private final CoverageService coverageService;

    public TdsApplicabilityService(OrganizationRepository organizationRepository,
                                   TdsApplicabilityYearRepository repository,
                                   CoverageService coverageService) {
        this.organizationRepository = organizationRepository;
        this.repository = repository;
        this.coverageService = coverageService;
    }

    public TdsApplicability resolve(UUID orgId, int financialYear) {
        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new IllegalArgumentException("Organization not found"));

        EntityClass.EffectiveCode effective = EntityClass.effectiveCode(org.getPan(), org.getDeductorType());
        if (effective.code() != null && !effective.code().equals(INDIVIDUAL_HUF_CODE)) {
            return new TdsApplicability(financialYear, true, TdsApplicabilityBasis.ENTITY_TYPE,
                    false, null, null);
        }

        Optional<TdsApplicabilityYear> recorded = repository
                .findByOrganizationIdAndFinancialYear(orgId, financialYear);
        if (recorded.isPresent()) {
            return fromRow(financialYear, recorded.get(), recorded.get().getBasis(), false);
        }

        List<TdsApplicabilityYear> rows = repository.findByOrganizationIdOrderByFinancialYearAsc(orgId);
        if (rows.isEmpty()) {
            return new TdsApplicability(financialYear, true, TdsApplicabilityBasis.LEGACY_DEFAULT,
                    false, null, null);
        }

        TdsApplicabilityYear nearest = rows.stream()
                .min(Comparator
                        .comparingInt((TdsApplicabilityYear r) -> Math.abs(r.getFinancialYear() - financialYear))
                        .thenComparing(TdsApplicabilityYear::getFinancialYear))
                .orElseThrow();
        return fromRow(financialYear, nearest, TdsApplicabilityBasis.CARRIED_FORWARD, true);
    }

    public boolean isApplicable(UUID orgId, int financialYear) {
        return resolve(orgId, financialYear).applicable();
    }

    public boolean isApplicableOn(UUID orgId, LocalDate date) {
        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new IllegalArgumentException("Organization not found"));
        return isApplicable(orgId, PeriodCalendar.financialYearOf(date, org.getFinancialYearStart()));
    }

    /** Any applicable year from {@code from}'s financial year through the current one. */
    public boolean anyApplicableFrom(UUID orgId, LocalDate from) {
        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new IllegalArgumentException("Organization not found"));
        int fyStartMonth = org.getFinancialYearStart();
        int fromFy = PeriodCalendar.financialYearOf(from, fyStartMonth);
        int currentFy = PeriodCalendar.financialYearOf(LocalDate.now(), fyStartMonth);
        for (int fy = fromFy; fy <= currentFy; fy++) {
            if (isApplicable(orgId, fy)) {
                return true;
            }
        }
        return false;
    }

    /** Every year from the coverage-start financial year through the current one. */
    public List<TdsApplicability> years(UUID orgId) {
        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new IllegalArgumentException("Organization not found"));
        int fyStartMonth = org.getFinancialYearStart();
        int fromFy = PeriodCalendar.financialYearOf(coverageService.coverageStart(orgId), fyStartMonth);
        int currentFy = PeriodCalendar.financialYearOf(LocalDate.now(), fyStartMonth);
        List<TdsApplicability> years = new ArrayList<>();
        for (int fy = fromFy; fy <= currentFy; fy++) {
            years.add(resolve(orgId, fy));
        }
        return years;
    }

    private TdsApplicability fromRow(int financialYear, TdsApplicabilityYear row,
                                     TdsApplicabilityBasis basis, boolean needsAnswer) {
        return new TdsApplicability(financialYear, row.isApplicable(), basis, needsAnswer,
                row.getPriorYearTurnover(), row.getTurnoverNature());
    }
}
