package com.arktech.superaccountant.statutory.models;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Pure (no Spring, no repository) selection over dated {@link StatutorySectionRule}
 * rows: the latest row whose period covers the date, for the requested context,
 * falling back to the DEFAULT context when the context has no covering row.
 */
public final class StatutorySectionRules {

    private StatutorySectionRules() {
    }

    /**
     * Row in force for the date in the given context, or null when nothing covers it.
     * Both bounds are inclusive; when several rows cover, the latest effectiveFrom wins.
     * A null context is treated as DEFAULT; a null date yields null.
     */
    public static StatutorySectionRule effectiveRule(List<StatutorySectionRule> rows,
                                                     LocalDate date,
                                                     String context) {
        String requested = context == null ? StatutoryRuleContext.DEFAULT : context;
        StatutorySectionRule rule = latestCovering(rows, date, requested);
        if (rule == null && !StatutoryRuleContext.DEFAULT.equals(requested)) {
            rule = latestCovering(rows, date, StatutoryRuleContext.DEFAULT);
        }
        return rule;
    }

    private static StatutorySectionRule latestCovering(List<StatutorySectionRule> rows,
                                                       LocalDate date,
                                                       String context) {
        if (rows == null || date == null) {
            return null;
        }
        StatutorySectionRule best = null;
        for (StatutorySectionRule row : rows) {
            if (!Objects.equals(context, row.getRuleContext())) {
                continue;
            }
            if (row.getEffectiveFrom() == null || row.getEffectiveFrom().isAfter(date)) {
                continue;
            }
            if (row.getEffectiveTo() != null && row.getEffectiveTo().isBefore(date)) {
                continue;
            }
            if (best == null || row.getEffectiveFrom().isAfter(best.getEffectiveFrom())) {
                best = row;
            }
        }
        return best;
    }
}
