package com.arktech.superaccountant.tds.models;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "tds_challan", indexes = @Index(name = "idx_tds_challan_payment_workflow", columnList = "payment_workflow_id"))
@Data
@NoArgsConstructor
public class TdsChallan {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "payment_workflow_id", nullable = false)
    private UUID paymentWorkflowId;

    @Column(name = "bsr_code", length = 7)
    private String bsrCode;

    @Column(name = "deposit_date")
    private LocalDate depositDate;

    @Column(name = "challan_serial_number", length = 5)
    private String challanSerialNumber;

    @Column(name = "cin")
    private String cin;

    @Column(name = "tds_amount", precision = 19, scale = 2)
    private BigDecimal tdsAmount = BigDecimal.ZERO;

    @Column(precision = 19, scale = 2)
    private BigDecimal surcharge = BigDecimal.ZERO;

    @Column(name = "education_cess", precision = 19, scale = 2)
    private BigDecimal educationCess = BigDecimal.ZERO;

    @Column(precision = 19, scale = 2)
    private BigDecimal interest = BigDecimal.ZERO;

    @Column(precision = 19, scale = 2)
    private BigDecimal penalty = BigDecimal.ZERO;

    @Column(name = "total_amount", precision = 19, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "is_nil_challan", nullable = false)
    private boolean isNilChallan;
}
