package com.arktech.superaccountant.tds.engine;

import com.arktech.superaccountant.daybook.models.ParsedVoucher;
import com.arktech.superaccountant.daybook.models.ParsedVoucherEntry;
import com.arktech.superaccountant.masters.models.LedgerCategory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class DebtorTdsReceivableScanner {

    public record DebtorTanGap(String debtorLedgerName, String receivableLedgerName, UUID voucherId,
                               String voucherNumber, LocalDate voucherDate, BigDecimal deducted) {
    }

    private DebtorTdsReceivableScanner() {
    }

    public static List<DebtorTanGap> scan(List<ParsedVoucher> vouchers,
                                          Map<String, LedgerCategory> categoriesByKey,
                                          Set<String> debtorKeys,
                                          Map<String, ?> debtorTansByKey) {
        List<DebtorTanGap> gaps = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (ParsedVoucher v : vouchers) {
            if (v == null || v.isCancelled() || v.getVoucherDate() == null || v.getEntries() == null) {
                continue;
            }
            List<ParsedVoucherEntry> receivableEntries = new ArrayList<>();
            List<ParsedVoucherEntry> debtorEntries = new ArrayList<>();
            for (ParsedVoucherEntry e : v.getEntries()) {
                if (e == null || e.getLedgerName() == null || e.getAmount() == null) {
                    continue;
                }
                if (e.getAmount().signum() > 0 && TdsEngine.key(e.getLedgerName()).contains("tds receivable")) {
                    receivableEntries.add(e);
                }
                if (e.getAmount().signum() < 0 && debtorKeys.contains(TdsEngine.key(e.getLedgerName()))) {
                    debtorEntries.add(e);
                }
            }
            for (ParsedVoucherEntry receivable : receivableEntries) {
                for (ParsedVoucherEntry debtor : debtorEntries) {
                    String debtorKey = TdsEngine.key(debtor.getLedgerName());
                    if (debtorTansByKey.containsKey(debtorKey)) {
                        continue;
                    }
                    String dedupKey = v.getId() + "|" + debtorKey;
                    if (!seen.add(dedupKey)) {
                        continue;
                    }
                    gaps.add(new DebtorTanGap(debtor.getLedgerName(), receivable.getLedgerName(),
                            v.getId(), v.getVoucherNumber(), v.getVoucherDate(),
                            receivable.getAmount().abs().setScale(2, RoundingMode.HALF_UP)));
                }
            }
        }
        return gaps;
    }
}
