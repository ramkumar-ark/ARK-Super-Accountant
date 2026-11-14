package com.arktech.superaccountant.tds.models;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "tds_deductee_entry", indexes = @Index(name = "idx_tds_deductee_entry_workflow", columnList = "workflow_id"))
@Data
@NoArgsConstructor
public class TdsDeducteeEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "workflow_id", nullable = false)
    private UUID workflowId;

    // Nullable until user maps this entry to a challan
    @Column(name = "tds_challan_id")
    private UUID tdsChallanId;

    @Column(name = "deductee_name", nullable = false)
    private String deducteeName;

    @Column(name = "deductee_pan", length = 10)
    private String deducteePan;

    @Column(name = "pan_valid", nullable = false)
    private boolean panValid;

    @Convert(converter = TdsDeducteeTypeConverter.class)
    @Column(name = "deductee_type", nullable = false, length = 20)
    private TdsDeducteeType deducteeType = TdsDeducteeType.NON_COMPANY;

    @Column(length = 10, nullable = false)
    private String section;

    @Column(name = "amount_paid", precision = 19, scale = 2, nullable = false)
    private BigDecimal amountPaid;

    @Column(name = "tds_deducted", precision = 19, scale = 2, nullable = false)
    private BigDecimal tdsDeducted;

    @Column(name = "tds_deposited", precision = 19, scale = 2, nullable = false)
    private BigDecimal tdsDeposited;

    @Column(name = "date_of_payment")
    private LocalDate dateOfPayment;

    @Column(name = "date_of_deduction")
    private LocalDate dateOfDeduction;

    // Percentage actually applied, e.g. 1.00, 2.00, 10.00, 20.00
    @Column(name = "rate_applied", precision = 5, scale = 2)
    private BigDecimal rateApplied;

    @Column(name = "is_higher_rate_applied", nullable = false)
    private boolean isHigherRateApplied;

    // JSON array of ParsedVoucher UUIDs, e.g. ["a1b2..."] — normally one element
    @Column(name = "source_voucher_ids", columnDefinition = "TEXT")
    private String sourceVoucherIds;
}
