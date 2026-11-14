package com.arktech.superaccountant.workflow.payload.request;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
public class WorkflowBulkActionRequest {
    private List<UUID> findingIds;
    private String action;
    private String note;
}
