package com.arktech.superaccountant.tds.models;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
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
}
