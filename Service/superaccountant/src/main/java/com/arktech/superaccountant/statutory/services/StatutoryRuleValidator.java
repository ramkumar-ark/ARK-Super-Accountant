package com.arktech.superaccountant.statutory.services;

import com.arktech.superaccountant.statutory.models.EffectiveDateKind;
import com.arktech.superaccountant.statutory.models.Statute;
import com.arktech.superaccountant.statutory.models.StatutorySection;
import com.arktech.superaccountant.statutory.models.StatutoryRuleContext;
import com.arktech.superaccountant.statutory.models.StatutorySectionRule;
import com.arktech.superaccountant.statutory.payload.request.CreateStatutoryRuleRequest;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Pure validation for app-administrator rule writes (no Spring context): bounds,
 * dated-row rules and the append-only, no-overlap history policy. The service turns
 * the returned errors into a 400 response; a non-empty superseded row is end-dated
 * in the same transaction as the new row.
 */
@Component
public class StatutoryRuleValidator {

    public static final String B2CL_THRESHOLD = "B2CL_THRESHOLD";
    public static final String IFF_CAP = "IFF_CAP";
    public static final String GST_RATE = "GST_RATE";

    private static final Set<String> TDS_CONTEXTS = Set.of(
            StatutoryRuleContext.DEFAULT,
            StatutoryRuleContext.BANK,
            StatutoryRuleContext.OTHER_PAYER,
            StatutoryRuleContext.SENIOR_CITIZEN);

    public record Result(List<String> errors, StatutorySectionRule superseded) {
    }

    public static Result validate(StatutorySection section, CreateStatutoryRuleRequest req,
            List<StatutorySectionRule> existing) {
        List<String> errors = new ArrayList<>();

        if (req.getEffectiveDateKind() == null) {
            errors.add("effectiveDateKind is required");
        }
        if (req.getEffectiveFrom() == null) {
            errors.add("effectiveFrom is required");
        } else if (req.getEffectiveDateKind() == EffectiveDateKind.FINANCIAL_YEAR_START
                && (req.getEffectiveFrom().getMonthValue() != 4 || req.getEffectiveFrom().getDayOfMonth() != 1)) {
            errors.add("effectiveFrom must be a financial-year start (1 April) for a regime change");
        }
        if (req.getEffectiveTo() != null && req.getEffectiveFrom() != null
                && req.getEffectiveTo().isBefore(req.getEffectiveFrom())) {
            errors.add("effectiveTo must not be before effectiveFrom");
        }

        checkRate(errors, "rateCompany", req.getRateCompany());
        checkRate(errors, "rateNonCompany", req.getRateNonCompany());
        checkRate(errors, "higherRateIfNoPan", req.getHigherRateIfNoPan());
        checkThreshold(errors, "thresholdPerTransaction", req.getThresholdPerTransaction());
        checkThreshold(errors, "thresholdAnnual", req.getThresholdAnnual());
        checkThreshold(errors, "thresholdMonthly", req.getThresholdMonthly());

        if (req.getSourceCitation() == null || req.getSourceCitation().isBlank()) {
            errors.add("sourceCitation is required");
        } else if (req.getSourceCitation().length() > 255) {
            errors.add("sourceCitation must be at most 255 characters");
        }
        if (req.getLegalReference() != null && req.getLegalReference().length() > 255) {
            errors.add("legalReference must be at most 255 characters");
        }

        if (!section.getActive()) {
            errors.add("section is inactive; activate it first");
        }

        StatutorySectionRule superseded = null;
        if (Statute.INCOME_TAX.equals(section.getStatute())) {
            String context = req.getRuleContext() == null || req.getRuleContext().isBlank()
                    ? StatutoryRuleContext.DEFAULT
                    : req.getRuleContext();
            if (!TDS_CONTEXTS.contains(context)) {
                errors.add("ruleContext must be one of DEFAULT, BANK, OTHER_PAYER, SENIOR_CITIZEN");
            }
            if (req.getRateCompany() == null) {
                errors.add("rateCompany is required for TDS rules");
            }
            if (req.getRateNonCompany() == null) {
                errors.add("rateNonCompany is required for TDS rules");
            }
            if (req.getHigherRateIfNoPan() == null) {
                errors.add("higherRateIfNoPan is required for TDS rules");
            }
        } else if (Statute.CGST.equals(section.getStatute())) {
            if (B2CL_THRESHOLD.equals(section.getCode()) || IFF_CAP.equals(section.getCode())) {
                String context = req.getRuleContext() == null || req.getRuleContext().isBlank()
                        ? StatutoryRuleContext.DEFAULT
                        : req.getRuleContext();
                if (!StatutoryRuleContext.DEFAULT.equals(context)) {
                    errors.add("ruleContext must be DEFAULT for GST parameters");
                }
                if (req.getThresholdAnnual() == null) {
                    errors.add("thresholdAnnual is required for " + section.getCode());
                }
                if (req.getRateCompany() != null || req.getRateNonCompany() != null
                        || req.getHigherRateIfNoPan() != null) {
                    errors.add("rates must be empty for " + section.getCode());
                }
                if (req.getEffectiveTo() != null) {
                    errors.add("effectiveTo must be empty for " + section.getCode()
                            + ": a gap would break downstream reads");
                }
            } else if (GST_RATE.equals(section.getCode())) {
                if (req.getRateCompany() == null) {
                    errors.add("rateCompany is required for GST_RATE");
                } else {
                    String expected = req.getRateCompany().stripTrailingZeros().toPlainString();
                    String context = req.getRuleContext() == null ? "" : req.getRuleContext();
                    if (!expected.equals(context)) {
                        errors.add("ruleContext must equal rateCompany for GST_RATE (expected " + expected + ")");
                    }
                }
                if (req.getThresholdAnnual() != null || req.getThresholdMonthly() != null
                        || req.getThresholdPerTransaction() != null) {
                    errors.add("thresholds must be empty for GST_RATE");
                }
            } else {
                errors.add("CGST section " + section.getCode() + " is not editable");
            }
        }

        // Append-only, no overlap per (section, context): a new row must start strictly
        // after every existing row of the same context, and only an open-ended
        // predecessor is end-dated (to from-1) as supersession.
        String context = req.getRuleContext() == null || req.getRuleContext().isBlank()
                ? StatutoryRuleContext.DEFAULT
                : req.getRuleContext();
        if (req.getEffectiveFrom() != null) {
            List<StatutorySectionRule> sameContext = existing.stream()
                    .filter(r -> context.equals(r.getRuleContext()))
                    .toList();
            if (!sameContext.isEmpty()) {
                StatutorySectionRule latest = sameContext.stream()
                        .max(Comparator.comparing(StatutorySectionRule::getEffectiveFrom))
                        .orElseThrow();
                if (!req.getEffectiveFrom().isAfter(latest.getEffectiveFrom())) {
                    errors.add("rows are append-only: a new row must start after " + latest.getEffectiveFrom());
                } else if (latest.getEffectiveTo() == null) {
                    latest.setEffectiveTo(req.getEffectiveFrom().minusDays(1));
                    superseded = latest;
                } else if (!latest.getEffectiveTo().isBefore(req.getEffectiveFrom())) {
                    errors.add("overlap: existing row runs to " + latest.getEffectiveTo());
                }
            }
        }

        return new Result(errors, superseded);
    }

    private static void checkRate(List<String> errors, String field, BigDecimal value) {
        if (value == null) {
            return;
        }
        if (value.compareTo(BigDecimal.ZERO) < 0 || value.compareTo(new BigDecimal("100")) > 0) {
            errors.add(field + " must be between 0 and 100");
        }
    }

    private static void checkThreshold(List<String> errors, String field, BigDecimal value) {
        if (value == null) {
            return;
        }
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            errors.add(field + " must be at least 0");
        }
    }
}
