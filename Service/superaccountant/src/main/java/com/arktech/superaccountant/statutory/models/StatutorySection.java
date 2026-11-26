package com.arktech.superaccountant.statutory.models;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Global (not per-organization) statutory section: identity-level data that does not
 * change with time — display description, RPU return codes and the payee-split flag.
 * Everything date-dependent (rates, thresholds, legal reference) lives on
 * {@link StatutorySectionRule} rows so the section itself never needs editing when
 * a Finance Act changes values.
 */
@Entity
@Table(name = "statutory_sections",
        uniqueConstraints = @UniqueConstraint(name = "uk_statutory_sections_statute_code",
                columnNames = {"statute", "code"}))
@Data
@NoArgsConstructor
public class StatutorySection {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Statute statute;

    /** Internal section code as used by the engine, e.g. 194C or 194I_A. */
    @Column(nullable = false, length = 20)
    private String code;

    @Column(length = 255)
    private String description;

    /** Raw e-TDS return (Form 26Q) section code, or null when the form has no code for this section. */
    @Column(name = "rpu_26q_code", length = 10)
    private String rpu26qCode;

    /** Raw Form 27EQ (CPC 140) payment code before payer/payee special-casing, or null. */
    @Column(name = "rpu_140_code", length = 10)
    private String rpu140Code;

    /** True when the RPU 140 filing splits this section by payee status (194C individual/HUF vs other). */
    @Column(name = "rpu_140_payee_split", nullable = false)
    private Boolean rpu140PayeeSplit = false;

    /** Display order for UIs listing all sections of a statute. */
    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    /** False hides the section from pickers without deleting history. */
    @Column(nullable = false)
    private Boolean active = true;

    /** True for rows seeded by the built-in migration; future admin edits can flag false. */
    @Column(nullable = false)
    private Boolean seeded = true;
}
