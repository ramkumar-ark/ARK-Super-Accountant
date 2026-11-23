package com.arktech.superaccountant.tdsapplicability;

import com.arktech.superaccountant.daybook.models.DayBookUploadJob;
import com.arktech.superaccountant.daybook.models.ParsedVoucher;
import com.arktech.superaccountant.daybook.models.ParsedVoucherEntry;
import com.arktech.superaccountant.daybook.repository.DayBookUploadJobRepository;
import com.arktech.superaccountant.daybook.repository.ParsedVoucherRepository;
import com.arktech.superaccountant.masters.models.Organization;
import com.arktech.superaccountant.masters.models.UploadJob;
import com.arktech.superaccountant.masters.models.UploadJobStatus;
import com.arktech.superaccountant.masters.models.UploadLedgerSnapshot;
import com.arktech.superaccountant.masters.repository.OrganizationRepository;
import com.arktech.superaccountant.masters.repository.UploadJobRepository;
import com.arktech.superaccountant.masters.repository.UploadLedgerSnapshotRepository;
import com.arktech.superaccountant.masters.util.LedgerNames;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * PAN-only TDS advisories (section 194M, 194-IB) for an individual/HUF
 * organization in a financial year it is not a TDS deductor. Advisories only:
 * no findings table, no workflow, no TAN requirement.
 *
 * <ul>
 *   <li>194M — per party ledger, the FY sum of debits to expense ledgers whose
 *       masters carry tdsSection 194C/194H/194J_A/194J_B/194J_C/194M, when that
 *       sum exceeds ₹50,00,000.</li>
 *   <li>194-IB — per party ledger per calendar month, debits to ledgers with
 *       tdsSection 194I_B, when the month's sum exceeds ₹50,000.</li>
 *   <li>{@code form} — from the first voucher date that crossed the threshold:
 *       before 1 April 2026 → Form 26QD (194M) / Form 26QC (194-IB); on or after
 *       → Form 141.</li>
 * </ul>
 *
 * Data sources mirror the other scanners: the latest completed masters upload's
 * ledger snapshots (for tdsSection) and the latest completed day-book upload's
 * parsed vouchers.
 */
@Service
public class PanOnlyTdsAdvisoryService {

    static final Set<UploadJobStatus> COMPLETED_STATUSES =
            EnumSet.of(UploadJobStatus.COMPLETED, UploadJobStatus.COMPLETED_WITH_MISMATCHES);

    private static final String INDIVIDUAL_HUF_CODE = "Q";
    private static final LocalDate FORM_141_FROM = LocalDate.of(2026, 4, 1);
    private static final Set<String> SECTION_194M_SECTIONS =
            Set.of("194C", "194H", "194J_A", "194J_B", "194J_C", "194M");
    private static final String SECTION_194IB = "194I_B";
    private static final BigDecimal LIMIT_194M = new BigDecimal("5000000.00");
    private static final BigDecimal LIMIT_194IB = new BigDecimal("50000.00");

    private final OrganizationRepository organizationRepository;
    private final TdsApplicabilityService applicabilityService;
    private final UploadJobRepository uploadJobRepository;
    private final UploadLedgerSnapshotRepository snapshotRepository;
    private final DayBookUploadJobRepository dayBookJobRepository;
    private final ParsedVoucherRepository voucherRepository;

    public PanOnlyTdsAdvisoryService(OrganizationRepository organizationRepository,
                                     TdsApplicabilityService applicabilityService,
                                     UploadJobRepository uploadJobRepository,
                                     UploadLedgerSnapshotRepository snapshotRepository,
                                     DayBookUploadJobRepository dayBookJobRepository,
                                     ParsedVoucherRepository voucherRepository) {
        this.organizationRepository = organizationRepository;
        this.applicabilityService = applicabilityService;
        this.uploadJobRepository = uploadJobRepository;
        this.snapshotRepository = snapshotRepository;
        this.dayBookJobRepository = dayBookJobRepository;
        this.voucherRepository = voucherRepository;
    }

    public List<PanOnlyAdvisory> advisories(UUID orgId, int financialYear) {
        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new IllegalArgumentException("Organization not found"));
        if (!INDIVIDUAL_HUF_CODE.equals(
                EntityClass.effectiveCode(org.getPan(), org.getDeductorType()).code())) {
            return List.of();
        }
        if (applicabilityService.isApplicable(orgId, financialYear)) {
            return List.of();
        }

        Map<String, String> tdsSectionsByKey = tdsSectionsOfLatestMastersUpload(orgId);
        if (tdsSectionsByKey.isEmpty()) {
            return List.of();
        }
        Optional<DayBookUploadJob> dayBookJob = dayBookJobRepository
                .findAllByOrganizationIdAndStatusInOrderByCreatedAtDesc(orgId, COMPLETED_STATUSES)
                .stream()
                .findFirst();
        if (dayBookJob.isEmpty()) {
            return List.of();
        }

        LocalDate fyStart = LocalDate.of(financialYear, org.getFinancialYearStart(), 1);
        LocalDate fyEnd = fyStart.plusYears(1).minusDays(1);
        List<ParsedVoucher> fyVouchers = voucherRepository.findByUploadJobId(dayBookJob.get().getId())
                .stream()
                .filter(v -> !v.isCancelled() && v.getVoucherDate() != null
                        && !v.getVoucherDate().isBefore(fyStart)
                        && !v.getVoucherDate().isAfter(fyEnd))
                .toList();
        return scan(fyVouchers, tdsSectionsByKey).stream()
                .map(a -> new PanOnlyAdvisory(a.section(), a.partyLedgerName(), financialYear,
                        a.month(), a.amount(), a.form()))
                .toList();
    }

    private Map<String, String> tdsSectionsOfLatestMastersUpload(UUID orgId) {
        return uploadJobRepository.findTopByOrganizationIdAndStatusInOrderByCreatedAtDesc(
                        orgId, COMPLETED_STATUSES)
                .map(job -> {
                    Map<String, String> byKey = new HashMap<>();
                    for (UploadLedgerSnapshot snapshot
                            : snapshotRepository.findByUploadJobId(job.getId())) {
                        if (snapshot.getTdsSection() != null
                                && !snapshot.getTdsSection().isBlank()) {
                            byKey.putIfAbsent(LedgerNames.normalize(snapshot.getLedgerName()),
                                    snapshot.getTdsSection());
                        }
                    }
                    return byKey;
                })
                .orElse(Map.of());
    }

    /** Pure scan over FY vouchers, keyed by normalized ledger name → tdsSection. */
    static List<PanOnlyAdvisory> scan(List<ParsedVoucher> vouchers,
                                      Map<String, String> tdsSectionsByKey) {
        List<ParsedVoucher> dated = new ArrayList<>(vouchers);
        dated.sort(Comparator.comparing(ParsedVoucher::getVoucherDate));

        Map<String, Bucket> year194M = new HashMap<>();
        Map<String, Bucket> month194IB = new HashMap<>();

        for (ParsedVoucher voucher : dated) {
            if (voucher.isCancelled() || voucher.getVoucherDate() == null
                    || voucher.getEntries() == null) {
                continue;
            }
            for (ParsedVoucherEntry entry : voucher.getEntries()) {
                if (entry == null || entry.getAmount() == null
                        || entry.getAmount().signum() <= 0) {
                    continue;
                }
                String key = LedgerNames.normalize(entry.getLedgerName());
                String section = tdsSectionsByKey.get(key);
                if (section == null) {
                    continue;
                }
                if (SECTION_194M_SECTIONS.contains(section)) {
                    year194M.compute(key, (k, b) -> accumulate(b, entry, LIMIT_194M));
                } else if (SECTION_194IB.equals(section)) {
                    String monthKey = key + "|" + YearMonth.from(voucher.getVoucherDate());
                    month194IB.compute(monthKey, (k, b) -> accumulate(b, entry, LIMIT_194IB));
                }
            }
        }

        List<PanOnlyAdvisory> advisories = new ArrayList<>();
        for (Map.Entry<String, Bucket> e : year194M.entrySet()) {
            if (e.getValue().total().compareTo(LIMIT_194M) > 0) {
                advisories.add(new PanOnlyAdvisory("194M", e.getValue().displayName(),
                        0, null, e.getValue().total().setScale(2, java.math.RoundingMode.HALF_UP),
                        formFor(e.getValue().crossedOn(), "Form 26QD")));
            }
        }
        for (Map.Entry<String, Bucket> e : month194IB.entrySet()) {
            if (e.getValue().total().compareTo(LIMIT_194IB) > 0) {
                String[] parts = e.getKey().split("\\|", 2);
                advisories.add(new PanOnlyAdvisory("194-IB", e.getValue().displayName(),
                        0, parts[1], e.getValue().total().setScale(2, java.math.RoundingMode.HALF_UP),
                        formFor(e.getValue().crossedOn(), "Form 26QC")));
            }
        }
        advisories.sort(Comparator
                .comparing(PanOnlyAdvisory::section)
                .thenComparing(PanOnlyAdvisory::partyLedgerName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(PanOnlyAdvisory::month,
                        Comparator.nullsFirst(Comparator.naturalOrder())));
        return advisories;
    }

    /** Per-party (or per party-month) accumulation: running total, first threshold-crossing date. */
    private record Bucket(BigDecimal total, LocalDate crossedOn, String displayName) {
    }

    private static Bucket accumulate(Bucket bucket, ParsedVoucherEntry entry, BigDecimal limit) {
        BigDecimal total = (bucket == null ? BigDecimal.ZERO : bucket.total()).add(entry.getAmount());
        LocalDate crossedOn = bucket != null && bucket.crossedOn() != null
                ? bucket.crossedOn()
                : total.compareTo(limit) > 0 ? entry.getParsedVoucher().getVoucherDate() : null;
        String name = bucket != null ? bucket.displayName() : entry.getLedgerName();
        return new Bucket(total, crossedOn, name);
    }

    private static String formFor(LocalDate crossedOn, String legacyForm) {
        return crossedOn != null && !crossedOn.isBefore(FORM_141_FROM) ? "Form 141" : legacyForm;
    }
}
