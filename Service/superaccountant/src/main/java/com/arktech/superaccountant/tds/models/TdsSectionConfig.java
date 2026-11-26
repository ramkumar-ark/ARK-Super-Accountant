package com.arktech.superaccountant.tds.models;

import com.arktech.superaccountant.statutory.models.StatutoryRuleContext;
import com.arktech.superaccountant.statutory.models.StatutorySectionRule;
import com.arktech.superaccountant.statutory.models.StatutorySectionRules;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.springframework.beans.BeanUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "tds_section_configs")
@Data
@NoArgsConstructor
public class TdsSectionConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "section_code", length = 10, nullable = false, unique = true)
    private String sectionCode;

    private String description;

    @Column(name = "rate_company", precision = 5, scale = 2, nullable = false)
    private BigDecimal rateCompany;

    @Column(name = "rate_non_company", precision = 5, scale = 2, nullable = false)
    private BigDecimal rateNonCompany;

    // Nullable: sections without a per-transaction limit (e.g. 194A)
    @Column(name = "threshold_per_transaction", precision = 19, scale = 2)
    private BigDecimal thresholdPerTransaction;

    @Column(name = "threshold_annual", precision = 19, scale = 2, nullable = false)
    private BigDecimal thresholdAnnual;

    @Column(name = "higher_rate_if_no_pan", precision = 5, scale = 2, nullable = false)
    private BigDecimal higherRateIfNoPan = new BigDecimal("20.00");

    /**
     * Income-tax Act 2025 (s.393) values, applicable when the earlier of credit or payment
     * falls on/after 1 April 2026. Null new-act rates mean the section has no new-act mapping
     * yet and old values continue to apply.
     */
    @Column(name = "new_act_rate_company", precision = 5, scale = 2)
    private BigDecimal newActRateCompany;

    @Column(name = "new_act_rate_non_company", precision = 5, scale = 2)
    private BigDecimal newActRateNonCompany;

    @Column(name = "new_act_threshold_per_transaction", precision = 19, scale = 2)
    private BigDecimal newActThresholdPerTransaction;

    @Column(name = "new_act_threshold_annual", precision = 19, scale = 2)
    private BigDecimal newActThresholdAnnual;

    /**
     * Dated statutory rule rows attached by the caller (TdsAnalysisInputFactory loads
     * them from statutory_section_rules); not persisted on this entity. When present,
     * {@link #effectiveFor} resolves values from these rows instead of the new-act
     * columns above, which remain as the fallback.
     */
    @Transient
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private List<StatutorySectionRule> datedRules;

    public static boolean newActApplies(LocalDate tdsDate) {
        return !tdsDate.isBefore(NEW_ACT_EFFECTIVE_DATE);
    }

    public static final LocalDate NEW_ACT_EFFECTIVE_DATE = LocalDate.of(2026, 4, 1);

    /** Human-facing reference for a TDS date: old section code before 1 April 2026, s.393 after. */
    public String displayFor(LocalDate tdsDate) {
        return TdsSectionRefs.forDate(sectionCode, tdsDate);
    }

    /**
     * Returns the values in force for a TDS date (earlier of credit or payment).
     * Never mutates this entity; detached copies are safe for engine use.
     * When dated rule rows are attached, the covering DEFAULT row wins; otherwise
     * this falls back to the stored new-act columns via
     * {@link #effectiveForFromColumns}.
     */
    public TdsSectionConfig effectiveFor(LocalDate tdsDate) {
        if (datedRules == null || datedRules.isEmpty()) {
            return effectiveForFromColumns(tdsDate);
        }
        StatutorySectionRule rule =
                StatutorySectionRules.effectiveRule(datedRules, tdsDate, StatutoryRuleContext.DEFAULT);
        if (rule == null) {
            // No dated row covers the date (e.g. before the earliest effectiveFrom):
            // the stored values are the pre-existing regime.
            return this;
        }
        TdsSectionConfig copy = new TdsSectionConfig();
        BeanUtils.copyProperties(this, copy);
        copy.setId(getId());
        copy.setRateCompany(rule.getRateCompany());
        copy.setRateNonCompany(rule.getRateNonCompany());
        copy.setThresholdPerTransaction(rule.getThresholdPerTransaction());
        copy.setThresholdAnnual(rule.getThresholdAnnual());
        copy.setHigherRateIfNoPan(rule.getHigherRateIfNoPan());
        return copy;
    }

    /**
     * Legacy two-regime resolution from the stored columns: the new-Act columns
     * on/after 1 April 2026 when populated, otherwise the stored values. Kept as the
     * fallback when no dated rows are attached and as the reference implementation
     * the seeded dated rows must reproduce.
     */
    public TdsSectionConfig effectiveForFromColumns(LocalDate tdsDate) {
        if (!newActApplies(tdsDate) || newActRateCompany == null) {
            return this;
        }
        TdsSectionConfig copy = new TdsSectionConfig();
        BeanUtils.copyProperties(this, copy);
        copy.setId(getId());
        copy.setRateCompany(getNewActRateCompany());
        copy.setRateNonCompany(getNewActRateNonCompany());
        copy.setThresholdPerTransaction(getNewActThresholdPerTransaction());
        copy.setThresholdAnnual(getNewActThresholdAnnual());
        return copy;
    }
}
