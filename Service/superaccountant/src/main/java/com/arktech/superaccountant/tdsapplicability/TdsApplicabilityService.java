package com.arktech.superaccountant.tdsapplicability;

import com.arktech.superaccountant.masters.models.Organization;
import com.arktech.superaccountant.masters.repository.OrganizationRepository;
import com.arktech.superaccountant.tdsapplicability.models.TdsApplicability;
import com.arktech.superaccountant.tdsapplicability.models.TdsApplicabilityBasis;
import com.arktech.superaccountant.tdsapplicability.models.TdsApplicabilityYear;
import com.arktech.superaccountant.tdsapplicability.models.TurnoverNature;
import com.arktech.superaccountant.tdsapplicability.repository.TdsApplicabilityYearRepository;
import com.arktech.superaccountant.workflow.repository.WorkflowRepository;
import com.arktech.superaccountant.workflow.service.CoverageService;
import com.arktech.superaccountant.workflow.service.compliance.ResponsibilityStartSettingsService;
import com.arktech.superaccountant.workflow.service.period.PeriodCalendar;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
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
    private final WorkflowRepository workflowRepository;
    private final ResponsibilityStartSettingsService responsibilityStartSettingsService;
    private final PriorYearTurnoverService priorYearTurnoverService;

    public TdsApplicabilityService(OrganizationRepository organizationRepository,
                                   TdsApplicabilityYearRepository repository,
                                   CoverageService coverageService,
                                   WorkflowRepository workflowRepository,
                                   ResponsibilityStartSettingsService responsibilityStartSettingsService,
                                   PriorYearTurnoverService priorYearTurnoverService) {
        this.organizationRepository = organizationRepository;
        this.repository = repository;
        this.coverageService = coverageService;
        this.workflowRepository = workflowRepository;
        this.responsibilityStartSettingsService = responsibilityStartSettingsService;
        this.priorYearTurnoverService = priorYearTurnoverService;
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

    /**
     * Writes the user's declared answer for one financial year as a {@code DECLARED}
     * row (registration and the yearly re-confirmation ask). The row wins over the
     * entity type on the next resolve for individual/HUF deductors.
     */
    public TdsApplicability recordDeclared(UUID orgId, int financialYear, boolean applicable,
                                           BigDecimal priorYearTurnover, TurnoverNature nature,
                                           String actor) {
        TdsApplicabilityYear row = new TdsApplicabilityYear();
        row.setOrganizationId(orgId);
        row.setFinancialYear(financialYear);
        row.setApplicable(applicable);
        row.setBasis(TdsApplicabilityBasis.DECLARED);
        row.setPriorYearTurnover(priorYearTurnover);
        row.setTurnoverNature(nature);
        row.setDecidedBy(actor);
        row.setDecidedAt(Instant.now());
        repository.save(row);
        return new TdsApplicability(financialYear, applicable, TdsApplicabilityBasis.DECLARED,
                false, priorYearTurnover, nature);
    }

    /**
     * Records or updates the user's answer for one financial year with an explicit
     * basis and note (settings edit, Task 2 registration, Task 8 confirmation).
     * After saving, the TDS_PAYMENT and TDS_26Q baseline rows are rebuilt so the
     * compliance settings follow the new applicability answer.
     */
    @Transactional
    public TdsApplicability recordYear(UUID orgId, int financialYear, boolean applicable,
                                       TdsApplicabilityBasis basis, BigDecimal turnover,
                                       TurnoverNature nature, String note, String actor) {
        TdsApplicabilityYear row = repository
                .findByOrganizationIdAndFinancialYear(orgId, financialYear)
                .orElseGet(() -> {
                    TdsApplicabilityYear fresh = new TdsApplicabilityYear();
                    fresh.setOrganizationId(orgId);
                    fresh.setFinancialYear(financialYear);
                    return fresh;
                });
        List<Boolean> before = resolvedAnswers(orgId, financialYear);
        row.setApplicable(applicable);
        row.setBasis(basis);
        row.setPriorYearTurnover(turnover);
        row.setTurnoverNature(nature);
        row.setNote(note);
        row.setDecidedBy(actor);
        row.setDecidedAt(Instant.now());
        repository.save(row);
        if (!before.equals(resolvedAnswers(orgId, financialYear))) {
            responsibilityStartSettingsService.rebuildBaselineRows(orgId, actor, com.arktech.superaccountant.workflow.models.WorkflowType.TDS_PAYMENT);
            responsibilityStartSettingsService.rebuildBaselineRows(orgId, actor, com.arktech.superaccountant.workflow.models.WorkflowType.TDS_26Q);
        }
        return new TdsApplicability(financialYear, applicable, basis, false, turnover, nature);
    }

    private List<Boolean> resolvedAnswers(UUID orgId, int savedFinancialYear) {
        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new IllegalArgumentException("Organization not found"));
        int fyStartMonth = org.getFinancialYearStart();
        int fromFy = Math.min(savedFinancialYear,
                PeriodCalendar.financialYearOf(coverageService.coverageStart(orgId), fyStartMonth));
        int toFy = Math.max(savedFinancialYear,
                PeriodCalendar.financialYearOf(LocalDate.now(), fyStartMonth));
        List<Boolean> answers = new ArrayList<>();
        for (int fy = fromFy; fy <= toFy; fy++) {
            answers.add(resolve(orgId, fy).applicable());
        }
        return answers;
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
            TdsApplicability resolved = resolve(orgId, fy);
            years.add(new TdsApplicability(fy, resolved.applicable(), resolved.basis(),
                    resolved.needsAnswer(), resolved.priorYearTurnover(), resolved.turnoverNature(),
                    priorYearTurnoverService.estimateFor(orgId, fy)));
        }
        return years;
    }

    private TdsApplicability fromRow(int financialYear, TdsApplicabilityYear row,
                                     TdsApplicabilityBasis basis, boolean needsAnswer) {
        return new TdsApplicability(financialYear, row.isApplicable(), basis, needsAnswer,
                row.getPriorYearTurnover(), row.getTurnoverNature());
    }
}
