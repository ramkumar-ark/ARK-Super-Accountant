package com.arktech.superaccountant.workflow.models;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "financial_year_closes")
@Data
@NoArgsConstructor
public class FinancialYearClose {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "financial_year", nullable = false)
    private int financialYear;

    @Convert(converter = CloseTypeConverter.class)
    @Column(name = "close_type", nullable = false, length = 32)
    private CloseType closeType;

    @Column(name = "itr_ack_no")
    private String itrAckNo;

    @Column(name = "itr_ack_date")
    private LocalDate itrAckDate;

    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(name = "closed_by", nullable = false)
    private String closedBy;

    @Column(name = "closed_at", nullable = false)
    private Instant closedAt = Instant.now();
}
