package com.arktech.superaccountant.workflow.payload.request;

import jakarta.validation.constraints.NotBlank;

public class UnfinalizeRequest {
    @NotBlank
    private String reason;

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
