package com.arktech.superaccountant.workflow.payload.request;

import jakarta.validation.constraints.NotBlank;

public class StartWorkflowRequest {
    @NotBlank
    private String type;

    @NotBlank
    private String periodKey;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getPeriodKey() {
        return periodKey;
    }

    public void setPeriodKey(String periodKey) {
        this.periodKey = periodKey;
    }
}
