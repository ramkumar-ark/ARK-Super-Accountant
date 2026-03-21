package com.arktech.superaccountant.masters.payload.response;

import com.arktech.superaccountant.masters.models.UploadJobStatus;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class UploadJobResponse {
    private UUID id;
    private String fileName;
    private UploadJobStatus status;
    private Integer totalLedgersParsed;
    private Integer totalMismatches;
    private String uploadedBy;
    private Instant createdAt;
    private Instant completedAt;
    private List<FindingResponse> findings;
}
