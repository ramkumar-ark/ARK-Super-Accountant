package com.arktech.superaccountant.tds.models;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.BeanUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
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

    /** Human-facing reference under the new Act, e.g. "393(1) [Table: Sl. No. 6(i).D(a)]". */
    @Column(name = "new_act_display_ref", length = 100)
    private String newActDisplayRef;

    /** Form 140 (Rule 219) 4-digit tax code; null when the section has no Form 140 code. */
    @Column(name = "new_act_rpu_code", length = 4)
    private String newActRpuCode;

    @Column(name = "display_ref", length = 100)
    private String displayRef;

    public String getDisplayRef() {
        return displayRef != null ? displayRef : sectionCode;
    }

    public static boolean newActApplies(LocalDate tdsDate) {
        return !tdsDate.isBefore(NEW_ACT_EFFECTIVE_DATE);
    }

    public static final LocalDate NEW_ACT_EFFECTIVE_DATE = LocalDate.of(2026, 4, 1);

    /**
     * Returns the values in force for a TDS date (earlier of credit or payment): the new-Act
     * values on/after 1 April 2026 when populated, otherwise the stored values. Never mutates
     * this entity; detached copies are safe for engine use.
     */
    public TdsSectionConfig effectiveFor(LocalDate tdsDate) {
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
        copy.setDisplayRef(getNewActDisplayRef() != null ? getNewActDisplayRef() : getSectionCode());
        copy.setRpuCode(getNewActRpuCode());
        return copy;
    }

    @Transient
    private String rpuCode;

    public String getRpuCode() {
        return rpuCode;
    }

    public void setRpuCode(String rpuCode) {
        this.rpuCode = rpuCode;
    }
}
