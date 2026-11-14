package com.arktech.superaccountant.gst.rules;

import com.arktech.superaccountant.daybook.models.ParsedVoucher;
import com.arktech.superaccountant.daybook.models.ParsedVoucherEntry;
import com.arktech.superaccountant.gst.models.GstDaybookFinding;
import com.arktech.superaccountant.gst.models.GstFindingType;
import com.arktech.superaccountant.masters.models.FindingSeverity;
import com.arktech.superaccountant.masters.models.GstApplicabilityType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class RcmSelfInvoiceMissingRule implements GstDaybookRule {

    @Override
    public List<GstDaybookFinding> execute(GstRuleContext ctx, List<ParsedVoucher> vouchers) {
        List<GstDaybookFinding> findings = new ArrayList<>();
        if (vouchers == null) return findings;
        Set<String> selfInvoiced = new HashSet<>();
        for (ParsedVoucher v : vouchers) {
            if (v == null || v.isCancelled() || !v.isReverseChargeApplicable()) continue;
            if (v.getVoucherDate() == null || v.getPartyLedgerName() == null) continue;
            selfInvoiced.add(key(v.getVoucherDate(), v.getPartyLedgerName()));
        }
        for (ParsedVoucher v : vouchers) {
            if (v == null || v.isCancelled() || v.isReverseChargeApplicable() || v.getEntries() == null) continue;
            if (v.getVoucherDate() != null && v.getPartyLedgerName() != null
                    && selfInvoiced.contains(key(v.getVoucherDate(), v.getPartyLedgerName()))) {
                continue;
            }
            for (ParsedVoucherEntry e : v.getEntries()) {
                if (!e.isDebit()) continue;
                boolean rcmLedger = ctx.snapshot(e.getLedgerName())
                        .map(s -> s.getGstApplicabilityType() == GstApplicabilityType.RCM)
                        .orElse(false);
                if (!rcmLedger) continue;
                YearMonth month = v.getVoucherDate() == null ? null : YearMonth.from(v.getVoucherDate());
                findings.add(GstRuleSupport.finding(ctx, v, GstFindingType.RCM_SELF_INVOICE_MISSING,
                        FindingSeverity.HIGH, e.getLedgerName(),
                        "Reverse-charge self-invoice for '" + v.getPartyLedgerName() + "' in " + month,
                        "None found",
                        "RCM ledger '" + e.getLedgerName() + "' debited " + e.getAmount() + " with no reverse-charge self-invoice in " + month));
                findings.get(findings.size() - 1).setTaxableAmount(abs(e));
            }
        }
        return findings;
    }

    private String key(LocalDate date, String party) {
        return YearMonth.from(date) + "|" + party.trim().toLowerCase(Locale.ROOT);
    }

    private BigDecimal abs(ParsedVoucherEntry e) {
        return e.getAmount() == null ? BigDecimal.ZERO : e.getAmount().abs();
    }
}
