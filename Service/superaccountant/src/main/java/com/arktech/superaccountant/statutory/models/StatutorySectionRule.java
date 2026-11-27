package com.arktech.superaccountant.statutory.models;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Dated rule row for a {@link StatutorySection}: the rates/thresholds in force for a
 * period, together with the legal reference for that period (e.g. "194C" up to
 * 31 March 2026 and "393(1) [Table: Sl. No. 6(i)]" from 1 April 2026). Selection is
 * inclusive on both bounds: a row with effectiveFrom 2026-04-01 covers 1 April 2026,
 * a row with effectiveTo 2026-03-31 covers 31 March 2026.
 */
@Entity
@Table(name = "statutory_section_rules")
@Data
@NoArgsConstructor
public class StatutorySectionRule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "section_id", nullable = false)
    private UUID sectionId;

    /**
     * Payer/payee context this row applies to (see {@link StatutoryRuleContext}).
     * Sections without context-dependent values keep a single DEFAULT row set.
     */
    @Column(name = "rule_context", nullable = false, length = 30)
    private String ruleContext = StatutoryRuleContext.DEFAULT;

    @Column(name = "rate_company", precision = 5, scale = 2)
    private BigDecimal rateCompany;

    @Column(name = "rate_non_company", precision = 5, scale = 2)
    private BigDecimal rateNonCompany;

    // Nullable: sections without a per-transaction limit (e.g. 194A)
    @Column(name = "threshold_per_transaction", precision = 19, scale = 2)
    private BigDecimal thresholdPerTransaction;

    @Column(name = "threshold_annual", precision = 19, scale = 2)
    private BigDecimal thresholdAnnual;

    /**
     * Per-calendar-month threshold per payee (e.g. rent under s.393(1) Table Sl. No. 2(ii): no
     * deduction while the rent credited or paid for a month or part of a month does not exceed
     * Rs 50,000). When set, the engine tests the voucher's month instead of the annual aggregate.
     */
    @Column(name = "threshold_monthly", precision = 19, scale = 2)
    private BigDecimal thresholdMonthly;

    /**
     * True when tax is deducted only on the part of the aggregate above the annual threshold
     * (s.194Q / s.393(1) Table Sl. No. 8(ii)) rather than on the whole payment once the
     * threshold is crossed. Null is treated as false.
     */
    @Column(name = "threshold_excess_only")
    private Boolean thresholdExcessOnly;

    @Column(name = "higher_rate_if_no_pan", precision = 5, scale = 2)
    private BigDecimal higherRateIfNoPan;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    /** Null means open-ended (still in force). */
    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    /** Legal reference shown for this period, e.g. "393(1) [Table: Sl. No. 6(i)]". */
    @Column(name = "legal_reference", length = 255)
    private String legalReference;

    /** Where the values come from, e.g. "Income-tax Act 2025 s.393". */
    @Column(name = "source_citation", length = 255)
    private String sourceCitation;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
