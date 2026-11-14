package com.arktech.superaccountant.daybook.services;

import com.arktech.superaccountant.daybook.models.DayBookUploadJob;
import com.arktech.superaccountant.daybook.payload.response.DayBookUploadJobResponse;
import com.arktech.superaccountant.daybook.payload.response.DayBookUploadJobResponseMapper;
import com.arktech.superaccountant.daybook.repository.DayBookUploadJobRepository;
import com.arktech.superaccountant.daybook.repository.ParsedVoucherRepository;
import com.arktech.superaccountant.masters.services.MastersGateService;
import com.arktech.superaccountant.tally.models.TallyMessage;
import com.arktech.superaccountant.tally.models.Voucher;
import com.arktech.superaccountant.tally.services.TallyParserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Validating intake for a day-book JSON upload: masters precondition, file
 * extension, coverage-range enforcement, and persistence. Shared by the legacy
 * day-book route and the workflow upload-binding route (worker B).
 */
@Service
public class DayBookIntakeService {

    @Autowired
    private TallyParserService tallyParserService;

    @Autowired
    private DayBookParserService dayBookParserService;

    @Autowired
    private DayBookUploadJobRepository dayBookUploadJobRepository;

    @Autowired
    private ParsedVoucherRepository parsedVoucherRepository;

    @Autowired
    private DayBookMasterValidationService dayBookMasterValidationService;

    @Autowired
    private MastersGateService mastersGateService;

    /**
     * Validates and persists a day-book upload.
     *
     * @param file                 the uploaded JSON file
     * @param coverageFrom         declared coverage start (required)
     * @param coverageTo           declared coverage end (required)
     * @param orgId                organization from the JWT principal
     * @param username             uploading user, from the JWT principal
     * @param createdForWorkflowId workflow this upload is for, or null for a legacy upload
     * @return the persisted job and any non-fatal warnings
     * @throws DayBookIntakeException with the HTTP status and error body when the upload is rejected
     */
    public DayBookIntakeResult intake(MultipartFile file, LocalDate coverageFrom, LocalDate coverageTo,
                                      UUID orgId, String username, UUID createdForWorkflowId) {
        if (coverageFrom != null && coverageTo != null && coverageFrom.isAfter(coverageTo)) {
            throw new DayBookIntakeException(400, Map.of("error", "coverageFrom must not be after coverageTo"));
        }

        // Masters-validated precondition: unresolved findings no longer block; the
        // unknown-ledger check below still guards against masters gaps.
        if (!mastersGateService.mastersValidated(orgId)) {
            throw new DayBookIntakeException(422, Map.of(
                    "error", "Upload and validate masters before uploading the day book."));
        }

        // Security: file extension check — prevent file extension spoofing (T-4-03)
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.toLowerCase().endsWith(".json")) {
            throw new DayBookIntakeException(400, Map.of("error", "Only JSON files are accepted."));
        }

        // Create job record with default FAILED status — updated to COMPLETED after success
        DayBookUploadJob job = new DayBookUploadJob();
        job.setOrganizationId(orgId);
        job.setFileName(originalFilename);
        job.setUploadedBy(username);
        job.setStatus(com.arktech.superaccountant.masters.models.UploadJobStatus.FAILED);
        job.setCoverageFrom(coverageFrom);
        job.setCoverageTo(coverageTo);
        job.setCreatedForWorkflowId(createdForWorkflowId);
        job = dayBookUploadJobRepository.save(job);

        TallyMessage tallyMessage;
        try {
            tallyMessage = tallyParserService.parseJson(file);
        } catch (IllegalArgumentException e) {
            job.setErrorMessage(e.getMessage());
            job.setCompletedAt(Instant.now());
            dayBookUploadJobRepository.save(job);
            throw new DayBookIntakeException(400, Map.of("error", e.getMessage(), "uploadId", job.getId()));
        } catch (IOException e) {
            job.setErrorMessage("Failed to read file: " + e.getMessage());
            job.setCompletedAt(Instant.now());
            dayBookUploadJobRepository.save(job);
            throw new DayBookIntakeException(500, Map.of("error", "File parsing failed.", "uploadId", job.getId()));
        }

        // Service-level envelope validation: null tallymessage → FAILED job (T-4-02)
        if (tallyMessage.getTallymessage() == null) {
            String msg = "Tally message contains no vouchers. Check that the file is a valid Tally day book export.";
            job.setErrorMessage(msg);
            job.setCompletedAt(Instant.now());
            dayBookUploadJobRepository.save(job);
            throw new DayBookIntakeException(400, Map.of("error", msg, "uploadId", job.getId()));
        }

        // Coverage check: min/max over parsed voucher dates; null/unparseable dates are ignored
        LocalDate min = null;
        LocalDate max = null;
        int outOfRangeCount = 0;
        LocalDate firstOutOfRangeDate = null;
        for (Voucher v : tallyMessage.getTallymessage()) {
            LocalDate d = VoucherCanonicalizer.parseDate(v.getDate());
            if (d == null) {
                continue;
            }
            if (min == null || d.isBefore(min)) {
                min = d;
            }
            if (max == null || d.isAfter(max)) {
                max = d;
            }
            if ((d.isBefore(coverageFrom) || d.isAfter(coverageTo))) {
                outOfRangeCount++;
                if (firstOutOfRangeDate == null) {
                    firstOutOfRangeDate = d;
                }
            }
        }
        if (outOfRangeCount > 0) {
            String msg = "Vouchers outside declared coverage " + coverageFrom + " to " + coverageTo;
            job.setStatus(com.arktech.superaccountant.masters.models.UploadJobStatus.FAILED);
            job.setErrorMessage(msg);
            job.setCompletedAt(Instant.now());
            dayBookUploadJobRepository.save(job);
            throw new DayBookIntakeException(422, Map.of(
                    "error", msg,
                    "outOfRangeCount", outOfRangeCount,
                    "firstOutOfRangeDate", firstOutOfRangeDate.toString()));
        }

        // Extract unique ledger names from all voucher entries in the parsed message
        Set<String> daybookLedgers = tallyMessage.getTallymessage().stream()
                .flatMap(v -> v.getAllLedgerEntriesCombined() != null
                        ? v.getAllLedgerEntriesCombined().stream()
                        : Stream.<com.arktech.superaccountant.tally.models.LedgerEntry>empty())
                .map(entry -> entry.getLedgername())
                .filter(name -> name != null && !name.isBlank())
                .collect(Collectors.toSet());

        if (!daybookLedgers.isEmpty()) {
            List<String> unknownLedgers = dayBookMasterValidationService.findUnknownLedgers(orgId, daybookLedgers);
            if (!unknownLedgers.isEmpty()) {
                job.setStatus(com.arktech.superaccountant.masters.models.UploadJobStatus.FAILED);
                job.setErrorMessage("Unknown ledgers: " + unknownLedgers);
                job.setCompletedAt(Instant.now());
                dayBookUploadJobRepository.save(job);
                throw new DayBookIntakeException(422, Map.of(
                        "error", "Daybook contains ledgers not found in validated masters.",
                        "unknownLedgers", unknownLedgers));
            }
        }

        var persistedVouchers = dayBookParserService.persistVouchers(tallyMessage, job.getId(), orgId);
        job.setTotalVouchersParsed(persistedVouchers.size());
        job.setMinVoucherDate(min);
        job.setMaxVoucherDate(max);
        job.setStatus(com.arktech.superaccountant.masters.models.UploadJobStatus.COMPLETED);
        job.setCompletedAt(Instant.now());
        job = dayBookUploadJobRepository.save(job);

        List<String> warnings = new ArrayList<>();
        if (min != null && min.isAfter(coverageFrom.plusDays(15))) {
            warnings.add("First voucher is dated " + min + ", more than 15 days after the declared start "
                    + coverageFrom + ".");
        }
        return new DayBookIntakeResult(job, warnings);
    }

    /**
     * Builds the upload response for the persisted job, including the voucher summary.
     */
    public com.arktech.superaccountant.daybook.payload.response.DayBookUploadJobResponse toResponse(
            DayBookUploadJob job, List<String> warnings) {
        List<Object[]> summaryRows = parsedVoucherRepository.findVoucherTypeSummary(job.getId());
        List<com.arktech.superaccountant.daybook.payload.response.VoucherTypeSummaryResponse> summary =
                new ArrayList<>();
        for (Object[] row : summaryRows) {
            summary.add(com.arktech.superaccountant.daybook.payload.response.VoucherTypeSummaryResponse.builder()
                    .voucherTypeName((String) row[0])
                    .count((Long) row[1])
                    .totalDebit(row[2] != null ? (BigDecimal) row[2] : BigDecimal.ZERO)
                    .totalCredit(row[3] != null ? (BigDecimal) row[3] : BigDecimal.ZERO)
                    .minDate(row[4] != null ? (LocalDate) row[4] : null)
                    .maxDate(row[5] != null ? (LocalDate) row[5] : null)
                    .build());
        }
        return DayBookUploadJobResponseMapper.toResponse(job, warnings, summary);
    }
}
