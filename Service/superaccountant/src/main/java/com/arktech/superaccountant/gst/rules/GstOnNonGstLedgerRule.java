package com.arktech.superaccountant.gst.rules;

import com.arktech.superaccountant.daybook.models.ParsedVoucher;
import com.arktech.superaccountant.daybook.models.ParsedVoucherEntry;
import com.arktech.superaccountant.gst.models.GstDaybookFinding;
import com.arktech.superaccountant.gst.models.GstFindingType;
import com.arktech.superaccountant.masters.models.FindingSeverity;
import com.arktech.superaccountant.masters.models.LedgerCategory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Component
public class GstOnNonGstLedgerRule implements GstDaybookRule {

    @Override
    public List<GstDaybookFinding> execute(GstRuleContext ctx, List<ParsedVoucher> vouchers) {
        List<GstDaybookFinding> findings = new ArrayList<>();
        if (vouchers == null) return findings;
        for (ParsedVoucher v : vouchers) {
            if (v == null || v.isCancelled() || v.getEntries() == null) continue;
            for (ParsedVoucherEntry e : v.getEntries()) {
                if (GstRuleSupport.gstHead(e.getLedgerName()) == null) continue;
                if (ctx.snapshot(e.getLedgerName()).isEmpty()) continue;
                if (ctx.category(e.getLedgerName()) == LedgerCategory.GST) continue;
                if (e.getAmount() == null || e.getAmount().compareTo(BigDecimal.ZERO) == 0) continue;
                findings.add(GstRuleSupport.finding(ctx, v, GstFindingType.GST_ON_NON_GST_LEDGER,
                        FindingSeverity.MEDIUM, e.getLedgerName(),
                        "Category GST", "Category " + ctx.category(e.getLedgerName()),
                        "'" + e.getLedgerName() + "' looks like a GST ledger but is not categorised GST in masters"));
            }
        }
        return findings;
    }
}
