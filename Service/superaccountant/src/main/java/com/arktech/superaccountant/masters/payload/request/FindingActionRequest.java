package com.arktech.superaccountant.masters.payload.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class FindingActionRequest {
    @NotNull
    private String action; // "ACKNOWLEDGE", "APPROVE", "REJECT"

    private String note; // optional reason; required for REJECT

    private String value; // proposed master value; required for typed-input rule codes on ACKNOWLEDGE
}
