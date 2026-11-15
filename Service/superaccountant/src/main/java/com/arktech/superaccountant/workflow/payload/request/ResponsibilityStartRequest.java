package com.arktech.superaccountant.workflow.payload.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
public class ResponsibilityStartRequest {
    @NotNull
    private String type;

    @NotNull
    private LocalDate firstPeriodStart;
}
