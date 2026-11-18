package com.arktech.superaccountant.gstr1.payload.request;

import jakarta.validation.constraints.NotBlank;

public record Gstr1FillInRequest(
        @NotBlank String scope,
        @NotBlank String key,
        @NotBlank String attribute,
        String value) {
}
