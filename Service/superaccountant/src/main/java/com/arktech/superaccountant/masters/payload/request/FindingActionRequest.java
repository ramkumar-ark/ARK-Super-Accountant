package com.arktech.superaccountant.masters.payload.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class FindingActionRequest {
    @NotNull
    private String action; // "ACKNOWLEDGE", "APPROVE", "REJECT"

    private String note; // optional reason/rejection note
}
