package com.arktech.superaccountant.tdsapplicability;

import com.arktech.superaccountant.masters.models.Organization;
import com.arktech.superaccountant.masters.repository.OrganizationRepository;
import com.arktech.superaccountant.tdsapplicability.models.PromptKind;
import com.arktech.superaccountant.tdsapplicability.models.PromptStatus;
import com.arktech.superaccountant.tdsapplicability.models.TdsApplicability;
import com.arktech.superaccountant.tdsapplicability.models.TdsApplicabilityBasis;
import com.arktech.superaccountant.tdsapplicability.models.TdsApplicabilityPrompt;
import com.arktech.superaccountant.tdsapplicability.models.TurnoverNature;
import com.arktech.superaccountant.tdsapplicability.repository.TdsApplicabilityPromptRepository;
import com.arktech.superaccountant.tdsapplicability.repository.TdsApplicabilityYearRepository;
import com.arktech.superaccountant.workflow.models.WorkflowStage;
import com.arktech.superaccountant.workflow.models.WorkflowType;
import com.arktech.superaccountant.workflow.repository.WorkflowRepository;
import com.arktech.superaccountant.workflow.service.period.PeriodCalendar;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;


/**
 * Raises and resolves the TDS applicability prompts (ADR 0008, task 8):
 *
 * <ul>
 *   <li>{@code BOOKS_EXCEED_LIMIT} — for an individual/HUF organization with a year recorded
 *       as not applicable, when the computed preceding-year turnover crosses
 *       ₹50,00,000 (the lower statutory limit). Raised when the baseline is confirmed and
 *       when a financial year is closed.</li>
 *   <li>{@code NEXT_YEAR_DECLARATION} — raised when a financial year closes and the next
 *       year has no recorded applicability answer.</li>
 *   <li>{@code LEGACY_INDIVIDUAL_NOTE} — one-time, created lazily on the first prompt GET
 *       for an individual/HUF organization with no recorded rows.</li>
 * </ul>
 *
 * A prompt of a kind + financial year is never raised again once it was dismissed.
 */
@Service
public class TdsApplicabilityPromptService {

    private static final String INDIVIDUAL_HUF_CODE = "Q";

    /** The lower statutory turnover limit (profession): ₹50,00,000. */
    private static final BigDecimal TURNOVER_TRIGGER = new BigDecimal("5000000.00");

    private final OrganizationRepository organizationRepository;
    private final TdsApplicabilityYearRepository yearRepository;
    private final TdsApplicabilityPromptRepository promptRepository;
    private final TdsApplicabilityService tdsApplicabilityService;
    private final PriorYearTurnoverService priorYearTurnoverService;
    private final WorkflowRepository workflowRepository;

    public TdsApplicabilityPromptService(OrganizationRepository organizationRepository,
                                         TdsApplicabilityYearRepository yearRepository,
                                         TdsApplicabilityPromptRepository promptRepository,
                                         TdsApplicabilityService tdsApplicabilityService,
                                         PriorYearTurnoverService priorYearTurnoverService,
                                         WorkflowRepository workflowRepository) {
        this.organizationRepository = organizationRepository;
        this.yearRepository = yearRepository;
        this.promptRepository = promptRepository;
        this.tdsApplicabilityService = tdsApplicabilityService;
        this.priorYearTurnoverService = priorYearTurnoverService;
        this.workflowRepository = workflowRepository;
    }

    /** The open prompts for one organization, as shown on the dashboard banner. */
    public record PromptView(UUID id, PromptKind kind, int financialYear,
                             TurnoverEstimate estimate, String message) {
    }

    /** Confirm body: whether TDS applies, with the books figure when confirming from books. */
    public record ConfirmRequest(boolean applicable, BigDecimal priorYearTurnover,
                                 TurnoverNature turnoverNature) {
    }

    /** REQUIRES_NEW: the AFTER_COMMIT listener runs with the triggering transaction already completed. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void raiseBooksExceedPrompts(UUID orgId) {
        if (!isIndividualHuf(orgId)) {
            return;
        }
        for (TdsApplicability year : tdsApplicabilityService.years(orgId)) {
            if (!year.applicable()) {
                checkBooksExceed(orgId, year.financialYear());
            }
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onFinancialYearClosed(UUID orgId, int closedFy) {
        if (!isIndividualHuf(orgId)) {
            return;
        }
        checkBooksExceed(orgId, closedFy + 1);

        int nextFy = closedFy + 1;
        if (yearRepository.findByOrganizationIdAndFinancialYear(orgId, nextFy).isEmpty()
                && !promptExists(orgId, PromptKind.NEXT_YEAR_DECLARATION, nextFy)) {
            create(orgId, PromptKind.NEXT_YEAR_DECLARATION, nextFy, null);
        }
    }

    /** Open prompts; lazily creates the one-time legacy note for a rowless individual/HUF org. */
    @Transactional
    public List<PromptView> openPrompts(UUID orgId) {
        maybeCreateLegacyNote(orgId);
        return promptRepository
                .findByOrganizationIdAndStatusOrderByCreatedAtAsc(orgId, PromptStatus.OPEN)
                .stream()
                .map(this::view)
                .toList();
    }

    @Transactional
    public PromptView confirm(UUID orgId, UUID promptId, boolean applicable,
                              BigDecimal priorYearTurnover, TurnoverNature nature, String actor) {
        TdsApplicabilityPrompt prompt = openPrompt(orgId, promptId);
        if (!applicable && workflowRepository
                .existsByOrganizationIdAndTypeInAndFinancialYearAndStageInAndActiveTrue(orgId,
                        List.of(WorkflowType.TDS_PAYMENT, WorkflowType.TDS_26Q),
                        prompt.getFinancialYear(),
                        List.of(WorkflowStage.FINALIZED, WorkflowStage.FILED))) {
            throw new IllegalStateException(fyLabel(prompt.getFinancialYear())
                    + " has a finalized TDS workflow; unfinalize it first");
        }
        TdsApplicabilityBasis basis = prompt.getKind() == PromptKind.BOOKS_EXCEED_LIMIT
                ? TdsApplicabilityBasis.CONFIRMED_FROM_BOOKS
                : TdsApplicabilityBasis.DECLARED;
        tdsApplicabilityService.recordYear(orgId, prompt.getFinancialYear(), applicable, basis,
                priorYearTurnover, nature,
                prompt.getKind() + " prompt confirmed", actor);
        prompt.setStatus(PromptStatus.CONFIRMED);
        prompt.setResolvedAt(Instant.now());
        prompt.setResolvedBy(actor);
        promptRepository.save(prompt);
        return view(prompt);
    }

    @Transactional
    public PromptView dismiss(UUID orgId, UUID promptId, String reason, String actor) {
        TdsApplicabilityPrompt prompt = openPrompt(orgId, promptId);
        prompt.setStatus(PromptStatus.DISMISSED);
        prompt.setDismissReason(reason);
        prompt.setResolvedAt(Instant.now());
        prompt.setResolvedBy(actor);
        promptRepository.save(prompt);
        return view(prompt);
    }

    private void checkBooksExceed(UUID orgId, int financialYear) {
        TurnoverEstimate estimate = priorYearTurnoverService.estimateFor(orgId, financialYear);
        if (estimate == null || estimate.amount() == null) {
            return;
        }
        if (estimate.amount().compareTo(TURNOVER_TRIGGER) <= 0) {
            return;
        }
        if (promptExists(orgId, PromptKind.BOOKS_EXCEED_LIMIT, financialYear)) {
            return;
        }
        create(orgId, PromptKind.BOOKS_EXCEED_LIMIT, financialYear, estimate);
    }

    private void maybeCreateLegacyNote(UUID orgId) {
        if (!isIndividualHuf(orgId)) {
            return;
        }
        if (!yearRepository.findByOrganizationIdOrderByFinancialYearAsc(orgId).isEmpty()) {
            return;
        }
        if (promptRepository.existsByOrganizationIdAndKind(orgId, PromptKind.LEGACY_INDIVIDUAL_NOTE)) {
            return;
        }
        Organization org = organizationRepository.findById(orgId).orElseThrow();
        int currentFy = PeriodCalendar.financialYearOf(LocalDate.now(), org.getFinancialYearStart());
        create(orgId, PromptKind.LEGACY_INDIVIDUAL_NOTE, currentFy, null);
    }

    private boolean promptExists(UUID orgId, PromptKind kind, int financialYear) {
        return promptRepository.existsByOrganizationIdAndKindAndFinancialYearAndStatusIn(
                orgId, kind, financialYear, List.of(PromptStatus.OPEN, PromptStatus.DISMISSED));
    }

    private TdsApplicabilityPrompt create(UUID orgId, PromptKind kind, int financialYear,
                                          TurnoverEstimate estimate) {
        TdsApplicabilityPrompt prompt = new TdsApplicabilityPrompt();
        prompt.setOrganizationId(orgId);
        prompt.setKind(kind);
        prompt.setFinancialYear(financialYear);
        prompt.setStatus(PromptStatus.OPEN);
        if (estimate != null) {
            prompt.setEstimateMeasuredFy(estimate.measuredFinancialYear());
            prompt.setEstimateAmount(estimate.amount());
            prompt.setEstimateSource(estimate.source() != null ? estimate.source().name() : null);
            prompt.setEstimateComplete(estimate.complete());
        }
        return promptRepository.save(prompt);
    }

    private TdsApplicabilityPrompt openPrompt(UUID orgId, UUID promptId) {
        TdsApplicabilityPrompt prompt = promptRepository.findById(promptId)
                .orElseThrow(() -> new IllegalArgumentException("Prompt not found"));
        if (!prompt.getOrganizationId().equals(orgId)) {
            throw new IllegalArgumentException("Prompt not found");
        }
        if (prompt.getStatus() != PromptStatus.OPEN) {
            throw new IllegalStateException("Prompt is not open");
        }
        return prompt;
    }

    private PromptView view(TdsApplicabilityPrompt prompt) {
        TurnoverEstimate estimate = null;
        if (prompt.getEstimateSource() != null) {
            estimate = new TurnoverEstimate(
                    prompt.getEstimateMeasuredFy() != null ? prompt.getEstimateMeasuredFy() : prompt.getFinancialYear() - 1,
                    prompt.getEstimateAmount(),
                    TurnoverEstimate.TurnoverSource.valueOf(prompt.getEstimateSource()),
                    Boolean.TRUE.equals(prompt.getEstimateComplete()));
        }
        return new PromptView(prompt.getId(), prompt.getKind(), prompt.getFinancialYear(),
                estimate, message(prompt, estimate));
    }

    private String message(TdsApplicabilityPrompt prompt, TurnoverEstimate estimate) {
        int fy = prompt.getFinancialYear();
        String label = "FY " + fy + "-" + String.format("%02d", (fy + 1) % 100);
        return switch (prompt.getKind()) {
            case BOOKS_EXCEED_LIMIT -> {
                String rupees = estimate != null && estimate.amount() != null
                        ? indianRupees(estimate.amount())
                        : "unknown";
                yield "Books show receipts of ₹" + rupees + " for "
                        + "FY " + (fy - 1) + "-" + String.format("%02d", fy % 100)
                        + ". If this exceeds ₹1 crore (business) or ₹50 lakh (profession), "
                        + "TDS has applied since 1 April " + (fy - 1)
                        + " and deductions for this year are due.";
            }
            case NEXT_YEAR_DECLARATION -> label
                    + " has started — please declare whether TDS applies for the new year.";
            case LEGACY_INDIVIDUAL_NOTE -> "TDS has been on for your organization by default. "
                    + "As an individual/HUF you may be below the turnover limit — please confirm "
                    + "whether TDS applies.";
        };
    }

    /** Indian numbering: last 3 digits, then pairs — 12000000 → "1,20,00,000". */
    private static String indianRupees(BigDecimal amount) {
        String digits = amount.setScale(0, java.math.RoundingMode.HALF_UP).toBigInteger().abs().toString();
        if (digits.length() <= 3) {
            return digits;
        }
        String head = digits.substring(0, digits.length() - 3);
        String tail = digits.substring(digits.length() - 3);
        StringBuilder groups = new StringBuilder();
        while (head.length() > 2) {
            groups.insert(0, "," + head.substring(head.length() - 2));
            head = head.substring(0, head.length() - 2);
        }
        return head + (groups.length() > 0 ? groups + "," : "") + tail;
    }

    /** FY 2025 renders as "FY 2025-26", matching the client's fyLabel. */
    private static String fyLabel(int financialYear) {
        return "FY " + financialYear + "-" + String.format("%02d", (financialYear + 1) % 100);
    }

    private boolean isIndividualHuf(UUID orgId) {        Organization org = organizationRepository.findById(orgId).orElse(null);
        if (org == null) {
            return false;
        }
        var effective = com.arktech.superaccountant.tdsapplicability.EntityClass
                .effectiveCode(org.getPan(), org.getDeductorType());
        return INDIVIDUAL_HUF_CODE.equals(effective.code());
    }
}
