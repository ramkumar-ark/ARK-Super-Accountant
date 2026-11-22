package com.arktech.superaccountant.tds.payload.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class DebtorTanRequest {
    @NotBlank
    private String ledgerName;

    @NotBlank
    private String tan;

    private String tallyGuid;
}
