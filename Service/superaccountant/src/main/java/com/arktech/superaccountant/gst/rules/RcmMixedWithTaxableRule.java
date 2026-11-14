package com.arktech.superaccountant.gst.rules;

import com.arktech.superaccountant.daybook.models.ParsedVoucher;
import com.arktech.superaccountant.daybook.models.ParsedVoucherEntry;
import com.arktech.superaccountant.gst.models.GstDaybookFinding;
import com.arktech.superaccountant.gst.models.GstFindingType;
import com.arktech.superaccountant.masters.models.FindingSeverity;
import com.arktech.superaccountant.masters.models.GstApplicabilityType;
import com.arktech.superaccountant.masters.models.LedgerCategory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class RcmMixedWithTaxableRule implements GstDaybookRule {

    @Override
    public List<GstDaybookFinding> execute(GstRuleContext ctx, List<ParsedVoucher> vouchers) {
        List<GstDaybookFinding> findings = new ArrayList<>();
        if (vouchers == null) return findings;
        for (ParsedVoucher v : vouchers) {
            if (v == null || v.isCancelled() || !v.isReverseChargeApplicable() || v.getEntries() == null) continue;
            Map<String, String> distinct = new LinkedHashMap<>();
            v.getEntries().stream()
                    .filter(e -> !e.isPartyLedger())
                    .filter(e -> ctx.category(e.getLedgerName()) != LedgerCategory.GST)
                    .filter(e -> taxable(e, ctx))
                    .map(ParsedVoucherEntry::getLedgerName)
                    .forEach(n -> distinct.putIfAbsent(n == null ? "" : n.trim().toLowerCase(Locale.ROOT), n));
            List<String> names = new ArrayList<>(distinct.values());
            if (names.size() <= 1) continue;
            findings.add(GstRuleSupport.finding(ctx, v, GstFindingType.RCM_MIXED_WITH_TAXABLE,
                    FindingSeverity.LOW, null,
                    "Only RCM-applicable entries on a reverse-charge voucher",
                    String.join(", ", names),
                    "Taxable entries mixed with reverse-charge entries in " + v.getVoucherTypeName() + " voucher"));
        }
        return findings;
    }

    private boolean taxable(ParsedVoucherEntry e, GstRuleContext ctx) {
        if ("Taxable".equalsIgnoreCase(e.getGstTaxability())) return true;
        return ctx.snapshot(e.getLedgerName())
                .map(s -> s.getGstApplicabilityType() == GstApplicabilityType.TAXABLE)
                .orElse(false);
    }
}
