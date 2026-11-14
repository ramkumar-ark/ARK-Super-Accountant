package com.arktech.superaccountant.masters.models;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "organizations")
@Data
@NoArgsConstructor
public class Organization {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotBlank
    @Size(max = 255)
    @Column(nullable = false)
    private String name;

    @Column(name = "gstin", length = 15)
    private String gstin;

    @Column(name = "pan", length = 10)
    private String pan;

    @Column(name = "registered_address", columnDefinition = "TEXT")
    private String registeredAddress;

    @Column(name = "financial_year_start", nullable = false)
    private int financialYearStart = 4;

    @Column(name = "books_start_date")
    private LocalDate booksStartDate;

    public static final String TAN_PATTERN = "[A-Z]{4}[0-9]{5}[A-Z]";

    @Column(name = "tan", length = 10)
    private String tan;

    // NSDL deductor/collector category code, e.g. "K" = Company, "P" = Individual/HUF
    @Column(name = "deductor_type", length = 2)
    private String deductorType;

    @Column(name = "responsible_person_name")
    private String responsiblePersonName;

    @Column(name = "responsible_person_designation")
    private String responsiblePersonDesignation;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Organization(String name) {
        this.name = name;
    }
}
