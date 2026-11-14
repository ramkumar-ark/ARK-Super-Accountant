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

import static com.arktech.superaccountant.gst.rules.GstRuleSupport.TOLERANCE;

/**
 * Rule 3 — AMOUNT_SPLIT_ERROR (HIGH): mixed heads, or CGST != SGST beyond tolerance.
 * Two independent checks — a voucher can produce both findings.
 */
@Component
public class GstAmountSplitRule implements GstDaybookRule {

    @Override
    public List<GstDaybookFinding> execute(GstRuleContext ctx, List<ParsedVoucher> vouchers) {
        List<GstDaybookFinding> findings = new ArrayList<>();
        for (ParsedVoucher v : vouchers) {
            if (v.isCancelled() || v.getEntries() == null) continue;
            Map<String, BigDecimal> heads = GstRuleSupport.headTotals(GstRuleSupport.gstEntries(v, ctx));
            BigDecimal cgst = heads.getOrDefault("CGST", BigDecimal.ZERO);
            BigDecimal sgst = heads.getOrDefault("SGST", BigDecimal.ZERO);
            BigDecimal igst = heads.getOrDefault("IGST", BigDecimal.ZERO);

            if (igst.compareTo(BigDecimal.ZERO) > 0
                    && (cgst.compareTo(BigDecimal.ZERO) > 0 || sgst.compareTo(BigDecimal.ZERO) > 0)) {
                findings.add(GstRuleSupport.finding(ctx, v, GstFindingType.AMOUNT_SPLIT_ERROR,
                        FindingSeverity.HIGH, null,
                        "Either IGST alone or CGST+SGST alone",
                        "IGST " + igst + " with CGST " + cgst + " / SGST " + sgst,
                        "Voucher mixes IGST with CGST/SGST"));
            }
            if (cgst.compareTo(BigDecimal.ZERO) > 0 && sgst.compareTo(BigDecimal.ZERO) > 0
                    && cgst.subtract(sgst).abs().compareTo(TOLERANCE) > 0) {
                findings.add(GstRuleSupport.finding(ctx, v, GstFindingType.AMOUNT_SPLIT_ERROR,
                        FindingSeverity.HIGH, null,
                        "CGST = SGST", "CGST " + cgst + " vs SGST " + sgst,
                        "CGST and SGST amounts differ beyond tolerance"));
            }
        }
        return findings;
    }
}
