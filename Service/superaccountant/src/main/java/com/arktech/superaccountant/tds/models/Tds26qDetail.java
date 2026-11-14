package com.arktech.superaccountant.tds.models;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "tds_26q_detail")
@Data
@NoArgsConstructor
public class Tds26qDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "workflow_id", nullable = false, unique = true)
    private UUID workflowId;

    /** Deductor snapshot — copied from Organization at creation. */
    @Column(name = "tan", length = 10)
    private String tan;

    @Column(name = "deductor_pan", length = 10)
    private String deductorPan;

    @Column(name = "deductor_name")
    private String deductorName;

    @Column(name = "deductor_address", columnDefinition = "TEXT")
    private String deductorAddress;

    @Column(name = "deductor_type", length = 2)
    private String deductorType;

    @Column(name = "responsible_person_name")
    private String responsiblePersonName;

    @Column(name = "responsible_person_designation")
    private String responsiblePersonDesignation;

    /** Provisional Receipt Number of the original filing — required when this workflow supersedes another. */
    @Column(name = "original_receipt_number", length = 15)
    private String originalReceiptNumber;
}
