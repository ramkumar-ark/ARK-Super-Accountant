package com.arktech.superaccountant.masters.payload.response;

import com.arktech.superaccountant.masters.models.ResolveStatus;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class ResolveResponse {
    private UUID id;
    private ResolveStatus status;
    private String resolvedBy;
    private Instant resolvedAt;
    private String note;
}
