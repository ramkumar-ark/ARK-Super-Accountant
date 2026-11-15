package com.arktech.superaccountant.workflow.snapshot;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Data
@NoArgsConstructor
@Entity
@Table(name = "frozen_vouchers")
public class FrozenVoucher {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID organizationId;

    private String voucherKey;

    private LocalDate voucherDate;

    private String voucherTypeName;

    private String voucherNumber;

    private String contentHash;

    @jakarta.persistence.Column(columnDefinition = "text")
    private String payload;

    private UUID frozenByWorkflowId;

    private Instant frozenAt;

    private Instant releasedAt;
}
