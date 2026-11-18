package com.arktech.superaccountant.gstr1.payload.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record Gstr1FillInRequest(
        @NotBlank @Size(max = 16) String scope,
        @NotBlank @Size(max = 512) String key,
        @NotBlank @Size(max = 32) String attribute,
        @Size(max = 1024) String value) {
}
