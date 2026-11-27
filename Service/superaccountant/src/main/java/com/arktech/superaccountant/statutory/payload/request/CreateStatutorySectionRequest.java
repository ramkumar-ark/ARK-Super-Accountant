package com.arktech.superaccountant.statutory.payload.request;

import lombok.Data;

/**
 * A new Income-tax (TDS) section. GST parameter pseudo-sections are fixed by code (each one
 * is read by a specific computation), so only Income-tax sections can be added. The section
 * starts with no rule rows; values are added as dated rule rows.
 */
@Data
public class CreateStatutorySectionRequest {

    private String code;

    private String description;

    private String rpu26qCode;

    private String rpu140Code;

    private Boolean rpu140PayeeSplit;

    private Integer sortOrder;
}
