package com.arktech.superaccountant.statutory.payload.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One TDS section option for pickers: identity from statutory_sections plus the
 * legal reference resolved from the section's dated rule rows.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StatutorySectionOptionResponse {

    /** Internal section code, e.g. 194C — the value stored on masters and mappings. */
    private String code;

    private String description;

    /** Legal reference from the dated rule rows (the s.393 reference when present). */
    private String legalReference;

    /** Raw e-TDS return (Form 26Q) section code, or null when the form has no code for it. */
    private String rpu26qCode;

    /** Raw Form 27EQ (CPC 140) payment code before payer/payee special-casing, or null. */
    private String rpu140Code;

    private Integer sortOrder;
}
