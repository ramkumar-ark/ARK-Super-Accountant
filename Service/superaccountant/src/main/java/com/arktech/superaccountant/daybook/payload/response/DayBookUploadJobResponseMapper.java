package com.arktech.superaccountant.daybook.payload.response;

import com.arktech.superaccountant.daybook.models.DayBookUploadJob;

import java.util.List;

/**
 * Maps a persisted {@link DayBookUploadJob} to its response DTO.
 */
public final class DayBookUploadJobResponseMapper {

    private DayBookUploadJobResponseMapper() {
    }

    public static DayBookUploadJobResponse toResponse(DayBookUploadJob job,
                                                      List<String> warnings,
                                                      List<VoucherTypeSummaryResponse> voucherSummary) {
        return DayBookUploadJobResponse.builder()
                .id(job.getId())
                .fileName(job.getFileName())
                .status(job.getStatus())
                .totalVouchersParsed(job.getTotalVouchersParsed())
                .uploadedBy(job.getUploadedBy())
                .createdAt(job.getCreatedAt())
                .completedAt(job.getCompletedAt())
                .errorMessage(job.getErrorMessage())
                .coverageFrom(job.getCoverageFrom())
                .coverageTo(job.getCoverageTo())
                .minVoucherDate(job.getMinVoucherDate())
                .maxVoucherDate(job.getMaxVoucherDate())
                .createdForWorkflowId(job.getCreatedForWorkflowId())
                .warnings(warnings)
                .voucherSummary(voucherSummary)
                .build();
    }
}
