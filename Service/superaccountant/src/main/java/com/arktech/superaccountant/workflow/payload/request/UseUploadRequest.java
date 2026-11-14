package com.arktech.superaccountant.workflow.payload.request;

import java.util.UUID;

public class UseUploadRequest {
    private UUID uploadJobId;

    public UUID getUploadJobId() {
        return uploadJobId;
    }

    public void setUploadJobId(UUID uploadJobId) {
        this.uploadJobId = uploadJobId;
    }
}
