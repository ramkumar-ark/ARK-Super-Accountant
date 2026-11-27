package com.arktech.superaccountant.statutory.services;

import com.arktech.superaccountant.statutory.models.StatutoryRuleContext;
import com.arktech.superaccountant.statutory.models.StatutorySectionRule;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Seed definition of the GST parameter rows. To change a GST parameter for a new period,
 * end the current row (effectiveTo) and add a dated row here; startup adds missing rows
 * (see DataMigrationService#seedGstParameters). Existing rows are never rewritten.
 *
 * <p>Rate slabs and the IFF cap reproduce the values the app has always applied
 * (behaviour-preserving seed, not a statement of historic law for every date).
 */
public final class GstStatutorySeed {

    private GstStatutorySeed() {
    }

    /** One dated value. {@code value} goes to threshold_annual, or rate_company for GST_RATE. */
    public record Row(String code, String description, int sortOrder, String context,
                      BigDecimal value, LocalDate effectiveFrom, LocalDate effectiveTo,
                      String sourceCitation) {
    }

    private static final LocalDate GST_START = LocalDate.of(2017, 7, 1);
    private static final String B2CL_DESCRIPTION =
            "B2CL invoice value threshold (inter-state supply to unregistered person)";
    private static final String RATE_CITATION =
            "GST Council rate schedule; app's established allowed-rate list";
    private static final String[] RATES = {
            "0", "0.1", "0.25", "1", "1.5", "3", "5", "7.5", "12", "18", "28", "40"};

    public static final List<Row> ROWS = buildRows();

    private static List<Row> buildRows() {
        List<Row> rows = new ArrayList<>();
        rows.add(new Row(GstParameters.B2CL_THRESHOLD, B2CL_DESCRIPTION, 1,
                StatutoryRuleContext.DEFAULT, new BigDecimal("250000.00"),
                GST_START, LocalDate.of(2024, 7, 31),
                "CGST Rules 2017 r.59(4) (two and a half lakh rupees)"));
        rows.add(new Row(GstParameters.B2CL_THRESHOLD, B2CL_DESCRIPTION, 1,
                StatutoryRuleContext.DEFAULT, new BigDecimal("100000.00"),
                LocalDate.of(2024, 8, 1), null,
                "CGST Rules 2017 r.59(4) as amended by Notification 12/2024-CT dated 10 Jul 2024,"
                        + " w.e.f. 1 Aug 2024 (one lakh rupees)"));
        rows.add(new Row(GstParameters.IFF_CAP,
                "Invoice Furnishing Facility monthly aggregate cap", 2,
                StatutoryRuleContext.DEFAULT, new BigDecimal("5000000.00"),
                LocalDate.of(2021, 1, 1), null,
                "CGST Rules 2017 r.59 (Invoice Furnishing Facility, QRMP scheme)"));
        for (String rate : RATES) {
            rows.add(new Row(GstParameters.GST_RATE, "Allowed GST rate slab (percent)", 3,
                    rate, new BigDecimal(rate), GST_START, null, RATE_CITATION));
        }
        return List.copyOf(rows);
    }

    /** The seed as in-memory rule rows, for callers and tests that need no database. */
    public static GstParameters asParameters() {
        Map<String, List<StatutorySectionRule>> byCode = new LinkedHashMap<>();
        for (Row row : ROWS) {
            byCode.computeIfAbsent(row.code(), k -> new ArrayList<>()).add(toRule(row, null));
        }
        return GstParameters.of(byCode);
    }

    /** Rule entity for a seed row; {@code sectionId} is null for in-memory use. */
    public static StatutorySectionRule toRule(Row row, java.util.UUID sectionId) {
        StatutorySectionRule rule = new StatutorySectionRule();
        rule.setSectionId(sectionId);
        rule.setRuleContext(row.context());
        if (GstParameters.GST_RATE.equals(row.code())) {
            rule.setRateCompany(row.value());
            rule.setRateNonCompany(row.value());
        } else {
            rule.setThresholdAnnual(row.value());
        }
        rule.setEffectiveFrom(row.effectiveFrom());
        rule.setEffectiveTo(row.effectiveTo());
        rule.setSourceCitation(row.sourceCitation());
        return rule;
    }
}
