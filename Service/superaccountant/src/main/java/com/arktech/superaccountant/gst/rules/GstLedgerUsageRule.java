package com.arktech.superaccountant.gst.rules;

import com.arktech.superaccountant.daybook.models.ParsedVoucher;
import com.arktech.superaccountant.gst.models.GstDaybookFinding;
import com.arktech.superaccountant.gst.models.GstFindingType;
import com.arktech.superaccountant.masters.models.FindingSeverity;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Rule 4 — WRONG_GST_LEDGER_STATE (HIGH): IGST on intra-state, CGST/SGST on inter-state. */
@Component
public class GstLedgerUsageRule implements GstDaybookRule {

    @Override
    public List<GstDaybookFinding> execute(GstRuleContext ctx, List<ParsedVoucher> vouchers) {
        List<GstDaybookFinding> findings = new ArrayList<>();
        for (ParsedVoucher v : vouchers) {
            if (v.isCancelled() || v.getEntries() == null) continue;
            Boolean inter = GstRuleSupport.interState(ctx, v);
            if (inter == null) continue;

            Map<String, BigDecimal> heads = GstRuleSupport.headTotals(GstRuleSupport.gstEntries(v, ctx));
            BigDecimal cgstSgst = heads.getOrDefault("CGST", BigDecimal.ZERO)
                    .add(heads.getOrDefault("SGST", BigDecimal.ZERO));
            BigDecimal igst = heads.getOrDefault("IGST", BigDecimal.ZERO);

            if (!inter && igst.compareTo(BigDecimal.ZERO) > 0) {
                findings.add(GstRuleSupport.finding(ctx, v, GstFindingType.WRONG_GST_LEDGER_STATE,
                        FindingSeverity.HIGH, null, "CGST + SGST (intra-state)", "IGST " + igst,
                        "IGST used for intra-state transaction with '" + v.getPartyLedgerName() + "'"));
            } else if (inter && cgstSgst.compareTo(BigDecimal.ZERO) > 0) {
                findings.add(GstRuleSupport.finding(ctx, v, GstFindingType.WRONG_GST_LEDGER_STATE,
                        FindingSeverity.HIGH, null, "IGST (inter-state)", "CGST/SGST " + cgstSgst,
                        "CGST/SGST used for inter-state transaction with '" + v.getPartyLedgerName() + "'"));
            }
        }
        return findings;
    }
}
