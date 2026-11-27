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

    /**
     * LEGACY: the rate/threshold columns (rateCompany .. higherRateIfNoPan) and the newAct*
     * columns below are frozen. Once a section is backfilled, statutory_section_rules is the
     * authoritative source for rates and thresholds; these columns are only a fallback for
     * not-yet-backfilled databases, are never re-synced, and are slated for removal in PR2/PR3.
     * Rate/threshold edits must be made to statutory_section_rules.
     */
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
     * LEGACY (frozen, not re-synced; edit statutory_section_rules instead).
     * Income-tax Act 2025 (s.393) values, applicable when the earlier of credit or payment
     * falls on/after 1 April 2026. Null new-act rates mean the section has no new-act mapping
     * yet and old values continue to apply.
     *
     * @deprecated since PR2 the statutory tables are the single source of section truth
     *     (statutory_section_rules holds the dated values); these columns are only read by
     *     the seed/backfill path and are slated for removal in PR3.
     */
    @Deprecated
    @Column(name = "new_act_rate_company", precision = 5, scale = 2)
    private BigDecimal newActRateCompany;

    @Deprecated
    @Column(name = "new_act_rate_non_company", precision = 5, scale = 2)
    private BigDecimal newActRateNonCompany;

    @Deprecated
    @Column(name = "new_act_threshold_per_transaction", precision = 19, scale = 2)
    private BigDecimal newActThresholdPerTransaction;

    @Deprecated
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

    /**
     * Engine-only (never persisted): per-calendar-month threshold per payee, from the covering
     * rule row. When set it replaces the annual aggregate test (rent, s.393(1) Table Sl. No. 2(ii)).
     */
    @Transient
    private BigDecimal thresholdMonthly;

    /**
     * Engine-only: tax applies only to the part of the aggregate above {@link #thresholdAnnual}
     * (s.194Q). False means the whole payment is taxed once the threshold is crossed.
     */
    @Transient
    private boolean thresholdExcessOnly;

    /**
     * Engine-only: the section's rate pair is split by payee status. {@link #rateNonCompany} is
     * then the individual/HUF rate (PAN 4th character P or H) and {@link #rateCompany} is the
     * rate for every other payee (company, firm, LLP, AOP, trust, ...), e.g. 194C at 1% / 2%.
     * Copied from {@code StatutorySection.rpu140PayeeSplit} by the caller.
     */
    @Transient
    private boolean payeeSplit;

    public static boolean newActApplies(LocalDate tdsDate) {
        return !tdsDate.isBefore(NEW_ACT_EFFECTIVE_DATE);
    }

    public static final LocalDate NEW_ACT_EFFECTIVE_DATE = LocalDate.of(2026, 4, 1);

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
        copy.setThresholdMonthly(rule.getThresholdMonthly());
        copy.setThresholdExcessOnly(Boolean.TRUE.equals(rule.getThresholdExcessOnly()));
        return copy;
    }

    /**
     * Legacy two-regime resolution from the stored columns: the new-Act columns
     * on/after 1 April 2026 when populated, otherwise the stored values. Kept as the
     * fallback when no dated rows are attached and as the reference implementation
     * the seeded dated rows must reproduce.
     *
     * @deprecated since PR2 the statutory tables are the single source of section truth;
     *     this column-based fallback remains only until PR3 removes the legacy columns.
     */
    @Deprecated
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
