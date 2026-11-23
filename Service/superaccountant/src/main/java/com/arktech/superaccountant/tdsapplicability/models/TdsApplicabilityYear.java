package com.arktech.superaccountant.tdsapplicability.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One financial year's TDS deductor applicability for one organization.
 * Rows are never deleted; a year that is not a deductor year is hidden, not erased.
 */
@Entity
@Table(name = "tds_applicability_years",
        uniqueConstraints = @UniqueConstraint(columnNames = {"organization_id", "financial_year"}))
@Data
@NoArgsConstructor
public class TdsApplicabilityYear {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "financial_year", nullable = false)
    private int financialYear;

    @Column(nullable = false)
    private boolean applicable;

    @Enumerated(EnumType.STRING)
    @Column(name = "basis", length = 32, nullable = false)
    private TdsApplicabilityBasis basis;

    @Column(name = "prior_year_turnover", precision = 18, scale = 2)
    private BigDecimal priorYearTurnover;

    @Enumerated(EnumType.STRING)
    @Column(name = "turnover_nature", length = 16)
    private TurnoverNature turnoverNature;

    @Column(name = "decided_by")
    private String decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(columnDefinition = "TEXT")
    private String note;
}
