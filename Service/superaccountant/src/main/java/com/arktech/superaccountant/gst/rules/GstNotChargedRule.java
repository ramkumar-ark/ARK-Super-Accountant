package com.arktech.superaccountant.gst.rules;

import com.arktech.superaccountant.daybook.models.ParsedVoucher;
import com.arktech.superaccountant.daybook.models.ParsedVoucherEntry;
import com.arktech.superaccountant.gst.models.GstDaybookFinding;
import com.arktech.superaccountant.gst.models.GstFindingType;
import com.arktech.superaccountant.masters.models.FindingSeverity;
import com.arktech.superaccountant.masters.models.GstApplicabilityType;
import com.arktech.superaccountant.masters.models.LedgerCategory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Rule 1 — GST_NOT_CHARGED (HIGH): taxable expense/purchase debit, registered party,
 * but the voucher carries no GST entries with a non-zero amount. Purchase side only.
 */
@Component
public class GstNotChargedRule implements GstDaybookRule {

    @Override
    public List<GstDaybookFinding> execute(GstRuleContext ctx, List<ParsedVoucher> vouchers) {
        List<GstDaybookFinding> findings = new ArrayList<>();
        for (ParsedVoucher v : vouchers) {
            if (v.isCancelled() || v.getEntries() == null) continue;
            if (!GstRuleSupport.isPurchaseSide(v.getVoucherTypeName())) continue;

            boolean hasNonZeroGst = GstRuleSupport.gstEntries(v, ctx).stream()
                    .anyMatch(e -> e.getAmount() != null && e.getAmount().compareTo(BigDecimal.ZERO) != 0);
            if (hasNonZeroGst) continue;

            if (!partyRegistered(ctx, v)) continue;

            for (ParsedVoucherEntry e : v.getEntries()) {
                if (!e.isDebit()) continue;
                LedgerCategory cat = ctx.category(e.getLedgerName());
                if (cat != LedgerCategory.EXPENSE && cat != LedgerCategory.PURCHASE) continue;
                boolean taxable = ctx.snapshot(e.getLedgerName())
                        .map(s -> Boolean.TRUE.equals(s.getGstApplicable())
                                && s.getGstApplicabilityType() == GstApplicabilityType.TAXABLE)
                        .orElse(false);
                if (!taxable) continue;

                findings.add(GstRuleSupport.finding(ctx, v, GstFindingType.GST_NOT_CHARGED,
                        FindingSeverity.HIGH, e.getLedgerName(),
                        "CGST/SGST or IGST entries with non-zero amounts",
                        "No GST entries on voucher",
                        "Taxable ledger '" + e.getLedgerName() + "' debited " + e.getAmount()
                                + " for registered party '" + v.getPartyLedgerName()
                                + "' but no GST was charged"));
            }
        }
        return findings;
    }

    private boolean partyRegistered(GstRuleContext ctx, ParsedVoucher v) {
        if (v.getPartyGstin() != null && !v.getPartyGstin().isBlank()) return true;
        return ctx.snapshot(v.getPartyLedgerName())
                .map(s -> s.getGstin() != null && !s.getGstin().isBlank())
                .orElse(false);
    }
}
