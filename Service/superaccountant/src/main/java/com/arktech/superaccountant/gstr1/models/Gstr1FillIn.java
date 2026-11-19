package com.arktech.superaccountant.gstr1.models;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "gstr1_fill_ins",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_gstr1_fill_in",
                columnNames = {"workflow_id", "scope", "fill_in_key", "attribute"}))
@Data
@NoArgsConstructor
public class Gstr1FillIn {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "workflow_id", nullable = false)
    private UUID workflowId;

    @Convert(converter = Gstr1FillInScopeConverter.class)
    @Column(nullable = false, length = 16)
    private Gstr1FillInScope scope;

    /**
     * Voucher key (see VoucherKeys) for VOUCHER scope, or the ledger name for LEDGER scope.
     */
    @Column(name = "fill_in_key", nullable = false, length = 512)
    private String key;

    @Convert(converter = Gstr1FillInAttributeConverter.class)
    @Column(nullable = false, length = 32)
    private Gstr1FillInAttribute attribute;

    /**
     * Nullable: a carried-forward default may be blank until the accountant fills it in.
     */
    @Column(name = "fill_value", length = 1024)
    private String value;

    @Column(name = "entered_by", nullable = false, length = 255)
    private String enteredBy;

    @Column(name = "entered_at", nullable = false)
    private Instant enteredAt = Instant.now();
}
