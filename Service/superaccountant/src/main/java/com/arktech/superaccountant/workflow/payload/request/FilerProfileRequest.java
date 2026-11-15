package com.arktech.superaccountant.workflow.payload.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
public class FilerProfileRequest {
    @NotNull
    private String frequency;

    private boolean iffOptIn;

    private boolean einvoiceEnabled;

    @NotNull
    private LocalDate effectiveFrom;
}
