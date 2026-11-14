package com.arktech.superaccountant.gst.rules;

import com.arktech.superaccountant.daybook.models.ParsedVoucher;
import com.arktech.superaccountant.daybook.models.ParsedVoucherEntry;
import com.arktech.superaccountant.gst.models.GstDaybookFinding;
import com.arktech.superaccountant.gst.models.GstFindingType;
import com.arktech.superaccountant.masters.models.FindingSeverity;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class InputOutputDirectionRule implements GstDaybookRule {

    @Override
    public List<GstDaybookFinding> execute(GstRuleContext ctx, List<ParsedVoucher> vouchers) {
        List<GstDaybookFinding> findings = new ArrayList<>();
        if (vouchers == null) return findings;
        for (ParsedVoucher v : vouchers) {
            if (v == null || v.isCancelled() || v.getEntries() == null) continue;
            boolean purchase = GstRuleSupport.isPurchaseSide(v.getVoucherTypeName());
            boolean sales = GstRuleSupport.isSalesSide(v.getVoucherTypeName());
            if (!purchase && !sales) continue;
            for (ParsedVoucherEntry e : GstRuleSupport.gstEntries(v, ctx)) {
                String name = e.getLedgerName() == null ? "" : e.getLedgerName().toLowerCase(Locale.ROOT);
                if (purchase && name.contains("output")) {
                    findings.add(GstRuleSupport.finding(ctx, v, GstFindingType.INPUT_OUTPUT_DIRECTION,
                            FindingSeverity.MEDIUM, e.getLedgerName(), "Input GST ledger", e.getLedgerName(),
                            "Output GST ledger used in " + v.getVoucherTypeName() + " voucher"));
                } else if (sales && name.contains("input")) {
                    findings.add(GstRuleSupport.finding(ctx, v, GstFindingType.INPUT_OUTPUT_DIRECTION,
                            FindingSeverity.MEDIUM, e.getLedgerName(), "Output GST ledger", e.getLedgerName(),
                            "Input GST ledger used in " + v.getVoucherTypeName() + " voucher"));
                }
            }
        }
        return findings;
    }
}
