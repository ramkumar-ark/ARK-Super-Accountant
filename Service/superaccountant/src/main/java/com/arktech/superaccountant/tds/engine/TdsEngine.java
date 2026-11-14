package com.arktech.superaccountant.tds.engine;

import com.arktech.superaccountant.daybook.models.ParsedVoucher;
import com.arktech.superaccountant.daybook.models.ParsedVoucherEntry;
import com.arktech.superaccountant.masters.models.FindingSeverity;
import com.arktech.superaccountant.masters.models.LedgerCategory;
import com.arktech.superaccountant.masters.models.ResolveStatus;
import com.arktech.superaccountant.masters.models.UploadLedgerSnapshot;
import com.arktech.superaccountant.tds.models.TdsDeducteeEntry;
import com.arktech.superaccountant.tds.models.TdsDeducteeType;
import com.arktech.superaccountant.tds.models.TdsFinding;
import com.arktech.superaccountant.tds.models.TdsFindingType;
import com.arktech.superaccountant.tds.models.TdsSectionConfig;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class TdsEngine {

    public static final String PAN_PATTERN = "[A-Z]{5}[0-9]{4}[A-Z]";
    public static final BigDecimal TOLERANCE = new BigDecimal("1.00");
    private static final String NOT_SUBJECT = "NOT_SUBJECT";
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    public TdsAnalysisResult analyze(TdsAnalysisInput input) {
        List<TdsFinding> findings = new ArrayList<>();
        List<TdsDeducteeEntry> entries = new ArrayList<>();
        List<VoucherFact> facts = new ArrayList<>();
        runPerVoucherPass(input, facts, findings, entries);
        runThresholdPass(facts, input, findings);
        return new TdsAnalysisResult(findings, entries);
    }

    public static String key(String ledgerName) {
        if (ledgerName == null) {
            return null;
        }
        return ledgerName.trim().toLowerCase(Locale.ROOT);
    }

    static TdsDeducteeType deducteeTypeFromPan(String validPan) {
        if (validPan != null && validPan.length() >= 4 && validPan.charAt(3) == 'C') {
            return TdsDeducteeType.COMPANY;
        }
        return TdsDeducteeType.NON_COMPANY;
    }

    private void runPerVoucherPass(TdsAnalysisInput input, List<VoucherFact> facts,
                                   List<TdsFinding> findings, List<TdsDeducteeEntry> entries) {
        Set<String> panFindingKeys = new HashSet<>();
        for (ParsedVoucher v : input.vouchers()) {
            if (v == null || v.isCancelled() || v.getVoucherDate() == null
                    || v.getEntries() == null
                    || v.getVoucherDate().isBefore(input.fyStart())
                    || v.getVoucherDate().isAfter(input.periodEnd())) {
                continue;
            }
            Map<String, BigDecimal> baseBySection = new LinkedHashMap<>();
            List<ParsedVoucherEntry> tdsEntries = new ArrayList<>();
            for (ParsedVoucherEntry e : v.getEntries()) {
                if (e == null || e.getLedgerName() == null || e.getAmount() == null) {
                    continue;
                }
                UploadLedgerSnapshot snapshot = input.snapshotsByName().get(key(e.getLedgerName()));
                LedgerCategory category = snapshot != null && snapshot.getCategory() != null
                        ? snapshot.getCategory()
                        : LedgerCategory.OTHER;
                if (e.getAmount().signum() > 0
                        && (category == LedgerCategory.EXPENSE || category == LedgerCategory.PURCHASE)
                        && Boolean.TRUE.equals(snapshot != null ? snapshot.getTdsApplicable() : null)) {
                    String section = snapshot.getTdsSection() == null
                            ? null : snapshot.getTdsSection().trim();
                    if (section != null && !section.isBlank() && !NOT_SUBJECT.equals(section)) {
                        baseBySection.merge(section, e.getAmount(), BigDecimal::add);
                    }
                } else if (category == LedgerCategory.TDS && e.getAmount().signum() != 0) {
                    tdsEntries.add(e);
                }
            }
            if (baseBySection.isEmpty()) {
                continue;
            }
            Map<String, BigDecimal> tdsBySection = new LinkedHashMap<>();
            for (ParsedVoucherEntry e : tdsEntries) {
                UploadLedgerSnapshot snapshot = input.snapshotsByName().get(key(e.getLedgerName()));
                    String tdsSection = snapshot.getTdsSection() == null
                            ? null : snapshot.getTdsSection().trim();
                    if (tdsSection != null && baseBySection.containsKey(tdsSection)) {
                        tdsBySection.merge(tdsSection, e.getAmount().abs(), BigDecimal::add);
                    } else if (tdsSection == null && baseBySection.size() == 1) {
                        tdsBySection.merge(baseBySection.keySet().iterator().next(),
                                e.getAmount().abs(), BigDecimal::add);
                    } else {
                        findings.add(finding(input, v, TdsFindingType.WRONG_SECTION, FindingSeverity.HIGH,
                                tdsSection,
                                null, e.getAmount().abs().setScale(2, RoundingMode.HALF_UP),
                                "TDS deducted under section " + tdsSection
                                        + " but voucher expenses map to " + baseBySection.keySet(),
                                "Correct the TDS ledger or the expense ledger's section in Tally."));
                    }
            }
            String partyName = v.getPartyLedgerName();
            if (partyName == null || partyName.isBlank()) {
                List<ParsedVoucherEntry> creditorEntries = new ArrayList<>();
                for (ParsedVoucherEntry e : v.getEntries()) {
                    if (e == null || e.getLedgerName() == null) {
                        continue;
                    }
                    UploadLedgerSnapshot snapshot = input.snapshotsByName().get(key(e.getLedgerName()));
                    if (snapshot != null && snapshot.getCategory() == LedgerCategory.CREDITOR) {
                        creditorEntries.add(e);
                    }
                }
                partyName = creditorEntries.size() == 1 ? creditorEntries.get(0).getLedgerName() : null;
            }
            String partyKey = key(partyName);
            for (Map.Entry<String, BigDecimal> baseEntry : baseBySection.entrySet()) {
                String section = baseEntry.getKey();
                BigDecimal base = baseEntry.getValue();
                TdsSectionConfig cfg = input.configsBySection().get(section);
                if (cfg == null) {
                    continue;
                }
                UploadLedgerSnapshot partySnapshot = partyKey == null
                        ? null : input.snapshotsByName().get(partyKey);
                String pan = partySnapshot == null ? null : partySnapshot.getPan();
                boolean panValid = pan != null && pan.trim().matches(PAN_PATTERN);
                String trimmedPan = pan == null ? null : pan.trim();
                BigDecimal rate = rateFor(partyKey, cfg, input);
                BigDecimal expected = base.multiply(rate)
                        .divide(HUNDRED, 2, RoundingMode.HALF_UP);
                BigDecimal deducted = tdsBySection.getOrDefault(section, BigDecimal.ZERO)
                        .setScale(2, RoundingMode.HALF_UP);
                facts.add(new VoucherFact(v, partyName, partyKey, section, base, deducted, rate));
                if (isInPeriod(v.getVoucherDate(), input)) {
                    if (pan == null) {
                        String dedupKey = partyKey + "|" + section + "|" + TdsFindingType.PAN_MISSING;
                        if (panFindingKeys.add(dedupKey)) {
                            findings.add(finding(input, v, TdsFindingType.PAN_MISSING, FindingSeverity.HIGH,
                                    section, null, null,
                                    "Party '" + partyName + "' has no PAN — " + rate
                                            + "% higher rate applies.",
                                    "Add a valid PAN in the PAN tab, or deduct at " + rate + "%."));
                        }
                    } else if (!panValid) {
                        String dedupKey = partyKey + "|" + section + "|" + TdsFindingType.PAN_INVALID;
                        if (panFindingKeys.add(dedupKey)) {
                            findings.add(finding(input, v, TdsFindingType.PAN_INVALID, FindingSeverity.HIGH,
                                    section, null, null,
                                    "Party '" + partyName + "' has invalid PAN '" + trimmedPan + "' — "
                                            + rate + "% higher rate applies.",
                                    "Add a valid PAN in the PAN tab, or deduct at " + rate + "%."));
                        }
                    }
                    if (deducted.signum() != 0) {
                        BigDecimal diff = expected.subtract(deducted);
                        if (!panValid) {
                            if (diff.abs().compareTo(TOLERANCE) > 0) {
                                findings.add(finding(input, v, TdsFindingType.HIGHER_RATE_NOT_APPLIED,
                                        FindingSeverity.HIGH, section, expected, deducted,
                                        "PAN missing/invalid: expected " + rate + "% (" + expected
                                                + "), deducted " + deducted + ".",
                                        "Deduct at the " + rate + "% higher rate or fix the PAN."));
                            }
                        } else if (diff.abs().compareTo(TOLERANCE) > 0) {
                            if (diff.signum() > 0) {
                                findings.add(finding(input, v, TdsFindingType.SHORT_DEDUCTED,
                                        FindingSeverity.HIGH, section, expected, deducted,
                                        "Expected TDS " + expected + " at " + rate + "%, deducted "
                                                + deducted + ".",
                                        "Deduct the shortfall of " + diff + " and revise the entry in Tally."));
                            } else {
                                findings.add(finding(input, v, TdsFindingType.EXCESS_DEDUCTED,
                                        FindingSeverity.MEDIUM, section, expected, deducted,
                                        "Deducted " + deducted + " exceeds expected " + expected + ".",
                                        "Verify the rate applied; excess can be adjusted in later payments."));
                            }
                        }
                    }
                    if (deducted.signum() > 0) {
                        entries.add(deducteeEntry(input, v, partyName, trimmedPan, panValid, section,
                                base.setScale(2, RoundingMode.HALF_UP), deducted, rate));
                    }
                }
            }
        }
    }

    private void runThresholdPass(List<VoucherFact> facts, TdsAnalysisInput input, List<TdsFinding> findings) {
        Map<String, List<VoucherFact>> groups = new LinkedHashMap<>();
        for (VoucherFact fact : facts) {
            groups.computeIfAbsent(fact.partyKey() + "|" + fact.section(), k -> new ArrayList<>()).add(fact);
        }
        for (List<VoucherFact> group : groups.values()) {
            group.sort(Comparator
                    .comparing((VoucherFact f) -> f.voucher().getVoucherDate(),
                            Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(f -> f.voucher().getVoucherNumber() == null
                            ? "" : f.voucher().getVoucherNumber()));
            TdsSectionConfig cfg = input.configsBySection().get(group.get(0).section());
            if (cfg == null) {
                continue;
            }
            BigDecimal aggregate = BigDecimal.ZERO;
            VoucherFact crossing = null;
            List<VoucherFact> priorUndeducted = new ArrayList<>();
            List<VoucherFact> postCrossing = new ArrayList<>();
            for (VoucherFact f : group) {
                aggregate = aggregate.add(f.base());
                if (crossing == null) {
                    if (aggregate.compareTo(cfg.getThresholdAnnual()) > 0) {
                        crossing = f;
                    } else if (f.deducted().signum() == 0) {
                        priorUndeducted.add(f);
                    }
                } else {
                    postCrossing.add(f);
                }
            }
            if (crossing != null && isInPeriod(crossing.voucher().getVoucherDate(), input)
                    && (crossing.deducted().signum() == 0 || !priorUndeducted.isEmpty())) {
                BigDecimal bases = BigDecimal.ZERO;
                List<String> relatedIds = new ArrayList<>();
                for (VoucherFact f : priorUndeducted) {
                    bases = bases.add(f.base());
                    relatedIds.add("\"" + f.voucher().getId() + "\"");
                }
                if (crossing.deducted().signum() == 0) {
                    bases = bases.add(crossing.base());
                }
                BigDecimal expected = bases.multiply(crossing.rate())
                        .divide(HUNDRED, 2, RoundingMode.HALF_UP);
                findings.add(finding(input, crossing.voucher(), TdsFindingType.THRESHOLD_CROSSED,
                        FindingSeverity.HIGH, crossing.section(), expected,
                        new BigDecimal("0.00"),
                        "Aggregate payments of " + bases + " to '" + crossing.partyName() + "' under "
                                + crossing.section() + " crossed the annual threshold of "
                                + cfg.getThresholdAnnual() + "; TDS was not deducted.",
                        "Deduct " + expected + " and record the TDS entry in Tally."));
                findings.get(findings.size() - 1)
                        .setRelatedVoucherIds(relatedIds.isEmpty() ? "[]" : "[" + String.join(",", relatedIds) + "]");
            }
            List<VoucherFact> notDeductedCandidates = new ArrayList<>();
            for (VoucherFact f : priorUndeducted) {
                if (cfg.getThresholdPerTransaction() != null
                        && f.base().compareTo(cfg.getThresholdPerTransaction()) > 0) {
                    notDeductedCandidates.add(f);
                }
            }
            notDeductedCandidates.addAll(postCrossing);
            for (VoucherFact f : notDeductedCandidates) {
                if (f.deducted().signum() == 0 && isInPeriod(f.voucher().getVoucherDate(), input)) {
                    BigDecimal expected = f.base().multiply(f.rate())
                            .divide(HUNDRED, 2, RoundingMode.HALF_UP);
                    findings.add(finding(input, f.voucher(), TdsFindingType.NOT_DEDUCTED,
                            FindingSeverity.HIGH, f.section(), expected, new BigDecimal("0.00"),
                            "No TDS deducted on liable payment of " + f.base() + " under " + f.section() + ".",
                            "Deduct " + expected + " and record the TDS entry in Tally."));
                }
            }
        }
    }

    static boolean liable(String partyKey, String section, BigDecimal amount, LocalDate asOf,
                          List<VoucherFact> facts, TdsSectionConfig cfg) {
        if (cfg.getThresholdPerTransaction() != null
                && amount.compareTo(cfg.getThresholdPerTransaction()) > 0) {
            return true;
        }
        BigDecimal aggregate = BigDecimal.ZERO;
        for (VoucherFact f : facts) {
            if (partyKey != null && partyKey.equals(f.partyKey()) && section.equals(f.section())
                    && f.voucher().getVoucherDate() != null && !f.voucher().getVoucherDate().isAfter(asOf)) {
                aggregate = aggregate.add(f.base());
            }
        }
        return aggregate.add(amount).compareTo(cfg.getThresholdAnnual()) > 0;
    }

    private BigDecimal rateFor(String partyKey, TdsSectionConfig cfg, TdsAnalysisInput in) {
        UploadLedgerSnapshot partySnapshot = partyKey == null ? null : in.snapshotsByName().get(partyKey);
        String pan = partySnapshot == null ? null : partySnapshot.getPan();
        boolean panValid = pan != null && pan.trim().matches(PAN_PATTERN);
        if (!panValid) {
            return cfg.getHigherRateIfNoPan();
        }
        return deducteeTypeFromPan(pan.trim()) == TdsDeducteeType.COMPANY
                ? cfg.getRateCompany() : cfg.getRateNonCompany();
    }

    private TdsFinding finding(TdsAnalysisInput in, ParsedVoucher v, TdsFindingType t, FindingSeverity s,
                               String section, BigDecimal expected, BigDecimal actual,
                               String message, String fix) {
        TdsFinding f = new TdsFinding();
        f.setOrganizationId(in.organizationId());
        f.setWorkflowId(in.workflowId());
        f.setUploadJobId(in.uploadJobId());
        f.setFindingType(t);
        f.setSeverity(s);
        f.setPartyLedgerName(v.getPartyLedgerName());
        f.setVoucherId(v.getId());
        f.setVoucherDate(v.getVoucherDate());
        f.setVoucherNumber(v.getVoucherNumber());
        f.setSection(section);
        f.setExpectedAmount(expected);
        f.setActualAmount(actual);
        f.setMessage(message);
        f.setSuggestedFix(fix);
        f.setResolveStatus(ResolveStatus.OPEN);
        return f;
    }

    private TdsDeducteeEntry deducteeEntry(TdsAnalysisInput in, ParsedVoucher v, String partyName,
                                           String pan, boolean panValid, String section,
                                           BigDecimal base, BigDecimal deducted, BigDecimal rate) {
        TdsDeducteeEntry e = new TdsDeducteeEntry();
        e.setWorkflowId(in.workflowId());
        e.setDeducteeName(partyName);
        e.setDeducteePan(panValid ? pan : null);
        e.setPanValid(panValid);
        e.setDeducteeType(deducteeTypeFromPan(panValid ? pan : null));
        e.setSection(section);
        e.setAmountPaid(base);
        e.setTdsDeducted(deducted);
        e.setTdsDeposited(deducted);
        e.setDateOfPayment(v.getVoucherDate());
        e.setDateOfDeduction(v.getVoucherDate());
        e.setRateApplied(rate);
        e.setHigherRateApplied(!panValid);
        e.setSourceVoucherIds("[\"" + v.getId() + "\"]");
        return e;
    }

    private boolean isInPeriod(LocalDate date, TdsAnalysisInput input) {
        return !date.isBefore(input.periodStart()) && !date.isAfter(input.periodEnd());
    }

    private record VoucherFact(ParsedVoucher voucher, String partyName, String partyKey, String section,
                               BigDecimal base, BigDecimal deducted, BigDecimal rate) {
    }
}
