package com.arktech.superaccountant.tds.models;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "tds_payment_detail")
@Data
@NoArgsConstructor
public class TdsPaymentDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "workflow_id", nullable = false, unique = true)
    private UUID workflowId;

    /** First of the covered month, e.g. 2025-04-01. */
    @Column(name = "month", nullable = false)
    private LocalDate month;

    /** Statutory payment due date for the month's TDS. */
    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;
}
