package com.arktech.superaccountant.statutory.services;

import com.arktech.superaccountant.statutory.models.StatutoryRuleContext;
import com.arktech.superaccountant.statutory.models.StatutorySectionRule;
import com.arktech.superaccountant.statutory.models.StatutorySectionRules;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Immutable, date-aware view of the GST (CGST) parameter rows held as pseudo-sections in
 * statutory_sections / statutory_section_rules. This is the one lookup every GST consumer
 * (GSTR-1 prepare and validate, filed-return reconciliation) reads, so a statutory change
 * is a new dated row and cannot make two consumers disagree.
 *
 * <p>A date that no row covers is a configuration gap, not a default: lookups throw rather
 * than silently fall back to a hard-coded value.
 */
public final class GstParameters {

    /** Inter-state B2C invoice value above which GSTR-1 reports invoice-wise (B2CL); value in threshold_annual. */
    public static final String B2CL_THRESHOLD = "B2CL_THRESHOLD";

    /** Aggregate cap for an Invoice Furnishing Facility month; value in threshold_annual. */
    public static final String IFF_CAP = "IFF_CAP";

    /** One row per valid GST rate slab, rule_context = the rate text, value in rate_company. */
    public static final String GST_RATE = "GST_RATE";

    private final Map<String, List<StatutorySectionRule>> rulesByCode;

    private GstParameters(Map<String, List<StatutorySectionRule>> rulesByCode) {
        this.rulesByCode = rulesByCode;
    }

    /** @param rulesByCode rule rows of each CGST pseudo-section, keyed by section code */
    public static GstParameters of(Map<String, List<StatutorySectionRule>> rulesByCode) {
        return new GstParameters(Map.copyOf(rulesByCode));
    }

    public BigDecimal b2clThreshold(LocalDate date) {
        return requiredAnnual(B2CL_THRESHOLD, date);
    }

    /**
     * Threshold for a GSTR-1 return period. A period takes the threshold in force on its
     * first day, which is exact because the threshold changes on month boundaries
     * (a notification effective mid-month would need a per-document date instead).
     */
    public BigDecimal b2clThreshold(YearMonth period) {
        return b2clThreshold(period.atDay(1));
    }

    public BigDecimal iffCap(LocalDate date) {
        return requiredAnnual(IFF_CAP, date);
    }

    /** True when {@code rate} (numeric equality, any scale) is an allowed GST slab on {@code date}. */
    public boolean isAllowedRate(BigDecimal rate, LocalDate date) {
        if (rate == null) {
            return false;
        }
        List<StatutorySectionRule> rows = rulesByCode.getOrDefault(GST_RATE, List.of());
        Set<String> contexts = new LinkedHashSet<>();
        rows.forEach(r -> contexts.add(r.getRuleContext()));
        for (String context : contexts) {
            StatutorySectionRule covering = StatutorySectionRules.effectiveRule(rows, date, context);
            if (covering != null && covering.getRateCompany() != null
                    && covering.getRateCompany().compareTo(rate) == 0) {
                return true;
            }
        }
        return false;
    }

    private BigDecimal requiredAnnual(String code, LocalDate date) {
        StatutorySectionRule rule = StatutorySectionRules.effectiveRule(
                rulesByCode.getOrDefault(code, List.of()), date, StatutoryRuleContext.DEFAULT);
        if (rule == null || rule.getThresholdAnnual() == null) {
            throw new IllegalStateException(
                    "No CGST statutory rule row for " + code + " covering " + date);
        }
        return rule.getThresholdAnnual();
    }
}
