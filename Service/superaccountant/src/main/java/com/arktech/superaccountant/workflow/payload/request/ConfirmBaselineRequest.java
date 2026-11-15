package com.arktech.superaccountant.workflow.payload.request;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
public class ConfirmBaselineRequest {
    private UUID uploadJobId;
}
