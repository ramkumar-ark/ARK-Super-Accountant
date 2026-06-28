package com.arktech.superaccountant.daybook.payload.response;

import com.arktech.superaccountant.masters.models.UploadJobStatus;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Response DTO for DayBookController upload and summary endpoints.
 * Shape defined by D-05: id, status, totalVouchersParsed, errorMessage, voucherSummary.
 */
@Data
@Builder
public class DayBookUploadJobResponse {
    private UUID id;
    private String fileName;
    private UploadJobStatus status;
    private Integer totalVouchersParsed;
    private String uploadedBy;
    private Instant createdAt;
    private Instant completedAt;
    private String errorMessage;
    private List<VoucherTypeSummaryResponse> voucherSummary;
}
