package com.arktech.superaccountant.workflow.payload.request;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class WorkflowFindingActionRequest {
    private String action;
    private String value;
    private String note;
}
