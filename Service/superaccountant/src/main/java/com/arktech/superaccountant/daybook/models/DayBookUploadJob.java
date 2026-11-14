package com.arktech.superaccountant.daybook.models;

import com.arktech.superaccountant.masters.models.UploadJobStatus;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "day_book_upload_jobs")
@Data
@NoArgsConstructor
public class DayBookUploadJob {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "file_name")
    private String fileName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UploadJobStatus status;

    @Column(name = "total_vouchers_parsed")
    private Integer totalVouchersParsed;

    @Column(name = "uploaded_by")
    private String uploadedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    // Declared coverage range (required for every upload, N2)
    @Column(name = "coverage_from")
    private LocalDate coverageFrom;

    @Column(name = "coverage_to")
    private LocalDate coverageTo;

    // Actual voucher date range observed in the file (null/unparseable dates ignored)
    @Column(name = "min_voucher_date")
    private LocalDate minVoucherDate;

    @Column(name = "max_voucher_date")
    private LocalDate maxVoucherDate;

    // Workflow this upload was made for, or null for a legacy upload
    @Column(name = "created_for_workflow_id")
    private UUID createdForWorkflowId;
}
