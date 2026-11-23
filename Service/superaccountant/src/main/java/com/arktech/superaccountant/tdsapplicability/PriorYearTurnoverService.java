package com.arktech.superaccountant.tdsapplicability;

import com.arktech.superaccountant.daybook.models.DayBookUploadJob;
import com.arktech.superaccountant.daybook.repository.DayBookUploadJobRepository;
import com.arktech.superaccountant.daybook.repository.ParsedVoucherRepository;
import com.arktech.superaccountant.filedreturns.FiledFormType;
import com.arktech.superaccountant.filedreturns.FiledReturnSnapshot;
import com.arktech.superaccountant.filedreturns.FiledReturnLineRepository;
import com.arktech.superaccountant.filedreturns.FiledReturnSnapshotRepository;
import com.arktech.superaccountant.masters.models.LedgerCategory;
import com.arktech.superaccountant.masters.models.Organization;
import com.arktech.superaccountant.masters.models.UploadJobStatus;
import com.arktech.superaccountant.masters.models.UploadLedgerSnapshot;
import com.arktech.superaccountant.masters.repository.OrganizationRepository;
import com.arktech.superaccountant.masters.repository.UploadJobRepository;
import com.arktech.superaccountant.masters.repository.UploadLedgerSnapshotRepository;
import com.arktech.superaccountant.masters.util.LedgerNames;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Estimates the preceding financial year's turnover for the TDS applicability
 * prompt, without persisting anything:
 *
 * <ol>
 *   <li>BOOKS — sales/direct-income credits minus debits (e.g. sales returns) on
 *       non-cancelled vouchers dated in the measured FY, restricted to ledgers
 *       classified INCOME in the latest completed masters snapshot whose group
 *       chain contains "Sales Accounts" or "Direct Incomes". Complete only when
 *       the union of completed day-book coverage spans the whole FY; a partial
 *       sum is still reported, flagged as an estimate.</li>
 *   <li>GSTR3B — only when there is no day-book data at all: sum of taxable
 *       values of table 3.1a/3.1b/3.1c/3.1e across non-superseded GSTR-3B
 *       snapshots covering the FY. Complete only with all 12 monthly periods.</li>
 *   <li>NONE — no usable data; amount null.</li>
 * </ol>
 */
@Service
public class PriorYearTurnoverService {

    private static final EnumSet<UploadJobStatus> COMPLETED_STATUSES =
            EnumSet.of(UploadJobStatus.COMPLETED, UploadJobStatus.COMPLETED_WITH_MISMATCHES);

    private static final List<String> GST_3B_TURNOVER_TABLE_CODES =
            List.of("3.1a", "3.1b", "3.1c", "3.1e");

    private static final int MONTHS_PER_YEAR = 12;

    private final OrganizationRepository organizationRepository;
    private final UploadJobRepository uploadJobRepository;
    private final UploadLedgerSnapshotRepository ledgerSnapshotRepository;
    private final DayBookUploadJobRepository dayBookJobRepository;
    private final ParsedVoucherRepository voucherRepository;
    private final FiledReturnLineRepository filedLineRepository;
    private final FiledReturnSnapshotRepository filedSnapshotRepository;

    public PriorYearTurnoverService(OrganizationRepository organizationRepository,
                                    UploadJobRepository uploadJobRepository,
                                    UploadLedgerSnapshotRepository ledgerSnapshotRepository,
                                    DayBookUploadJobRepository dayBookJobRepository,
                                    ParsedVoucherRepository voucherRepository,
                                    FiledReturnLineRepository filedLineRepository,
                                    FiledReturnSnapshotRepository filedSnapshotRepository) {
        this.organizationRepository = organizationRepository;
        this.uploadJobRepository = uploadJobRepository;
        this.ledgerSnapshotRepository = ledgerSnapshotRepository;
        this.dayBookJobRepository = dayBookJobRepository;
        this.voucherRepository = voucherRepository;
        this.filedLineRepository = filedLineRepository;
        this.filedSnapshotRepository = filedSnapshotRepository;
    }

    /** Estimates turnover for FY {@code applicabilityFy - 1}, the year before the one asked about. */
    public TurnoverEstimate estimateFor(UUID orgId, int applicabilityFy) {
        int measuredFy = applicabilityFy - 1;
        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new IllegalArgumentException("Organization not found"));

        LocalDate fyStart = LocalDate.of(measuredFy, org.getFinancialYearStart(), 1);
        LocalDate fyEnd = fyStart.plusYears(1).minusDays(1);

        Optional<BigDecimal> books = booksFigure(orgId, fyStart, fyEnd);
        if (books.isPresent()) {
            return new TurnoverEstimate(measuredFy, books.get().max(BigDecimal.ZERO),
                    TurnoverEstimate.TurnoverSource.BOOKS, booksCoverageComplete(orgId, fyStart, fyEnd));
        }
        return gstr3bEstimate(orgId, measuredFy, fyStart, fyEnd);
    }

    /** Present only when the organization has at least one completed day-book upload. */
    private Optional<BigDecimal> booksFigure(UUID orgId, LocalDate fyStart, LocalDate fyEnd) {
        boolean anyCompletedJob = !dayBookJobRepository
                .findAllByOrganizationIdAndStatusInOrderByCreatedAtDesc(orgId, COMPLETED_STATUSES)
                .isEmpty();
        if (!anyCompletedJob) {
            return Optional.empty();
        }
        List<String> salesLedgerNames = salesLedgerNames(orgId);
        if (salesLedgerNames.isEmpty()) {
            return Optional.of(BigDecimal.ZERO);
        }
        return Optional.of(voucherRepository.sumCreditMinusDebitForLedgers(
                orgId, false, fyStart, fyEnd, salesLedgerNames));
    }

    private boolean booksCoverageComplete(UUID orgId, LocalDate fyStart, LocalDate fyEnd) {
        List<DayBookUploadJob> jobs = dayBookJobRepository
                .findAllByOrganizationIdAndStatusInOrderByCreatedAtDesc(orgId, COMPLETED_STATUSES);
        List<LocalDate[]> intervals = new ArrayList<>();
        for (DayBookUploadJob job : jobs) {
            LocalDate from = job.getCoverageFrom() != null ? job.getCoverageFrom() : job.getMinVoucherDate();
            LocalDate to = job.getCoverageTo() != null ? job.getCoverageTo() : job.getMaxVoucherDate();
            if (from == null || to == null) {
                continue;
            }
            from = from.isBefore(fyStart) ? fyStart : from;
            to = to.isAfter(fyEnd) ? fyEnd : to;
            if (!from.isAfter(to)) {
                intervals.add(new LocalDate[]{from, to});
            }
        }
        intervals.sort((a, b) -> a[0].compareTo(b[0]));
        LocalDate coveredTo = null;
        LocalDate cursor = fyStart;
        for (LocalDate[] interval : intervals) {
            if (interval[0].isAfter(cursor)) {
                return false;
            }
            if (coveredTo == null || interval[1].isAfter(coveredTo)) {
                coveredTo = interval[1];
            }
            cursor = coveredTo.plusDays(1);
            if (coveredTo.isAfter(fyEnd) || coveredTo.isEqual(fyEnd)) {
                return true;
            }
        }
        return coveredTo != null && (coveredTo.isEqual(fyEnd) || coveredTo.isAfter(fyEnd));
    }

    private List<String> salesLedgerNames(UUID orgId) {
        return uploadJobRepository.findTopByOrganizationIdAndStatusInOrderByCreatedAtDesc(
                        orgId, COMPLETED_STATUSES)
                .map(job -> ledgerSnapshotRepository.findByUploadJobId(job.getId()).stream()
                        .filter(s -> s.getCategory() == LedgerCategory.INCOME)
                        .filter(s -> matchesSalesGroup(s))
                        .map(s -> LedgerNames.normalize(s.getLedgerName()))
                        .toList())
                .orElse(List.of());
    }

    private boolean matchesSalesGroup(UploadLedgerSnapshot s) {
        String chain = s.getGroupChain();
        if (chain == null) {
            return false;
        }
        String lower = chain.toLowerCase();
        return lower.contains("sales accounts") || lower.contains("direct incomes");
    }

    private TurnoverEstimate gstr3bEstimate(UUID orgId, int measuredFy,
                                            LocalDate fyStart, LocalDate fyEnd) {
        List<FiledReturnSnapshot> snapshots = filedSnapshotRepository
                .findAllByOrganizationIdAndFormTypeAndSupersededAtIsNullAndCoverStartBetween(
                        orgId, FiledFormType.GSTR3B, fyStart, fyEnd);
        if (snapshots.isEmpty()) {
            return TurnoverEstimate.none(measuredFy);
        }
        List<UUID> snapshotIds = snapshots.stream().map(FiledReturnSnapshot::getId).toList();
        BigDecimal amount = filedLineRepository.sumTaxableForTurnover(snapshotIds, GST_3B_TURNOVER_TABLE_CODES);
        long distinctPeriods = snapshots.stream()
                .map(FiledReturnSnapshot::getFilingPeriod)
                .distinct()
                .count();
        boolean complete = distinctPeriods >= MONTHS_PER_YEAR;
        return new TurnoverEstimate(measuredFy, amount, TurnoverEstimate.TurnoverSource.GSTR3B, complete);
    }

}
