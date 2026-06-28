package com.arktech.superaccountant.masters.payload.request;

import lombok.Data;

@Data
public class OnboardRequest {
    private boolean useTemplate;
    private String templateSlug;  // "standard" | "simplified" | "manufacturing"; takes priority over useTemplate
}
