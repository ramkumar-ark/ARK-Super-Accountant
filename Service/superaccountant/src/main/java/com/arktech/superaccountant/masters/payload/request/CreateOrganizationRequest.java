package com.arktech.superaccountant.masters.payload.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreateOrganizationRequest {
    @NotBlank
    @Size(max = 255)
    private String name;

    @Pattern(regexp = "^$|[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z]{1}[1-9A-Z]{1}Z[0-9A-Z]{1}", message = "GSTIN must be 15 characters in the format: 22AAAAA0000A1Z5")
    private String gstin;

    @Pattern(regexp = "^$|[A-Z]{5}[0-9]{4}[A-Z]{1}", message = "PAN must be 10 characters in the format: ABCDE1234F")
    private String pan;

    private String registeredAddress;

    @Min(value = 1, message = "Financial year start must be a month number 1–12")
    @Max(value = 12, message = "Financial year start must be a month number 1–12")
    private int financialYearStart = 4;

    /** Optional; accepted in any case and normalised to upper case by the controller. */
    private String tan;

    /** Used only when the PAN gives no deductor code; validated by {@code DeductorType.isValid}. */
    private String deductorType;

    /** Required for individual/HUF deductors unless derivable from turnover and nature. */
    private Boolean tdsTurnoverExceeded;

    @DecimalMin(value = "0", message = "Prior year turnover must be zero or more")
    private BigDecimal priorYearTurnover;

    private String turnoverNature;
}
