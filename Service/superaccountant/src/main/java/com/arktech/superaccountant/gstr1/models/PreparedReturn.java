package com.arktech.superaccountant.gstr1.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "prepared_returns")
@Data
@NoArgsConstructor
public class PreparedReturn {

    @Id
    private UUID id;

    @Column(name = "workflow_id", nullable = false)
    private UUID workflowId;

    @Column(name = "return_kind", nullable = false, length = 32)
    private String returnKind;

    @Column(name = "built_at", nullable = false)
    private Instant builtAt;

    @Column(name = "frozen_at")
    private Instant frozenAt;
}
